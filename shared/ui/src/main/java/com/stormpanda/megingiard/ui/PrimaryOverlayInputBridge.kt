package com.stormpanda.megingiard.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.nextItem
import com.stormpanda.megingiard.math.prevItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

private const val TAG = "PrimaryOvlyInputBridge"

private const val STICK_DEADZONE = 0.5f
private const val REPEAT_START_DELAY_MS = 120L
private const val REPEAT_MIN_DELAY_MS = 60L
private const val REPEAT_ACCEL_FACTOR = 0.90f
private val FOCUS_BORDER_WIDTH = 2.dp
private val ZERO_BORDER_WIDTH = 0.dp
private val FOCUS_CORNER_RADIUS = 8.dp
private val DEFAULT_FOCUS_SHAPE = RoundedCornerShape(FOCUS_CORNER_RADIUS)

/**
 * Direction for shoulder bumper tab navigation.
 */
enum class BumperDirection {
    PREV,
    NEXT,
}

/**
 * Cycles to previous or next item in the collection based on [BumperDirection].
 */
fun <T> List<T>.cycle(
    currentItem: T,
    direction: BumperDirection,
): T =
    when (direction) {
        BumperDirection.PREV -> prevItem(currentItem)
        BumperDirection.NEXT -> nextItem(currentItem)
    }

/**
 * Bridge singleton for dispatching gamepad events (bumpers, joystick translation)
 * to primary screen overlay menus and dialogs.
 */
object PrimaryOverlayInputBridge {
    private val _bumperEvents = MutableSharedFlow<BumperDirection>(extraBufferCapacity = 16)
    val bumperEvents: SharedFlow<BumperDirection> = _bumperEvents.asSharedFlow()

    private val _focusRecoveryEvents = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val focusRecoveryEvents: SharedFlow<Int> = _focusRecoveryEvents.asSharedFlow()

    private val _isGamepadInputActive = MutableStateFlow(true)
    val isGamepadInputActive: StateFlow<Boolean> = _isGamepadInputActive.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var repeatJob: Job? = null
    private var lastJoystickKeyCode = 0
    private var lastL2Pressed = false
    private var lastR2Pressed = false

    /**
     * Marks hardware gamepad or directional keyboard navigation as the active input modality.
     */
    fun notifyGamepadInput() {
        if (!_isGamepadInputActive.value) {
            AppLog.d(TAG, "notifyGamepadInput: switching active input mode to gamepad")
            _isGamepadInputActive.value = true
        }
    }

    /**
     * Marks direct touch or pointer interaction as the active input modality so sub-page
     * transitions do not force keyboard focus mode.
     */
    fun notifyTouchInput() {
        if (_isGamepadInputActive.value) {
            AppLog.d(TAG, "notifyTouchInput: switching active input mode to touch")
            _isGamepadInputActive.value = false
        }
    }

    fun sendBumper(direction: BumperDirection) {
        notifyGamepadInput()
        AppLog.d(TAG, "sendBumper: direction=$direction")
        _bumperEvents.tryEmit(direction)
    }

    /**
     * Dispatches a focus recovery request when unhandled directional keys or action buttons occur
     * (e.g. after touch/mouse pointer interaction cleared Compose focus).
     */
    fun sendFocusRecovery(keyCode: Int) {
        notifyGamepadInput()
        AppLog.d(TAG, "sendFocusRecovery: keyCode=$keyCode")
        _focusRecoveryEvents.tryEmit(keyCode)
    }

    /**
     * Translates continuous analog stick / hat switch movements into discrete D-pad KeyEvents
     * with deadzone filtering, stateful press/release transitions, and accelerating repeat.
     *
     * @param event The generic motion event from the gamepad.
     * @param onDpadKey Callback receiving the action ([KeyEvent.ACTION_DOWN] / [KeyEvent.ACTION_UP]) and translated key codes.
     * @return true if the event was handled as a navigation or modifier movement.
     */
    fun processGenericMotionEvent(
        event: MotionEvent,
        onDpadKey: (action: Int, keyCode: Int) -> Unit,
    ): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
            (event.source and InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD
        ) {
            return false
        }

        val brake = event.getAxisValue(MotionEvent.AXIS_BRAKE)
        val ltrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val isL2Pressed = brake > STICK_DEADZONE || ltrigger > STICK_DEADZONE

        if (isL2Pressed != lastL2Pressed) {
            notifyGamepadInput()
            lastL2Pressed = isL2Pressed
            onDpadKey(if (isL2Pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2)
        }

        val gas = event.getAxisValue(MotionEvent.AXIS_GAS)
        val rtrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        val isR2Pressed = gas > STICK_DEADZONE || rtrigger > STICK_DEADZONE

        if (isR2Pressed != lastR2Pressed) {
            notifyGamepadInput()
            lastR2Pressed = isR2Pressed
            onDpadKey(if (isR2Pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2)
        }

        val axisX = event.getAxisValue(MotionEvent.AXIS_X).let { if (abs(it) > STICK_DEADZONE) it else 0f }
        val axisY = event.getAxisValue(MotionEvent.AXIS_Y).let { if (abs(it) > STICK_DEADZONE) it else 0f }
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X).let { if (abs(it) > STICK_DEADZONE) it else 0f }
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y).let { if (abs(it) > STICK_DEADZONE) it else 0f }

