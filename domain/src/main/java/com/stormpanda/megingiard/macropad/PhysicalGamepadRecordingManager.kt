package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
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
import com.stormpanda.megingiard.macropad.GamepadKeycodes

private const val TAG = "PhysGamepadRecManager"

/** Linux input event types used for evdev filtering. */
private const val EV_KEY = 1
private const val EV_ABS = 3

/** D-Pad hat axis codes (not in GamepadKeycodes — BTN_ range only). */
private const val ABS_HAT0X = 16
private const val ABS_HAT0Y = 17

/** Normalised dead zone radius. Stick movements below this magnitude do not start a gesture. */
private const val JOYSTICK_DEAD_ZONE = 0.08f

/** RDP simplification tolerance in normalised axis units (0–1 scale). */
private const val RDP_EPSILON = 0.04f

/**
 * Records analog stick gestures from the physical gamepad via the `megingiard_privd`
 * daemon's evdev subscription (`SUB GAMEPAD` / `UNSUB GAMEPAD`).
 *
 * **Recording flow:**
 * 1. [startRecording] — suppresses injection via [PrivdGamepadInjector.isRecordingActive],
 *    sends `SUB GAMEPAD\n` to the daemon, and launches a coroutine that collects
 *    [PrivdClient.evdevEvents].
 * 2. Evdev events are translated to [GamepadRecordingState.Recording] state updates and
 *    accumulated into per-stick [PathSample] lists.
 * 3. [finishRecording] — closes open gestures, applies RDP decimation, sends
 *    `UNSUB GAMEPAD\n`, restores injection, and emits [GamepadRecordingState.Done].
 * 4. [cancelRecording] — tears down without emitting steps.
 *
 * Gamepad button and D-Pad input during recording is captured (echoed to state) but **not**
 * injected into the game — [PrivdGamepadInjector.isRecordingActive] blocks all injection.
 * Callers must handle button step assembly if desired; v1 records only analog stick paths.
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
    private var pressedButtons: MutableSet<Int> = mutableSetOf()
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
        recordingStartEpochMs = System.currentTimeMillis()

        _state.value = GamepadRecordingState.Recording(
            pressedButtons = emptySet(),
            dpadDirectionX = 0,
            dpadDirectionY = 0,
            leftStickX = 0f,
            leftStickY = 0f,
            rightStickX = 0f,
            rightStickY = 0f,
        )

        collectorJob = scope.launch {
            PrivdClient.evdevEvents.collect { event ->
                handleEvdevEvent(event)
            }
        }
    }

    /**
     * Stops recording, decimates accumulated samples, and emits [GamepadRecordingState.Done].
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

        val stopMs = System.currentTimeMillis()
        closeOpenGestures(stopMs)

        PrivdClient.send("UNSUB GAMEPAD\n")
        PrivdGamepadInjector.isRecordingActive = false

        val steps = recordedSteps.toList()
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

    private fun handleEvdevEvent(event: com.stormpanda.megingiard.privd.EvdevEvent) {
        when (event.type) {
            EV_KEY -> handleKeyEvent(event.code, event.value)
            EV_ABS -> handleAbsEvent(event.code, event.value)
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

    private fun handleKeyEvent(code: Int, value: Int) {
        if (value == 1) pressedButtons.add(code) else pressedButtons.remove(code)
    }

    private fun handleAbsEvent(code: Int, rawValue: Int) {
        val nowMs = System.currentTimeMillis()
        when (code) {
            ABS_HAT0X -> dpadX = rawValue
            ABS_HAT0Y -> dpadY = rawValue
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

    private fun updateStick(stick: JoystickStick, x: Float, y: Float, nowMs: Long) {
        val mag = kotlin.math.sqrt(x * x + y * y)
        val samples = if (stick == JoystickStick.LEFT) leftSamples else rightSamples
        val inGesture = if (stick == JoystickStick.LEFT) leftInGesture else rightInGesture
        val gestureStartMs = if (stick == JoystickStick.LEFT) leftGestureStartMs else rightGestureStartMs

        if (!inGesture) {
            if (mag > JOYSTICK_DEAD_ZONE) {
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

            if (mag <= JOYSTICK_DEAD_ZONE) {
                /* Stick returned to neutral — close gesture. */
                val decimated = rdpDecimate(samples.toList(), RDP_EPSILON)
                val durationMs = offsetMs.coerceAtLeast(1L)
                val step = MacroStep.JoystickPath(
                    startTimeMs = gestureStartMs - recordingStartEpochMs,
                    durationMs = durationMs,
                    stick = stick,
                    samples = decimated,
                )
                recordedSteps.add(step)
                samples.clear()
                if (stick == JoystickStick.LEFT) {
                    leftInGesture = false
                } else {
                    rightInGesture = false
                }
                AppLog.d(TAG, "gesture closed stick=$stick samples=${decimated.size} (raw=${samples.size})")
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
            val decimated = rdpDecimate(leftSamples.toList(), RDP_EPSILON)
            recordedSteps.add(
                MacroStep.JoystickPath(
                    startTimeMs = leftGestureStartMs - recordingStartEpochMs,
                    durationMs = durationMs,
                    stick = JoystickStick.LEFT,
                    samples = decimated,
                ),
            )
            leftInGesture = false
            AppLog.d(TAG, "closeOpenGesture: left stick forced-closed samples=${decimated.size}")
        }
        if (rightInGesture && rightSamples.isNotEmpty()) {
            val durationMs = (stopMs - rightGestureStartMs).coerceAtLeast(1L)
            val decimated = rdpDecimate(rightSamples.toList(), RDP_EPSILON)
            recordedSteps.add(
                MacroStep.JoystickPath(
                    startTimeMs = rightGestureStartMs - recordingStartEpochMs,
                    durationMs = durationMs,
                    stick = JoystickStick.RIGHT,
                    samples = decimated,
                ),
            )
            rightInGesture = false
            AppLog.d(TAG, "closeOpenGesture: right stick forced-closed samples=${decimated.size}")
        }
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
        pressedButtons = mutableSetOf()
        dpadX = 0
        dpadY = 0
        recordedSteps.clear()
        recordingStartEpochMs = 0L
    }
}
