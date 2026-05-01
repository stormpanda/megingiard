package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [applyShiftSubsequent] — the pure shift-subsequent logic used by the
 * macro timeline editor when an existing step is edited with "shift subsequent" enabled.
 *
 * Threshold: steps starting at OR AFTER the edited step's old start time shift by
 * startDelta; steps starting at OR AFTER the old end time additionally shift by
 * durationDelta.
 */
class MacroStepShiftTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun gamepad(startMs: Long, durationMs: Long = 100L) = MacroStep.GamepadButtonTap(
        startTimeMs = startMs,
        durationMs  = durationMs,
        btnCode     = 0x130,
        label       = "A",
    )

    // ── 1. Pure start-time move forward ──────────────────────────────────────

    @Test
    fun `start move forward — step before edited start is not shifted`() {
        // edited step: start=200, dur=100 → oldEnd=300
        // bystander:   start=100 (before old start) → never shifts
        val edited = gamepad(startMs = 200L, durationMs = 100L)
        val before = gamepad(startMs = 100L)
        val newEdited = edited.copy(startTimeMs = 400L)          // +200 ms start move

        val result = applyShiftSubsequent(
            steps        = listOf(before, edited),
            editedIndex  = 1,
            oldStep      = edited,
            newStep      = newEdited,
        )

        assertEquals(100L, result[0].startTimeMs) // bystander untouched
    }

    @Test
    fun `start move forward — concurrent step at same old start shifts by startDelta`() {
        // concurrent step starts exactly at oldStart (threshold >=)
        val edited     = gamepad(startMs = 200L, durationMs = 200L) // oldEnd=400
        val concurrent = gamepad(startMs = 200L)                    // same start
        val newEdited  = edited.copy(startTimeMs = 300L)            // startDelta=+100

        val result = applyShiftSubsequent(
            steps       = listOf(concurrent, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        assertEquals(300L, result[0].startTimeMs) // shifted by startDelta only (not past oldEnd)
    }

    @Test
    fun `start move forward — step inside edited span shifts by startDelta only`() {
        // concurrent step starts inside the edited step's old span but before old end
        val edited     = gamepad(startMs = 100L, durationMs = 500L) // oldEnd=600
        val inner      = gamepad(startMs = 300L)                    // inside span
        val newEdited  = edited.copy(startTimeMs = 200L)            // startDelta=+100

        val result = applyShiftSubsequent(
            steps       = listOf(inner, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // inner starts at 300 >= 100 (old start) → +100; 300 < 600 (old end) → no durationDelta
        assertEquals(400L, result[0].startTimeMs)
    }

    @Test
    fun `start move forward — step at old end shifts by startDelta + durationDelta`() {
        // step exactly at old end gets full end-delta treatment
        val edited    = gamepad(startMs = 100L, durationMs = 200L) // oldEnd=300
        val atEnd     = gamepad(startMs = 300L)
        val newEdited = edited.copy(startTimeMs = 200L)            // startDelta=+100, durationDelta=0

        val result = applyShiftSubsequent(
            steps       = listOf(atEnd, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // startDelta=+100, durationDelta=0 → total +100
        assertEquals(400L, result[0].startTimeMs)
    }

    // ── 2. Pure start-time move backward ─────────────────────────────────────

    @Test
    fun `start move backward — subsequent steps shift backward`() {
        val edited    = gamepad(startMs = 500L, durationMs = 100L)
        val after     = gamepad(startMs = 700L)
        val newEdited = edited.copy(startTimeMs = 300L)            // startDelta=-200

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        assertEquals(500L, result[0].startTimeMs) // 700 + (-200) = 500
    }

    @Test
    fun `start move backward — result clamped to 0`() {
        val edited    = gamepad(startMs = 100L, durationMs = 50L)
        val close     = gamepad(startMs = 120L)
        val newEdited = edited.copy(startTimeMs = 0L)              // startDelta=-100

        val result = applyShiftSubsequent(
            steps       = listOf(close, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // 120 + (-100) = 20, but... 20 >= 0, no clamp needed here
        assertEquals(20L, result[0].startTimeMs)
    }

    @Test
    fun `start move backward — clamped at 0 when result would be negative`() {
        val edited    = gamepad(startMs = 500L, durationMs = 100L)
        val close     = gamepad(startMs = 510L)
        val newEdited = edited.copy(startTimeMs = 0L)              // startDelta=-500

        val result = applyShiftSubsequent(
            steps       = listOf(close, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // 510 + (-500) = 10 — still positive
        assertEquals(10L, result[0].startTimeMs)
    }

    @Test
    fun `start move backward — 0 clamp applies when shift makes time negative`() {
        val edited    = gamepad(startMs = 1_000L, durationMs = 100L)
        val early     = gamepad(startMs = 1_000L) // same start, only 1000 ms into timeline
        val newEdited = edited.copy(startTimeMs = 0L)              // startDelta=-1000

        val result = applyShiftSubsequent(
            steps       = listOf(early, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // 1000 + (-1000) = 0 → exactly 0, not clamped
        assertEquals(0L, result[0].startTimeMs)
    }

    // ── 3. Pure duration extend (startDelta = 0) — backward-compat ───────────

    @Test
    fun `duration extend — step before old end is NOT shifted`() {
        val edited    = gamepad(startMs = 200L, durationMs = 200L) // oldEnd=400
        val inner     = gamepad(startMs = 300L)                    // inside span, < oldEnd
        val newEdited = edited.copy(durationMs = 400L)             // startDelta=0, durationDelta=+200

        val result = applyShiftSubsequent(
            steps       = listOf(inner, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // startDelta=0 → no start shift; inner < oldEnd → no duration shift
        assertEquals(300L, result[0].startTimeMs)
    }

    @Test
    fun `duration extend — step at old end shifts by durationDelta`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L) // oldEnd=300
        val atEnd     = gamepad(startMs = 300L)
        val newEdited = edited.copy(durationMs = 400L)             // durationDelta=+200

        val result = applyShiftSubsequent(
            steps       = listOf(atEnd, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        assertEquals(500L, result[0].startTimeMs)
    }

    @Test
    fun `duration shorten — step after old end shifts by negative durationDelta`() {
        val edited    = gamepad(startMs = 0L, durationMs = 500L)   // oldEnd=500
        val after     = gamepad(startMs = 600L)
        val newEdited = edited.copy(durationMs = 200L)             // durationDelta=-300

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        assertEquals(300L, result[0].startTimeMs) // 600 + (-300) = 300
    }

    // ── 4. Move + extend combined ─────────────────────────────────────────────

    @Test
    fun `move forward and extend — concurrent step gets only startDelta`() {
        // edited: start=100, dur=200, end=300
        // new:    start=200, dur=300, end=500  → startDelta=+100, durationDelta=+100
        val edited     = gamepad(startMs = 100L, durationMs = 200L)
        val concurrent = gamepad(startMs = 150L)                   // inside old span
        val newEdited  = edited.copy(startTimeMs = 200L, durationMs = 300L)

        val result = applyShiftSubsequent(
            steps       = listOf(concurrent, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // 150 >= 100 → +startDelta(100) = 250; 150 < 300 (oldEnd) → no durationDelta
        assertEquals(250L, result[0].startTimeMs)
    }

    @Test
    fun `move forward and extend — step after old end gets endDelta (startDelta + durationDelta)`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L) // oldEnd=300
        val after     = gamepad(startMs = 400L)
        val newEdited = edited.copy(startTimeMs = 200L, durationMs = 300L) // endDelta=200

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        // 400 >= 100 → +100; 400 >= 300 → +100; total +200 → 600
        assertEquals(600L, result[0].startTimeMs)
    }

    // ── 5. Step before edited start — never shifts ────────────────────────────

    @Test
    fun `step strictly before edited start is never shifted regardless of deltas`() {
        val edited    = gamepad(startMs = 500L, durationMs = 200L)
        val before    = gamepad(startMs = 200L)
        val newEdited = edited.copy(startTimeMs = 800L, durationMs = 400L)

        val result = applyShiftSubsequent(
            steps       = listOf(before, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        assertEquals(200L, result[0].startTimeMs) // untouched
    }

    // ── 6. No-op cases ────────────────────────────────────────────────────────

    @Test
    fun `no delta at all — list is returned unchanged`() {
        val edited = gamepad(startMs = 100L, durationMs = 200L)
        val other  = gamepad(startMs = 500L)

        val result = applyShiftSubsequent(
            steps       = listOf(other, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = edited, // identical
        )

        assertEquals(500L, result[0].startTimeMs)
    }

    @Test
    fun `edited step itself is replaced with newStep`() {
        val edited    = gamepad(startMs = 100L, durationMs = 200L)
        val newEdited = gamepad(startMs = 300L, durationMs = 100L)

        val result = applyShiftSubsequent(
            steps       = listOf(edited),
            editedIndex = 0,
            oldStep     = edited,
            newStep     = newEdited,
        )

        assertEquals(newEdited, result[0])
    }

    // ── 7. maxTimeMs clamping ─────────────────────────────────────────────────

    @Test
    fun `shifted start is clamped to maxTimeMs`() {
        val edited    = gamepad(startMs = 0L,      durationMs = 100L)
        val after     = gamepad(startMs = 9_900L)
        val newEdited = edited.copy(startTimeMs = 500L)             // startDelta=+500

        val result = applyShiftSubsequent(
            steps       = listOf(after, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            maxTimeMs   = 10_000L,
        )

        // 9900 + 500 = 10400 → clamped to 10000
        assertEquals(10_000L, result[0].startTimeMs)
    }

    @Test
    fun `shifted start is clamped to 0 on large backward shift`() {
        val edited    = gamepad(startMs = 5_000L, durationMs = 100L)
        val near      = gamepad(startMs = 5_050L)
        val newEdited = edited.copy(startTimeMs = 0L)               // startDelta=-5000

        val result = applyShiftSubsequent(
            steps       = listOf(near, edited),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
            maxTimeMs   = 10_000L,
        )

        // 5050 + (-5000) = 50 → not clamped, still valid
        assertEquals(50L, result[0].startTimeMs)
    }

    // ── 8. Multiple steps — only steps >= oldStart affected ──────────────────

    @Test
    fun `multiple steps — only those at or after old start shift`() {
        val edited = gamepad(startMs = 300L, durationMs = 100L) // oldEnd=400
        val s0 = gamepad(startMs = 100L) // before → no shift
        val s1 = gamepad(startMs = 300L) // at oldStart → +startDelta
        val s2 = gamepad(startMs = 350L) // inside span → +startDelta
        val s3 = gamepad(startMs = 400L) // at oldEnd → +startDelta +durationDelta
        val s4 = gamepad(startMs = 600L) // after oldEnd → +startDelta +durationDelta
        val newEdited = edited.copy(startTimeMs = 400L) // startDelta=+100, durationDelta=0

        val result = applyShiftSubsequent(
            steps       = listOf(s0, edited, s1, s2, s3, s4),
            editedIndex = 1,
            oldStep     = edited,
            newStep     = newEdited,
        )

        assertEquals(100L,  result[0].startTimeMs) // s0: no shift
        assertEquals(400L,  result[1].startTimeMs) // edited: newStep
        assertEquals(400L,  result[2].startTimeMs) // s1: 300+100
        assertEquals(450L,  result[3].startTimeMs) // s2: 350+100
        assertEquals(500L,  result[4].startTimeMs) // s3: 400+100 (durationDelta=0)
        assertEquals(700L,  result[5].startTimeMs) // s4: 600+100
    }
}
