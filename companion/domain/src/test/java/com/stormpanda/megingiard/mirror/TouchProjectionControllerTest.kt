package com.stormpanda.megingiard.mirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchProjectionControllerTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var controller: TouchProjectionController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ScreenCaptureManager.setCutouts(emptyList())
        controller =
            TouchProjectionController(
                edgeZonePx = 48f,
                overlayAtBottom = true,
            )
    }

    @After
    fun tearDown() {
        ScreenCaptureManager.setCutouts(emptyList())
        controller.reset()
        Dispatchers.resetMain()
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

    @Test
    fun testOverlappingCutoutsSelectsTopMostCutout() {
        val bottomCutout =
            ScreenCutout(
                id = "bottom_cutout",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.1f,
                destY = 0.1f,
                destWidth = 0.8f,
                destHeight = 0.8f,
                touchProjectionEnabled = true,
            )
        val topCutout =
            ScreenCutout(
                id = "top_cutout",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 0.5f,
                srcHeight = 0.5f,
                destX = 0.2f,
                destY = 0.2f,
                destWidth = 0.4f,
                destHeight = 0.4f,
                touchProjectionEnabled = true,
            )
        ScreenCaptureManager.setCutouts(listOf(bottomCutout, topCutout))

        // Press at (300, 300) in 1000x1000 box (destX = 0.3, destY = 0.3)
        // This is inside both bottomCutout [100..900, 100..900] and topCutout [200..600, 200..600]
        val handled =
            controller.onPress(
                pointerId = 1L,
                x = 300f,
                y = 300f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = false,
                pointerCount = 1,
            )
        assertTrue(handled)
        assertEquals("top_cutout", controller.getActiveCutoutId(1L))
    }

    @Test
    fun testCircleCutoutHitTestingBounds() {
        val circleCutout =
            ScreenCutout(
                id = "circle_cutout",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.2f,
                destY = 0.2f,
                destWidth = 0.4f,
                destHeight = 0.4f,
                shape = CutoutShape.CIRCLE,
                touchProjectionEnabled = true,
            )
        ScreenCaptureManager.setCutouts(listOf(circleCutout))

        // Bounding box in 1000x1000:
        // destLeft = 200, destTop = 200, destWidth = 400, destHeight = 400
        // Center: cx = 400, cy = 400, radius = 200
        // Corner point: x = 210, y = 210 (inside bounding box [200..600, 200..600])
        // dx = 210 - 400 = -190, dy = 210 - 400 = -190
        // dx^2 + dy^2 = 190^2 + 190^2 = 72200 > radius^2 (40000) -> outside circle!
        val cornerHandled =
            controller.onPress(
                pointerId = 1L,
                x = 210f,
                y = 210f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = false,
                pointerCount = 1,
            )
        assertFalse(cornerHandled)

        // Center point: x = 400, y = 400
        // dx = 0, dy = 0 -> inside circle!
        val centerHandled =
            controller.onPress(
                pointerId = 2L,
                x = 400f,
                y = 400f,
                boxW = 1000f,
                boxH = 1000f,
                isConsumed = false,
                pointerCount = 1,
            )
        assertTrue(centerHandled)
        assertEquals("circle_cutout", controller.getActiveCutoutId(2L))
    }
}
