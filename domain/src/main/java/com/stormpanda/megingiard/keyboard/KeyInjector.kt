package com.stormpanda.megingiard.keyboard

import android.content.Context
import com.stormpanda.megingiard.AppLog

private const val TAG = "KeyInjector"

/**
 * Public facade for keyboard event injection.
 *
 * Delegates to [ShellKeyInjector]. Keycodes are standard Linux keycodes
 * (see [LinuxKeycodes] for named constants).
 */
object KeyInjector {

    fun start(context: Context) {
        AppLog.i(TAG, "start()")
        ShellKeyInjector.start(context)
    }

    fun stop() {
        AppLog.i(TAG, "stop()")
        ShellKeyInjector.stop()
    }

    val isRunning: Boolean get() = ShellKeyInjector.isRunning

    fun keyDown(linuxKeycode: Int) = ShellKeyInjector.injectKey(KeyAction.DOWN, linuxKeycode)
    fun keyUp(linuxKeycode: Int)   = ShellKeyInjector.injectKey(KeyAction.UP, linuxKeycode)

    /** Convenience: sends key down immediately followed by key up. */
    fun keyTap(linuxKeycode: Int) {
        keyDown(linuxKeycode)
        keyUp(linuxKeycode)
    }
}
