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
    private val EDGE_ZONE_PX = 60f
    private val THRESHOLD_PX = 80f
    private val CONTAINER_H = 1000f

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a top-edge processor (quick menu bar at top). */
    private fun topProcessor(
        onTouchingChanged: (Boolean) -> Unit = {},
        onEdgeSwipe: () -> Unit = {},
        onSwipeProgress: (Float, Boolean) -> Unit = { _, _ -> },
        onSwipeCancel: () -> Unit = {},
        onHapticTick: () -> Unit = {},
    ) = SwipeGestureProcessor(
        edgeZonePx = EDGE_ZONE_PX,
        swipeThresholdPx = THRESHOLD_PX,
        overlayAtBottom = false,
        onTouchingChanged = onTouchingChanged,
        onEdgeSwipe = onEdgeSwipe,
        onSwipeProgress = onSwipeProgress,
        onSwipeCancel = onSwipeCancel,
        onHapticTick = onHapticTick,
    )

    /** Creates a bottom-edge processor (quick menu bar at bottom). */
    private fun bottomProcessor(
        onTouchingChanged: (Boolean) -> Unit = {},
        onEdgeSwipe: () -> Unit = {},
        onSwipeProgress: (Float, Boolean) -> Unit = { _, _ -> },
        onSwipeCancel: () -> Unit = {},
        onHapticTick: () -> Unit = {},
    ) = SwipeGestureProcessor(
        edgeZonePx = EDGE_ZONE_PX,
        swipeThresholdPx = THRESHOLD_PX,
        overlayAtBottom = true,
        onTouchingChanged = onTouchingChanged,
        onEdgeSwipe = onEdgeSwipe,
        onSwipeProgress = onSwipeProgress,
        onSwipeCancel = onSwipeCancel,
        onHapticTick = onHapticTick,
    )

    // ── onPress — touching callback ───────────────────────────────────────────

    @Test
    fun `onPress calls onTouchingChanged(true)`() {
        var touching = false
        topProcessor(onTouchingChanged = { touching = it }).onPress(30f, CONTAINER_H)
        assertTrue(touching)
    }

    @Test
    fun `onRelease with all pointers up calls onTouchingChanged(false)`() {
        var touching = true
        val p = topProcessor(onTouchingChanged = { touching = it })
        p.onPress(30f, CONTAINER_H)
        p.onRelease(allPointersUp = true)
        assertFalse(touching)
    }

    @Test
    fun `onRelease with remaining pointers does not clear touching`() {
        var touching = false
        val p = topProcessor(onTouchingChanged = { touching = it })
        p.onPress(30f, CONTAINER_H)
        assertTrue(touching)
        p.onRelease(allPointersUp = false) // second pointer still down
        assertTrue(touching)
    }

    // ── top-edge swipe detection ──────────────────────────────────────────────

    @Test
    fun `top edge swipe triggers onEdgeSwipe only upon release past threshold`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(pointerY = 40f, containerHeight = CONTAINER_H) // within top edge zone
        p.onMove(pointerY = 40f + THRESHOLD_PX) // exactly at threshold
        assertEquals(0, swipeCount) // should NOT trigger on move
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount) // triggers on release
    }

    @Test
    fun `top edge swipe does not trigger below threshold`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(40f, CONTAINER_H)
        p.onMove(40f + THRESHOLD_PX - 1f) // 1px short
        p.onRelease(allPointersUp = true)
        assertEquals(0, swipeCount)
    }

    @Test
    fun `press outside top edge zone does not trigger swipe`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(pointerY = EDGE_ZONE_PX + 1f, containerHeight = CONTAINER_H) // outside zone
        p.onMove(pointerY = EDGE_ZONE_PX + 1f + THRESHOLD_PX + 10f)
        p.onRelease(allPointersUp = true)
        assertEquals(0, swipeCount)
    }

    // ── bottom-edge swipe detection ───────────────────────────────────────────

    @Test
    fun `bottom edge swipe triggers onEdgeSwipe only upon release past threshold`() {
        var swipeCount = 0
        val p = bottomProcessor(onEdgeSwipe = { swipeCount++ })
        val startY = CONTAINER_H - EDGE_ZONE_PX + 5f // inside bottom edge zone
        p.onPress(startY, CONTAINER_H)
        p.onMove(startY - THRESHOLD_PX) // moved up enough
        assertEquals(0, swipeCount) // should NOT trigger on move
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount) // triggers on release
    }

    @Test
    fun `bottom edge swipe does not trigger below threshold`() {
        var swipeCount = 0
        val p = bottomProcessor(onEdgeSwipe = { swipeCount++ })
        val startY = CONTAINER_H - 10f
        p.onPress(startY, CONTAINER_H)
        p.onMove(startY - (THRESHOLD_PX - 1f))
        p.onRelease(allPointersUp = true)
        assertEquals(0, swipeCount)
    }

    // ── swipeTriggered guard (fire-once-per-gesture) ──────────────────────────

    @Test
    fun `swipe fires at most once per gesture even with continued movement`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(10f, CONTAINER_H)
        p.onMove(10f + THRESHOLD_PX)
        p.onMove(10f + THRESHOLD_PX + 20f)
        p.onMove(10f + THRESHOLD_PX + 40f)
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount)
    }

    @Test
    fun `swipe can fire again after release`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        // First gesture
        p.onPress(10f, CONTAINER_H)
        p.onMove(10f + THRESHOLD_PX)
        p.onRelease(allPointersUp = true)
        assertEquals(1, swipeCount)
        // Second gesture
        p.onPress(10f, CONTAINER_H)
        p.onMove(10f + THRESHOLD_PX)
        p.onRelease(allPointersUp = true)
        assertEquals(2, swipeCount)
    }

    // ── progress, cancel, and haptics ─────────────────────────────────────────

    @Test
    fun `reports progress on move`() {
        var progressDelta = 0f
        var progressPast = false
        val p =
            topProcessor(onSwipeProgress = { delta, past ->
                progressDelta = delta
                progressPast = past
            })
        p.onPress(10f, CONTAINER_H)
        p.onMove(10f + 50f)
        assertEquals(50f, progressDelta)
        assertFalse(progressPast)

        p.onMove(10f + THRESHOLD_PX + 10f)
        assertEquals(THRESHOLD_PX + 10f, progressDelta)
        assertTrue(progressPast)
    }

    @Test
    fun `triggers haptic tick exactly once when crossing threshold`() {
        var hapticCount = 0
        val p = topProcessor(onHapticTick = { hapticCount++ })
        p.onPress(10f, CONTAINER_H)
        p.onMove(10f + 50f)
        assertEquals(0, hapticCount)

        p.onMove(10f + THRESHOLD_PX) // crosses threshold
        assertEquals(1, hapticCount)

        p.onMove(10f + THRESHOLD_PX + 10f) // stays past threshold
        assertEquals(1, hapticCount) // no extra ticks

        p.onMove(10f + 50f) // goes back below threshold
        assertEquals(1, hapticCount)

        p.onMove(10f + THRESHOLD_PX) // crosses again
        assertEquals(2, hapticCount)
    }

    @Test
    fun `triggers cancel when released below threshold`() {
        var cancelled = false
        val p = topProcessor(onSwipeCancel = { cancelled = true })
        p.onPress(10f, CONTAINER_H)
        p.onMove(10f + 50f)
        p.onRelease(allPointersUp = true)
        assertTrue(cancelled)
    }

    // ── isNearEdge ────────────────────────────────────────────────────────────

    @Test
    fun `isNearEdge is true when press lands in edge zone`() {
        val p = topProcessor()
        p.onPress(30f, CONTAINER_H)
        assertTrue(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is false when press lands outside edge zone`() {
        val p = topProcessor()
        p.onPress(EDGE_ZONE_PX + 10f, CONTAINER_H)
        assertFalse(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is reset to false after release`() {
        val p = topProcessor()
        p.onPress(30f, CONTAINER_H)
        assertTrue(p.isNearEdge)
        p.onRelease(allPointersUp = true)
        assertFalse(p.isNearEdge)
    }

    // ── quick-menu-bar-zone horizontal constraints ────────────────────────────

    @Test
    fun `isNearEdge is true when press lands in edge zone and within quick menu bar zone`() {
        val p =
            SwipeGestureProcessor(
                edgeZonePx = EDGE_ZONE_PX,
                swipeThresholdPx = THRESHOLD_PX,
                overlayAtBottom = false,
                quickMenuBarZoneWidthPx = 100f,
                onTouchingChanged = {},
                onEdgeSwipe = {},
            )
        // container width = 400f -> center = 200f -> quick menu bar zone = [150f, 250f]
        p.onPress(pointerY = 30f, containerHeight = CONTAINER_H, pointerX = 180f, containerWidth = 400f)
        assertTrue(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is false when press lands in edge zone but outside quick menu bar zone`() {
        val p =
            SwipeGestureProcessor(
                edgeZonePx = EDGE_ZONE_PX,
                swipeThresholdPx = THRESHOLD_PX,
                overlayAtBottom = false,
                quickMenuBarZoneWidthPx = 100f,
                onTouchingChanged = {},
                onEdgeSwipe = {},
            )
        // container width = 400f -> center = 200f -> quick menu bar zone = [150f, 250f]
        p.onPress(pointerY = 30f, containerHeight = CONTAINER_H, pointerX = 100f, containerWidth = 400f)
        assertFalse(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is true when press lands in edge zone and within custom zone check`() {
        val p =
            SwipeGestureProcessor(
                edgeZonePx = EDGE_ZONE_PX,
                swipeThresholdPx = THRESHOLD_PX,
                overlayAtBottom = false,
                customZoneCheck = { x, _ -> x >= 20f && x <= 80f },
                onTouchingChanged = {},
                onEdgeSwipe = {},
            )
        p.onPress(pointerY = 30f, containerHeight = CONTAINER_H, pointerX = 50f, containerWidth = 400f)
        assertTrue(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is false when press lands in edge zone but outside custom zone check`() {
        val p =
            SwipeGestureProcessor(
                edgeZonePx = EDGE_ZONE_PX,
                swipeThresholdPx = THRESHOLD_PX,
                overlayAtBottom = false,
                customZoneCheck = { x, _ -> x >= 20f && x <= 80f },
                onTouchingChanged = {},
                onEdgeSwipe = {},
            )
        p.onPress(pointerY = 30f, containerHeight = CONTAINER_H, pointerX = 100f, containerWidth = 400f)
        assertFalse(p.isNearEdge)
    }
}
