package com.stormpanda.megingiard.macropad

// ─────────────────────────────────────────────────────────────────────────────
// Shift-subsequent logic for the macro timeline editor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Applies the "shift subsequent steps" transformation when an existing step in a macro
 * timeline is edited.
 *
 * ### Shift rules (applied simultaneously; [maxTimeMs] clamps results to [0, maxTimeMs]):
 *
 * - Steps that start **before** the edited step's old start time are **never** shifted.
 * - Steps that start at **or after** the edited step's old start time gain [startDelta]
 *   (the amount the edited step's own start time changed).
 * - Steps that start at **or after** the edited step's **old end time** additionally gain
 *   [durationDelta] (the amount the edited step's duration changed).
 *
 * Combined: a step at or after the old end gets `startDelta + durationDelta = endDelta`,
 * which preserves the existing end-time-based shift behaviour when [startDelta] is zero.
 *
 * @param steps        The full step list before the edit.
 * @param editedIndex  The index of the step being replaced.
 * @param oldStep      The step before the edit.
 * @param newStep      The step after the edit (already replaces [editedIndex]).
 * @param maxTimeMs    Upper bound for clamping shifted start times (default 10 000 ms).
 * @return A new list with [newStep] at [editedIndex] and all other steps shifted accordingly.
 */
fun applyShiftSubsequent(
    steps: List<MacroStep>,
    editedIndex: Int,
    oldStep: MacroStep,
    newStep: MacroStep,
    maxTimeMs: Long = DEFAULT_MAX_TIME_MS,
): List<MacroStep> {
    val startDelta    = newStep.startTimeMs - oldStep.startTimeMs
    val durationDelta = newStep.durationMs  - oldStep.durationMs
    val oldEnd        = oldStep.endTimeMs()

    return steps.mapIndexed { i, step ->
        if (i == editedIndex) return@mapIndexed newStep

        var shift = 0L
        if (startDelta != 0L && step.startTimeMs >= oldStep.startTimeMs) shift += startDelta
        if (durationDelta != 0L && step.startTimeMs >= oldEnd)           shift += durationDelta

        if (shift == 0L) step
        else step.withStartTime((step.startTimeMs + shift).coerceIn(0L, maxTimeMs))
    }
}

private const val DEFAULT_MAX_TIME_MS = 10_000L
