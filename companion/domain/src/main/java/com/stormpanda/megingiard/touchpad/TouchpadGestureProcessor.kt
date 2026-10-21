package com.stormpanda.megingiard.touchpad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.macropad.MouseButton
import com.stormpanda.megingiard.settings.TouchpadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "TouchpadGestureProc"

private const val TP_MOUSE_SENSITIVITY = 2f
private const val TP_TAP_TIMEOUT_MS = 200L
private const val TP_TAP_SLOP_PX = 20f
private const val TP_CLICK_DURATION_MS = 40L
private const val TP_DOUBLE_TAP_TIMEOUT_MS = 500L
private const val TP_SENSITIVITY_MAX = 10f
private const val TP_SCROLL_THRESHOLD_BASE_PX = 12f
private const val MAX_TOUCH_SLOTS = 10

data class TouchpadGestureConfig(
    val useMouse: Boolean = true,
    val sensitivity: Float = 1.0f,
    val twoFingerScrollEnabled: Boolean = true,
    val naturalScrollEnabled: Boolean = true,
    val scrollSpeed: Float = 1.0f,
    val tapToClick: Boolean = true,
    val twoFingerTap: Boolean = true,
    val threeFingerTap: Boolean = true,
    val tapDrag: Boolean = true,
)

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
    private val scope: CoroutineScope,
    private val config: () -> TouchpadGestureConfig = { TouchpadGestureConfig() },
    private val onHapticFeedback: () -> Unit = {},
) {
    private val _hasActivePointers = MutableStateFlow(false)
    val hasActivePointers: StateFlow<Boolean> = _hasActivePointers.asStateFlow()

    // ── Touch mode state ────────────────────────────────────────────────────
    private val pointerToSlotMap = HashMap<Long, Int>()

    // ── Mouse mode state ────────────────────────────────────────────────────
    private val pressTimes = HashMap<Long, Long>()
    private val downPositions = LinkedHashMap<Long, Pair<Float, Float>>()
    private val movedTooFar = HashSet<Long>()
    private val releasedAsTap = ArrayList<Long>()
    private var scrollAccumY = 0f
    private var moveRemainderX = 0f
    private var moveRemainderY = 0f
    private var wasTwoFingerScrolling = false
    private var lastTapReleaseTime = 0L
    private var isDragging = false
    private var pendingClickJob: Job? = null

    private fun updateActivePointers() {
        _hasActivePointers.value = downPositions.isNotEmpty() || pointerToSlotMap.isNotEmpty()
    }

    private fun resetMousePointers() {
        releasedAsTap.clear()
        pressTimes.clear()
        downPositions.clear()
        movedTooFar.clear()
        moveRemainderX = 0f
        moveRemainderY = 0f
        wasTwoFingerScrolling = false
        updateActivePointers()
    }

    private fun clickButton(button: MouseButton) {
        onHapticFeedback()
        pendingClickJob?.cancel()
        var jobRef: Job? = null
        val job =
            scope.launch {
                var downSent = false
                try {
                    MouseInjector.buttonDown(button)
                    downSent = true
                    delay(TP_CLICK_DURATION_MS)
                } finally {
                    if (downSent && (button != MouseButton.LEFT || !isDragging)) {
                        MouseInjector.buttonUp(button)
                    }
                    if (pendingClickJob === jobRef) {
                        pendingClickJob = null
                    }
                }
            }
        jobRef = job
        pendingClickJob = job
    }

    /**
     * Handle a Press event.
     *
     * @param pointerId    unique ID of the newly-pressed pointer
     * @param x            pointer X in surface pixels
     * @param y            pointer Y in surface pixels
     * @param surfaceW     width of the touch surface in pixels
     * @param surfaceH     height of the touch surface in pixels
     */
    fun onPress(
        pointerId: Long,
        x: Float,
        y: Float,
        surfaceW: Float,
        surfaceH: Float,
    ) {
        if (downPositions.containsKey(pointerId) || pointerToSlotMap.containsKey(pointerId)) {
            AppLog.w(TAG, "onPress called for already-active pointerId=$pointerId; releasing stale state")
            onRelease(pointerId, x, y, surfaceW, surfaceH)
        }
        val currentConfig = config()
        if (currentConfig.useMouse) {
            pressTimes[pointerId] = System.currentTimeMillis()
            downPositions[pointerId] = Pair(x, y)
            moveRemainderX = 0f
            moveRemainderY = 0f
            if (downPositions.size != 2) {
                scrollAccumY = 0f
            }
            if (currentConfig.tapDrag && downPositions.size == 1) {
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
                        MouseInjector.leftDown()
                    }
                }
            }
        } else {
            val slot = (0 until MAX_TOUCH_SLOTS).firstOrNull { !pointerToSlotMap.containsValue(it) }
            if (slot != null) {
                pointerToSlotMap[pointerId] = slot
                val nx = (x / surfaceW).coerceIn(0f, 1f)
                val ny = (y / surfaceH).coerceIn(0f, 1f)
                TouchInjector.injectTouch(slot, TouchAction.DOWN, nx, ny)
            }
        }
        updateActivePointers()
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
        val currentConfig = config()
        if (currentConfig.useMouse) {
            val initPos = downPositions[pointerId] ?: return
            // Disqualify as tap if slop exceeded
            if (pointerId !in movedTooFar) {
                val dx = x - initPos.first
                val dy = y - initPos.second
                if (dx * dx + dy * dy > TP_TAP_SLOP_PX * TP_TAP_SLOP_PX) {
                    movedTooFar.add(pointerId)
                }
            }
            val isPrimary = downPositions.keys.firstOrNull() == pointerId
            if (currentConfig.twoFingerScrollEnabled && downPositions.size == 2) {
                moveRemainderX = 0f
                moveRemainderY = 0f
                if (pointerId in movedTooFar) {
                    wasTwoFingerScrolling = true
                }
                if (isPrimary) {
                    scrollAccumY += deltaY
                    val speed = currentConfig.scrollSpeed.coerceIn(TouchpadSettings.SCROLL_SPEED_MIN, TouchpadSettings.SCROLL_SPEED_MAX)
                    val scrollThreshold = TP_SCROLL_THRESHOLD_BASE_PX / speed // Sensitivity threshold in pixels
                    val units = (scrollAccumY / scrollThreshold).toInt()
                    if (units != 0) {
                        wasTwoFingerScrolling = true
                        val directionMultiplier = if (currentConfig.naturalScrollEnabled) 1 else -1
                        MouseInjector.scrollWheel(units * directionMultiplier)
                        scrollAccumY -= units * scrollThreshold
                    }
                }
            } else if (isPrimary && !wasTwoFingerScrolling) {
                val sens = currentConfig.sensitivity.coerceIn(TouchpadSettings.SENSITIVITY_MIN, TP_SENSITIVITY_MAX)
                moveRemainderX += deltaX * TP_MOUSE_SENSITIVITY * sens
                moveRemainderY += deltaY * TP_MOUSE_SENSITIVITY * sens
                val dx = moveRemainderX.toInt()
                val dy = moveRemainderY.toInt()
                if (dx != 0 || dy != 0) {
                    moveRemainderX -= dx
                    moveRemainderY -= dy
                    MouseInjector.moveMouse(dx, dy)
                }
            }
        } else {
            val slot = pointerToSlotMap[pointerId]
            if (slot != null) {
                val nx = (x / surfaceW).coerceIn(0f, 1f)
                val ny = (y / surfaceH).coerceIn(0f, 1f)
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
        val currentConfig = config()
        if (currentConfig.useMouse) {
            if (!downPositions.containsKey(pointerId)) return
            val pressTime = pressTimes.remove(pointerId)
            downPositions.remove(pointerId)
            val disqualified = movedTooFar.remove(pointerId)
            val isTap =
                pressTime != null &&
                    !disqualified &&
                    (System.currentTimeMillis() - pressTime) < TP_TAP_TIMEOUT_MS
            AppLog.d(TAG, "onRelease: pressTime=$pressTime, disqualified=$disqualified, isTap=$isTap")
            if (isTap) releasedAsTap.add(pointerId)
            if (downPositions.size != 2) {
                scrollAccumY = 0f
            }

            if (isDragging) {
                isDragging = false
                resetMousePointers()
                AppLog.d(TAG, "onRelease: ending drag lock")
                MouseInjector.leftUp()
                return
            }

            // When all fingers are up, evaluate taps
            if (downPositions.isEmpty()) {
                val tapCount = if (wasTwoFingerScrolling) 0 else releasedAsTap.size
                resetMousePointers()
                when {
                    tapCount == 1 && currentConfig.tapToClick -> {
                        lastTapReleaseTime = System.currentTimeMillis()
                        clickButton(MouseButton.LEFT)
                    }

                    tapCount == 2 && currentConfig.twoFingerTap -> {
                        clickButton(MouseButton.RIGHT)
                    }

                    tapCount == 3 && currentConfig.threeFingerTap -> {
                        clickButton(MouseButton.MIDDLE)
                    }
                }
            } else {
                updateActivePointers()
            }
        } else {
            val slot = pointerToSlotMap.remove(pointerId)
            if (slot != null) {
                val nx = (x / surfaceW).coerceIn(0f, 1f)
                val ny = (y / surfaceH).coerceIn(0f, 1f)
                TouchInjector.injectTouch(slot, TouchAction.UP, nx, ny)
            }
            updateActivePointers()
        }
    }

    /**
     * Resets the processor state when the gesture stream is cancelled, mode changes, or leaves composition.
     */
    fun onCancel() {
        // Unconditionally clean up Mouse mode state synchronously
        val wasDragging = isDragging
        isDragging = false
        resetMousePointers()
        scrollAccumY = 0f
        pendingClickJob?.cancel()
        pendingClickJob = null
        if (wasDragging) {
            MouseInjector.leftUp()
        }

        // Unconditionally clean up Touch mode state synchronously
        val slotsToRelease = pointerToSlotMap.entries.toList()
        pointerToSlotMap.clear()
        for ((_, slot) in slotsToRelease) {
            TouchInjector.injectTouch(slot, TouchAction.UP, 0f, 0f)
        }
        updateActivePointers()
    }
}
