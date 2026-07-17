package com.stormpanda.megingiard.keyboard

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "KeyInjector"

/**
 * Public facade for keyboard event injection — strategy router.
 *
 * Strategy routing:
 * - If [PrivdClient.isConnected], routes key events directly via [PrivdClient]
 *   to the running privileged daemon.
 * - Otherwise, falls back to spawning [ShellKeyInjector] (bundled `keyinjector_arm64` binary).
 */
object KeyInjector {
    @Volatile private var usePrivd: Boolean = false

    fun start(context: Context) {
        usePrivd = PrivdClient.isConnected
        AppLog.i(TAG, "start() — backend=${if (usePrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        if (usePrivd) {
            PrivdClient.send("KB_START\n")
        } else {
            ShellKeyInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (usePrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        if (usePrivd) {
            PrivdClient.send("KB_STOP\n")
        } else {
            ShellKeyInjector.stop()
        }
    }

    val isRunning: Boolean get() = if (usePrivd) PrivdClient.isConnected else ShellKeyInjector.isRunning

    fun keyDown(linuxKeycode: Int) {
        if (linuxKeycode !in 1..255) {
            AppLog.w(TAG, "Ignoring out-of-range linuxKeycode: $linuxKeycode for keyDown")
            return
        }
        if (usePrivd) {
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
        if (usePrivd) {
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
