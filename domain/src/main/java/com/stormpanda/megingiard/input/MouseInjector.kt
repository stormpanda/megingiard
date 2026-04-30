package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog

@Suppressprivate const val TAG = "MouseInjector"

/**
 * Public facade for mouse injection (clicks + relative pointer movement).
 *
 * Delegates to [ShellMouseInjector].
 *
 * Button sides: `'L'` = left, `'R'` = right, `'M'` = middle, `'4'` = button 4, `'5'` = button 5.
 */
object MouseInjector {

    fun start(context: Context) {
        AppLog.i(TAG, "start()")
        ShellMouseInjector.start(context)
    }

    fun stop() {
        AppLog.i(TAG, "stop()")
        ShellMouseInjector.stop()
    }

    val isRunning: Boolean get() = ShellMouseInjector.isRunning

    fun leftDown()    = ShellMouseInjector.buttonDown('L')
    fun leftUp()      = ShellMouseInjector.buttonUp('L')
    fun rightDown()   = ShellMouseInjector.buttonDown('R')
    fun rightUp()     = ShellMouseInjector.buttonUp('R')
    fun middleDown()  = ShellMouseInjector.buttonDown('M')
    fun middleUp()    = ShellMouseInjector.buttonUp('M')
    fun mouse4Down()  = ShellMouseInjector.buttonDown('4')
    fun mouse4Up()    = ShellMouseInjector.buttonUp('4')
    fun mouse5Down()  = ShellMouseInjector.buttonDown('5')
    fun mouse5Up()    = ShellMouseInjector.buttonUp('5')

    /**
     * Moves the mouse cursor by [dx] / [dy] pixels (relative, for trackpoint use).
     * Consecutive calls are coalesced in the writer thread — only the latest
     * delta in a burst is sent to reduce latency backlog.
     */
    fun moveMouse(dx: Int, dy: Int) = ShellMouseInjector.moveMouse(dx, dy)

    fun scrollWheel(delta: Int) = ShellMouseInjector.scrollWheel(delta)
}
