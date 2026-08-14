package com.stormpanda.megingiard.touchpad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "TouchpadGestureProc"

private const val TP_MOUSE_SENSITIVITY = 2f
private const val TP_TAP_TIMEOUT_MS = 200L
private const val TP_TAP_SLOP_PX = 20f
private const val TP_CLICK_DURATION_MS = 40L
private const val TP_DOUBLE_TAP_TIMEOUT_MS = 500L
private const val TP_SENSITIVITY_MIN = 0.1f
private const val TP_SENSITIVITY_MAX = 10f
private const val TP_SCROLL_SPEED_MIN = 0.1f
private const val TP_SCROLL_SPEED_MAX = 10.0f
private const val TP_SCROLL_THRESHOLD_BASE_PX = 12f
private const val MAX_TOUCH_SLOTS = 10

/**
 * Gesture processor for the virtual touchpad, supporting two input modes:
 *
 * **Mouse mode:** Relative cursor movement with tap-to-click detection.
 *   - Single finger drag → mouse cursor move
 *   - Single-finger tap → left click (if `tapToClick` enabled)
 *   - Two-finger tap → right click (if `twoFingerTap` enabled)
 *
 * **Touch mode:** Absolute position forwarding to the native touch injector.
 *   - Touch coordinates normalised to the surface bounds.
 *
 * This class is Compose-free. The UI creates one instance per pointer-input
 * scope and dispatches raw pointer events through the `on*` methods.
 */
