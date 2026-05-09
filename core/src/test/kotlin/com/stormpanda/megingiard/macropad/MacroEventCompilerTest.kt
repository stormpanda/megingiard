package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [buildMacroEventList] — the pure compiler that converts overlapping
 * [MacroStep]s into a flat, time-sorted [MacroEvent] list.
 */
class MacroEventCompilerTest {

    private fun macro(vararg steps: MacroStep) = Macro(
        id = "test",
        name = "test",
        steps = steps.toList(),
    )

    // ── empty input ───────────────────────────────────────────────────────────

    @Test
    fun `empty macro produces no events`() {
        assertTrue(buildMacroEventList(macro()).isEmpty())
    }

    // ── GamepadButtonTap ──────────────────────────────────────────────────────

    @Test
    fun `button tap produces DOWN at start and UP at end`() {
        val events = buildMacroEventList(macro(
            MacroStep.GamepadButtonTap(startTimeMs = 100L, durationMs = 250L, btnCode = 0x130, label = "A"),
        ))
        assertEquals(2, events.size)
        assertEquals(MacroEventType.BUTTON_DOWN, events[0].type)
        assertEquals(100L, events[0].timeMs)
        assertEquals(0x130, events[0].code)

        assertEquals(MacroEventType.BUTTON_UP, events[1].type)
        assertEquals(350L, events[1].timeMs)
        assertEquals(0x130, events[1].code)
    }

    // ── JoystickMove ──────────────────────────────────────────────────────────

    @Test
    fun `left stick move produces four JOYSTICK_SET events on ABS_X and ABS_Y`() {
        val events = buildMacroEventList(macro(
            MacroStep.JoystickMove(0L, 200L, JoystickStick.LEFT, x = 1.0f, y = -1.0f),
        ))
        assertEquals(4, events.size)
        val startEvents = events.filter { it.timeMs == 0L }
        val endEvents   = events.filter { it.timeMs == 200L }
        assertEquals(2, startEvents.size)
        assertEquals(2, endEvents.size)
        assertTrue(startEvents.any { it.code == GamepadKeycodes.ABS_X && it.value > 0 })
        assertTrue(startEvents.any { it.code == GamepadKeycodes.ABS_Y && it.value < 0 })
        assertTrue(endEvents.all { it.value == 0 })
    }

