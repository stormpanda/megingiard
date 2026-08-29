package com.stormpanda.megingiard.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrimaryOverlayInputBridgeTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        PrimaryOverlayInputBridge.resetJoystickState()
    }

    @After
    fun tearDown() {
        PrimaryOverlayInputBridge.resetJoystickState()
        Dispatchers.resetMain()
    }

    private fun createJoystickMotionEvent(
        axisX: Float = 0f,
        axisY: Float = 0f,
        hatX: Float = 0f,
        hatY: Float = 0f,
        axisBrake: Float = 0f,
        axisLTrigger: Float = 0f,
        source: Int = InputDevice.SOURCE_JOYSTICK,
    ): MotionEvent {
        val pointerProperties =
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_UNKNOWN
                },
            )
        val pointerCoords =
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    setAxisValue(MotionEvent.AXIS_X, axisX)
                    setAxisValue(MotionEvent.AXIS_Y, axisY)
                    setAxisValue(MotionEvent.AXIS_HAT_X, hatX)
                    setAxisValue(MotionEvent.AXIS_HAT_Y, hatY)
                    setAxisValue(MotionEvent.AXIS_BRAKE, axisBrake)
                    setAxisValue(MotionEvent.AXIS_LTRIGGER, axisLTrigger)
                },
            )
        return MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_MOVE,
            1,
            pointerProperties,
            pointerCoords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            source,
            0,
        )
    }

    @Test
    fun testBumperPrevEventEmission() =
        runTest {
            var received: BumperDirection? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.bumperEvents.collect {
                    received = it
                }
            }
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.PREV)
            assertEquals(BumperDirection.PREV, received)
        }

    @Test
    fun testBumperNextEventEmission() =
        runTest {
            var received: BumperDirection? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.bumperEvents.collect {
                    received = it
                }
            }
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
            assertEquals(BumperDirection.NEXT, received)
        }

    @Test
    fun testFocusRecoveryEventEmission() =
        runTest {
            var receivedKey: Int? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.focusRecoveryEvents.collect {
                    receivedKey = it
                }
            }
            PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, receivedKey)
        }

    @Test
    fun testProcessGenericMotionEvent_nonJoystickSource_returnsFalse() {
        val motionEvent = createJoystickMotionEvent(axisX = 0.8f, source = InputDevice.SOURCE_TOUCHSCREEN)

        var actionDispatched = -1
        var keyDispatched = 0
        val handled =
            PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, key ->
                actionDispatched = action
                keyDispatched = key
            }

        assertFalse(handled)
        assertEquals(-1, actionDispatched)
        assertEquals(0, keyDispatched)
    }

    @Test
    fun testProcessGenericMotionEvent_deadzoneFiltering() {
        val motionEvent = createJoystickMotionEvent(axisX = 0.2f, axisY = -0.3f)

        var actionDispatched = -1
        var keyDispatched = 0
        val handled =
            PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, key ->
                actionDispatched = action
                keyDispatched = key
            }

        assertFalse(handled)
        assertEquals(-1, actionDispatched)
        assertEquals(0, keyDispatched)
    }

    @Test
    fun testProcessGenericMotionEvent_analogStickDirections() {
        // UP
        var motionEvent = createJoystickMotionEvent(axisY = -0.8f)
        var actionDispatched = -1
        var keyDispatched = 0
        var handled =
            PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, key ->
                actionDispatched = action
                keyDispatched = key
            }
        assertTrue(handled)
        assertEquals(KeyEvent.ACTION_DOWN, actionDispatched)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, keyDispatched)

        // DOWN (transition UP -> DOWN fires UP release then DOWN press)
        motionEvent = createJoystickMotionEvent(axisY = 0.8f)
        val events = mutableListOf<Pair<Int, Int>>()
        handled =
            PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, key ->
                events.add(action to key)
            }
        assertTrue(handled)
        assertEquals(listOf(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_DOWN), events)

        // RELEASE TO CENTER (fires release of DOWN)
        motionEvent = createJoystickMotionEvent(axisY = 0f)
        events.clear()
        handled =
            PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, key ->
                events.add(action to key)
            }
        assertTrue(handled)
        assertEquals(listOf(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_DOWN), events)
    }

    @Test
    fun testProcessGenericMotionEvent_hatSwitchDirections() {
        val motionEvent = createJoystickMotionEvent(hatX = 1f)

        var actionDispatched = -1
        var keyDispatched = 0
        val handled =
            PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, key ->
                actionDispatched = action
                keyDispatched = key
            }
        assertTrue(handled)
        assertEquals(KeyEvent.ACTION_DOWN, actionDispatched)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, keyDispatched)
    }

    @Test
    fun testProcessGenericMotionEvent_continuousHoldingRepeat() =
        runTest {
            val motionEvent = createJoystickMotionEvent(axisY = 0.8f)
            val downEvents = mutableListOf<Int>()
            val handled =
                PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { action, key ->
                    if (action == KeyEvent.ACTION_DOWN) {
                        downEvents.add(key)
                    }
                }
            assertTrue(handled)
            assertEquals(1, downEvents.size)

            // Advance virtual time past initial delay (250ms) and first repeat tick (120ms)
            testScheduler.advanceTimeBy(400)
            assertTrue("Expected repeated ACTION_DOWN events while holding, got ${downEvents.size}", downEvents.size >= 2)

            // Release stick to center
            val releaseEvent = createJoystickMotionEvent(axisY = 0f)
            PrimaryOverlayInputBridge.processGenericMotionEvent(releaseEvent) { _, _ -> }

            val countAfterRelease = downEvents.size
            testScheduler.advanceTimeBy(500)
            assertEquals("Repeat must stop after releasing to center", countAfterRelease, downEvents.size)
        }

    @Test
    fun testProcessGenericMotionEvent_analogL2Trigger() =
        runTest {
            val events = mutableListOf<Pair<Int, Int>>()
            val pressEvent = createJoystickMotionEvent(axisBrake = 0.8f)
            val handledPress =
                PrimaryOverlayInputBridge.processGenericMotionEvent(pressEvent) { action, key ->
                    events.add(action to key)
                }
            assertTrue(handledPress)
            assertEquals(1, events.size)
            assertEquals(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_L2, events[0])

            val releaseEvent = createJoystickMotionEvent(axisBrake = 0.0f)
            val handledRelease =
                PrimaryOverlayInputBridge.processGenericMotionEvent(releaseEvent) { action, key ->
                    events.add(action to key)
                }
            assertFalse(handledRelease)
            assertEquals(2, events.size)
            assertEquals(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_L2, events[1])
        }
}
