package com.stormpanda.megingiard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SwipeGestureProcessor] — the edge-swipe gesture state machine.
 *
 * The callbacks (`onTouchingChanged`, `onEdgeSwipe`) are injected here so the tests
 * run on the JVM without touching [AppStateManager] or Android framework APIs.
 */
class SwipeGestureProcessorTest {

    private val EDGE_ZONE_PX   = 60f
    private val THRESHOLD_PX   = 80f
    private val CONTAINER_H    = 1000f

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a top-edge processor (pill at top). */
    private fun topProcessor(
        onTouchingChanged: (Boolean) -> Unit = {},
        onEdgeSwipe: () -> Unit = {},
    ) = SwipeGestureProcessor(
        edgeZonePx        = EDGE_ZONE_PX,
        swipeThresholdPx  = THRESHOLD_PX,
        overlayAtBottom   = false,
        onTouchingChanged = onTouchingChanged,
        onEdgeSwipe       = onEdgeSwipe,
    )

    /** Creates a bottom-edge processor (pill at bottom). */
    private fun bottomProcessor(
        onTouchingChanged: (Boolean) -> Unit = {},
        onEdgeSwipe: () -> Unit = {},
    ) = SwipeGestureProcessor(
        edgeZonePx        = EDGE_ZONE_PX,
        swipeThresholdPx  = THRESHOLD_PX,
        overlayAtBottom   = true,
        onTouchingChanged = onTouchingChanged,
        onEdgeSwipe       = onEdgeSwipe,
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
        p.onRelease(allPointersUp = false)  // second pointer still down
        assertTrue(touching)
    }

    // ── top-edge swipe detection ──────────────────────────────────────────────

    @Test
    fun `top edge swipe triggers onEdgeSwipe when moving down past threshold`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(pointerY = 40f, containerHeight = CONTAINER_H)  // within top edge zone
        p.onMove(pointerY = 40f + THRESHOLD_PX)                   // exactly at threshold
        assertEquals(1, swipeCount)
    }

    @Test
    fun `top edge swipe does not trigger below threshold`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(40f, CONTAINER_H)
        p.onMove(40f + THRESHOLD_PX - 1f)   // 1px short
        assertEquals(0, swipeCount)
    }

    @Test
    fun `press outside top edge zone does not trigger swipe`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(pointerY = EDGE_ZONE_PX + 1f, containerHeight = CONTAINER_H)  // outside zone
        p.onMove(pointerY = EDGE_ZONE_PX + 1f + THRESHOLD_PX + 10f)
        assertEquals(0, swipeCount)
    }

    // ── bottom-edge swipe detection ───────────────────────────────────────────

    @Test
    fun `bottom edge swipe triggers onEdgeSwipe when moving up past threshold`() {
        var swipeCount = 0
        val p = bottomProcessor(onEdgeSwipe = { swipeCount++ })
        val startY = CONTAINER_H - EDGE_ZONE_PX + 5f              // inside bottom edge zone
        p.onPress(startY, CONTAINER_H)
        p.onMove(startY - THRESHOLD_PX)                            // moved up enough
        assertEquals(1, swipeCount)
    }

    @Test
    fun `bottom edge swipe does not trigger below threshold`() {
        var swipeCount = 0
        val p = bottomProcessor(onEdgeSwipe = { swipeCount++ })
        val startY = CONTAINER_H - 10f
        p.onPress(startY, CONTAINER_H)
        p.onMove(startY - (THRESHOLD_PX - 1f))
        assertEquals(0, swipeCount)
    }

    // ── swipeTriggered guard (fire-once-per-gesture) ──────────────────────────

    @Test
    fun `swipe fires at most once per gesture even with continued movement`() {
        var swipeCount = 0
        val p = topProcessor(onEdgeSwipe = { swipeCount++ })
        p.onPress(10f, CONTAINER_H)
        p.onMove(10f + THRESHOLD_PX)         // fires
        p.onMove(10f + THRESHOLD_PX + 20f)   // should NOT fire again
        p.onMove(10f + THRESHOLD_PX + 40f)
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
        assertEquals(2, swipeCount)
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

    // ── pill-zone horizontal constraints ──────────────────────────────────────

    @Test
    fun `isNearEdge is true when press lands in edge zone and within pill zone`() {
        val p = SwipeGestureProcessor(
            edgeZonePx = EDGE_ZONE_PX,
            swipeThresholdPx = THRESHOLD_PX,
            overlayAtBottom = false,
            pillZoneWidthPx = 100f,
            onTouchingChanged = {},
            onEdgeSwipe = {},
        )
        // container width = 400f -> center = 200f -> pill zone = [150f, 250f]
        p.onPress(pointerY = 30f, containerHeight = CONTAINER_H, pointerX = 180f, containerWidth = 400f)
        assertTrue(p.isNearEdge)
    }

    @Test
    fun `isNearEdge is false when press lands in edge zone but outside pill zone`() {
        val p = SwipeGestureProcessor(
            edgeZonePx = EDGE_ZONE_PX,
            swipeThresholdPx = THRESHOLD_PX,
            overlayAtBottom = false,
            pillZoneWidthPx = 100f,
            onTouchingChanged = {},
            onEdgeSwipe = {},
        )
        // container width = 400f -> center = 200f -> pill zone = [150f, 250f]
        p.onPress(pointerY = 30f, containerHeight = CONTAINER_H, pointerX = 100f, containerWidth = 400f)
        assertFalse(p.isNearEdge)
    }
}
