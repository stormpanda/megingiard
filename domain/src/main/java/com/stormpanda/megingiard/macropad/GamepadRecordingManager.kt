package com.stormpanda.megingiard.macropad

import android.content.Context
import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "GamepadRecordingManager"
private const val MAX_LIVE_EVENTS = 5
private const val JOYSTICK_DEAD_ZONE = 0.15f
private const val BTN_C_ALIAS = 306
private const val FRAMEWORK_CAPTURE_DEVICE = "/android/framework/gamepad"
private const val INT16_MAX = 32767f

sealed interface GamepadRecordingState {
    data object Idle : GamepadRecordingState
    data object Starting : GamepadRecordingState
    data class Recording(
        val devicePath: String,
        val usingFrameworkCapture: Boolean,
        val liveEvents: List<GamepadRecordingLiveEvent>,
    ) : GamepadRecordingState
    data class Done(val steps: List<MacroStep>) : GamepadRecordingState
    data class Error(val reason: GamepadRecordingError) : GamepadRecordingState
}

sealed interface GamepadRecordingLiveEvent {
    data class Button(val code: Int, val pressed: Boolean) : GamepadRecordingLiveEvent
    data class DPad(val dirX: Int, val dirY: Int) : GamepadRecordingLiveEvent
    data class Joystick(
        val stick: JoystickStick,
        val x: Float,
        val y: Float,
    ) : GamepadRecordingLiveEvent
}

enum class GamepadRecordingError {
    START_FAILED,
}

private data class JoystickGesture(
    val startTimeMs: Long,
    val peakX: Float,
    val peakY: Float,
    val currentX: Float,
    val currentY: Float,
)

object GamepadRecordingManager {

    private val _state = MutableStateFlow<GamepadRecordingState>(GamepadRecordingState.Idle)
    val state: StateFlow<GamepadRecordingState> = _state.asStateFlow()

    @Volatile
    private var frameworkCaptureActive = false

    private var recordingStartElapsedMs = 0L
    private val recordedSteps = mutableListOf<MacroStep>()
    private val pendingButtonDowns = mutableMapOf<Int, Long>()
    private var hatStartMs: Long? = null
    private var currentHatX = 0
    private var currentHatY = 0
    private var leftGesture: JoystickGesture? = null
    private var rightGesture: JoystickGesture? = null
    private var leftCurrentX = 0f
    private var leftCurrentY = 0f
    private var rightCurrentX = 0f
    private var rightCurrentY = 0f

    fun isFrameworkCaptureActive(): Boolean = frameworkCaptureActive

    fun onFrameworkButtonEvent(rawCode: Int, pressed: Boolean): Boolean {
        if (!frameworkCaptureActive) {
            return false
        }
        val currentState = _state.value
        if (currentState !is GamepadRecordingState.Recording) {
            return false
        }
        val nowMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        recordButtonEvent(rawCode = rawCode, pressed = pressed, nowMs = nowMs, mirrorToInjector = true)
        return true
    }

    fun onFrameworkDpadState(dirX: Int, dirY: Int): Boolean {
        if (!frameworkCaptureActive) {
            return false
        }
        val currentState = _state.value
        if (currentState !is GamepadRecordingState.Recording) {
            return false
        }
        val nowMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        updateHat(nowMs = nowMs, nextX = dirX.coerceIn(-1, 1), nextY = dirY.coerceIn(-1, 1), mirrorToInjector = true)
        return true
    }

    fun onFrameworkAxisSnapshot(
        leftX: Float,
        leftY: Float,
        rightX: Float,
        rightY: Float,
        hatX: Int,
        hatY: Int,
    ): Boolean {
        if (!frameworkCaptureActive) {
            return false
        }
        val currentState = _state.value
        if (currentState !is GamepadRecordingState.Recording) {
            return false
        }
        val nowMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        updateHat(nowMs = nowMs, nextX = hatX.coerceIn(-1, 1), nextY = hatY.coerceIn(-1, 1), mirrorToInjector = true)
        updateJoystickNormalized(GamepadKeycodes.ABS_X, leftX, nowMs, mirrorToInjector = true)
        updateJoystickNormalized(GamepadKeycodes.ABS_Y, leftY, nowMs, mirrorToInjector = true)
        updateJoystickNormalized(GamepadKeycodes.ABS_Z, rightX, nowMs, mirrorToInjector = true)
        updateJoystickNormalized(GamepadKeycodes.ABS_RZ, rightY, nowMs, mirrorToInjector = true)
        return true
    }

