package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.keyboard.KeyCodeInjector
import com.stormpanda.megingiard.keyboard.KeyDef
import com.stormpanda.megingiard.keyboard.KeyboardGestureProcessor
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.touchpad.TouchpadGestureConfig
import com.stormpanda.megingiard.touchpad.TouchpadGestureProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-End integration test suite verifying injector lifecycle safety:
 *
 * 1. Slot release safety in [TouchInjector].
 * 2. Multi-finger gesture processing and touch-cancellation safety in [TouchpadGestureProcessor].
 * 3. Key repeat dispatch and gesture state cancellation safety in [KeyboardGestureProcessor].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InjectorLifecycleSafetyE2ETest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        PrivdClient.setStateForTesting(PrivdConnectionState.DISCONNECTED)
        PrivdClient.clearQueueForTest()
    }

    @After
    fun tearDown() {
        PrivdClient.clearQueueForTest()
        PrivdClient.setStateForTesting(PrivdConnectionState.DISCONNECTED)
        Dispatchers.resetMain()
    }

    @Test
    fun testTouchInjectorReleaseAllSlotsSafety() {
        PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)

        TouchInjector.injectTouch(0, TouchAction.DOWN, 0.5f, 0.5f)
        TouchInjector.injectTouch(1, TouchAction.DOWN, 0.25f, 0.75f)
        PrivdClient.clearQueueForTest()

        TouchInjector.releaseAllSlots()
        assertEquals(10, PrivdClient.queueSizeForTest)
        for (slot in 0..9) {
            assertEquals("U $slot\n", PrivdClient.pollCommandForTest())
        }
    }

    @Test
    fun testTouchpadGestureProcessorCancellationSafety() =
        runTest(testDispatcher) {
            val processor =
                TouchpadGestureProcessor(
                    scope = this,
                    config = {
                        TouchpadGestureConfig(
                            useMouse = true,
                            sensitivity = 1.5f,
                        )
                    },
                )

            // 1. Dispatch 2-finger touch down
            processor.onPress(
                pointerId = 0L,
                x = 100f,
                y = 100f,
                surfaceW = 1000f,
                surfaceH = 1000f,
            )
            processor.onPress(
                pointerId = 1L,
                x = 120f,
                y = 100f,
                surfaceW = 1000f,
                surfaceH = 1000f,
            )

            // 2. Dispatch 2-finger scroll move
            processor.onMove(
                pointerId = 0L,
                x = 100f,
                y = 150f,
                deltaX = 0f,
                deltaY = 50f,
                surfaceW = 1000f,
                surfaceH = 1000f,
            )
            processor.onMove(
                pointerId = 1L,
                x = 120f,
                y = 150f,
                deltaX = 0f,
                deltaY = 50f,
                surfaceW = 1000f,
                surfaceH = 1000f,
            )

            // 3. Dispatch onCancel to simulate gesture interruption / view mode switch
            processor.onCancel()

            // 4. Re-dispatch single finger move -> should process cleanly from a neutral baseline
            processor.onPress(
                pointerId = 0L,
                x = 50f,
                y = 50f,
                surfaceW = 1000f,
                surfaceH = 1000f,
            )
            processor.onRelease(
                pointerId = 0L,
                x = 50f,
                y = 50f,
                surfaceW = 1000f,
                surfaceH = 1000f,
            )
        }

    @Test
    fun testKeyboardGestureProcessorPopupAndKeyRepeatSafety() =
        runTest(testDispatcher) {
            val calls = mutableListOf<Pair<String, Int>>()

            val testInjector =
                object : KeyCodeInjector {
                    override fun keyDown(keycode: Int) {
                        calls.add("down" to keycode)
                    }

                    override fun keyUp(keycode: Int) {
                        calls.add("up" to keycode)
                    }
                }

            val processor =
                KeyboardGestureProcessor(
                    scope = this,
                    isShiftActive = { false },
                    isCapsActive = { false },
                    isAltGrActive = { false },
                    initialDensity = 1.0f,
                    onInjectPopupSelection = { _, _ -> },
                    injector = testInjector,
                )

            val testGrid =
                listOf(
                    listOf(
                        KeyDef("q", "q", LinuxKeycodes.KEY_Q, popupOptions = listOf("q", "1", "2")),
                        KeyDef("w", "w", LinuxKeycodes.KEY_W),
                    ),
                    listOf(
                        KeyDef("space", "space", LinuxKeycodes.KEY_SPACE, widthWeight = 3f),
                    ),
                )

            processor.updateBounds("q", 0f, 0f, 50f, 50f)
            processor.updateBounds("space", 0f, 50f, 150f, 100f)

            // 1. Pointer down on key 'q'
            processor.onPress(1L, 25f, 25f, testGrid, isFullLayout = false)
            assertTrue(
                "Expected popup or press initiated for 'q'",
                processor.activePopupState.value
                    ?.keyDef
                    ?.id == "q",
            )

            // 2. Advance time to trigger long press popup
            advanceTimeBy(500)
            assertTrue("Expected long press popup state active", processor.activePopupState.value?.isLongPress == true)

            // 3. Pointer up -> releases popup
            processor.onRelease(1L)

            // 4. Slide on space bar
            processor.onPress(2L, 75f, 75f, testGrid, isFullLayout = false)
            processor.onMove(2L, 50f, 75f, -25f, 0f, testGrid, isFullLayout = false)
            processor.onMove(2L, 30f, 75f, -20f, 0f, testGrid, isFullLayout = false)

            // Verify left arrow injection on drag
            assertTrue("Expected cursor left arrow key injected on space slide", calls.any { it.second == LinuxKeycodes.KEY_LEFT })

            // 5. Cancel all cleans up all ongoing repeat coroutines and popup states
            processor.onCancel()
            assertNull(processor.activePopupState.value)
            assertFalse(processor.isSpaceDragging)
        }
}
