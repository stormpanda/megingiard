package com.stormpanda.megingiard.macropad

import android.content.Context

/**
 * Public facade for mouse injection (clicks + relative pointer movement).
 *
 * Delegates to [ShellMouseInjector].
 *
 * Button sides: `'L'` = left, `'R'` = right, `'M'` = middle.
 */
object MouseInjector {

    fun start(context: Context) = ShellMouseInjector.start(context)
    fun stop()                  = ShellMouseInjector.stop()

    val isRunning: Boolean get() = ShellMouseInjector.isRunning

    fun leftDown()  = ShellMouseInjector.buttonDown('L')
    fun leftUp()    = ShellMouseInjector.buttonUp('L')
    fun rightDown() = ShellMouseInjector.buttonDown('R')
    fun rightUp()   = ShellMouseInjector.buttonUp('R')

    /**
     * Moves the mouse cursor by [dx] / [dy] pixels (relative, for trackpoint use).
     * Consecutive calls are coalesced in the writer thread — only the latest
     * delta in a burst is sent to reduce latency backlog.
     */
    fun moveMouse(dx: Int, dy: Int) = ShellMouseInjector.moveMouse(dx, dy)

    fun scrollWheel(delta: Int) = ShellMouseInjector.scrollWheel(delta)
}
