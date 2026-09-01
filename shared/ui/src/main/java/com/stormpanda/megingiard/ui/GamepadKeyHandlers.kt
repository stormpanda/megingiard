package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

private const val TAG = "GamepadKeyHandlers"

/** Returns true if the key code represents a back/escape/cancel action. */
fun isBackKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BUTTON_B ||
        keyCode == KeyEvent.KEYCODE_BACK ||
        keyCode == KeyEvent.KEYCODE_ESCAPE

/** Returns true if the key event is a KeyDown action representing back/escape/cancel. */
fun ComposeKeyEvent.isBackKeyDown(): Boolean = type == KeyEventType.KeyDown && isBackKey(nativeKeyEvent.keyCode)

/**
 * Detects pointer press and release events in the [PointerEventPass.Initial] pass, ensuring
 * pointer tracking is scoped to touches that originated inside the bounds of the component.
 */
suspend fun PointerInputScope.detectHoldPointerEvents(
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    awaitPointerEventScope {
        val activePids = mutableSetOf<PointerId>()
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            for (change in event.changes) {
                val pid = change.id
                when (event.type) {
                    PointerEventType.Press -> {
                        if (!change.previousPressed &&
                            change.position.x in 0f..size.width.toFloat() &&
                            change.position.y in 0f..size.height.toFloat()
                        ) {
                            activePids += pid
                            onPress()
                            change.consume()
                        }
                    }

                    PointerEventType.Release -> {
                        if (!change.pressed && pid in activePids) {
                            activePids -= pid
                            if (activePids.isEmpty()) onRelease()
                            change.consume()
                        }
                    }

                    PointerEventType.Move -> {
                        if (pid in activePids) {
                            change.consume()
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }
}

const val GAMEPAD_REPEAT_INITIAL_DELAY_MS = 250L
const val GAMEPAD_REPEAT_START_DELAY_MS = 100L
const val GAMEPAD_REPEAT_MIN_DELAY_MS = 20L
const val GAMEPAD_REPEAT_ACCEL_FACTOR = 0.85f

/**
 * Launches an accelerated repeating coroutine loop for directional D-pad holds.
 */
fun CoroutineScope.launchDirectionalRepeat(
    keyCode: Int,
    isActiveCheck: () -> Boolean,
    action: () -> Unit,
): Job =
    launch {
        delay(GAMEPAD_REPEAT_INITIAL_DELAY_MS)
        var delayMs = GAMEPAD_REPEAT_START_DELAY_MS
        while (isActive && isActiveCheck()) {
            action()
            delay(delayMs)
            delayMs = max(GAMEPAD_REPEAT_MIN_DELAY_MS, (delayMs * GAMEPAD_REPEAT_ACCEL_FACTOR).toLong())
        }
    }

/**
 * Handles 2D directional adjustment and repeating key events (D-pad Up/Down/Left/Right, A/B/Enter/Back dismiss).
 */
fun handle2DAdjustmentKeyEvent(
    keyEvent: ComposeKeyEvent,
    isAdjusting: Boolean,
    onStartAdjusting: (keyCode: Int, dirX: Int, dirY: Int) -> Unit,
    onStopAdjusting: (keyCode: Int) -> Unit,
    onDismissAdjustment: () -> Unit,
    onModifierKeyDown: ((keyCode: Int) -> Boolean)? = null,
    onModifierKeyUp: ((keyCode: Int) -> Boolean)? = null,
): Boolean {
    if (!isAdjusting) return false
    val keyCode = keyEvent.nativeKeyEvent.keyCode
    return if (keyEvent.type == KeyEventType.KeyDown) {
        if (onModifierKeyDown?.invoke(keyCode) == true) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                onStartAdjusting(keyCode, 0, -1)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                onStartAdjusting(keyCode, 0, 1)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                onStartAdjusting(keyCode, -1, 0)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                onStartAdjusting(keyCode, 1, 0)
                true
            }

            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                onDismissAdjustment()
                true
            }

            else -> {
                false
            }
        }
    } else if (keyEvent.type == KeyEventType.KeyUp) {
        if (onModifierKeyUp?.invoke(keyCode) == true) return true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                onStopAdjusting(keyCode)
                true
            }

            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                true
            }

            else -> {
                false
            }
        }
    } else {
        false
    }
}

/**
 * Handles standardized gamepad adjustment key events (D-pad Left/Right adjustment, A/B/Enter dismiss).
 */
fun handleAdjustmentKeyEvent(
    keyEvent: ComposeKeyEvent,
    isAdjusting: Boolean,
    onAdjustLeft: () -> Unit,
    onAdjustRight: () -> Unit,
    onDismissAdjustment: () -> Unit,
): Boolean {
    if (!isAdjusting) return false
    val keyCode = keyEvent.nativeKeyEvent.keyCode
    return if (keyEvent.type == KeyEventType.KeyDown) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                onAdjustLeft()
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                onAdjustRight()
                true
            }

            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                AppLog.d(TAG, "handleAdjustmentKeyEvent: dismissing adjustment mode on keyCode=$keyCode")
                onDismissAdjustment()
                true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                AppLog.d(TAG, "handleAdjustmentKeyEvent: navigating away from adjustment mode on keyCode=$keyCode")
                onDismissAdjustment()
                false
            }

            else -> {
                false
            }
        }
    } else if (keyEvent.type == KeyEventType.KeyUp) {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            -> true

            else -> false
        }
    } else {
        false
    }
}
