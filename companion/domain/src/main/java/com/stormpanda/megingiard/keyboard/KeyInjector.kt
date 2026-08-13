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
    @Volatile
    private var appContext: Context? = null

    private val router =
        InjectorBackendRouter(
            tag = TAG,
            onPrivdConnected = {
                AppLog.i(TAG, "Privd reconnected -> re-sending KB_START to daemon")
                PrivdClient.send("KB_START\n")
                if (ShellKeyInjector.isRunning) {
                    ShellKeyInjector.stop()
                }
            },
            onPrivdDisconnected = {
                AppLog.i(TAG, "Privd disconnected while KeyInjector active -> launching fallback ShellKeyInjector")
                appContext?.let { ShellKeyInjector.start(it) }
            },
        )

    fun start(context: Context) {
        appContext = context.applicationContext
        if (router.resolveBackend()) {
            PrivdClient.send("KB_START\n")
        } else {
            ShellKeyInjector.start(context)
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop() — backend=${if (router.isPrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        router.markStopped()
        if (router.isPrivd) {
            PrivdClient.send("KB_STOP\n")
        } else {
            ShellKeyInjector.stop()
        }
        appContext = null
    }

    val isRunning: Boolean get() = router.isRunning { ShellKeyInjector.isRunning }

    fun keyDown(linuxKeycode: Int) {
        if (linuxKeycode !in 1..255) {
            AppLog.w(TAG, "Ignoring out-of-range linuxKeycode: $linuxKeycode for keyDown")
            return
        }
        router.dispatch(
            privdAction = { PrivdClient.send("KD $linuxKeycode\n") },
            shellAction = { ShellKeyInjector.injectKey(KeyAction.DOWN, linuxKeycode) },
        )
    }

    fun keyUp(linuxKeycode: Int) {
        if (linuxKeycode !in 1..255) {
            AppLog.w(TAG, "Ignoring out-of-range linuxKeycode: $linuxKeycode for keyUp")
            return
        }
        router.dispatch(
            privdAction = { PrivdClient.send("KU $linuxKeycode\n") },
            shellAction = { ShellKeyInjector.injectKey(KeyAction.UP, linuxKeycode) },
        )
    }

    /** Convenience: sends key down immediately followed by key up. */
    fun keyTap(linuxKeycode: Int) {
        keyDown(linuxKeycode)
        keyUp(linuxKeycode)
    }
}
