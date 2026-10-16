package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.input.TouchAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class TouchScreenObserverTest {
    @Before
    fun setUp() {
        TouchScreenObserver.stopAll()
    }

    @After
    fun tearDown() {
        TouchScreenObserver.stopAll()
    }

    @Test
    fun clientReferenceCounting_andPerClientCallbacks() {
        assertFalse(TouchScreenObserver.isRunning)

        var normalizedEventsA = 0
        var touchEventsB = 0

        TouchScreenObserver.start(
            clientToken = "clientA",
            onTouchNormalized = { _, _ -> normalizedEventsA++ },
        )
        TouchScreenObserver.start(
            clientToken = "clientB",
            onTouchEvent = { _, _, _, _ -> touchEventsB++ },
        )

        TouchScreenObserver.dispatchTouchEvent(0, TouchAction.DOWN, 0.5f, 0.5f)
        assertEquals(1, normalizedEventsA)
        assertEquals(1, touchEventsB)

        TouchScreenObserver.stop("clientA")
        TouchScreenObserver.stop("nonExistent")

        TouchScreenObserver.dispatchTouchEvent(0, TouchAction.MOVE, 0.6f, 0.6f)
        assertEquals(1, normalizedEventsA)
        assertEquals(2, touchEventsB)

        TouchScreenObserver.stop("clientB")
        assertFalse(TouchScreenObserver.isRunning)
    }

    @Test
    fun stopAll_cleansCallbacksAndState() {
        var events = 0
        TouchScreenObserver.start(
            clientToken = "client1",
            onTouchNormalized = { _, _ -> events++ },
            onTouchEvent = { _, _, _, _ -> events++ },
        )
        TouchScreenObserver.stopAll()

        TouchScreenObserver.dispatchTouchEvent(0, TouchAction.DOWN, 0.5f, 0.5f)
        assertFalse(TouchScreenObserver.isRunning)
        assertEquals(0, events)
    }
}
