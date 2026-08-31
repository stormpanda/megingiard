package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [buildMacroEventList] — the pure compiler that converts overlapping
 * [MacroStep]s into a flat, time-sorted [MacroEvent] list.
 */
class MacroEventCompilerTest {
    private fun compile(vararg steps: MacroStep) = buildMacroEventList(Macro(id = "test", name = "test", steps = steps.toList()))

    private fun touchSample(
        offsetMs: Long,
        pointerId: Int = 0,
        action: TouchAction = TouchAction.DOWN,
        normX: Float = 0.1f,
        normY: Float = 0.2f,
    ) = TouchSample(offsetMs = offsetMs, pointerId = pointerId, action = action, normX = normX, normY = normY)

    // ── empty input ───────────────────────────────────────────────────────────

    @Test
    fun `empty macro produces no events`() {
        assertTrue(compile().isEmpty())
    }

    // ── GamepadButtonTap ──────────────────────────────────────────────────────

    @Test
    fun `button tap produces DOWN at start and UP at end`() {
        val events = compile(MacroStep.GamepadButtonTap(startTimeMs = 100L, durationMs = 250L, btnCode = 0x130, label = "A"))
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
        val events = compile(MacroStep.JoystickMove(0L, 200L, JoystickStick.LEFT, x = 1.0f, y = -1.0f))
        assertEquals(4, events.size)
        val startEvents = events.filter { it.timeMs == 0L }
        val endEvents = events.filter { it.timeMs == 200L }
        assertEquals(2, startEvents.size)
        assertEquals(2, endEvents.size)
        assertTrue(startEvents.any { it.code == GamepadKeycodes.ABS_X && it.value > 0 })
        assertTrue(startEvents.any { it.code == GamepadKeycodes.ABS_Y && it.value < 0 })
        assertTrue(endEvents.all { it.value == 0 })
    }

