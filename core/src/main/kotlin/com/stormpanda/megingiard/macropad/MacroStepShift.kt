package com.stormpanda.megingiard.macropad

// ─────────────────────────────────────────────────────────────────────────────
// ShiftMode + shift-subsequent logic for the macro timeline editor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Controls how subsequent steps are shifted when an existing macro step is edited.
 *
 * The "subsequent" threshold is always `>= oldStep.endTimeMs()`: only steps that start
 * at or after the edited step's old end time are eligible for shifting. Steps that
 * overlap or run concurrently with the edited step are **never** moved in any mode.
 */
enum class ShiftMode {
    /** No other steps are moved. */
    NONE,

    /**
     * Eligible steps are shifted by `newStep.startTimeMs − oldStep.startTimeMs`.
     *
     * Use this when you moved the step's start time and want subsequent steps to
     * follow, preserving their relative distance to the edited step's new start.
     * A pure duration change produces a delta of zero → nothing shifts.
     */
    START_DELTA,

    /**
     * Eligible steps are shifted by `newStep.endTimeMs() − oldStep.endTimeMs()`.
     *
     * Use this when you changed the step's duration (or both start and duration),
     * and want subsequent steps to be pushed out or pulled in to match the new end.
     */
    END_DELTA,
}

/**
 * Applies the shift-subsequent transformation when an existing macro step is edited.
 *
 * @param steps        The full step list before the edit.
 * @param editedIndex  The index of the step being replaced.
 * @param oldStep      The step before the edit.
 * @param newStep      The step after the edit.
 * @param mode         Which delta to apply; [ShiftMode.NONE] skips all shifting.
 * @param maxTimeMs    Upper bound for clamping shifted start times (default 10 000 ms).
 * @return A new list with [newStep] at [editedIndex] and all other steps shifted accordingly.
 */
fun applyShiftSubsequent(
    steps: List<MacroStep>,
    editedIndex: Int,
    oldStep: MacroStep,
    newStep: MacroStep,
    mode: ShiftMode,
    maxTimeMs: Long = DEFAULT_MAX_TIME_MS,
): List<MacroStep> {
    require(steps.getOrNull(editedIndex) == oldStep) {
        "oldStep must match steps[editedIndex]"
    }
    if (mode == ShiftMode.NONE) {
        return steps.toMutableList().also { it[editedIndex] = newStep }
    }

    val delta = when (mode) {
        ShiftMode.START_DELTA -> newStep.startTimeMs - oldStep.startTimeMs
        ShiftMode.END_DELTA   -> newStep.endTimeMs() - oldStep.endTimeMs()
        ShiftMode.NONE        -> 0L // unreachable — handled above
    }
    val oldEnd = oldStep.endTimeMs()

    return steps.mapIndexed { i, step ->
        if (i == editedIndex) return@mapIndexed newStep
        if (delta == 0L || step.startTimeMs < oldEnd) return@mapIndexed step
        step.withStartTime((step.startTimeMs + delta).coerceIn(0L, maxTimeMs))
    }
}

private const val DEFAULT_MAX_TIME_MS = 10_000L
