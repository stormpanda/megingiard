package com.stormpanda.megingiard.macropad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAC_ABS_FULL_DEFLECTION = 32767

private enum class MacroEventType { BUTTON_DOWN, BUTTON_UP, JOYSTICK_SET, HAT }

private data class MacroEvent(
    val timeMs: Long,
    val type: MacroEventType,
    val code: Int,
    val value: Int,
)

/**
 * Executes [Macro] sequences by compiling their overlapping [MacroStep] list into a
 * flat, time-sorted event list and replaying it with coroutine [delay]s.
 *
 * Execution is fire-and-forget: each [execute] call launches a new coroutine on
 * [Dispatchers.IO] and returns immediately. Multiple macros can run concurrently.
 *
 * The internal [scope] is app-lifetime (process-scoped singleton) and is never cancelled.
 */
object MacroExecutor {

    // App-lifetime scope: intentionally never cancelled — lives for the duration of the process.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun execute(macro: Macro) {
        if (macro.steps.isEmpty()) return
        scope.launch { executeSuspend(macro) }
    }

    // -------------------------------------------------------------------------
    // Internal execution logic
    // -------------------------------------------------------------------------

    private suspend fun executeSuspend(macro: Macro) {
        val events = buildEventList(macro)
        var currentTimeMs = 0L
        for (event in events) {
            val waitMs = event.timeMs - currentTimeMs
            if (waitMs > 0L) delay(waitMs)
            currentTimeMs = event.timeMs
            dispatch(event)
        }
    }

    private fun buildEventList(macro: Macro): List<MacroEvent> {
        val events = mutableListOf<MacroEvent>()
        for (step in macro.steps) {
            when (step) {
                is MacroStep.GamepadButtonTap -> {
                    events += MacroEvent(step.startTimeMs,               MacroEventType.BUTTON_DOWN, step.btnCode, 0)
                    events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.BUTTON_UP, step.btnCode, 0)
                }
                is MacroStep.JoystickMove -> {
                    val rawX = (step.x.coerceIn(-1f, 1f) * MAC_ABS_FULL_DEFLECTION).toInt()
                    val rawY = (step.y.coerceIn(-1f, 1f) * MAC_ABS_FULL_DEFLECTION).toInt()
                    val axisX = if (step.stick == JoystickStick.LEFT) GamepadKeycodes.ABS_X else GamepadKeycodes.ABS_Z
                    val axisY = if (step.stick == JoystickStick.LEFT) GamepadKeycodes.ABS_Y else GamepadKeycodes.ABS_RZ
                    events += MacroEvent(step.startTimeMs,               MacroEventType.JOYSTICK_SET, axisX, rawX)
                    events += MacroEvent(step.startTimeMs,               MacroEventType.JOYSTICK_SET, axisY, rawY)
                    events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.JOYSTICK_SET, axisX, 0)
                    events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.JOYSTICK_SET, axisY, 0)
                }
                is MacroStep.DPadTap -> {
                    events += MacroEvent(step.startTimeMs,               MacroEventType.HAT, 0, step.dirX)
                    events += MacroEvent(step.startTimeMs,               MacroEventType.HAT, 1, step.dirY)
                    events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.HAT, 0, 0)
                    events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.HAT, 1, 0)
                }
            }
        }
        events.sortBy { it.timeMs }
        return events
    }

    private fun dispatch(event: MacroEvent) {
        when (event.type) {
            MacroEventType.BUTTON_DOWN  -> GamepadInjector.buttonDown(event.code)
            MacroEventType.BUTTON_UP    -> GamepadInjector.buttonUp(event.code)
            MacroEventType.JOYSTICK_SET -> GamepadInjector.joystick(event.code, event.value)
            MacroEventType.HAT          -> GamepadInjector.hat(axis = event.code, value = event.value)
        }
    }
}
