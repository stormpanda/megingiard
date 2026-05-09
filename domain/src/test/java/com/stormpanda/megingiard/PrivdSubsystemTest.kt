package com.stormpanda.megingiard

import com.stormpanda.megingiard.macropad.GamepadKeycodes
import com.stormpanda.megingiard.macropad.JoystickStick
import com.stormpanda.megingiard.macropad.MacroStep
import com.stormpanda.megingiard.macropad.PhysicalGamepadRecordingManager
import com.stormpanda.megingiard.privd.BootstrapStage
import com.stormpanda.megingiard.privd.EvdevEvent
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdFeature
import com.stormpanda.megingiard.privd.PrivdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EV_KEY = 1
private const val EV_ABS = 3
private const val ABS_HAT0X = 16

/**
 * Sanity tests for the Privileged Mode subsystem.
 *
 * The runtime classes (`PrivdClient`, `PrivdManager`, `PrivdBootstrapper`)
 * depend on `android.net.LocalSocket`, `android.util.Log`, and the
 * `libadb-android` library, which are not available in the local JVM test
 * runtime and would require Robolectric. These tests therefore cover only
 * the pure-Kotlin surfaces — enum stability and feature-flag identity.
 * The full pair / push / spawn path is exercised by manual on-device
 * verification through the in-app setup wizard.
 */
class PrivdSubsystemTest {

    @Test
    fun `PrivdConnectionState enum has stable shape`() {
        assertEquals(3, PrivdConnectionState.entries.size)
        assertNotNull(PrivdConnectionState.valueOf("DISCONNECTED"))
        assertNotNull(PrivdConnectionState.valueOf("CONNECTING"))
        assertNotNull(PrivdConnectionState.valueOf("CONNECTED"))
    }

    @Test
    fun `PrivdState enum has stable shape`() {
        assertEquals(5, PrivdState.entries.size)
        assertNotNull(PrivdState.valueOf("OFF"))
        assertNotNull(PrivdState.valueOf("BOOTSTRAPPING"))
        assertNotNull(PrivdState.valueOf("CONNECTING"))
        assertNotNull(PrivdState.valueOf("RUNNING"))
        assertNotNull(PrivdState.valueOf("FAILED"))
    }

    @Test
    fun `PrivdFeature enum lists known features`() {
        assertNotNull(PrivdFeature.valueOf("GAMEPAD_MERGE"))
        assertNotNull(PrivdFeature.valueOf("GAMEPAD_RECORDING"))
        assertEquals(2, PrivdFeature.entries.size)
    }