        val effX = if (hatX != 0f) hatX else axisX
        val effY = if (hatY != 0f) hatY else axisY

        val targetKeyCode =
            when {
                effY < -STICK_DEADZONE -> KeyEvent.KEYCODE_DPAD_UP
                effY > STICK_DEADZONE -> KeyEvent.KEYCODE_DPAD_DOWN
                effX < -STICK_DEADZONE -> KeyEvent.KEYCODE_DPAD_LEFT
                effX > STICK_DEADZONE -> KeyEvent.KEYCODE_DPAD_RIGHT
                else -> 0
            }

        if (targetKeyCode == lastJoystickKeyCode) {
            return targetKeyCode != 0 || isL2Pressed || isR2Pressed
        }

        repeatJob?.cancel()
        repeatJob = null

        if (lastJoystickKeyCode != 0) {
            val oldKey = lastJoystickKeyCode
            lastJoystickKeyCode = 0
            onDpadKey(KeyEvent.ACTION_UP, oldKey)
        }

        if (targetKeyCode != 0) {
            notifyGamepadInput()
            lastJoystickKeyCode = targetKeyCode
            onDpadKey(KeyEvent.ACTION_DOWN, targetKeyCode)
            repeatJob =
                scope.launchDirectionalRepeat(
                    keyCode = targetKeyCode,
                    isActiveCheck = { lastJoystickKeyCode == targetKeyCode },
                    startDelayMs = REPEAT_START_DELAY_MS,
                    minDelayMs = REPEAT_MIN_DELAY_MS,
                    accelFactor = REPEAT_ACCEL_FACTOR,
                ) {
                    onDpadKey(KeyEvent.ACTION_DOWN, targetKeyCode)
                }
        }

        return true
    }

    /**
     * Resets the joystick and input-modality state tracker.
     */
    fun resetJoystickState() {
        AppLog.d(TAG, "resetJoystickState")
        repeatJob?.cancel()
        repeatJob = null
        lastJoystickKeyCode = 0
        lastL2Pressed = false
        lastR2Pressed = false
        _isGamepadInputActive.value = true
    }
}

/**
 * Modifier that equips any composable on the primary screen overlay with:
 * 1. A single 2D focus target ([Modifier.clickable] when touch-clickable, or [Modifier.focusable] otherwise).
 * 2. Visual focus ring indicator using the theme's accent color when focused.
 * 3. Top-down [Modifier.onPreviewKeyEvent] activation for Gamepad Button A, D-Pad Center, and Enter,
 *    decoupled from direct pointer taps ([onTouchClick]).
 */
fun Modifier.primaryOverlayFocusable(
    onClick: (() -> Unit)? = null,
    onTouchClick: (() -> Unit)? = onClick,
    shape: Shape = DEFAULT_FOCUS_SHAPE,
    borderWidth: Dp = FOCUS_BORDER_WIDTH,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
): Modifier =
    composed {
        val colors = LocalAppColors.current
        val effectiveInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val isFocused by effectiveInteractionSource.collectIsFocusedAsState()
        var lastActionDownKeyCode by remember { mutableIntStateOf(0) }

        val focusBorderModifier =
            if (isFocused && borderWidth > ZERO_BORDER_WIDTH) {
                Modifier.border(borderWidth, colors.accent, shape)
            } else {
                Modifier
            }

        val keyHandlerModifier =
            if (onClick != null) {
                Modifier.onPreviewKeyEvent { keyEvent ->
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    val isActivationKey =
                        keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER ||
                            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    if (!isActivationKey) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            lastActionDownKeyCode = 0
                        }
                        false
                    } else if (keyEvent.type == KeyEventType.KeyDown) {
                        lastActionDownKeyCode = keyCode
                        true
                    } else if (keyEvent.type == KeyEventType.KeyUp) {
                        val wasDownOnNode = lastActionDownKeyCode == keyCode
                        lastActionDownKeyCode = 0
                        if (wasDownOnNode && enabled) {
                            onClick()
                        }
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            }

        val focusableOrClickModifier =
            if (onTouchClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    interactionSource = effectiveInteractionSource,
                    indication = null,
                    onClick = onTouchClick,
                )
            } else {
                Modifier.focusable(
                    enabled = enabled,
                    interactionSource = effectiveInteractionSource,
                )
            }

        this
            .then(focusBorderModifier)
            .then(keyHandlerModifier)
            .then(focusableOrClickModifier)
    }
