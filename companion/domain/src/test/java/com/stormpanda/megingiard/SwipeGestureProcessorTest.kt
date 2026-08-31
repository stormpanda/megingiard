package com.stormpanda.megingiard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SwipeGestureProcessor] — the edge-swipe gesture state machine.
 *
 * The callbacks are injected here so the tests run on the JVM without touching
 * [AppStateManager] or Android framework APIs.
 */
class SwipeGestureProcessorTest {
    private val edgeZonePx = 60f
    private val thresholdPx = 80f
    private val containerH = 1000f

    private fun createProcessor(
        overlayAtBottom: Boolean = false,
        quickMenuBarZoneWidthPx: Float = 0f,
        customZoneCheck: ((Float, Float) -> Boolean)? = null,
        onTouchingChanged: (Boolean) -> Unit = {},
        onEdgeSwipe: () -> Unit = {},
        onSwipeProgress: (Float, Boolean) -> Unit = { _, _ -> },
        onSwipeCancel: () -> Unit = {},
        onHapticTick: () -> Unit = {},
    ) = SwipeGestureProcessor(
        edgeZonePx = edgeZonePx,
        swipeThresholdPx = thresholdPx,
        overlayAtBottom = overlayAtBottom,
        quickMenuBarZoneWidthPx = quickMenuBarZoneWidthPx,
        customZoneCheck = customZoneCheck,
        onTouchingChanged = onTouchingChanged,
        onEdgeSwipe = onEdgeSwipe,
        onSwipeProgress = onSwipeProgress,
        onSwipeCancel = onSwipeCancel,
        onHapticTick = onHapticTick,
    )

    private fun SwipeGestureProcessor.press(
        pointerY: Float,
        containerHeight: Float = containerH,
        pointerX: Float = 0f,
        containerWidth: Float = 0f,
    ) = onPress(pointerY, containerHeight, pointerX, containerWidth)

    @Test
    fun `onPress calls onTouchingChanged(true)`() {
        var touching = false
        createProcessor(onTouchingChanged = { touching = it }).press(30f)
        assertTrue(touching)
    }

    @Test
    fun `onRelease with all pointers up calls onTouchingChanged(false)`() {
        var touching = true
        val p = createProcessor(onTouchingChanged = { touching = it })
        p.press(30f)
        p.onRelease(allPointersUp = true)
        assertFalse(touching)
    }

    @Test
    fun `onRelease with remaining pointers does not clear touching`() {
        var touching = false
        val p = createProcessor(onTouchingChanged = { touching = it })
        p.press(30f)
        assertTrue(touching)
        p.onRelease(allPointersUp = false)
        assertTrue(touching)
    }

