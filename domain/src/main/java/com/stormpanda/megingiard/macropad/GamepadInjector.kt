package com.stormpanda.megingiard.macropad

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.InjectorBackendRouter
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdGamepadInjector

private const val TAG = "GamepadInjector"

/**
 * Public facade for gamepad button injection — strategy router.
 */
object GamepadInjector {
    private val router =
        InjectorBackendRouter(
            tag = TAG,
            onPrivdConnected = {
                if (ShellGamepadInjector.isRunning) {
                    ShellGamepadInjector.stop()
                }
            },
        )

    fun start(context: Context) {
        val useMerge = router.resolveBackend()
        if (useMerge) {
            if (!PrivdClient.isConnected) {
                AppLog.w(TAG, "Merge enabled but PrivdClient is not connected — dispatch will no-op")
            }
        } else {
            ShellGamepadInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (router.isPrivd) "PRIVD_MERGE" else "VIRTUAL_UINPUT"}")
        router.markStopped()
        if (!router.isPrivd) {
            ShellGamepadInjector.stop()
        }
    }

    val isRunning: Boolean
        get() = router.isRunning { ShellGamepadInjector.isRunning }

    fun buttonDown(btnCode: Int) {
        router.dispatch({ PrivdGamepadInjector.buttonDown(btnCode) }, { ShellGamepadInjector.buttonDown(btnCode) })
    }

    fun buttonUp(btnCode: Int) {
        router.dispatch({ PrivdGamepadInjector.buttonUp(btnCode) }, { ShellGamepadInjector.buttonUp(btnCode) })
    }

    /** Sends a D-Pad hat event. axis: 0 = X (−1 left / +1 right), 1 = Y (−1 up / +1 down) */
    fun hat(
        axis: Int,
        value: Int,
    ) {
        router.dispatch({ PrivdGamepadInjector.hat(axis, value) }, { ShellGamepadInjector.hat(axis, value) })
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
        router.dispatch({ PrivdGamepadInjector.joystick(axisCode, value) }, { ShellGamepadInjector.joystick(axisCode, value) })
    }
}
