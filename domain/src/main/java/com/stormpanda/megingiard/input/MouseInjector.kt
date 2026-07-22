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
    private val router = InjectorBackendRouter(TAG)

    fun start(context: Context) {
        if (!router.resolveBackend()) {
            ShellMouseInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (router.isPrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        if (!router.isPrivd) {
            ShellMouseInjector.stop()
        }
    }

    val isRunning: Boolean get() = router.isRunning { ShellMouseInjector.isRunning }

    fun leftDown() {
        if (router.isPrivd) PrivdClient.send("MB L D\n") else ShellMouseInjector.buttonDown('L')
    }

    fun leftUp() {
        if (router.isPrivd) PrivdClient.send("MB L U\n") else ShellMouseInjector.buttonUp('L')
    }

    fun rightDown() {
        if (router.isPrivd) PrivdClient.send("MB R D\n") else ShellMouseInjector.buttonDown('R')
    }

    fun rightUp() {
        if (router.isPrivd) PrivdClient.send("MB R U\n") else ShellMouseInjector.buttonUp('R')
    }

    fun middleDown() {
        if (router.isPrivd) PrivdClient.send("MB M D\n") else ShellMouseInjector.buttonDown('M')
    }

    fun middleUp() {
        if (router.isPrivd) PrivdClient.send("MB M U\n") else ShellMouseInjector.buttonUp('M')
    }

    fun mouse4Down() {
        if (router.isPrivd) PrivdClient.send("MB 4 D\n") else ShellMouseInjector.buttonDown('4')
    }

    fun mouse4Up() {
        if (router.isPrivd) PrivdClient.send("MB 4 U\n") else ShellMouseInjector.buttonUp('4')
    }

    fun mouse5Down() {
        if (router.isPrivd) PrivdClient.send("MB 5 D\n") else ShellMouseInjector.buttonDown('5')
    }

    fun mouse5Up() {
        if (router.isPrivd) PrivdClient.send("MB 5 U\n") else ShellMouseInjector.buttonUp('5')
    }

    fun moveMouse(
        dx: Int,
        dy: Int,
    ) {
        if (dx == 0 && dy == 0) return
        if (router.isPrivd) {
            PrivdClient.send("MM $dx $dy\n")
        } else {
            ShellMouseInjector.moveMouse(dx, dy)
        }
    }

    fun scrollWheel(delta: Int) {
        if (delta == 0) return
        if (router.isPrivd) {
            PrivdClient.send("MW $delta\n")
        } else {
            ShellMouseInjector.scrollWheel(delta)
        }
    }
}
