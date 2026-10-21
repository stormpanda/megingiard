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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "VisualAutoTuneCoord"

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

internal data class AutoTuneUiState(
    val isCalibrating: Boolean = false,
    val isPaused: Boolean = false,
    val calibrationType: CalibrationType = CalibrationType.NONE,
    val sampleCount: Int = 0,
    val canFinish: Boolean = false,
    val dynamicPercent: Int = 0,
    val lastTunedPercent: Int? = null,
)

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
    private val _uiState = MutableStateFlow(AutoTuneUiState())
    val uiState: StateFlow<AutoTuneUiState> = _uiState.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    @Volatile
    private var isFinishRequested = false

    @Volatile
    private var isResetRequested = false

    private var calibrationJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun setPreviewBitmap(bitmap: Bitmap?) {
        val old = _previewBitmap.value
        _previewBitmap.value = bitmap
        if (old != null && old != bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    /**
     * Signals the active calibration sampling loop to complete immediately and run analysis.
     * Only triggers when minimum required frames have been collected.
     */
    fun finishCalibration() {
        val current = _uiState.value
        if (current.isCalibrating && current.canFinish) {
            AppLog.i(TAG, "finishCalibration requested by user")
            isFinishRequested = true
        }
    }

    /**
     * Resets currently collected frames and preview state during an active calibration session.
     */
    fun resetCalibration() {
        if (_uiState.value.isCalibrating) {
            AppLog.i(TAG, "resetCalibration requested by user")
            isResetRequested = true
        }
    }

    /**
     * Toggles calibration pause state. When paused, frame sampling is suspended.
     */
    fun togglePause() {
        if (_uiState.value.isCalibrating) {
            val nextPaused = !_uiState.value.isPaused
            _uiState.update { it.copy(isPaused = nextPaused) }
            AppLog.i(TAG, "togglePause: isPaused=$nextPaused")
        }
    }

    /**
     * Explicitly sets calibration pause state.
     */
    fun setPaused(paused: Boolean) {
        if (_uiState.value.isCalibrating) {
            _uiState.update { it.copy(isPaused = paused) }
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
        val appContext = context.applicationContext
        var capturedFirstBitmap: Bitmap? = null
        runCalibrationSession(
            type = CalibrationType.CUTOUT,
            targetId = cutout.id,
            normX = cutout.srcX,
            normY = cutout.srcY,
            normW = cutout.srcWidth,
            normH = cutout.srcHeight,
            captureFirstFrame = true,
            onFirstFrameChanged = { capturedFirstBitmap = it },
            onFinalize = { sampledFrames, cropW, cropH, firstFrameBitmap, result ->
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
                        firstFrameBitmap
                            ?: if (refColorFrame != null && refColorFrame.size == cropW * cropH) {
                                Bitmap.createBitmap(refColorFrame, cropW, cropH, Bitmap.Config.ARGB_8888)
                            } else if (sampledFrames.isNotEmpty()) {
                                Bitmap.createBitmap(sampledFrames.first(), cropW, cropH, Bitmap.Config.ARGB_8888)
                            } else {
                                null
                            }
                    withContext(Dispatchers.IO) {
                        CutoutMaskManager.saveMask(
                            context = appContext,
                            cutoutId = cutout.id,
                            bitmap = maskBitmap,
                            varianceMap = result.varianceMap,
                            freezeFrame = freezeBitmap,
                        )
                    }
                }

                val hasMask = mask != null && !result.isStaticScene
                val updatedCutout =
                    cutout.copy(
                        hasTransparencyMask = hasMask,
                        maskTranslucency = 0,
                        renderAsStaticAsset = false,
                    )
                MacroPadState.updateCutout(updatedCutout)
                _uiState.update { it.copy(lastTunedPercent = if (hasMask) result.transparentPercent else null) }
                AppLog.i(
                    TAG,
                    "HUD/UI isolation mask calibration completed: hasMask=$hasMask, transparentPct=${result.transparentPercent}%, summary=${result.summary}",
                )
                onComplete?.invoke(updatedCutout, result)
            },
            onCleanup = {
                val bmp = capturedFirstBitmap
                if (bmp != null && CutoutMaskManager.getFreezeFrame(appContext, cutout.id) != bmp && !bmp.isRecycled) {
                    bmp.recycle()
                }
            },
        )
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
        val appContext = context.applicationContext
        val anchor = layout.visualAnchor
        runCalibrationSession(
            type = CalibrationType.LAYOUT_ANCHOR,
            targetId = layout.id,
            normX = anchor.srcX,
            normY = anchor.srcY,
            normW = anchor.srcWidth,
            normH = anchor.srcHeight,
            captureFirstFrame = false,
            onFinalize = { sampledFrames, cropW, cropH, _, result ->
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
                        withContext(Dispatchers.IO) {
                            val mask = result.maskPixels
                            if (mask != null && result.maskWidth > 0 && result.maskHeight > 0) {
                                val maskBitmap =
                                    Bitmap.createBitmap(mask, result.maskWidth, result.maskHeight, Bitmap.Config.ARGB_8888)
                                CutoutMaskManager.saveMask(
                                    context = appContext,
                                    cutoutId = layout.id,
                                    bitmap = maskBitmap,
                                    varianceMap = result.varianceMap,
                                    freezeFrame = freezeBitmap,
                                )
                            } else {
                                CutoutMaskManager.saveFreezeFrame(appContext, layout.id, freezeBitmap)
                            }
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
                _uiState.update { it.copy(lastTunedPercent = null) }
                AppLog.i(
                    TAG,
                    "Layout anchor calibration complete: points=${signature?.points?.size ?: 0}, enabled=$hasValidSignature",
                )
                onComplete?.invoke(updatedLayout, signature)
            },
        )
    }

    private fun runCalibrationSession(
        type: CalibrationType,
        targetId: String,
        normX: Float,
        normY: Float,
        normW: Float,
        normH: Float,
        captureFirstFrame: Boolean,
        onFirstFrameChanged: (Bitmap?) -> Unit = {},
        onFinalize: suspend (
            sampledFrames: List<IntArray>,
            cropW: Int,
            cropH: Int,
            firstFrameBitmap: Bitmap?,
            result: AutoTuneResult,
        ) -> Unit,
        onCleanup: () -> Unit = {},
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
        AnchorPresenceManager.ensureSmootherUnfrozen()

        isFinishRequested = false
        isResetRequested = false
        _uiState.value =
            AutoTuneUiState(
                isCalibrating = true,
                calibrationType = type,
            )
        setPreviewBitmap(null)

        calibrationJob =
            scope.launch {
                AppLog.i(TAG, "Starting $type calibration for target $targetId")

                delay(CALIBRATION_WARMUP_DELAY_MS)
                if (!isActive || isFinishRequested) return@launch

                val sampledFrames = ArrayList<IntArray>()
                var cropW = 0
                var cropH = 0
                var tracker: CalibrationPreviewTracker? = null
                var previewPixels: IntArray? = null
                var firstFrameBitmap: Bitmap? = null
                var totalSampledDurationMs = 0L
                var lastLoopTime = SystemClock.elapsedRealtime()

                try {
                    while (isActive && !isFinishRequested) {
                        val now = SystemClock.elapsedRealtime()
                        val loopElapsed = now - lastLoopTime
                        lastLoopTime = now

                        if (isResetRequested) {
                            isResetRequested = false
                            AppLog.i(TAG, "Resetting active $type calibration session samples")
                            sampledFrames.clear()
                            tracker = null
                            previewPixels = null
                            firstFrameBitmap?.let {
                                if (!it.isRecycled) it.recycle()
                            }
                            firstFrameBitmap = null
                            onFirstFrameChanged(null)
                            totalSampledDurationMs = 0L
                            _uiState.update {
                                it.copy(
                                    sampleCount = 0,
                                    canFinish = false,
                                    dynamicPercent = 0,
                                )
                            }
                            setPreviewBitmap(null)
                        }

                        if (_uiState.value.isPaused) {
                            delay(SAMPLE_INTERVAL_MS)
                            continue
                        }

                        totalSampledDurationMs += loopElapsed
                        if (totalSampledDurationMs >= MAX_CALIBRATION_DURATION_MS) {
                            AppLog.i(TAG, "$type calibration reached maximum safety duration (${MAX_CALIBRATION_DURATION_MS}ms)")
                            break
                        }

                        val frameBitmap = MirrorFrameSampler.captureFrame(SAMPLE_WIDTH, SAMPLE_HEIGHT)
                        if (frameBitmap != null) {
                            try {
                                val cX = (normX * frameBitmap.width).roundToInt().coerceIn(0, frameBitmap.width - 1)
                                val cY = (normY * frameBitmap.height).roundToInt().coerceIn(0, frameBitmap.height - 1)
                                val cRight =
                                    ((normX + normW) * frameBitmap.width).roundToInt().coerceIn(cX + 1, frameBitmap.width)
                                val cBottom =
                                    ((normY + normH) * frameBitmap.height).roundToInt().coerceIn(
                                        cY + 1,
                                        frameBitmap.height,
                                    )
                                cropW = cRight - cX
                                cropH = cBottom - cY

                                if (cropW > 0 && cropH > 0) {
                                    val pixels = IntArray(cropW * cropH)
                                    frameBitmap.getPixels(pixels, 0, cropW, cX, cY, cropW, cropH)
                                    sampledFrames.add(pixels)

                                    if (captureFirstFrame && firstFrameBitmap == null) {
                                        firstFrameBitmap = Bitmap.createBitmap(pixels, cropW, cropH, Bitmap.Config.ARGB_8888)
                                        onFirstFrameChanged(firstFrameBitmap)
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
                                        _uiState.update {
                                            it.copy(
                                                sampleCount = tracker.frameCount,
                                                dynamicPercent = tracker.transparentPixelPercent,
                                                canFinish = tracker.frameCount >= MIN_CALIBRATION_FRAMES,
                                            )
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
                            "Collected ${sampledFrames.size} $type crops. Running analysis on Default dispatcher...",
                        )
                        val result =
                            withContext(Dispatchers.Default) {
                                CutoutAutoTuner.analyze(sampledFrames, cropW, cropH, cutoutId = targetId)
                            }
                        onFinalize(sampledFrames, cropW, cropH, firstFrameBitmap, result)
                    } else {
                        AppLog.w(TAG, "No video frames could be sampled during $type calibration")
                        _uiState.update { it.copy(lastTunedPercent = null) }
                    }
                } catch (e: Exception) {
                    if (coroutineContext[Job]?.isCancelled == true) {
                        AppLog.i(TAG, "$type calibration cancelled")
                    } else {
                        AppLog.e(TAG, "$type calibration failed with exception", e)
                    }
                    _uiState.update { it.copy(lastTunedPercent = null) }
                } finally {
                    onCleanup()
                    resetSessionStateFlows()
                    AppStateManager.resumeSuspended()
                }
            }
    }

    private fun resetSessionStateFlows() {
        setPreviewBitmap(null)
        _uiState.update { AutoTuneUiState(lastTunedPercent = it.lastTunedPercent) }
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
        resetSessionStateFlows()
        if (resumeSuspended && wasActive) {
            AppStateManager.resumeSuspended()
        }
    }
}
