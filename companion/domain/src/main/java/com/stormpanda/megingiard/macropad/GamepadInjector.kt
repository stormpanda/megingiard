package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "GamepadInjector"

private const val JOYSTICK_RAW_MIN = -32768
private const val JOYSTICK_DEFLECTION_MAX = 32767
private const val JOYSTICK_DEFLECTION_MIN = -32767
private const val JOYSTICK_DEFLECTION_CENTER = 0
private const val HAT_AXIS_X = 0
private const val HAT_AXIS_Y = 1
private const val HAT_DIR_POS = 1
private const val HAT_DIR_NEG = -1
private const val HAT_DIR_CENTER = 0

private val VALID_JOYSTICK_AXES =
    setOf(
        GamepadKeycodes.ABS_X,
        GamepadKeycodes.ABS_Y,
        GamepadKeycodes.ABS_Z,
        GamepadKeycodes.ABS_RZ,
    )

/**
 * Public facade for gamepad button injection via Privileged Mode merge.
 *
 * Direct physical gamepad merge requires [PrivdClient.isConnected]. Standalone virtual
 * uinput fallback is retired to prevent dual-controller conflicts on the AYN Thor.
 */
object GamepadInjector {
    val isRunning: Boolean
        get() = PrivdClient.isConnected

    /**
     * Set to `true` by [PhysicalGamepadRecordingManager] while a physical recording session is
     * active. All inject methods return early when this flag is set, preventing self-echo.
     */
    @Volatile internal var isRecordingActive: Boolean = false

    private fun sendButtonDown(code: Int) {
        if (isRecordingActive) {
            AppLog.d(TAG, "buttonDown btnCode=$code suppressed (recording active)")
            return
        }
        AppLog.d(TAG, "buttonDown btnCode=$code")
        PrivdClient.send("GD $code\n")
    }

    private fun sendButtonUp(code: Int) {
        if (isRecordingActive) {
            AppLog.d(TAG, "buttonUp btnCode=$code suppressed (recording active)")
            return
        }
        AppLog.d(TAG, "buttonUp btnCode=$code")
        PrivdClient.send("GU $code\n")
    }

    fun buttonDown(btnCode: Int) {
        if (!PrivdClient.isConnected) {
            AppLog.w(TAG, "buttonDown($btnCode) dropped: Privd not connected")
            return
        }
        when (btnCode) {
            GamepadKeycodes.BTN_DPAD_UP -> {
                hat(HAT_AXIS_Y, HAT_DIR_NEG)
                sendButtonDown(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_DOWN -> {
                hat(HAT_AXIS_Y, HAT_DIR_POS)
                sendButtonDown(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_LEFT -> {
                hat(HAT_AXIS_X, HAT_DIR_NEG)
                sendButtonDown(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_POS)
                sendButtonDown(btnCode)
            }

            GamepadKeycodes.CODE_DPAD_UP_LEFT -> {
                hat(HAT_AXIS_X, HAT_DIR_NEG)
                hat(HAT_AXIS_Y, HAT_DIR_NEG)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_UP)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_LEFT)
            }

            GamepadKeycodes.CODE_DPAD_UP_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_POS)
                hat(HAT_AXIS_Y, HAT_DIR_NEG)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_UP)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_RIGHT)
            }

            GamepadKeycodes.CODE_DPAD_DOWN_LEFT -> {
                hat(HAT_AXIS_X, HAT_DIR_NEG)
                hat(HAT_AXIS_Y, HAT_DIR_POS)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_DOWN)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_LEFT)
            }

            GamepadKeycodes.CODE_DPAD_DOWN_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_POS)
                hat(HAT_AXIS_Y, HAT_DIR_POS)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_DOWN)
                sendButtonDown(GamepadKeycodes.BTN_DPAD_RIGHT)
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
                sendButtonDown(btnCode)
            }
        }
    }

    fun buttonUp(btnCode: Int) {
        if (!PrivdClient.isConnected) return
        when (btnCode) {
            GamepadKeycodes.BTN_DPAD_UP, GamepadKeycodes.BTN_DPAD_DOWN -> {
                hat(HAT_AXIS_Y, HAT_DIR_CENTER)
                sendButtonUp(btnCode)
            }

            GamepadKeycodes.BTN_DPAD_LEFT, GamepadKeycodes.BTN_DPAD_RIGHT -> {
                hat(HAT_AXIS_X, HAT_DIR_CENTER)
                sendButtonUp(btnCode)
            }

            GamepadKeycodes.CODE_DPAD_UP_LEFT,
            GamepadKeycodes.CODE_DPAD_UP_RIGHT,
            GamepadKeycodes.CODE_DPAD_DOWN_LEFT,
            GamepadKeycodes.CODE_DPAD_DOWN_RIGHT,
            -> {
                hat(HAT_AXIS_X, HAT_DIR_CENTER)
                hat(HAT_AXIS_Y, HAT_DIR_CENTER)
                sendButtonUp(GamepadKeycodes.BTN_DPAD_UP)
                sendButtonUp(GamepadKeycodes.BTN_DPAD_DOWN)
                sendButtonUp(GamepadKeycodes.BTN_DPAD_LEFT)
                sendButtonUp(GamepadKeycodes.BTN_DPAD_RIGHT)
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
                sendButtonUp(btnCode)
            }
        }
    }

    /** Sends a D-Pad hat event. axis: 0 = X (−1 left / +1 right), 1 = Y (−1 up / +1 down) */
    fun hat(
        axis: Int,
        value: Int,
    ) {
        if (!PrivdClient.isConnected || isRecordingActive) return
        require(axis in HAT_AXIS_X..HAT_AXIS_Y) { "axis must be 0 (X) or 1 (Y)" }
        require(value in HAT_DIR_NEG..HAT_DIR_POS) { "value must be -1, 0, or +1" }
        AppLog.d(TAG, "hat axis=$axis value=$value")
        PrivdClient.send("HD $axis $value\n")
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
        if (!PrivdClient.isConnected || isRecordingActive) return
        require(axisCode in VALID_JOYSTICK_AXES) { "axisCode must be one of ABS_X(0), ABS_Y(1), ABS_Z(2), or ABS_RZ(5)" }
        require(value in JOYSTICK_RAW_MIN..JOYSTICK_DEFLECTION_MAX) { "value must be in int16 range -32768..32767" }
        PrivdClient.send("JS $axisCode $value\n")
    }
}
