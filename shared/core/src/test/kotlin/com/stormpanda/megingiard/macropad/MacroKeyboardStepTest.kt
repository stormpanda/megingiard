package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MacroStep.KeyboardKeyTap], JSON serialization, character mapping,
 * and compiler event generation.
 */
class MacroKeyboardStepTest {
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

    private fun compile(vararg steps: MacroStep) = buildMacroEventList(Macro(id = "test", name = "test", steps = steps.toList()))

    @Test
    fun `keyboard key tap survives JSON round-trip`() {
        assertRoundTrip<MacroStep>(
            MacroStep.KeyboardKeyTap(
                startTimeMs = 150L,
                durationMs = 60L,
                keycode = LinuxKeycodes.KEY_SPACE,
                label = "Space",
                modifiers = emptyList(),
            ),
        )
    }

    @Test
    fun `keyboard key tap with modifiers survives JSON round-trip`() {
        assertRoundTrip<MacroStep>(
            MacroStep.KeyboardKeyTap(
                startTimeMs = 0L,
                durationMs = 100L,
                keycode = LinuxKeycodes.KEY_S,
                label = "S",
                modifiers = listOf(LinuxKeycodes.KEY_LEFTCTRL, LinuxKeycodes.KEY_LEFTSHIFT),
            ),
        )
    }

    @Test
    fun `serial name discriminator for keyboard_key_tap is stable`() {
        val step: MacroStep =
            MacroStep.KeyboardKeyTap(
                startTimeMs = 0L,
                durationMs = 50L,
                keycode = LinuxKeycodes.KEY_ENTER,
                label = "Enter",
            )
        val serialized = json.encodeToString<MacroStep>(step)
        assertTrue(serialized.contains("\"keyboard_key_tap\""))
    }

    @Test
    fun `charToKeyMapping maps lowercase and uppercase letters`() {
        val mappingA = LinuxKeycodes.charToKeyMapping('a')
        assertNotNull(mappingA)
        assertEquals(LinuxKeycodes.KEY_A, mappingA!!.keycode)
        assertFalse(mappingA.shift)

        val mappingUpperA = LinuxKeycodes.charToKeyMapping('A')
        assertNotNull(mappingUpperA)
        assertEquals(LinuxKeycodes.KEY_A, mappingUpperA!!.keycode)
        assertTrue(mappingUpperA.shift)
    }

    @Test
    fun `charToKeyMapping maps numbers and symbols`() {
        val mapping1 = LinuxKeycodes.charToKeyMapping('1')
        assertNotNull(mapping1)
        assertEquals(LinuxKeycodes.KEY_1, mapping1!!.keycode)
        assertFalse(mapping1.shift)

        val mappingExclamation = LinuxKeycodes.charToKeyMapping('!')
        assertNotNull(mappingExclamation)
        assertEquals(LinuxKeycodes.KEY_1, mappingExclamation!!.keycode)
        assertTrue(mappingExclamation.shift)

        val mappingSpace = LinuxKeycodes.charToKeyMapping(' ')
        assertNotNull(mappingSpace)
        assertEquals(LinuxKeycodes.KEY_SPACE, mappingSpace!!.keycode)
        assertFalse(mappingSpace.shift)
    }

    @Test
    fun `compiler produces KEY_DOWN at start and KEY_UP at end for key tap`() {
        val step =
            MacroStep.KeyboardKeyTap(
                startTimeMs = 200L,
                durationMs = 80L,
                keycode = LinuxKeycodes.KEY_W,
                label = "W",
            )
        val events = compile(step)
        assertEquals(2, events.size)

        assertEquals(MacroEventType.KEY_DOWN, events[0].type)
        assertEquals(200L, events[0].timeMs)
        assertEquals(LinuxKeycodes.KEY_W, events[0].code)

        assertEquals(MacroEventType.KEY_UP, events[1].type)
        assertEquals(280L, events[1].timeMs)
        assertEquals(LinuxKeycodes.KEY_W, events[1].code)
    }

    @Test
    fun `compiler presses modifiers before base key and releases in reverse order`() {
        val step =
            MacroStep.KeyboardKeyTap(
                startTimeMs = 100L,
                durationMs = 50L,
                keycode = LinuxKeycodes.KEY_C,
                label = "C",
                modifiers = listOf(LinuxKeycodes.KEY_LEFTCTRL, LinuxKeycodes.KEY_LEFTALT),
            )
        val events = compile(step)
        // 2 modifier downs + 1 key down, then 1 key up + 2 modifier ups = 6 events
        assertEquals(6, events.size)

        val downs = events.filter { it.type == MacroEventType.KEY_DOWN }
        assertEquals(3, downs.size)
        assertEquals(LinuxKeycodes.KEY_LEFTCTRL, downs[0].code)
        assertEquals(LinuxKeycodes.KEY_LEFTALT, downs[1].code)
        assertEquals(LinuxKeycodes.KEY_C, downs[2].code)
        assertTrue(downs.all { it.timeMs == 100L })

        val ups = events.filter { it.type == MacroEventType.KEY_UP }
        assertEquals(3, ups.size)
        assertEquals(LinuxKeycodes.KEY_C, ups[0].code)
        assertEquals(LinuxKeycodes.KEY_LEFTALT, ups[1].code)
        assertEquals(LinuxKeycodes.KEY_LEFTCTRL, ups[2].code)
        assertTrue(ups.all { it.timeMs == 150L })
    }

