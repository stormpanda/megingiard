package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.macropad.LayoutTransitionManager
import com.stormpanda.megingiard.macropad.MAX_LAYOUT_STREAM_DELAY_FRAMES
import com.stormpanda.megingiard.macropad.MIN_LAYOUT_STREAM_DELAY_FRAMES
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "HudPresenceManager"

private const val PRESENCE_CHECK_INTERVAL_ACTIVE_MS = 16L // ~60 Hz (1-frame instant cutscene detection)
private const val PRESENCE_CHECK_INTERVAL_LOST_MS = 33L // ~30 Hz prompt recovery when HUD returns
private const val AUTO_SWITCH_COOLDOWN_MS = 500L
private const val DEFAULT_SOURCE_WIDTH = 1920
private const val DEFAULT_SOURCE_HEIGHT = 1080

/**
 * Singleton manager coordinating real-time HUD presence detection and freeze-frame caching.
 *
 * Evaluates the active layout's visual reference anchor at 60 Hz to determine presence state.
 * When the anchor signature is lost (e.g. cutscene, inventory menu), all cutouts in the layout
 * simultaneously freeze on their pristine delayed frames retrieved from zero-allocation ring buffers.
 * Enforcing stream delay >= 1 frame whenever visual anchoring is enabled eliminates the need for
 * periodic background live frame capture.
 */
object HudPresenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var appContext: Context? = null

    private val layoutStates = ConcurrentHashMap<String, HudPresenceState>()
    private val layoutConsecutiveCounts = ConcurrentHashMap<String, Int>()
    private val lastValidFrameBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val cutoutRingBuffers = ConcurrentHashMap<String, CutoutFrameRingBuffer>()

    @Volatile
    private var reusableCropBitmap: Bitmap? = null

    @Volatile
    private var reusableCandidateCropBitmap: Bitmap? = null

    private var candidateScanIndex = 0
    private var lastAutoSwitchTimeMs = 0L

    private val _presenceRevision = MutableStateFlow(0)
    val presenceRevision: StateFlow<Int> = _presenceRevision.asStateFlow()

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

    @Synchronized
    private fun updateMonitoringLoop(
        isCapturing: Boolean,
        layout: PadLayout?,
        profile: PadProfile?,
        viewMode: CompanionViewMode,
    ) {
        val hasLayoutAnchor = layout?.visualAnchor?.enabled == true
        val isAutoLayoutSwitching = viewMode == CompanionViewMode.AUTO && profile?.autoLayoutSwitching == true
        val hasAnyAnchoredLayout = profile?.layouts?.any { it.visualAnchor.enabled } == true
        val shouldMonitor = isCapturing && (hasLayoutAnchor || (isAutoLayoutSwitching && hasAnyAnchoredLayout))

        if (shouldMonitor) {
            if (monitorJob?.isActive != true) {
                AppLog.i(TAG, "Starting HUD presence monitoring loop (active 60 Hz / recover 30 Hz)")
                monitorJob = scope.launch { runMonitoringLoop() }
            }
        } else {
            if (monitorJob?.isActive == true) {
                AppLog.i(TAG, "Stopping HUD presence monitoring loop")
                monitorJob?.cancel()
                monitorJob = null
                reusableCropBitmap?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                reusableCropBitmap = null
                reusableCandidateCropBitmap?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                reusableCandidateCropBitmap = null
                candidateScanIndex = 0
                lastValidFrameBitmaps.values.forEach { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                lastValidFrameBitmaps.clear()
                layoutStates.clear()
                layoutConsecutiveCounts.clear()
                cutoutRingBuffers.values.forEach { it.recycle() }
                cutoutRingBuffers.clear()
            }
        }
    }

    private suspend fun runMonitoringLoop() {
        while (scope.isActive) {
            val context = appContext ?: continue
            val activeLayout = MacroPadState.activeLayout.value
            val layoutAnchor = activeLayout?.visualAnchor?.takeIf { it.enabled }
            val activeProfile = MacroPadState.activeProfile.value
            val viewMode = AppStateManager.companionViewMode.value
            val isAutoSwitchEligible =
                viewMode == CompanionViewMode.AUTO &&
                    activeProfile?.autoLayoutSwitching == true

            if (activeLayout == null || layoutAnchor == null) {
                if (isAutoSwitchEligible) {
                    val candidates =
                        activeProfile
                            ?.layouts
                            ?.filter { candidate ->
                                candidate.id != activeLayout?.id &&
                                    candidate.visualAnchor.enabled &&
                                    CutoutMaskManager.isLayoutAnchorCalibrated(context, candidate.id)
                            }.orEmpty()
                    if (candidates.isNotEmpty()) {
                        val candidate = candidates[candidateScanIndex % candidates.size]
                        candidateScanIndex++
                        val srcW = ScreenCaptureManager.captureSourceWidth.value.let { if (it > 0) it else DEFAULT_SOURCE_WIDTH }
                        val srcH = ScreenCaptureManager.captureSourceHeight.value.let { if (it > 0) it else DEFAULT_SOURCE_HEIGHT }
                        val now = SystemClock.uptimeMillis()
                        val cooldownPassed = (now - lastAutoSwitchTimeMs) >= AUTO_SWITCH_COOLDOWN_MS
                        if (cooldownPassed && evaluateCandidateLayout(context, candidate, srcW, srcH)) {
                            AppLog.i(TAG, "Candidate layout '${candidate.name}' (${candidate.id}) matched anchor! Auto-switching layout.")
                            lastAutoSwitchTimeMs = now
                            layoutStates[candidate.id] = HudPresenceState.PRESENT
                            layoutConsecutiveCounts[candidate.id] = 0
                            candidateScanIndex = 0
                            LayoutTransitionManager.switchLayout(candidate.id)
                        }
                    }
                }
                delay(PRESENCE_CHECK_INTERVAL_LOST_MS)
                continue
            }

            val allCutouts = ScreenCaptureManager.cutouts.value
            val activeCutoutIds = allCutouts.map { it.id }.toSet()
            val staleCutoutIds = cutoutRingBuffers.keys - activeCutoutIds
            for (staleId in staleCutoutIds) {
                cutoutRingBuffers.remove(staleId)?.recycle()
                lastValidFrameBitmaps.remove(staleId)?.let { if (!it.isRecycled) it.recycle() }
            }

            // 60 Hz during gameplay; 30 Hz when lost
            val isCurrentLost = layoutStates[activeLayout.id] == HudPresenceState.LOST
            val checkInterval = if (!isCurrentLost) PRESENCE_CHECK_INTERVAL_ACTIVE_MS else PRESENCE_CHECK_INTERVAL_LOST_MS
            delay(checkInterval)

            val srcW = ScreenCaptureManager.captureSourceWidth.value.let { if (it > 0) it else DEFAULT_SOURCE_WIDTH }
            val srcH = ScreenCaptureManager.captureSourceHeight.value.let { if (it > 0) it else DEFAULT_SOURCE_HEIGHT }

            var minNormX = layoutAnchor.srcX
            var minNormY = layoutAnchor.srcY
            var maxNormX = layoutAnchor.srcX + layoutAnchor.srcWidth
            var maxNormY = layoutAnchor.srcY + layoutAnchor.srcHeight

            val layoutDelayFrames =
                layoutAnchor.streamDelayFrames.coerceIn(MIN_LAYOUT_STREAM_DELAY_FRAMES, MAX_LAYOUT_STREAM_DELAY_FRAMES)

            for (cutout in allCutouts) {
                minNormX = minOf(minNormX, cutout.srcX)
                minNormY = minOf(minNormY, cutout.srcY)
                maxNormX = maxOf(maxNormX, cutout.srcX + cutout.srcWidth)
                maxNormY = maxOf(maxNormY, cutout.srcY + cutout.srcHeight)
            }

            val cropLeft = (minNormX * srcW).roundToInt().coerceIn(0, srcW - 1)
            val cropTop = (minNormY * srcH).roundToInt().coerceIn(0, srcH - 1)
            val cropRight = (maxNormX * srcW).roundToInt().coerceIn(cropLeft + 1, srcW)
            val cropBottom = (maxNormY * srcH).roundToInt().coerceIn(cropTop + 1, srcH)
            val cropW = cropRight - cropLeft
            val cropH = cropBottom - cropTop
            val cropRect = Rect(cropLeft, cropTop, cropRight, cropBottom)

            var reusable = reusableCropBitmap
            if (reusable == null || reusable.width != cropW || reusable.height != cropH || reusable.isRecycled) {
                if (reusable != null && !reusable.isRecycled) {
                    reusable.recycle()
                }
                reusable =
                    try {
                        Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                    } catch (e: OutOfMemoryError) {
                        AppLog.e(TAG, "OOM allocating reusable crop bitmap (${cropW}x$cropH)", e)
                        null
                    }
                reusableCropBitmap = reusable
            }

            val frame = MirrorFrameSampler.captureCrop(cropRect, reusableBitmap = reusable) ?: continue
            try {
                val frameW = frame.width
                val frameH = frame.height
                if (frameW <= 0 || frameH <= 0) continue

                var anyStateChanged = false

                for (cutout in allCutouts) {
                    val cX = (cutout.srcX * srcW).roundToInt().coerceIn(0, srcW - 1)
                    val cY = (cutout.srcY * srcH).roundToInt().coerceIn(0, srcH - 1)
                    val cRight = ((cutout.srcX + cutout.srcWidth) * srcW).roundToInt().coerceIn(cX + 1, srcW)
                    val cBottom = ((cutout.srcY + cutout.srcHeight) * srcH).roundToInt().coerceIn(cY + 1, srcH)
                    val cW = cRight - cX
                    val cH = cBottom - cY

                    val localCropX = (cX - cropLeft).coerceIn(0, frameW - 1)
                    val localCropY = (cY - cropTop).coerceIn(0, frameH - 1)
                    val safeCropW = minOf(cW, frameW - localCropX)
                    val safeCropH = minOf(cH, frameH - localCropY)

                    if (layoutDelayFrames > 0 && safeCropW > 0 && safeCropH > 0) {
                        var ring = cutoutRingBuffers[cutout.id]
                        if (ring == null || ring.width != cW || ring.height != cH) {
                            ring?.recycle()
                            ring = CutoutFrameRingBuffer(cW, cH, MAX_LAYOUT_STREAM_DELAY_FRAMES + 2)
                            cutoutRingBuffers[cutout.id] = ring
                        }
                        ring.pushFrame(frame, localCropX, localCropY)
                    }
                }

                val signature = CutoutMaskManager.getLayoutAnchorSignature(context, activeLayout.id)
                if (signature != null && signature.points.isNotEmpty()) {
                    val matchRatio =
                        HudPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                            val globalU = layoutAnchor.srcX + u * layoutAnchor.srcWidth
                            val globalV = layoutAnchor.srcY + v * layoutAnchor.srcHeight
                            val px = (globalU * srcW).roundToInt().coerceIn(0, srcW - 1)
                            val py = (globalV * srcH).roundToInt().coerceIn(0, srcH - 1)
                            val localX = (px - cropLeft).coerceIn(0, frameW - 1)
                            val localY = (py - cropTop).coerceIn(0, frameH - 1)
                            frame.getPixel(localX, localY)
                        }

                    val curState = layoutStates[activeLayout.id] ?: HudPresenceState.PRESENT
                    val curCount = layoutConsecutiveCounts[activeLayout.id] ?: 0
                    val (newState, newCount) =
                        HudPresenceEvaluator.transitionState(curState, curCount, matchRatio, activeLayout.id)

                    if (newState != curState) {
                        anyStateChanged = true
                    }
                    layoutStates[activeLayout.id] = newState
                    layoutConsecutiveCounts[activeLayout.id] = newCount

                    // When transitioning PRESENT -> LOST, save pristine frame for all cutouts from ring buffer
                    if (curState == HudPresenceState.PRESENT && newState == HudPresenceState.LOST) {
                        for (cutout in allCutouts) {
                            val delayedBmp = cutoutRingBuffers[cutout.id]?.getDelayedFrame(layoutDelayFrames)
                            if (delayedBmp != null && !delayedBmp.isRecycled) {
                                try {
                                    val freezeCopy = delayedBmp.copy(Bitmap.Config.ARGB_8888, false)
                                    val oldCrop = lastValidFrameBitmaps.put(cutout.id, freezeCopy)
                                    if (oldCrop != null && !oldCrop.isRecycled) {
                                        oldCrop.recycle()
                                    }
                                    CutoutMaskManager.saveFreezeFrame(context, cutout.id, freezeCopy)
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to copy delayed frame for freeze on cutout ${cutout.id}", e)
                                }
                            }
                        }
                    }

                    // When LOST, check candidates if profile auto-layout switching is active
                    if (newState == HudPresenceState.LOST && isAutoSwitchEligible) {
                        val candidates =
                            activeProfile
                                ?.layouts
                                ?.filter { candidate ->
                                    candidate.id != activeLayout.id &&
                                        candidate.visualAnchor.enabled &&
                                        CutoutMaskManager.isLayoutAnchorCalibrated(context, candidate.id)
                                }.orEmpty()
                        if (candidates.isNotEmpty()) {
                            val candidate = candidates[candidateScanIndex % candidates.size]
                            candidateScanIndex++
                            val now = SystemClock.uptimeMillis()
                            val cooldownPassed = (now - lastAutoSwitchTimeMs) >= AUTO_SWITCH_COOLDOWN_MS
                            if (cooldownPassed && evaluateCandidateLayout(context, candidate, srcW, srcH)) {
                                AppLog.i(
                                    TAG,
                                    "Candidate layout '${candidate.name}' (${candidate.id}) matched anchor! Auto-switching layout.",
                                )
                                lastAutoSwitchTimeMs = now
                                layoutStates[candidate.id] = HudPresenceState.PRESENT
                                layoutConsecutiveCounts[candidate.id] = 0
                                candidateScanIndex = 0
                                LayoutTransitionManager.switchLayout(candidate.id)
                            }
                        }
                    }
                }

                if (anyStateChanged) {
                    _presenceRevision.value++
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error in HUD presence evaluation loop", e)
            } finally {
                if (frame != reusableCropBitmap && frame != ScreenCaptureManager.frozenBitmap.value) {
                    frame.recycle()
                }
            }
        }
    }

    private suspend fun evaluateCandidateLayout(
        context: Context,
        candidate: PadLayout,
        srcW: Int,
        srcH: Int,
    ): Boolean {
        val anchor = candidate.visualAnchor
        if (!anchor.enabled) return false

        val cropLeft = (anchor.srcX * srcW).roundToInt().coerceIn(0, srcW - 1)
        val cropTop = (anchor.srcY * srcH).roundToInt().coerceIn(0, srcH - 1)
        val cropRight = ((anchor.srcX + anchor.srcWidth) * srcW).roundToInt().coerceIn(cropLeft + 1, srcW)
        val cropBottom = ((anchor.srcY + anchor.srcHeight) * srcH).roundToInt().coerceIn(cropTop + 1, srcH)
        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop
        if (cropW <= 0 || cropH <= 0) return false

        val cropRect = Rect(cropLeft, cropTop, cropRight, cropBottom)

        var reusable = reusableCandidateCropBitmap
        if (reusable == null || reusable.width != cropW || reusable.height != cropH || reusable.isRecycled) {
            if (reusable != null && !reusable.isRecycled) {
                reusable.recycle()
            }
            reusable =
                try {
                    Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    AppLog.e(TAG, "OOM allocating candidate crop bitmap (${cropW}x$cropH)", e)
                    null
                }
            reusableCandidateCropBitmap = reusable
        }

        val frame = MirrorFrameSampler.captureCrop(cropRect, reusableBitmap = reusable) ?: return false
        try {
            val frameW = frame.width
            val frameH = frame.height
            if (frameW <= 0 || frameH <= 0) return false

            val signature = CutoutMaskManager.getLayoutAnchorSignature(context, candidate.id) ?: return false
            if (signature.points.isEmpty()) return false

            val matchRatio =
                HudPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                    val globalU = anchor.srcX + u * anchor.srcWidth
                    val globalV = anchor.srcY + v * anchor.srcHeight
                    val px = (globalU * srcW).roundToInt().coerceIn(0, srcW - 1)
                    val py = (globalV * srcH).roundToInt().coerceIn(0, srcH - 1)
                    val localX = (px - cropLeft).coerceIn(0, frameW - 1)
                    val localY = (py - cropTop).coerceIn(0, frameH - 1)
                    frame.getPixel(localX, localY)
                }

            return matchRatio >= HudPresenceEvaluator.MATCH_THRESHOLD_PRESENT
        } finally {
            if (frame != reusableCandidateCropBitmap && frame != ScreenCaptureManager.frozenBitmap.value) {
                frame.recycle()
            }
        }
    }

    /**
     * Checks whether the HUD for the layout [layoutId] is currently flagged as lost.
     */
    fun isLayoutHudLost(layoutId: String): Boolean = layoutStates[layoutId] == HudPresenceState.LOST

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
     * Retrieves the most recent valid HUD frame bitmap for [cutoutId].
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
        layoutStates.remove(layoutId)
        layoutConsecutiveCounts.remove(layoutId)
    }
}
