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

private const val PRESENCE_PROBE_WIDTH = 480
private const val PRESENCE_CHECK_INTERVAL_ACTIVE_MS = 33L // ~30 Hz (prompt detection on cutscene start)
private const val PRESENCE_CHECK_INTERVAL_LOST_MS = 200L // 5 Hz backoff during cutscenes/menus for battery efficiency
private const val LIVE_FRAME_BUFFER_INTERVAL_MS = 500L // 2 Hz live frame updates for dynamic cutouts
private const val DEFAULT_SOURCE_WIDTH = 1920
private const val DEFAULT_SOURCE_HEIGHT = 1080

/**
 * Singleton manager coordinating real-time HUD presence detection and freeze-frame caching.
 *
 * Periodically samples top-screen video frames using a lightweight hardware-scaled probe bitmap
 * at ~30 Hz, evaluates anchor signatures of active cutouts with ultra-low latency, and manages
 * freeze-frame capture decoupled at a lower cadence.
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
    private var reusableProbeBitmap: Bitmap? = null

    @Volatile
    private var reusableBufferBitmap: Bitmap? = null

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
                AppLog.i(TAG, "Starting HUD presence monitoring loop (adaptive 30 Hz / 5 Hz)")
                monitorJob = scope.launch { runMonitoringLoop() }
            }
        } else {
            if (monitorJob?.isActive == true) {
                AppLog.i(TAG, "Stopping HUD presence monitoring loop")
                monitorJob?.cancel()
                monitorJob = null
                reusableProbeBitmap?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                reusableProbeBitmap = null
                reusableBufferBitmap?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                reusableBufferBitmap = null
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
            val context = appContext ?: continue
            val currentCutouts = ScreenCaptureManager.cutouts.value.filter { it.freezeOnHudLoss }
            if (currentCutouts.isEmpty()) {
                delay(PRESENCE_CHECK_INTERVAL_LOST_MS)
                continue
            }

            // Adaptive cadence: 30 Hz when any cutout is PRESENT; 5 Hz when all are LOST
            val hasAnyPresent = currentCutouts.any { cutoutStates[it.id] != HudPresenceState.LOST }
            val checkInterval = if (hasAnyPresent) PRESENCE_CHECK_INTERVAL_ACTIVE_MS else PRESENCE_CHECK_INTERVAL_LOST_MS
            delay(checkInterval)

            val srcW = ScreenCaptureManager.captureSourceWidth.value.let { if (it > 0) it else DEFAULT_SOURCE_WIDTH }
            val srcH = ScreenCaptureManager.captureSourceHeight.value.let { if (it > 0) it else DEFAULT_SOURCE_HEIGHT }

            val probeW = PRESENCE_PROBE_WIDTH
            val probeH = (PRESENCE_PROBE_WIDTH * srcH / srcW).coerceAtLeast(1)

            var probeReusable = reusableProbeBitmap
            if (probeReusable == null || probeReusable.width != probeW || probeReusable.height != probeH || probeReusable.isRecycled) {
                if (probeReusable != null && !probeReusable.isRecycled) {
                    probeReusable.recycle()
                }
                probeReusable =
                    try {
                        Bitmap.createBitmap(probeW, probeH, Bitmap.Config.ARGB_8888)
                    } catch (e: OutOfMemoryError) {
                        AppLog.e(TAG, "OOM allocating reusable probe bitmap (${probeW}x$probeH)", e)
                        null
                    }
                reusableProbeBitmap = probeReusable
            }

            val probeFrame = MirrorFrameSampler.captureFrame(probeW, probeH, reusableBitmap = probeReusable) ?: continue
            try {
                if (probeFrame.width <= 0 || probeFrame.height <= 0) continue

                var anyStateChanged = false
                val cutoutsNeedingBuffer = mutableListOf<ScreenCutout>()

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
                            val px = (globalU * probeW).roundToInt().coerceIn(0, probeW - 1)
                            val py = (globalV * probeH).roundToInt().coerceIn(0, probeH - 1)
                            probeFrame.getPixel(px, py)
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

                    // Evaluate if high-res 1080p frame buffer capture is needed for freeze:
                    val hasCachedFrame = lastValidFrameBitmaps.containsKey(cutout.id)
                    val isConfidentPresent = matchRatio >= HudPresenceEvaluator.MATCH_THRESHOLD_PRESENT
                    if (newState == HudPresenceState.PRESENT && (!hasCachedFrame || isConfidentPresent)) {
                        if (cutout.customAnchorEnabled) {
                            val now = SystemClock.elapsedRealtime()
                            val lastCapture = lastLiveFrameCaptureTimes[cutout.id] ?: 0L
                            if (!hasCachedFrame || now - lastCapture >= LIVE_FRAME_BUFFER_INTERVAL_MS) {
                                cutoutsNeedingBuffer.add(cutout)
                            }
                        } else {
                            val existingFreeze = CutoutMaskManager.getFreezeFrame(context, cutout.id)
                            if (existingFreeze == null) {
                                cutoutsNeedingBuffer.add(cutout)
                            }
                        }
                    }
                }

                if (cutoutsNeedingBuffer.isNotEmpty()) {
                    captureFullFrameBuffer(context, srcW, srcH, cutoutsNeedingBuffer)
                }

                if (anyStateChanged) {
                    _presenceRevision.value++
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error in HUD presence evaluation loop", e)
            } finally {
                if (probeFrame != reusableProbeBitmap && probeFrame != ScreenCaptureManager.frozenBitmap.value) {
                    probeFrame.recycle()
                }
            }
        }
    }

    private fun captureFullFrameBuffer(
        context: Context,
        srcW: Int,
        srcH: Int,
        cutouts: List<ScreenCutout>,
    ) {
        var bufferReusable = reusableBufferBitmap
        if (bufferReusable == null || bufferReusable.width != srcW || bufferReusable.height != srcH || bufferReusable.isRecycled) {
            if (bufferReusable != null && !bufferReusable.isRecycled) {
                bufferReusable.recycle()
            }
            bufferReusable =
                try {
                    Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    AppLog.e(TAG, "OOM allocating reusable buffer bitmap (${srcW}x$srcH)", e)
                    null
                }
            reusableBufferBitmap = bufferReusable
        }

        val frame = MirrorFrameSampler.captureFrame(srcW, srcH, reusableBitmap = bufferReusable) ?: return
        try {
            val frameW = frame.width
            val frameH = frame.height
            if (frameW <= 0 || frameH <= 0) return

            val now = SystemClock.elapsedRealtime()
            for (cutout in cutouts) {
                val cX = (cutout.srcX * frameW).roundToInt().coerceIn(0, frameW - 1)
                val cY = (cutout.srcY * frameH).roundToInt().coerceIn(0, frameH - 1)
                val cRight = ((cutout.srcX + cutout.srcWidth) * frameW).roundToInt().coerceIn(cX + 1, frameW)
                val cBottom = ((cutout.srcY + cutout.srcHeight) * frameH).roundToInt().coerceIn(cY + 1, frameH)
                val cW = cRight - cX
                val cH = cBottom - cY

                if (cW <= 0 || cH <= 0) continue

                try {
                    val crop = Bitmap.createBitmap(frame, cX, cY, cW, cH)
                    val oldCrop = lastValidFrameBitmaps.put(cutout.id, crop)
                    if (oldCrop != null && !oldCrop.isRecycled) {
                        oldCrop.recycle()
                    }
                    lastLiveFrameCaptureTimes[cutout.id] = now

                    if (!cutout.customAnchorEnabled) {
                        CutoutMaskManager.saveFreezeFrame(context, cutout.id, crop)
                    } else {
                        val existingFreeze = CutoutMaskManager.getFreezeFrame(context, cutout.id)
                        if (existingFreeze == null || existingFreeze.width != cW || existingFreeze.height != cH) {
                            CutoutMaskManager.saveFreezeFrame(context, cutout.id, crop)
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to capture crop for cutout ${cutout.id}", e)
                }
            }
        } finally {
            if (frame != reusableBufferBitmap && frame != ScreenCaptureManager.frozenBitmap.value) {
                frame.recycle()
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
