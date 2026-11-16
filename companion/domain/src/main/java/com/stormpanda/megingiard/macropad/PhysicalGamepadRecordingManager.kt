package com.stormpanda.megingiard.macropad

import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.EvdevEvent
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.settings.MacroPadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "PhysGamepadRecManager"

/** Linux input event types used for evdev filtering. */
private const val EV_KEY = 1
private const val EV_ABS = 3

/** D-Pad hat axis codes (not in GamepadKeycodes — BTN_ range only). */
private const val ABS_HAT0X = 16
private const val ABS_HAT0Y = 17

private const val STICK_RAW_SCALE = 32767f
private const val TRIGGER_RAW_SCALE = 32767f
private const val TRIGGER_DEADZONE = 0.02f
private const val TRIGGER_DIGITAL_HUD_THRESHOLD = 0.5f

sealed interface GamepadRecordingState {
    data object Idle : GamepadRecordingState

    data class Recording(
        val pressedButtons: Set<Int>,
        val dpadDirectionX: Int,
        val dpadDirectionY: Int,
        val leftStickX: Float,
        val leftStickY: Float,
        val rightStickX: Float,
        val rightStickY: Float,
        val leftTrigger: Float = 0f,
        val rightTrigger: Float = 0f,
        val stepCount: Int = 0,
        val startElapsedRealtime: Long = 0L,
    ) : GamepadRecordingState

    data class Done(
        val steps: List<MacroStep>,
    ) : GamepadRecordingState
}

/**
 * Records buttons, D-Pad taps, and analog stick gestures from the physical gamepad
 * via the `megingiard_privd`
 * daemon's evdev subscription (`SUB GAMEPAD` / `UNSUB GAMEPAD`).
 *
 * **Recording flow:**
 * 1. [startRecording] — suppresses injection via [GamepadInjector.isRecordingActive],
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
 */
object PhysicalGamepadRecordingManager {
    private val recordingLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<GamepadRecordingState>(GamepadRecordingState.Idle)
    val state: StateFlow<GamepadRecordingState> = _state.asStateFlow()

    private var recordingStartEpochMs: Long = 0L

    private var collectorJob: Job? = null

    // Per-stick gesture tracking
    private val leftSamples = mutableListOf<PathSample>()
    private val rightSamples = mutableListOf<PathSample>()
    private var leftInGesture = false
    private var rightInGesture = false
    private var leftGestureStartMs = 0L
    private var rightGestureStartMs = 0L

    // Per-trigger gesture tracking
    private val leftTriggerSamples = mutableListOf<TriggerSample>()
    private val rightTriggerSamples = mutableListOf<TriggerSample>()
    private var leftTriggerInGesture = false
    private var rightTriggerInGesture = false
    private var leftTriggerGestureStartMs = 0L
    private var rightTriggerGestureStartMs = 0L
    private var leftTriggerVal = 0f
    private var rightTriggerVal = 0f
    private var leftTriggerDigitalDown = false
    private var rightTriggerDigitalDown = false
    private var leftTriggerSuppressDigital = false
    private var rightTriggerSuppressDigital = false

    // Latest raw axis values — held so we can close open gestures at stop time
    private var leftX = 0f
    private var leftY = 0f
    private var rightX = 0f
    private var rightY = 0f

    // Steps collected from closed gestures during the session
    private val recordedSteps = mutableListOf<MacroStep>()

    // Latest recording state snapshot — mutated as events arrive and emitted
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
    fun startRecording() =
        synchronized(recordingLock) {
            if (_state.value is GamepadRecordingState.Recording) {
                AppLog.w(TAG, "startRecording() called while already recording — ignored")
                return@synchronized
            }
            AppLog.i(TAG, "startRecording()")
            resetInternalState()
            GamepadInjector.isRecordingActive = true
            PrivdClient.send("SUB GAMEPAD\n")
            beginRecordingSession(startElapsedMs = SystemClock.elapsedRealtime())

            collectorJob?.cancel()
            collectorJob =
                scope.launch {
                    PrivdClient.evdevEvents.collect { event ->
                        recordEvdevEvent(event = event, nowElapsedMs = SystemClock.elapsedRealtime())
                    }
                }
        }

