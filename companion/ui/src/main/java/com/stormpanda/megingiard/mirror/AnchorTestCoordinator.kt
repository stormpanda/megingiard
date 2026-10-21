package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.DEFAULT_LAYOUT_ANCHOR_MATCH_THRESHOLD
import com.stormpanda.megingiard.macropad.MAX_LAYOUT_ANCHOR_MATCH_THRESHOLD
import com.stormpanda.megingiard.macropad.MIN_LAYOUT_ANCHOR_MATCH_THRESHOLD
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
import kotlin.math.roundToInt

private const val TAG = "AnchorTestCoordinator"
private const val TEST_SAMPLE_INTERVAL_MS = 33L
private const val TEST_WARMUP_DELAY_MS = 400L
private const val DEFAULT_SCREEN_WIDTH = 1920
private const val DEFAULT_SCREEN_HEIGHT = 1080

data class AnchorTestUiState(
    val isTesting: Boolean = false,
    val currentMatchRatio: Float = 0f,
    val isAnchorActive: Boolean = false,
    val pointMatches: List<AnchorPointMatchResult> = emptyList(),
    val targetPoints: List<AnchorPoint> = emptyList(),
    val matchThreshold: Float = DEFAULT_LAYOUT_ANCHOR_MATCH_THRESHOLD,
) {
    val matchedPointCount: Int
        get() = pointMatches.count { it.isMatch }

    val totalPointCount: Int
        get() = if (pointMatches.isNotEmpty()) pointMatches.size else targetPoints.size
}

/**
 * Singleton coordinator managing interactive anchor presence testing.
 *
 * Leaves Display 0 completely unobstructed with zero overlays so gameplay continues at 120Hz,
 * while Display 4 displays live detection diagnostics and dual reference/current preview feeds.
 */
object AnchorTestCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var testJob: Job? = null

    private val _uiState = MutableStateFlow(AnchorTestUiState())
    val uiState: StateFlow<AnchorTestUiState> = _uiState.asStateFlow()

    private val _referenceBitmap = MutableStateFlow<Bitmap?>(null)
    val referenceBitmap: StateFlow<Bitmap?> = _referenceBitmap.asStateFlow()

    private val _liveCropBitmap = MutableStateFlow<Bitmap?>(null)
    val liveCropBitmap: StateFlow<Bitmap?> = _liveCropBitmap.asStateFlow()

    private var onUpdateLayoutCallback: ((PadLayout) -> Unit)? = null
    private var activeTestLayout: PadLayout? = null

    private fun setReferenceBitmap(bitmap: Bitmap?) {
        val old = _referenceBitmap.value
        _referenceBitmap.value = bitmap
        if (old != null && old != bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    private fun setLiveCropBitmap(bitmap: Bitmap?) {
        val old = _liveCropBitmap.value
        _liveCropBitmap.value = bitmap
        if (old != null && old != bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    /**
     * Starts the live anchor test session for [layout].
     * Suspends the primary settings modal, unfreezes mirror stream, and samples at 30 Hz.
     */
    fun startTesting(
        context: Context,
        layout: PadLayout,
        onUpdateLayout: ((PadLayout) -> Unit)? = null,
    ) {
        stopTesting(resumeSuspended = false)
        activeTestLayout = layout
        onUpdateLayoutCallback = onUpdateLayout

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

        val points = layout.visualAnchor.signature?.points ?: emptyList()
        _uiState.value =
            AnchorTestUiState(
                isTesting = true,
                targetPoints = points,
                matchThreshold = layout.visualAnchor.effectiveMatchThreshold,
            )

        // Load reference calibrated bitmap (prefer pre-rendered static asset with mask, fallback to freeze frame)
        val staticAsset = CutoutMaskManager.getStaticAsset(context.applicationContext, layout.id)
        val freeze = staticAsset ?: CutoutMaskManager.getFreezeFrame(context.applicationContext, layout.id)
        if (freeze != null && !freeze.isRecycled) {
            try {
                setReferenceBitmap(freeze.copy(Bitmap.Config.ARGB_8888, false))
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to copy reference freeze frame bitmap", e)
                setReferenceBitmap(null)
            }
        } else {
            setReferenceBitmap(null)
        }

        testJob =
            scope.launch(Dispatchers.Default) {
                AppLog.i(TAG, "Starting anchor test session for layout ${layout.id}")
                delay(TEST_WARMUP_DELAY_MS)
                if (!isActive) return@launch

                val anchor = layout.visualAnchor
                val signature = anchor.signature

                var curState = AnchorPresenceState.LOST
                var curCount = 0
                var reusableLiveBitmap: Bitmap? = null

                try {
                    while (isActive) {
                        val left = (anchor.srcX * DEFAULT_SCREEN_WIDTH).roundToInt().coerceIn(0, DEFAULT_SCREEN_WIDTH - 1)
                        val top = (anchor.srcY * DEFAULT_SCREEN_HEIGHT).roundToInt().coerceIn(0, DEFAULT_SCREEN_HEIGHT - 1)
                        val right =
                            ((anchor.srcX + anchor.srcWidth) * DEFAULT_SCREEN_WIDTH).roundToInt().coerceIn(left + 1, DEFAULT_SCREEN_WIDTH)
                        val bottom =
                            ((anchor.srcY + anchor.srcHeight) * DEFAULT_SCREEN_HEIGHT).roundToInt().coerceIn(top + 1, DEFAULT_SCREEN_HEIGHT)
                        val rect = Rect(left, top, right, bottom)

                        if (rect.width() > 0 && rect.height() > 0) {
                            val crop = MirrorFrameSampler.captureCrop(rect, reusableLiveBitmap)
                            if (crop != null && !crop.isRecycled) {
                                reusableLiveBitmap = crop
                                try {
                                    val displayCopy = crop.copy(Bitmap.Config.ARGB_8888, false)
                                    setLiveCropBitmap(displayCopy)
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to copy live crop for test display", e)
                                }

                                if (signature != null && signature.points.isNotEmpty()) {
                                    val pointResults =
                                        AnchorPresenceEvaluator.evaluatePointMatches(signature) { u, v ->
                                            val px = (u * crop.width).toInt().coerceIn(0, crop.width - 1)
                                            val py = (v * crop.height).toInt().coerceIn(0, crop.height - 1)
                                            crop.getPixel(px, py)
                                        }
                                    val matchedCount = pointResults.count { it.isMatch }
                                    val matchRatio =
                                        if (pointResults.isNotEmpty()) {
                                            matchedCount.toFloat() / pointResults.size.toFloat()
                                        } else {
                                            0f
                                        }

                                    val presentThreshold = _uiState.value.matchThreshold
                                    val (nextState, nextCount) =
                                        AnchorPresenceEvaluator.transitionState(
                                            currentState = curState,
                                            consecutiveCount = curCount,
                                            matchRatio = matchRatio,
                                            cutoutId = layout.id,
                                            presentThreshold = presentThreshold,
                                        )
                                    curState = nextState
                                    curCount = nextCount
                                    _uiState.update {
                                        it.copy(
                                            pointMatches = pointResults,
                                            currentMatchRatio = matchRatio,
                                            isAnchorActive = (curState == AnchorPresenceState.PRESENT),
                                        )
                                    }
                                }
                            }
                        }

                        delay(TEST_SAMPLE_INTERVAL_MS)
                    }
                } finally {
                    reusableLiveBitmap?.let {
                        if (!it.isRecycled) it.recycle()
                    }
                }
            }
    }

    /**
     * Terminates the active test session, cleans up memory, and restores suspended modal.
     */
    fun stopTesting(resumeSuspended: Boolean = true) {
        val wasTesting = _uiState.value.isTesting
        if (wasTesting) {
            AppLog.i(TAG, "Stopping anchor test session (resumeSuspended=$resumeSuspended)")
            testJob?.cancel()
            testJob = null
            setReferenceBitmap(null)
            setLiveCropBitmap(null)
            _uiState.value = AnchorTestUiState()

            if (resumeSuspended) {
                AppStateManager.resumeSuspended()
            }
            onUpdateLayoutCallback = null
            activeTestLayout = null
        }
    }

    /**
     * Updates the required match threshold on the fly during active testing.
     * Persists the adjustment back to the layout via [onUpdateLayoutCallback].
     */
    fun updateMatchThreshold(newThreshold: Float) {
        val clamped =
            newThreshold.coerceIn(
                MIN_LAYOUT_ANCHOR_MATCH_THRESHOLD,
                MAX_LAYOUT_ANCHOR_MATCH_THRESHOLD,
            )
        AppLog.d(TAG, "Anchor test match threshold updated: $clamped (layout=${activeTestLayout?.id})")
        _uiState.update { it.copy(matchThreshold = clamped) }
        activeTestLayout?.let { curLayout ->
            val updated =
                curLayout.copy(
                    visualAnchor = curLayout.visualAnchor.copy(matchThreshold = clamped),
                )
            activeTestLayout = updated
            onUpdateLayoutCallback?.invoke(updated)
        }
    }
}
