package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction

// Scale factor for float axis values (-1..1) → int16 (-32768..32767).
// Using 32768 so -1.0 maps exactly to -32768; positive side is clamped to 32767 below.
private const val ABS_FULL_DEFLECTION = 32768

/** Event types emitted by [buildMacroEventList]. */
enum class MacroEventType { BUTTON_DOWN, BUTTON_UP, JOYSTICK_SET, HAT, TOUCH_DOWN, TOUCH_MOVE, TOUCH_UP }

/**
 * A single discrete input event compiled from a [MacroStep].
 *
 * [normX] and [normY] carry normalised coordinates for [MacroEventType.TOUCH_DOWN] /
 * [MacroEventType.TOUCH_MOVE] / [MacroEventType.TOUCH_UP]; all other event types leave
 * them at their default 0f.
 */
data class MacroEvent(
    val timeMs: Long,
    val type: MacroEventType,
    val code: Int,
    val value: Int,
    val normX: Float = 0f,
    val normY: Float = 0f,
)

/**
 * "Reset" events (UP, axis-to-zero) must be dispatched before "set" events at the same
 * timestamp so that one step ending exactly as another begins produces the correct device
 * state rather than a stale latch.
 */
val MacroEvent.isReset: Boolean
    get() = when (type) {
        MacroEventType.BUTTON_UP             -> true
        MacroEventType.TOUCH_UP              -> true
        MacroEventType.BUTTON_DOWN           -> false
        MacroEventType.TOUCH_MOVE            -> false
        MacroEventType.TOUCH_DOWN            -> false
        MacroEventType.JOYSTICK_SET,
        MacroEventType.HAT                   -> value == 0
    }

/**
 * Compiles the overlapping [MacroStep] list of [macro] into a flat, time-sorted list of
 * [MacroEvent]s ready for sequential dispatch by `MacroExecutor`.
 *
 * This is a **pure function** with no Android dependencies — it can be unit-tested in the
 * `:core` JVM test source set without any device or framework mocking.
 */
fun buildMacroEventList(macro: Macro): List<MacroEvent> {
    val events = mutableListOf<MacroEvent>()
    for (step in macro.steps) {
        when (step) {
            is MacroStep.GamepadButtonTap -> {
                events += MacroEvent(step.startTimeMs, MacroEventType.BUTTON_DOWN, step.btnCode, 0)
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.BUTTON_UP, step.btnCode, 0)
            }
            is MacroStep.JoystickMove -> {
                val rawX = (step.x.coerceIn(-1f, 1f) * ABS_FULL_DEFLECTION).toInt().coerceIn(-32768, 32767)
                val rawY = (step.y.coerceIn(-1f, 1f) * ABS_FULL_DEFLECTION).toInt().coerceIn(-32768, 32767)
                val axisX = if (step.stick == JoystickStick.LEFT) GamepadKeycodes.ABS_X else GamepadKeycodes.ABS_Z
                val axisY = if (step.stick == JoystickStick.LEFT) GamepadKeycodes.ABS_Y else GamepadKeycodes.ABS_RZ
                events += MacroEvent(step.startTimeMs, MacroEventType.JOYSTICK_SET, axisX, rawX)
                events += MacroEvent(step.startTimeMs, MacroEventType.JOYSTICK_SET, axisY, rawY)
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.JOYSTICK_SET, axisX, 0)
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.JOYSTICK_SET, axisY, 0)
            }
            is MacroStep.DPadTap -> {
                events += MacroEvent(step.startTimeMs, MacroEventType.HAT, 0, step.dirX)
                events += MacroEvent(step.startTimeMs, MacroEventType.HAT, 1, step.dirY)
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.HAT, 0, 0)
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.HAT, 1, 0)
            }
            is MacroStep.TouchTap -> {
                events += MacroEvent(step.startTimeMs, MacroEventType.TOUCH_DOWN, 0, 0, step.normX, step.normY)
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.TOUCH_UP, 0, 0, step.normX, step.normY)
            }
            is MacroStep.JoystickPath -> {
                val axisX = if (step.stick == JoystickStick.LEFT) GamepadKeycodes.ABS_X else GamepadKeycodes.ABS_Z
                val axisY = if (step.stick == JoystickStick.LEFT) GamepadKeycodes.ABS_Y else GamepadKeycodes.ABS_RZ
                for (sample in step.samples) {
                    /* Defensive: skip samples whose offset reaches or exceeds the step's duration
                       — they would otherwise land at the same global timestamp as the
                       end-of-step neutral reset and the sort order (resets first at equal
                       timestamps) would leave the stick non-neutral at step end. The
                       recorder is also responsible for ensuring durationMs > maxOffsetMs;
                       this guard keeps the compiler robust against data on disk that may
                       not honour that invariant. */
                    if (sample.offsetMs >= step.durationMs) continue
                    val rawX = (sample.x.coerceIn(-1f, 1f) * ABS_FULL_DEFLECTION).toInt().coerceIn(-32768, 32767)
                    val rawY = (sample.y.coerceIn(-1f, 1f) * ABS_FULL_DEFLECTION).toInt().coerceIn(-32768, 32767)
                    val t = step.startTimeMs + sample.offsetMs
                    events += MacroEvent(t, MacroEventType.JOYSTICK_SET, axisX, rawX)
                    events += MacroEvent(t, MacroEventType.JOYSTICK_SET, axisY, rawY)
                }
                /* Return axes to neutral at step end. */
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.JOYSTICK_SET, axisX, 0)
                events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.JOYSTICK_SET, axisY, 0)
            }
            is MacroStep.TouchPath -> {
                val activePointers = mutableMapOf<Int, Pair<Float, Float>>()
                for (sample in step.samples) {
                    if (sample.offsetMs > step.durationMs) continue
                    val t = step.startTimeMs + sample.offsetMs
                    val eventType = when (sample.action) {
                        TouchAction.DOWN -> {
                            activePointers[sample.pointerId] = Pair(sample.normX, sample.normY)
                            MacroEventType.TOUCH_DOWN
                        }
                        TouchAction.MOVE -> {
                            activePointers[sample.pointerId] = Pair(sample.normX, sample.normY)
                            MacroEventType.TOUCH_MOVE
                        }
                        TouchAction.UP -> {
                            activePointers -= sample.pointerId
                            MacroEventType.TOUCH_UP
                        }
                    }
                    events += MacroEvent(t, eventType, sample.pointerId, 0, sample.normX, sample.normY)
                }
                /* Emit synthetic TOUCH_UP for any pointer still active at step end
                   (e.g. the UP was filtered because durationMs was shortened in the editor). */
                val stepEndMs = step.startTimeMs + step.durationMs
                for ((pointerId, pos) in activePointers) {
                    events += MacroEvent(stepEndMs, MacroEventType.TOUCH_UP, pointerId, 0, pos.first, pos.second)
                }
            }
        }
    }
    events.sortWith(compareBy({ it.timeMs }, { if (it.isReset) 0 else 1 }))
    return events
}
