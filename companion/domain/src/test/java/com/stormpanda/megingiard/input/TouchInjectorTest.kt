package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TouchInjectorTest {
    @Before
    fun setUp() {
        PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)
        PrivdClient.clearQueueForTest()
    }

    @After
    fun tearDown() {
        PrivdClient.clearQueueForTest()
        PrivdClient.setStateForTesting(null)
    }

    @Test
    fun testCoordinateClampingAndConversion() {
        // normalizedX = 0.5, normalizedY = 0.5 -> px = 540, py = 960
        TouchInjector.injectTouch(TouchAction.DOWN, 0.5f, 0.5f)
        assertEquals("D 0 540 960\n", PrivdClient.pollCommandForTest())

        // Out-of-bounds coordinates clamped to [-0.5, 1.5]
        // normalizedX = -2.0 -> cx = -0.5 -> py = -960
        // normalizedY = 3.0 -> cy = 1.5 -> px = -540
        TouchInjector.injectTouch(TouchAction.MOVE, -2.0f, 3.0f)
        assertEquals("M 0 -540 -960\n", PrivdClient.pollCommandForTest())
    }

    @Test
    fun testReleaseAllSlotsEnqueuesUpForAllTenSlots() {
        TouchInjector.releaseAllSlots()
        assertEquals(10, PrivdClient.queueSizeForTest)
        for (slot in 0..9) {
            assertEquals("U $slot\n", PrivdClient.pollCommandForTest())
        }
    }

    @Test
    fun testSlotBoundsAndMultiSlotInjection() {
        for (slot in 0..9) {
            TouchInjector.injectTouch(slot, TouchAction.DOWN, 0.1f * slot, 0.1f * slot)
            TouchInjector.injectTouch(slot, TouchAction.MOVE, 0.1f * slot + 0.05f, 0.1f * slot + 0.05f)
            TouchInjector.injectTouch(slot, TouchAction.UP, 0.1f * slot, 0.1f * slot)
        }
        assertEquals(30, PrivdClient.queueSizeForTest)
    }
}
