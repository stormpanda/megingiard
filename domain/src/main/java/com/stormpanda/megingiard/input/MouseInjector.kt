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

    fun leftDown() {
        router.dispatch({ PrivdClient.send("MB L D\n") }, { ShellMouseInjector.buttonDown('L') })
    }

    fun leftUp() {
        router.dispatch({ PrivdClient.send("MB L U\n") }, { ShellMouseInjector.buttonUp('L') })
    }

    fun rightDown() {
        router.dispatch({ PrivdClient.send("MB R D\n") }, { ShellMouseInjector.buttonDown('R') })
    }

    fun rightUp() {
        router.dispatch({ PrivdClient.send("MB R U\n") }, { ShellMouseInjector.buttonUp('R') })
    }

    fun middleDown() {
        router.dispatch({ PrivdClient.send("MB M D\n") }, { ShellMouseInjector.buttonDown('M') })
    }

    fun middleUp() {
        router.dispatch({ PrivdClient.send("MB M U\n") }, { ShellMouseInjector.buttonUp('M') })
    }

    fun mouse4Down() {
        router.dispatch({ PrivdClient.send("MB 4 D\n") }, { ShellMouseInjector.buttonDown('4') })
    }

    fun mouse4Up() {
        router.dispatch({ PrivdClient.send("MB 4 U\n") }, { ShellMouseInjector.buttonUp('4') })
    }

    fun mouse5Down() {
        router.dispatch({ PrivdClient.send("MB 5 D\n") }, { ShellMouseInjector.buttonDown('5') })
    }

    fun mouse5Up() {
        router.dispatch({ PrivdClient.send("MB 5 U\n") }, { ShellMouseInjector.buttonUp('5') })
    }

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
