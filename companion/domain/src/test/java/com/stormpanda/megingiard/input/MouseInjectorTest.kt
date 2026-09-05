package com.stormpanda.megingiard.input

import org.junit.Assert.assertFalse
import org.junit.Test

class MouseInjectorTest {
    @Test
    fun testIsRunningInitialState() {
        assertFalse(MouseInjector.isRunning)
    }

    @Test
    fun testMouseButtonActionsDoNotCrash() {
        MouseInjector.leftDown()
        MouseInjector.leftUp()
        MouseInjector.rightDown()
        MouseInjector.rightUp()
        MouseInjector.middleDown()
        MouseInjector.middleUp()
        MouseInjector.mouse4Down()
        MouseInjector.mouse4Up()
        MouseInjector.mouse5Down()
        MouseInjector.mouse5Up()
    }

    @Test
    fun testMouseMoveZeroDeltaIgnored() {
        MouseInjector.moveMouse(0, 0)
        MouseInjector.moveMouse(10, -5)
    }

    @Test
    fun testScrollWheelZeroDeltaIgnored() {
        MouseInjector.scrollWheel(0)
        MouseInjector.scrollWheel(1)
        MouseInjector.scrollWheel(-1)
    }

    @Test
    fun testStopWhenNotStarted() {
        MouseInjector.stop()
        assertFalse(MouseInjector.isRunning)
    }
}
