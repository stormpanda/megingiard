package com.stormpanda.megingiard.macropad

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Scale factor for float axis values (-1..1) → int16 (-32768..32767).
// Using 32768 so -1.0 maps exactly to -32768; positive side is clamped below.
private const val MAC_ABS_FULL_DEFLECTION = 32768

// Wait after starting GamepadInjector before the first event dispatch, in milliseconds.
// start() blocks until the native binary signals "R\n", meaning the uinput fd is opened,
// but Android's InputFlinger discovers the new virtual device asynchronously. Dispatching
// events before InputFlinger registers the device causes them to be silently dropped.
private const val MAC_GAMEPAD_INJECTOR_INIT_MS = 200L

@Suppress("unused")
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
 * A macro button acts as a toggle: a first tap starts execution, a second tap stops it.
 * When [Macro.loopEnabled] is true, the sequence repeats indefinitely until [stop] is called.
 *
 * The internal [scope] is app-lifetime (process-scoped singleton) and is never cancelled.
 */
object MacroExecutor {

    // App-lifetime scope: intentionally never cancelled — lives for the duration of the process.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Stored at app startup so that touch-tap steps can start TouchInjector without
    // requiring the caller to pass a Context through the action-dispatch chain.
    private var appContext: Context? = null

    // One active Job per macro ID; ConcurrentHashMap for thread-safe access from IO + Main.
    private val runningJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    private val _runningMacroIds = MutableStateFlow<Set<String>>(emptySet())

    /** IDs of macros currently executing. Collect in UI to drive button animations. */
    val runningMacroIds: StateFlow<Set<String>> = _runningMacroIds.asStateFlow()

    /**
     * Must be called once from [MainActivity.onCreate] to provide a stable application
     * context used when starting [TouchInjector] for [MacroStep.TouchTap] replay.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        AppLog.d(TAG, "init appContext=${appContext?.packageName}")
    }

    /** Returns true if the macro with [macroId] is currently running. */
    fun isRunning(macroId: String): Boolean = macroId in _runningMacroIds.value

    /**
     * Starts executing [macro]. If the same macro is already running, the previous execution
     * is cancelled before starting a new one (use [stop] directly for toggle-off semantics).
     */
    fun execute(macro: Macro, context: Context? = null) {
        if (macro.steps.isEmpty()) return
        val ctx = context ?: appContext
        // Cancel any existing execution for this macro before launching a new one.
        // Do NOT remove from the map here — the finally block handles removal via a job-identity
        // check, preventing the old job's finally from corrupting the new job's state.
        runningJobs[macro.id]?.cancel()
        AppLog.d(TAG, "execute macro='${macro.name}' id=${macro.id} steps=${macro.steps.size} loop=${macro.loopEnabled}")
        val job = scope.launch { executeSuspend(macro, ctx) }
        runningJobs[macro.id] = job
    }

    /**
     * Stops a running macro by cancelling its coroutine. The [_runningMacroIds] set is
     * cleaned up in the [executeSuspend] finally block once the cancellation propagates.
     */
    fun stop(macroId: String) {
        AppLog.d(TAG, "stop macroId=$macroId")
        // Do NOT remove from the map — let the finally block do it via job-identity check.
        runningJobs[macroId]?.cancel()
    }

    // -------------------------------------------------------------------------
    // Internal execution logic
    // -------------------------------------------------------------------------

