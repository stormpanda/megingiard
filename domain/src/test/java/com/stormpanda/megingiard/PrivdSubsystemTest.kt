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
}