    @Test
    fun `KEY_UP sorts before KEY_DOWN at the exact same timestamp`() {
        val step1 =
            MacroStep.KeyboardKeyTap(
                startTimeMs = 0L,
                durationMs = 50L,
                keycode = LinuxKeycodes.KEY_A,
                label = "A",
            )
        val step2 =
            MacroStep.KeyboardKeyTap(
                startTimeMs = 50L,
                durationMs = 50L,
                keycode = LinuxKeycodes.KEY_A,
                label = "A",
            )
        val events = compile(step1, step2)
        val at50 = events.filter { it.timeMs == 50L }
        assertEquals(2, at50.size)
        assertEquals(MacroEventType.KEY_UP, at50[0].type)
        assertEquals(MacroEventType.KEY_DOWN, at50[1].type)
    }

    @Test
    fun `withStartTime and withTiming preserve KeyboardKeyTap attributes`() {
        val original =
            MacroStep.KeyboardKeyTap(
                startTimeMs = 100L,
                durationMs = 50L,
                keycode = LinuxKeycodes.KEY_ESC,
                label = "ESC",
                modifiers = listOf(LinuxKeycodes.KEY_LEFTALT),
            )
        val shifted = original.withStartTime(300L) as MacroStep.KeyboardKeyTap
        assertEquals(300L, shifted.startTimeMs)
        assertEquals(50L, shifted.durationMs)
        assertEquals(LinuxKeycodes.KEY_ESC, shifted.keycode)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTALT), shifted.modifiers)

        val reTimed = original.withTiming(400L, 80L) as MacroStep.KeyboardKeyTap
        assertEquals(400L, reTimed.startTimeMs)
        assertEquals(80L, reTimed.durationMs)
        assertEquals(LinuxKeycodes.KEY_ESC, reTimed.keycode)
    }

    @Test
    fun `randomized macro applies timing jitter to KeyboardKeyTap`() {
        val macro =
            Macro(
                id = "kb-macro",
                name = "Type",
                steps =
                    listOf(
                        MacroStep.KeyboardKeyTap(100L, 50L, LinuxKeycodes.KEY_A, "A"),
                        MacroStep.KeyboardKeyTap(200L, 50L, LinuxKeycodes.KEY_B, "B"),
                    ),
                randomizeTimingEnabled = true,
                randomizeTimingRangeMs = 10,
            )
        val randomized = macro.randomized()
        assertEquals(2, randomized.steps.size)
        val step1 = randomized.steps[0] as MacroStep.KeyboardKeyTap
        val step2 = randomized.steps[1] as MacroStep.KeyboardKeyTap

        assertTrue(step1.startTimeMs in 90L..110L)
        assertTrue(step1.durationMs in 40L..60L)
        assertTrue(step2.startTimeMs in 190L..210L)
        assertTrue(step2.durationMs in 40L..60L)
    }

    @Test
    fun `hasKeyboardSteps returns true only when macro contains KeyboardKeyTap`() {
        val keyboardMacro =
            Macro(
                id = "kb",
                name = "Keyboard Macro",
                steps = listOf(MacroStep.KeyboardKeyTap(0L, 50L, LinuxKeycodes.KEY_ENTER, "Enter")),
            )
        assertTrue(keyboardMacro.hasKeyboardSteps)

        val gamepadMacro =
            Macro(
                id = "gp",
                name = "Gamepad Macro",
                steps = listOf(MacroStep.GamepadButtonTap(0L, 50L, 304, "A")),
            )
        assertFalse(gamepadMacro.hasKeyboardSteps)

        val mixedMacro =
            Macro(
                id = "mixed",
                name = "Mixed Macro",
                steps =
                    listOf(
                        MacroStep.GamepadButtonTap(0L, 50L, 304, "A"),
                        MacroStep.KeyboardKeyTap(100L, 50L, LinuxKeycodes.KEY_SPACE, "Space"),
                    ),
            )
        assertTrue(mixedMacro.hasKeyboardSteps)

        val emptyMacro = Macro(id = "empty", name = "Empty Macro", steps = emptyList())
        assertFalse(emptyMacro.hasKeyboardSteps)
    }
}
