package com.stormpanda.megingiard.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-End integration test suite verifying gamepad-first 2D navigation,
 * modal dialog focus entrapment, and focus recovery:
 *
 * 1. Physical gamepad analog stick / hat translation and deadzone filtering via [PrimaryOverlayInputBridge].
 * 2. Accelerating key repeat cadence and bumper cycling.
 * 3. Modal lifecycle, focus suspension, and background isolation in [AppStateManager].
 * 4. Universal focus recovery signal dispatching upon unhandled inputs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GamepadNavigationFocusIsolationE2ETest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SettingsManager.init(RuntimeEnvironment.getApplication())
        PrimaryOverlayInputBridge.resetJoystickState()
        AppStateManager.closePrimaryModal()
        AppStateManager.clearSuspended()
    }

    @After
    fun tearDown() {
        PrimaryOverlayInputBridge.resetJoystickState()
        AppStateManager.closePrimaryModal()
        AppStateManager.clearSuspended()
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

    @Test
    fun testModalFocusSuspensionAndRestorationPipelineE2E() {
        // 1. Initial state: no active or suspended modal
        assertNull(AppStateManager.activePrimaryModal.value)
        assertNull(AppStateManager.suspendedPrimaryModal.value)

        // 2. Open Primary Modal (MacroPad Editor)
        val editorConfig = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR)
        AppStateManager.openPrimaryModal(editorConfig)
        assertEquals(editorConfig, AppStateManager.activePrimaryModal.value)
        assertNull(AppStateManager.suspendedPrimaryModal.value)

        // 3. User initiates Gamepad Recording -> modal must be suspended and dismissed to allow overlay interaction
        AppStateManager.suspendCurrentAndDismiss()
        assertNull("Active modal must be dismissed while recording", AppStateManager.activePrimaryModal.value)
        assertEquals("Suspended modal must preserve editor configuration", editorConfig, AppStateManager.suspendedPrimaryModal.value)

        // 4. Recording finishes -> resume suspended modal
        AppStateManager.resumeSuspended()
        assertEquals("Editor modal must be automatically restored upon completion", editorConfig, AppStateManager.activePrimaryModal.value)
        assertNull("Suspended modal reference should be cleared after restoration", AppStateManager.suspendedPrimaryModal.value)

        // 5. Close modal
        AppStateManager.closePrimaryModal()
        assertNull(AppStateManager.activePrimaryModal.value)
    }

    @Test
    fun testGamepadMotionTranslationAndAcceleratingRepeatE2E() =
        runTest(testDispatcher) {
            val keyEvents = mutableListOf<Pair<Int, Int>>()

            // 1. Deflect stick Down (axisY = 0.9f)
            val motionEventDown = createJoystickMotionEvent(axisY = 0.9f)
            val handledDown =
                PrimaryOverlayInputBridge.processGenericMotionEvent(motionEventDown) { action, keyCode ->
                    keyEvents.add(action to keyCode)
                }
            assertTrue("Expected down deflection to be handled", handledDown)
            assertEquals("Expected single initial DPAD_DOWN event", 1, keyEvents.size)
            assertEquals(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_DOWN, keyEvents[0])

            // 2. Advance time past initial repeat delay (250ms) -> repeat should fire
            advanceTimeBy(300)
            assertTrue("Expected repeat events to accumulate during hold", keyEvents.size >= 2)
            assertTrue(keyEvents.all { it.second == KeyEvent.KEYCODE_DPAD_DOWN })

            // 3. Release stick to neutral (axisY = 0.0f)
            val releaseEvent = createJoystickMotionEvent(axisY = 0.0f)
            PrimaryOverlayInputBridge.processGenericMotionEvent(releaseEvent) { action, keyCode ->
                keyEvents.add(action to keyCode)
            }
            val lastEvent = keyEvents.last()
            assertEquals(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_DOWN, lastEvent)

            // 4. Advance time further -> no more repeat events should fire after release
            val countAtRelease = keyEvents.size
            advanceTimeBy(500)
            assertEquals("No events should be emitted after neutral release", countAtRelease, keyEvents.size)
        }

    @Test
    fun testBumperNavigationCycleAndFocusRecoveryE2E() =
        runTest(testDispatcher) {
            val categories = listOf("QUICK_ACTIONS", "PROFILES", "LAYOUTS", "BUTTONS", "MACROS")
            var currentCategory = "QUICK_ACTIONS"

            val receivedBumperEvents = mutableListOf<BumperDirection>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.bumperEvents.collect {
                    receivedBumperEvents.add(it)
                }
            }

            val receivedRecoveryEvents = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                PrimaryOverlayInputBridge.focusRecoveryEvents.collect {
                    receivedRecoveryEvents.add(it)
                }
            }

            // 1. Press R1 (Bumper NEXT)
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
            currentCategory = categories.cycle(currentCategory, BumperDirection.NEXT)
            assertEquals("PROFILES", currentCategory)

            // 2. Press R1 again -> LAYOUTS
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
            currentCategory = categories.cycle(currentCategory, BumperDirection.NEXT)
            assertEquals("LAYOUTS", currentCategory)

            // 3. Press L1 (Bumper PREV) -> PROFILES
            PrimaryOverlayInputBridge.sendBumper(BumperDirection.PREV)
            currentCategory = categories.cycle(currentCategory, BumperDirection.PREV)
            assertEquals("PROFILES", currentCategory)

            assertEquals(listOf(BumperDirection.NEXT, BumperDirection.NEXT, BumperDirection.PREV), receivedBumperEvents)

            // 4. Trigger focus recovery (e.g. touch unseated focus node)
            PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals(1, receivedRecoveryEvents.size)
            assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, receivedRecoveryEvents[0])
        }
}
