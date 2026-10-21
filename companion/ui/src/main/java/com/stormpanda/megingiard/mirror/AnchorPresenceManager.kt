package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
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
 * Singleton manager coordinating real-time visual anchor presence detection and GPU stream freezing.
 *
 * Evaluates the active layout's visual reference anchor at 60 Hz on the GL thread to determine presence state.
 */
object AnchorPresenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var appContext: Context? = null

    private val layoutStates = ConcurrentHashMap<String, AnchorPresenceState>()
    private val layoutConsecutiveCounts = ConcurrentHashMap<String, Int>()
    private val lastValidFrameBitmaps = ConcurrentHashMap<String, Bitmap>()

    @VisibleForTesting
    internal val lastValidFrameCount: Int
        get() = lastValidFrameBitmaps.size

    private var lastAutoSwitchTimeMs = 0L
    private var lostStateStartMs = 0L

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

    internal fun ensureSmootherUnfrozen() {
        activeSmoother?.get()?.setFrozen(false)
    }

    @VisibleForTesting
    internal fun isMonitoringSuspended(): Boolean =
        VisualAutoTuneCoordinator.uiState.value.isCalibrating ||
            AnchorTestCoordinator.uiState.value.isTesting ||
            AppStateManager.isEditorActive.value ||
            AppStateManager.isViewportEditActive.value ||
            AppStateManager.activePrimaryModal.value != null

    private fun updateSmootherTracking() {
        val smoother = activeSmoother?.get() ?: return
        val activeLayout = MacroPadState.activeLayout.value
        val anchor = activeLayout?.visualAnchor
        if (activeLayout != null && anchor != null && anchor.enabled && anchor.isCalibrated) {
            smoother.updateStreamDelay(anchor.streamDelayFrames)
            smoother.setFrozen(false)
            smoother.setAnchorTracking(activeLayout.id, anchor) { layoutId, matchRatio ->
                onHardwareAnchorEvaluated(layoutId, matchRatio)
            }
        } else {
            smoother.updateStreamDelay(0)
            smoother.setAnchorTracking("", null, null)
            smoother.setFrozen(false)
        }
    }

    @VisibleForTesting
    internal fun onHardwareAnchorEvaluated(
        layoutId: String,
        matchRatio: Float,
    ) {
        if (isMonitoringSuspended()) {
            activeSmoother?.get()?.setFrozen(false)
            return
        }
        val activeLayout = MacroPadState.activeLayout.value ?: return
        if (layoutId != activeLayout.id) return

        val curState = layoutStates[layoutId] ?: AnchorPresenceState.PRESENT
        val curCount = layoutConsecutiveCounts[layoutId] ?: 0
        val (newState, newCount) =
            AnchorPresenceEvaluator.transitionState(
                currentState = curState,
                consecutiveCount = curCount,
                matchRatio = matchRatio,
                cutoutId = layoutId,
                presentThreshold = activeLayout.visualAnchor.effectiveMatchThreshold,
            )

        if (newState != curState) {
            if (newState == AnchorPresenceState.LOST) {
                lostStateStartMs = SystemClock.uptimeMillis()
                val cutouts = ScreenCaptureManager.cutouts.value.ifEmpty { activeLayout.mirrorCutouts }
                val shouldFreeze =
                    activeLayout.visualAnchor.hasEffect(CutoutLostAnchorEffect.FREEZE) &&
                        cutouts.isNotEmpty()
                if (shouldFreeze) {
                    activeSmoother?.get()?.setFrozen(true)
                    captureOneShotFrozenFrame(activeLayout)
                } else {
                    activeSmoother?.get()?.setFrozen(false)
                }
            } else if (newState == AnchorPresenceState.PRESENT) {
                lostStateStartMs = 0L
                activeSmoother?.get()?.setFrozen(false)
            }
            _presenceRevision.value++
        }
        layoutStates[layoutId] = newState
        layoutConsecutiveCounts[layoutId] = newCount
    }

    private fun captureOneShotFrozenFrame(layout: PadLayout) {
        scope.launch(Dispatchers.Main.immediate) {
            try {
                val cutouts = ScreenCaptureManager.cutouts.value.ifEmpty { layout.mirrorCutouts }
                if (cutouts.isNotEmpty()) {
                    val fullFrame = MirrorFrameSampler.captureFullFrame()
                    if (fullFrame != null && fullFrame.width > 0 && fullFrame.height > 0) {
                        val fw = fullFrame.width
                        val fh = fullFrame.height
                        for (cutout in cutouts) {
                            val effectiveCrop = InteractiveCutoutController.getEffectiveCrop(cutout)
                            val cx = (effectiveCrop.srcX * fw).roundToInt().coerceIn(0, fw - 1)
                            val cy = (effectiveCrop.srcY * fh).roundToInt().coerceIn(0, fh - 1)
                            val cw = (effectiveCrop.srcWidth * fw).roundToInt().coerceIn(1, fw - cx)
                            val ch = (effectiveCrop.srcHeight * fh).roundToInt().coerceIn(1, fh - cy)
                            val cropBmp = Bitmap.createBitmap(fullFrame, cx, cy, cw, ch)
                            val old = lastValidFrameBitmaps.put(cutout.id, cropBmp)
                            if (old != null && old !== cropBmp && !old.isRecycled) {
                                old.recycle()
                            }
                        }
                        _presenceRevision.value++
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to capture one-shot frozen frame: ${e.message}")
            } finally {
                // Always unfreeze smoother so TextureView continues updating for candidate layout scanning
                activeSmoother?.get()?.setFrozen(false)
            }
        }
    }

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
        AppLog.d(TAG, "Resetting anchor presence state, clearing buffers, and unfreezing smoother")
        lostStateStartMs = 0L
        lastAutoSwitchTimeMs = 0L
        activeSmoother?.get()?.setFrozen(false)
        layoutStates.clear()
        layoutConsecutiveCounts.clear()
        val keys = lastValidFrameBitmaps.keys.toList()
        for (k in keys) {
            lastValidFrameBitmaps.remove(k)?.let { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }

    private suspend fun runMonitoringLoop() {
        while (scope.isActive) {
            val context = appContext
            if (context == null) {
                delay(PRESENCE_CHECK_INTERVAL_LOST_FAST_MS)
                continue
            }

            // Suspend presence monitoring and layout auto-switching during calibration, testing, or while editor/modal is open
            if (isMonitoringSuspended()) {
                activeSmoother?.get()?.setFrozen(false)
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

            // Presence is sampled in hardware at 60 Hz directly on the GL thread with 0 CPU readbacks.
            // While the current layout anchor is LOST, candidate layouts are polled to support auto layout switching.
            if (isCurrentLost && isAutoSwitchEligible) {
                val frame = MirrorFrameSampler.captureFullFrame()
                if (frame != null && frame.width > 0 && frame.height > 0) {
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
        }
    }

    @VisibleForTesting
    internal suspend fun processCandidateScan(
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
        val cooldownPassed = lastAutoSwitchTimeMs == 0L || (now - lastAutoSwitchTimeMs) >= AUTO_SWITCH_COOLDOWN_MS
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
            activeSmoother?.get()?.setFrozen(false)
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

        val aX = (anchor.srcX * frameW).roundToInt().coerceIn(0, frameW - 1)
        val aY = (anchor.srcY * frameH).roundToInt().coerceIn(0, frameH - 1)
        val aRight = ((anchor.srcX + anchor.srcWidth) * frameW).roundToInt().coerceIn(aX + 1, frameW)
        val aBottom = ((anchor.srcY + anchor.srcHeight) * frameH).roundToInt().coerceIn(aY + 1, frameH)
        val cropW = aRight - aX
        val cropH = aBottom - aY

        val isMatch =
            AnchorPresenceEvaluator.matchesWithEarlyBailout(
                signature = signature,
                threshold = candidate.visualAnchor.effectiveMatchThreshold,
            ) { u, v ->
                val px = (aX + (u * cropW).toInt()).coerceIn(0, frameW - 1)
                val py = (aY + (v * cropH).toInt()).coerceIn(0, frameH - 1)
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
     * Retrieves the frozen frame bitmap for cutout [cutoutId].
     * Prefers the live last-valid frame captured immediately prior to anchor loss,
     * falling back to the persisted calibration reference frame if none is in memory.
     */
    fun getFrozenFrame(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        lastValidFrameBitmaps[cutoutId]?.let { bmp ->
            if (!bmp.isRecycled) return bmp
            lastValidFrameBitmaps.remove(cutoutId)
        }
        return CutoutMaskManager.getFreezeFrame(context, cutoutId)
    }

    @VisibleForTesting
    internal fun setFrozenFrameForTesting(
        cutoutId: String,
        bitmap: Bitmap,
    ) {
        val old = lastValidFrameBitmaps.put(cutoutId, bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    /**
     * Clears presence state for layout [layoutId].
     */
    fun clearLayout(layoutId: String) {
        lostStateStartMs = 0L
        activeSmoother?.get()?.setFrozen(false)
        layoutStates.remove(layoutId)
        layoutConsecutiveCounts.remove(layoutId)
    }

    /**
     * Clears cached frozen frame bitmap for cutout [cutoutId].
     */
    fun clearCutout(cutoutId: String) {
        lastValidFrameBitmaps.remove(cutoutId)?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}
