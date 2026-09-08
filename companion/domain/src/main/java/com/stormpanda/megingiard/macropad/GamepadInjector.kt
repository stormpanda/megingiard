package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdGamepadInjector

private const val TAG = "GamepadInjector"

private const val JOYSTICK_DEFLECTION_MAX = 32767
private const val JOYSTICK_DEFLECTION_MIN = -32767
private const val JOYSTICK_DEFLECTION_CENTER = 0
private const val HAT_AXIS_X = 0
private const val HAT_AXIS_Y = 1
private const val HAT_DIR_POS = 1
private const val HAT_DIR_NEG = -1
private const val HAT_DIR_CENTER = 0

/**
 * Public facade for gamepad button injection via Privileged Mode merge.
 *
 * Direct physical gamepad merge requires [PrivdClient.isConnected]. Standalone virtual
 * uinput fallback is retired to prevent dual-controller conflicts on the AYN Thor.
 */
object GamepadInjector {
    val isRunning: Boolean
        get() = PrivdClient.isConnected

    fun buttonDown(btnCode: Int) {
        if (!PrivdClient.isConnected) {
            AppLog.w(TAG, "buttonDown($btnCode) dropped: Privd not connected")
            return
        }
        when (btnCode) {
            GamepadKeycodes.BTN_DPAD_UP -> {
                hat(HAT_AXIS_Y, HAT_DIR_NEG)
                PrivdGamepadInjector.buttonDown(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_DOWN -> {
                hat(HAT_AXIS_Y, HAT_DIR_POS)
                PrivdGamepadInjector.buttonDown(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_LEFT -> {
                hat(HAT_AXIS_X, HAT_DIR_NEG)
                PrivdGamepadInjector.buttonDown(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_POS)
                PrivdGamepadInjector.buttonDown(btnCode)
            }

            GamepadKeycodes.CODE_DPAD_UP_LEFT -> {
                hat(HAT_AXIS_X, HAT_DIR_NEG)
                hat(HAT_AXIS_Y, HAT_DIR_NEG)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_UP)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_LEFT)
            }

            GamepadKeycodes.CODE_DPAD_UP_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_POS)
                hat(HAT_AXIS_Y, HAT_DIR_NEG)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_UP)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_RIGHT)
            }

