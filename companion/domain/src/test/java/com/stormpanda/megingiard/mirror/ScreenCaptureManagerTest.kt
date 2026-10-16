package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenCaptureManagerTest {
    @Before
    fun setUp() {
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setFrozen(false)
        ScreenCaptureManager.consumeScreenshotRequest()
    }

    @Test
    fun testInitialStateAndSetters() {
        assertFalse(ScreenCaptureManager.isCapturing.value)
        assertFalse(ScreenCaptureManager.isFrozen.value)

        ScreenCaptureManager.setCapturing(true)
        assertTrue(ScreenCaptureManager.isCapturing.value)

        ScreenCaptureManager.setFrozen(true)
        assertTrue(ScreenCaptureManager.isFrozen.value)
    }

    @Test
    fun testSetCapturingFalseClearsFrozenState() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFrozen(true)
        assertTrue(ScreenCaptureManager.isFrozen.value)

        ScreenCaptureManager.setCapturing(false)
        assertFalse(ScreenCaptureManager.isCapturing.value)
        assertFalse(ScreenCaptureManager.isFrozen.value)
    }

    @Test
    fun testScreenshotRequestLifecycle() {
        assertFalse(ScreenCaptureManager.screenshotRequested.value)
        assertNull(ScreenCaptureManager.pendingScreenshotTarget.value)

        val target = ScreenshotTarget.TOP
        ScreenCaptureManager.requestScreenshot(target)
        assertTrue(ScreenCaptureManager.screenshotRequested.value)
        assertEquals(target, ScreenCaptureManager.pendingScreenshotTarget.value)

        ScreenCaptureManager.consumeScreenshotRequest()
        assertFalse(ScreenCaptureManager.screenshotRequested.value)
        assertNull(ScreenCaptureManager.pendingScreenshotTarget.value)
    }

    @Test
    fun testResetMirrorSessionState() {
        ScreenCaptureManager.setFrozen(true)

        ScreenCaptureManager.resetMirrorSessionState()
        assertFalse(ScreenCaptureManager.isFrozen.value)
    }
}