    private suspend fun executeSuspend(macro: Macro, context: Context?) {
        // Capture this coroutine's Job for race-safe map cleanup in finally.
        val thisJob = coroutineContext[Job]!!

        // Track every input that is currently "live" so we can release them all if execution
        // is stopped or cancelled mid-sequence. Covers all virtual devices that MacroExecutor
        // can drive: gamepad (buttons, axes, hat) and touch.
        val pressedButtons = mutableSetOf<Int>()
        val activeAxes = mutableMapOf<Int, Int>()    // axis code → last non-zero value
        var liveHatX = 0
        var liveHatY = 0
        var liveTouchPos: Pair<Float, Float>? = null

        _runningMacroIds.update { it + macro.id }
        val events = buildEventList(macro)
        val hasTouchEvents = events.any { it.type == MacroEventType.TOUCH_DOWN || it.type == MacroEventType.TOUCH_UP }
        val hasGamepadEvents = events.any {
            it.type == MacroEventType.BUTTON_DOWN || it.type == MacroEventType.BUTTON_UP ||
            it.type == MacroEventType.JOYSTICK_SET || it.type == MacroEventType.HAT
        }
        var startedGamepad = false
        try {
            // Start injectors that aren't already running.
            // start() blocks until the binary signals readiness ("R\n") before returning,
            // so no additional delay is needed — isRunning is already true when start() returns.
            if (hasTouchEvents && context != null && !TouchInjector.isRunning) {
                AppLog.i(TAG, "macro has touch events → starting TouchInjector")
                TouchInjector.start(context)
            }
            startedGamepad = hasGamepadEvents && context != null && !GamepadInjector.isRunning
            if (startedGamepad) {
                AppLog.i(TAG, "macro has gamepad events → starting GamepadInjector")
                GamepadInjector.start(context!!)
                // InputFlinger discovers the uinput virtual device asynchronously after the binary
                // creates it. Wait for device registration before dispatching the first event.
                delay(MAC_GAMEPAD_INJECTOR_INIT_MS)
            }
            if (hasTouchEvents && !TouchInjector.isRunning) {
                AppLog.w(TAG, "TouchInjector failed to start — touch steps will be skipped")
            }
            if (hasGamepadEvents && !GamepadInjector.isRunning) {
                AppLog.w(TAG, "GamepadInjector failed to start — gamepad steps will be skipped")
            }
            // Execute once, or loop indefinitely if loopEnabled (cancelled by stop()).
            do {
                var currentTimeMs = 0L
                for (event in events) {
                    val waitMs = event.timeMs - currentTimeMs
                    if (waitMs > 0L) delay(waitMs)
                    currentTimeMs = event.timeMs
                    // Dispatch and track live input state for cleanup on cancellation.
                    when (event.type) {
                        MacroEventType.BUTTON_DOWN -> {
                            pressedButtons += event.code
                            GamepadInjector.buttonDown(event.code)
                        }
                        MacroEventType.BUTTON_UP -> {
                            pressedButtons -= event.code
                            GamepadInjector.buttonUp(event.code)
                        }
                        MacroEventType.JOYSTICK_SET -> {
                            if (event.value != 0) activeAxes[event.code] = event.value
                            else activeAxes -= event.code
                            GamepadInjector.joystick(event.code, event.value)
                        }
                        MacroEventType.HAT -> {
                            if (event.code == 0) liveHatX = event.value else liveHatY = event.value
                            GamepadInjector.hat(axis = event.code, value = event.value)
                        }
                        MacroEventType.TOUCH_DOWN -> {
                            liveTouchPos = Pair(event.normX, event.normY)
                            TouchInjector.injectTouch(TouchAction.DOWN, event.normX, event.normY)
                        }
                        MacroEventType.TOUCH_UP -> {
                            liveTouchPos = null
                            TouchInjector.injectTouch(TouchAction.UP, event.normX, event.normY)
                        }
                    }
                }
                AppLog.d(TAG, "macro '${macro.name}' iteration complete (${events.size} events) loop=${macro.loopEnabled}")
                if (macro.loopEnabled && macro.loopPauseMs > 0) {
                    delay(macro.loopPauseMs.toLong())
                }
            } while (macro.loopEnabled)
        } finally {
            AppLog.d(TAG, "macro '${macro.name}' done (buttons=${pressedButtons.size} axes=${activeAxes.size} hat=$liveHatX,$liveHatY touch=${liveTouchPos != null})")
            // Release all inputs that are still active. This handles early cancellation
            // (user taps stop mid-sequence) in addition to the normal end-of-sequence reset.
            pressedButtons.forEach { GamepadInjector.buttonUp(it) }
            activeAxes.keys.forEach { GamepadInjector.joystick(it, 0) }
            if (liveHatX != 0 || liveHatY != 0) {
                GamepadInjector.hat(axis = 0, value = 0)
                GamepadInjector.hat(axis = 1, value = 0)
            }
            liveTouchPos?.let { (x, y) -> TouchInjector.injectTouch(TouchAction.UP, x, y) }
            if (hasTouchEvents) {
                AppLog.i(TAG, "macro done → stopping TouchInjector")
                TouchInjector.stop()
            }
            if (startedGamepad) {
                AppLog.i(TAG, "macro done → stopping GamepadInjector")
                GamepadInjector.stop()
            }
            // Race-safe state cleanup: only remove our map entry and running-ID if this job is
            // still the one registered for this macro. A rapid execute()→execute() restart
            // replaces the map entry before our finally runs — we must not remove the newer
            // job or clear its running indicator.
            if (runningJobs.remove(macro.id, thisJob)) {
                _runningMacroIds.update { it - macro.id }
            }
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

}