class TouchpadGestureProcessor(
    private val useMouse: () -> Boolean,
    private val scope: CoroutineScope,
    private val sensitivity: () -> Float = { 1.0f },
    private val twoFingerScrollEnabled: () -> Boolean = { true },
    private val naturalScrollEnabled: () -> Boolean = { true },
    private val scrollSpeed: () -> Float = { 1.0f },
    private val tapToClick: () -> Boolean = { true },
    private val twoFingerTap: () -> Boolean = { true },
    private val threeFingerTap: () -> Boolean = { true },
    private val tapDrag: () -> Boolean = { true },
    private val onHapticFeedback: () -> Unit = {},
) {
    private fun getSensitivity(): Float {
        val s = sensitivity()
        return if (s.isFinite() && s > 0f) {
            s.coerceIn(TP_SENSITIVITY_MIN, TP_SENSITIVITY_MAX)
        } else {
            1.0f
        }
    }

    private fun getScrollSpeed(): Float {
        val s = scrollSpeed()
        return if (s.isFinite() && s > 0f) {
            s.coerceIn(TP_SCROLL_SPEED_MIN, TP_SCROLL_SPEED_MAX)
        } else {
            1.0f
        }
    }

    // ── Touch mode state ────────────────────────────────────────────────────
    private val _touchPos = MutableStateFlow<Pair<Float, Float>?>(null)

    /** Current finger position (touch mode only), null when not touching. */
    val touchPos: StateFlow<Pair<Float, Float>?> = _touchPos.asStateFlow()

    private val pointerToSlotMap = HashMap<Long, Int>()
    private val activeSlots = BooleanArray(MAX_TOUCH_SLOTS) { false }

    // ── Mouse mode state ────────────────────────────────────────────────────
    private val pressTimes = HashMap<Long, Long>()
    private val downPositions = HashMap<Long, Pair<Float, Float>>()
    private val movedTooFar = HashSet<Long>()
    private val releasedAsTap = ArrayList<Long>()
    private var primaryPointer: Long? = null
    private var scrollAccumY = 0f
    private var lastTapReleaseTime = 0L
    private var isDragging = false
    private var pendingClickJob: Job? = null

    /**
     * Handle a Press event.
     *
     * @param pointerId    unique ID of the newly-pressed pointer
     * @param x            pointer X in surface pixels
     * @param y            pointer Y in surface pixels
     * @param surfaceW     width of the touch surface in pixels
     * @param surfaceH     height of the touch surface in pixels
     * @param overlayOpen  true if the quick menu overlay is currently visible
     */
    fun onPress(
        pointerId: Long,
        x: Float,
        y: Float,
        surfaceW: Float,
        surfaceH: Float,
        overlayOpen: Boolean,
    ) {
        if (overlayOpen) {
            AppStateManager.closeQuickMenu()
            return
        }

        if (useMouse()) {
            pressTimes[pointerId] = System.currentTimeMillis()
            downPositions[pointerId] = Pair(x, y)
            if (primaryPointer == null) primaryPointer = pointerId
            if (downPositions.size != 2) {
                scrollAccumY = 0f
            }
            if (tapDrag() && downPositions.size == 1) {
                val now = System.currentTimeMillis()
                AppLog.d(TAG, "onPress: tapDrag=true, delta=${now - lastTapReleaseTime}ms, limit=$TP_DOUBLE_TAP_TIMEOUT_MS")
                if (now - lastTapReleaseTime < TP_DOUBLE_TAP_TIMEOUT_MS) {
                    isDragging = true
                    lastTapReleaseTime = 0L
                    AppLog.d(TAG, "onPress: starting drag lock")
                    onHapticFeedback()
                    val job = pendingClickJob
                    if (job != null && job.isActive) {
                        AppLog.d(TAG, "onPress: cancelling pending click job to inherit down state")
                        job.cancel()
                        pendingClickJob = null
                    } else {
                        scope.launch {
                            MouseInjector.leftDown()
                        }
                    }
                }
            }
        } else {
            var slot = -1
            for (i in 0 until MAX_TOUCH_SLOTS) {
                if (!activeSlots[i]) {
                    slot = i
                    break
                }
            }
            if (slot != -1) {
                activeSlots[slot] = true
                pointerToSlotMap[pointerId] = slot
                val nx = (x / surfaceW).coerceIn(0f, 1f)
                val ny = (y / surfaceH).coerceIn(0f, 1f)
                if (pointerToSlotMap.size == 1) {
                    _touchPos.value = Pair(x, y)
                }
                TouchInjector.injectTouch(slot, TouchAction.DOWN, nx, ny)
            }
        }
    }

    /**
     * Handle a Move event.
     *
     * @param pointerId  the pointer that moved
     * @param x          current pointer X
     * @param y          current pointer Y
     * @param deltaX     position change X since last event
     * @param deltaY     position change Y since last event
     * @param surfaceW   width of the touch surface in pixels
     * @param surfaceH   height of the touch surface in pixels
     */
    fun onMove(
        pointerId: Long,
        x: Float,
        y: Float,
        deltaX: Float,
        deltaY: Float,
        surfaceW: Float,
        surfaceH: Float,
    ) {
        if (useMouse()) {
            // Disqualify as tap if slop exceeded
            val initPos = downPositions[pointerId]
            if (initPos != null && pointerId !in movedTooFar) {
                val dx = x - initPos.first
                val dy = y - initPos.second
                if (dx * dx + dy * dy > TP_TAP_SLOP_PX * TP_TAP_SLOP_PX) {
                    movedTooFar.add(pointerId)
                }
            }
            if (twoFingerScrollEnabled() && downPositions.size == 2) {
                if (pointerId == primaryPointer) {
                    scrollAccumY += deltaY
                    val scrollThreshold = TP_SCROLL_THRESHOLD_BASE_PX / getScrollSpeed() // Sensitivity threshold in pixels
                    val units = (scrollAccumY / scrollThreshold).toInt()
                    if (units != 0) {
                        val directionMultiplier = if (naturalScrollEnabled()) 1 else -1
                        MouseInjector.scrollWheel(units * directionMultiplier)
                        scrollAccumY -= units * scrollThreshold
                    }
                }
            } else if (pointerId == primaryPointer) {
                val dx = (deltaX * TP_MOUSE_SENSITIVITY * getSensitivity()).roundToInt()
                val dy = (deltaY * TP_MOUSE_SENSITIVITY * getSensitivity()).roundToInt()
                if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
            }
        } else {
            val slot = pointerToSlotMap[pointerId]
            if (slot != null) {
                val nx = (x / surfaceW).coerceIn(0f, 1f)
                val ny = (y / surfaceH).coerceIn(0f, 1f)
                if (pointerToSlotMap.keys.firstOrNull() == pointerId) {
                    _touchPos.value = Pair(x, y)
                }
                TouchInjector.injectTouch(slot, TouchAction.MOVE, nx, ny)
            }
        }
    }

    /**
     * Handle a Release event.
     *
     * @param pointerId     the pointer that was released
     * @param x             final pointer X
     * @param y             final pointer Y
     * @param surfaceW      width of the touch surface
     * @param surfaceH      height of the touch surface
     */
    fun onRelease(
        pointerId: Long,
        x: Float,
        y: Float,
        surfaceW: Float,
        surfaceH: Float,
    ) {
        if (useMouse()) {
            val pressTime = pressTimes.remove(pointerId)
            downPositions.remove(pointerId)
            val disqualified = movedTooFar.remove(pointerId)
            val isTap =
                pressTime != null &&
                    !disqualified &&
                    (System.currentTimeMillis() - pressTime) < TP_TAP_TIMEOUT_MS
            AppLog.d(TAG, "onRelease: pressTime=$pressTime, disqualified=$disqualified, isTap=$isTap")
            if (pointerId == primaryPointer) {
                primaryPointer = null // caller should set new primary if needed
            }
            if (isTap) releasedAsTap.add(pointerId)
            if (downPositions.size != 2) {
                scrollAccumY = 0f
            }

            if (isDragging) {
                isDragging = false
                releasedAsTap.clear()
                pressTimes.clear()
                downPositions.clear()
                movedTooFar.clear()
                primaryPointer = null
                AppLog.d(TAG, "onRelease: ending drag lock")
                scope.launch {
                    MouseInjector.leftUp()
                }
                return
            }

            // When all fingers are up, evaluate taps
            val allPointersUp = downPositions.isEmpty()
            if (allPointersUp) {
                val tapCount = releasedAsTap.size
                releasedAsTap.clear()
                pressTimes.clear()
                downPositions.clear()
                movedTooFar.clear()
                primaryPointer = null
                when {
                    tapCount == 1 && tapToClick() -> {
                        lastTapReleaseTime = System.currentTimeMillis()
                        onHapticFeedback()
                        pendingClickJob =
                            scope.launch {
                                try {
                                    MouseInjector.leftDown()
                                    delay(TP_CLICK_DURATION_MS)
                                    MouseInjector.leftUp()
                                } finally {
                                    pendingClickJob = null
                                }
                            }
                    }

                    tapCount == 2 && twoFingerTap() -> {
                        onHapticFeedback()
                        scope.launch {
                            MouseInjector.rightDown()
                            delay(TP_CLICK_DURATION_MS)
                            MouseInjector.rightUp()
                        }
                    }

                    tapCount >= 3 && threeFingerTap() -> {
                        onHapticFeedback()
                        scope.launch {
                            MouseInjector.middleDown()
                            delay(TP_CLICK_DURATION_MS)
                            MouseInjector.middleUp()
                        }
                    }
                }
            }
        } else {
            val slot = pointerToSlotMap.remove(pointerId)
            if (slot != null) {
                activeSlots[slot] = false
                val nx = (x / surfaceW).coerceIn(0f, 1f)
                val ny = (y / surfaceH).coerceIn(0f, 1f)
                _touchPos.value = null
                TouchInjector.injectTouch(slot, TouchAction.UP, nx, ny)
            }
        }
    }

    /**
     * Resets the processor state when the gesture stream is cancelled, mode changes, or leaves composition.
     */
    fun onCancel() {
        // Unconditionally clean up Mouse mode state synchronously
        val wasDragging = isDragging
        isDragging = false
        releasedAsTap.clear()
        pressTimes.clear()
        downPositions.clear()
        movedTooFar.clear()
        primaryPointer = null
        scrollAccumY = 0f
        pendingClickJob?.cancel()
        pendingClickJob = null
        if (wasDragging) {
            MouseInjector.leftUp()
        }

        // Unconditionally clean up Touch mode state synchronously
        val slotsToRelease = pointerToSlotMap.entries.toList()
        pointerToSlotMap.clear()
        activeSlots.fill(false)
        _touchPos.value = null
        for ((_, slot) in slotsToRelease) {
            TouchInjector.injectTouch(slot, TouchAction.UP, 0f, 0f)
        }
    }

    /**
     * Designate a pointer as the new primary (for cursor movement in mouse mode).
     * Call when the previous primary pointer lifts and others are still held.
     */
    fun setPrimaryPointer(pointerId: Long) {
        primaryPointer = pointerId
    }
}
