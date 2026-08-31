package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [applyShiftSubsequent] with the three [ShiftMode] values.
 *
 * Key invariant across all modes: only steps whose startTimeMs is >= the edited step's
 * **old end time** are eligible for shifting. Steps that overlap or run concurrently
 * (start between oldStart and oldEnd) are **never** moved.
 */
class MacroStepShiftTest {
    private fun gamepad(
        startMs: Long,
        durationMs: Long = 100L,
    ) = MacroStep.GamepadButtonTap(
        startTimeMs = startMs,
        durationMs = durationMs,
        btnCode = 0x130,
        label = "A",
    )

    private fun joystickPath(
        startMs: Long,
        durationMs: Long = 201L,
    ) = MacroStep.JoystickPath(
        startTimeMs = startMs,
        durationMs = durationMs,
        stick = JoystickStick.LEFT,
        samples =
            listOf(
                PathSample(offsetMs = 0L, x = 0.5f, y = 0f),
                PathSample(offsetMs = 100L, x = 1.0f, y = 0.5f),
                PathSample(offsetMs = 200L, x = 0.0f, y = 0.0f),
            ),
    )

    private fun shift(
        edited: MacroStep,
        newEdited: MacroStep,
        mode: ShiftMode,
        vararg otherSteps: MacroStep,
        maxTimeMs: Long = 10_000L,
    ): List<MacroStep> {
        val steps = otherSteps.toList() + edited
        return applyShiftSubsequent(
            steps = steps,
            editedIndex = steps.lastIndex,
            oldStep = edited,
            newStep = newEdited,
            mode = mode,
            maxTimeMs = maxTimeMs,
        )
    }

    @Test
    fun `NONE — edited step is replaced, no other step moves`() {
        val edited = gamepad(startMs = 200L, durationMs = 200L)
        val before = gamepad(startMs = 100L)
        val concurrent = gamepad(startMs = 300L)
        val after = gamepad(startMs = 500L)
        val newEdited = edited.copy(startTimeMs = 500L, durationMs = 400L)

        val result =
            applyShiftSubsequent(
                steps = listOf(before, concurrent, edited, after),
                editedIndex = 2,
                oldStep = edited,
                newStep = newEdited,
                mode = ShiftMode.NONE,
            )

        assertEquals(newEdited, result[2])
        assertEquals(100L, result[0].startTimeMs)
        assertEquals(300L, result[1].startTimeMs)
        assertEquals(500L, result[3].startTimeMs)
    }

    @Test
    fun `START_DELTA — step before oldEnd is not shifted`() {
        val edited = gamepad(startMs = 100L, durationMs = 300L)
        val concurrent = gamepad(startMs = 200L)
        val result = shift(edited, edited.copy(startTimeMs = 200L), ShiftMode.START_DELTA, concurrent)
        assertEquals(200L, result[0].startTimeMs)
    }

