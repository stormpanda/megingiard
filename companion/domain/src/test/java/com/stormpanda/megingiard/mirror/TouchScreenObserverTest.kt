package com.stormpanda.megingiard.mirror

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun clientReferenceCounting() {
        assertFalse(TouchScreenObserver.isRunning)

        TouchScreenObserver.start("clientA")
        TouchScreenObserver.start("clientB")

        TouchScreenObserver.stop("clientA")
        TouchScreenObserver.stop("nonExistent")

        TouchScreenObserver.stop("clientB")
        assertFalse(TouchScreenObserver.isRunning)
    }

    @Test
    fun stopAll_cleansCallbacksAndState() {
        TouchScreenObserver.onTouchNormalized = { _, _ -> }
        TouchScreenObserver.onTouchEvent = { _, _, _, _ -> }

        TouchScreenObserver.start("client1")
        TouchScreenObserver.stopAll()

        assertFalse(TouchScreenObserver.isRunning)
        assertTrue(TouchScreenObserver.onTouchNormalized == null)
        assertTrue(TouchScreenObserver.onTouchEvent == null)
    }
}