    @Test
    fun `physical gamepad recording converts button events into tap steps`() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 1_000L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_KEY, GamepadKeycodes.BTN_SOUTH, 1),
            nowElapsedMs = 1_010L,
        )
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_KEY, GamepadKeycodes.BTN_SOUTH, 0),
            nowElapsedMs = 1_050L,
        )

        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 1_060L)

        assertEquals(1, steps.size)
        val step = steps.single() as MacroStep.GamepadButtonTap
        assertEquals(0L, step.startTimeMs)
        assertEquals(40L, step.durationMs)
        assertEquals(GamepadKeycodes.BTN_SOUTH, step.btnCode)
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `physical gamepad recording converts hat events into dpad steps`() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 2_000L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, ABS_HAT0X, 1),
            nowElapsedMs = 2_020L,
        )
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, ABS_HAT0X, 0),
            nowElapsedMs = 2_090L,
        )

        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 2_100L)

        assertEquals(1, steps.size)
        val step = steps.single() as MacroStep.DPadTap
        assertEquals(0L, step.startTimeMs)
        assertEquals(70L, step.durationMs)
        assertEquals(1, step.dirX)
        assertEquals(0, step.dirY)
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `physical gamepad recording converts analog events into joystick path steps`() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 3_000L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_X, 16_384),
            nowElapsedMs = 3_010L,
        )
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_Y, 16_384),
            nowElapsedMs = 3_040L,
        )
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_X, 0),
            nowElapsedMs = 3_080L,
        )
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_Y, 0),
            nowElapsedMs = 3_100L,
        )

        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 3_120L)

        assertEquals(1, steps.size)
        val step = steps.single() as MacroStep.JoystickPath
        assertEquals(0L, step.startTimeMs)
        assertEquals(JoystickStick.LEFT, step.stick)
        assertTrue(step.samples.isNotEmpty())
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `PrivdError enum covers all bootstrap failure modes`() {
        assertEquals(6, PrivdError.entries.size)
        assertNotNull(PrivdError.valueOf("DAEMON_UNREACHABLE"))
        assertNotNull(PrivdError.valueOf("PAIRING_FAILED"))
        assertNotNull(PrivdError.valueOf("ADB_DISCOVERY_FAILED"))
        assertNotNull(PrivdError.valueOf("ADB_CONNECT_FAILED"))
        assertNotNull(PrivdError.valueOf("BOOTSTRAP_PUSH_FAILED"))
        assertNotNull(PrivdError.valueOf("BOOTSTRAP_SPAWN_FAILED"))
    }

    @Test
    fun `BootstrapStage enum has stable shape`() {
        assertEquals(7, BootstrapStage.entries.size)
        assertNotNull(BootstrapStage.valueOf("IDLE"))
        assertNotNull(BootstrapStage.valueOf("PAIRING"))
        assertNotNull(BootstrapStage.valueOf("CONNECTING_ADB"))
        assertNotNull(BootstrapStage.valueOf("PUSHING_BINARY"))
        assertNotNull(BootstrapStage.valueOf("SPAWNING_DAEMON"))
        assertNotNull(BootstrapStage.valueOf("VERIFYING"))
        assertNotNull(BootstrapStage.valueOf("DONE"))
    }

    // ── PhysicalGamepadRecordingManager — additional recording logic ──────────

    @Test
    fun `empty recording produces no steps`() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 0L)
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 500L)
        assertTrue(steps.isEmpty())
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `leading idle is trimmed so first step startTimeMs is 0`() {
        // Recording starts at 5000ms; the button press arrives 200ms later.
        // After trimLeadingIdle() the step must start at 0.
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 5_000L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_KEY, GamepadKeycodes.BTN_SOUTH, 1),
            nowElapsedMs = 5_200L,
        )
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_KEY, GamepadKeycodes.BTN_SOUTH, 0),
            nowElapsedMs = 5_300L,
        )
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 5_400L)
        assertEquals(1, steps.size)
        assertEquals(0L, steps.first().startTimeMs)
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `closed joystick path durationMs is strictly greater than last sample offsetMs`() {
        // R3 regression guard: recorder sets durationMs = lastSampleOffsetMs + 1
        // so that no sample can land at the same timestamp as the end-of-step neutral reset.
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 0L)
        // Push left stick above deadzone (16384 / 32767 ≈ 0.5 > 0.15 default deadzone)
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_X, 16_384),
            nowElapsedMs = 10L,
        )
        // Return to neutral — closes the gesture normally
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_X, 0),
            nowElapsedMs = 100L,
        )
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 200L)
        val path = steps.filterIsInstance<MacroStep.JoystickPath>().first()
        val lastOffset = path.samples.maxOf { it.offsetMs }
        assertTrue(
            "durationMs (${path.durationMs}) must be strictly greater than last sample offset ($lastOffset)",
            path.durationMs > lastOffset,
        )
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `right stick ABS_Z events produce a JoystickPath with RIGHT stick`() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 0L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_Z, 16_384),
            nowElapsedMs = 10L,
        )
        // Return to neutral — closes gesture
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_Z, 0),
            nowElapsedMs = 80L,
        )
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 100L)
        val path = steps.filterIsInstance<MacroStep.JoystickPath>().first()
        assertEquals(JoystickStick.RIGHT, path.stick)
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `stick still deflected at stop time is force-closed and emitted as JoystickPath`() {
        // The stick goes above deadzone but never returns to neutral before finishRecording.
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 0L)
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_X, 16_384),
            nowElapsedMs = 10L,
        )
        // No neutral event — finishRecording force-closes the open gesture.
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 200L)
        val path = steps.filterIsInstance<MacroStep.JoystickPath>().singleOrNull()
        assertNotNull("Expected a force-closed JoystickPath to be emitted", path)
        PhysicalGamepadRecordingManager.resetState()
    }

    @Test
    fun `concurrent button press and stick gesture produce two distinct steps`() {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = 0L)
        // Button DOWN
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_KEY, GamepadKeycodes.BTN_SOUTH, 1),
            nowElapsedMs = 10L,
        )
        // Stick deflects while button is still held
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_X, 16_384),
            nowElapsedMs = 20L,
        )
        // Stick returns to neutral
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_ABS, GamepadKeycodes.ABS_X, 0),
            nowElapsedMs = 80L,
        )
        // Button UP
        PhysicalGamepadRecordingManager.recordEvdevEvent(
            event = EvdevEvent(EV_KEY, GamepadKeycodes.BTN_SOUTH, 0),
            nowElapsedMs = 100L,
        )
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = 110L)
        assertEquals(2, steps.size)
        assertTrue(steps.any { it is MacroStep.GamepadButtonTap })
        assertTrue(steps.any { it is MacroStep.JoystickPath })
        PhysicalGamepadRecordingManager.resetState()
    }
}