    fun startRecording(context: Context) {
        val currentState = _state.value
        if (currentState is GamepadRecordingState.Starting || currentState is GamepadRecordingState.Recording) {
            AppLog.w(TAG, "startRecording ignored because recording is already active")
            return
        }
        AppLog.i(TAG, "startRecording (framework capture + injector passthrough)")
        resetInternalState()
        _state.value = GamepadRecordingState.Starting

        GamepadInjector.stop()
        GamepadInjector.start(context = context.applicationContext)
        if (!GamepadInjector.isRunning) {
            AppLog.e(TAG, "Failed to start GamepadInjector for passthrough")
            AppStateManager.setGamepadRecordingPassthroughActive(false)
            _state.value = GamepadRecordingState.Error(GamepadRecordingError.START_FAILED)
            return
        }
        AppStateManager.setGamepadRecordingPassthroughActive(true)
        recordingStartElapsedMs = SystemClock.elapsedRealtime()
        frameworkCaptureActive = true
        _state.value = GamepadRecordingState.Recording(
            devicePath = FRAMEWORK_CAPTURE_DEVICE,
            usingFrameworkCapture = true,
            liveEvents = emptyList(),
        )
        AppLog.i(TAG, "Recording started on framework route with passthrough active")
    }

    suspend fun finishRecording() {
        val currentState = _state.value
        if (currentState !is GamepadRecordingState.Starting && currentState !is GamepadRecordingState.Recording) {
            AppLog.w(TAG, "finishRecording ignored because no recording is active")
            return
        }
        AppLog.i(TAG, "finishRecording")
        val stopMs = relativeElapsedMs(SystemClock.elapsedRealtime())
        emitRemainingSteps(stopMs)
        resetPassthroughState()
        stopInjector()
        AppStateManager.setGamepadRecordingPassthroughActive(false)
        frameworkCaptureActive = false
        val steps = recordedSteps.sortedBy { it.startTimeMs }
        AppLog.i(TAG, "Recording finished with ${steps.size} steps")
        _state.value = GamepadRecordingState.Done(steps)
    }

    suspend fun cancelRecording() {
        val currentState = _state.value
        if (currentState == GamepadRecordingState.Idle) {
            return
        }
        AppLog.i(TAG, "cancelRecording")
        resetPassthroughState()
        stopInjector()
        AppStateManager.setGamepadRecordingPassthroughActive(false)
        resetInternalState()
        _state.value = GamepadRecordingState.Idle
    }

    fun resetState() {
        AppLog.d(TAG, "resetState")
        resetPassthroughState()
        stopInjector()
        AppStateManager.setGamepadRecordingPassthroughActive(false)
        resetInternalState()
        _state.value = GamepadRecordingState.Idle
    }

    private fun recordButtonEvent(rawCode: Int, pressed: Boolean, nowMs: Long, mirrorToInjector: Boolean) {
        val code = normalizeButtonCode(rawCode)
        if (rawCode != code) {
            AppLog.d(TAG, "Normalized button code raw=$rawCode mapped=$code")
        }
        if (pressed) {
            if (pendingButtonDowns.containsKey(code)) {
                return
            }
            pendingButtonDowns[code] = nowMs
            AppLog.d(TAG, "Button down code=$code at=$nowMs")
            if (mirrorToInjector && GamepadInjector.isRunning) {
                GamepadInjector.buttonDown(code)
            }
            appendLiveEvent(GamepadRecordingLiveEvent.Button(code = code, pressed = true))
        } else {
            if (mirrorToInjector && GamepadInjector.isRunning) {
                GamepadInjector.buttonUp(code)
            }
            val startMs = pendingButtonDowns.remove(code) ?: return
            val durationMs = (nowMs - startMs).coerceAtLeast(1L)
            val label = GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: "BTN $code"
            recordedSteps += MacroStep.GamepadButtonTap(
                startTimeMs = startMs,
                durationMs = durationMs,
                btnCode = code,
                label = label,
            )
            AppLog.d(TAG, "Button tap code=$code start=$startMs duration=$durationMs")
            appendLiveEvent(GamepadRecordingLiveEvent.Button(code = code, pressed = false))
        }
    }

    private fun normalizeButtonCode(rawCode: Int): Int = when (rawCode) {
        BTN_C_ALIAS -> GamepadKeycodes.BTN_SOUTH
        else -> rawCode
    }

