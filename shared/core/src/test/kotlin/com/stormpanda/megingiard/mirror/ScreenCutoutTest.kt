package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenCutoutTest {
    @Test
    fun `verify ScreenCutout FULLSCREEN constant properties`() {
        val fullscreen = ScreenCutout.FULLSCREEN
        assertEquals("fullscreen_master", fullscreen.id)
        assertEquals(0f, fullscreen.srcX, 0.0001f)
        assertEquals(0f, fullscreen.srcY, 0.0001f)
        assertEquals(1f, fullscreen.srcWidth, 0.0001f)
        assertEquals(1f, fullscreen.srcHeight, 0.0001f)
        assertEquals(0f, fullscreen.destX, 0.0001f)
        assertEquals(0f, fullscreen.destY, 0.0001f)
        assertEquals(1f, fullscreen.destWidth, 0.0001f)
        assertEquals(1f, fullscreen.destHeight, 0.0001f)
        assertEquals(1f, fullscreen.opacity, 0.0001f)
        assertEquals(CutoutShape.RECTANGLE, fullscreen.shape)
        assertEquals(AspectRatioMode.TOP, fullscreen.aspectRatioMode)
        assertFalse(fullscreen.motionSmoothing)
        assertFalse(fullscreen.followTouch)
        assertFalse(fullscreen.touchProjectionEnabled)
    }

    @Test
    fun `verify createDefault fits aspect ratio correctly`() {
        val cutout =
            ScreenCutout.createDefault(
                srcPixelWidth = 1920f,
                srcPixelHeight = 1080f,
                bottomPixelWidth = 1000f,
                bottomPixelHeight = 1000f,
            )
        assertEquals(0f, cutout.srcX, 0.0001f)
        assertEquals(0f, cutout.srcY, 0.0001f)
        assertEquals(1f, cutout.srcWidth, 0.0001f)
        assertEquals(1f, cutout.srcHeight, 0.0001f)
        assertEquals(0f, cutout.destX, 0.0001f)
        assertEquals(1f, cutout.destWidth, 0.0001f)
        // 1080 / 1920 = 0.5625
        assertEquals(0.5625f, cutout.destHeight, 0.0001f)
        assertEquals((1f - 0.5625f) / 2f, cutout.destY, 0.0001f)
    }
}
