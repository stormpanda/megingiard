package com.stormpanda.megingiard

import com.stormpanda.megingiard.macropad.GamepadKeycodes
import com.stormpanda.megingiard.macropad.JoystickStick
import com.stormpanda.megingiard.macropad.MacroStep
import com.stormpanda.megingiard.macropad.PhysicalGamepadRecordingManager
import com.stormpanda.megingiard.privd.BootstrapStage
import com.stormpanda.megingiard.privd.EvdevEvent
import com.stormpanda.megingiard.privd.PrivdAdbConnectionManager
import com.stormpanda.megingiard.privd.PrivdBootstrapper
import com.stormpanda.megingiard.privd.PrivdConnectionState
import com.stormpanda.megingiard.privd.PrivdError
import com.stormpanda.megingiard.privd.PrivdFeature
import com.stormpanda.megingiard.privd.PrivdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SSLContext

private const val EV_KEY = 1
private const val EV_ABS = 3
private const val ABS_HAT0X = 16

class PrivdSubsystemTest {
    private fun evKey(
        code: Int,
        value: Int,
    ) = EvdevEvent(EV_KEY, code, value)

    private fun evAbs(
        code: Int,
        value: Int,
    ) = EvdevEvent(EV_ABS, code, value)

    private fun recordSession(
        startMs: Long = 0L,
        stopMs: Long = 100L,
        events: List<Pair<Long, EvdevEvent>>,
    ): List<MacroStep> {
        PhysicalGamepadRecordingManager.startRecordingForTest(startElapsedMs = startMs)
        for ((now, ev) in events) {
            PhysicalGamepadRecordingManager.recordEvdevEvent(ev, now)
        }
        val steps = PhysicalGamepadRecordingManager.finishRecordingForTest(stopElapsedMs = stopMs)
        PhysicalGamepadRecordingManager.resetState()
        return steps
    }

    @Test
    fun `PrivdConnectionState enum has stable shape`() {
        assertEquals(listOf("DISCONNECTED", "CONNECTING", "CONNECTED"), PrivdConnectionState.entries.map { it.name })
    }

    @Test
    fun `PrivdState enum has stable shape`() {
        assertEquals(listOf("OFF", "BOOTSTRAPPING", "CONNECTING", "RUNNING", "FAILED"), PrivdState.entries.map { it.name })
    }

    @Test
    fun `PrivdFeature enum lists known features`() {
        assertEquals(listOf("GAMEPAD_MERGE", "GAMEPAD_RECORDING", "MIRROR"), PrivdFeature.entries.map { it.name })
    }

    @Test
    fun `PrivdError enum covers all bootstrap failure modes`() {
        val expected =
            listOf(
                "DAEMON_UNREACHABLE",
                "PAIRING_FAILED",
                "ADB_DISCOVERY_FAILED",
                "ADB_CONNECT_FAILED",
                "BOOTSTRAP_PUSH_FAILED",
                "BOOTSTRAP_SPAWN_FAILED",
                "BOOTSTRAP_PROVISION_FAILED",
                "ADB_PAIRING_REQUIRED",
                "VERSION_MISMATCH",
            )
        assertEquals(expected, PrivdError.entries.map { it.name })
    }

    @Test
    fun `BootstrapStage enum has stable shape`() {
        val expected = listOf("IDLE", "PAIRING", "CONNECTING_ADB", "PUSHING_BINARY", "SPAWNING_DAEMON", "VERIFYING", "DONE")
        assertEquals(expected, BootstrapStage.entries.map { it.name })
    }

    @Test
    fun `physical gamepad recording converts button events into tap steps`() {
        val steps =
            recordSession(
                startMs = 1_000L,
                stopMs = 1_060L,
                events = listOf(1_010L to evKey(GamepadKeycodes.BTN_SOUTH, 1), 1_050L to evKey(GamepadKeycodes.BTN_SOUTH, 0)),
            )
        val step = steps.single() as MacroStep.GamepadButtonTap
        assertEquals(0L, step.startTimeMs)
        assertEquals(40L, step.durationMs)
        assertEquals(GamepadKeycodes.BTN_SOUTH, step.btnCode)
    }

    @Test
    fun `physical gamepad recording converts hat events into dpad steps`() {
        val steps =
            recordSession(
                startMs = 2_000L,
                stopMs = 2_100L,
                events = listOf(2_020L to evAbs(ABS_HAT0X, 1), 2_090L to evAbs(ABS_HAT0X, 0)),
            )
        val step = steps.single() as MacroStep.DPadTap
        assertEquals(0L, step.startTimeMs)
        assertEquals(70L, step.durationMs)
        assertEquals(1, step.dirX)
        assertEquals(0, step.dirY)
    }

