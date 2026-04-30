package com.stormpanda.megingiard.macropad

import android.content.Context
import com.stormpanda.megingiard.AppLog

@Suppress("unused")
private const val TAG = "GamepadInjector"

/**
 * Public facade for gamepad button injection.
 *
 * Delegates to [ShellGamepadInjector]. Button codes are Linux BTN_* values —
 * use [GamepadKeycodes] for named constants.
 */
object GamepadInjector {

    fun start(context: Context) {
        AppLog.i(TAG, "start()")
        ShellGamepadInjector.start(context)
    }

    fun stop() {
        AppLog.i(TAG, "stop()")
        ShellGamepadInjector.stop()
    }

    val isRunning: Boolean get() = ShellGamepadInjector.isRunning

    fun buttonDown(btnCode: Int) = ShellGamepadInjector.buttonDown(btnCode)
    fun buttonUp(btnCode: Int)   = ShellGamepadInjector.buttonUp(btnCode)

    /** Sends a D-Pad hat event. axis: 0 = X (−1 left / +1 right), 1 = Y (−1 up / +1 down) */
    fun hat(axis: Int, value: Int) = ShellGamepadInjector.hat(axis, value)

    /**
     * Sends an analog joystick axis event.
     * [axisCode]: [GamepadKeycodes.ABS_X]=0, [GamepadKeycodes.ABS_Y]=1,
     *             [GamepadKeycodes.ABS_Z]=2, [GamepadKeycodes.ABS_RZ]=5.
     * [value]: raw int16, range −32768…+32767.
     */
    fun joystick(axisCode: Int, value: Int) = ShellGamepadInjector.joystick(axisCode, value)
}
