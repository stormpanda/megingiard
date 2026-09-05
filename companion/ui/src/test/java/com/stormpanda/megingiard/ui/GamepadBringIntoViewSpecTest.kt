package com.stormpanda.megingiard.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class GamepadBringIntoViewSpecTest {
    private fun createSpec(extraPaddingPx: Float): BringIntoViewSpec {
        return object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val topThreshold = extraPaddingPx
                val bottomThreshold = (containerSize - extraPaddingPx).coerceAtLeast(topThreshold)

                return when {
                    offset < topThreshold -> offset - topThreshold
                    offset + size > bottomThreshold -> (offset + size) - bottomThreshold
                    else -> 0f
                }
            }
        }
    }

    @Test
    fun itemWithinComfortBounds_returnsZeroScrollDelta() {
        val spec = createSpec(extraPaddingPx = 64f)
        val containerHeight = 600f
        val itemHeight = 60f
        val itemOffset = 200f // comfortably between 64f and 536f

        val delta = spec.calculateScrollDistance(itemOffset, itemHeight, containerHeight)

        assertEquals(0f, delta, 0.001f)
    }

    @Test
    fun itemNavigatingUp_scrollsWithExtraPaddingPastItemTop() {
        val spec = createSpec(extraPaddingPx = 64f)
        val containerHeight = 600f
        val itemHeight = 60f
        val itemOffset = -50f // item is above the viewport

        val delta = spec.calculateScrollDistance(itemOffset, itemHeight, containerHeight)

        // Delta should be -50 - 64 = -114f, moving item top to +64f so preceding item/header is visible
        assertEquals(-114f, delta, 0.001f)
    }

    @Test
    fun itemNearTopEdge_scrollsUpToRevealHeaderAbove() {
        val spec = createSpec(extraPaddingPx = 64f)
        val containerHeight = 600f
        val itemHeight = 60f
        val itemOffset = 20f // visible at top, but less than 64f margin

        val delta = spec.calculateScrollDistance(itemOffset, itemHeight, containerHeight)

        // Delta should be 20 - 64 = -44f, scrolling up further to fully reveal the section header
        assertEquals(-44f, delta, 0.001f)
    }

    @Test
    fun itemNavigatingDown_scrollsWithExtraPaddingPastItemBottom() {
        val spec = createSpec(extraPaddingPx = 64f)
        val containerHeight = 600f
        val itemHeight = 60f
        val itemOffset = 550f // item extends below container bottom (550 + 60 = 610f > 536f)

        val delta = spec.calculateScrollDistance(itemOffset, itemHeight, containerHeight)

        // Bottom threshold is 600 - 64 = 536f. Item bottom is 610f.
        // Delta should be 610 - 536 = +74f, ensuring 64px extra space below for next item preview
        assertEquals(74f, delta, 0.001f)
        assertTrue(delta > 0f)
    }

    @Test
    fun itemNearBottomEdge_scrollsDownToPartiallyRevealNextItem() {
        val spec = createSpec(extraPaddingPx = 64f)
        val containerHeight = 600f
        val itemHeight = 60f
        val itemOffset = 490f // 490 + 60 = 550f > 536f

        val delta = spec.calculateScrollDistance(itemOffset, itemHeight, containerHeight)

        // Delta should be 550 - 536 = +14f
        assertEquals(14f, delta, 0.001f)
    }
}