    @Test
    fun `physical gamepad recording converts analog events into joystick path steps`() {
        val steps =
            recordSession(
                startMs = 3_000L,
                stopMs = 3_120L,
                events =
                    listOf(
                        3_010L to evAbs(GamepadKeycodes.ABS_X, 16_384),
                        3_040L to evAbs(GamepadKeycodes.ABS_Y, 16_384),
                        3_080L to evAbs(GamepadKeycodes.ABS_X, 0),
                        3_100L to evAbs(GamepadKeycodes.ABS_Y, 0),
                    ),
            )
        val step = steps.single() as MacroStep.JoystickPath
        assertEquals(0L, step.startTimeMs)
        assertEquals(JoystickStick.LEFT, step.stick)
        assertTrue(step.samples.isNotEmpty())
    }

    @Test
    fun `empty recording produces no steps`() {
        assertTrue(recordSession(0L, 500L, emptyList()).isEmpty())
    }

    @Test
    fun `leading idle is trimmed so first step startTimeMs is 0`() {
        val steps =
            recordSession(
                startMs = 5_000L,
                stopMs = 5_400L,
                events = listOf(5_200L to evKey(GamepadKeycodes.BTN_SOUTH, 1), 5_300L to evKey(GamepadKeycodes.BTN_SOUTH, 0)),
            )
        assertEquals(1, steps.size)
        assertEquals(0L, steps.first().startTimeMs)
    }

    @Test
    fun `closed joystick path durationMs is strictly greater than last sample offsetMs`() {
        val steps =
            recordSession(
                startMs = 0L,
                stopMs = 200L,
                events = listOf(10L to evAbs(GamepadKeycodes.ABS_X, 16_384), 100L to evAbs(GamepadKeycodes.ABS_X, 0)),
            )
        val path = steps.filterIsInstance<MacroStep.JoystickPath>().first()
        val lastOffset = path.samples.maxOf { it.offsetMs }
        assertTrue(path.durationMs > lastOffset)
    }

    @Test
    fun `right stick ABS_Z events produce a JoystickPath with RIGHT stick`() {
        val steps =
            recordSession(
                startMs = 0L,
                stopMs = 100L,
                events = listOf(10L to evAbs(GamepadKeycodes.ABS_Z, 16_384), 80L to evAbs(GamepadKeycodes.ABS_Z, 0)),
            )
        val path = steps.filterIsInstance<MacroStep.JoystickPath>().first()
        assertEquals(JoystickStick.RIGHT, path.stick)
    }

    @Test
    fun `stick still deflected at stop time is force-closed and emitted as JoystickPath`() {
        val steps = recordSession(startMs = 0L, stopMs = 200L, events = listOf(10L to evAbs(GamepadKeycodes.ABS_X, 16_384)))
        assertNotNull(steps.filterIsInstance<MacroStep.JoystickPath>().singleOrNull())
    }

    @Test
    fun `concurrent button press and stick gesture produce two distinct steps`() {
        val steps =
            recordSession(
                startMs = 0L,
                stopMs = 110L,
                events =
                    listOf(
                        10L to evKey(GamepadKeycodes.BTN_SOUTH, 1),
                        20L to evAbs(GamepadKeycodes.ABS_X, 16_384),
                        80L to evAbs(GamepadKeycodes.ABS_X, 0),
                        100L to evKey(GamepadKeycodes.BTN_SOUTH, 0),
                    ),
            )
        assertEquals(2, steps.size)
        assertTrue(steps.any { it is MacroStep.GamepadButtonTap })
        assertTrue(steps.any { it is MacroStep.JoystickPath })
    }

    @Test
    fun `readAdbTlsConnectPort handles missing getprop command gracefully without hanging`() {
        assertTrue(PrivdBootstrapper.readAdbTlsConnectPort() >= 0)
    }

    @Test
    fun `flushLibaDBCache clears static sslContext field in SslUtils`() {
        val clazz = Class.forName("io.github.muntashirakon.adb.SslUtils")
        val field = clazz.getDeclaredField("sslContext").apply { isAccessible = true }

        val dummyContext = SSLContext.getDefault()
        field.set(null, dummyContext)
        assertEquals(dummyContext, field.get(null))

        PrivdAdbConnectionManager.flushLibaDBCache()

        assertNull(field.get(null))
    }
}
