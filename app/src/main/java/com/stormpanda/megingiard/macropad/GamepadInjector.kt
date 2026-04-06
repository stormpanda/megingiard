package com.stormpanda.megingiard.macropad

import android.content.Context

/**
 * Public facade for gamepad button injection.
 *
 * Delegates to [ShellGamepadInjector]. Button codes are Linux BTN_* values —
 * use [GamepadKeycodes] for named constants.
 */
object GamepadInjector {

    fun start(context: Context) = ShellGamepadInjector.start(context)
    fun stop()                  = ShellGamepadInjector.stop()

    val isRunning: Boolean get() = ShellGamepadInjector.isRunning

    fun buttonDown(btnCode: Int) = ShellGamepadInjector.buttonDown(btnCode)
    fun buttonUp(btnCode: Int)   = ShellGamepadInjector.buttonUp(btnCode)

    /** Sends a D-Pad hat event. axis: 0 = X (−1 left / +1 right), 1 = Y (−1 up / +1 down) */
    fun hat(axis: Int, value: Int) = ShellGamepadInjector.hat(axis, value)

    /**
     * Sends an analog joystick axis event.
     * [axisCode]: [GamepadKeycodes.ABS_X]=0, [GamepadKeycodes.ABS_Y]=1,
     *             [GamepadKeycodes.ABS_RX]=3, [GamepadKeycodes.ABS_RY]=4.
     * [value]: raw int16, range −32768…+32767.
     */
    fun joystick(axisCode: Int, value: Int) = ShellGamepadInjector.joystick(axisCode, value)
}
