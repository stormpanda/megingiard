package com.stormpanda.megingiard.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

private const val TAG = "PrimaryOverlayInputBridge"

private const val STICK_DEADZONE = 0.5f
private const val STICK_REPEAT_INTERVAL_MS = 180L
private val FOCUS_BORDER_WIDTH = 2.dp
private val FOCUS_CORNER_RADIUS = 8.dp

/**
 * Direction for shoulder bumper tab navigation.
 */
enum class BumperDirection {
    PREV,
    NEXT,
}

/**
 * Bridge singleton for dispatching gamepad events (bumpers, joystick translation)
 * to primary screen overlay menus and dialogs.
 */
object PrimaryOverlayInputBridge {
    private val _bumperEvents = MutableSharedFlow<BumperDirection>(extraBufferCapacity = 16)
    val bumperEvents: SharedFlow<BumperDirection> = _bumperEvents.asSharedFlow()

    private var lastJoystickMotionMs = 0L
    private var lastJoystickKeyCode = 0

    fun sendBumper(direction: BumperDirection) {
        AppLog.d(TAG, "sendBumper: direction=$direction")
        _bumperEvents.tryEmit(direction)
    }

    /**
     * Translates continuous analog stick / hat switch movements into discrete D-pad KeyEvents
     * with deadzone filtering and repeat throttling.
     *
     * @param event The generic motion event from the gamepad.
     * @param onDpadKey Callback receiving the translated [KeyEvent.KEYCODE_DPAD_*].
     * @return true if the event was handled as a navigation movement.
     */
    fun processGenericMotionEvent(
        event: MotionEvent,
        onDpadKey: (Int) -> Unit,
    ): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
            (event.source and InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD
        ) {
            return false
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

        if (targetKeyCode == 0) {
            lastJoystickKeyCode = 0
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (targetKeyCode != lastJoystickKeyCode || (now - lastJoystickMotionMs) >= STICK_REPEAT_INTERVAL_MS) {
            lastJoystickKeyCode = targetKeyCode
            lastJoystickMotionMs = now
            onDpadKey(targetKeyCode)
            return true
        }

        return true
    }

    /**
     * Resets the joystick state tracker.
     */
    fun resetJoystickState() {
        AppLog.d(TAG, "resetJoystickState")
        lastJoystickKeyCode = 0
        lastJoystickMotionMs = 0L
    }
}

/**
 * Modifier that equips any composable on the primary screen overlay with:
 * 1. 2D Focus capability ([Modifier.focusable]).
 * 2. Visual focus ring indicator using the theme's accent color when focused.
 * 3. Gamepad Button A ([KeyEvent.KEYCODE_BUTTON_A]) and Enter activation for [onClick].
 */
fun Modifier.primaryOverlayFocusable(
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(FOCUS_CORNER_RADIUS),
    borderWidth: Dp = FOCUS_BORDER_WIDTH,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
): Modifier =
    composed {
        val colors = LocalAppColors.current
        val effectiveInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val isFocused by effectiveInteractionSource.collectIsFocusedAsState()

        val focusBorderModifier =
            if (isFocused) {
                Modifier.border(borderWidth, colors.accent, shape)
            } else {
                Modifier
            }

        val keyHandlerModifier =
            if (onClick != null) {
                Modifier.onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyUp &&
                        (
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                        )
                    ) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            }

        val focusableOrClickModifier =
            if (onClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    interactionSource = effectiveInteractionSource,
                    indication = null,
                    onClick = onClick,
                )
            } else {
                Modifier.focusable(
                    enabled = enabled,
                    interactionSource = effectiveInteractionSource,
                )
            }

        this
            .then(focusableOrClickModifier)
            .then(focusBorderModifier)
            .then(keyHandlerModifier)
    }
