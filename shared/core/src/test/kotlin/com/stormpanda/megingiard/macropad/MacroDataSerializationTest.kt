package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip serialization tests for [Macro] and the [MacroStep] sealed hierarchy.
 */
class MacroDataSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private inline fun <reified T> assertRoundTrip(value: T) {
        val encoded = json.encodeToString(value)
        val decoded = json.decodeFromString<T>(encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `gamepad button tap survives JSON round-trip`() {
        assertRoundTrip<MacroStep>(MacroStep.GamepadButtonTap(100L, 250L, 0x130, "A"))
    }

    @Test
    fun `joystick move survives JSON round-trip`() {
        assertRoundTrip<MacroStep>(MacroStep.JoystickMove(0L, 500L, JoystickStick.RIGHT, 0.75f, -0.25f))
    }

    @Test
    fun `dpad tap survives JSON round-trip`() {
        assertRoundTrip<MacroStep>(MacroStep.DPadTap(50L, 100L, -1, 1))
    }

    @Test
    fun `touch tap survives JSON round-trip`() {
        assertRoundTrip<MacroStep>(MacroStep.TouchTap(0L, 33L, 0.5f, 0.5f))
    }

    @Test
    fun `serial name discriminators are stable`() {
        val discriminators =
            mapOf(
                MacroStep.GamepadButtonTap(0L, 100L, 0x130, "A") to "gamepad_button_tap",
                MacroStep.JoystickMove(0L, 100L, JoystickStick.LEFT, 0f, 0f) to "joystick_move",
                MacroStep.DPadTap(0L, 100L, 0, 0) to "dpad_tap",
                MacroStep.TouchTap(0L, 100L, 0f, 0f) to "touch_tap",
                MacroStep.JoystickPath(0L, 100L, JoystickStick.RIGHT, listOf(PathSample(0L, 0.5f, 0.5f))) to "joystick_path",
            )
        for ((step, disc) in discriminators) {
            assertTrue("$disc discriminator", json.encodeToString<MacroStep>(step).contains("\"$disc\""))
        }
    }

    @Test
    fun `macro with mixed step types round-trips`() {
        val macro =
            Macro(
                id = "test-uuid-1234",
                name = "Combo",
                steps =
                    listOf(
                        MacroStep.GamepadButtonTap(0L, 80L, 0x130, "A"),
                        MacroStep.JoystickMove(50L, 200L, JoystickStick.LEFT, 1.0f, 0.0f),
                        MacroStep.DPadTap(300L, 100L, 0, 1),
                        MacroStep.TouchTap(450L, 50L, 0.5f, 0.5f),
                    ),
                loopEnabled = true,
                loopPauseMs = 250,
            )
        assertRoundTrip(macro)
    }

    @Test
    fun `endTimeMs and totalDurationMs are correct`() {
        val s1 = MacroStep.GamepadButtonTap(100L, 50L, 0x130, "A")
        assertEquals(150L, s1.endTimeMs())

        val steps =
            listOf(
                MacroStep.GamepadButtonTap(0L, 80L, 0x130, "A"),
                MacroStep.JoystickMove(50L, 200L, JoystickStick.LEFT, 0f, 0f),
                MacroStep.DPadTap(100L, 100L, 0, 1),
            )
        assertEquals(250L, steps.totalDurationMs())
    }

    @Test
    fun `joystick path survives JSON round-trip`() {
        val step: MacroStep =
            MacroStep.JoystickPath(
                startTimeMs = 0L,
                durationMs = 300L,
                stick = JoystickStick.LEFT,
                samples =
                    listOf(
                        PathSample(offsetMs = 0L, x = 0f, y = 0f),
                        PathSample(offsetMs = 100L, x = 0.5f, y = 0.25f),
                        PathSample(offsetMs = 200L, x = 1.0f, y = -0.5f),
                    ),
            )
        assertRoundTrip(step)
    }

    @Test
    fun `empty step list has zero total duration`() {
        assertEquals(0L, emptyList<MacroStep>().totalDurationMs())
    }

    @Test
    fun `unknown keys are ignored on decode`() {
        val withExtra = """{"id": "abc", "name": "X", "steps": [], "loopEnabled": false, "loopPauseMs": 0, "futureField": "ignore-me"}"""
        val decoded = json.decodeFromString<Macro>(withExtra)
        assertEquals("abc", decoded.id)
        assertEquals("X", decoded.name)
    }

    @Test
    fun `mixed macro including JoystickPath survives JSON round-trip`() {
        val macro =
            Macro(
                id = "test-uuid-path",
                name = "Full combo with path",
                steps =
                    listOf(
                        MacroStep.GamepadButtonTap(0L, 50L, 0x130, "A"),
                        MacroStep.JoystickPath(
                            startTimeMs = 60L,
                            durationMs = 201L,
                            stick = JoystickStick.LEFT,
                            samples =
                                listOf(
                                    PathSample(offsetMs = 0L, x = 0.5f, y = 0f),
                                    PathSample(offsetMs = 100L, x = 1.0f, y = 0.5f),
                                    PathSample(offsetMs = 200L, x = 0.0f, y = 0.0f),
                                ),
                        ),
                        MacroStep.DPadTap(300L, 100L, 1, 0),
                    ),
            )
        assertRoundTrip(macro)
    }

    @Test
    fun `JoystickPath endTimeMs equals startTimeMs plus durationMs`() {
        val step =
            MacroStep.JoystickPath(
                startTimeMs = 200L,
                durationMs = 350L,
                stick = JoystickStick.RIGHT,
                samples = listOf(PathSample(0L, 0f, 0f)),
            )
        assertEquals(550L, step.endTimeMs())
    }
}
