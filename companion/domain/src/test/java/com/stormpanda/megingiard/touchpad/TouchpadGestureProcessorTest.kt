package com.stormpanda.megingiard.touchpad

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchpadGestureProcessorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun TestScope.createProcessor(
        useMouse: () -> Boolean = { false },
        tapToClick: Boolean = true,
        tapDrag: Boolean = true,
        twoFingerTap: Boolean = true,
        threeFingerTap: Boolean = true,
        naturalScrollEnabled: Boolean = true,
        scrollSpeed: Float = 1.0f,
        onHapticFeedback: () -> Unit = {},
    ) = TouchpadGestureProcessor(
        useMouse = useMouse,
        scope = this,
        sensitivity = { 1.0f },
        twoFingerScrollEnabled = { true },
        tapToClick = { tapToClick },
        tapDrag = { tapDrag },
        twoFingerTap = { twoFingerTap },
        threeFingerTap = { threeFingerTap },
        naturalScrollEnabled = { naturalScrollEnabled },
        scrollSpeed = { scrollSpeed },
        onHapticFeedback = onHapticFeedback,
    )

    private fun TouchpadGestureProcessor.press(
        id: Long,
        x: Float,
        y: Float,
        isHover: Boolean = false,
    ) = onPress(id, x, y, 1000f, 1000f, isHover)

    private fun TouchpadGestureProcessor.release(
        id: Long,
        x: Float,
        y: Float,
    ) = onRelease(id, x, y, 1000f, 1000f)

    private fun TouchpadGestureProcessor.move(
        id: Long,
        x: Float,
        y: Float,
        dx: Float = 0f,
        dy: Float = 0f,
    ) = onMove(id, x, y, dx, dy, 1000f, 1000f)

    @Test
    fun `absolute mode updates touch positions`() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            assertNull(processor.touchPos.value)

            processor.press(1L, 100f, 200f)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            processor.move(1L, 150f, 250f, 50f, 50f)
            assertEquals(Pair(150f, 250f), processor.touchPos.value)

            processor.release(1L, 150f, 250f)
            assertNull(processor.touchPos.value)
        }

    @Test
    fun `absolute mode multi touch allocates distinct slots`() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            assertNull(processor.touchPos.value)

            processor.press(1L, 100f, 200f)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            processor.press(2L, 300f, 400f)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            processor.release(1L, 150f, 250f)
            assertNull(processor.touchPos.value)

            processor.release(2L, 350f, 450f)
            assertNull(processor.touchPos.value)
        }

    @Test
    fun `relative mode triple tap triggers middle click`() =
        runTest(testDispatcher) {
            val processor = createProcessor(useMouse = { true }, threeFingerTap = true)

            processor.press(1L, 100f, 200f)
            processor.press(2L, 120f, 220f)
            processor.press(3L, 140f, 240f)

            processor.release(1L, 100f, 200f)
            processor.release(2L, 120f, 220f)
            processor.release(3L, 140f, 240f)
        }

    @Test
    fun `relative mode double tap and hold triggers drag`() =
        runTest(testDispatcher) {
            val processor = createProcessor(useMouse = { true }, tapDrag = true)

            processor.press(1L, 100f, 200f)
            processor.release(1L, 100f, 200f)

            processor.press(2L, 100f, 200f)
            processor.move(2L, 150f, 250f, 50f, 50f)
            processor.release(2L, 150f, 250f)
        }

    @Test
    fun `relative mode scroll handles speed and natural scrolling`() =
        runTest(testDispatcher) {
            val traditionalProcessor =
                createProcessor(
                    useMouse = { true },
                    naturalScrollEnabled = false,
                    scrollSpeed = 1.0f,
                )
            traditionalProcessor.press(1L, 100f, 200f)
            traditionalProcessor.press(2L, 120f, 220f)
            traditionalProcessor.move(1L, 100f, 224f, 0f, 24f)

            val naturalProcessor =
                createProcessor(
                    useMouse = { true },
                    naturalScrollEnabled = true,
                    scrollSpeed = 2.0f,
                )
            naturalProcessor.press(1L, 100f, 200f)
            naturalProcessor.press(2L, 120f, 220f)
            naturalProcessor.move(1L, 100f, 224f, 0f, 24f)
        }

    @Test
    fun `haptic feedback triggers on single tap`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor = createProcessor(useMouse = { true }, tapToClick = true, onHapticFeedback = { hapticCount++ })

            processor.press(1L, 100f, 200f)
            processor.release(1L, 100f, 200f)
            assertEquals(1, hapticCount)
        }

    @Test
    fun `haptic feedback triggers on drag start`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor = createProcessor(useMouse = { true }, tapDrag = true, onHapticFeedback = { hapticCount++ })

            processor.press(1L, 100f, 200f)
            processor.release(1L, 100f, 200f)
            assertEquals(1, hapticCount)

            processor.press(2L, 100f, 200f)
            assertEquals(2, hapticCount)

            processor.release(2L, 100f, 200f)
        }

    @Test
    fun `haptic feedback triggers on two finger tap`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor = createProcessor(useMouse = { true }, twoFingerTap = true, onHapticFeedback = { hapticCount++ })

            processor.press(1L, 100f, 200f)
            processor.press(2L, 120f, 220f)
            processor.release(1L, 100f, 200f)
            processor.release(2L, 120f, 220f)
            assertEquals(1, hapticCount)
        }

    @Test
    fun `haptic feedback triggers on three finger tap`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor = createProcessor(useMouse = { true }, threeFingerTap = true, onHapticFeedback = { hapticCount++ })

            processor.press(1L, 100f, 200f)
            processor.press(2L, 120f, 220f)
            processor.press(3L, 140f, 240f)
            processor.release(1L, 100f, 200f)
            processor.release(2L, 120f, 220f)
            processor.release(3L, 140f, 240f)
            assertEquals(1, hapticCount)
        }

    @Test
    fun `onCancel cleans absolute mode touch slots`() =
        runTest(testDispatcher) {
            val processor = createProcessor()

            processor.press(1L, 100f, 200f)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            processor.onCancel()
            assertNull(processor.touchPos.value)
        }

    @Test
    fun `onCancel cleans absolute touch slots even after switching to mouse mode`() =
        runTest(testDispatcher) {
            var currentUseMouse = false
            val processor = createProcessor(useMouse = { currentUseMouse })

            processor.press(1L, 100f, 200f)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            currentUseMouse = true
            processor.onCancel()
            assertNull(processor.touchPos.value)
        }
}
