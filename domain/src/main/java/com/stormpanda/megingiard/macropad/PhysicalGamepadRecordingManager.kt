package com.stormpanda.megingiard.macropad

import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.EvdevEvent
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdGamepadInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PhysGamepadRecManager"

/** Linux input event types used for evdev filtering. */
private const val EV_KEY = 1
private const val EV_ABS = 3

/** D-Pad hat axis codes (not in GamepadKeycodes — BTN_ range only). */
private const val ABS_HAT0X = 16
private const val ABS_HAT0Y = 17

/** Normalised dead zone radius. Stick movements below this magnitude do not start a gesture. */
// Dead zones are now per-stick, read from MacroPadSettings.deadzoneLeft / deadzoneRight.
// The constant is kept as a documentation anchor but is no longer used directly.
@Suppress("unused")
private const val JOYSTICK_DEAD_ZONE_DEFAULT = 0.15f

/**
 * Records buttons, D-Pad taps, and analog stick gestures from the physical gamepad
 * via the `megingiard_privd`
 * daemon's evdev subscription (`SUB GAMEPAD` / `UNSUB GAMEPAD`).
 *
 * **Recording flow:**
 * 1. [startRecording] — suppresses injection via [PrivdGamepadInjector.isRecordingActive],
 *    sends `SUB GAMEPAD\n` to the daemon, and launches a coroutine that collects
 *    [PrivdClient.evdevEvents].
 * 2. Evdev events are translated to [GamepadRecordingState.Recording] state updates;
 *    button, D-Pad, and per-stick [PathSample] data are accumulated as macro steps.
 * 3. [finishRecording] — closes open button / D-Pad / stick gestures, sends
 *    `UNSUB GAMEPAD\n`, restores injection, and emits [GamepadRecordingState.Done].
 * 4. [cancelRecording] — tears down without emitting steps.
 *
 * The daemon only reads the physical evdev stream; it does not take an exclusive grab,
 * so physical input continues to reach Android and the target game while the macro is
 * recorded.
 *
 * **Coroutine scope:** app-lifetime [SupervisorJob] on [Dispatchers.Default].
 * The scope is never cancelled — only the per-session collector [Job] is cancelled.
 *
 * @see GamepadRecordingState — same sealed interface as [GamepadRecordingManager]
 */
object PhysicalGamepadRecordingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<GamepadRecordingState>(GamepadRecordingState.Idle)
    val state: StateFlow<GamepadRecordingState> = _state.asStateFlow()

    @Volatile private var recordingStartEpochMs: Long = 0L
    @Volatile private var collectorJob: Job? = null

    /* Per-stick gesture tracking */
    private val leftSamples = mutableListOf<PathSample>()
    private val rightSamples = mutableListOf<PathSample>()
    private var leftInGesture = false
    private var rightInGesture = false
    private var leftGestureStartMs = 0L
    private var rightGestureStartMs = 0L

    /* Latest raw axis values — held so we can close open gestures at stop time */
    private var leftX = 0f
    private var leftY = 0f
    private var rightX = 0f
    private var rightY = 0f

    /* Steps collected from closed gestures during the session */
    private val recordedSteps = mutableListOf<MacroStep>()

    /* Latest recording state snapshot — mutated as events arrive and emitted */
    private val pendingButtonDowns = mutableMapOf<Int, Long>()
    private var pressedButtons: MutableSet<Int> = mutableSetOf()
    private var hatStartMs: Long? = null
    private var dpadX = 0
    private var dpadY = 0

    /**
     * Starts a physical recording session. No-op if a session is already active.
     *
     * Prerequisites: [PrivdClient.isConnected] must be true.
     */
    fun startRecording() {
        if (_state.value is GamepadRecordingState.Recording) {
            AppLog.w(TAG, "startRecording() called while already recording — ignored")
            return
        }
        AppLog.i(TAG, "startRecording()")
        resetInternalState()
        PrivdGamepadInjector.isRecordingActive = true
        PrivdClient.send("SUB GAMEPAD\n")
        beginRecordingSession(startElapsedMs = SystemClock.elapsedRealtime())

        collectorJob = scope.launch {
            PrivdClient.evdevEvents.collect { event ->
                recordEvdevEvent(event = event, nowElapsedMs = SystemClock.elapsedRealtime())
            }
        }
    }

    /**
     * Stops recording, closes any open gestures, and emits [GamepadRecordingState.Done].
     */
    fun finishRecording() {
        val currentState = _state.value
        if (currentState !is GamepadRecordingState.Recording) {
            AppLog.w(TAG, "finishRecording() called while not recording — ignored")
            return
        }
        AppLog.i(TAG, "finishRecording() — ${recordedSteps.size} closed gestures so far")

        collectorJob?.cancel()
        collectorJob = null

        val steps = completeRecordingAt(stopElapsedMs = SystemClock.elapsedRealtime())

        PrivdClient.send("UNSUB GAMEPAD\n")
        PrivdGamepadInjector.isRecordingActive = false

        AppLog.d(TAG, "finishRecording() done: ${steps.size} steps")
        _state.value = GamepadRecordingState.Done(steps)
    }

    /**
     * Cancels the recording session without emitting any steps.
     */
    fun cancelRecording() {
        AppLog.i(TAG, "cancelRecording()")
        collectorJob?.cancel()
        collectorJob = null
        PrivdClient.send("UNSUB GAMEPAD\n")
        PrivdGamepadInjector.isRecordingActive = false
        _state.value = GamepadRecordingState.Idle
    }

    /** Resets [state] to [GamepadRecordingState.Idle]. Call after consuming a [GamepadRecordingState.Done]. */
    fun resetState() {
        AppLog.d(TAG, "resetState()")
        _state.value = GamepadRecordingState.Idle
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    internal fun startRecordingForTest(startElapsedMs: Long) {
        resetInternalState()
        beginRecordingSession(startElapsedMs = startElapsedMs)
    }

    internal fun finishRecordingForTest(stopElapsedMs: Long): List<MacroStep> {
        val steps = completeRecordingAt(stopElapsedMs = stopElapsedMs)
        _state.value = GamepadRecordingState.Done(steps)
        return steps
    }

    internal fun recordEvdevEvent(event: EvdevEvent, nowElapsedMs: Long) {
        if (_state.value !is GamepadRecordingState.Recording) return
        when (event.type) {
            EV_KEY -> handleKeyEvent(code = event.code, value = event.value, nowMs = relativeElapsedMs(nowElapsedMs))
            EV_ABS -> handleAbsEvent(code = event.code, rawValue = event.value, nowMs = nowElapsedMs)
        }
        /* Update Recording state snapshot after each event for UI reactivity. */
        val current = _state.value as? GamepadRecordingState.Recording ?: return
        _state.value = current.copy(
            pressedButtons = pressedButtons.toSet(),
            dpadDirectionX = dpadX,
            dpadDirectionY = dpadY,
            leftStickX = leftX,
            leftStickY = leftY,
            rightStickX = rightX,
            rightStickY = rightY,
        )
    }

    private fun beginRecordingSession(startElapsedMs: Long) {
        recordingStartEpochMs = startElapsedMs
        _state.value = GamepadRecordingState.Recording(
            pressedButtons = emptySet(),
            dpadDirectionX = 0,
            dpadDirectionY = 0,
            leftStickX = 0f,
            leftStickY = 0f,
            rightStickX = 0f,
            rightStickY = 0f,
        )
    }

    private fun completeRecordingAt(stopElapsedMs: Long): List<MacroStep> {
        val stopRelativeMs = relativeElapsedMs(stopElapsedMs)
        flushOpenButtonAndHatSteps(stopRelativeMs)
        closeOpenGestures(stopElapsedMs)
        return trimLeadingIdle(recordedSteps.sortedBy { it.startTimeMs })
    }

    private fun handleKeyEvent(code: Int, value: Int, nowMs: Long) {
        when (value) {
            1 -> {
                if (pendingButtonDowns.containsKey(code)) return
                pendingButtonDowns[code] = nowMs
                pressedButtons.add(code)
                AppLog.d(TAG, "button down code=$code at=$nowMs")
            }
            0 -> {
                val startMs = pendingButtonDowns.remove(code) ?: return
                pressedButtons.remove(code)
                emitButtonStep(code = code, startMs = startMs, endMs = nowMs)
            }
        }
    }

    private fun handleAbsEvent(code: Int, rawValue: Int, nowMs: Long) {
        when (code) {
            ABS_HAT0X -> updateHat(nowMs = relativeElapsedMs(nowMs), nextX = rawValue, nextY = dpadY)
            ABS_HAT0Y -> updateHat(nowMs = relativeElapsedMs(nowMs), nextX = dpadX, nextY = rawValue)
            GamepadKeycodes.ABS_X -> {
                leftX = (rawValue / 32767f).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.LEFT, x = leftX, y = leftY, nowMs = nowMs)
            }
            GamepadKeycodes.ABS_Y -> {
                leftY = (rawValue / 32767f).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.LEFT, x = leftX, y = leftY, nowMs = nowMs)
            }
            GamepadKeycodes.ABS_Z -> {
                rightX = (rawValue / 32767f).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.RIGHT, x = rightX, y = rightY, nowMs = nowMs)
            }
            GamepadKeycodes.ABS_RZ -> {
                rightY = (rawValue / 32767f).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.RIGHT, x = rightX, y = rightY, nowMs = nowMs)
            }
        }
    }

    private fun emitButtonStep(code: Int, startMs: Long, endMs: Long) {
        val durationMs = (endMs - startMs).coerceAtLeast(1L)
        val label = GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: "BTN $code"
        recordedSteps.add(
            MacroStep.GamepadButtonTap(
                startTimeMs = startMs,
                durationMs = durationMs,
                btnCode = code,
                label = label,
            ),
        )
        AppLog.d(TAG, "button tap code=$code start=$startMs duration=$durationMs")
    }

    private fun updateHat(nowMs: Long, nextX: Int, nextY: Int) {
        val normalizedX = nextX.coerceIn(-1, 1)
        val normalizedY = nextY.coerceIn(-1, 1)
        val previousX = dpadX
        val previousY = dpadY
        if (previousX == normalizedX && previousY == normalizedY) return

        val wasNeutral = previousX == 0 && previousY == 0
        val isNeutral = normalizedX == 0 && normalizedY == 0
        val localHatStartMs = hatStartMs

        if (!wasNeutral && localHatStartMs != null) {
            emitHatStep(startMs = localHatStartMs, endMs = nowMs, dirX = previousX, dirY = previousY)
        }

        dpadX = normalizedX
        dpadY = normalizedY
        hatStartMs = if (isNeutral) null else nowMs
    }

    private fun emitHatStep(startMs: Long, endMs: Long, dirX: Int, dirY: Int) {
        val durationMs = (endMs - startMs).coerceAtLeast(1L)
        recordedSteps.add(
            MacroStep.DPadTap(
                startTimeMs = startMs,
                durationMs = durationMs,
                dirX = dirX,
                dirY = dirY,
            ),
        )
        AppLog.d(TAG, "dpad step dirX=$dirX dirY=$dirY start=$startMs duration=$durationMs")
    }

    private fun updateStick(stick: JoystickStick, x: Float, y: Float, nowMs: Long) {
        val mag = kotlin.math.sqrt(x * x + y * y)
        val deadZone = if (stick == JoystickStick.LEFT)
            MacroPadSettings.deadzoneLeft.value
        else
            MacroPadSettings.deadzoneRight.value
        val samples = if (stick == JoystickStick.LEFT) leftSamples else rightSamples
        val inGesture = if (stick == JoystickStick.LEFT) leftInGesture else rightInGesture
        val gestureStartMs = if (stick == JoystickStick.LEFT) leftGestureStartMs else rightGestureStartMs

        if (!inGesture) {
            if (mag > deadZone) {
                /* Stick left dead zone — begin new gesture. */
                val start = nowMs
                samples.clear()
                samples.add(PathSample(offsetMs = 0L, x = x, y = y))
                if (stick == JoystickStick.LEFT) {
                    leftInGesture = true
                    leftGestureStartMs = start
                } else {
                    rightInGesture = true
                    rightGestureStartMs = start
                }
                AppLog.d(TAG, "gesture start stick=$stick at ${nowMs - recordingStartEpochMs} ms")
            }
        } else {
            /* Gesture in progress — accumulate sample. */
            val offsetMs = nowMs - gestureStartMs
            samples.add(PathSample(offsetMs = offsetMs, x = x, y = y))

            if (mag <= deadZone) {
                /* Stick returned to neutral — close gesture. */
                val durationMs = offsetMs.coerceAtLeast(1L)
                val step = MacroStep.JoystickPath(
                    startTimeMs = gestureStartMs - recordingStartEpochMs,
                    durationMs = durationMs,
                    stick = stick,
                    samples = samples.toList(),
                )
                recordedSteps.add(step)
                samples.clear()
                if (stick == JoystickStick.LEFT) {
                    leftInGesture = false
                } else {
                    rightInGesture = false
                }
                AppLog.d(TAG, "gesture closed stick=$stick samples=${step.samples.size}")
            }
        }
    }

    /**
     * Forcibly closes any open gesture at [stopMs], using the last known stick position.
     * Called from [finishRecording] so that gestures are not silently dropped if the user
     * stops recording with a stick still deflected.
     */
    private fun closeOpenGestures(stopMs: Long) {
        if (leftInGesture && leftSamples.isNotEmpty()) {
            val durationMs = (stopMs - leftGestureStartMs).coerceAtLeast(1L)
            recordedSteps.add(
                MacroStep.JoystickPath(
                    startTimeMs = leftGestureStartMs - recordingStartEpochMs,
                    durationMs = durationMs,
                    stick = JoystickStick.LEFT,
                    samples = leftSamples.toList(),
                ),
            )
            leftInGesture = false
            AppLog.d(TAG, "closeOpenGesture: left stick forced-closed samples=${leftSamples.size}")
        }
        if (rightInGesture && rightSamples.isNotEmpty()) {
            val durationMs = (stopMs - rightGestureStartMs).coerceAtLeast(1L)
            recordedSteps.add(
                MacroStep.JoystickPath(
                    startTimeMs = rightGestureStartMs - recordingStartEpochMs,
                    durationMs = durationMs,
                    stick = JoystickStick.RIGHT,
                    samples = rightSamples.toList(),
                ),
            )
            rightInGesture = false
            AppLog.d(TAG, "closeOpenGesture: right stick forced-closed samples=${rightSamples.size}")
        }
    }

    private fun flushOpenButtonAndHatSteps(stopMs: Long) {
        pendingButtonDowns.toMap().forEach { (code, startMs) ->
            emitButtonStep(code = code, startMs = startMs, endMs = stopMs)
        }
        pendingButtonDowns.clear()
        pressedButtons.clear()

        val localHatStartMs = hatStartMs
        if (localHatStartMs != null && (dpadX != 0 || dpadY != 0)) {
            emitHatStep(startMs = localHatStartMs, endMs = stopMs, dirX = dpadX, dirY = dpadY)
        }
        hatStartMs = null
        dpadX = 0
        dpadY = 0
    }

    private fun trimLeadingIdle(steps: List<MacroStep>): List<MacroStep> {
        if (steps.isEmpty()) return steps
        val firstStartMs = steps.minOf { it.startTimeMs }
        if (firstStartMs <= 0L) return steps
        AppLog.d(TAG, "trim leading idle offset=$firstStartMs")
        return steps.map { step -> step.withStartTime(step.startTimeMs - firstStartMs) }
    }

    private fun relativeElapsedMs(nowElapsedMs: Long): Long {
        if (recordingStartEpochMs == 0L) return 0L
        return (nowElapsedMs - recordingStartEpochMs).coerceAtLeast(0L)
    }

    private fun resetInternalState() {
        leftSamples.clear()
        rightSamples.clear()
        leftInGesture = false
        rightInGesture = false
        leftGestureStartMs = 0L
        rightGestureStartMs = 0L
        leftX = 0f
        leftY = 0f
        rightX = 0f
        rightY = 0f
        pendingButtonDowns.clear()
        pressedButtons = mutableSetOf()
        hatStartMs = null
        dpadX = 0
        dpadY = 0
        recordedSteps.clear()
        recordingStartEpochMs = 0L
    }
}
