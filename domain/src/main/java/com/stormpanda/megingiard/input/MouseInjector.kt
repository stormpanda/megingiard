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
    @Volatile private var usePrivd: Boolean = false

    fun start(context: Context) {
        usePrivd = PrivdClient.isConnected
        AppLog.i(TAG, "start() — backend=${if (usePrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        if (!usePrivd) {
            ShellMouseInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (usePrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        if (!usePrivd) {
            ShellMouseInjector.stop()
        }
    }

    val isRunning: Boolean get() = if (usePrivd) PrivdClient.isConnected else ShellMouseInjector.isRunning

    fun leftDown() {
        if (usePrivd) PrivdClient.send("MB L D\n") else ShellMouseInjector.buttonDown('L')
    }

    fun leftUp() {
        if (usePrivd) PrivdClient.send("MB L U\n") else ShellMouseInjector.buttonUp('L')
    }

    fun rightDown() {
        if (usePrivd) PrivdClient.send("MB R D\n") else ShellMouseInjector.buttonDown('R')
    }

    fun rightUp() {
        if (usePrivd) PrivdClient.send("MB R U\n") else ShellMouseInjector.buttonUp('R')
    }

    fun middleDown() {
        if (usePrivd) PrivdClient.send("MB M D\n") else ShellMouseInjector.buttonDown('M')
    }

    fun middleUp() {
        if (usePrivd) PrivdClient.send("MB M U\n") else ShellMouseInjector.buttonUp('M')
    }

    fun mouse4Down() {
        if (usePrivd) PrivdClient.send("MB 4 D\n") else ShellMouseInjector.buttonDown('4')
    }

    fun mouse4Up() {
        if (usePrivd) PrivdClient.send("MB 4 U\n") else ShellMouseInjector.buttonUp('4')
    }

    fun mouse5Down() {
        if (usePrivd) PrivdClient.send("MB 5 D\n") else ShellMouseInjector.buttonDown('5')
    }

    fun mouse5Up() {
        if (usePrivd) PrivdClient.send("MB 5 U\n") else ShellMouseInjector.buttonUp('5')
    }

    fun moveMouse(
        dx: Int,
        dy: Int,
    ) {
        if (dx == 0 && dy == 0) return
        if (usePrivd) {
            PrivdClient.send("MM $dx $dy\n")
        } else {
            ShellMouseInjector.moveMouse(dx, dy)
        }
    }

    fun scrollWheel(delta: Int) {
        if (delta == 0) return
        if (usePrivd) {
            PrivdClient.send("MW $delta\n")
        } else {
            ShellMouseInjector.scrollWheel(delta)
        }
    }
}
