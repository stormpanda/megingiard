package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.floorMod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "VertRollingCarouselTest"

class VerticalRollingCarouselTest {
    @Test
    fun testComputeRollingRingSize_isMultipleOfItemCountAndCoversWindow() {
        AppLog.d(TAG, "Testing computeRollingRingSize across item and window sizes")
        assertEquals(0, computeRollingRingSize(itemCount = 0, visibleItemsCount = 3))
        assertEquals(5, computeRollingRingSize(itemCount = 1, visibleItemsCount = 3))
        assertEquals(6, computeRollingRingSize(itemCount = 2, visibleItemsCount = 3))
        assertEquals(6, computeRollingRingSize(itemCount = 3, visibleItemsCount = 3))
        assertEquals(8, computeRollingRingSize(itemCount = 4, visibleItemsCount = 3))
        assertEquals(5, computeRollingRingSize(itemCount = 5, visibleItemsCount = 3))
        assertEquals(12, computeRollingRingSize(itemCount = 12, visibleItemsCount = 3))
        assertEquals(8, computeRollingRingSize(itemCount = 4, visibleItemsCount = 5))

        for (itemCount in 1..20) {
            val ringSize = computeRollingRingSize(itemCount = itemCount, visibleItemsCount = 3)
            assertEquals(0, ringSize % itemCount)
            assertTrue("ringSize=$ringSize must be >= 5 for itemCount=$itemCount", ringSize >= 5)
        }
    }

    @Test
    fun testRollingOffsetTracker_shortestWrapAroundAndDynamicCountReset() {
        AppLog.d(TAG, "Testing RollingOffsetTracker wrap-around and dynamic itemCount reset")
        val tracker = RollingOffsetTracker(initialIndex = 0)
        assertEquals(0f, tracker.resolveTarget(selectedIndex = 0, itemCount = 4), 0.0001f)
        assertEquals(1f, tracker.resolveTarget(selectedIndex = 1, itemCount = 4), 0.0001f)
        assertEquals(2f, tracker.resolveTarget(selectedIndex = 2, itemCount = 4), 0.0001f)
        assertEquals(3f, tracker.resolveTarget(selectedIndex = 3, itemCount = 4), 0.0001f)
        // Forward wrap 3 -> 0 advances to +4f rather than rewinding -3 slots
        assertEquals(4f, tracker.resolveTarget(selectedIndex = 0, itemCount = 4), 0.0001f)
        // Backward wrap 0 -> 3 steps back to +3f
        assertEquals(3f, tracker.resolveTarget(selectedIndex = 3, itemCount = 4), 0.0001f)

        // Wrap forward another full turn to offset = 8f (selectedIndex = 0), then change itemCount 4 -> 5:
        // must re-anchor targetOffset to selectedIndex so modular ring mapping stays congruent mod 5.
        tracker.resolveTarget(selectedIndex = 0, itemCount = 4)
        tracker.resolveTarget(selectedIndex = 1, itemCount = 4)
        tracker.resolveTarget(selectedIndex = 2, itemCount = 4)
        tracker.resolveTarget(selectedIndex = 3, itemCount = 4)
        assertEquals(8f, tracker.resolveTarget(selectedIndex = 0, itemCount = 4), 0.0001f)
        assertEquals(0f, tracker.resolveTarget(selectedIndex = 0, itemCount = 5), 0.0001f)
    }

    @Test
    fun testComputeRollingRingDiff_preservesItemMappingAcrossMultiTurnRolls() {
        AppLog.d(TAG, "Testing computeRollingRingDiff across multi-turn positive and negative offsets")
        for (itemCount in listOf(2, 3, 4, 7)) {
            val ringSize = computeRollingRingSize(itemCount = itemCount, visibleItemsCount = 3)
            val tracker = RollingOffsetTracker(initialIndex = 0)
            tracker.resolveTarget(selectedIndex = 0, itemCount = itemCount)

            var current = 0
            // Roll forward 3 full turns + backward 5 full turns
            repeat(itemCount * 3) {
                current = (current + 1).floorMod(itemCount)
                val target = tracker.resolveTarget(selectedIndex = current, itemCount = itemCount)
                val centerRingSlot =
                    (0 until ringSize).single { slot ->
                        abs(computeRollingRingDiff(slot, target, ringSize)) < 0.001f
                    }
                assertEquals(current, centerRingSlot % itemCount)
                assertEquals(current, target.roundToInt().floorMod(itemCount))
            }
            repeat(itemCount * 5) {
                current = (current - 1).floorMod(itemCount)
                val target = tracker.resolveTarget(selectedIndex = current, itemCount = itemCount)
                val centerRingSlot =
                    (0 until ringSize).single { slot ->
                        abs(computeRollingRingDiff(slot, target, ringSize)) < 0.001f
                    }
                assertEquals(current, centerRingSlot % itemCount)
            }
        }
    }

    @Test
    fun testComputeTarget_isPureUntilCommitted() {
        AppLog.d(TAG, "Testing RollingOffsetTracker.computeTarget purity when composition aborts")
        val tracker = RollingOffsetTracker(initialIndex = 0)
        val initial = tracker.computeTarget(selectedIndex = 0, itemCount = 4)
        tracker.commitTarget(selectedIndex = 0, itemCount = 4, computedTarget = initial)
        assertEquals(0f, tracker.targetOffset, 0.0001f)

        // Simulate an uncommitted composition pass for selectedIndex = 3 (wraps backward to -1f)
        val uncommitted = tracker.computeTarget(selectedIndex = 3, itemCount = 4)
        assertEquals(-1f, uncommitted, 0.0001f)
        // Without commitTarget(), tracker state remains untouched so a retry computes -1f again
        assertEquals(0f, tracker.targetOffset, 0.0001f)
        assertEquals(-1f, tracker.computeTarget(selectedIndex = 3, itemCount = 4), 0.0001f)

        tracker.commitTarget(selectedIndex = 3, itemCount = 4, computedTarget = uncommitted)
        assertEquals(-1f, tracker.targetOffset, 0.0001f)
    }
}