    @Test
    fun `right stick move uses ABS_Z and ABS_RZ axis codes`() {
        val events = buildMacroEventList(macro(
            MacroStep.JoystickMove(0L, 100L, JoystickStick.RIGHT, x = 0.5f, y = 0.5f),
        ))
        val axisCodes = events.map { it.code }.toSet()
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_Z))
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_RZ))
    }

    @Test
    fun `joystick full deflection maps to int16 range`() {
        val events = buildMacroEventList(macro(
            MacroStep.JoystickMove(0L, 100L, JoystickStick.LEFT, x = 1.0f, y = -1.0f),
        ))
        val xEvent = events.first { it.timeMs == 0L && it.code == GamepadKeycodes.ABS_X }
        val yEvent = events.first { it.timeMs == 0L && it.code == GamepadKeycodes.ABS_Y }
        assertEquals(32767, xEvent.value)   // 1.0f * 32768 clamped to 32767
        assertEquals(-32768, yEvent.value)  // -1.0f * 32768 = -32768 exactly
    }

    @Test
    fun `joystick over-range input is clamped to int16`() {
        // coerceIn(-1f, 1f) runs first, so 2.0f → 1.0f → 32767
        val events = buildMacroEventList(macro(
            MacroStep.JoystickMove(0L, 100L, JoystickStick.LEFT, x = 2.0f, y = -2.0f),
        ))
        val xEvent = events.first { it.timeMs == 0L && it.code == GamepadKeycodes.ABS_X }
        assertEquals(32767, xEvent.value)
    }

    // ── DPadTap ───────────────────────────────────────────────────────────────

    @Test
    fun `dpad tap produces four HAT events`() {
        val events = buildMacroEventList(macro(
            MacroStep.DPadTap(0L, 100L, dirX = -1, dirY = 1),
        ))
        assertEquals(4, events.size)
        assertTrue(events.all { it.type == MacroEventType.HAT })
        val start = events.filter { it.timeMs == 0L }
        val end   = events.filter { it.timeMs == 100L }
        assertTrue(start.any { it.code == 0 && it.value == -1 })
        assertTrue(start.any { it.code == 1 && it.value == 1 })
        assertTrue(end.all { it.value == 0 })
    }

    // ── TouchTap ─────────────────────────────────────────────────────────────

    @Test
    fun `touch tap produces TOUCH_DOWN then TOUCH_UP with coordinates`() {
        val events = buildMacroEventList(macro(
            MacroStep.TouchTap(50L, 33L, 0.25f, 0.75f),
        ))
        assertEquals(2, events.size)
        assertEquals(MacroEventType.TOUCH_DOWN, events[0].type)
        assertEquals(50L, events[0].timeMs)
        assertEquals(0.25f, events[0].normX)
        assertEquals(0.75f, events[0].normY)

        assertEquals(MacroEventType.TOUCH_UP, events[1].type)
        assertEquals(83L, events[1].timeMs)
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Test
    fun `events are sorted by timestamp ascending`() {
        val events = buildMacroEventList(macro(
            MacroStep.GamepadButtonTap(200L, 50L, 0x130, "A"),
            MacroStep.GamepadButtonTap(0L,   50L, 0x131, "B"),
        ))
        val times = events.map { it.timeMs }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun `reset events sort before set events at the same timestamp`() {
        // step1 ends at 100ms; step2 also starts at 100ms — UP must precede DOWN
        val events = buildMacroEventList(macro(
            MacroStep.GamepadButtonTap(0L,   100L, 0x130, "A"),  // UP at 100
            MacroStep.GamepadButtonTap(100L, 100L, 0x131, "B"),  // DOWN at 100
        ))
        val at100 = events.filter { it.timeMs == 100L }
        assertEquals(2, at100.size)
        assertEquals(MacroEventType.BUTTON_UP,   at100[0].type)
        assertEquals(MacroEventType.BUTTON_DOWN, at100[1].type)
    }

    // ── JoystickPath ──────────────────────────────────────────────────────────

    @Test
    fun `joystick path emits JOYSTICK_SET for each sample then reset at end`() {
        val step = MacroStep.JoystickPath(
            startTimeMs = 100L,
            durationMs  = 200L,
            stick       = JoystickStick.LEFT,
            samples     = listOf(
                PathSample(offsetMs = 0L,   x = 0.5f,  y = 0f),
                PathSample(offsetMs = 100L, x = 1.0f,  y = 0.5f),
            ),
        )
        val events = buildMacroEventList(macro(step))
        // Each sample emits 2 JOYSTICK_SET events (X + Y axis), plus 2 reset events at end.
        assertEquals(6, events.size)

        val sample0Events = events.filter { it.timeMs == 100L }
        assertEquals(2, sample0Events.size)
        assertTrue(sample0Events.all { it.type == MacroEventType.JOYSTICK_SET })
        assertTrue(sample0Events.any { it.code == GamepadKeycodes.ABS_X && it.value > 0 })

        val sample1Events = events.filter { it.timeMs == 200L }
        assertEquals(2, sample1Events.size)

        val resetEvents = events.filter { it.timeMs == 300L }
        assertEquals(2, resetEvents.size)
        assertTrue(resetEvents.all { it.value == 0 })
    }

    @Test
    fun `joystick path right stick uses ABS_Z and ABS_RZ`() {
        val step = MacroStep.JoystickPath(
            startTimeMs = 0L,
            durationMs  = 100L,
            stick       = JoystickStick.RIGHT,
            samples     = listOf(PathSample(0L, 0.5f, 0.5f)),
        )
        val events = buildMacroEventList(macro(step))
        val axisCodes = events.filter { it.timeMs == 0L }.map { it.code }.toSet()
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_Z))
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_RZ))
    }

    @Test
    fun `joystick path sample at duration is filtered so reset wins at end`() {
        // Regression: a sample whose offsetMs equals the step's durationMs would land at the
        // same global timestamp as the end-of-step neutral reset. The reset must sort first
        // (resets before non-resets at equal timestamps) but the sample must NOT be applied
        // afterwards, otherwise the stick latches non-neutral. The compiler defends against
        // this by skipping samples whose offsetMs >= durationMs.
        val step = MacroStep.JoystickPath(
            startTimeMs = 0L,
            durationMs  = 100L,
            stick       = JoystickStick.LEFT,
            samples     = listOf(
                PathSample(offsetMs = 0L,   x = 0.5f, y = 0f),
                PathSample(offsetMs = 100L, x = 1.0f, y = 0f), // boundary: offset == duration
            ),
        )
        val events = buildMacroEventList(macro(step))
        val at100 = events.filter { it.timeMs == 100L && it.code == GamepadKeycodes.ABS_X }
        // Only the reset event should land at t=100, not a JOYSTICK_SET with value != 0.
        assertEquals(1, at100.size)
        assertEquals(0, at100[0].value)
    }

    @Test
    fun `joystick path with no samples emits only the two neutral reset events`() {
        val step = MacroStep.JoystickPath(
            startTimeMs = 50L,
            durationMs  = 200L,
            stick       = JoystickStick.LEFT,
            samples     = emptyList(),
        )
        val events = buildMacroEventList(macro(step))
        assertEquals(2, events.size)
        assertEquals(250L, events[0].timeMs)
        assertEquals(250L, events[1].timeMs)
        assertTrue(events.all { it.value == 0 })
    }

    @Test
    fun `joystick path sample beyond duration is also filtered`() {
        // Guard also catches offsetMs > durationMs (not only the == boundary).
        val step = MacroStep.JoystickPath(
            startTimeMs = 0L,
            durationMs  = 100L,
            stick       = JoystickStick.LEFT,
            samples     = listOf(
                PathSample(offsetMs = 50L,  x = 0.5f, y = 0f), // inside → kept
                PathSample(offsetMs = 150L, x = 1.0f, y = 0f), // beyond → filtered
            ),
        )
        val events = buildMacroEventList(macro(step))
        // 2 events from the valid sample + 2 reset events = 4 total
        assertEquals(4, events.size)
        val at150 = events.filter { it.timeMs == 150L }
        assertTrue("No event should land at t=150 (beyond step end)", at150.isEmpty())
    }

    @Test
    fun `joystick reset value zero sorts before non-zero at same timestamp`() {
        // Two joystick steps back-to-back on the same axis: reset of step1 and set of step2
        // both land at 100ms.  The reset (value=0) must come first.
        val events = buildMacroEventList(macro(
            MacroStep.JoystickMove(0L,   100L, JoystickStick.LEFT, x = 1.0f, y = 0f),
            MacroStep.JoystickMove(100L, 100L, JoystickStick.LEFT, x = -1.0f, y = 0f),
        ))
        val xAt100 = events.filter { it.timeMs == 100L && it.code == GamepadKeycodes.ABS_X }
        assertTrue(xAt100.size >= 2)
        // reset (value == 0) must precede set (value != 0) at the same timestamp
        val firstNonZeroIdx = xAt100.indexOfFirst { it.value != 0 }
        val lastZeroIdx     = xAt100.indexOfLast  { it.value == 0 }
        assertTrue(lastZeroIdx < firstNonZeroIdx)
    }
}
