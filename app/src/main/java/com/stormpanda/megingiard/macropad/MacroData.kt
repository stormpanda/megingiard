package com.stormpanda.megingiard.macropad

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Joystick stick selector
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
enum class JoystickStick { LEFT, RIGHT }

// ─────────────────────────────────────────────────────────────────────────────
// Macro step — a single timed action within a Macro sequence
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A timed action within a [Macro]. All subtypes share [startTimeMs] and [durationMs]
 * so that overlapping steps can be compiled into a flat event list by [MacroExecutor].
 *
 * Steps are serialised with [kotlinx.serialization] using [SerialName] discriminators
 * to allow future step types (keyboard, mouse) to be added without breaking existing data.
 */
@Serializable
sealed class MacroStep {

    abstract val startTimeMs: Long
    abstract val durationMs: Long

    /** Returns the absolute end timestamp (startTimeMs + durationMs). */
    fun endTimeMs(): Long = startTimeMs + durationMs

    // ── Step subtypes ─────────────────────────────────────────────────────────

    /**
     * Presses and holds the gamepad button [btnCode] for [durationMs] milliseconds,
     * beginning [startTimeMs] milliseconds after macro start.
     */
    @Serializable
    @SerialName("gamepad_button_tap")
    data class GamepadButtonTap(
        override val startTimeMs: Long,
        override val durationMs: Long,
        val btnCode: Int,
        val label: String,
    ) : MacroStep()

    /**
     * Moves [stick] to position ([x], [y]) for [durationMs] milliseconds,
     * beginning [startTimeMs] milliseconds after macro start.
     * [x] and [y] are normalised in [−1.0, 1.0]; 1.0 = full deflection.
     * On step end the axis is reset to 0.
     */
    @Serializable
    @SerialName("joystick_move")
    data class JoystickMove(
        override val startTimeMs: Long,
        override val durationMs: Long,
        val stick: JoystickStick,
        val x: Float,
        val y: Float,
    ) : MacroStep()

    /**
     * Holds the D-Pad in direction ([dirX], [dirY]) for [durationMs] milliseconds,
     * beginning [startTimeMs] milliseconds after macro start.
     * [dirX] and [dirY] are integers in {−1, 0, +1}.
     * On step end the hat axes are reset to 0.
     */
    @Serializable
    @SerialName("dpad_tap")
    data class DPadTap(
        override val startTimeMs: Long,
        override val durationMs: Long,
        val dirX: Int,
        val dirY: Int,
    ) : MacroStep()
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Computes the total macro duration = end time of the last step. */
fun List<MacroStep>.totalDurationMs(): Long = maxOfOrNull { it.endTimeMs() } ?: 0L

// ─────────────────────────────────────────────────────────────────────────────
// Macro — a named sequence of timed steps
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param id    Stable unique identifier (UUID string).
 * @param name  User-visible name shown in editors and on pad buttons.
 * @param steps Ordered list of timed steps; steps may overlap for parallel execution.
 *
 * The data is exported/imported as a standalone JSON array via [kotlinx.serialization].
 */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val steps: List<MacroStep> = emptyList(),
)
