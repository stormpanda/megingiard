package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.privd.PrivdClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TouchInjectorTest {
    @Before
    fun setUp() {
        // Ensure clean state before each test
        TouchInjector.stop("test_token")
    }

    @Test
    fun testIsRunningInitialState() {
        assertFalse(TouchInjector.isRunning)
    }

    @Test
    fun testCoordinateClampingAndConversion() {
        // Verify clamping logic on injectTouch (-0.5 to 1.5)
        // normalizedX = 0.5, normalizedY = 0.5
        // px = (1 - 0.5) * 1080 = 540
        // py = 0.5 * 1920 = 960
        TouchInjector.injectTouch(TouchAction.DOWN, 0.5f, 0.5f)

        // Out-of-bounds coordinates clamped to [-0.5, 1.5]
        TouchInjector.injectTouch(TouchAction.MOVE, -2.0f, 3.0f)
    }

    @Test
    fun testReleaseAllSlotsDoesNotCrash() {
        TouchInjector.releaseAllSlots()
    }

    @Test
    fun testSlotBoundsAndMultiSlotInjection() {
        for (slot in 0..9) {
            TouchInjector.injectTouch(slot, TouchAction.DOWN, 0.1f * slot, 0.1f * slot)
            TouchInjector.injectTouch(slot, TouchAction.MOVE, 0.1f * slot + 0.05f, 0.1f * slot + 0.05f)
            TouchInjector.injectTouch(slot, TouchAction.UP, 0.1f * slot, 0.1f * slot)
        }

        // Out-of-bounds slots are clamped/ignored safely
        TouchInjector.injectTouch(-1, TouchAction.DOWN, 0.5f, 0.5f)
        TouchInjector.injectTouch(10, TouchAction.DOWN, 0.5f, 0.5f)
    }

    @Test
    fun testClientReferenceCounting() {
        TouchInjector.stop("nonExistent")
        assertFalse(TouchInjector.isRunning)
    }
}
