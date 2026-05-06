package com.stormpanda.megingiard.macropad

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdGamepadInjector
import com.stormpanda.megingiard.settings.MacroPadSettings

private const val TAG = "GamepadInjector"

/**
 * Public facade for gamepad button injection — strategy router.
 *
 * Two backends:
 *  - **Virtual** ([ShellGamepadInjector]): spawns the bundled
 *    `gamepadinjector_arm64` binary, which creates a fresh virtual uinput
 *    gamepad. Default path. Available on every device.
 *  - **Privileged merge** ([PrivdGamepadInjector] via [PrivdClient]):
 *    forwards events to the privileged daemon, which writes them directly
 *    into the **physical** gamepad's evdev node. Requires Privileged Mode
 *    to be RUNNING and the per-feature flag
 *    [MacroPadSettings.privdGamepadMergeEnabled] to be true.
 *
 * The active backend is selected at [start] time based on settings + Privd
 * connection state. Once chosen, all dispatch calls go to that backend for
 * the lifetime of the session. Toggling the setting later requires a
 * [stop] + [start] cycle.
 */
object GamepadInjector {

    /** Currently active backend. Locked in at [start] time. */
    @Volatile private var useMerge: Boolean = false

    fun start(context: Context) {
        useMerge = shouldUseMerge()
        AppLog.i(TAG, "start() — backend=${if (useMerge) "PRIVD_MERGE" else "VIRTUAL_UINPUT"}")
        if (useMerge) {
            // Privd connection is already managed by PrivdManager; nothing to start here.
            // If the connection drops mid-session, dispatch becomes a silent no-op
            // (safer than auto-falling-back to a second virtual gamepad).
            if (!PrivdClient.isConnected) {
                AppLog.w(TAG, "Merge enabled but PrivdClient is not connected — dispatch will no-op")
            }
        } else {
            ShellGamepadInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (useMerge) "PRIVD_MERGE" else "VIRTUAL_UINPUT"}")
        if (!useMerge) {
            ShellGamepadInjector.stop()
        }
    }

    val isRunning: Boolean
        get() = if (useMerge) PrivdClient.isConnected else ShellGamepadInjector.isRunning

    fun buttonDown(btnCode: Int) {
        if (useMerge) PrivdGamepadInjector.buttonDown(btnCode)
        else ShellGamepadInjector.buttonDown(btnCode)
    }

    fun buttonUp(btnCode: Int) {
        if (useMerge) PrivdGamepadInjector.buttonUp(btnCode)
        else ShellGamepadInjector.buttonUp(btnCode)
    }

    /** Sends a D-Pad hat event. axis: 0 = X (−1 left / +1 right), 1 = Y (−1 up / +1 down) */
    fun hat(axis: Int, value: Int) {
        if (useMerge) PrivdGamepadInjector.hat(axis, value)
        else ShellGamepadInjector.hat(axis, value)
    }

    /**
     * Sends an analog joystick axis event.
     * [axisCode]: [GamepadKeycodes.ABS_X]=0, [GamepadKeycodes.ABS_Y]=1,
     *             [GamepadKeycodes.ABS_Z]=2, [GamepadKeycodes.ABS_RZ]=5.
     * [value]: raw int16, range −32768…+32767.
     */
    fun joystick(axisCode: Int, value: Int) {
        if (useMerge) PrivdGamepadInjector.joystick(axisCode, value)
        else ShellGamepadInjector.joystick(axisCode, value)
    }

    private fun shouldUseMerge(): Boolean =
        MacroPadSettings.privdGamepadMergeEnabled.value && PrivdClient.isConnected
}
