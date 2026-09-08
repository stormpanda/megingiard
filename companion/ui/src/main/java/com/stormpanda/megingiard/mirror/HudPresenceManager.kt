package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import com.stormpanda.megingiard.AppLog
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

private const val TAG = "HudPresenceManager"

private const val PRESENCE_CHECK_INTERVAL_MS = 250L // 4 Hz
private const val CHECK_FRAME_WIDTH = 960
private const val CHECK_FRAME_HEIGHT = 540

/**
 * Singleton manager coordinating real-time HUD presence detection and freeze-frame caching.
 *
 * Periodically samples top-screen video frames at 4 Hz, evaluates anchor signatures of active
 * cutouts, and smoothly manages the transition to frozen frames during cutscenes or in-game menus.
 */
object HudPresenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var appContext: Context? = null

    private val cutoutStates = ConcurrentHashMap<String, HudPresenceState>()
    private val cutoutConsecutiveCounts = ConcurrentHashMap<String, Int>()
    private val lastValidFrameBitmaps = ConcurrentHashMap<String, Bitmap>()

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
                AppLog.i(TAG, "Starting HUD presence monitoring loop (4 Hz)")
                monitorJob = scope.launch { runMonitoringLoop() }
            }
        } else {
            if (monitorJob?.isActive == true) {
                AppLog.i(TAG, "Stopping HUD presence monitoring loop")
                monitorJob?.cancel()
                monitorJob = null
                lastValidFrameBitmaps.values.forEach { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                lastValidFrameBitmaps.clear()
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

            val frame = MirrorFrameSampler.captureFrame(CHECK_FRAME_WIDTH, CHECK_FRAME_HEIGHT) ?: continue
            try {
                val frameW = frame.width
                val frameH = frame.height
                if (frameW <= 0 || frameH <= 0) continue

                var anyStateChanged = false

                for (cutout in currentCutouts) {
                    val signature = CutoutMaskManager.getAnchorSignature(context, cutout.id) ?: continue
                    if (signature.points.isEmpty()) continue

                    val matchRatio =
                        HudPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                            val globalU = cutout.srcX + u * cutout.srcWidth
                            val globalV = cutout.srcY + v * cutout.srcHeight
                            val px = (globalU * frameW).toInt().coerceIn(0, frameW - 1)
                            val py = (globalV * frameH).toInt().coerceIn(0, frameH - 1)
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

                    if (newState == HudPresenceState.PRESENT) {
                        val cX = (cutout.srcX * frameW).toInt().coerceIn(0, frameW - 1)
                        val cY = (cutout.srcY * frameH).toInt().coerceIn(0, frameH - 1)
                        val cW = (cutout.srcWidth * frameW).toInt().coerceIn(1, frameW - cX)
                        val cH = (cutout.srcHeight * frameH).toInt().coerceIn(1, frameH - cY)
                        try {
                            val crop = Bitmap.createBitmap(frame, cX, cY, cW, cH)
                            val oldCrop = lastValidFrameBitmaps.put(cutout.id, crop)
                            if (oldCrop != null && !oldCrop.isRecycled) {
                                oldCrop.recycle()
                            }
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Failed to capture live valid crop for cutout ${cutout.id}", e)
                        }
                    }
                }

                if (anyStateChanged) {
                    _presenceRevision.value++
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error in HUD presence evaluation loop", e)
            } finally {
                if (frame != ScreenCaptureManager.frozenBitmap.value) {
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
     * Retrieves the most recent valid HUD frame bitmap for [cutoutId], falling back to the calibrated disk frame.
     */
    fun getFrozenFrame(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        lastValidFrameBitmaps[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            lastValidFrameBitmaps.remove(cutoutId)
        }
        return CutoutMaskManager.getFreezeFrame(context, cutoutId)
    }

    /**
     * Clears presence state and cached frames for [cutoutId].
     */
    fun clearCutout(cutoutId: String) {
        cutoutStates.remove(cutoutId)
        cutoutConsecutiveCounts.remove(cutoutId)
        lastValidFrameBitmaps.remove(cutoutId)?.let {
            if (!it.isRecycled) it.recycle()
        }
    }
}
