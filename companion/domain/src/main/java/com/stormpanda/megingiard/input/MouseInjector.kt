package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "MouseInjector"

/**
 * Public facade for mouse injection (clicks + relative pointer movement).
 *
 * Strategy router:
 * - If PrivdClient.isConnected, routes events to the privileged daemon via [PrivdClient].
 * - Otherwise, falls back to [ShellMouseInjector] (bundled native process uinput helper).
 */
object MouseInjector {
    private val router =
        InjectorBackendRouter(
            tag = TAG,
            onPrivdConnected = {
                if (ShellMouseInjector.isRunning) {
                    ShellMouseInjector.stop()
                }
            },
        )

    fun start(context: Context) {
        if (!router.resolveBackend()) {
            ShellMouseInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (router.isPrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        router.markStopped()
        if (!router.isPrivd) {
            ShellMouseInjector.stop()
        }
    }

    val isRunning: Boolean get() = router.isRunning { ShellMouseInjector.isRunning }

    fun buttonDown(code: Char) {
        router.dispatch({ PrivdClient.send("MB $code D\n") }, { ShellMouseInjector.buttonDown(code) })
    }

    fun buttonUp(code: Char) {
        router.dispatch({ PrivdClient.send("MB $code U\n") }, { ShellMouseInjector.buttonUp(code) })
    }

    fun buttonDown(button: com.stormpanda.megingiard.macropad.MouseButton) = buttonDown(button.code)

    fun buttonUp(button: com.stormpanda.megingiard.macropad.MouseButton) = buttonUp(button.code)

    fun leftDown() = buttonDown('L')

    fun leftUp() = buttonUp('L')

    fun rightDown() = buttonDown('R')

    fun rightUp() = buttonUp('R')

    fun middleDown() = buttonDown('M')

    fun middleUp() = buttonUp('M')

    fun mouse4Down() = buttonDown('4')

    fun mouse4Up() = buttonUp('4')

    fun mouse5Down() = buttonDown('5')

    fun mouse5Up() = buttonUp('5')

    fun moveMouse(
        dx: Int,
        dy: Int,
    ) {
        if (dx == 0 && dy == 0) return
        router.dispatch({ PrivdClient.send("MM $dx $dy\n") }, { ShellMouseInjector.moveMouse(dx, dy) })
    }

    fun scrollWheel(delta: Int) {
        if (delta == 0) return
        router.dispatch({ PrivdClient.send("MW $delta\n") }, { ShellMouseInjector.scrollWheel(delta) })
    }
}
