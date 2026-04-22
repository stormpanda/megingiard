package com.stormpanda.megingiard.macropad

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Scale factor for float axis values (-1..1) → int16 (-32768..32767).
// Using 32768 so -1.0 maps exactly to -32768; positive side is clamped below.
private const val MAC_ABS_FULL_DEFLECTION = 32768

// Wait after starting TouchInjector before the first injection, in milliseconds.
private const val MAC_TOUCH_INJECTOR_INIT_MS = 200L

private const val TAG = "MacroExecutor"

private enum class MacroEventType { BUTTON_DOWN, BUTTON_UP, JOYSTICK_SET, HAT, TOUCH_DOWN, TOUCH_UP }

private data class MacroEvent(
    val timeMs: Long,
    val type: MacroEventType,
    val code: Int,
    val value: Int,
    val normX: Float = 0f,
    val normY: Float = 0f,
)

// "Reset" events (BUTTON_UP, JOYSTICK_SET to 0, HAT to 0) should be dispatched
// before "set" events that share the same timestamp, to avoid incorrect final state
// when one step ends exactly as another begins.
private val MacroEvent.isReset: Boolean get() = when (type) {
    MacroEventType.BUTTON_UP   -> true
    MacroEventType.TOUCH_UP    -> true
    MacroEventType.BUTTON_DOWN -> false
    MacroEventType.TOUCH_DOWN  -> false
    MacroEventType.JOYSTICK_SET, MacroEventType.HAT -> value == 0
}

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

    // Stored at app startup so that touch-tap steps can start TouchInjector without
    // requiring the caller to pass a Context through the action-dispatch chain.
    private var appContext: Context? = null

    /**
     * Must be called once from [MainActivity.onCreate] to provide a stable application
     * context used when starting [TouchInjector] for [MacroStep.TouchTap] replay.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        AppLog.d(TAG, "init appContext=${appContext?.packageName}")
    }

    fun execute(macro: Macro, context: Context? = null) {
        if (macro.steps.isEmpty()) return
        val ctx = context ?: appContext
        AppLog.d(TAG, "execute macro='${macro.name}' id=${macro.id} steps=${macro.steps.size}")
        scope.launch { executeSuspend(macro, ctx) }
    }

    // -------------------------------------------------------------------------
    // Internal execution logic
    // -------------------------------------------------------------------------

    private suspend fun executeSuspend(macro: Macro, context: Context?) {
        val events = buildEventList(macro)
        val hasTouchEvents = events.any { it.type == MacroEventType.TOUCH_DOWN || it.type == MacroEventType.TOUCH_UP }
        if (hasTouchEvents && context != null && !TouchInjector.isRunning) {
            AppLog.i(TAG, "macro has touch events → starting TouchInjector")
            TouchInjector.start(context)
            // Allow the injector process to initialise before first injection.
            delay(MAC_TOUCH_INJECTOR_INIT_MS)
        }
        var currentTimeMs = 0L
        for (event in events) {
            val waitMs = event.timeMs - currentTimeMs
            if (waitMs > 0L) delay(waitMs)
            currentTimeMs = event.timeMs
            dispatch(event)
        }
        AppLog.d(TAG, "macro '${macro.name}' complete (${events.size} events)")
        if (hasTouchEvents) {
            AppLog.i(TAG, "macro done → stopping TouchInjector")
            TouchInjector.stop()
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
                    val rawX = (step.x.coerceIn(-1f, 1f) * MAC_ABS_FULL_DEFLECTION).toInt().coerceIn(-32768, 32767)
                    val rawY = (step.y.coerceIn(-1f, 1f) * MAC_ABS_FULL_DEFLECTION).toInt().coerceIn(-32768, 32767)
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
                is MacroStep.TouchTap -> {
                    events += MacroEvent(step.startTimeMs,                    MacroEventType.TOUCH_DOWN, 0, 0, step.normX, step.normY)
                    events += MacroEvent(step.startTimeMs + step.durationMs, MacroEventType.TOUCH_UP,   0, 0, step.normX, step.normY)
                }
            }
        }
        events.sortWith(compareBy({ it.timeMs }, { if (it.isReset) 0 else 1 }))
        return events
    }

    private fun dispatch(event: MacroEvent) {
        when (event.type) {
            MacroEventType.BUTTON_DOWN  -> GamepadInjector.buttonDown(event.code)
            MacroEventType.BUTTON_UP    -> GamepadInjector.buttonUp(event.code)
            MacroEventType.JOYSTICK_SET -> GamepadInjector.joystick(event.code, event.value)
            MacroEventType.HAT          -> GamepadInjector.hat(axis = event.code, value = event.value)
            MacroEventType.TOUCH_DOWN   -> TouchInjector.injectTouch(TouchAction.DOWN, event.normX, event.normY)
            MacroEventType.TOUCH_UP     -> TouchInjector.injectTouch(TouchAction.UP,   event.normX, event.normY)
        }
    }
}
