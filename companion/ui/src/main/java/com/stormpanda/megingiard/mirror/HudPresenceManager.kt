package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MacroPadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "HudPresenceManager"

private const val PRESENCE_CHECK_INTERVAL_MS = 100L // 10 Hz (prompt detection on cutscene start)
private const val LIVE_FRAME_BUFFER_INTERVAL_MS = 200L // 5 Hz live frame updates for dynamic cutouts
private const val DEFAULT_SOURCE_WIDTH = 1920
private const val DEFAULT_SOURCE_HEIGHT = 1080

/**
 * Singleton manager coordinating real-time HUD presence detection and freeze-frame caching.
 *
 * Periodically samples top-screen video frames at 10 Hz, evaluates anchor signatures of active
 * cutouts, and smoothly manages the transition to frozen frames during cutscenes or in-game menus.
 */
object HudPresenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var appContext: Context? = null

    private val cutoutStates = ConcurrentHashMap<String, HudPresenceState>()
    private val cutoutConsecutiveCounts = ConcurrentHashMap<String, Int>()
    private val lastValidFrameBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val lastLiveFrameCaptureTimes = ConcurrentHashMap<String, Long>()

    @Volatile
    private var reusableFrameBitmap: Bitmap? = null

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
            ScreenCaptureManager.isCapturing.collect { capturing ->
                updateMonitoringLoop(capturing, ScreenCaptureManager.cutouts.value)
            }
        }
        scope.launch {
            ScreenCaptureManager.cutouts.collect { cutouts ->
                updateMonitoringLoop(ScreenCaptureManager.isCapturing.value, cutouts)
            }
        }
    }

    @Synchronized
    private fun updateMonitoringLoop(
        isCapturing: Boolean,
        cutouts: List<ScreenCutout>,
    ) {
        val shouldMonitor = isCapturing && cutouts.any { it.freezeOnHudLoss }
        if (shouldMonitor) {
            if (monitorJob?.isActive != true) {
                AppLog.i(TAG, "Starting HUD presence monitoring loop (10 Hz)")
                monitorJob = scope.launch { runMonitoringLoop() }
            }
        } else {
            if (monitorJob?.isActive == true) {
                AppLog.i(TAG, "Stopping HUD presence monitoring loop")
                monitorJob?.cancel()
                monitorJob = null
                reusableFrameBitmap?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                reusableFrameBitmap = null
                lastValidFrameBitmaps.values.forEach { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                lastValidFrameBitmaps.clear()
                lastLiveFrameCaptureTimes.clear()
                cutoutStates.clear()
                cutoutConsecutiveCounts.clear()
            }
        }
    }

    private suspend fun runMonitoringLoop() {
        while (scope.isActive) {
            delay(PRESENCE_CHECK_INTERVAL_MS)

            val context = appContext ?: continue
            val currentCutouts = ScreenCaptureManager.cutouts.value.filter { it.freezeOnHudLoss }
            if (currentCutouts.isEmpty()) continue

            val srcW = ScreenCaptureManager.captureSourceWidth.value.let { if (it > 0) it else DEFAULT_SOURCE_WIDTH }
            val srcH = ScreenCaptureManager.captureSourceHeight.value.let { if (it > 0) it else DEFAULT_SOURCE_HEIGHT }

            var reusable = reusableFrameBitmap
            if (reusable == null || reusable.width != srcW || reusable.height != srcH || reusable.isRecycled) {
                if (reusable != null && !reusable.isRecycled) {
                    reusable.recycle()
                }
                reusable =
                    try {
                        Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
                    } catch (e: OutOfMemoryError) {
                        AppLog.e(TAG, "OOM allocating reusable frame bitmap (${srcW}x$srcH)", e)
                        null
                    }
                reusableFrameBitmap = reusable
            }

            val frame = MirrorFrameSampler.captureFrame(srcW, srcH, reusableBitmap = reusable) ?: continue
            try {
                val frameW = frame.width
                val frameH = frame.height
                if (frameW <= 0 || frameH <= 0) continue

                var anyStateChanged = false

                for (cutout in currentCutouts) {
                    val targetCutoutId = (if (cutout.customAnchorEnabled) cutout.anchorCutoutId else null) ?: cutout.id
                    val signature = CutoutMaskManager.getAnchorSignature(context, targetCutoutId) ?: continue
                    if (signature.points.isEmpty()) continue

                    val allCutouts = ScreenCaptureManager.cutouts.value
                    val anchorCrop = cutout.getEffectiveAnchorCrop(allCutouts)
                    val matchRatio =
                        HudPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                            val globalU = anchorCrop.x + u * anchorCrop.width
                            val globalV = anchorCrop.y + v * anchorCrop.height
                            val px = (globalU * frameW).roundToInt().coerceIn(0, frameW - 1)
                            val py = (globalV * frameH).roundToInt().coerceIn(0, frameH - 1)
                            frame.getPixel(px, py)
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

                    // Frame capture for freeze:
                    val hasCachedFrame = lastValidFrameBitmaps.containsKey(cutout.id)
                    val isConfidentPresent = matchRatio >= HudPresenceEvaluator.MATCH_THRESHOLD_PRESENT
                    if (newState == HudPresenceState.PRESENT && (!hasCachedFrame || isConfidentPresent)) {
                        val cX = (cutout.srcX * frameW).roundToInt().coerceIn(0, frameW - 1)
                        val cY = (cutout.srcY * frameH).roundToInt().coerceIn(0, frameH - 1)
                        val cRight = ((cutout.srcX + cutout.srcWidth) * frameW).roundToInt().coerceIn(cX + 1, frameW)
                        val cBottom = ((cutout.srcY + cutout.srcHeight) * frameH).roundToInt().coerceIn(cY + 1, frameH)
                        val cW = cRight - cX
                        val cH = cBottom - cY

                        if (cutout.customAnchorEnabled) {
                            // Dynamic cutout: buffer live frames continuously at ~5 Hz
                            val now = SystemClock.elapsedRealtime()
                            val lastCapture = lastLiveFrameCaptureTimes[cutout.id] ?: 0L
                            if (!hasCachedFrame || now - lastCapture >= LIVE_FRAME_BUFFER_INTERVAL_MS) {
                                lastLiveFrameCaptureTimes[cutout.id] = now
                                try {
                                    val crop = Bitmap.createBitmap(frame, cX, cY, cW, cH)
                                    val oldCrop = lastValidFrameBitmaps.put(cutout.id, crop)
                                    if (oldCrop != null && !oldCrop.isRecycled) {
                                        oldCrop.recycle()
                                    }
                                    val existingFreeze = CutoutMaskManager.getFreezeFrame(context, cutout.id)
                                    if (existingFreeze == null || existingFreeze.width != cW || existingFreeze.height != cH) {
                                        CutoutMaskManager.saveFreezeFrame(context, cutout.id, crop)
                                    }
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to capture live crop for dynamic cutout ${cutout.id}", e)
                                }
                            }
                        } else {
                            // Static HUD: capture once if no calibrated frame on disk or if resolution is outdated
                            val existingFreeze = CutoutMaskManager.getFreezeFrame(context, cutout.id)
                            if (existingFreeze == null || existingFreeze.width != cW || existingFreeze.height != cH) {
                                try {
                                    val crop = Bitmap.createBitmap(frame, cX, cY, cW, cH)
                                    CutoutMaskManager.saveFreezeFrame(context, cutout.id, crop)
                                    val oldCrop = lastValidFrameBitmaps.put(cutout.id, crop)
                                    if (oldCrop != null && !oldCrop.isRecycled) {
                                        oldCrop.recycle()
                                    }
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to capture live valid crop for cutout ${cutout.id}", e)
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
                if (frame != reusableFrameBitmap && frame != ScreenCaptureManager.frozenBitmap.value) {
                    frame.recycle()
                }
            }
        }
    }

    /**
     * Checks whether the HUD for [cutoutId] is currently flagged as lost.
     */
    fun isCutoutHudLost(cutoutId: String): Boolean = cutoutStates[cutoutId] == HudPresenceState.LOST

    /**
     * Retrieves the most recent valid HUD frame bitmap for [cutoutId].
     * For dynamic cutouts with custom anchors, prioritizes the live buffered frame.
     * For static HUD cutouts, prioritizes the high-resolution calibrated reference frame.
     */
    fun getFrozenFrame(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        val cutout =
            ScreenCaptureManager.cutouts.value.find { it.id == cutoutId }
                ?: MacroPadState.activeLayout.value
                    ?.mirrorCutouts
                    ?.find { it.id == cutoutId }
        if (cutout?.customAnchorEnabled == true) {
            lastValidFrameBitmaps[cutoutId]?.let { cached ->
                if (!cached.isRecycled) return cached
                lastValidFrameBitmaps.remove(cutoutId)
            }
            CutoutMaskManager.getFreezeFrame(context, cutoutId)?.let { refFrame ->
                if (!refFrame.isRecycled) return refFrame
            }
        } else {
            CutoutMaskManager.getFreezeFrame(context, cutoutId)?.let { refFrame ->
                if (!refFrame.isRecycled) return refFrame
            }
            lastValidFrameBitmaps[cutoutId]?.let { cached ->
                if (!cached.isRecycled) return cached
                lastValidFrameBitmaps.remove(cutoutId)
            }
        }
        return null
    }

    /**
     * Clears presence state and cached frames for [cutoutId].
     */
    fun clearCutout(cutoutId: String) {
        cutoutStates.remove(cutoutId)
        cutoutConsecutiveCounts.remove(cutoutId)
        lastLiveFrameCaptureTimes.remove(cutoutId)
        lastValidFrameBitmaps.remove(cutoutId)?.let {
            if (!it.isRecycled) it.recycle()
        }
    }
}
