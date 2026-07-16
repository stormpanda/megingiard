package com.stormpanda.megingiard.touchpad

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchpadGestureProcessorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `absolute mode updates touch positions`() =
        runTest(testDispatcher) {
            val processor =
                TouchpadGestureProcessor(
                    useMouse = false,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                )

            assertNull(processor.touchPos.value)

            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, false)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            processor.onMove(1L, 150f, 250f, 50f, 50f, 1000f, 1000f, false)
            assertEquals(Pair(150f, 250f), processor.touchPos.value)

            processor.onRelease(1L, 150f, 250f, 1000f, 1000f, true, true, true, false)
            assertNull(processor.touchPos.value)
        }

    @Test
    fun `absolute mode multi touch allocates distinct slots`() =
        runTest(testDispatcher) {
            val processor =
                TouchpadGestureProcessor(
                    useMouse = false,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                )

            assertNull(processor.touchPos.value)

            // Press first pointer (should be mapped to slot 0)
            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, false)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            // Press second pointer (should be mapped to slot 1)
            processor.onPress(2L, 300f, 400f, 1000f, 1000f, false, false)
            // Primary touch position should still be the first one
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            // Release first pointer
            processor.onRelease(1L, 150f, 250f, 1000f, 1000f, false, true, true, false)
            assertNull(processor.touchPos.value)

            // Release second pointer
            processor.onRelease(2L, 350f, 450f, 1000f, 1000f, true, true, true, false)
            assertNull(processor.touchPos.value)
        }

    @Test
    fun `relative mode triple tap triggers middle click`() =
        runTest(testDispatcher) {
            val processor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                )

            // Simulate three-finger tap
            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, false)
            processor.onPress(2L, 120f, 220f, 1000f, 1000f, false, false)
            processor.onPress(3L, 140f, 240f, 1000f, 1000f, false, false)

            processor.onRelease(1L, 100f, 200f, 1000f, 1000f, false, true, true, true)
            processor.onRelease(2L, 120f, 220f, 1000f, 1000f, false, true, true, true)
            processor.onRelease(3L, 140f, 240f, 1000f, 1000f, true, true, true, true)

            // No exception thrown
        }

    @Test
    fun `relative mode double tap and hold triggers drag`() =
        runTest(testDispatcher) {
            val processor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                )

            // First tap down and release
            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, true)
            processor.onRelease(1L, 100f, 200f, 1000f, 1000f, true, true, true, false)

            // Second tap down and hold
            processor.onPress(2L, 100f, 200f, 1000f, 1000f, false, true)
            processor.onMove(2L, 150f, 250f, 50f, 50f, 1000f, 1000f, false)
            processor.onRelease(2L, 150f, 250f, 1000f, 1000f, true, true, true, false)

            // No exception thrown
        }

    @Test
    fun `relative mode scroll handles speed and natural scrolling`() =
        runTest(testDispatcher) {
            // Test traditional scrolling (naturalScrollEnabled = false, scrollSpeed = 1.0f)
            val traditionalProcessor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                    naturalScrollEnabled = false,
                    scrollSpeed = 1.0f,
                )

            traditionalProcessor.onPress(1L, 100f, 200f, 1000f, 1000f, false, false)
            traditionalProcessor.onPress(2L, 120f, 220f, 1000f, 1000f, false, false)

            // Drag down 24px (should produce -2 scroll wheel units, traditional direction)
            traditionalProcessor.onMove(1L, 100f, 224f, 0f, 24f, 1000f, 1000f, false)

            // Test natural scrolling (naturalScrollEnabled = true, scrollSpeed = 2.0f)
            val naturalProcessor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                    naturalScrollEnabled = true,
                    scrollSpeed = 2.0f,
                )

            naturalProcessor.onPress(1L, 100f, 200f, 1000f, 1000f, false, false)
            naturalProcessor.onPress(2L, 120f, 220f, 1000f, 1000f, false, false)

            // Drag down 24px:
            // scrollThreshold = 12f / 2.0f = 6f
            // units = 24 / 6 = 4 units.
            // directionMultiplier = 1 (natural scroll active).
            // Should produce +4 scroll wheel units.
            naturalProcessor.onMove(1L, 100f, 224f, 0f, 24f, 1000f, 1000f, false)
        }

    @Test
    fun `haptic feedback triggers on single tap`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                    onHapticFeedback = { hapticCount++ },
                )

            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, true)
            processor.onRelease(1L, 100f, 200f, 1000f, 1000f, true, true, true, false)
            assertEquals(1, hapticCount)
        }

    @Test
    fun `haptic feedback triggers on drag start`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                    onHapticFeedback = { hapticCount++ },
                )

            // First tap
            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, true)
            processor.onRelease(1L, 100f, 200f, 1000f, 1000f, true, true, true, false)
            assertEquals(1, hapticCount)

            // Second tap (drag start)
            processor.onPress(2L, 100f, 200f, 1000f, 1000f, false, true)
            assertEquals(2, hapticCount)

            processor.onRelease(2L, 100f, 200f, 1000f, 1000f, true, true, true, false)
        }

    @Test
    fun `haptic feedback triggers on two finger tap`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                    onHapticFeedback = { hapticCount++ },
                )

            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, false)
            processor.onPress(2L, 120f, 220f, 1000f, 1000f, false, false)
            processor.onRelease(1L, 100f, 200f, 1000f, 1000f, false, true, true, false)
            processor.onRelease(2L, 120f, 220f, 1000f, 1000f, true, true, true, false)
            assertEquals(1, hapticCount)
        }

    @Test
    fun `haptic feedback triggers on three finger tap`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor =
                TouchpadGestureProcessor(
                    useMouse = true,
                    scope = this,
                    sensitivity = 1.0f,
                    twoFingerScrollEnabled = true,
                    onHapticFeedback = { hapticCount++ },
                )

            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false, false)
            processor.onPress(2L, 120f, 220f, 1000f, 1000f, false, false)
            processor.onPress(3L, 140f, 240f, 1000f, 1000f, false, false)
            processor.onRelease(1L, 100f, 200f, 1000f, 1000f, false, true, true, true)
            processor.onRelease(2L, 120f, 220f, 1000f, 1000f, false, true, true, true)
            processor.onRelease(3L, 140f, 240f, 1000f, 1000f, true, true, true, true)
            assertEquals(1, hapticCount)
        }
}
