package com.stormpanda.megingiard.mirror

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveCutoutControllerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun sampleInteractiveCutout(
        id: String = "interactive-1",
        snapBackMode: CutoutSnapBackMode = CutoutSnapBackMode.OFF,
    ) = ScreenCutout(
        id = id,
        name = "Interactive 1",
        srcX = 0.2f,
        srcY = 0.2f,
        srcWidth = 0.4f,
        srcHeight = 0.4f,
        destX = 0.1f,
        destY = 0.1f,
        destWidth = 0.5f,
        destHeight = 0.5f,
        interactivePanZoom = true,
        snapBackMode = snapBackMode,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        InteractiveCutoutController.scope = CoroutineScope(testDispatcher)
        InteractiveCutoutController.reset()
    }

    @After
    fun tearDown() {
        InteractiveCutoutController.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun `onPress ignores non-interactive or touch-projected cutouts`() {
        val nonInteractive = sampleInteractiveCutout(id = "c1").copy(interactivePanZoom = false)
        val touchProjected = sampleInteractiveCutout(id = "c2").copy(touchProjectionEnabled = true)
        val cutouts = listOf(nonInteractive, touchProjected)

        val handled =
            InteractiveCutoutController.onPress(
                pointerId = 1L,
                xPx = 200f,
                yPx = 200f,
                boxW = 1000f,
                boxH = 1000f,
                cutouts = cutouts,
            )

        assertFalse(handled)
        assertFalse(InteractiveCutoutController.isPointerTracked(1L))
    }

    @Test
    fun `1-finger pan moves source crop and updates override`() {
        val cutout = sampleInteractiveCutout()
        val cutouts = listOf(cutout)

        val pressed =
            InteractiveCutoutController.onPress(
                pointerId = 1L,
                xPx = 200f,
                yPx = 200f,
                boxW = 1000f,
                boxH = 1000f,
                cutouts = cutouts,
            )
        assertTrue(pressed)
        assertTrue(InteractiveCutoutController.isPointerTracked(1L))

        // Move pointer right by 50px on 500px wide cutout (destWidth = 0.5 * 1000 = 500px)
        // deltaNormX = 50 / 500 = 0.1
        // rawDx = -0.1 * 0.4 = -0.04 -> srcX = 0.16
        val moved =
            InteractiveCutoutController.onMove(
                pointerId = 1L,
                xPx = 250f,
                yPx = 200f,
                boxW = 1000f,
                boxH = 1000f,
                cutouts = cutouts,
            )
        assertTrue(moved)

        val effective = InteractiveCutoutController.getEffectiveCrop(cutout)
        assertEquals(0.16f, effective.srcX, 0.001f)
        assertEquals(0.2f, effective.srcY, 0.001f)
    }

    @Test
    fun `Instant snap-back animates crop back to default on release`() {
        val cutout = sampleInteractiveCutout(snapBackMode = CutoutSnapBackMode.INSTANT)
        val cutouts = listOf(cutout)

        InteractiveCutoutController.onPress(
            pointerId = 1L,
            xPx = 200f,
            yPx = 200f,
            boxW = 1000f,
            boxH = 1000f,
            cutouts = cutouts,
        )
        InteractiveCutoutController.onMove(
            pointerId = 1L,
            xPx = 250f,
            yPx = 200f,
            boxW = 1000f,
            boxH = 1000f,
            cutouts = cutouts,
        )

        var hapticFired = false
        InteractiveCutoutController.onHapticFeedback = { hapticFired = true }

        InteractiveCutoutController.onRelease(1L, cutouts)

        // Advance animation
        testScope.advanceUntilIdle()

        val effective = InteractiveCutoutController.getEffectiveCrop(cutout)
        assertEquals(0.2f, effective.srcX, 0.001f)
        assertEquals(0.2f, effective.srcY, 0.001f)
        assertTrue(hapticFired)
    }

    @Test
    fun `Off snap-back mode retains panned position after release`() {
        val cutout = sampleInteractiveCutout(snapBackMode = CutoutSnapBackMode.OFF)
        val cutouts = listOf(cutout)

        InteractiveCutoutController.onPress(
            pointerId = 1L,
            xPx = 200f,
            yPx = 200f,
            boxW = 1000f,
            boxH = 1000f,
            cutouts = cutouts,
        )
        InteractiveCutoutController.onMove(
            pointerId = 1L,
            xPx = 250f,
            yPx = 200f,
            boxW = 1000f,
            boxH = 1000f,
            cutouts = cutouts,
        )

        InteractiveCutoutController.onRelease(1L, cutouts)
        testScope.advanceUntilIdle()

        val effective = InteractiveCutoutController.getEffectiveCrop(cutout)
        assertEquals(0.16f, effective.srcX, 0.001f)
        assertEquals(0.2f, effective.srcY, 0.001f)
    }

    @Test
    fun `onFollowTouchReceived cancels active animation and removes override`() {
        val cutout = sampleInteractiveCutout(snapBackMode = CutoutSnapBackMode.OFF)
        val cutouts = listOf(cutout)

        InteractiveCutoutController.onPress(
            pointerId = 1L,
            xPx = 200f,
            yPx = 200f,
            boxW = 1000f,
            boxH = 1000f,
            cutouts = cutouts,
        )
        InteractiveCutoutController.onMove(
            pointerId = 1L,
            xPx = 250f,
            yPx = 200f,
            boxW = 1000f,
            boxH = 1000f,
            cutouts = cutouts,
        )

        InteractiveCutoutController.onFollowTouchReceived(cutout.id)

        val effective = InteractiveCutoutController.getEffectiveCrop(cutout)
        assertEquals(0.2f, effective.srcX, 0.001f)
        assertEquals(0.2f, effective.srcY, 0.001f)
    }

    @Test
    fun `2-finger pinch and pan adjusts zoom and translation and fires onCropUpdated`() {
        val cutout = sampleInteractiveCutout(snapBackMode = CutoutSnapBackMode.OFF)
        val cutouts = listOf(cutout)

        var cropUpdateCount = 0
        InteractiveCutoutController.onCropUpdated = { cropUpdateCount++ }

        // Press finger 1 at (200, 200) and finger 2 at (400, 200) -> initial distance = 200px, midpoint = (300, 200)
        InteractiveCutoutController.onPress(pointerId = 1L, xPx = 200f, yPx = 200f, boxW = 1000f, boxH = 1000f, cutouts = cutouts)
        InteractiveCutoutController.onPress(pointerId = 2L, xPx = 400f, yPx = 200f, boxW = 1000f, boxH = 1000f, cutouts = cutouts)

        // Move finger 1 to (150, 200) and finger 2 to (450, 200) -> new distance = 300px (1.5x zoom out / magnification)
        InteractiveCutoutController.onMove(pointerId = 1L, xPx = 150f, yPx = 200f, boxW = 1000f, boxH = 1000f, cutouts = cutouts)
        InteractiveCutoutController.onMove(pointerId = 2L, xPx = 450f, yPx = 200f, boxW = 1000f, boxH = 1000f, cutouts = cutouts)

        assertTrue(cropUpdateCount >= 2)
        val effective = InteractiveCutoutController.getEffectiveCrop(cutout)
        // Crop width should have decreased from 0.4 due to magnification
        assertTrue(effective.srcWidth < 0.4f)
    }

    @Test
    fun `transformPanDelta rotates and flips pan deltas correctly`() {
        val rawX = 0.2f
        val rawY = 0.1f

        // 0 deg, no flip
        val deg0 = transformPanDelta(rawX, rawY, rotation = 0, flipHorizontal = false, flipVertical = false)
        assertEquals(0.2f, deg0.first, 0.0001f)
        assertEquals(0.1f, deg0.second, 0.0001f)

        // 90 deg: dx = rawY = 0.1, dy = -rawX = -0.2
        val deg90 = transformPanDelta(rawX, rawY, rotation = 90, flipHorizontal = false, flipVertical = false)
        assertEquals(0.1f, deg90.first, 0.0001f)
        assertEquals(-0.2f, deg90.second, 0.0001f)

        // 180 deg: dx = -rawX = -0.2, dy = -rawY = -0.1
        val deg180 = transformPanDelta(rawX, rawY, rotation = 180, flipHorizontal = false, flipVertical = false)
        assertEquals(-0.2f, deg180.first, 0.0001f)
        assertEquals(-0.1f, deg180.second, 0.0001f)

        // 270 deg: dx = -rawY = -0.1, dy = rawX = 0.2
        val deg270 = transformPanDelta(rawX, rawY, rotation = 270, flipHorizontal = false, flipVertical = false)
        assertEquals(-0.1f, deg270.first, 0.0001f)
        assertEquals(0.2f, deg270.second, 0.0001f)

        // Horizontal flip on 0 deg: dx = -rawX = -0.2, dy = rawY = 0.1
        val flipH = transformPanDelta(rawX, rawY, rotation = 0, flipHorizontal = true, flipVertical = false)
        assertEquals(-0.2f, flipH.first, 0.0001f)
        assertEquals(0.1f, flipH.second, 0.0001f)

        // Vertical flip on 0 deg: dx = rawX = 0.2, dy = -rawY = -0.1
        val flipV = transformPanDelta(rawX, rawY, rotation = 0, flipHorizontal = false, flipVertical = true)
        assertEquals(0.2f, flipV.first, 0.0001f)
        assertEquals(-0.1f, flipV.second, 0.0001f)
    }

    @Test
    fun `transformFocalPoint rotates and flips pinch focal point correctly`() {
        val rawX = 0.2f
        val rawY = 0.3f

        // 0 deg, no flip: (0.2, 0.3)
        val deg0 = transformFocalPoint(rawX, rawY, rotation = 0, flipHorizontal = false, flipVertical = false)
        assertEquals(0.2f, deg0.first, 0.0001f)
        assertEquals(0.3f, deg0.second, 0.0001f)

        // 90 deg: u = rawY = 0.3, v = 1 - rawX = 0.8
        val deg90 = transformFocalPoint(rawX, rawY, rotation = 90, flipHorizontal = false, flipVertical = false)
        assertEquals(0.3f, deg90.first, 0.0001f)
        assertEquals(0.8f, deg90.second, 0.0001f)

        // 180 deg: u = 1 - rawX = 0.8, v = 1 - rawY = 0.7
        val deg180 = transformFocalPoint(rawX, rawY, rotation = 180, flipHorizontal = false, flipVertical = false)
        assertEquals(0.8f, deg180.first, 0.0001f)
        assertEquals(0.7f, deg180.second, 0.0001f)

        // 270 deg: u = 1 - rawY = 0.7, v = rawX = 0.2
        val deg270 = transformFocalPoint(rawX, rawY, rotation = 270, flipHorizontal = false, flipVertical = false)
        assertEquals(0.7f, deg270.first, 0.0001f)
        assertEquals(0.2f, deg270.second, 0.0001f)

        // Horizontal flip on 0 deg: u = 1 - 0.2 = 0.8, v = 0.3
        val flipH = transformFocalPoint(rawX, rawY, rotation = 0, flipHorizontal = true, flipVertical = false)
        assertEquals(0.8f, flipH.first, 0.0001f)
        assertEquals(0.3f, flipH.second, 0.0001f)

        // Vertical flip on 0 deg: u = 0.2, v = 1 - 0.3 = 0.7
        val flipV = transformFocalPoint(rawX, rawY, rotation = 0, flipHorizontal = false, flipVertical = true)
        assertEquals(0.2f, flipV.first, 0.0001f)
        assertEquals(0.7f, flipV.second, 0.0001f)

        // Both flips on 0 deg: (0.8, 0.7)
        val flipBoth = transformFocalPoint(rawX, rawY, rotation = 0, flipHorizontal = true, flipVertical = true)
        assertEquals(0.8f, flipBoth.first, 0.0001f)
        assertEquals(0.7f, flipBoth.second, 0.0001f)
    }
}
