package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
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
import kotlin.math.roundToInt

private const val TAG = "AnchorTestCoordinator"
private const val TEST_SAMPLE_INTERVAL_MS = 33L
private const val DEFAULT_SCREEN_WIDTH = 1920
private const val DEFAULT_SCREEN_HEIGHT = 1080

/**
 * Singleton coordinator managing interactive anchor presence testing.
 *
 * Leaves Display 0 completely unobstructed with zero overlays so gameplay continues at 120Hz,
 * while Display 4 displays live detection diagnostics and dual reference/current preview feeds.
 */
object AnchorTestCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var testJob: Job? = null

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _currentMatchRatio = MutableStateFlow(0f)
    val currentMatchRatio: StateFlow<Float> = _currentMatchRatio.asStateFlow()

    private val _isAnchorActive = MutableStateFlow(false)
    val isAnchorActive: StateFlow<Boolean> = _isAnchorActive.asStateFlow()

    private val _referenceBitmap = MutableStateFlow<Bitmap?>(null)
    val referenceBitmap: StateFlow<Bitmap?> = _referenceBitmap.asStateFlow()

    private val _liveCropBitmap = MutableStateFlow<Bitmap?>(null)
    val liveCropBitmap: StateFlow<Bitmap?> = _liveCropBitmap.asStateFlow()

    private val _pointMatches = MutableStateFlow<List<AnchorPointMatchResult>>(emptyList())
    val pointMatches: StateFlow<List<AnchorPointMatchResult>> = _pointMatches.asStateFlow()

    private val _matchedPointCount = MutableStateFlow(0)
    val matchedPointCount: StateFlow<Int> = _matchedPointCount.asStateFlow()

    private val _totalPointCount = MutableStateFlow(0)
    val totalPointCount: StateFlow<Int> = _totalPointCount.asStateFlow()

    private val _targetPoints = MutableStateFlow<List<AnchorPoint>>(emptyList())
    val targetPoints: StateFlow<List<AnchorPoint>> = _targetPoints.asStateFlow()

    private fun setReferenceBitmap(bitmap: Bitmap?) {
        val old = _referenceBitmap.value
        _referenceBitmap.value = bitmap
        if (old != null && old != bitmap) {
            synchronized(old) {
                if (!old.isRecycled) {
                    old.recycle()
                }
            }
        }
    }

    private fun setLiveCropBitmap(bitmap: Bitmap?) {
        val old = _liveCropBitmap.value
        _liveCropBitmap.value = bitmap
        if (old != null && old != bitmap) {
            synchronized(old) {
                if (!old.isRecycled) {
                    old.recycle()
                }
            }
        }
    }

    /**
     * Starts the live anchor test session for [layout].
     * Suspends the primary settings modal, unfreezes mirror stream, and samples at 30 Hz.
     */
    fun startTesting(
        context: Context,
        layout: PadLayout,
    ) {
        stopTesting(resumeSuspended = false)

        AppStateManager.suspendCurrentAndDismiss()
        if (ScreenCaptureManager.isFrozen.value) {
            ScreenCaptureManager.setFrozen(false)
        }

        _isTesting.value = true
        _currentMatchRatio.value = 0f
        _isAnchorActive.value = false
        _pointMatches.value = emptyList()
        _matchedPointCount.value = 0
        val points = layout.visualAnchor.signature?.points ?: emptyList()
        _targetPoints.value = points
        _totalPointCount.value = points.size

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
                                            val px = (u * crop.width).roundToInt().coerceIn(0, crop.width - 1)
                                            val py = (v * crop.height).roundToInt().coerceIn(0, crop.height - 1)
                                            crop.getPixel(px, py)
                                        }
                                    _pointMatches.value = pointResults
                                    val matchedCount = pointResults.count { it.isMatch }
                                    _matchedPointCount.value = matchedCount
                                    _totalPointCount.value = pointResults.size

                                    val matchRatio =
                                        if (pointResults.isNotEmpty()) {
                                            matchedCount.toFloat() / pointResults.size.toFloat()
                                        } else {
                                            0f
                                        }
                                    _currentMatchRatio.value = matchRatio

                                    val (nextState, nextCount) =
                                        AnchorPresenceEvaluator.transitionState(curState, curCount, matchRatio, layout.id)
                                    curState = nextState
                                    curCount = nextCount
                                    _isAnchorActive.value = (curState == AnchorPresenceState.PRESENT)
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
        val wasTesting = _isTesting.value
        if (wasTesting) {
            AppLog.i(TAG, "Stopping anchor test session (resumeSuspended=$resumeSuspended)")
            testJob?.cancel()
            testJob = null
            setReferenceBitmap(null)
            setLiveCropBitmap(null)
            _isTesting.value = false
            _isAnchorActive.value = false
            _currentMatchRatio.value = 0f
            _pointMatches.value = emptyList()
            _matchedPointCount.value = 0
            _totalPointCount.value = 0
            _targetPoints.value = emptyList()

            if (resumeSuspended) {
                AppStateManager.resumeSuspended()
            }
        }
    }
}