    /**
     * Stops recording, closes any open gestures, and emits [GamepadRecordingState.Done].
     */
    fun finishRecording() =
        synchronized(recordingLock) {
            val currentState = _state.value
            if (currentState !is GamepadRecordingState.Recording) {
                AppLog.w(TAG, "finishRecording() called while not recording — ignored")
                return@synchronized
            }
            AppLog.i(TAG, "finishRecording() — ${recordedSteps.size} closed gestures so far")

            collectorJob?.cancel()
            collectorJob = null

            val steps = completeRecordingAt(stopElapsedMs = SystemClock.elapsedRealtime())

            PrivdClient.send("UNSUB GAMEPAD\n")
            GamepadInjector.isRecordingActive = false

            AppLog.d(TAG, "finishRecording() done: ${steps.size} steps")
            _state.value = GamepadRecordingState.Done(steps)
        }

    /**
     * Cancels the recording session without emitting any steps.
     */
    fun cancelRecording() =
        synchronized(recordingLock) {
            AppLog.i(TAG, "cancelRecording()")
            collectorJob?.cancel()
            collectorJob = null
            PrivdClient.send("UNSUB GAMEPAD\n")
            GamepadInjector.isRecordingActive = false
            resetInternalState()
            _state.value = GamepadRecordingState.Idle
        }

