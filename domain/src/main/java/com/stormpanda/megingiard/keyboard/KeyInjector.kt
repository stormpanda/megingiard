package com.stormpanda.megingiard.keyboard

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.InjectorBackendRouter
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "KeyInjector"

/**
 * Public facade for keyboard event injection — strategy router.
 */
object KeyInjector {
    private val router = InjectorBackendRouter(TAG)

    fun start(context: Context) {
        if (router.resolveBackend()) {
            PrivdClient.send("KB_START\n")
        } else {
            ShellKeyInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (router.isPrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        if (router.isPrivd) {
            PrivdClient.send("KB_STOP\n")
        } else {
            ShellKeyInjector.stop()
        }
    }

    val isRunning: Boolean get() = router.isRunning { ShellKeyInjector.isRunning }

    fun keyDown(linuxKeycode: Int) {
        if (linuxKeycode !in 1..255) {
            AppLog.w(TAG, "Ignoring out-of-range linuxKeycode: $linuxKeycode for keyDown")
            return
        }
        if (router.isPrivd) {
            PrivdClient.send("KD $linuxKeycode\n")
        } else {
            ShellKeyInjector.injectKey(KeyAction.DOWN, linuxKeycode)
        }
    }

    fun keyUp(linuxKeycode: Int) {
        if (linuxKeycode !in 1..255) {
            AppLog.w(TAG, "Ignoring out-of-range linuxKeycode: $linuxKeycode for keyUp")
            return
        }
        if (router.isPrivd) {
            PrivdClient.send("KU $linuxKeycode\n")
        } else {
            ShellKeyInjector.injectKey(KeyAction.UP, linuxKeycode)
        }
    }

    /** Convenience: sends key down immediately followed by key up. */
    fun keyTap(linuxKeycode: Int) {
        keyDown(linuxKeycode)
        keyUp(linuxKeycode)
    }
}
