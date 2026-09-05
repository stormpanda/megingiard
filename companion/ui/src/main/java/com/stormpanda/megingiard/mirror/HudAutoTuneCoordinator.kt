package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
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
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

private const val TAG = "HudAutoTuneCoordinator"

private const val CALIBRATION_DURATION_MS = 6000L
private const val SAMPLE_INTERVAL_MS = 120L
private const val SAMPLE_WIDTH = 1920
private const val SAMPLE_HEIGHT = 1080
private const val MS_PER_SECOND = 1000.0

/**
 * Coordinates multi-second video frame sampling and HUD auto-tune calibration.
 * Exposes observable StateFlows for UI countdowns and progress bars.
 *
 * Automatically suspends and dismisses open primary modals on Display 0 via
 * [AppStateManager.suspendCurrentAndDismiss] before starting sampling, allowing unobstructed
 * in-game player movement, and automatically restores the editor via [AppStateManager.resumeSuspended]
 * upon completion or cancellation.
 */
internal object HudAutoTuneCoordinator {
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _lastTunedPercent = MutableStateFlow<Int?>(null)
    val lastTunedPercent: StateFlow<Int?> = _lastTunedPercent.asStateFlow()

    private var calibrationJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Starts the 6-second auto-tune sampling sequence for [cutout].
     * Suspends the primary modal overlay on Display 0, unfreezes mirror capture,
     * and restores the editor upon completion.
     */
    fun startCalibration(
        context: Context,
        cutout: ScreenCutout,
        onComplete: ((ScreenCutout, AutoTuneResult) -> Unit)? = null,
    ) {
        cancelCalibration(resumeSuspended = false)

        AppStateManager.suspendCurrentAndDismiss()
        if (ScreenCaptureManager.isFrozen.value) {
            ScreenCaptureManager.setFrozen(false)
        }

        calibrationJob =
            scope.launch {
                AppLog.i(TAG, "Starting HUD Auto-Tune calibration for cutout ${cutout.id} (duration=${CALIBRATION_DURATION_MS}ms)")
                _isCalibrating.value = true
                _lastTunedPercent.value = null
                _progress.value = 0f
                _remainingSeconds.value = (CALIBRATION_DURATION_MS / MS_PER_SECOND).toInt()

                val sampledFrames = ArrayList<IntArray>()
                var cropW = 0
                var cropH = 0
                val startTime = SystemClock.elapsedRealtime()

                try {
                    while (isActive) {
                        val elapsed = SystemClock.elapsedRealtime() - startTime
                        if (elapsed >= CALIBRATION_DURATION_MS) break

                        val remainingMs = max(0L, CALIBRATION_DURATION_MS - elapsed)
                        _remainingSeconds.value = ceil(remainingMs / MS_PER_SECOND).toInt()
                        _progress.value = (elapsed.toFloat() / CALIBRATION_DURATION_MS).coerceIn(0f, 1f)

                        val frameBitmap = MirrorFrameSampler.captureFrame(SAMPLE_WIDTH, SAMPLE_HEIGHT)
                        if (frameBitmap != null) {
                            try {
                                val cX = (cutout.srcX * frameBitmap.width).toInt().coerceIn(0, frameBitmap.width - 1)
                                val cY = (cutout.srcY * frameBitmap.height).toInt().coerceIn(0, frameBitmap.height - 1)
                                cropW = (cutout.srcWidth * frameBitmap.width).toInt().coerceIn(1, frameBitmap.width - cX)
                                cropH = (cutout.srcHeight * frameBitmap.height).toInt().coerceIn(1, frameBitmap.height - cY)

                                val pixels = IntArray(cropW * cropH)
                                frameBitmap.getPixels(pixels, 0, cropW, cX, cY, cropW, cropH)
                                sampledFrames.add(pixels)
                            } finally {
                                if (frameBitmap != ScreenCaptureManager.frozenBitmap.value) {
                                    frameBitmap.recycle()
                                }
                            }
                        }

                        delay(SAMPLE_INTERVAL_MS)
                    }

                    if (sampledFrames.isNotEmpty() && cropW > 0 && cropH > 0) {
                        AppLog.i(
                            TAG,
                            "Collected ${sampledFrames.size} frame crops. Running pixel color change analysis on Default dispatcher...",
                        )
                        val result =
                            withContext(Dispatchers.Default) {
                                HudAutoTuner.analyze(sampledFrames, cropW, cropH)
                            }

                        val mask = result.maskPixels
                        if (mask != null && result.maskWidth > 0 && result.maskHeight > 0 && !result.isStaticScene) {
                            val maskBitmap =
                                Bitmap.createBitmap(
                                    mask,
                                    result.maskWidth,
                                    result.maskHeight,
                                    Bitmap.Config.ARGB_8888,
                                )
                            CutoutMaskManager.saveMask(context.applicationContext, cutout.id, maskBitmap)
                        }

                        val hasMask = mask != null && !result.isStaticScene
                        val updatedCutout =
                            cutout.copy(
                                hasTransparencyMask = hasMask,
                            )
                        MacroPadState.updateCutout(updatedCutout)
                        _lastTunedPercent.value = if (hasMask) result.transparentPercent else null
                        AppLog.i(
                            TAG,
                            "Auto-Tune completed successfully: hasMask=$hasMask, transparentPct=${result.transparentPercent}%, summary=${result.summary}",
                        )
                        onComplete?.invoke(updatedCutout, result)
                    } else {
                        AppLog.w(TAG, "No video frames could be sampled during calibration")
                        _lastTunedPercent.value = null
                    }
                } catch (e: Exception) {
                    if (coroutineContext[Job]?.isCancelled == true) {
                        AppLog.i(TAG, "Auto-Tune calibration cancelled")
                    } else {
                        AppLog.e(TAG, "Auto-Tune calibration failed with exception", e)
                    }
                    _lastTunedPercent.value = null
                } finally {
                    _isCalibrating.value = false
                    _progress.value = 0f
                    _remainingSeconds.value = 0
                    if (coroutineContext[Job]?.isCancelled != true) {
                        AppLog.i(TAG, "Auto-Tune calibration loop ended normally; restoring suspended modal")
                        AppStateManager.resumeSuspended()
                    }
                }
            }
    }

    /**
     * Cancels any in-progress calibration run and resets state.
     * When [resumeSuspended] is true and a calibration was active, restores the suspended primary modal.
     */
    fun cancelCalibration(resumeSuspended: Boolean = true) {
        val wasActive = calibrationJob?.isActive == true
        if (wasActive) {
            AppLog.i(TAG, "Cancelling active HUD calibration (resumeSuspended=$resumeSuspended)")
            calibrationJob?.cancel()
        }
        calibrationJob = null
        _isCalibrating.value = false
        _progress.value = 0f
        _remainingSeconds.value = 0
        if (resumeSuspended && wasActive) {
            AppStateManager.resumeSuspended()
        }
    }
}
