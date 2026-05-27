package com.stormpanda.megingiard.mirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenCaptureFollowTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ScreenCaptureManager.resetMirrorSessionState()
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setSurfaceSize(1920f, 1080f)
        ScreenCaptureManager.setCaptureSourceSize(1920, 1080)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleFollow updates isFollowActive`() {
        assertFalse(ScreenCaptureManager.isFollowActive.value)
        ScreenCaptureManager.toggleFollow()
        assertTrue(ScreenCaptureManager.isFollowActive.value)
        assertEquals(5f, ScreenCaptureManager.scale.value, 0.001f)

        ScreenCaptureManager.toggleFollow()
        assertFalse(ScreenCaptureManager.isFollowActive.value)
        assertEquals(1f, ScreenCaptureManager.scale.value, 0.001f)
    }

    @Test
    fun `onTouchReceived centers correctly within bounds`() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)

        // Center touch
        ScreenCaptureManager.onTouchReceived(0.5f, 0.5f)
        assertEquals(0f, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(0f, ScreenCaptureManager.offsetY.value, 0.001f)

        // Top-left touch -> should slide viewport to bottom-right to keep content in view
        // nx = 0.2f, ny = 0.2f
        // maxX = (1920 * (5 - 1)) / 2 = 3840
        // targetOffsetX = -(0.2 - 0.5) * 1920 * 5 = 0.3 * 9600 = 2880f
        ScreenCaptureManager.onTouchReceived(0.2f, 0.2f)
        assertEquals(2880f, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(1620f, ScreenCaptureManager.offsetY.value, 0.001f)

        // Extrema touch -> should be clamped to maxX/maxY
        // nx = 0.0f
        // maxX = 3840f
        // targetOffsetX = -(0.0 - 0.5) * 1920 * 5 = 4800f -> clamped to 3840f
        ScreenCaptureManager.onTouchReceived(0f, 0f)
        println("OFF_X: ${ScreenCaptureManager.offsetX.value}, OFF_Y: ${ScreenCaptureManager.offsetY.value}")
        assertEquals("offsetX is incorrect", 3840f, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals("offsetY is incorrect", 2160f, ScreenCaptureManager.offsetY.value, 0.001f)
    }

    @Test
    fun `onMouseMoved updates virtual cursor and centers correctly`() {
        ScreenCaptureManager.setCapturing(true)
        ScreenCaptureManager.setFollowActive(true)

        // Initial cursor starts at center (960, 540)
        // Move mouse by (100, -50) -> cursor at (1060, 490)
        // dx = 100, dy = -50
        ScreenCaptureManager.onMouseMoved(100, -50)
        
        val expectedNx = 1060f / 1920f
        val expectedNy = 490f / 1080f
        
        val sw = 1920f
        val sh = 1080f
        val scale = 5f
        val maxX = (sw * (scale - 1f)) / 2f
        val maxY = (sh * (scale - 1f)) / 2f
        
        val expectedOffsetX = (-(expectedNx - 0.5f) * sw * scale).coerceIn(-maxX, maxX)
        val expectedOffsetY = (-(expectedNy - 0.5f) * sh * scale).coerceIn(-maxY, maxY)
        
        assertEquals(expectedOffsetX, ScreenCaptureManager.offsetX.value, 0.001f)
        assertEquals(expectedOffsetY, ScreenCaptureManager.offsetY.value, 0.001f)
    }
}
