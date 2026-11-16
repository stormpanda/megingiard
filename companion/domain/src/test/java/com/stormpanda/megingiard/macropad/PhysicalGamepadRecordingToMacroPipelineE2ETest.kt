package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.privd.EvdevEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-End integration test suite verifying the Physical Gamepad Macro Recording,
 * Evdev event parsing, analog stick deadzone filtering, and macro compilation pipeline:
 *
 * 1. Evdev button, D-Pad hat, and multi-axis analog stick gesture recording in [PhysicalGamepadRecordingManager].
 * 2. Analog stick deadzone filtering and micro-jitter suppression.
 * 3. Open gesture auto-closure and leading idle normalization at finish.
 * 4. Cancellation safety and state cleanup.
 * 5. Full JSON serialization round-trip of recorded macro steps.
 */
class PhysicalGamepadRecordingToMacroPipelineE2ETest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun evKey(
        code: Int,
        value: Int,
    ) = EvdevEvent(1, code, value)

    private fun evAbs(
        code: Int,
        value: Int,
    ) = EvdevEvent(3, code, value)

    @Before
    fun setUp() {
        PhysicalGamepadRecordingManager.resetState()
    }

    @After
    fun tearDown() {
        PhysicalGamepadRecordingManager.cancelRecording()
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun testComplexPhysicalGamepadRecordingSequenceE2E() {
        // Start recording at t = 2000ms
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 2000L)
        assertTrue(PhysicalGamepadRecordingManager.state.value is GamepadRecordingState.Recording)

        // 1. D-Pad Down tap at t = 2500ms (held for 100ms)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(17, 1), 2500L) // ABS_HAT0Y = 1 (Down)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(17, 0), 2600L) // Neutral

        // 2. D-Pad Right tap at t = 2700ms (held for 150ms)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(16, 1), 2700L) // ABS_HAT0X = 1 (Right)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(16, 0), 2850L) // Neutral

        // 3. BTN_SOUTH (A / Jump) tap at t = 3000ms (held for 80ms)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_SOUTH, 1), 3000L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_SOUTH, 0), 3080L)

        // 4. Left Analog Stick Gesture: quarter-circle sweep (t = 3200ms .. 3600ms)
        // t = 3200ms: stick deflected right (x = 0.8f, raw = 26214)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_X, 26214), 3200L)
        // t = 3350ms: stick deflected down-right (x = 0.8f, y = 0.8f)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_Y, 26214), 3350L)
        // t = 3500ms: stick deflected down (x = 0.0f, y = 0.8f)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_X, 0), 3500L)
        // t = 3600ms: stick returned to neutral deadzone (x = 0.0f, y = 0.0f)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_Y, 0), 3600L)

        // Finish recording at t = 4000ms
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 4000L)
        assertEquals(4, steps.size)

        // Verify Leading Idle Trimming: First step starts at 0ms (2500ms - 2500ms)
        val dpadDown = steps[0] as MacroStep.DPadTap
        assertEquals(0L, dpadDown.startTimeMs)
        assertEquals(100L, dpadDown.durationMs)
        assertEquals(0, dpadDown.dirX)
        assertEquals(1, dpadDown.dirY)

        // Second step: D-Pad Right (2700ms - 2500ms = 200ms)
        val dpadRight = steps[1] as MacroStep.DPadTap
        assertEquals(200L, dpadRight.startTimeMs)
        assertEquals(150L, dpadRight.durationMs)
        assertEquals(1, dpadRight.dirX)
        assertEquals(0, dpadRight.dirY)

        // Third step: Button South (3000ms - 2500ms = 500ms)
        val buttonStep = steps[2] as MacroStep.GamepadButtonTap
        assertEquals(500L, buttonStep.startTimeMs)
        assertEquals(80L, buttonStep.durationMs)
        assertEquals(GamepadKeycodes.BTN_SOUTH, buttonStep.btnCode)

        // Fourth step: Left Stick Path (3200ms - 2500ms = 700ms)
        val stickStep = steps[3] as MacroStep.JoystickPath
        assertEquals(700L, stickStep.startTimeMs)
        assertEquals(JoystickStick.LEFT, stickStep.stick)
        assertTrue("Stick gesture should capture multiple path points", stickStep.samples.size >= 2)
        assertEquals(401L, stickStep.durationMs) // 3600 - 3200 + 1ms to ensure reset sorts after last sample

        // Serialization round-trip
        val macro = Macro(id = "macro-combo-1", name = "Quarter Circle Jump", steps = steps)
        val jsonString = json.encodeToString(macro)
        val deserialized = json.decodeFromString<Macro>(jsonString)
        assertEquals(4, deserialized.steps.size)
        assertEquals(dpadDown.startTimeMs, deserialized.steps[0].startTimeMs)
    }

    @Test
    fun testDeadzoneFilteringAndMicroJitterRejectionE2E() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 1000L)

        // Analog stick noise/jitter below 5% deadzone (|val| < 1638 / 32767 = 0.05f)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_X, 800), 1100L) // 0.024f
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_Y, -700), 1200L) // -0.021f
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_X, -500), 1300L) // -0.015f
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_X, 0), 1400L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_Y, 0), 1400L)

        // Finish recording
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 1500L)
        assertTrue("Micro-jitter within deadzone must be ignored completely", steps.isEmpty())
    }

    @Test
    fun testOpenGesturesAutoClosureOnFinishE2E() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 1000L)

        // 1. Press BTN_WEST (X) at t = 1200ms and NEVER send key up
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_WEST, 1), 1200L)

        // 2. Deflect Right Stick at t = 1400ms (x = 0.9f) and NEVER send neutral return
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_Z, 29490), 1400L)

        // 3. User finishes recording at t = 2000ms
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 2000L)
        assertEquals("Open button and stick gestures must be closed on finish", 2, steps.size)

        val buttonStep = steps[0] as MacroStep.GamepadButtonTap
        assertEquals(0L, buttonStep.startTimeMs) // Normalized from 1200
        assertEquals(800L, buttonStep.durationMs) // 2000 - 1200 = 800ms
        assertEquals(GamepadKeycodes.BTN_WEST, buttonStep.btnCode)

        val stickStep = steps[1] as MacroStep.JoystickPath
        assertEquals(200L, stickStep.startTimeMs) // Normalized (1400 - 1200 = 200)
        assertEquals(600L, stickStep.durationMs) // 2000 - 1400 = 600ms
        assertEquals(JoystickStick.RIGHT, stickStep.stick)
    }

    @Test
    fun testCancelRecordingCleansUpStateE2E() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 1000L)

        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_NORTH, 1), 1100L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_NORTH, 0), 1200L)

        // Cancel recording
        PhysicalGamepadRecordingManager.cancelRecording()
        assertEquals(GamepadRecordingState.Idle, PhysicalGamepadRecordingManager.state.value)

        // Starting a new recording should not retain old steps
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 3000L)
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 3100L)
        assertTrue("Cancelled recording must not leak steps into next session", steps.isEmpty())
    }

    @Test
    fun testAnalogOnlyTriggerRecordingEmitsTriggerPathE2E() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 1000L)

        // Firmware Mode 1 (Analog Only): EV_ABS ABS_BRAKE & ABS_GAS without EV_KEY BTN_TL2/BTN_TR2
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_BRAKE, 16384), 1100L)
        val midState = PhysicalGamepadRecordingManager.state.value as GamepadRecordingState.Recording
        assertTrue("HUD should highlight L2 at >= 50% pull", GamepadKeycodes.BTN_TL2 in midState.pressedButtons)
        assertEquals(0.5f, midState.leftTrigger, 0.01f)

        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_BRAKE, 32767), 1180L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_BRAKE, 0), 1260L)

        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 1400L)
        assertEquals(1, steps.size)
        val triggerPath = steps[0] as MacroStep.TriggerPath
        assertEquals(GamepadTrigger.LEFT, triggerPath.trigger)
        assertEquals(0L, triggerPath.startTimeMs)
        assertEquals(161L, triggerPath.durationMs)
        assertEquals(3, triggerPath.samples.size)

        val compiled = buildMacroEventList(Macro(id = "l2-macro", name = "L2", steps = steps))
        assertTrue(compiled.any { it.type == MacroEventType.JOYSTICK_SET && it.code == GamepadKeycodes.ABS_BRAKE && it.value == 32767 })
        assertTrue(compiled.any { it.type == MacroEventType.DIGITAL_BUTTON_DOWN && it.code == GamepadKeycodes.BTN_TL2 })
    }

    @Test
    fun testDualModeAnalogAndDigitalTriggerDeduplicatesIntoSingleTriggerPathE2E() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 2000L)

        // L2 in Mode 2 (normal order: EV_ABS crosses deadzone first, then EV_KEY BTN_TL2 1 at 50%)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_BRAKE, 8000), 2100L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_TL2, 1), 2130L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_BRAKE, 32767), 2160L)
        // Fast release where EV_ABS 0 arrives before EV_KEY 0
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_BRAKE, 0), 2240L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_TL2, 0), 2245L)

        // R2 in Mode 2 (reversed intra-frame order: EV_KEY BTN_TR2 1 arrives before first EV_ABS)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_TR2, 1), 2400L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_GAS, 28000), 2402L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_TR2, 0), 2500L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evAbs(GamepadKeycodes.ABS_GAS, 0), 2510L)

        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 2600L)
        assertEquals(
            "Dual analog+digital triggers must produce exactly two TriggerPath steps with no duplicate GamepadButtonTap",
            2,
            steps.size,
        )
        assertTrue(steps.all { it is MacroStep.TriggerPath })
        assertEquals(GamepadTrigger.LEFT, (steps[0] as MacroStep.TriggerPath).trigger)
        assertEquals(GamepadTrigger.RIGHT, (steps[1] as MacroStep.TriggerPath).trigger)
    }

    @Test
    fun testDigitalOnlyTriggerModeStillRecordsGamepadButtonTapE2E() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 1000L)

        // Firmware Mode 0 (Digital Only): EV_KEY BTN_TR2 without EV_ABS
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_TR2, 1), 1100L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(evKey(GamepadKeycodes.BTN_TR2, 0), 1250L)

        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 1300L)
        assertEquals(1, steps.size)
        val tap = steps[0] as MacroStep.GamepadButtonTap
        assertEquals(GamepadKeycodes.BTN_TR2, tap.btnCode)
        assertEquals(150L, tap.durationMs)
    }
}