            GamepadKeycodes.CODE_DPAD_DOWN_LEFT -> {
                hat(HAT_AXIS_X, HAT_DIR_NEG)
                hat(HAT_AXIS_Y, HAT_DIR_POS)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_DOWN)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_LEFT)
            }

            GamepadKeycodes.CODE_DPAD_DOWN_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_POS)
                hat(HAT_AXIS_Y, HAT_DIR_POS)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_DOWN)
                PrivdGamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_RIGHT)
            }

            GamepadKeycodes.CODE_LS_UP -> {
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_LS_DOWN -> {
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_MAX)
            }

            GamepadKeycodes.CODE_LS_LEFT -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_LS_RIGHT -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_MAX)
            }

            GamepadKeycodes.CODE_LS_UP_LEFT -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_MIN)
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_LS_UP_RIGHT -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_MAX)
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_LS_DOWN_LEFT -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_MIN)
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_MAX)
            }

            GamepadKeycodes.CODE_LS_DOWN_RIGHT -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_MAX)
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_MAX)
            }

            GamepadKeycodes.CODE_RS_UP -> {
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_RS_DOWN -> {
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_MAX)
            }

            GamepadKeycodes.CODE_RS_LEFT -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_RS_RIGHT -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_MAX)
            }

            GamepadKeycodes.CODE_RS_UP_LEFT -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_MIN)
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_RS_UP_RIGHT -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_MAX)
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_MIN)
            }

            GamepadKeycodes.CODE_RS_DOWN_LEFT -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_MIN)
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_MAX)
            }

            GamepadKeycodes.CODE_RS_DOWN_RIGHT -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_MAX)
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_MAX)
            }

            else -> {
                PrivdGamepadInjector.buttonDown(btnCode)
            }
        }
    }

    fun buttonUp(btnCode: Int) {
        if (!PrivdClient.isConnected) return
        when (btnCode) {
            GamepadKeycodes.BTN_DPAD_UP, GamepadKeycodes.BTN_DPAD_DOWN -> {
                hat(HAT_AXIS_Y, HAT_DIR_CENTER)
                PrivdGamepadInjector.buttonUp(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_LEFT, GamepadKeycodes.BTN_DPAD_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_CENTER)
                PrivdGamepadInjector.buttonUp(btnCode)
            }

            GamepadKeycodes.CODE_DPAD_UP_LEFT,
            GamepadKeycodes.CODE_DPAD_UP_RIGHT,
            GamepadKeycodes.CODE_DPAD_DOWN_LEFT,
            GamepadKeycodes.CODE_DPAD_DOWN_RIGHT,
            -> {
                hat(HAT_AXIS_X, HAT_DIR_CENTER)
                hat(HAT_AXIS_Y, HAT_DIR_CENTER)
                PrivdGamepadInjector.buttonUp(GamepadKeycodes.BTN_DPAD_UP)
                PrivdGamepadInjector.buttonUp(GamepadKeycodes.BTN_DPAD_DOWN)
                PrivdGamepadInjector.buttonUp(GamepadKeycodes.BTN_DPAD_LEFT)
                PrivdGamepadInjector.buttonUp(GamepadKeycodes.BTN_DPAD_RIGHT)
            }

            GamepadKeycodes.CODE_LS_UP,
            GamepadKeycodes.CODE_LS_DOWN,
            -> {
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_CENTER)
            }

            GamepadKeycodes.CODE_LS_LEFT,
            GamepadKeycodes.CODE_LS_RIGHT,
            -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_CENTER)
            }

            GamepadKeycodes.CODE_LS_UP_LEFT,
            GamepadKeycodes.CODE_LS_UP_RIGHT,
            GamepadKeycodes.CODE_LS_DOWN_LEFT,
            GamepadKeycodes.CODE_LS_DOWN_RIGHT,
            -> {
                joystick(GamepadKeycodes.ABS_X, JOYSTICK_DEFLECTION_CENTER)
                joystick(GamepadKeycodes.ABS_Y, JOYSTICK_DEFLECTION_CENTER)
            }

            GamepadKeycodes.CODE_RS_UP,
            GamepadKeycodes.CODE_RS_DOWN,
            -> {
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_CENTER)
            }

            GamepadKeycodes.CODE_RS_LEFT,
            GamepadKeycodes.CODE_RS_RIGHT,
            -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_CENTER)
            }

            GamepadKeycodes.CODE_RS_UP_LEFT,
            GamepadKeycodes.CODE_RS_UP_RIGHT,
            GamepadKeycodes.CODE_RS_DOWN_LEFT,
            GamepadKeycodes.CODE_RS_DOWN_RIGHT,
            -> {
                joystick(GamepadKeycodes.ABS_Z, JOYSTICK_DEFLECTION_CENTER)
                joystick(GamepadKeycodes.ABS_RZ, JOYSTICK_DEFLECTION_CENTER)
            }

            else -> {
                PrivdGamepadInjector.buttonUp(btnCode)
            }
        }
    }

    /** Sends a D-Pad hat event. axis: 0 = X (−1 left / +1 right), 1 = Y (−1 up / +1 down) */
    fun hat(
        axis: Int,
        value: Int,
    ) {
        if (!PrivdClient.isConnected) return
        PrivdGamepadInjector.hat(axis, value)
    }

    /**
     * Sends an analog joystick axis event.
     * [axisCode]: [GamepadKeycodes.ABS_X]=0, [GamepadKeycodes.ABS_Y]=1,
     *             [GamepadKeycodes.ABS_Z]=2, [GamepadKeycodes.ABS_RZ]=5.
     * [value]: raw int16, range −32768…+32767.
     */
    fun joystick(
        axisCode: Int,
        value: Int,
    ) {
        if (!PrivdClient.isConnected) return
        PrivdGamepadInjector.joystick(axisCode, value)
    }
}
