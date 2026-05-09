package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip serialization tests for [Macro] and the [MacroStep] sealed hierarchy.
 *
 * Exported macropad data is persisted as JSON via kotlinx.serialization. The
 * `@SerialName` discriminators on each [MacroStep] subtype are part of the
 * on-disk schema — these tests guard against accidental renames that would
 * silently invalidate user data.
 */
class MacroDataSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `gamepad button tap survives JSON round-trip`() {
        val step: MacroStep = MacroStep.GamepadButtonTap(
            startTimeMs = 100L,
            durationMs = 250L,
            btnCode = 0x130,
            label = "A",
        )
        val encoded = json.encodeToString(step)
        val decoded = json.decodeFromString<MacroStep>(encoded)
        assertEquals(step, decoded)
    }

    @Test
    fun `joystick move survives JSON round-trip`() {
        val step: MacroStep = MacroStep.JoystickMove(
            startTimeMs = 0L,
            durationMs = 500L,
            stick = JoystickStick.RIGHT,
            x = 0.75f,
            y = -0.25f,
        )
        val decoded = json.decodeFromString<MacroStep>(json.encodeToString(step))
        assertEquals(step, decoded)
    }

    @Test
    fun `dpad tap survives JSON round-trip`() {
        val step: MacroStep = MacroStep.DPadTap(
            startTimeMs = 50L,
            durationMs = 100L,
            dirX = -1,
            dirY = 1,
        )
        val decoded = json.decodeFromString<MacroStep>(json.encodeToString(step))
        assertEquals(step, decoded)
    }

    @Test
    fun `touch tap survives JSON round-trip`() {
        val step: MacroStep = MacroStep.TouchTap(
            startTimeMs = 0L,
            durationMs = 33L,
            normX = 0.5f,
            normY = 0.5f,
        )
        val decoded = json.decodeFromString<MacroStep>(json.encodeToString(step))
        assertEquals(step, decoded)
    }

    @Test
    fun `serial name discriminators are stable`() {
        // These string literals are part of the on-disk format. If any of them
        // change, existing user data becomes unreadable.
        val gamepad = json.encodeToString<MacroStep>(
            MacroStep.GamepadButtonTap(0L, 100L, 0x130, "A"),
        )
        val joystick = json.encodeToString<MacroStep>(
            MacroStep.JoystickMove(0L, 100L, JoystickStick.LEFT, 0f, 0f),
        )
        val dpad = json.encodeToString<MacroStep>(
            MacroStep.DPadTap(0L, 100L, 0, 0),
        )
        val touch = json.encodeToString<MacroStep>(
            MacroStep.TouchTap(0L, 100L, 0f, 0f),
        )
        assertTrue("gamepad_button_tap discriminator", gamepad.contains("\"gamepad_button_tap\""))
        assertTrue("joystick_move discriminator", joystick.contains("\"joystick_move\""))
        assertTrue("dpad_tap discriminator", dpad.contains("\"dpad_tap\""))
        assertTrue("touch_tap discriminator", touch.contains("\"touch_tap\""))
    }

    @Test
    fun `macro with mixed step types round-trips`() {
        val macro = Macro(
            id = "test-uuid-1234",
            name = "Combo",
            steps = listOf(
                MacroStep.GamepadButtonTap(0L, 80L, 0x130, "A"),
                MacroStep.JoystickMove(50L, 200L, JoystickStick.LEFT, 1.0f, 0.0f),
                MacroStep.DPadTap(300L, 100L, 0, 1),
                MacroStep.TouchTap(450L, 50L, 0.5f, 0.5f),
            ),
            loopEnabled = true,
            loopPauseMs = 250,
        )
        val decoded = json.decodeFromString<Macro>(json.encodeToString(macro))
        assertEquals(macro, decoded)
    }

    @Test
    fun `endTimeMs and totalDurationMs are correct`() {
        val s1 = MacroStep.GamepadButtonTap(100L, 50L, 0x130, "A")
        assertEquals(150L, s1.endTimeMs())

        val steps = listOf(
            MacroStep.GamepadButtonTap(0L, 80L, 0x130, "A"),
            MacroStep.JoystickMove(50L, 200L, JoystickStick.LEFT, 0f, 0f), // ends 250
            MacroStep.DPadTap(100L, 100L, 0, 1),                            // ends 200
        )
        assertEquals(250L, steps.totalDurationMs())
    }

    @Test
    fun `joystick path survives JSON round-trip`() {
        val step: MacroStep = MacroStep.JoystickPath(
            startTimeMs = 0L,
            durationMs  = 300L,
            stick       = JoystickStick.LEFT,
            samples     = listOf(
                PathSample(offsetMs = 0L,   x = 0f,    y = 0f),
                PathSample(offsetMs = 100L, x = 0.5f,  y = 0.25f),
                PathSample(offsetMs = 200L, x = 1.0f,  y = -0.5f),
            ),
        )
        val decoded = json.decodeFromString<MacroStep>(json.encodeToString(step))
        assertEquals(step, decoded)
    }

    @Test
    fun `joystick path serial name discriminator is stable`() {
        val json2 = json
        val step: MacroStep = MacroStep.JoystickPath(
            startTimeMs = 0L,
            durationMs  = 100L,
            stick       = JoystickStick.RIGHT,
            samples     = listOf(PathSample(0L, 0.5f, 0.5f)),
        )
        val encoded = json2.encodeToString(step)
        assertTrue("joystick_path discriminator", encoded.contains("\"joystick_path\""))
    }

    @Test
    fun `empty step list has zero total duration`() {
        assertEquals(0L, emptyList<MacroStep>().totalDurationMs())
    }

    @Test
    fun `unknown keys are ignored on decode`() {
        // Ensures forward compatibility: data written by a future version with
        // additional fields can still be read by the current version.
        val withExtra = """
            {
                "id": "abc",
                "name": "X",
                "steps": [],
                "loopEnabled": false,
                "loopPauseMs": 0,
                "futureField": "ignore-me"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<Macro>(withExtra)
        assertEquals("abc", decoded.id)
        assertEquals("X", decoded.name)
    }

    @Test
    fun `mixed macro including JoystickPath survives JSON round-trip`() {
        val macro = Macro(
            id = "test-uuid-path",
            name = "Full combo with path",
            steps = listOf(
                MacroStep.GamepadButtonTap(0L, 50L, 0x130, "A"),
                MacroStep.JoystickPath(
                    startTimeMs = 60L,
                    durationMs  = 201L, // > max sample offset (200)
                    stick       = JoystickStick.LEFT,
                    samples     = listOf(
                        PathSample(offsetMs = 0L,   x = 0.5f, y = 0f),
                        PathSample(offsetMs = 100L, x = 1.0f, y = 0.5f),
                        PathSample(offsetMs = 200L, x = 0.0f, y = 0.0f),
                    ),
                ),
                MacroStep.DPadTap(300L, 100L, 1, 0),
            ),
        )
        val decoded = json.decodeFromString<Macro>(json.encodeToString(macro))
        assertEquals(macro, decoded)
        val path = decoded.steps[1] as MacroStep.JoystickPath
        assertEquals(3, path.samples.size)
        assertEquals(100L, path.samples[1].offsetMs)
    }

    @Test
    fun `JoystickPath endTimeMs equals startTimeMs plus durationMs`() {
        val step = MacroStep.JoystickPath(
            startTimeMs = 200L,
            durationMs  = 350L,
            stick       = JoystickStick.RIGHT,
            samples     = listOf(PathSample(0L, 0f, 0f)),
        )
        assertEquals(550L, step.endTimeMs())
    }
}
