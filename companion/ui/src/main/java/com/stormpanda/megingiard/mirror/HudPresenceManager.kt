package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MAX_LAYOUT_STREAM_DELAY_FRAMES
import com.stormpanda.megingiard.macropad.MIN_LAYOUT_STREAM_DELAY_FRAMES
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
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
    private val cutoutStates = ConcurrentHashMap<String, HudPresenceState>()
    private val cutoutConsecutiveCounts = ConcurrentHashMap<String, Int>()
    private val lastValidFrameBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val cutoutRingBuffers = ConcurrentHashMap<String, CutoutFrameRingBuffer>()

    @Volatile
    private var reusableCropBitmap: Bitmap? = null

    private val _presenceRevision = MutableStateFlow(0)
    val presenceRevision: StateFlow<Int> = _presenceRevision.asStateFlow()

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
                ScreenCaptureManager.cutouts,
            ) { capturing, layout, cutouts ->
                Triple(capturing, layout, cutouts)
            }.collect { (capturing, layout, cutouts) ->
                updateMonitoringLoop(capturing, layout, cutouts)
            }
        }
    }

    @Synchronized
    private fun updateMonitoringLoop(
        isCapturing: Boolean,
        layout: PadLayout?,
        cutouts: List<ScreenCutout>,
    ) {
        val hasLayoutAnchor = layout?.visualAnchor?.enabled == true

        @Suppress("DEPRECATION")
        val hasLegacyCutoutAnchor = cutouts.any { it.freezeOnHudLoss || it.streamDelayFrames > 0 }
        val shouldMonitor = isCapturing && (hasLayoutAnchor || hasLegacyCutoutAnchor)

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
                lastValidFrameBitmaps.values.forEach { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                lastValidFrameBitmaps.clear()
                layoutStates.clear()
                layoutConsecutiveCounts.clear()
                cutoutStates.clear()
                cutoutConsecutiveCounts.clear()
                cutoutRingBuffers.values.forEach { it.recycle() }
                cutoutRingBuffers.clear()
            }
        }
    }

    private suspend fun runMonitoringLoop() {
        while (scope.isActive) {
            val context = appContext ?: continue
            val activeLayout = MacroPadState.activeLayout.value
            val allCutouts = ScreenCaptureManager.cutouts.value
            val layoutAnchor = activeLayout?.visualAnchor?.takeIf { it.enabled }
            val hasLayoutAnchor = layoutAnchor != null

            @Suppress("DEPRECATION")
            val legacyCutouts =
                if (!hasLayoutAnchor) {
                    allCutouts.filter { it.freezeOnHudLoss || it.streamDelayFrames > 0 }
                } else {
                    emptyList()
                }

            if (!hasLayoutAnchor && legacyCutouts.isEmpty()) {
                delay(PRESENCE_CHECK_INTERVAL_LOST_MS)
                continue
            }

            // 60 Hz during gameplay; 30 Hz when lost
            val isCurrentLost =
                if (hasLayoutAnchor) {
                    layoutStates[activeLayout!!.id] == HudPresenceState.LOST
                } else {
                    legacyCutouts.all { cutoutStates[it.id] == HudPresenceState.LOST }
                }
            val checkInterval = if (!isCurrentLost) PRESENCE_CHECK_INTERVAL_ACTIVE_MS else PRESENCE_CHECK_INTERVAL_LOST_MS
            delay(checkInterval)

            val srcW = ScreenCaptureManager.captureSourceWidth.value.let { if (it > 0) it else DEFAULT_SOURCE_WIDTH }
            val srcH = ScreenCaptureManager.captureSourceHeight.value.let { if (it > 0) it else DEFAULT_SOURCE_HEIGHT }

            var minNormX = 1f
            var minNormY = 1f
            var maxNormX = 0f
            var maxNormY = 0f

            val layoutDelayFrames =
                if (hasLayoutAnchor) {
                    layoutAnchor!!.streamDelayFrames.coerceIn(MIN_LAYOUT_STREAM_DELAY_FRAMES, MAX_LAYOUT_STREAM_DELAY_FRAMES)
                } else {
                    0
                }

            if (hasLayoutAnchor) {
                minNormX = minOf(minNormX, layoutAnchor!!.srcX)
                minNormY = minOf(minNormY, layoutAnchor.srcY)
                maxNormX = maxOf(maxNormX, layoutAnchor.srcX + layoutAnchor.srcWidth)
                maxNormY = maxOf(maxNormY, layoutAnchor.srcY + layoutAnchor.srcHeight)

                for (cutout in allCutouts) {
                    minNormX = minOf(minNormX, cutout.srcX)
                    minNormY = minOf(minNormY, cutout.srcY)
                    maxNormX = maxOf(maxNormX, cutout.srcX + cutout.srcWidth)
                    maxNormY = maxOf(maxNormY, cutout.srcY + cutout.srcHeight)
                }
            } else {
                for (cutout in legacyCutouts) {
                    val anchorCrop = cutout.getEffectiveAnchorCrop(allCutouts)
                    minNormX = minOf(minNormX, anchorCrop.x)
                    minNormY = minOf(minNormY, anchorCrop.y)
                    maxNormX = maxOf(maxNormX, anchorCrop.x + anchorCrop.width)
                    maxNormY = maxOf(maxNormY, anchorCrop.y + anchorCrop.height)

                    @Suppress("DEPRECATION")
                    val delayFrames = cutout.streamDelayFrames.coerceIn(0, MAX_STREAM_DELAY_FRAMES)
                    @Suppress("DEPRECATION")
                    if (delayFrames > 0 || cutout.customAnchorEnabled) {
                        minNormX = minOf(minNormX, cutout.srcX)
                        minNormY = minOf(minNormY, cutout.srcY)
                        maxNormX = maxOf(maxNormX, cutout.srcX + cutout.srcWidth)
                        maxNormY = maxOf(maxNormY, cutout.srcY + cutout.srcHeight)
                    }
                }
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

                if (hasLayoutAnchor) {
                    val monitoredCutouts = allCutouts
                    for (cutout in monitoredCutouts) {
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

                    val signature = CutoutMaskManager.getLayoutAnchorSignature(context, activeLayout!!.id)
                    if (signature != null && signature.points.isNotEmpty()) {
                        val matchRatio =
                            HudPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                                val globalU = layoutAnchor!!.srcX + u * layoutAnchor.srcWidth
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
                            for (cutout in monitoredCutouts) {
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
                    }
                } else {
                    // Legacy fallback loop
                    for (cutout in legacyCutouts) {
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

                        @Suppress("DEPRECATION")
                        val delayFrames = cutout.streamDelayFrames.coerceIn(0, MAX_STREAM_DELAY_FRAMES)
                        if (delayFrames > 0 && safeCropW > 0 && safeCropH > 0) {
                            var ring = cutoutRingBuffers[cutout.id]
                            if (ring == null || ring.width != cW || ring.height != cH) {
                                ring?.recycle()
                                ring = CutoutFrameRingBuffer(cW, cH, MAX_STREAM_DELAY_FRAMES + 2)
                                cutoutRingBuffers[cutout.id] = ring
                            }
                            ring.pushFrame(frame, localCropX, localCropY)
                        } else if (delayFrames == 0) {
                            cutoutRingBuffers.remove(cutout.id)?.recycle()
                        }

                        @Suppress("DEPRECATION")
                        if (!cutout.freezeOnHudLoss) continue

                        @Suppress("DEPRECATION")
                        val targetCutoutId = (if (cutout.customAnchorEnabled) cutout.anchorCutoutId else null) ?: cutout.id
                        val signature = CutoutMaskManager.getAnchorSignature(context, targetCutoutId) ?: continue
                        if (signature.points.isEmpty()) continue

                        val anchorCrop = cutout.getEffectiveAnchorCrop(allCutouts)
                        val matchRatio =
                            HudPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                                val globalU = anchorCrop.x + u * anchorCrop.width
                                val globalV = anchorCrop.y + v * anchorCrop.height
                                val px = (globalU * srcW).roundToInt().coerceIn(0, srcW - 1)
                                val py = (globalV * srcH).roundToInt().coerceIn(0, srcH - 1)
                                val localX = (px - cropLeft).coerceIn(0, frameW - 1)
                                val localY = (py - cropTop).coerceIn(0, frameH - 1)
                                frame.getPixel(localX, localY)
                            }

                        val curState = cutoutStates[cutout.id] ?: HudPresenceState.PRESENT
                        val curCount = cutoutConsecutiveCounts[cutout.id] ?: 0
                        val (newState, newCount) =
                            HudPresenceEvaluator.transitionState(curState, curCount, matchRatio, cutout.id)

                        if (newState != curState) {
                            anyStateChanged = true
                        }
                        cutoutStates[cutout.id] = newState
                        cutoutConsecutiveCounts[cutout.id] = newCount

                        if (curState == HudPresenceState.PRESENT && newState == HudPresenceState.LOST) {
                            if (delayFrames > 0) {
                                val delayedBmp = cutoutRingBuffers[cutout.id]?.getDelayedFrame(delayFrames)
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

    /**
     * Checks whether the HUD for the layout [layoutId] is currently flagged as lost.
     */
    fun isLayoutHudLost(layoutId: String): Boolean = layoutStates[layoutId] == HudPresenceState.LOST

    /**
     * Checks whether the HUD for [cutoutId] is currently flagged as lost.
     * When visual anchoring is active on the active layout, inherits the layout's presence state.
     */
    fun isCutoutHudLost(cutoutId: String): Boolean {
        val activeLay = MacroPadState.activeLayout.value
        if (activeLay != null && activeLay.visualAnchor.enabled) {
            return isLayoutHudLost(activeLay.id)
        }
        return cutoutStates[cutoutId] == HudPresenceState.LOST
    }

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
        cutoutStates.remove(cutoutId)
        cutoutConsecutiveCounts.remove(cutoutId)
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
