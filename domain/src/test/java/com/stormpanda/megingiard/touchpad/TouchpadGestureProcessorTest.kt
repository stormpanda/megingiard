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

            processor.onPress(1L, 100f, 200f, 1000f, 1000f, false)
            assertEquals(Pair(100f, 200f), processor.touchPos.value)

            processor.onMove(1L, 150f, 250f, 50f, 50f, 1000f, 1000f, false)
            assertEquals(Pair(150f, 250f), processor.touchPos.value)

            processor.onRelease(1L, 150f, 250f, 1000f, 1000f, true, true, true)
            assertNull(processor.touchPos.value)
        }
}