    /** Resets [state] to [GamepadRecordingState.Idle]. Call after consuming a [GamepadRecordingState.Done]. */
    fun resetState() =
        synchronized(recordingLock) {
            AppLog.d(TAG, "resetState()")
            resetInternalState()
            _state.value = GamepadRecordingState.Idle
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    internal fun startRecordingForTest(startElapsedMs: Long) =
        synchronized(recordingLock) {
            resetInternalState()
            beginRecordingSession(startElapsedMs = startElapsedMs)
        }

    internal fun finishRecordingForTest(stopElapsedMs: Long): List<MacroStep> =
        synchronized(recordingLock) {
            val steps = completeRecordingAt(stopElapsedMs = stopElapsedMs)
            _state.value = GamepadRecordingState.Done(steps)
            return@synchronized steps
        }

    internal fun recordEvdevEvent(
        event: EvdevEvent,
        nowElapsedMs: Long,
    ) = synchronized(recordingLock) {
        if (_state.value !is GamepadRecordingState.Recording) return@synchronized
        when (event.type) {
            EV_KEY -> handleKeyEvent(code = event.code, value = event.value, nowMs = relativeElapsedMs(nowElapsedMs))
            EV_ABS -> handleAbsEvent(code = event.code, rawValue = event.value, nowMs = nowElapsedMs)
        }
        // Update Recording state snapshot after each event for UI reactivity.
        val current = _state.value as? GamepadRecordingState.Recording ?: return@synchronized
        val activeStepCount =
            recordedSteps.size +
                pendingButtonDowns.size +
                (if (hatStartMs != null) 1 else 0) +
                (if (leftInGesture) 1 else 0) +
                (if (rightInGesture) 1 else 0) +
                (if (leftTriggerInGesture) 1 else 0) +
                (if (rightTriggerInGesture) 1 else 0)
        _state.value =
            current.copy(
                pressedButtons = pressedButtons.toSet(),
                dpadDirectionX = dpadX,
                dpadDirectionY = dpadY,
                leftStickX = leftX,
                leftStickY = leftY,
                rightStickX = rightX,
                rightStickY = rightY,
                leftTrigger = leftTriggerVal,
                rightTrigger = rightTriggerVal,
                stepCount = activeStepCount,
                startElapsedRealtime = recordingStartEpochMs,
            )
    }

    private fun beginRecordingSession(startElapsedMs: Long) =
        synchronized(recordingLock) {
            recordingStartEpochMs = startElapsedMs
            _state.value =
                GamepadRecordingState.Recording(
                    pressedButtons = emptySet(),
                    dpadDirectionX = 0,
                    dpadDirectionY = 0,
                    leftStickX = 0f,
                    leftStickY = 0f,
                    rightStickX = 0f,
                    rightStickY = 0f,
                    leftTrigger = 0f,
                    rightTrigger = 0f,
                    stepCount = 0,
                    startElapsedRealtime = startElapsedMs,
                )
        }

    private fun completeRecordingAt(stopElapsedMs: Long): List<MacroStep> =
        synchronized(recordingLock) {
            val stopRelativeMs = relativeElapsedMs(stopElapsedMs)
            flushOpenButtonAndHatSteps(stopRelativeMs)
            closeOpenGestures(stopElapsedMs)
            return@synchronized trimLeadingIdle(recordedSteps.sortedBy { it.startTimeMs })
        }

    private fun handleKeyEvent(
        code: Int,
        value: Int,
        nowMs: Long,
    ) {
        when (value) {
            1 -> {
                if (code == GamepadKeycodes.BTN_TL2) {
                    leftTriggerDigitalDown = true
                    if (leftTriggerInGesture || leftTriggerSuppressDigital) {
                        syncTriggerHudButton(GamepadTrigger.LEFT)
                        return
                    }
                } else if (code == GamepadKeycodes.BTN_TR2) {
                    rightTriggerDigitalDown = true
                    if (rightTriggerInGesture || rightTriggerSuppressDigital) {
                        syncTriggerHudButton(GamepadTrigger.RIGHT)
                        return
                    }
                }
                if (pendingButtonDowns.containsKey(code)) return
                pendingButtonDowns[code] = nowMs
                pressedButtons.add(code)
                AppLog.d(TAG, "button down code=$code at=$nowMs")
            }

            0 -> {
                if (code == GamepadKeycodes.BTN_TL2) {
                    leftTriggerDigitalDown = false
                    if (!leftTriggerInGesture) {
                        leftTriggerSuppressDigital = false
                    }
                    syncTriggerHudButton(GamepadTrigger.LEFT)
                    val startMs = pendingButtonDowns.remove(code) ?: return
                    emitButtonStep(code = code, startMs = startMs, endMs = nowMs)
                    return
                } else if (code == GamepadKeycodes.BTN_TR2) {
                    rightTriggerDigitalDown = false
                    if (!rightTriggerInGesture) {
                        rightTriggerSuppressDigital = false
                    }
                    syncTriggerHudButton(GamepadTrigger.RIGHT)
                    val startMs = pendingButtonDowns.remove(code) ?: return
                    emitButtonStep(code = code, startMs = startMs, endMs = nowMs)
                    return
                }
                val startMs = pendingButtonDowns.remove(code) ?: return
                pressedButtons.remove(code)
                emitButtonStep(code = code, startMs = startMs, endMs = nowMs)
            }
        }
    }

    private fun handleAbsEvent(
        code: Int,
        rawValue: Int,
        nowMs: Long,
    ) {
        when (code) {
            ABS_HAT0X -> {
                updateHat(nowMs = relativeElapsedMs(nowMs), nextX = rawValue, nextY = dpadY)
            }

            ABS_HAT0Y -> {
                updateHat(nowMs = relativeElapsedMs(nowMs), nextX = dpadX, nextY = rawValue)
            }

            GamepadKeycodes.ABS_X -> {
                leftX = (rawValue / STICK_RAW_SCALE).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.LEFT, x = leftX, y = leftY, nowMs = nowMs)
            }

            GamepadKeycodes.ABS_Y -> {
                leftY = (rawValue / STICK_RAW_SCALE).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.LEFT, x = leftX, y = leftY, nowMs = nowMs)
            }

            GamepadKeycodes.ABS_Z -> {
                rightX = (rawValue / STICK_RAW_SCALE).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.RIGHT, x = rightX, y = rightY, nowMs = nowMs)
            }

            GamepadKeycodes.ABS_RZ -> {
                rightY = (rawValue / STICK_RAW_SCALE).coerceIn(-1f, 1f)
                updateStick(stick = JoystickStick.RIGHT, x = rightX, y = rightY, nowMs = nowMs)
            }

            GamepadKeycodes.ABS_BRAKE -> {
                leftTriggerVal = (rawValue / TRIGGER_RAW_SCALE).coerceIn(0f, 1f)
                updateTrigger(trigger = GamepadTrigger.LEFT, pressure = leftTriggerVal, nowMs = nowMs)
            }