    @Test
    fun `right stick move uses ABS_Z and ABS_RZ axis codes`() {
        val events = compile(MacroStep.JoystickMove(0L, 100L, JoystickStick.RIGHT, x = 0.5f, y = 0.5f))
        val axisCodes = events.map { it.code }.toSet()
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_Z))
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_RZ))
    }

    @Test
    fun `joystick full deflection maps to int16 range`() {
        val events = compile(MacroStep.JoystickMove(0L, 100L, JoystickStick.LEFT, x = 1.0f, y = -1.0f))
        val xEvent = events.first { it.timeMs == 0L && it.code == GamepadKeycodes.ABS_X }
        val yEvent = events.first { it.timeMs == 0L && it.code == GamepadKeycodes.ABS_Y }
        assertEquals(32767, xEvent.value)
        assertEquals(-32768, yEvent.value)
    }

    @Test
    fun `joystick over-range input is clamped to int16`() {
        val events = compile(MacroStep.JoystickMove(0L, 100L, JoystickStick.LEFT, x = 2.0f, y = -2.0f))
        val xEvent = events.first { it.timeMs == 0L && it.code == GamepadKeycodes.ABS_X }
        assertEquals(32767, xEvent.value)
    }

    // ── DPadTap ───────────────────────────────────────────────────────────────

    @Test
    fun `dpad tap produces four HAT events`() {
        val events = compile(MacroStep.DPadTap(0L, 100L, dirX = -1, dirY = 1))
        assertEquals(4, events.size)
        assertTrue(events.all { it.type == MacroEventType.HAT })
        val start = events.filter { it.timeMs == 0L }
        val end = events.filter { it.timeMs == 100L }
        assertTrue(start.any { it.code == 0 && it.value == -1 })
        assertTrue(start.any { it.code == 1 && it.value == 1 })
        assertTrue(end.all { it.value == 0 })
    }

    // ── TouchTap ─────────────────────────────────────────────────────────────

    @Test
    fun `touch tap produces TOUCH_DOWN then TOUCH_UP with coordinates`() {
        val events = compile(MacroStep.TouchTap(50L, 33L, 0.25f, 0.75f))
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
        val events =
            compile(
                MacroStep.GamepadButtonTap(200L, 50L, 0x130, "A"),
                MacroStep.GamepadButtonTap(0L, 50L, 0x131, "B"),
            )
        val times = events.map { it.timeMs }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun `reset events sort before set events at the same timestamp`() {
        val events =
            compile(
                MacroStep.GamepadButtonTap(0L, 100L, 0x130, "A"),
                MacroStep.GamepadButtonTap(100L, 100L, 0x131, "B"),
            )
        val at100 = events.filter { it.timeMs == 100L }
        assertEquals(2, at100.size)
        assertEquals(MacroEventType.BUTTON_UP, at100[0].type)
        assertEquals(MacroEventType.BUTTON_DOWN, at100[1].type)
    }

    // ── JoystickPath ──────────────────────────────────────────────────────────

    @Test
    fun `joystick path emits JOYSTICK_SET for each sample then reset at end`() {
        val step =
            MacroStep.JoystickPath(
                startTimeMs = 100L,
                durationMs = 200L,
                stick = JoystickStick.LEFT,
                samples = listOf(PathSample(0L, 0.5f, 0f), PathSample(100L, 1.0f, 0.5f)),
            )
        val events = compile(step)
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
        val step = MacroStep.JoystickPath(0L, 100L, JoystickStick.RIGHT, samples = listOf(PathSample(0L, 0.5f, 0.5f)))
        val events = compile(step)
        val axisCodes = events.filter { it.timeMs == 0L }.map { it.code }.toSet()
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_Z))
        assertTrue(axisCodes.contains(GamepadKeycodes.ABS_RZ))
    }

    @Test
    fun `joystick path sample at duration is filtered so reset wins at end`() {
        val step =
            MacroStep.JoystickPath(
                0L,
                100L,
                JoystickStick.LEFT,
                samples = listOf(PathSample(0L, 0.5f, 0f), PathSample(100L, 1.0f, 0f)),
            )
        val events = compile(step)
        val at100 = events.filter { it.timeMs == 100L && it.code == GamepadKeycodes.ABS_X }
        assertEquals(1, at100.size)
        assertEquals(0, at100[0].value)
    }

    @Test
    fun `joystick path with no samples emits only the two neutral reset events`() {
        val step = MacroStep.JoystickPath(50L, 200L, JoystickStick.LEFT, samples = emptyList())
        val events = compile(step)
        assertEquals(2, events.size)
        assertEquals(250L, events[0].timeMs)
        assertEquals(250L, events[1].timeMs)
        assertTrue(events.all { it.value == 0 })
    }

    @Test
    fun `joystick path sample beyond duration is also filtered`() {
        val step =
            MacroStep.JoystickPath(
                0L,
                100L,
                JoystickStick.LEFT,
                samples = listOf(PathSample(50L, 0.5f, 0f), PathSample(150L, 1.0f, 0f)),
            )
        val events = compile(step)
        assertEquals(4, events.size)
        assertTrue(events.none { it.timeMs == 150L })
    }

    @Test
    fun `joystick reset value zero sorts before non-zero at same timestamp`() {
        val events =
            compile(
                MacroStep.JoystickMove(0L, 100L, JoystickStick.LEFT, x = 1.0f, y = 0f),
                MacroStep.JoystickMove(100L, 100L, JoystickStick.LEFT, x = -1.0f, y = 0f),
            )
        val xAt100 = events.filter { it.timeMs == 100L && it.code == GamepadKeycodes.ABS_X }
        assertTrue(xAt100.size >= 2)
        val firstNonZeroIdx = xAt100.indexOfFirst { it.value != 0 }
        val lastZeroIdx = xAt100.indexOfLast { it.value == 0 }
        assertTrue(lastZeroIdx < firstNonZeroIdx)
    }

    // ── TouchPath ─────────────────────────────────────────────────────────────

    @Test
    fun `touch path compiles samples correctly with pointerId and action`() {
        val step =
            MacroStep.TouchPath(
                startTimeMs = 100L,
                durationMs = 500L,
                samples =
                    listOf(
                        touchSample(0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                        touchSample(100L, pointerId = 1, action = TouchAction.DOWN, normX = 0.5f, normY = 0.6f),
                        touchSample(200L, pointerId = 0, action = TouchAction.MOVE, normX = 0.15f, normY = 0.25f),
                        touchSample(300L, pointerId = 0, action = TouchAction.UP, normX = 0.15f, normY = 0.25f),
                        touchSample(400L, pointerId = 1, action = TouchAction.UP, normX = 0.5f, normY = 0.6f),
                    ),
            )
        val events = compile(step)
        assertEquals(5, events.size)

        assertEquals(100L, events[0].timeMs)
        assertEquals(MacroEventType.TOUCH_DOWN, events[0].type)
        assertEquals(0, events[0].code)
        assertEquals(0.1f, events[0].normX)
        assertEquals(0.2f, events[0].normY)

        assertEquals(200L, events[1].timeMs)
        assertEquals(MacroEventType.TOUCH_DOWN, events[1].type)
        assertEquals(1, events[1].code)

        assertEquals(300L, events[2].timeMs)
        assertEquals(MacroEventType.TOUCH_MOVE, events[2].type)
        assertEquals(0, events[2].code)
        assertEquals(0.15f, events[2].normX)
        assertEquals(0.25f, events[2].normY)

        assertEquals(400L, events[3].timeMs)
        assertEquals(MacroEventType.TOUCH_UP, events[3].type)
        assertEquals(0, events[3].code)

        assertEquals(500L, events[4].timeMs)
        assertEquals(MacroEventType.TOUCH_UP, events[4].type)
        assertEquals(1, events[4].code)
    }

    @Test
    fun `touch path sample beyond duration is filtered`() {
        val step =
            MacroStep.TouchPath(
                startTimeMs = 0L,
                durationMs = 100L,
                samples =
                    listOf(
                        touchSample(0L, action = TouchAction.DOWN),
                        touchSample(100L, action = TouchAction.UP),
                        touchSample(150L, action = TouchAction.UP),
                    ),
            )
        val events = compile(step)
        assertEquals(2, events.size)
        assertEquals(MacroEventType.TOUCH_DOWN, events[0].type)
        assertEquals(MacroEventType.TOUCH_UP, events[1].type)
        assertEquals(100L, events[1].timeMs)
    }

    @Test
    fun `touch path emits synthetic UP for pointer still active when durationMs truncates stream`() {
        val step =
            MacroStep.TouchPath(
                startTimeMs = 0L,
                durationMs = 50L,
                samples =
                    listOf(
                        touchSample(0L, normX = 0.3f, normY = 0.4f),
                        touchSample(25L, action = TouchAction.MOVE, normX = 0.35f, normY = 0.45f),
                        touchSample(100L, action = TouchAction.UP, normX = 0.35f, normY = 0.45f),
                    ),
            )
        val events = compile(step)
        assertEquals(3, events.size)
        assertEquals(MacroEventType.TOUCH_DOWN, events[0].type)
        assertEquals(0L, events[0].timeMs)
        assertEquals(MacroEventType.TOUCH_MOVE, events[1].type)
        assertEquals(25L, events[1].timeMs)
        assertEquals(MacroEventType.TOUCH_UP, events[2].type)
        assertEquals(50L, events[2].timeMs)
        assertEquals(0, events[2].code)
        assertEquals(0.35f, events[2].normX)
        assertEquals(0.45f, events[2].normY)
    }

    @Test
    fun `touch path emits synthetic UPs for all truncated pointers at step end`() {
        val step =
            MacroStep.TouchPath(
                startTimeMs = 100L,
                durationMs = 30L,
                samples =
                    listOf(
                        touchSample(0L, pointerId = 0, normX = 0.1f, normY = 0.1f),
                        touchSample(10L, pointerId = 1, normX = 0.9f, normY = 0.9f),
                        touchSample(80L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.1f),
                        touchSample(90L, pointerId = 1, action = TouchAction.UP, normX = 0.9f, normY = 0.9f),
                    ),
            )
        val events = compile(step)
        assertEquals(4, events.size)
        val ups = events.filter { it.type == MacroEventType.TOUCH_UP }
        assertEquals(2, ups.size)
        assertTrue(ups.all { it.timeMs == 130L })
        assertTrue(ups.any { it.code == 0 })
        assertTrue(ups.any { it.code == 1 })
    }
}
