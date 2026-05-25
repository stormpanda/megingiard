package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TouchRecordingManager"

enum class TouchRecordingMode { TAP, GESTURE }

sealed interface TouchRecordingState {
    data object Idle : TouchRecordingState
    data class Recording(
        val mode: TouchRecordingMode,
        val recordedGestureCount: Int,
    ) : TouchRecordingState
    data class Done(val steps: List<MacroStep>) : TouchRecordingState
}

/**
 * Singleton that coordinates the touch recording flow (taps and gestures) for macro steps.
 *
 * Flow:
 * 1. [requestRecording] — called from the timeline editor when the user taps "Record Touch"
 *    and selects a mode. Sets [recordingRequested] to `true`, which [ScreenCaptureService]
 *    observes to show [RecordingMirrorPresentation] on the secondary display.
 * 2. [onTapRecorded] — called by [RecordingMirrorPresentation] for TAP mode and keeps
 *    the legacy one-shot flow.
 * 3. [recordGestureCompleted] — called for each completed GESTURE segment. The
 *    presentation stays open until [finishRecording] or [cancelRecording].
 * 4. The timeline editor observes [recordedTap] or [state] and appends the
 *    corresponding step(s), then calls [consumeRecordedTap] / [resetState].
 */
object TouchRecordingManager {

    private val _recordingRequested = MutableStateFlow(false)
    /** `true` while the recording mirror is expected to be shown. */
    val recordingRequested: StateFlow<Boolean> = _recordingRequested.asStateFlow()

    private val _recordingMode = MutableStateFlow(TouchRecordingMode.TAP)
    /** The active recording mode (TAP or GESTURE). */
    val recordingMode: StateFlow<TouchRecordingMode> = _recordingMode.asStateFlow()

    private val _state = MutableStateFlow<TouchRecordingState>(TouchRecordingState.Idle)
    val state: StateFlow<TouchRecordingState> = _state.asStateFlow()

    private val recordedGestureSteps = mutableListOf<MacroStep.TouchPath>()

    private val _recordedTap = MutableStateFlow<Pair<Float, Float>?>(null)
    /**
     * Normalised tap position (normX in 0..1, normY in 0..1) recorded by the user,
     * or `null` if no tap has been recorded yet / after consumption.
     */
    val recordedTap: StateFlow<Pair<Float, Float>?> = _recordedTap.asStateFlow()

    /**
     * Signals that the recording mirror should be shown with the specified mode.
     * Clears any stale recorded inputs first.
     */
    fun requestRecording(mode: TouchRecordingMode) {
        AppLog.i(TAG, "requestRecording mode=$mode")
        _recordedTap.value = null
        recordedGestureSteps.clear()
        _state.value = TouchRecordingState.Recording(
            mode = mode,
            recordedGestureCount = 0,
        )
        _recordingMode.value = mode
        _recordingRequested.value = true
    }

    /**
     * Called by [RecordingMirrorPresentation] when the user taps the mirror in TAP mode.
     * Stores the coordinates and clears the recording request.
     */
    fun onTapRecorded(normX: Float, normY: Float) {
        AppLog.i(TAG, "onTapRecorded normX=$normX normY=$normY")
        _recordedTap.value = Pair(normX, normY)
        _state.value = TouchRecordingState.Idle
        _recordingRequested.value = false
    }

    /**
     * Called by [RecordingMirrorPresentation] when the user completes one gesture segment.
     * The recording mirror stays open so more gestures can be added before Stop & Save.
     */
    fun recordGestureCompleted(samples: List<TouchSample>, startOffsetMs: Long) {
        val currentState = _state.value as? TouchRecordingState.Recording
        if (currentState == null) {
            AppLog.w(TAG, "recordGestureCompleted called while not recording — ignored")
            return
        }
        if (currentState.mode != TouchRecordingMode.GESTURE) {
            AppLog.w(TAG, "recordGestureCompleted called in non-GESTURE mode (${currentState.mode}) — ignored")
            return
        }
        if (samples.isEmpty()) return
        val completedSamples = completeTouchPathSamples(samples)
        val durationMs = completedSamples.maxOfOrNull { it.offsetMs } ?: 0L
        recordedGestureSteps += MacroStep.TouchPath(
            startTimeMs = startOffsetMs,
            durationMs = durationMs,
            samples = completedSamples,
        )
        AppLog.i(TAG, "recordGestureCompleted samples=${samples.size} completedSamples=${completedSamples.size} startOffsetMs=$startOffsetMs")
        _state.value = TouchRecordingState.Recording(
            mode = TouchRecordingMode.GESTURE,
            recordedGestureCount = recordedGestureSteps.size,
        )
    }

    fun finishRecording() {
        if (_state.value !is TouchRecordingState.Recording) {
            AppLog.w(TAG, "finishRecording called while not recording — ignored")
            return
        }
        AppLog.i(TAG, "finishRecording steps=${recordedGestureSteps.size}")
        val sorted = recordedGestureSteps.sortedBy { it.startTimeMs }
        val trimmed = trimLeadingIdle(sorted)
        _state.value = TouchRecordingState.Done(trimmed)
        recordedGestureSteps.clear()
        _recordingRequested.value = false
    }

    /**
     * Cancels an in-progress recording. Clears both the request and any partial result.
     */
    fun cancelRecording() {
        AppLog.i(TAG, "cancelRecording")
        _recordingRequested.value = false
        _recordedTap.value = null
        recordedGestureSteps.clear()
        _state.value = TouchRecordingState.Idle
    }

    /**
     * Called by the timeline editor after it has appended the [MacroStep.TouchTap].
     * Resets [recordedTap] to `null` so the `LaunchedEffect` does not re-trigger.
     */
    fun consumeRecordedTap() {
        AppLog.d(TAG, "consumeRecordedTap")
        _recordedTap.value = null
    }

    fun resetState() {
        AppLog.d(TAG, "resetState")
        recordedGestureSteps.clear()
        _state.value = TouchRecordingState.Idle
    }

    private fun trimLeadingIdle(steps: List<MacroStep.TouchPath>): List<MacroStep.TouchPath> {
        if (steps.isEmpty()) return steps
        val firstStartMs = steps.minOf { it.startTimeMs }
        if (firstStartMs <= 0L) return steps
        AppLog.d(TAG, "trimLeadingIdle offset=$firstStartMs")
        return steps.map { it.copy(startTimeMs = it.startTimeMs - firstStartMs) }
    }
}
