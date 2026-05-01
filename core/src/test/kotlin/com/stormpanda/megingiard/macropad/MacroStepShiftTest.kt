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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun gamepad(startMs: Long, durationMs: Long = 100L) = MacroStep.GamepadButtonTap(
        startTimeMs = startMs,
        durationMs  = durationMs,
        btnCode     = 0x130,
        label       = "A",
    )

    // ── ShiftMode.NONE ────────────────────────────────────────────────────────

    @Test
    fun `NONE — edited step is replaced, no other step moves`() {
        val edited    = gamepad(startMs = 200L, durationMs = 200L)
        val before    = gamepad(startMs = 100L)
        val concurrent = gamepad(startMs = 300L) // inside old span
        val after     = gamepad(startMs = 500L)
        val newEdited = edited.copy(startTimeMs = 500L, durationMs = 400L)

        val result = applyShiftSubsequent(
            steps       = listOf(before, concurrent, edited, after),
            editedIndex = 2,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.NONE,
        )

        assertEquals(newEdited, result[2]) // replaced
        assertEquals(100L, result[0].startTimeMs) // untouched
        assertEquals(300L, result[1].startTimeMs) // untouched
        assertEquals(500L, result[3].startTimeMs) // untouched
    }

    // ── ShiftMode.START_DELTA — threshold = oldEnd ────────────────────────────

    @Test
    fun `START_DELTA — step before oldEnd is not shifted`() {
        val edited     = gamepad(startMs = 100L, durationMs = 300L) // oldEnd = 400
        val concurrent = gamepad(startMs = 200L)                    // inside span, < oldEnd
        val newEdited  = edited.copy(startTimeMs = 200L)            // startDelta = +100

        val result = applyShiftSubsequent(
            steps       = listOf(concurrent, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.START_DELTA,
        )

        assertEquals(200L, result[0].startTimeMs) // concurrent: NOT shifted (< oldEnd)
    }

    @Test
    fun `START_DELTA — step at oldEnd is shifted by startDelta`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L) // oldEnd = 300
        val atEnd     = gamepad(startMs = 300L)
        val newEdited = edited.copy(startTimeMs = 200L)            // startDelta = +100

        val result = applyShiftSubsequent(
            steps       = listOf(atEnd, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.START_DELTA,
        )

        assertEquals(400L, result[0].startTimeMs) // 300 + 100
    }

    @Test
    fun `START_DELTA — pure duration change produces zero delta, nothing shifts`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L) // oldEnd = 300
        val after     = gamepad(startMs = 400L)
        val newEdited = edited.copy(durationMs = 500L)             // startDelta = 0

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.START_DELTA,
        )

        assertEquals(400L, result[0].startTimeMs) // no shift
    }

    @Test
    fun `START_DELTA — backward start move shifts subsequent steps backward`() {
        val edited    = gamepad(startMs = 500L, durationMs = 100L) // oldEnd = 600
        val after     = gamepad(startMs = 700L)
        val newEdited = edited.copy(startTimeMs = 300L)            // startDelta = -200

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.START_DELTA,
        )

        assertEquals(500L, result[0].startTimeMs) // 700 - 200
    }

    @Test
    fun `START_DELTA — result clamped to 0`() {
        val edited    = gamepad(startMs = 500L, durationMs = 100L)
        val close     = gamepad(startMs = 600L)
        val newEdited = edited.copy(startTimeMs = 0L)              // startDelta = -500

        val result = applyShiftSubsequent(
            steps       = listOf(close, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.START_DELTA,
        )

        // 600 - 500 = 100 → still positive, not clamped
        assertEquals(100L, result[0].startTimeMs)
    }

    @Test
    fun `START_DELTA — result clamped to maxTimeMs`() {
        val edited    = gamepad(startMs = 100L, durationMs = 100L)
        val after     = gamepad(startMs = 200L)
        val newEdited = edited.copy(startTimeMs = 9_900L)          // startDelta = +9800

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.START_DELTA,
            maxTimeMs   = 10_000L,
        )

        // 200 + 9800 = 10000 → at max boundary
        assertEquals(10_000L, result[0].startTimeMs)
    }

    // ── ShiftMode.END_DELTA — threshold = oldEnd ──────────────────────────────

    @Test
    fun `END_DELTA — step before oldEnd is not shifted`() {
        val edited     = gamepad(startMs = 100L, durationMs = 300L) // oldEnd = 400
        val concurrent = gamepad(startMs = 250L)
        val newEdited  = edited.copy(durationMs = 600L)             // endDelta = +300

        val result = applyShiftSubsequent(
            steps       = listOf(concurrent, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.END_DELTA,
        )

        assertEquals(250L, result[0].startTimeMs) // NOT shifted
    }

    @Test
    fun `END_DELTA — step at oldEnd is shifted by endDelta (duration extend)`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L) // oldEnd = 300
        val atEnd     = gamepad(startMs = 300L)
        val newEdited = edited.copy(durationMs = 400L)             // endDelta = +200

        val result = applyShiftSubsequent(
            steps       = listOf(atEnd, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.END_DELTA,
        )

        assertEquals(500L, result[0].startTimeMs) // 300 + 200
    }

    @Test
    fun `END_DELTA — duration shorten shifts subsequent steps backward`() {
        val edited    = gamepad(startMs = 0L, durationMs = 500L)   // oldEnd = 500
        val after     = gamepad(startMs = 600L)
        val newEdited = edited.copy(durationMs = 200L)             // endDelta = -300

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.END_DELTA,
        )

        assertEquals(300L, result[0].startTimeMs) // 600 - 300
    }

    @Test
    fun `END_DELTA — move + extend, endDelta = startDelta + durationDelta`() {
        // old: start=100, dur=200, end=300
        // new: start=200, dur=300, end=500  → endDelta = +200
        val edited    = gamepad(startMs = 100L, durationMs = 200L)
        val after     = gamepad(startMs = 400L)
        val newEdited = edited.copy(startTimeMs = 200L, durationMs = 300L)

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.END_DELTA,
        )

        assertEquals(600L, result[0].startTimeMs) // 400 + 200
    }

    @Test
    fun `END_DELTA — pure start move, endDelta equals startDelta, subsequent steps follow`() {
        val edited    = gamepad(startMs = 200L, durationMs = 100L) // oldEnd = 300
        val after     = gamepad(startMs = 400L)
        val newEdited = edited.copy(startTimeMs = 300L)            // endDelta = +100

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.END_DELTA,
        )

        assertEquals(500L, result[0].startTimeMs) // 400 + 100
    }

    @Test
    fun `END_DELTA — no change produces zero delta, nothing shifts`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L)
        val after     = gamepad(startMs = 400L)

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = edited,
            mode        = ShiftMode.END_DELTA,
        )

        assertEquals(400L, result[0].startTimeMs)
    }

    // ── START_DELTA vs END_DELTA distinguish when both start and dur change ───

    @Test
    fun `START_DELTA and END_DELTA differ when both start and duration change`() {
        // old: start=100, dur=200, end=300
        // new: start=200, dur=100, end=300 → startDelta=+100, endDelta=0
        val edited    = gamepad(startMs = 100L, durationMs = 200L)
        val after     = gamepad(startMs = 400L)
        val newEdited = edited.copy(startTimeMs = 200L, durationMs = 100L)

        val startResult = applyShiftSubsequent(
            steps = listOf(after, edited), editedIndex = 1,
            oldStep = edited, newStep = newEdited, mode = ShiftMode.START_DELTA,
        )
        val endResult = applyShiftSubsequent(
            steps = listOf(after, edited), editedIndex = 1,
            oldStep = edited, newStep = newEdited, mode = ShiftMode.END_DELTA,
        )

        assertEquals(500L, startResult[0].startTimeMs) // 400 + 100 (startDelta)
        assertEquals(400L, endResult[0].startTimeMs)   // 400 + 0   (endDelta = 0)
    }

    // ── Concurrent steps never shift in any non-NONE mode ────────────────────

    @Test
    fun `concurrent steps never shift regardless of mode`() {
        // Edited: start=100, dur=300, end=400
        // Concurrent: starts at 200 (inside span)
        val edited     = gamepad(startMs = 100L, durationMs = 300L)
        val concurrent = gamepad(startMs = 200L)
        val newEdited  = edited.copy(startTimeMs = 0L, durationMs = 600L) // big change

        for (mode in listOf(ShiftMode.START_DELTA, ShiftMode.END_DELTA)) {
            val result = applyShiftSubsequent(
                steps = listOf(concurrent, edited), editedIndex = 1,
                oldStep = edited, newStep = newEdited, mode = mode,
            )
            assertEquals("concurrent must not shift in mode $mode",
                200L, result[0].startTimeMs)
        }
    }

    // ── Multiple steps — only those at or after oldEnd are affected ───────────

    @Test
    fun `END_DELTA — multiple steps with correct threshold applied`() {
        val edited = gamepad(startMs = 200L, durationMs = 200L) // oldEnd = 400
        val s0 = gamepad(startMs = 100L)  // before oldStart → never shifts
        val s1 = gamepad(startMs = 200L)  // at oldStart but < oldEnd → no shift
        val s2 = gamepad(startMs = 300L)  // inside span → no shift
        val s3 = gamepad(startMs = 400L)  // at oldEnd → shifts
        val s4 = gamepad(startMs = 600L)  // after oldEnd → shifts
        val newEdited = edited.copy(durationMs = 400L) // endDelta = +200

        val result = applyShiftSubsequent(
            steps       = listOf(s0, s1, s2, s3, s4, edited),
            editedIndex = 5,
            oldStep     = edited,
            newStep     = newEdited,
            mode        = ShiftMode.END_DELTA,
        )

        assertEquals(100L,  result[0].startTimeMs) // s0: no shift
        assertEquals(200L,  result[1].startTimeMs) // s1: no shift (< oldEnd)
        assertEquals(300L,  result[2].startTimeMs) // s2: no shift (< oldEnd)
        assertEquals(600L,  result[3].startTimeMs) // s3: 400 + 200
        assertEquals(800L,  result[4].startTimeMs) // s4: 600 + 200
    }

    // ── Edited step is always correctly placed ────────────────────────────────

    @Test
    fun `edited step is always replaced with newStep`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L)
        val newEdited = gamepad(startMs = 500L, durationMs = 50L)

        for (mode in ShiftMode.entries) {
            val result = applyShiftSubsequent(
                steps = listOf(edited), editedIndex = 0,
                oldStep = edited, newStep = newEdited, mode = mode,
            )
            assertEquals("edited step should be newStep in mode $mode",
                newEdited, result[0])
        }
    }
}

