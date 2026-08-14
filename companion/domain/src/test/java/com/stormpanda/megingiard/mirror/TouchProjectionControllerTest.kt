package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TouchProjectionControllerTest {
    private lateinit var controller: TouchProjectionController

    @Before
    fun setUp() {
        controller =
            TouchProjectionController(
                edgeZonePx = 48f,
                overlayAtBottom = true,
            )
    }

    @Test
    fun testInitialIndicatorState() {
        assertNull(controller.indicatorPos.value)
    }

    @Test
    fun testNearEdgePressReturnsFalse() {
        // Press near bottom edge (y = 980 in a 1000px height box with 48px edge zone)
        val handled =
            controller.onPress(
                pointerId = 1L,
                x = 500f,
                y = 980f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = false,
                pointerCount = 1,
            )
        assertFalse(handled)
    }

    @Test
    fun testConsumedPressReturnsFalse() {
        val handled =
            controller.onPress(
                pointerId = 1L,
                x = 500f,
                y = 500f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = true,
                pointerCount = 1,
            )
        assertFalse(handled)
    }

    @Test
    fun testResetClearsIndicator() {
        controller.reset()
        assertNull(controller.indicatorPos.value)
    }
}