    @Test
    fun `top edge swipe triggers onEdgeSwipe only upon release past threshold`() {
        var swipeCount = 0
        val p = createProcessor(onEdgeSwipe = { swipeCount++ })
        p.press(40f)
        p.onMove(40f + thresholdPx)
        assertEquals(0, swipeCount)
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount)
    }

    @Test
    fun `top edge swipe does not trigger below threshold`() {
        var swipeCount = 0
        val p = createProcessor(onEdgeSwipe = { swipeCount++ })
        p.press(40f)
        p.onMove(40f + thresholdPx - 1f)
        p.onRelease(allPointersUp = true)
        assertEquals(0, swipeCount)
    }

    @Test
    fun `press outside top edge zone does not trigger swipe`() {
        var swipeCount = 0
        val p = createProcessor(onEdgeSwipe = { swipeCount++ })
        p.press(edgeZonePx + 1f)
        p.onMove(edgeZonePx + 1f + thresholdPx + 10f)
        p.onRelease(allPointersUp = true)
        assertEquals(0, swipeCount)
    }

    @Test
    fun `bottom edge swipe triggers onEdgeSwipe only upon release past threshold`() {
        var swipeCount = 0
        val p = createProcessor(overlayAtBottom = true, onEdgeSwipe = { swipeCount++ })
        val startY = containerH - edgeZonePx + 5f
        p.press(startY)
        p.onMove(startY - thresholdPx)
        assertEquals(0, swipeCount)
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount)
    }

    @Test
    fun `bottom edge swipe does not trigger below threshold`() {
        var swipeCount = 0
        val p = createProcessor(overlayAtBottom = true, onEdgeSwipe = { swipeCount++ })
        val startY = containerH - 10f
        p.press(startY)
        p.onMove(startY - (thresholdPx - 1f))
        p.onRelease(allPointersUp = true)
        assertEquals(0, swipeCount)
    }

    @Test
    fun `swipe fires at most once per gesture even with continued movement`() {
        var swipeCount = 0
        val p = createProcessor(onEdgeSwipe = { swipeCount++ })
        p.press(10f)
        p.onMove(10f + thresholdPx)
        p.onMove(10f + thresholdPx + 20f)
        p.onMove(10f + thresholdPx + 40f)
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount)
    }

    @Test
    fun `swipe can fire again after release`() {
        var swipeCount = 0
        val p = createProcessor(onEdgeSwipe = { swipeCount++ })
        p.press(10f)
        p.onMove(10f + thresholdPx)
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount)

        p.press(10f)
        p.onMove(10f + thresholdPx)
        p.onRelease(allPointersUp = true)
        assertEquals(2, swipeCount)
    }

    @Test
    fun `reports progress on move`() {
        var progressDelta = 0f
        var progressPast = false
        val p =
            createProcessor(onSwipeProgress = { delta, past ->
                progressDelta = delta
                progressPast = past
            })
        p.press(10f)
        p.onMove(10f + 50f)
        assertEquals(50f, progressDelta)
        assertFalse(progressPast)

        p.onMove(10f + thresholdPx + 10f)
        assertEquals(thresholdPx + 10f, progressDelta)
        assertTrue(progressPast)
    }

    @Test
    fun `triggers haptic tick exactly once when crossing threshold`() {
        var hapticCount = 0
        val p = createProcessor(onHapticTick = { hapticCount++ })
        p.press(10f)
        p.onMove(10f + 50f)
        assertEquals(0, hapticCount)

        p.onMove(10f + thresholdPx)
        assertEquals(1, hapticCount)

        p.onMove(10f + thresholdPx + 10f)
        assertEquals(1, hapticCount)

        p.onMove(10f + 50f)
        assertEquals(1, hapticCount)

        p.onMove(10f + thresholdPx)
        assertEquals(2, hapticCount)
    }

    @Test
    fun `triggers cancel when released below threshold`() {
        var cancelled = false
        val p = createProcessor(onSwipeCancel = { cancelled = true })
        p.press(10f)
        p.onMove(10f + 50f)
        p.onRelease(allPointersUp = true)
        assertTrue(cancelled)
    }

    @Test
    fun `isNearEdge is true when press lands in edge zone`() {
        val p = createProcessor()
        p.press(30f)
        assertTrue(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is false when press lands outside edge zone`() {
        val p = createProcessor()
        p.press(edgeZonePx + 10f)
        assertFalse(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is reset to false after release`() {
        val p = createProcessor()
        p.press(30f)
        assertTrue(p.isNearEdge)
        p.onRelease(allPointersUp = true)
        assertFalse(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is true when press lands in edge zone and within quick menu bar zone`() {
        val p = createProcessor(quickMenuBarZoneWidthPx = 100f)
        p.press(pointerY = 30f, pointerX = 180f, containerWidth = 400f)
        assertTrue(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is false when press lands in edge zone but outside quick menu bar zone`() {
        val p = createProcessor(quickMenuBarZoneWidthPx = 100f)
        p.press(pointerY = 30f, pointerX = 100f, containerWidth = 400f)
        assertFalse(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is true when press lands in edge zone and within custom zone check`() {
        val p = createProcessor(customZoneCheck = { x, _ -> x in 20f..80f })
        p.press(pointerY = 30f, pointerX = 50f, containerWidth = 400f)
        assertTrue(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is false when press lands in edge zone but outside custom zone check`() {
        val p = createProcessor(customZoneCheck = { x, _ -> x in 20f..80f })
        p.press(pointerY = 30f, pointerX = 100f, containerWidth = 400f)
        assertFalse(p.isNearEdge)
    }
}
