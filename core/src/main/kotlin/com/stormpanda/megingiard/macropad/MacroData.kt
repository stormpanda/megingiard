package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
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

    /**
     * Injects a single touch tap at the normalised position ([normX], [normY]) on the
     * primary display, beginning [startTimeMs] milliseconds after macro start, held for
     * [durationMs] milliseconds. Coordinates are in logical display space:
     * 0.0 (left/top) … 1.0 (right/bottom).
     */
    @Serializable
    @SerialName("touch_tap")
    data class TouchTap(
        override val startTimeMs: Long,
        override val durationMs: Long,
        val normX: Float,
        val normY: Float,
    ) : MacroStep()

    /**
     * Replays a recorded analog stick path on [stick], beginning [startTimeMs] milliseconds
     * after macro start. [samples] is a list of raw normalised axis positions
     * (each in [−1.0, 1.0]) with timing offsets relative to [startTimeMs]. On step end
     * the axis is reset to 0. Created exclusively by [PhysicalGamepadRecordingManager].
     */
    @Serializable
    @SerialName("joystick_path")
    data class JoystickPath(
        override val startTimeMs: Long,
        override val durationMs: Long,
        val stick: JoystickStick,
        val samples: List<PathSample>,
    ) : MacroStep()

    /**
     * Replays a recorded continuous multi-touch gesture path, beginning [startTimeMs]
     * milliseconds after macro start. [samples] is a list of relative-time touch pointer
     * actions with normalised logical coordinates [0.0, 1.0].
     */
    @Serializable
    @SerialName("touch_path")
    data class TouchPath(
        override val startTimeMs: Long,
        override val durationMs: Long,
        val samples: List<TouchSample>,
    ) : MacroStep()
}

/**
 * A single analog stick position sample within a [MacroStep.JoystickPath].
 *
 * [offsetMs] is relative to the parent [MacroStep.JoystickPath.startTimeMs].
 * [x] and [y] are normalised axis values in [−1.0, 1.0].
 */
@Serializable
data class PathSample(val offsetMs: Long, val x: Float, val y: Float)

/**
 * A single touch interaction sample within a [MacroStep.TouchPath].
 *
 * [offsetMs] is relative to the parent [MacroStep.TouchPath.startTimeMs].
 * [pointerId] is the unique touch slot (0..9) identifying the finger.
 * [action] is DOWN / MOVE / UP.
 * [normX] and [normY] are normalised coordinates in logical display space [0.0, 1.0].
 */
@Serializable
data class TouchSample(
    val offsetMs: Long,
    val pointerId: Int,
    val action: TouchAction,
    val normX: Float,
    val normY: Float,
)

// ─────────────────────────────────────────────────────────────────────────────
// Extension helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Computes the total macro duration = end time of the last step. */
fun List<MacroStep>.totalDurationMs(): Long = maxOfOrNull { it.endTimeMs() } ?: 0L

/** Returns a copy of this step with [startTimeMs] replaced by [newStartTimeMs]. */
fun MacroStep.withStartTime(newStartTimeMs: Long): MacroStep = when (this) {
    is MacroStep.GamepadButtonTap -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.JoystickMove     -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.DPadTap          -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.TouchTap         -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.JoystickPath     -> copy(startTimeMs = newStartTimeMs)
    is MacroStep.TouchPath        -> copy(startTimeMs = newStartTimeMs)
}

/** Returns a new list where every step's start time is shifted by [offsetMs]. */
fun List<MacroStep>.offsetBy(offsetMs: Long): List<MacroStep> =
    map { step -> step.withStartTime(step.startTimeMs + offsetMs) }

// ─────────────────────────────────────────────────────────────────────────────
// Macro — a named sequence of timed steps
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param id          Stable unique identifier (UUID string).
 * @param name        User-visible name shown in editors and on pad buttons.
 * @param steps       Ordered list of timed steps; steps may overlap for parallel execution.
 * @param loopEnabled When true, the macro replays indefinitely until stopped by a second tap.
 * @param loopPauseMs Milliseconds to pause between loop iterations (0 = no pause). Only used
 *                    when [loopEnabled] is true.
 *
 * The data is exported/imported as a standalone JSON array via [kotlinx.serialization].
 */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val steps: List<MacroStep> = emptyList(),
    val loopEnabled: Boolean = false,
    val loopPauseMs: Int = 0,
)
