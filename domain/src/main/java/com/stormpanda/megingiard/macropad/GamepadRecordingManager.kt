package com.stormpanda.megingiard.macropad

import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "GamepadRecordingManager"
private const val JOYSTICK_DEAD_ZONE = 0.15f

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
    ) : GamepadRecordingState
    data class Done(val steps: List<MacroStep>) : GamepadRecordingState
}

private data class JoystickGesture(
    val startTimeMs: Long,
    val peakX: Float,
    val peakY: Float,
)

object GamepadRecordingManager {

    private val _state = MutableStateFlow<GamepadRecordingState>(GamepadRecordingState.Idle)
    val state: StateFlow<GamepadRecordingState> = _state.asStateFlow()

    private var recordingStartElapsedMs = 0L
    private val recordedSteps = mutableListOf<MacroStep>()
    private val pendingButtonDowns = mutableMapOf<Int, Long>()
    private val pressedButtons = linkedSetOf<Int>()

    private var hatStartMs: Long? = null
    private var currentHatX = 0
    private var currentHatY = 0

    private var leftGesture: JoystickGesture? = null
    private var rightGesture: JoystickGesture? = null
    private var leftCurrentX = 0f
    private var leftCurrentY = 0f
    private var rightCurrentX = 0f
    private var rightCurrentY = 0f

    fun startRecording() {
        if (_state.value is GamepadRecordingState.Recording) {
            AppLog.w(TAG, "startRecording ignored because recording is already active")
            return
        }
        AppLog.i(TAG, "startRecording")
        resetInternalState()
        recordingStartElapsedMs = SystemClock.elapsedRealtime()
        updateRecordingState()
    }

    suspend fun finishRecording() {
        val currentState = _state.value
        if (currentState !is GamepadRecordingState.Recording) {
            AppLog.w(TAG, "finishRecording ignored because no recording is active")
            return
        }
        AppLog.i(TAG, "finishRecording")
        val stopMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        emitRemainingSteps(stopMs)
        val steps = trimLeadingIdle(recordedSteps.sortedBy { it.startTimeMs })
        AppLog.i(TAG, "Recording finished with ${steps.size} steps")
        _state.value = GamepadRecordingState.Done(steps)
    }

    suspend fun cancelRecording() {
        if (_state.value == GamepadRecordingState.Idle) {
            return
        }
        AppLog.i(TAG, "cancelRecording")
        resetInternalState()
        _state.value = GamepadRecordingState.Idle
    }

    fun resetState() {
        AppLog.d(TAG, "resetState")
        resetInternalState()
        _state.value = GamepadRecordingState.Idle
    }

    fun recordButtonDown(code: Int) {
        if (_state.value !is GamepadRecordingState.Recording) {
            return
        }
        if (pendingButtonDowns.containsKey(code)) {
            return
        }
        val nowMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        pendingButtonDowns[code] = nowMs
        pressedButtons += code
        AppLog.d(TAG, "Button down code=$code at=$nowMs")
        updateRecordingState()
    }

    fun recordButtonUp(code: Int) {
        if (_state.value !is GamepadRecordingState.Recording) {
            return
        }
        val nowMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        val startMs = pendingButtonDowns.remove(code) ?: return
        pressedButtons -= code
        val durationMs = (nowMs - startMs).coerceAtLeast(1L)
        val label = GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: "BTN $code"
        recordedSteps += MacroStep.GamepadButtonTap(
            startTimeMs = startMs,
            durationMs = durationMs,
            btnCode = code,
            label = label,
        )
        AppLog.d(TAG, "Button tap code=$code start=$startMs duration=$durationMs")
        updateRecordingState()
    }

    fun setDpad(dirX: Int, dirY: Int) {
        if (_state.value !is GamepadRecordingState.Recording) {
            return
        }
        val nowMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        updateHat(nowMs = nowMs, nextX = dirX.coerceIn(-1, 1), nextY = dirY.coerceIn(-1, 1))
        updateRecordingState()
    }

    fun setJoystick(stick: JoystickStick, x: Float, y: Float) {
        if (_state.value !is GamepadRecordingState.Recording) {
            return
        }
        val nowMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        when (stick) {
            JoystickStick.LEFT -> {
                leftCurrentX = x.coerceIn(-1f, 1f)
                leftCurrentY = y.coerceIn(-1f, 1f)
                leftGesture = updateJoystickGesture(
                    activeGesture = leftGesture,
                    stick = JoystickStick.LEFT,
                    nowMs = nowMs,
                    currentX = leftCurrentX,
                    currentY = leftCurrentY,
                )
            }
            JoystickStick.RIGHT -> {
                rightCurrentX = x.coerceIn(-1f, 1f)
                rightCurrentY = y.coerceIn(-1f, 1f)
                rightGesture = updateJoystickGesture(
                    activeGesture = rightGesture,
                    stick = JoystickStick.RIGHT,
                    nowMs = nowMs,
                    currentX = rightCurrentX,
                    currentY = rightCurrentY,
                )
            }
        }
        updateRecordingState()
    }

    private fun updateHat(nowMs: Long, nextX: Int, nextY: Int) {
        val previousX = currentHatX
        val previousY = currentHatY
        if (previousX == nextX && previousY == nextY) {
            return
        }

        val wasNeutral = previousX == 0 && previousY == 0
        val isNeutral = nextX == 0 && nextY == 0

        if (!wasNeutral && hatStartMs != null) {
            emitHatStep(startMs = hatStartMs!!, endMs = nowMs, dirX = previousX, dirY = previousY)
        }

        currentHatX = nextX
        currentHatY = nextY
        hatStartMs = if (isNeutral) null else nowMs
    }

