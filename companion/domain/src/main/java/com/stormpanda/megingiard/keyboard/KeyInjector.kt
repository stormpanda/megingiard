package com.stormpanda.megingiard.keyboard

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "KeyInjector"

/**
 * Public facade for keyboard event injection via Megingiard System Service (Privileged Mode).
 */
object KeyInjector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var active: Boolean = false

    init {
        scope.launch {
            PrivdClient.state.collect { state ->
                if (active && state == PrivdConnectionState.CONNECTED) {
                    AppLog.i(TAG, "Privd reconnected while KeyInjector active -> re-sending KB_START to daemon")
                    PrivdClient.send("KB_START\n")
                }
            }
        }
    }

    fun start(context: Context? = null) {
        active = true
        AppLog.i(TAG, "start()")
        if (PrivdClient.isConnected) {
            PrivdClient.send("KB_START\n")
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop()")
        active = false
        if (PrivdClient.isConnected) {
            PrivdClient.send("KB_STOP\n")
        }
    }

    val isRunning: Boolean get() = active && PrivdClient.isConnected

    fun isValidKeycode(linuxKeycode: Int): Boolean = linuxKeycode in 1..LinuxKeycodes.KEY_MAX

    fun keyDown(linuxKeycode: Int) {
        if (!isValidKeycode(linuxKeycode)) {
            AppLog.w(TAG, "Ignoring out-of-range linuxKeycode: $linuxKeycode for keyDown")
            return
        }
        PrivdClient.send("KD $linuxKeycode\n")
    }

    fun keyUp(linuxKeycode: Int) {
        if (!isValidKeycode(linuxKeycode)) {
            AppLog.w(TAG, "Ignoring out-of-range linuxKeycode: $linuxKeycode for keyUp")
            return
        }
        PrivdClient.send("KU $linuxKeycode\n")
    }

    /** Convenience: sends key down immediately followed by key up. */
    fun keyTap(linuxKeycode: Int) {
        keyDown(linuxKeycode)
        keyUp(linuxKeycode)
    }
}
