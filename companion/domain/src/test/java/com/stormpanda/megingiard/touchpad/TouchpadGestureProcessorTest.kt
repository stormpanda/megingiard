package com.stormpanda.megingiard.touchpad

import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchpadGestureProcessorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)
        PrivdClient.clearQueueForTest()
    }

    @After
    fun tearDown() {
        PrivdClient.clearQueueForTest()
        PrivdClient.setStateForTesting(null)
    }

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
        scope = this,
        config = {
            TouchpadGestureConfig(
                useMouse = useMouse(),
                sensitivity = 1.0f,
                twoFingerScrollEnabled = true,
                naturalScrollEnabled = naturalScrollEnabled,
                scrollSpeed = scrollSpeed,
                tapToClick = tapToClick,
                twoFingerTap = twoFingerTap,
                threeFingerTap = threeFingerTap,
                tapDrag = tapDrag,
            )
        },
        onHapticFeedback = onHapticFeedback,
    )

    private fun TouchpadGestureProcessor.press(
        id: Long,
        x: Float,
        y: Float,
    ) = onPress(id, x, y, 1000f, 1000f)

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
    fun `absolute mode dispatches touch commands`() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            assertEquals(false, processor.hasActivePointers.value)

            processor.press(1L, 100f, 200f)
            assertEquals(true, processor.hasActivePointers.value)
            assertEquals("D 0 864 192\n", PrivdClient.pollCommandForTest())

            processor.move(1L, 150f, 250f, 50f, 50f)
            assertEquals("M 0 810 288\n", PrivdClient.pollCommandForTest())

            processor.release(1L, 150f, 250f)
            assertEquals(false, processor.hasActivePointers.value)
            assertEquals("U 0\n", PrivdClient.pollCommandForTest())
        }

    @Test
    fun `absolute mode multi touch allocates distinct slots`() =
        runTest(testDispatcher) {
            val processor = createProcessor()

            processor.press(1L, 100f, 200f)
            assertEquals(true, processor.hasActivePointers.value)
            assertEquals("D 0 864 192\n", PrivdClient.pollCommandForTest())

            processor.press(2L, 300f, 400f)
            assertEquals(true, processor.hasActivePointers.value)
            assertEquals("D 1 648 576\n", PrivdClient.pollCommandForTest())

            processor.release(1L, 150f, 250f)
            assertEquals(true, processor.hasActivePointers.value)
            assertEquals("U 0\n", PrivdClient.pollCommandForTest())

            processor.release(2L, 350f, 450f)
            assertEquals(false, processor.hasActivePointers.value)
            assertEquals("U 1\n", PrivdClient.pollCommandForTest())
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
    fun `haptic feedback does not trigger on four finger tap`() =
        runTest(testDispatcher) {
            var hapticCount = 0
            val processor = createProcessor(useMouse = { true }, threeFingerTap = true, onHapticFeedback = { hapticCount++ })

            processor.press(1L, 100f, 200f)
            processor.press(2L, 120f, 220f)
            processor.press(3L, 140f, 240f)
            processor.press(4L, 160f, 260f)
            processor.release(1L, 100f, 200f)
            processor.release(2L, 120f, 220f)
            processor.release(3L, 140f, 240f)
            processor.release(4L, 160f, 260f)
            assertEquals(0, hapticCount)
        }

    @Test
    fun `onCancel cleans absolute mode touch slots`() =
        runTest(testDispatcher) {
            val processor = createProcessor()

            processor.press(1L, 100f, 200f)
            assertEquals("D 0 864 192\n", PrivdClient.pollCommandForTest())

            processor.onCancel()
            assertEquals("U 0\n", PrivdClient.pollCommandForTest())
        }

    @Test
    fun `onCancel cleans absolute touch slots even after switching to mouse mode`() =
        runTest(testDispatcher) {
            var currentUseMouse = false
            val processor = createProcessor(useMouse = { currentUseMouse })

            processor.press(1L, 100f, 200f)
            assertEquals("D 0 864 192\n", PrivdClient.pollCommandForTest())

            currentUseMouse = true
            processor.onCancel()
            assertEquals("U 0\n", PrivdClient.pollCommandForTest())
        }

    @Test
    fun `relative mode single finger move invokes moveMouse`() =
        runTest(testDispatcher) {
            val processor = createProcessor(useMouse = { true })
            processor.press(1L, 100f, 200f)
            processor.move(1L, 110f, 210f, dx = 10f, dy = 10f)
            processor.release(1L, 110f, 210f)
        }

    @Test
    fun `onCancel during tap click window sends buttonUp`() =
        runTest(testDispatcher) {
            val processor = createProcessor(useMouse = { true }, tapToClick = true)

            processor.press(1L, 100f, 200f)
            processor.release(1L, 100f, 200f)
            assertEquals("MB L D\n", PrivdClient.pollCommandForTest())

            processor.onCancel()
            assertEquals("MB L U\n", PrivdClient.pollCommandForTest())
        }

    @Test
    fun `relative mode continues moving when first finger releases while second finger stays down`() =
        runTest(testDispatcher) {
            val processor = createProcessor(useMouse = { true })

            processor.press(1L, 100f, 200f)
            processor.press(2L, 120f, 220f)
            processor.release(1L, 100f, 200f)

            processor.move(2L, 130f, 225f, dx = 10f, dy = 5f)
            assertEquals("MM 20 10\n", PrivdClient.pollCommandForTest())

            processor.release(2L, 130f, 225f)
        }

    @Test
    fun `relative mode accumulates sub-pixel movement remainders across slow frames`() =
        runTest(testDispatcher) {
            val processor = createProcessor(useMouse = { true })

            processor.press(1L, 100f, 200f)
            // Each move adds 0.2 * 2.0 = 0.4 px; 3 frames accumulate to 1.2 px -> 1 px move
            processor.move(1L, 100.2f, 200.2f, dx = 0.2f, dy = 0.2f)
            assertEquals(null, PrivdClient.pollCommandForTest())

            processor.move(1L, 100.4f, 200.4f, dx = 0.2f, dy = 0.2f)
            assertEquals(null, PrivdClient.pollCommandForTest())

            processor.move(1L, 100.6f, 200.6f, dx = 0.2f, dy = 0.2f)
            assertEquals("MM 1 1\n", PrivdClient.pollCommandForTest())

            processor.release(1L, 100.6f, 200.6f)
        }

    @Test
    fun `lifting one finger after two-finger scroll suppresses cursor jump until all fingers lift`() =
        runTest(testDispatcher) {
            val processor = createProcessor(useMouse = { true })

            processor.press(1L, 100f, 200f)
            processor.press(2L, 120f, 220f)
            processor.move(1L, 100f, 224f, dx = 0f, dy = 24f)
            assertEquals("MW 2\n", PrivdClient.pollCommandForTest())

            // Lift finger 1 first; remaining finger 2 moves slightly while lifting -> no MM cursor jump
            processor.release(1L, 100f, 224f)
            processor.move(2L, 130f, 230f, dx = 10f, dy = 6f)
            assertEquals(null, PrivdClient.pollCommandForTest())

            processor.release(2L, 130f, 230f)

            // Subsequent single-finger gesture moves cursor normally
            processor.press(3L, 100f, 200f)
            processor.move(3L, 110f, 205f, dx = 10f, dy = 5f)
            assertEquals("MM 20 10\n", PrivdClient.pollCommandForTest())
            processor.release(3L, 110f, 205f)
        }
}
