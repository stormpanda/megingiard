package com.stormpanda.megingiard.keyboard

import org.junit.Assert.assertFalse
import org.junit.Test

class KeyInjectorTest {
    @Test
    fun testIsRunningInitialState() {
        assertFalse(KeyInjector.isRunning)
    }

    @Test
    fun testKeyBoundsValidation() {
        // Out-of-range keycodes (< 1 or > 255) should be ignored without error
        KeyInjector.keyDown(0)
        KeyInjector.keyDown(300)
        KeyInjector.keyUp(-5)
        KeyInjector.keyUp(1000)
    }

    @Test
    fun testKeyTapHelper() {
        // Valid keycode 30 (KEY_A)
        KeyInjector.keyTap(30)
    }

    @Test
    fun testStopWhenNotStarted() {
        KeyInjector.stop()
        assertFalse(KeyInjector.isRunning)
    }
}
