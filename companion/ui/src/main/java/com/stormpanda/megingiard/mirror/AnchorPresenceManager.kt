package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.CutoutLostAnchorEffect
import com.stormpanda.megingiard.macropad.LayoutTransitionManager
import com.stormpanda.megingiard.macropad.MAX_LAYOUT_STREAM_DELAY_FRAMES
import com.stormpanda.megingiard.macropad.MIN_LAYOUT_STREAM_DELAY_FRAMES
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.ui.DialogToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "AnchorPresenceManager"

private const val PRESENCE_CHECK_INTERVAL_ACTIVE_MS = 16L // ~60 Hz (1-frame instant content absence detection)
private const val PRESENCE_CHECK_INTERVAL_LOST_FAST_MS = 33L // ~30 Hz prompt recovery (0–2s)
private const val PRESENCE_CHECK_INTERVAL_LOST_MEDIUM_MS = 100L // ~10 Hz candidate polling during cutscenes (2–5s)
private const val PRESENCE_CHECK_INTERVAL_LOST_SLOW_MS = 500L // ~2 Hz idle polling during long loading screens (>5s)

private const val LOST_BACKOFF_TIER_1_MS = 2_000L
private const val LOST_BACKOFF_TIER_2_MS = 5_000L
private const val AUTO_SWITCH_COOLDOWN_MS = 500L
private const val DEFAULT_SOURCE_WIDTH = 1920
private const val DEFAULT_SOURCE_HEIGHT = 1080

/**
 * Singleton manager coordinating real-time visual anchor presence detection and freeze-frame caching.
 *
 * Evaluates the active layout's visual reference anchor at 60 Hz to determine presence state.
 * When the anchor signature is lost (e.g. intended content is not on screen), all cutouts in the layout
 * simultaneously freeze on their pristine delayed frames retrieved from zero-allocation ring buffers.
 * Enforcing stream delay >= 1 frame whenever visual anchoring is enabled eliminates the need for
 * periodic background live frame capture.
 */
object AnchorPresenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var appContext: Context? = null

    private val layoutStates = ConcurrentHashMap<String, AnchorPresenceState>()
    private val layoutConsecutiveCounts = ConcurrentHashMap<String, Int>()
    private val lastValidFrameBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val cutoutRingBuffers = ConcurrentHashMap<String, CutoutFrameRingBuffer>()

    @VisibleForTesting
    internal val ringBufferCount: Int
        get() = cutoutRingBuffers.size

    @VisibleForTesting
    internal val lastValidFrameCount: Int
        get() = lastValidFrameBitmaps.size

    private var lastAutoSwitchTimeMs = 0L
    private var lostStateStartMs = 0L
    private var sparseProbePhase = 0

    @Volatile
    private var activeSmoother: WeakReference<GpuMotionSmoother>? = null

    fun registerGpuMotionSmoother(smoother: GpuMotionSmoother) {
        AppLog.d(TAG, "Registering active GpuMotionSmoother for hardware presence tracking")
        activeSmoother = WeakReference(smoother)
        updateSmootherTracking()
    }

    fun unregisterGpuMotionSmoother(smoother: GpuMotionSmoother?) {
        if (activeSmoother?.get() == smoother) {
            AppLog.d(TAG, "Unregistering active GpuMotionSmoother")
            activeSmoother = null
        }
    }

    private fun updateSmootherTracking() {
        val smoother = activeSmoother?.get() ?: return
        val activeLayout = MacroPadState.activeLayout.value
        val anchor = activeLayout?.visualAnchor
        if (activeLayout != null && anchor != null && anchor.enabled && anchor.isCalibrated) {
            smoother.updateStreamDelay(anchor.streamDelayFrames)
            smoother.setAnchorTracking(activeLayout.id, anchor) { layoutId, matchRatio ->
                onHardwareAnchorEvaluated(layoutId, matchRatio)
            }
        } else {
            smoother.updateStreamDelay(0)
            smoother.setAnchorTracking("", null, null)
            smoother.setFrozen(false)
        }
    }

    private fun onHardwareAnchorEvaluated(
        layoutId: String,
        matchRatio: Float,
    ) {
        val curState = layoutStates[layoutId] ?: AnchorPresenceState.PRESENT
        val curCount = layoutConsecutiveCounts[layoutId] ?: 0
        val (newState, newCount) =
            AnchorPresenceEvaluator.transitionState(curState, curCount, matchRatio, layoutId)

        if (newState != curState) {
            if (newState == AnchorPresenceState.LOST) {
                lostStateStartMs = SystemClock.uptimeMillis()
                activeSmoother?.get()?.setFrozen(true)
                captureOneShotFrozenFrame()
            } else if (newState == AnchorPresenceState.PRESENT) {
                lostStateStartMs = 0L
                activeSmoother?.get()?.setFrozen(false)
            }
            _presenceRevision.value++
        }
        layoutStates[layoutId] = newState
        layoutConsecutiveCounts[layoutId] = newCount
    }

    private fun captureOneShotFrozenFrame() {
        scope.launch(Dispatchers.Main.immediate) {
            val tv = MirrorFrameSampler.activeTextureViewRef?.get() ?: return@launch
            if (tv.isAvailable && tv.width > 0 && tv.height > 0) {
                try {
                    val bmp = tv.bitmap ?: return@launch
                    val activeLayout = MacroPadState.activeLayout.value
                    val allCutouts = ScreenCaptureManager.cutouts.value.ifEmpty { activeLayout?.mirrorCutouts.orEmpty() }
                    val frameW = bmp.width
                    val frameH = bmp.height

                    val srcRect = Rect()
                    val dstRect = Rect()

                    for (cutout in allCutouts) {
                        val override = InteractiveCutoutController.getOverrideCrop(cutout.id)
                        val cSrcX = override?.srcX?.coerceIn(0f, 1f) ?: cutout.srcX
                        val cSrcY = override?.srcY?.coerceIn(0f, 1f) ?: cutout.srcY
                        val cSrcW = override?.srcWidth?.coerceIn(0f, 1f) ?: cutout.srcWidth
                        val cSrcH = override?.srcHeight?.coerceIn(0f, 1f) ?: cutout.srcHeight

                        val cX = (cSrcX * frameW).roundToInt().coerceIn(0, frameW - 1)
                        val cY = (cSrcY * frameH).roundToInt().coerceIn(0, frameH - 1)
                        val cRight = ((cSrcX + cSrcW) * frameW).roundToInt().coerceIn(cX + 1, frameW)
                        val cBottom = ((cSrcY + cSrcH) * frameH).roundToInt().coerceIn(cY + 1, frameH)
                        val cW = (cRight - cX).coerceAtLeast(1)
                        val cH = (cBottom - cY).coerceAtLeast(1)

                        if (cW > 0 && cH > 0) {
                            try {
                                val cropped = Bitmap.createBitmap(cW, cH, Bitmap.Config.ARGB_8888)
                                val cCanvas = Canvas(cropped)
                                srcRect.set(cX, cY, cRight, cBottom)
                                dstRect.set(0, 0, cW, cH)
                                cCanvas.drawBitmap(bmp, srcRect, dstRect, null)
                                val old = lastValidFrameBitmaps.put(cutout.id, cropped)
                                if (old != null && !old.isRecycled) {
                                    old.recycle()
                                }
                            } catch (e: Exception) {
                                AppLog.w(TAG, "Error cropping frozen cutout bitmap: ${e.message}")
                            }
                        }
                    }
                    bmp.recycle()
                    _presenceRevision.value++
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error capturing one-shot frozen frame from TextureView", e)
                }
            }
        }
    }

    @VisibleForTesting
    internal val currentSparseProbePhase: Int
        get() = sparseProbePhase

    private val _presenceRevision = MutableStateFlow(0)
    val presenceRevision: StateFlow<Int> = _presenceRevision.asStateFlow()

    @VisibleForTesting
    internal fun computeLostCheckInterval(elapsedMs: Long): Long =
        when {
            elapsedMs >= LOST_BACKOFF_TIER_2_MS -> PRESENCE_CHECK_INTERVAL_LOST_SLOW_MS
            elapsedMs >= LOST_BACKOFF_TIER_1_MS -> PRESENCE_CHECK_INTERVAL_LOST_MEDIUM_MS
            else -> PRESENCE_CHECK_INTERVAL_LOST_FAST_MS
        }

    private data class CaptureState(
        val isCapturing: Boolean,
        val layout: PadLayout?,
        val profile: PadProfile?,
        val viewMode: CompanionViewMode,
    )

    /**
     * Initializes the manager with an application context and attaches state flow observers.
     */
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            observeCaptureState()
        }
    }

    private fun observeCaptureState() {
        scope.launch {
            combine(
                ScreenCaptureManager.isCapturing,
                MacroPadState.activeLayout,
                MacroPadState.activeProfile,
                AppStateManager.companionViewMode,
            ) { capturing, layout, profile, viewMode ->
                CaptureState(capturing, layout, profile, viewMode)
            }.collect { state ->
                updateMonitoringLoop(state.isCapturing, state.layout, state.profile, state.viewMode)
            }
        }
    }

    @VisibleForTesting
    internal val isMonitoring: Boolean
        get() = monitorJob?.isActive == true

    @VisibleForTesting
    @Synchronized
    internal fun updateMonitoringLoop(
        isCapturing: Boolean,
        layout: PadLayout?,
        profile: PadProfile?,
        viewMode: CompanionViewMode,
    ) {
        val hasLayoutAnchor = layout?.visualAnchor?.enabled == true
        val isAutoLayoutSwitching = viewMode == CompanionViewMode.AUTO && profile?.autoLayoutSwitching == true
        val hasAnyAnchoredLayout = profile?.layouts?.any { it.visualAnchor.enabled } == true
        val shouldMonitor = isCapturing && (hasLayoutAnchor || (isAutoLayoutSwitching && hasAnyAnchoredLayout))

        updateSmootherTracking()

        if (shouldMonitor) {
            if (monitorJob?.isActive != true) {
                AppLog.i(TAG, "Starting visual anchor presence monitoring loop (active 60 Hz / recover 30 Hz)")
                monitorJob = scope.launch { runMonitoringLoop() }
            }
        } else {
            if (monitorJob?.isActive == true) {
                AppLog.i(TAG, "Stopping visual anchor presence monitoring loop")
                monitorJob?.cancel()
                monitorJob = null
            }
            if (!isCapturing) {
                clearAllBuffers()
            }
        }
    }

    internal fun clearAllBuffers() {
        AppLog.d(TAG, "Clearing and recycling all ring buffer and freeze frame bitmaps")
        lostStateStartMs = 0L
        sparseProbePhase = 0
        activeSmoother?.get()?.setFrozen(false)
        cutoutRingBuffers.values.forEach { it.recycle() }
        cutoutRingBuffers.clear()
        lastValidFrameBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        lastValidFrameBitmaps.clear()
    }

    private suspend fun runMonitoringLoop() {
        while (scope.isActive) {
            val context = appContext ?: continue

            // Suspend presence monitoring and layout auto-switching during calibration or while editor/modal is open
            if (VisualAutoTuneCoordinator.isCalibrating.value ||
                AppStateManager.isEditorActive.value ||
                AppStateManager.isViewportEditActive.value ||
                AppStateManager.activePrimaryModal.value != null
            ) {
                delay(PRESENCE_CHECK_INTERVAL_LOST_FAST_MS)
                continue
            }

            val activeLayout = MacroPadState.activeLayout.value
            val layoutAnchor = activeLayout?.visualAnchor?.takeIf { it.enabled }
            val activeProfile = MacroPadState.activeProfile.value
            val viewMode = AppStateManager.companionViewMode.value
            val isAutoSwitchEligible =
                viewMode == CompanionViewMode.AUTO &&
                    activeProfile?.autoLayoutSwitching == true

            val srcW = ScreenCaptureManager.captureSourceWidth.value.let { if (it > 0) it else DEFAULT_SOURCE_WIDTH }
            val srcH = ScreenCaptureManager.captureSourceHeight.value.let { if (it > 0) it else DEFAULT_SOURCE_HEIGHT }

            if (activeLayout == null || layoutAnchor == null) {
                if (isAutoSwitchEligible) {
                    val frame = MirrorFrameSampler.captureFullFrame()
                    if (frame != null && frame.width > 0 && frame.height > 0) {
                        processCandidateScan(
                            context = context,
                            activeProfile = activeProfile,
                            excludedLayoutId = activeLayout?.id,
                            srcW = srcW,
                            srcH = srcH,
                            frame = frame,
                        )
                    }
                }
                delay(PRESENCE_CHECK_INTERVAL_LOST_FAST_MS)
                continue
            }

            val allCutouts = ScreenCaptureManager.cutouts.value
            // Zero-allocation stale cutout ring buffer cleanup
            if (cutoutRingBuffers.size > allCutouts.size) {
                val it = cutoutRingBuffers.keys.iterator()
                while (it.hasNext()) {
                    val id = it.next()
                    if (allCutouts.none { it.id == id }) {
                        cutoutRingBuffers.remove(id)?.recycle()
                        lastValidFrameBitmaps.remove(id)?.let { if (!it.isRecycled) it.recycle() }
                    }
                }
            }

            // 60 Hz during active display; tiered back-off (33ms -> 100ms -> 500ms) when lost
            val isCurrentLost = layoutStates[activeLayout.id] == AnchorPresenceState.LOST
            if (isCurrentLost && lostStateStartMs == 0L) {
                lostStateStartMs = SystemClock.uptimeMillis()
            }
            val checkInterval =
                if (!isCurrentLost) {
                    PRESENCE_CHECK_INTERVAL_ACTIVE_MS
                } else {
                    val elapsedSinceLost = (SystemClock.uptimeMillis() - lostStateStartMs).coerceAtLeast(0L)
                    computeLostCheckInterval(elapsedSinceLost)
                }
            delay(checkInterval)

            val hasHardwareTracking = activeSmoother?.get() != null
            if (hasHardwareTracking) {
                // Presence is sampled in hardware at 60 Hz directly on the GL thread with 0 CPU readbacks
                continue
            }

            val anchorHasFreeze = activeLayout.visualAnchor.hasEffect(CutoutLostAnchorEffect.FREEZE)
            val layoutDelayFrames =
                layoutAnchor.streamDelayFrames.coerceIn(MIN_LAYOUT_STREAM_DELAY_FRAMES, MAX_LAYOUT_STREAM_DELAY_FRAMES)

            // Capture the master frame once directly from MirrorFrameSampler (zero intermediate copies)
            val frame = MirrorFrameSampler.captureFullFrame() ?: continue
            try {
                val frameW = frame.width
                val frameH = frame.height
                if (frameW <= 0 || frameH <= 0) continue

                var anyStateChanged = false

                // Push historical frames directly from master frame into per-cutout ring buffers only when freeze is enabled
                if (anchorHasFreeze && layoutDelayFrames > 0) {
                    val ringCapacity = layoutDelayFrames + 2
                    for (cutout in allCutouts) {
                        val override = InteractiveCutoutController.getOverrideCrop(cutout.id)
                        val cSrcX = override?.srcX?.coerceIn(0f, 1f) ?: cutout.srcX
                        val cSrcY = override?.srcY?.coerceIn(0f, 1f) ?: cutout.srcY
                        val cSrcW = override?.srcWidth?.coerceIn(0f, 1f) ?: cutout.srcWidth
                        val cSrcH = override?.srcHeight?.coerceIn(0f, 1f) ?: cutout.srcHeight

                        val cX = (cSrcX * frameW).roundToInt().coerceIn(0, frameW - 1)
                        val cY = (cSrcY * frameH).roundToInt().coerceIn(0, frameH - 1)
                        val cRight = ((cSrcX + cSrcW) * frameW).roundToInt().coerceIn(cX + 1, frameW)
                        val cBottom = ((cSrcY + cSrcH) * frameH).roundToInt().coerceIn(cY + 1, frameH)
                        val cW = (cRight - cX).coerceAtLeast(1)
                        val cH = (cBottom - cY).coerceAtLeast(1)

                        if (cW > 0 && cH > 0) {
                            var ring = cutoutRingBuffers[cutout.id]
                            if (ring == null || ring.width != cW || ring.height != cH || ring.capacity != ringCapacity) {
                                ring?.recycle()
                                ring = CutoutFrameRingBuffer(cW, cH, ringCapacity)
                                cutoutRingBuffers[cutout.id] = ring
                            }
                            ring.pushFrame(frame, cX, cY)
                        }
                    }
                }

                // Evaluate active layout anchor signature directly against master frame
                val signature = activeLayout.visualAnchor.signature
                if (signature != null && signature.points.isNotEmpty()) {
                    val curState = layoutStates[activeLayout.id] ?: AnchorPresenceState.PRESENT
                    val curCount = layoutConsecutiveCounts[activeLayout.id] ?: 0

                    // 16-point stratified rotating sparse probe during steady PRESENT state (0 mismatches): skips remaining 48 pixels
                    val matchesSparse =
                        if (curState == AnchorPresenceState.PRESENT && curCount == 0) {
                            val phase = sparseProbePhase
                            sparseProbePhase = (sparseProbePhase + 1) and (AnchorPresenceEvaluator.SPARSE_PROBE_PHASE_COUNT - 1)
                            AnchorPresenceEvaluator.matchesSparseProbe(signature, phase) { u, v ->
                                val globalU = layoutAnchor.srcX + u * layoutAnchor.srcWidth
                                val globalV = layoutAnchor.srcY + v * layoutAnchor.srcHeight
                                val px = (globalU * frameW).roundToInt().coerceIn(0, frameW - 1)
                                val py = (globalV * frameH).roundToInt().coerceIn(0, frameH - 1)
                                frame.getPixel(px, py)
                            }
                        } else {
                            sparseProbePhase = 0
                            false
                        }

                    val (newState, newCount) =
                        if (matchesSparse) {
                            AnchorPresenceState.PRESENT to 0
                        } else {
                            val matchRatio =
                                AnchorPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                                    val globalU = layoutAnchor.srcX + u * layoutAnchor.srcWidth
                                    val globalV = layoutAnchor.srcY + v * layoutAnchor.srcHeight
                                    val px = (globalU * frameW).roundToInt().coerceIn(0, frameW - 1)
                                    val py = (globalV * frameH).roundToInt().coerceIn(0, frameH - 1)
                                    frame.getPixel(px, py)
                                }
                            AnchorPresenceEvaluator.transitionState(curState, curCount, matchRatio, activeLayout.id)
                        }

                    if (newState != curState) {
                        anyStateChanged = true
                        if (newState == AnchorPresenceState.LOST) {
                            lostStateStartMs = SystemClock.uptimeMillis()
                        } else if (newState == AnchorPresenceState.PRESENT) {
                            lostStateStartMs = 0L
                        }
                    }
                    layoutStates[activeLayout.id] = newState
                    layoutConsecutiveCounts[activeLayout.id] = newCount

                    // When transitioning PRESENT -> LOST, save pristine frame for all cutouts from ring buffer
                    if (curState == AnchorPresenceState.PRESENT && newState == AnchorPresenceState.LOST) {
                        for (cutout in allCutouts) {
                            val delayedBmp = cutoutRingBuffers[cutout.id]?.getDelayedFrame(layoutDelayFrames)
                            if (delayedBmp != null && !delayedBmp.isRecycled) {
                                try {
                                    val copy = delayedBmp.copy(Bitmap.Config.ARGB_8888, false)
                                    val old = lastValidFrameBitmaps.put(cutout.id, copy)
                                    if (old != null && !old.isRecycled) {
                                        old.recycle()
                                    }
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to copy delayed frame for freeze on cutout ${cutout.id}", e)
                                }
                            }
                        }
                    }

                    // Scan candidate layouts while current layout is LOST using the already-captured master frame
                    if (newState == AnchorPresenceState.LOST && isAutoSwitchEligible) {
                        processCandidateScan(
                            context = context,
                            activeProfile = activeProfile,
                            excludedLayoutId = activeLayout.id,
                            srcW = srcW,
                            srcH = srcH,
                            frame = frame,
                        )
                    }
                }

                if (anyStateChanged) {
                    _presenceRevision.value++
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error in visual anchor presence evaluation loop", e)
            }
        }
    }

    private suspend fun processCandidateScan(
        context: Context,
        activeProfile: PadProfile?,
        excludedLayoutId: String?,
        srcW: Int,
        srcH: Int,
        frame: Bitmap,
    ) {
        val candidates =
            activeProfile
                ?.layouts
                ?.filter { candidate ->
                    candidate.id != excludedLayoutId &&
                        candidate.visualAnchor.enabled &&
                        candidate.visualAnchor.isCalibrated
                }.orEmpty()
        if (candidates.isEmpty()) return

        val now = SystemClock.uptimeMillis()
        val cooldownPassed = (now - lastAutoSwitchTimeMs) >= AUTO_SWITCH_COOLDOWN_MS
        if (!cooldownPassed) return

        val matchedCandidates =
            scanMatchingCandidates(candidates) { candidate ->
                evaluateCandidateLayout(candidate, srcW, srcH, frame)
            }

        if (matchedCandidates.isNotEmpty()) {
            val primary = matchedCandidates.first()
            AppLog.i(
                TAG,
                "Candidate layout '${primary.name}' (${primary.id}) matched anchor! Auto-switching layout.",
            )
            lastAutoSwitchTimeMs = now
            lostStateStartMs = 0L
            layoutStates[primary.id] = AnchorPresenceState.PRESENT
            layoutConsecutiveCounts[primary.id] = 0
            LayoutTransitionManager.switchLayout(primary.id)

            if (matchedCandidates.size >= 2) {
                notifyAnchorConflict(context, matchedCandidates)
            }
        }
    }

    @VisibleForTesting
    internal suspend fun scanMatchingCandidates(
        candidates: List<PadLayout>,
        evaluator: suspend (PadLayout) -> Boolean,
    ): List<PadLayout> {
        val matches = mutableListOf<PadLayout>()
        for (candidate in candidates) {
            if (evaluator(candidate)) {
                matches.add(candidate)
            }
        }
        return matches
    }

    @VisibleForTesting
    internal fun formatAnchorConflictToast(
        context: Context,
        layoutNames: List<String>,
    ): String =
        if (layoutNames.size == 2) {
            context.getString(R.string.toast_anchor_conflict_two, layoutNames[0], layoutNames[1])
        } else {
            val joined = layoutNames.joinToString(", ") { "\"$it\"" }
            context.getString(R.string.toast_anchor_conflict_multiple, joined)
        }

    private fun notifyAnchorConflict(
        context: Context,
        conflictingLayouts: List<PadLayout>,
    ) {
        val names =
            conflictingLayouts.map {
                it.name.ifBlank { context.getString(R.string.macropad_editor_new_layout_default_name) }
            }
        AppLog.w(TAG, "Layout anchor conflict detected between: $names")
        val message = formatAnchorConflictToast(context, names)
        DialogToastManager.show(
            message = message,
            icon = Icons.Rounded.Warning,
            isError = true,
        )
    }

    private fun evaluateCandidateLayout(
        candidate: PadLayout,
        srcW: Int,
        srcH: Int,
        frame: Bitmap,
    ): Boolean {
        val anchor = candidate.visualAnchor
        if (!anchor.enabled) return false

        val signature = candidate.visualAnchor.signature ?: return false
        if (signature.points.isEmpty()) return false

        val frameW = frame.width
        val frameH = frame.height
        if (frameW <= 0 || frameH <= 0) return false

        val isMatch =
            AnchorPresenceEvaluator.matchesWithEarlyBailout(signature) { u, v ->
                val globalU = anchor.srcX + u * anchor.srcWidth
                val globalV = anchor.srcY + v * anchor.srcHeight
                val px = (globalU * frameW).roundToInt().coerceIn(0, frameW - 1)
                val py = (globalV * frameH).roundToInt().coerceIn(0, frameH - 1)
                frame.getPixel(px, py)
            }

        AppLog.d(
            TAG,
            "Candidate layout '${candidate.name}' (${candidate.id}) matched=$isMatch via early bailout evaluation",
        )
        return isMatch
    }

    /**
     * Checks whether the anchor for the layout [layoutId] is currently flagged as lost.
     */
    fun isLayoutAnchorLost(layoutId: String): Boolean = layoutStates[layoutId] == AnchorPresenceState.LOST

    /**
     * Retrieves the current presence state for the layout [layoutId], or null if not evaluated.
     */
    fun getLayoutPresenceState(layoutId: String): AnchorPresenceState? = layoutStates[layoutId]

    /**
     * Retrieves the delayed live frame bitmap for [cutoutId] if stream delay is configured.
     * Returns null if delay is 0 or no buffered frames are available.
     */
    fun getDelayedFrame(
        cutoutId: String,
        delayFrames: Int,
    ): Bitmap? {
        if (delayFrames <= 0) return null
        return cutoutRingBuffers[cutoutId]?.getDelayedFrame(delayFrames)
    }

    /**
     * Retrieves the most recent valid frozen frame bitmap for [cutoutId].
     * Prioritizes the high-resolution frame saved from the ring buffer upon absence.
     */
    fun getFrozenFrame(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        lastValidFrameBitmaps[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            lastValidFrameBitmaps.remove(cutoutId)
        }
        CutoutMaskManager.getFreezeFrame(context, cutoutId)?.let { refFrame ->
            if (!refFrame.isRecycled) return refFrame
        }
        return null
    }

    /**
     * Clears presence state and cached frames for [cutoutId].
     */
    fun clearCutout(cutoutId: String) {
        cutoutRingBuffers.remove(cutoutId)?.recycle()
        lastValidFrameBitmaps.remove(cutoutId)?.let {
            if (!it.isRecycled) it.recycle()
        }
    }

    /**
     * Clears presence state for layout [layoutId].
     */
    fun clearLayout(layoutId: String) {
        lostStateStartMs = 0L
        sparseProbePhase = 0
        activeSmoother?.get()?.setFrozen(false)
        layoutStates.remove(layoutId)
        layoutConsecutiveCounts.remove(layoutId)
    }
}
