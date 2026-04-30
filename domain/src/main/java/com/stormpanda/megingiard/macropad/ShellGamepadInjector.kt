package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.NativeBinaryInjector

/**
 * Injects gamepad button events by piping commands to the native
 * `gamepadinjector_arm64` binary, which creates a virtual gamepad via
 * `/dev/uinput` and writes Linux `EV_KEY` / `EV_ABS` events.
 *
 * ### Protocol
 * - `GD <code>\n` — button DOWN (Linux BTN_* value), `GU <code>\n` — button UP
 * - `HD <axis> <value>\n` — D-Pad hat: axis 0 = X (−1/0/+1), 1 = Y
 * - `JS <axisCode> <value>\n` — analog joystick (int16 range)
 *
 * Every event is delivered in order — no coalescing applied.
 */
internal sealed class GamepadCommand {
    data class Button(val down: Boolean, val btnCode: Int) : GamepadCommand()
    data class Hat(val axis: Int, val value: Int) : GamepadCommand()
    data class Joystick(val axisCode: Int, val value: Int) : GamepadCommand()
}

internal object ShellGamepadInjector : NativeBinaryInjector<GamepadCommand>(
    workerThreadName = "GamepadInjectorWriter",
) {
    override val tag = "ShellGamepadInjector"
    override val assetName = "gamepadinjector_arm64"

    override fun formatCommand(cmd: GamepadCommand): String = when (cmd) {
        is GamepadCommand.Button   -> "${if (cmd.down) "GD" else "GU"} ${cmd.btnCode}\n"
        is GamepadCommand.Hat      -> "HD ${cmd.axis} ${cmd.value}\n"
        is GamepadCommand.Joystick -> "JS ${cmd.axisCode} ${cmd.value}\n"
    }

    fun buttonDown(btnCode: Int) = enqueue(GamepadCommand.Button(down = true, btnCode = btnCode))
    fun buttonUp(btnCode: Int)   = enqueue(GamepadCommand.Button(down = false, btnCode = btnCode))

    /** Sends a D-Pad hat event. axis: 0 = X, 1 = Y; value: −1 / 0 / +1 */
    fun hat(axis: Int, value: Int) {
        require(axis in 0..1) { "axis must be 0 (X) or 1 (Y)" }
        require(value in -1..1) { "value must be -1, 0, or +1" }
        enqueue(GamepadCommand.Hat(axis = axis, value = value))
    }

    /**
     * Sends an analog joystick axis event.
     * [axisCode]: [GamepadKeycodes.ABS_X]=0, [GamepadKeycodes.ABS_Y]=1,
     *             [GamepadKeycodes.ABS_Z]=2, [GamepadKeycodes.ABS_RZ]=5.
     * [value]: raw int16 range −32768…+32767.
     */
    fun joystick(axisCode: Int, value: Int) {
        require(axisCode in setOf(GamepadKeycodes.ABS_X, GamepadKeycodes.ABS_Y, GamepadKeycodes.ABS_Z, GamepadKeycodes.ABS_RZ)) {
            "axisCode must be one of ABS_X(0), ABS_Y(1), ABS_Z(2), or ABS_RZ(5)"
        }
        require(value in -32768..32767) { "value must be in int16 range -32768..32767" }
        enqueue(GamepadCommand.Joystick(axisCode = axisCode, value = value))
    }
}
