package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MouseButton
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "MouseInjector"

/**
 * Public facade for mouse injection (clicks + relative pointer movement) via Megingiard System Service.
 */
object MouseInjector {
    @Volatile
    private var active: Boolean = false

    fun start(context: Context? = null) {
        active = true
        AppLog.i(TAG, "start()")
    }

    fun stop() {
        AppLog.i(TAG, "stop()")
        active = false
    }

    val isRunning: Boolean get() = active && PrivdClient.isConnected

    fun buttonDown(code: Char) {
        PrivdClient.send("MB $code D\n")
    }

    fun buttonUp(code: Char) {
        PrivdClient.send("MB $code U\n")
    }

    fun buttonDown(button: MouseButton) = buttonDown(button.code)

    fun buttonUp(button: MouseButton) = buttonUp(button.code)

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
        PrivdClient.send("MM $dx $dy\n")
    }

    fun scrollWheel(delta: Int) {
        if (delta == 0) return
        PrivdClient.send("MW $delta\n")
    }
}
