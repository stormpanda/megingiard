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
        ScreenCaptureManager.setLocked(false)
        ScreenCaptureManager.consumeScreenshotRequest()
    }

    @Test
    fun testInitialStateAndSetters() {
        assertFalse(ScreenCaptureManager.isCapturing.value)
        assertFalse(ScreenCaptureManager.isFrozen.value)
        assertFalse(ScreenCaptureManager.isLocked.value)

        ScreenCaptureManager.setCapturing(true)
        assertTrue(ScreenCaptureManager.isCapturing.value)

        ScreenCaptureManager.setFrozen(true)
        assertTrue(ScreenCaptureManager.isFrozen.value)

        ScreenCaptureManager.setLocked(true)
        assertTrue(ScreenCaptureManager.isLocked.value)
    }

    @Test
    fun testScaleAndOffsetSetters() {
        ScreenCaptureManager.setScale(1.5f)
        assertEquals(1.5f, ScreenCaptureManager.scale.value, 0.001f)

        ScreenCaptureManager.setOffsetX(10f)
        ScreenCaptureManager.setOffsetY(20f)
        assertEquals(10f, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(20f, ScreenCaptureManager.offsetY.value, 0.001f)
    }

    @Test
    fun testSurfaceSizeSetter() {
        ScreenCaptureManager.setSurfaceSize(1080f, 1920f)
        assertEquals(1080f, ScreenCaptureManager.surfaceWidth.value, 0.001f)
        assertEquals(1920f, ScreenCaptureManager.surfaceHeight.value, 0.001f)
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
    fun testToggleLockedAndProjection() {
        ScreenCaptureManager.setLocked(false)
        assertFalse(ScreenCaptureManager.isLocked.value)

        ScreenCaptureManager.toggleLocked()
        assertTrue(ScreenCaptureManager.isLocked.value)

        ScreenCaptureManager.toggleLocked()
        assertFalse(ScreenCaptureManager.isLocked.value)
    }

    @Test
    fun testResetMirrorSessionState() {
        ScreenCaptureManager.setLocked(true)
        ScreenCaptureManager.setFrozen(true)

        ScreenCaptureManager.resetMirrorSessionState()
        assertFalse(ScreenCaptureManager.isLocked.value)
        assertFalse(ScreenCaptureManager.isFrozen.value)
    }
}