    @Test
    fun `START_DELTA — step at oldEnd is shifted by startDelta`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val atEnd = gamepad(startMs = 300L)
        val result = shift(edited, edited.copy(startTimeMs = 200L), ShiftMode.START_DELTA, atEnd)
        assertEquals(400L, result[0].startTimeMs)
    }

    @Test
    fun `START_DELTA — pure duration change produces zero delta, nothing shifts`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val after = gamepad(startMs = 400L)
        val result = shift(edited, edited.copy(durationMs = 500L), ShiftMode.START_DELTA, after)
        assertEquals(400L, result[0].startTimeMs)
    }

    @Test
    fun `START_DELTA — backward start move shifts subsequent steps backward`() {
        val edited = gamepad(startMs = 500L, durationMs = 100L)
        val after = gamepad(startMs = 700L)
        val result = shift(edited, edited.copy(startTimeMs = 300L), ShiftMode.START_DELTA, after)
        assertEquals(500L, result[0].startTimeMs)
    }

    @Test
    fun `START_DELTA — result clamped to 0`() {
        val edited = gamepad(startMs = 500L, durationMs = 100L)
        val close = gamepad(startMs = 600L)
        val result = shift(edited, edited.copy(startTimeMs = 0L), ShiftMode.START_DELTA, close)
        assertEquals(100L, result[0].startTimeMs)
    }

    @Test
    fun `START_DELTA — result clamped to maxTimeMs`() {
        val edited = gamepad(startMs = 100L, durationMs = 100L)
        val after = gamepad(startMs = 200L)
        val result = shift(edited, edited.copy(startTimeMs = 9_900L), ShiftMode.START_DELTA, after, maxTimeMs = 10_000L)
        assertEquals(10_000L, result[0].startTimeMs)
    }

    @Test
    fun `END_DELTA — step before oldEnd is not shifted`() {
        val edited = gamepad(startMs = 100L, durationMs = 300L)
        val concurrent = gamepad(startMs = 250L)
        val result = shift(edited, edited.copy(durationMs = 600L), ShiftMode.END_DELTA, concurrent)
        assertEquals(250L, result[0].startTimeMs)
    }

    @Test
    fun `END_DELTA — step at oldEnd is shifted by endDelta (duration extend)`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val atEnd = gamepad(startMs = 300L)
        val result = shift(edited, edited.copy(durationMs = 400L), ShiftMode.END_DELTA, atEnd)
        assertEquals(500L, result[0].startTimeMs)
    }

    @Test
    fun `END_DELTA — duration shorten shifts subsequent steps backward`() {
        val edited = gamepad(startMs = 0L, durationMs = 500L)
        val after = gamepad(startMs = 600L)
        val result = shift(edited, edited.copy(durationMs = 200L), ShiftMode.END_DELTA, after)
        assertEquals(300L, result[0].startTimeMs)
    }

    @Test
    fun `END_DELTA — move + extend, endDelta = startDelta + durationDelta`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val after = gamepad(startMs = 400L)
        val result = shift(edited, edited.copy(startTimeMs = 200L, durationMs = 300L), ShiftMode.END_DELTA, after)
        assertEquals(600L, result[0].startTimeMs)
    }

    @Test
    fun `END_DELTA — pure start move, endDelta equals startDelta, subsequent steps follow`() {
        val edited = gamepad(startMs = 200L, durationMs = 100L)
        val after = gamepad(startMs = 400L)
        val result = shift(edited, edited.copy(startTimeMs = 300L), ShiftMode.END_DELTA, after)
        assertEquals(500L, result[0].startTimeMs)
    }

    @Test
    fun `END_DELTA — no change produces zero delta, nothing shifts`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val after = gamepad(startMs = 400L)
        val result = shift(edited, edited, ShiftMode.END_DELTA, after)
        assertEquals(400L, result[0].startTimeMs)
    }

    @Test
    fun `START_DELTA and END_DELTA differ when both start and duration change`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val after = gamepad(startMs = 400L)
        val newEdited = edited.copy(startTimeMs = 200L, durationMs = 100L)

        val startResult = shift(edited, newEdited, ShiftMode.START_DELTA, after)
        val endResult = shift(edited, newEdited, ShiftMode.END_DELTA, after)

        assertEquals(500L, startResult[0].startTimeMs)
        assertEquals(400L, endResult[0].startTimeMs)
    }

    @Test
    fun `concurrent steps never shift regardless of mode`() {
        val edited = gamepad(startMs = 100L, durationMs = 300L)
        val concurrent = gamepad(startMs = 200L)
        val newEdited = edited.copy(startTimeMs = 0L, durationMs = 600L)

        for (mode in listOf(ShiftMode.START_DELTA, ShiftMode.END_DELTA)) {
            val result = shift(edited, newEdited, mode, concurrent)
            assertEquals("concurrent must not shift in mode $mode", 200L, result[0].startTimeMs)
        }
    }

    @Test
    fun `END_DELTA — multiple steps with correct threshold applied`() {
        val edited = gamepad(startMs = 200L, durationMs = 200L)
        val s0 = gamepad(startMs = 100L)
        val s1 = gamepad(startMs = 200L)
        val s2 = gamepad(startMs = 300L)
        val s3 = gamepad(startMs = 400L)
        val s4 = gamepad(startMs = 600L)
        val newEdited = edited.copy(durationMs = 400L)

        val result = shift(edited, newEdited, ShiftMode.END_DELTA, s0, s1, s2, s3, s4)

        assertEquals(100L, result[0].startTimeMs)
        assertEquals(200L, result[1].startTimeMs)
        assertEquals(300L, result[2].startTimeMs)
        assertEquals(600L, result[3].startTimeMs)
        assertEquals(800L, result[4].startTimeMs)
    }

    @Test
    fun `edited step is always replaced with newStep`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val newEdited = gamepad(startMs = 500L, durationMs = 50L)

        for (mode in ShiftMode.entries) {
            val result = shift(edited, newEdited, mode)
            assertEquals("edited step should be newStep in mode $mode", newEdited, result[0])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `require guard throws when oldStep does not match steps at editedIndex`() {
        val actual = gamepad(startMs = 100L)
        val wrong = gamepad(startMs = 999L)
        applyShiftSubsequent(
            steps = listOf(actual),
            editedIndex = 0,
            oldStep = wrong,
            newStep = actual.copy(startTimeMs = 200L),
            mode = ShiftMode.END_DELTA,
        )
    }

    @Test
    fun `JoystickPath withStartTime changes startTimeMs but preserves samples verbatim`() {
        val original = joystickPath(startMs = 100L)
        val shifted = original.withStartTime(500L) as MacroStep.JoystickPath
        assertEquals(500L, shifted.startTimeMs)
        assertEquals(original.durationMs, shifted.durationMs)
        assertEquals(original.samples, shifted.samples)
        assertEquals(original.stick, shifted.stick)
    }

    @Test
    fun `START_DELTA — JoystickPath as subsequent step is shifted correctly`() {
        val edited = gamepad(startMs = 0L, durationMs = 100L)
        val pathAfter = joystickPath(startMs = 100L)
        val newEdited = edited.copy(startTimeMs = 50L)

        val result = shift(edited, newEdited, ShiftMode.START_DELTA, pathAfter)
        val shiftedPath = result[0] as MacroStep.JoystickPath
        assertEquals(150L, shiftedPath.startTimeMs)
        assertEquals(pathAfter.samples, shiftedPath.samples)
        assertEquals(pathAfter.durationMs, shiftedPath.durationMs)
    }
}
