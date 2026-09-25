package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "VisualAutoTuneCoordinator"

private const val MAX_CALIBRATION_DURATION_MS = 180_000L
private const val SAMPLE_INTERVAL_MS = 120L
private const val CALIBRATION_WARMUP_DELAY_MS = 500L
private const val SAMPLE_WIDTH = 1920
private const val SAMPLE_HEIGHT = 1080

/**
 * Category of element currently undergoing visual calibration.
 */
enum class CalibrationType {
    NONE,
    CUTOUT,
    LAYOUT_ANCHOR,
}

/**
 * Coordinates video frame sampling and visual auto-tune calibration.
 * Exposes observable StateFlows for live preview bitmaps, sample frame counts, and completion status.
 *
 * Automatically suspends and dismisses open primary modals on Display 0 via
 * [AppStateManager.suspendCurrentAndDismiss] before starting sampling, allowing unobstructed
 * in-game player movement, and automatically restores the editor via [AppStateManager.resumeSuspended]
 * upon completion or cancellation.
 */
internal object VisualAutoTuneCoordinator {
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _calibrationType = MutableStateFlow(CalibrationType.NONE)
    val calibrationType: StateFlow<CalibrationType> = _calibrationType.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _sampleCount = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    private val _canFinish = MutableStateFlow(false)
    val canFinish: StateFlow<Boolean> = _canFinish.asStateFlow()

    private val _dynamicPercent = MutableStateFlow(0)
    val dynamicPercent: StateFlow<Int> = _dynamicPercent.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _lastTunedPercent = MutableStateFlow<Int?>(null)
    val lastTunedPercent: StateFlow<Int?> = _lastTunedPercent.asStateFlow()

    @Volatile
    private var isFinishRequested = false

    @Volatile
    private var isResetRequested = false

    private var calibrationJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun setPreviewBitmap(bitmap: Bitmap?) {
        val old = _previewBitmap.value
        _previewBitmap.value = bitmap
        if (old != null && old != bitmap) {
            synchronized(old) {
                if (!old.isRecycled) {
                    old.recycle()
                }
            }
        }
    }

    /**
     * Signals the active calibration sampling loop to complete immediately and run analysis.
     * Only triggers when minimum required frames have been collected.
     */
    fun finishCalibration() {
        if (_isCalibrating.value && _canFinish.value) {
            AppLog.i(TAG, "finishCalibration requested by user")
            isFinishRequested = true
        }
    }

    /**
     * Resets currently collected frames and preview state during an active calibration session.
     */
    fun resetCalibration() {
        if (_isCalibrating.value) {
            AppLog.i(TAG, "resetCalibration requested by user")
            isResetRequested = true
        }
    }

    /**
     * Toggles calibration pause state. When paused, frame sampling is suspended.
     */
    fun togglePause() {
        if (_isCalibrating.value) {
            _isPaused.value = !_isPaused.value
            AppLog.i(TAG, "togglePause: isPaused=${_isPaused.value}")
        }
    }

    /**
     * Explicitly sets calibration pause state.
     */
    fun setPaused(paused: Boolean) {
        if (_isCalibrating.value) {
            _isPaused.value = paused
            AppLog.i(TAG, "setPaused: isPaused=$paused")
        }
    }

