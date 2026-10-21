package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MouseButton
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "MouseInjector"

/**
 * Public facade for mouse injection (clicks + relative pointer movement) via Megingiard System Service.
 */
object MouseInjector {
    private val heldButtons = mutableSetOf<Char>()

    fun stop() {
        AppLog.i(TAG, "stop()")
        val toRelease =
            synchronized(heldButtons) {
                val copy = heldButtons.toList()
                heldButtons.clear()
                copy
            }
        toRelease.forEach { code -> PrivdClient.send("MB $code U\n") }
    }

    fun buttonDown(code: Char) {
        synchronized(heldButtons) { heldButtons.add(code) }
        PrivdClient.send("MB $code D\n")
    }

    fun buttonUp(code: Char) {
        synchronized(heldButtons) { heldButtons.remove(code) }
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

    fun scrollHorizontalWheel(delta: Int) {
        if (delta == 0) return
        PrivdClient.send("MH $delta\n")
    }
}
