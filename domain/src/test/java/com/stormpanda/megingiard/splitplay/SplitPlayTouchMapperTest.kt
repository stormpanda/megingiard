package com.stormpanda.megingiard.splitplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitPlayTouchMapperTest {

    @Test
    fun testTopScreenMapping() {
        // Center touch of the game box: physically (960, 540) on top screen
        // Expected: (540, 480) in game coordinate space
        val centerMapped = SplitPlayTouchMapper.mapTouch(screenId = 0, px = 960f, py = 540f)
        assertEquals(Pair(540f, 480f), centerMapped)

        // Left-most touch of the game box (top of landscape, bottom-left of top-half portrait)
        val leftMapped = SplitPlayTouchMapper.mapTouch(screenId = 0, px = 480f, py = 0f)
        assertEquals(Pair(0f, 960f), leftMapped)

        // Right-most touch of the game box (bottom of landscape, top-right of top-half portrait)
        val rightMapped = SplitPlayTouchMapper.mapTouch(screenId = 0, px = 1440f, py = 1080f)
        assertEquals(Pair(1080f, 0f), rightMapped)

        // Out of bounds - too far left
        val tooFarLeft = SplitPlayTouchMapper.mapTouch(screenId = 0, px = 470f, py = 500f)
        assertNull(tooFarLeft)

        // Out of bounds - too far right
        val tooFarRight = SplitPlayTouchMapper.mapTouch(screenId = 0, px = 1450f, py = 500f)
        assertNull(tooFarRight)
    }

    @Test
    fun testBottomScreenMapping() {
        // Center touch of the game box: physically (960, 540) on bottom screen
        // Expected: (540, 1440) in game coordinate space (middle of bottom-half portrait)
        val centerMapped = SplitPlayTouchMapper.mapTouch(screenId = 4, px = 960f, py = 540f)
        assertEquals(Pair(540f, 1440f), centerMapped)

        // Left-most touch of the game box (top-left of landscape, bottom-left of bottom-half portrait)
        val leftMapped = SplitPlayTouchMapper.mapTouch(screenId = 4, px = 480f, py = 0f)
        assertEquals(Pair(0f, 1920f), leftMapped)

        // Right-most touch of the game box (bottom-right of landscape, top-right of bottom-half portrait)
        val rightMapped = SplitPlayTouchMapper.mapTouch(screenId = 4, px = 1440f, py = 1080f)
        assertEquals(Pair(1080f, 960f), rightMapped)

        // Out of bounds - too far left
        val tooFarLeft = SplitPlayTouchMapper.mapTouch(screenId = 4, px = 470f, py = 500f)
        assertNull(tooFarLeft)

        // Out of bounds - too far right
        val tooFarRight = SplitPlayTouchMapper.mapTouch(screenId = 4, px = 1450f, py = 500f)
        assertNull(tooFarRight)
    }
}