    /**
     * Starts the interactive auto-tune sampling sequence for [cutout].
     * Suspends the primary modal overlay on Display 0, unfreezes mirror capture,
     * streams live transparency previews, and restores the editor upon completion.
     */
    fun startCalibration(
        context: Context,
        cutout: ScreenCutout,
        onComplete: ((ScreenCutout, AutoTuneResult) -> Unit)? = null,
    ) {
        cancelCalibration(resumeSuspended = false)

        val existingSuspended = AppStateManager.suspendedPrimaryModal.value
        if (existingSuspended != null) {
            AppStateManager.closePrimaryModal()
        } else {
            AppStateManager.suspendCurrentAndDismiss()
        }
        if (ScreenCaptureManager.isFrozen.value) {
            ScreenCaptureManager.setFrozen(false)
        }

        isFinishRequested = false
        isResetRequested = false
        _isCalibrating.value = true
        _calibrationType.value = CalibrationType.CUTOUT
        _lastTunedPercent.value = null
        _progress.value = 0f
        _remainingSeconds.value = 0
        _sampleCount.value = 0
        _canFinish.value = false
        _dynamicPercent.value = 0
        _isPaused.value = false
        setPreviewBitmap(null)

        calibrationJob =
            scope.launch {
                AppLog.i(TAG, "Starting HUD/UI isolation calibration for cutout ${cutout.id}")

                // Wait for any dismissed top-screen modals, scrims, and toolbars to finish exiting
                // and for the video stream pipeline to flush obstructed frames.
                delay(CALIBRATION_WARMUP_DELAY_MS)
                if (!isActive || isFinishRequested) return@launch

                val sampledFrames = ArrayList<IntArray>()
                var cropW = 0
                var cropH = 0
                var tracker: CalibrationPreviewTracker? = null
                var previewPixels: IntArray? = null
                var cutoutFreezeBitmap: Bitmap? = null
                var totalSampledDurationMs = 0L
                var lastLoopTime = SystemClock.elapsedRealtime()

                try {
                    while (isActive && !isFinishRequested) {
                        val now = SystemClock.elapsedRealtime()
                        val loopElapsed = now - lastLoopTime
                        lastLoopTime = now

                        if (isResetRequested) {
                            isResetRequested = false
                            AppLog.i(TAG, "Resetting active cutout calibration session samples")
                            sampledFrames.clear()
                            tracker = null
                            previewPixels = null
                            cutoutFreezeBitmap?.let {
                                if (!it.isRecycled) it.recycle()
                            }
                            cutoutFreezeBitmap = null
                            totalSampledDurationMs = 0L
                            _sampleCount.value = 0
                            _canFinish.value = false
                            _dynamicPercent.value = 0
                            setPreviewBitmap(null)
                        }

                        if (_isPaused.value) {
                            delay(SAMPLE_INTERVAL_MS)
                            continue
                        }

                        totalSampledDurationMs += loopElapsed
                        if (totalSampledDurationMs >= MAX_CALIBRATION_DURATION_MS) {
                            AppLog.i(TAG, "Calibration reached maximum safety duration (${MAX_CALIBRATION_DURATION_MS}ms)")
                            break
                        }

                        val frameBitmap = MirrorFrameSampler.captureFrame(SAMPLE_WIDTH, SAMPLE_HEIGHT)
                        if (frameBitmap != null) {
                            try {
                                val cX = (cutout.srcX * frameBitmap.width).roundToInt().coerceIn(0, frameBitmap.width - 1)
                                val cY = (cutout.srcY * frameBitmap.height).roundToInt().coerceIn(0, frameBitmap.height - 1)
                                val cRight =
                                    ((cutout.srcX + cutout.srcWidth) * frameBitmap.width).roundToInt().coerceIn(cX + 1, frameBitmap.width)
                                val cBottom =
                                    ((cutout.srcY + cutout.srcHeight) * frameBitmap.height).roundToInt().coerceIn(
                                        cY + 1,
                                        frameBitmap.height,
                                    )
                                cropW = cRight - cX
                                cropH = cBottom - cY

                                if (cropW > 0 && cropH > 0) {
                                    val pixels = IntArray(cropW * cropH)
                                    frameBitmap.getPixels(pixels, 0, cropW, cX, cY, cropW, cropH)
                                    sampledFrames.add(pixels)

                                    if (cutoutFreezeBitmap == null) {
                                        cutoutFreezeBitmap = Bitmap.createBitmap(pixels, cropW, cropH, Bitmap.Config.ARGB_8888)
                                    }

                                    if (tracker == null || tracker.width != cropW || tracker.height != cropH) {
                                        tracker = CalibrationPreviewTracker(cropW, cropH)
                                        previewPixels = IntArray(cropW * cropH)
                                    }

                                    previewPixels?.let { outBuf ->
                                        tracker.ingestFrame(pixels, outBuf)
                                        try {
                                            val bmp = Bitmap.createBitmap(outBuf, cropW, cropH, Bitmap.Config.ARGB_8888)
                                            setPreviewBitmap(bmp)
                                        } catch (e: Exception) {
                                            AppLog.e(TAG, "Failed to create preview bitmap", e)
                                        }
                                        _sampleCount.value = tracker.frameCount
                                        _dynamicPercent.value = tracker.transparentPixelPercent
                                        if (tracker.frameCount >= MIN_CALIBRATION_FRAMES) {
                                            _canFinish.value = true
                                        }
                                    }
                                }
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
                                CutoutAutoTuner.analyze(sampledFrames, cropW, cropH, cutoutId = cutout.id)
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
                            val refColorFrame = result.referenceColorFrame
                            val freezeBitmap =
                                cutoutFreezeBitmap
                                    ?: if (refColorFrame != null && refColorFrame.size == cropW * cropH) {
                                        Bitmap.createBitmap(refColorFrame, cropW, cropH, Bitmap.Config.ARGB_8888)
                                    } else if (sampledFrames.isNotEmpty()) {
                                        Bitmap.createBitmap(sampledFrames.first(), cropW, cropH, Bitmap.Config.ARGB_8888)
                                    } else {
                                        null
                                    }
                            CutoutMaskManager.saveMask(
                                context = context.applicationContext,
                                cutoutId = cutout.id,
                                bitmap = maskBitmap,
                                varianceMap = result.varianceMap,
                                freezeFrame = freezeBitmap,
                            )
                        }

                        val hasMask = mask != null && !result.isStaticScene
                        val updatedCutout =
                            cutout.copy(
                                hasTransparencyMask = hasMask,
                                maskTranslucency = 0,
                                renderAsStaticAsset = false,
                            )
                        MacroPadState.updateCutout(updatedCutout)
                        _lastTunedPercent.value = if (hasMask) result.transparentPercent else null
                        AppLog.i(
                            TAG,
                            "HUD/UI isolation mask calibration completed: hasMask=$hasMask, transparentPct=${result.transparentPercent}%, summary=${result.summary}",
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
                    if (cutoutFreezeBitmap != null &&
                        CutoutMaskManager.getFreezeFrame(context.applicationContext, cutout.id) != cutoutFreezeBitmap
                    ) {
                        if (!cutoutFreezeBitmap.isRecycled) {
                            cutoutFreezeBitmap.recycle()
                        }
                    }
                    setPreviewBitmap(null)
                    _isCalibrating.value = false
                    _isPaused.value = false
                    _calibrationType.value = CalibrationType.NONE
                    _canFinish.value = false
                    _sampleCount.value = 0
                    _dynamicPercent.value = 0
                    _progress.value = 0f
                    _remainingSeconds.value = 0
                    AppStateManager.resumeSuspended()
                }
            }
    }

    /**
     * Starts the interactive auto-tune sampling sequence for a layout's [LayoutVisualAnchor].
     * Samples solely the layout visual anchor bounds on the primary display and computes
     * the [VisualAnchorSignature].
     */
    fun startLayoutAnchorCalibration(
        context: Context,
        layout: PadLayout,
        onComplete: ((PadLayout, VisualAnchorSignature?) -> Unit)? = null,
    ) {
        cancelCalibration(resumeSuspended = false)

        val existingSuspended = AppStateManager.suspendedPrimaryModal.value
        if (existingSuspended != null) {
            AppStateManager.closePrimaryModal()
        } else {
            AppStateManager.suspendCurrentAndDismiss()
        }
        if (ScreenCaptureManager.isFrozen.value) {
            ScreenCaptureManager.setFrozen(false)
        }

        isFinishRequested = false
        isResetRequested = false
        _isCalibrating.value = true
        _calibrationType.value = CalibrationType.LAYOUT_ANCHOR
        _lastTunedPercent.value = null
        _progress.value = 0f
        _remainingSeconds.value = 0
        _sampleCount.value = 0
        _canFinish.value = false
        _dynamicPercent.value = 0
        _isPaused.value = false
        setPreviewBitmap(null)

        calibrationJob =
            scope.launch {
                AppLog.i(TAG, "Starting layout anchor calibration for layout ${layout.id}")

                // Wait for any dismissed top-screen modals, scrims, and toolbars to finish exiting
                // and for the video stream pipeline to flush obstructed frames.
                delay(CALIBRATION_WARMUP_DELAY_MS)
                if (!isActive || isFinishRequested) return@launch

                val sampledFrames = ArrayList<IntArray>()
                var cropW = 0
                var cropH = 0
                var tracker: CalibrationPreviewTracker? = null
                var previewPixels: IntArray? = null
                var totalSampledDurationMs = 0L
                var lastLoopTime = SystemClock.elapsedRealtime()

                try {
                    val anchor = layout.visualAnchor
                    while (isActive && !isFinishRequested) {
                        val now = SystemClock.elapsedRealtime()
                        val loopElapsed = now - lastLoopTime
                        lastLoopTime = now

                        if (isResetRequested) {
                            isResetRequested = false
                            AppLog.i(TAG, "Resetting layout anchor calibration session samples")
                            sampledFrames.clear()
                            tracker = null
                            previewPixels = null
                            totalSampledDurationMs = 0L
                            _sampleCount.value = 0
                            _canFinish.value = false
                            _dynamicPercent.value = 0
                            setPreviewBitmap(null)
                        }

                        if (_isPaused.value) {
                            delay(SAMPLE_INTERVAL_MS)
                            continue
                        }

                        totalSampledDurationMs += loopElapsed
                        if (totalSampledDurationMs >= MAX_CALIBRATION_DURATION_MS) {
                            AppLog.i(TAG, "Layout anchor calibration reached maximum safety duration (${MAX_CALIBRATION_DURATION_MS}ms)")
                            break
                        }

                        val frameBitmap = MirrorFrameSampler.captureFrame(SAMPLE_WIDTH, SAMPLE_HEIGHT)
                        if (frameBitmap != null) {
                            try {
                                val aX = (anchor.srcX * frameBitmap.width).roundToInt().coerceIn(0, frameBitmap.width - 1)
                                val aY = (anchor.srcY * frameBitmap.height).roundToInt().coerceIn(0, frameBitmap.height - 1)
                                val aRight =
                                    ((anchor.srcX + anchor.srcWidth) * frameBitmap.width).roundToInt().coerceIn(aX + 1, frameBitmap.width)
                                val aBottom =
                                    ((anchor.srcY + anchor.srcHeight) * frameBitmap.height).roundToInt().coerceIn(
                                        aY + 1,
                                        frameBitmap.height,
                                    )
                                cropW = aRight - aX
                                cropH = aBottom - aY

                                if (cropW > 0 && cropH > 0) {
                                    val pixels = IntArray(cropW * cropH)
                                    frameBitmap.getPixels(pixels, 0, cropW, aX, aY, cropW, cropH)
                                    sampledFrames.add(pixels)

                                    if (tracker == null || tracker.width != cropW || tracker.height != cropH) {
                                        tracker = CalibrationPreviewTracker(cropW, cropH)
                                        previewPixels = IntArray(cropW * cropH)
                                    }

                                    previewPixels?.let { outBuf ->
                                        tracker.ingestFrame(pixels, outBuf)
                                        try {
                                            val bmp = Bitmap.createBitmap(outBuf, cropW, cropH, Bitmap.Config.ARGB_8888)
                                            setPreviewBitmap(bmp)
                                        } catch (e: Exception) {
                                            AppLog.e(TAG, "Failed to create preview bitmap", e)
                                        }
                                        _sampleCount.value = tracker.frameCount
                                        _dynamicPercent.value = tracker.transparentPixelPercent
                                        if (tracker.frameCount >= MIN_CALIBRATION_FRAMES) {
                                            _canFinish.value = true
                                        }
                                    }
                                }
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
                            "Collected ${sampledFrames.size} layout anchor crops. Running analysis on Default dispatcher...",
                        )
                        val result =
                            withContext(Dispatchers.Default) {
                                CutoutAutoTuner.analyze(sampledFrames, cropW, cropH, cutoutId = layout.id)
                            }

                        val signature = result.anchorSignature
                        val hasValidSignature = signature != null && signature.points.isNotEmpty()
                        if (hasValidSignature) {
                            val calibratedFrame = result.calibratedFrame
                            val refColorFrame = result.referenceColorFrame
                            val freezeBitmap =
                                if (calibratedFrame != null && calibratedFrame.size == cropW * cropH) {
                                    Bitmap.createBitmap(calibratedFrame, cropW, cropH, Bitmap.Config.ARGB_8888)
                                } else if (refColorFrame != null && refColorFrame.size == cropW * cropH) {
                                    Bitmap.createBitmap(refColorFrame, cropW, cropH, Bitmap.Config.ARGB_8888)
                                } else if (sampledFrames.isNotEmpty()) {
                                    Bitmap.createBitmap(sampledFrames.first(), cropW, cropH, Bitmap.Config.ARGB_8888)
                                } else {
                                    null
                                }
                            if (freezeBitmap != null) {
                                val mask = result.maskPixels
                                if (mask != null && result.maskWidth > 0 && result.maskHeight > 0) {
                                    val maskBitmap =
                                        Bitmap.createBitmap(mask, result.maskWidth, result.maskHeight, Bitmap.Config.ARGB_8888)
                                    CutoutMaskManager.saveMask(
                                        context = context.applicationContext,
                                        cutoutId = layout.id,
                                        bitmap = maskBitmap,
                                        varianceMap = result.varianceMap,
                                        freezeFrame = freezeBitmap,
                                    )
                                } else {
                                    CutoutMaskManager.saveFreezeFrame(context.applicationContext, layout.id, freezeBitmap)
                                }
                            }
                        }
                        val updatedAnchor =
                            layout.visualAnchor.copy(
                                enabled = hasValidSignature,
                                signature = if (hasValidSignature) signature else null,
                            )
                        val updatedLayout = layout.copy(visualAnchor = updatedAnchor)
                        MacroPadState.updateLayout(updatedLayout)
                        _lastTunedPercent.value = null
                        AppLog.i(
                            TAG,
                            "Layout anchor calibration complete: points=${signature?.points?.size ?: 0}, enabled=$hasValidSignature",
                        )
                        onComplete?.invoke(updatedLayout, signature)
                    } else {
                        AppLog.w(TAG, "No frames collected during layout anchor calibration")
                    }
                } catch (e: Exception) {
                    if (coroutineContext[Job]?.isCancelled == true) {
                        AppLog.i(TAG, "Layout anchor calibration cancelled")
                    } else {
                        AppLog.e(TAG, "Layout anchor calibration failed with exception", e)
                    }
                    _lastTunedPercent.value = null
                } finally {
                    setPreviewBitmap(null)
                    _isCalibrating.value = false
                    _isPaused.value = false
                    _calibrationType.value = CalibrationType.NONE
                    _canFinish.value = false
                    _sampleCount.value = 0
                    _dynamicPercent.value = 0
                    _progress.value = 0f
                    _remainingSeconds.value = 0
                    AppStateManager.resumeSuspended()
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
            AppLog.i(TAG, "Cancelling active visual calibration (resumeSuspended=$resumeSuspended)")
            calibrationJob?.cancel()
        }
        isFinishRequested = false
        isResetRequested = false
        calibrationJob = null
        setPreviewBitmap(null)
        _isCalibrating.value = false
        _isPaused.value = false
        _calibrationType.value = CalibrationType.NONE
        _canFinish.value = false
        _sampleCount.value = 0
        _dynamicPercent.value = 0
        _progress.value = 0f
        _remainingSeconds.value = 0
        if (resumeSuspended && wasActive) {
            AppStateManager.resumeSuspended()
        }
    }
}
