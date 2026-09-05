package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.keyboard.KeyCodeInjector
import com.stormpanda.megingiard.keyboard.KeyDef
import com.stormpanda.megingiard.keyboard.KeyRepeatController
import com.stormpanda.megingiard.keyboard.KeyboardGestureProcessor
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-End integration test suite verifying injector lifecycle safety and backend routing:
 *
 * 1. Multi-client reference counting and non-interfering start/stop lifecycle in [TouchInjector].
 * 2. Dynamic Privd daemon connection state transitions and automatic backend failover in [InjectorBackendRouter].
 * 3. Multi-finger gesture processing and touch-cancellation safety in [TouchpadGestureProcessor].
 * 4. Key repeat dispatch and gesture state cancellation safety in [KeyboardGestureProcessor] & [KeyRepeatController].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InjectorLifecycleSafetyE2ETest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        PrivdClient.setStateForTesting(PrivdConnectionState.DISCONNECTED)
    }

    @After
    fun tearDown() {
        TouchInjector.stop("touchpad_client")
        TouchInjector.stop("mirror_client")
        TouchInjector.stop("custom_client")
        PrivdClient.setStateForTesting(PrivdConnectionState.DISCONNECTED)
        Dispatchers.resetMain()
    }

    @Test
    fun testTouchInjectorMultiClientReferenceCounting() {
        PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)

        // 1. Initial state: inactive
        assertFalse("Expected TouchInjector inactive initially", TouchInjector.isRunning)

        // 2. Start client A ("touchpad_client")
        TouchInjector.start(context, "touchpad_client")
        assertTrue("Expected TouchInjector running for client A", TouchInjector.isRunning)

        // 3. Start client B ("mirror_client")
        TouchInjector.start(context, "mirror_client")
        assertTrue("Expected TouchInjector running for clients A & B", TouchInjector.isRunning)

        // 4. Stop client A — client B is still active, so TouchInjector must NOT stop
        TouchInjector.stop("touchpad_client")
        assertTrue("Expected TouchInjector to remain running while client B is active", TouchInjector.isRunning)

        // 5. Redundant stop for non-existent or already-stopped client should be a safe no-op
        TouchInjector.stop("touchpad_client")
        TouchInjector.stop("non_existent_client")
        assertTrue("Expected TouchInjector to remain running after redundant stop", TouchInjector.isRunning)

        // 6. Stop client B — all clients released, TouchInjector must now be stopped
        TouchInjector.stop("mirror_client")
        assertFalse("Expected TouchInjector stopped when all clients have released", TouchInjector.isRunning)
    }

    @Test
    fun testInjectorBackendRouterLifecycleAndDynamicReconnect() =
        runTest(testDispatcher) {
            var privdConnectedCount = 0
            var privdDisconnectedCount = 0

            val router =
                InjectorBackendRouter(
                    tag = "E2ETestRouter",
                    dispatcher = testDispatcher,
                    onPrivdConnected = { privdConnectedCount++ },
                    onPrivdDisconnected = { privdDisconnectedCount++ },
                )

            // 1. Resolve backend while disconnected -> returns false (fallback backend)
            val isPrivdInitial = router.resolveBackend()
            assertFalse("Expected fallback backend when Privd is disconnected", isPrivdInitial)
            assertFalse(router.isPrivd)

            // 2. Daemon connects -> router switches to PRIVD backend and invokes callback
            PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)
            testScheduler.advanceUntilIdle()

            assertTrue("Expected router.isPrivd == true after daemon connected", router.isPrivd)
            assertEquals("Expected onPrivdConnected callback triggered once", 1, privdConnectedCount)

            // 3. Daemon disconnects -> router switches to fallback backend and invokes callback
            PrivdClient.setStateForTesting(PrivdConnectionState.DISCONNECTED)
            testScheduler.advanceUntilIdle()

            assertFalse("Expected router.isPrivd == false after daemon disconnected", router.isPrivd)
            assertEquals("Expected onPrivdDisconnected callback triggered once", 1, privdDisconnectedCount)

            // 4. Mark stopped -> subsequent connection changes should not trigger active callbacks
            router.markStopped()
            PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)
            testScheduler.advanceUntilIdle()

            assertEquals("Expected no additional callback after markStopped", 1, privdConnectedCount)
        }

    @Test
    fun testTouchpadGestureProcessorCancellationSafety() =
        runTest(testDispatcher) {
            val processor =
                TouchpadGestureProcessor(
                    useMouse = { true },
                    scope = this,
                    sensitivity = { 1.5f },
                    twoFingerScrollEnabled = { true },
                    naturalScrollEnabled = { true },
                    scrollSpeed = { 1.0f },
                    tapToClick = { true },
                    twoFingerTap = { true },
                    threeFingerTap = { true },
                    tapDrag = { true },
                )

            // 1. Dispatch 2-finger touch down
            processor.onPress(
                pointerId = 0L,
                x = 100f,
                y = 100f,
                surfaceW = 1000f,
                surfaceH = 1000f,
                overlayOpen = false,
            )
            processor.onPress(
                pointerId = 1L,
                x = 120f,
                y = 100f,
                surfaceW = 1000f,
                surfaceH = 1000f,
                overlayOpen = false,
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
                overlayOpen = false,
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

            val repeatController =
                KeyRepeatController(
                    scope = this,
                )

            val processor =
                KeyboardGestureProcessor(
                    controller = repeatController,
                    scope = this,
                    kbRepeatEnabled = { true },
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
            processor.onRelease(1L, testGrid)

            // 4. Slide on space bar
            processor.onPress(2L, 75f, 75f, testGrid, isFullLayout = false)
            processor.onMove(2L, 50f, 75f, -25f, 0f, testGrid, isFullLayout = false)
            processor.onMove(2L, 30f, 75f, -20f, 0f, testGrid, isFullLayout = false)

            // Verify left arrow injection on drag
            assertTrue("Expected cursor left arrow key injected on space slide", calls.any { it.second == LinuxKeycodes.KEY_LEFT })

            // 5. Cancel all cleans up all ongoing repeat coroutines and popup states
            processor.onCancel(testGrid)
            assertNull(processor.activePopupState.value)
            assertFalse(processor.isSpaceDragging)
        }
}