    private fun updateHat(nowMs: Long, nextX: Int, nextY: Int, mirrorToInjector: Boolean) {
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
        hatStartMs = if (isNeutral) {
            null
        } else {
            appendLiveEvent(GamepadRecordingLiveEvent.DPad(dirX = nextX, dirY = nextY))
            nowMs
        }

        if (mirrorToInjector && GamepadInjector.isRunning) {
            GamepadInjector.hat(axis = 0, value = nextX)
            GamepadInjector.hat(axis = 1, value = nextY)
        }
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

    private fun updateJoystickNormalized(axisCode: Int, normalizedValue: Float, nowMs: Long, mirrorToInjector: Boolean) {
        val clamped = normalizedValue.coerceIn(-1f, 1f)
        when (axisCode) {
            GamepadKeycodes.ABS_X -> leftCurrentX = clamped
            GamepadKeycodes.ABS_Y -> leftCurrentY = clamped
            GamepadKeycodes.ABS_Z -> rightCurrentX = clamped
            GamepadKeycodes.ABS_RZ -> rightCurrentY = clamped
        }

        if (mirrorToInjector && GamepadInjector.isRunning) {
            GamepadInjector.joystick(axisCode = axisCode, value = normalizedToInt16(clamped))
        }

        applyJoystickGestureUpdate(axisCode = axisCode, nowMs = nowMs)
    }

    private fun applyJoystickGestureUpdate(axisCode: Int, nowMs: Long) {
        when (axisCode) {
            GamepadKeycodes.ABS_X,
            GamepadKeycodes.ABS_Y,
            -> leftGesture = updateJoystickGesture(
                activeGesture = leftGesture,
                stick = JoystickStick.LEFT,
                nowMs = nowMs,
                currentX = leftCurrentX,
                currentY = leftCurrentY,
            )

            GamepadKeycodes.ABS_Z,
            GamepadKeycodes.ABS_RZ,
            -> rightGesture = updateJoystickGesture(
                activeGesture = rightGesture,
                stick = JoystickStick.RIGHT,
                nowMs = nowMs,
                currentX = rightCurrentX,
                currentY = rightCurrentY,
            )
        }
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
            appendLiveEvent(GamepadRecordingLiveEvent.Joystick(stick = stick, x = currentX, y = currentY))
            return JoystickGesture(
                startTimeMs = nowMs,
                peakX = currentX,
                peakY = currentY,
                currentX = currentX,
                currentY = currentY,
            )
        }

        val peakMagnitude = magnitude(activeGesture.peakX, activeGesture.peakY)
        val nextPeakX: Float
        val nextPeakY: Float
        if (magnitude > peakMagnitude) {
            nextPeakX = currentX
            nextPeakY = currentY
            appendLiveEvent(GamepadRecordingLiveEvent.Joystick(stick = stick, x = currentX, y = currentY))
        } else {
            nextPeakX = activeGesture.peakX
            nextPeakY = activeGesture.peakY
        }
        return activeGesture.copy(
            peakX = nextPeakX,
            peakY = nextPeakY,
            currentX = currentX,
            currentY = currentY,
        )
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
    }

    private fun appendLiveEvent(event: GamepadRecordingLiveEvent) {
        val currentState = _state.value
        if (currentState !is GamepadRecordingState.Recording) {
            return
        }
        val updatedEvents = (currentState.liveEvents + event).takeLast(MAX_LIVE_EVENTS)
        _state.value = currentState.copy(liveEvents = updatedEvents)
    }

    private fun normalizedToInt16(value: Float): Int {
        return if (value <= -1f) {
            -32768
        } else {
            (value * INT16_MAX).roundToInt().coerceIn(-32768, 32767)
        }
    }

    private fun magnitude(x: Float, y: Float): Float = sqrt((x * x) + (y * y))

    private fun relativeElapsedMs(nowElapsedMs: Long): Long {
        if (recordingStartElapsedMs == 0L) {
            return 0L
        }
        return (nowElapsedMs - recordingStartElapsedMs).coerceAtLeast(0L)
    }

    private fun resetPassthroughState() {
        if (!GamepadInjector.isRunning) {
            return
        }
        pendingButtonDowns.keys.forEach { code ->
            GamepadInjector.buttonUp(code)
        }
        GamepadInjector.hat(axis = 0, value = 0)
        GamepadInjector.hat(axis = 1, value = 0)
        GamepadInjector.joystick(axisCode = GamepadKeycodes.ABS_X, value = 0)
        GamepadInjector.joystick(axisCode = GamepadKeycodes.ABS_Y, value = 0)
        GamepadInjector.joystick(axisCode = GamepadKeycodes.ABS_Z, value = 0)
        GamepadInjector.joystick(axisCode = GamepadKeycodes.ABS_RZ, value = 0)
    }

    private fun stopInjector() {
        if (GamepadInjector.isRunning) {
            GamepadInjector.stop()
        }
    }

    private fun resetInternalState() {
        AppLog.d(TAG, "resetInternalState")
        recordingStartElapsedMs = 0L
        frameworkCaptureActive = false
        recordedSteps.clear()
        pendingButtonDowns.clear()
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