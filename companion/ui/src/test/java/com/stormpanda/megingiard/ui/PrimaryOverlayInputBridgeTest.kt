package com.stormpanda.megingiard.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
    @Before
    fun setUp() {
        PrimaryOverlayInputBridge.resetJoystickState()
    }

    private fun createJoystickMotionEvent(
        axisX: Float = 0f,
        axisY: Float = 0f,
        hatX: Float = 0f,
        hatY: Float = 0f,
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

        var keyDispatched = 0
        val handled = PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { keyDispatched = it }

        assertFalse(handled)
        assertEquals(0, keyDispatched)
    }

    @Test
    fun testProcessGenericMotionEvent_deadzoneFiltering() {
        val motionEvent = createJoystickMotionEvent(axisX = 0.2f, axisY = -0.3f)

        var keyDispatched = 0
        val handled = PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { keyDispatched = it }

        assertFalse(handled)
        assertEquals(0, keyDispatched)
    }

    @Test
    fun testProcessGenericMotionEvent_analogStickDirections() {
        // UP
        var motionEvent = createJoystickMotionEvent(axisY = -0.8f)
        var keyDispatched = 0
        var handled = PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { keyDispatched = it }
        assertTrue(handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, keyDispatched)

        // DOWN
        motionEvent = createJoystickMotionEvent(axisY = 0.8f)
        handled = PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { keyDispatched = it }
        assertTrue(handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, keyDispatched)

        // LEFT
        motionEvent = createJoystickMotionEvent(axisX = -0.8f)
        handled = PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { keyDispatched = it }
        assertTrue(handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, keyDispatched)

        // RIGHT
        motionEvent = createJoystickMotionEvent(axisX = 0.8f)
        handled = PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { keyDispatched = it }
        assertTrue(handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, keyDispatched)
    }

    @Test
    fun testProcessGenericMotionEvent_hatSwitchDirections() {
        val motionEvent = createJoystickMotionEvent(hatX = 1f)

        var keyDispatched = 0
        val handled = PrimaryOverlayInputBridge.processGenericMotionEvent(motionEvent) { keyDispatched = it }
        assertTrue(handled)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, keyDispatched)
    }
}