            GamepadKeycodes.ABS_GAS -> {
                rightTriggerVal = (rawValue / TRIGGER_RAW_SCALE).coerceIn(0f, 1f)
                updateTrigger(trigger = GamepadTrigger.RIGHT, pressure = rightTriggerVal, nowMs = nowMs)
            }
        }
    }

    private fun syncTriggerHudButton(trigger: GamepadTrigger) {
        val btnCode = if (trigger == GamepadTrigger.LEFT) GamepadKeycodes.BTN_TL2 else GamepadKeycodes.BTN_TR2
        val isDigitalDown = if (trigger == GamepadTrigger.LEFT) leftTriggerDigitalDown else rightTriggerDigitalDown
        val analogPressure = if (trigger == GamepadTrigger.LEFT) leftTriggerVal else rightTriggerVal
        if (isDigitalDown || analogPressure >= TRIGGER_DIGITAL_HUD_THRESHOLD) {
            pressedButtons.add(btnCode)
        } else {
            pressedButtons.remove(btnCode)
        }
    }

    private fun updateTrigger(
        trigger: GamepadTrigger,
        pressure: Float,
        nowMs: Long,
    ) {
        val clamped = pressure.coerceIn(0f, 1f)
        val samples = if (trigger == GamepadTrigger.LEFT) leftTriggerSamples else rightTriggerSamples
        val inGesture = if (trigger == GamepadTrigger.LEFT) leftTriggerInGesture else rightTriggerInGesture
        val gestureStartMs = if (trigger == GamepadTrigger.LEFT) leftTriggerGestureStartMs else rightTriggerGestureStartMs
        val digitalBtnCode = if (trigger == GamepadTrigger.LEFT) GamepadKeycodes.BTN_TL2 else GamepadKeycodes.BTN_TR2

        syncTriggerHudButton(trigger)

        if (!inGesture) {
            if (clamped > TRIGGER_DEADZONE) {
                pendingButtonDowns.remove(digitalBtnCode)
                samples.clear()
                samples.add(TriggerSample(offsetMs = 0L, pressure = clamped))
                if (trigger == GamepadTrigger.LEFT) {
                    leftTriggerInGesture = true
                    leftTriggerGestureStartMs = nowMs
                    leftTriggerSuppressDigital = true
                } else {
                    rightTriggerInGesture = true
                    rightTriggerGestureStartMs = nowMs
                    rightTriggerSuppressDigital = true
                }
                AppLog.d(TAG, "trigger gesture start trigger=$trigger at ${nowMs - recordingStartEpochMs} ms")
            }
        } else {
            val offsetMs = (nowMs - gestureStartMs).coerceAtLeast(0L)
            samples.add(TriggerSample(offsetMs = offsetMs, pressure = clamped))

            if (clamped <= TRIGGER_DEADZONE) {
                val durationMs = (offsetMs + 1L).coerceAtLeast(1L)
                val step =
                    MacroStep.TriggerPath(
                        startTimeMs = (gestureStartMs - recordingStartEpochMs).coerceAtLeast(0L),
                        durationMs = durationMs,
                        trigger = trigger,
                        samples = samples.toList(),
                    )
                recordedSteps.add(step)
                samples.clear()
                if (trigger == GamepadTrigger.LEFT) {
                    leftTriggerInGesture = false
                    if (!leftTriggerDigitalDown) {
                        leftTriggerSuppressDigital = false
                    }
                } else {
                    rightTriggerInGesture = false
                    if (!rightTriggerDigitalDown) {
                        rightTriggerSuppressDigital = false
                    }
                }
                AppLog.d(TAG, "trigger gesture closed trigger=$trigger samples=${step.samples.size}")
            }
        }
    }

    private fun emitButtonStep(
        code: Int,
        startMs: Long,
        endMs: Long,
    ) {
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

    private fun updateHat(
        nowMs: Long,
        nextX: Int,
        nextY: Int,
    ) {
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

    private fun emitHatStep(
        startMs: Long,
        endMs: Long,
        dirX: Int,
        dirY: Int,
    ) {
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

    private fun updateStick(
        stick: JoystickStick,
        x: Float,
        y: Float,
        nowMs: Long,
    ) {
        val mag = sqrt(x * x + y * y)
        val deadZone =
            if (stick == JoystickStick.LEFT) {
                MacroPadSettings.deadzoneLeft.value
            } else {
                MacroPadSettings.deadzoneRight.value
            }
        val samples = if (stick == JoystickStick.LEFT) leftSamples else rightSamples
        val inGesture = if (stick == JoystickStick.LEFT) leftInGesture else rightInGesture
        val gestureStartMs = if (stick == JoystickStick.LEFT) leftGestureStartMs else rightGestureStartMs

        if (!inGesture) {
            if (mag > deadZone) {
                // Stick left dead zone — begin new gesture.
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
            // Gesture in progress — accumulate sample.
            val offsetMs = nowMs - gestureStartMs
            samples.add(PathSample(offsetMs = offsetMs, x = x, y = y))

            if (mag <= deadZone) {
                /* Stick returned to neutral — close gesture. Duration MUST be strictly
                   greater than the final sample's offset so that the compiler's
                   end-of-step neutral reset event sorts strictly after the last sample
                   at the same global timestamp. */
                val durationMs = (offsetMs + 1L).coerceAtLeast(1L)
                val step =
                    MacroStep.JoystickPath(
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
            val minDurationMs = leftSamples.last().offsetMs + 1L
            val durationMs = max(stopMs - leftGestureStartMs, minDurationMs).coerceAtLeast(1L)
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
            val minDurationMs = rightSamples.last().offsetMs + 1L
            val durationMs = max(stopMs - rightGestureStartMs, minDurationMs).coerceAtLeast(1L)
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
        if (leftTriggerInGesture && leftTriggerSamples.isNotEmpty()) {
            val minDurationMs = leftTriggerSamples.last().offsetMs + 1L
            val durationMs = max(stopMs - leftTriggerGestureStartMs, minDurationMs).coerceAtLeast(1L)
            recordedSteps.add(
                MacroStep.TriggerPath(
                    startTimeMs = (leftTriggerGestureStartMs - recordingStartEpochMs).coerceAtLeast(0L),
                    durationMs = durationMs,
                    trigger = GamepadTrigger.LEFT,
                    samples = leftTriggerSamples.toList(),
                ),
            )
            leftTriggerInGesture = false
            AppLog.d(TAG, "closeOpenGesture: left trigger forced-closed samples=${leftTriggerSamples.size}")
        }
        if (rightTriggerInGesture && rightTriggerSamples.isNotEmpty()) {
            val minDurationMs = rightTriggerSamples.last().offsetMs + 1L
            val durationMs = max(stopMs - rightTriggerGestureStartMs, minDurationMs).coerceAtLeast(1L)
            recordedSteps.add(
                MacroStep.TriggerPath(
                    startTimeMs = (rightTriggerGestureStartMs - recordingStartEpochMs).coerceAtLeast(0L),
                    durationMs = durationMs,
                    trigger = GamepadTrigger.RIGHT,
                    samples = rightTriggerSamples.toList(),
                ),
            )
            rightTriggerInGesture = false
            AppLog.d(TAG, "closeOpenGesture: right trigger forced-closed samples=${rightTriggerSamples.size}")
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

    private fun resetInternalState() =
        synchronized(recordingLock) {
            leftSamples.clear()
            rightSamples.clear()
            leftInGesture = false
            rightInGesture = false
            leftGestureStartMs = 0L
            rightGestureStartMs = 0L
            leftTriggerSamples.clear()
            rightTriggerSamples.clear()
            leftTriggerInGesture = false
            rightTriggerInGesture = false
            leftTriggerGestureStartMs = 0L
            rightTriggerGestureStartMs = 0L
            leftTriggerVal = 0f
            rightTriggerVal = 0f
            leftTriggerDigitalDown = false
            rightTriggerDigitalDown = false
            leftTriggerSuppressDigital = false
            rightTriggerSuppressDigital = false
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