    private fun emitHatStep(startMs: Long, endMs: Long, dirX: Int, dirY: Int) {
        val durationMs = (endMs - startMs).coerceAtLeast(1L)
        recordedSteps += MacroStep.DPadTap(
            startTimeMs = startMs,
            durationMs = durationMs,
            dirX = dirX,
            dirY = dirY,
        )
        AppLog.d(TAG, "D-pad step dirX=$dirX dirY=$dirY start=$startMs duration=$durationMs")
    }

    private fun updateJoystickGesture(
        activeGesture: JoystickGesture?,
        stick: JoystickStick,
        nowMs: Long,
        currentX: Float,
        currentY: Float,
    ): JoystickGesture? {
        val magnitude = magnitude(currentX, currentY)
        if (magnitude <= JOYSTICK_DEAD_ZONE) {
            if (activeGesture != null) {
                emitJoystickStep(stick = stick, gesture = activeGesture, endMs = nowMs)
            }
            return null
        }

        if (activeGesture == null) {
            AppLog.d(TAG, "Joystick gesture start stick=$stick at=$nowMs")
            return JoystickGesture(
                startTimeMs = nowMs,
                peakX = currentX,
                peakY = currentY,
            )
        }

        return if (magnitude > magnitude(activeGesture.peakX, activeGesture.peakY)) {
            activeGesture.copy(peakX = currentX, peakY = currentY)
        } else {
            activeGesture
        }
    }

    private fun emitJoystickStep(stick: JoystickStick, gesture: JoystickGesture, endMs: Long) {
        val durationMs = (endMs - gesture.startTimeMs).coerceAtLeast(1L)
        recordedSteps += MacroStep.JoystickMove(
            startTimeMs = gesture.startTimeMs,
            durationMs = durationMs,
            stick = stick,
            x = gesture.peakX,
            y = gesture.peakY,
        )
        AppLog.d(
            TAG,
            "Joystick step stick=$stick start=${gesture.startTimeMs} duration=$durationMs peakX=${gesture.peakX} peakY=${gesture.peakY}",
        )
    }

    private fun emitRemainingSteps(stopMs: Long) {
        pendingButtonDowns.toMap().forEach { (code, startMs) ->
            val durationMs = (stopMs - startMs).coerceAtLeast(1L)
            val label = GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: "BTN $code"
            recordedSteps += MacroStep.GamepadButtonTap(
                startTimeMs = startMs,
                durationMs = durationMs,
                btnCode = code,
                label = label,
            )
            AppLog.d(TAG, "Flushed held button code=$code start=$startMs duration=$durationMs")
        }
        pendingButtonDowns.clear()
        pressedButtons.clear()

        val localHatStartMs = hatStartMs
        if (localHatStartMs != null && (currentHatX != 0 || currentHatY != 0)) {
            emitHatStep(startMs = localHatStartMs, endMs = stopMs, dirX = currentHatX, dirY = currentHatY)
        }
        hatStartMs = null
        currentHatX = 0
        currentHatY = 0

        leftGesture?.let { emitJoystickStep(stick = JoystickStick.LEFT, gesture = it, endMs = stopMs) }
        rightGesture?.let { emitJoystickStep(stick = JoystickStick.RIGHT, gesture = it, endMs = stopMs) }
        leftGesture = null
        rightGesture = null
        leftCurrentX = 0f
        leftCurrentY = 0f
        rightCurrentX = 0f
        rightCurrentY = 0f
    }

    private fun updateRecordingState() {
        _state.value = GamepadRecordingState.Recording(
            pressedButtons = pressedButtons.toSet(),
            dpadDirectionX = currentHatX,
            dpadDirectionY = currentHatY,
            leftStickX = leftCurrentX,
            leftStickY = leftCurrentY,
            rightStickX = rightCurrentX,
            rightStickY = rightCurrentY,
        )
    }

    private fun magnitude(x: Float, y: Float): Float = sqrt((x * x) + (y * y))

    private fun relativeElapsedMs(nowElapsedMs: Long): Long {
        if (recordingStartElapsedMs == 0L) {
            return 0L
        }
        return (nowElapsedMs - recordingStartElapsedMs).coerceAtLeast(0L)
    }

    private fun trimLeadingIdle(steps: List<MacroStep>): List<MacroStep> {
        if (steps.isEmpty()) {
            return steps
        }
        val firstStartMs = steps.minOf { it.startTimeMs }
        if (firstStartMs <= 0L) {
            return steps
        }
        AppLog.d(TAG, "Trim leading idle offset=$firstStartMs")
        return steps.map { step ->
            when (step) {
                is MacroStep.GamepadButtonTap -> step.copy(startTimeMs = step.startTimeMs - firstStartMs)
                is MacroStep.JoystickMove -> step.copy(startTimeMs = step.startTimeMs - firstStartMs)
                is MacroStep.DPadTap -> step.copy(startTimeMs = step.startTimeMs - firstStartMs)
                is MacroStep.TouchTap -> step.copy(startTimeMs = step.startTimeMs - firstStartMs)
            }
        }
    }

    private fun resetInternalState() {
        AppLog.d(TAG, "resetInternalState")
        recordingStartElapsedMs = 0L
        recordedSteps.clear()
        pendingButtonDowns.clear()
        pressedButtons.clear()
        hatStartMs = null
        currentHatX = 0
        currentHatY = 0
        leftGesture = null
        rightGesture = null
        leftCurrentX = 0f
        leftCurrentY = 0f
        rightCurrentX = 0f
        rightCurrentY = 0f
    }
}