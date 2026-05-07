package com.stormpanda.megingiard.privd

import com.stormpanda.megingiard.macropad.GamepadKeycodes

private const val TAG = "PrivdGamepadInjector"
private val VALID_JOYSTICK_AXES = setOf(
    GamepadKeycodes.ABS_X,
    GamepadKeycodes.ABS_Y,
    GamepadKeycodes.ABS_Z,
    GamepadKeycodes.ABS_RZ,
)

/**
 * Routes gamepad events to the privileged daemon's physical-evdev path.
 *
 * Mirrors the public surface of `ShellGamepadInjector`, but instead of
 * spawning a process and creating a virtual uinput device, every command is
 * forwarded over [PrivdClient] to the running `megingiard_privd` daemon
 * (UID 2000, group `input`). The daemon writes the events directly into the
 * already-open `/dev/input/event*` node of the physical gamepad — so games
 * see only one controller, with the MacroPad inputs and the physical inputs
 * merged at the kernel evdev layer.
 *
 * No process lifecycle is owned here — connection management lives in
 * [PrivdClient] / `PrivdManager`. Callers should consult
 * [PrivdClient.isConnected] before relying on event delivery.
 *
 * Wire protocol (identical to the on-device `gamepadinjector` binary):
 *   - `GD <code>\n` button DOWN, `GU <code>\n` button UP
 *   - `HD <axis> <value>\n` D-Pad hat
 *   - `JS <axisCode> <value>\n` analog joystick
 */
internal object PrivdGamepadInjector {

    val isConnected: Boolean get() = PrivdClient.isConnected

    fun buttonDown(btnCode: Int) {
        PrivdClient.send("GD $btnCode\n")
    }

    fun buttonUp(btnCode: Int) {
        PrivdClient.send("GU $btnCode\n")
    }

    /** Sends a D-Pad hat event. axis: 0 = X, 1 = Y; value: −1 / 0 / +1 */
    fun hat(axis: Int, value: Int) {
        require(axis in 0..1) { "axis must be 0 (X) or 1 (Y)" }
        require(value in -1..1) { "value must be -1, 0, or +1" }
        PrivdClient.send("HD $axis $value\n")
    }

    /**
     * Sends an analog joystick axis event.
     * [axisCode] must be one of [GamepadKeycodes.ABS_X], [GamepadKeycodes.ABS_Y],
     * [GamepadKeycodes.ABS_Z], or [GamepadKeycodes.ABS_RZ].
     * [value]: raw int16, range −32768…+32767.
     */
    fun joystick(axisCode: Int, value: Int) {
        require(axisCode in VALID_JOYSTICK_AXES) { "axisCode must be one of ABS_X(0), ABS_Y(1), ABS_Z(2), or ABS_RZ(5)" }
        require(value in -32768..32767) { "value must be in int16 range -32768..32767" }
        PrivdClient.send("JS $axisCode $value\n")
    }
}
