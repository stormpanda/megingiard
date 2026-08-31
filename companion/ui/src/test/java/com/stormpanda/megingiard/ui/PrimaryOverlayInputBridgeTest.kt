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
        val pointerProperties = arrayOf(MotionEvent.PointerProperties().apply { id = 0 })
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
        return MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 1, pointerProperties, pointerCoords, 0, 0, 1f, 1f, 0, 0, source, 0)
    }

    private fun processEvent(event: MotionEvent): Pair<Boolean, List<Pair<Int, Int>>> {
        val events = mutableListOf<Pair<Int, Int>>()
        val handled =
            PrimaryOverlayInputBridge.processGenericMotionEvent(event) { action, key ->
                events.add(action to key)
            }
        return handled to events
    }

    @Test
    fun testBumperPrevEventEmission() =
        runTest {
            var received: BumperDirection? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.bumperEvents.collect { received = it }
            }
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.PREV)
            assertEquals(BumperDirection.PREV, received)
        }

    @Test
    fun testBumperNextEventEmission() =
        runTest {
            var received: BumperDirection? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.bumperEvents.collect { received = it }
            }
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
            assertEquals(BumperDirection.NEXT, received)
        }

    @Test
    fun testFocusRecoveryEventEmission() =
        runTest {
            var receivedKey: Int? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.focusRecoveryEvents.collect { receivedKey = it }
            }
            PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, receivedKey)
        }

    @Test
    fun testProcessGenericMotionEvent_nonJoystickSource_returnsFalse() {
        val (handled, events) = processEvent(createJoystickMotionEvent(axisX = 0.8f, source = InputDevice.SOURCE_TOUCHSCREEN))
        assertFalse(handled)
        assertTrue(events.isEmpty())
    }

    @Test
    fun testProcessGenericMotionEvent_deadzoneFiltering() {
        val (handled, events) = processEvent(createJoystickMotionEvent(axisX = 0.2f, axisY = -0.3f))
        assertFalse(handled)
        assertTrue(events.isEmpty())
    }

    @Test
    fun testProcessGenericMotionEvent_analogStickDirections() {
        val (handledUp, eventsUp) = processEvent(createJoystickMotionEvent(axisY = -0.8f))
        assertTrue(handledUp)
        assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_UP), eventsUp)

        val (handledDown, eventsDown) = processEvent(createJoystickMotionEvent(axisY = 0.8f))
        assertTrue(handledDown)
        assertEquals(listOf(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_DOWN), eventsDown)

        val (handledRelease, eventsRelease) = processEvent(createJoystickMotionEvent(axisY = 0f))
        assertTrue(handledRelease)
        assertEquals(listOf(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_DOWN), eventsRelease)
    }

    @Test
    fun testProcessGenericMotionEvent_hatSwitchDirections() {
        val (handled, events) = processEvent(createJoystickMotionEvent(hatX = 1f))
        assertTrue(handled)
        assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_RIGHT), events)
    }

    @Test
    fun testProcessGenericMotionEvent_continuousHoldingRepeat() =
        runTest {
            val downEvents = mutableListOf<Int>()
            val handled =
                PrimaryOverlayInputBridge.processGenericMotionEvent(createJoystickMotionEvent(axisY = 0.8f)) { action, key ->
                    if (action == KeyEvent.ACTION_DOWN) downEvents.add(key)
                }
            assertTrue(handled)
            assertEquals(1, downEvents.size)

            testScheduler.advanceTimeBy(400)
            assertTrue(downEvents.size >= 2)

            PrimaryOverlayInputBridge.processGenericMotionEvent(createJoystickMotionEvent(axisY = 0f)) { _, _ -> }
            val countAfterRelease = downEvents.size
            testScheduler.advanceTimeBy(500)
            assertEquals(countAfterRelease, downEvents.size)
        }

    @Test
    fun testProcessGenericMotionEvent_analogL2Trigger() =
        runTest {
            val (handledPress, eventsPress) = processEvent(createJoystickMotionEvent(axisBrake = 0.8f))
            assertTrue(handledPress)
            assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_L2), eventsPress)

            val (handledRelease, eventsRelease) = processEvent(createJoystickMotionEvent(axisBrake = 0.0f))
            assertFalse(handledRelease)
            assertEquals(listOf(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_L2), eventsRelease)
        }

    @Test
    fun testListCycleWithBumperDirection() {
        val items = listOf("A", "B", "C")
        assertEquals("B", items.cycle("A", BumperDirection.NEXT))
        assertEquals("C", items.cycle("B", BumperDirection.NEXT))
        assertEquals("A", items.cycle("C", BumperDirection.NEXT))
        assertEquals("C", items.cycle("A", BumperDirection.PREV))
        assertEquals("B", items.cycle("C", BumperDirection.PREV))
        assertEquals("A", items.cycle("B", BumperDirection.PREV))
    }
}
