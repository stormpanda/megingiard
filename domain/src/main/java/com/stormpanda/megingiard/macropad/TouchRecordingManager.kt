package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TouchRecordingManager"

enum class TouchRecordingMode { TAP, GESTURE }

/**
 * Singleton that coordinates the touch recording flow (taps and gestures) for macro steps.
 *
 * Flow:
 * 1. [requestRecording] — called from the timeline editor when the user taps "Record Touch"
 *    and selects a mode. Sets [recordingRequested] to `true`, which [ScreenCaptureService]
 *    observes to show [RecordingMirrorPresentation] on the secondary display.
 * 2. [onTapRecorded] or [onGestureRecorded] — called by [RecordingMirrorPresentation] when
 *    the user finishes their input. Stores the result and resets [recordingRequested].
 * 3. The timeline editor observes [recordedTap] or [recordedGesture] via `LaunchedEffect` and
 *    appends the corresponding step, then calls [consumeRecordedTap] / [consumeRecordedGesture]
 *    to clear the value.
 */
object TouchRecordingManager {

    private val _recordingRequested = MutableStateFlow(false)
    /** `true` while the recording mirror is expected to be shown. */
    val recordingRequested: StateFlow<Boolean> = _recordingRequested.asStateFlow()

    private val _recordingMode = MutableStateFlow(TouchRecordingMode.TAP)
    /** The active recording mode (TAP or GESTURE). */
    val recordingMode: StateFlow<TouchRecordingMode> = _recordingMode.asStateFlow()

    private val _recordedTap = MutableStateFlow<Pair<Float, Float>?>(null)
    /**
     * Normalised tap position (normX in 0..1, normY in 0..1) recorded by the user,
     * or `null` if no tap has been recorded yet / after consumption.
     */
    val recordedTap: StateFlow<Pair<Float, Float>?> = _recordedTap.asStateFlow()

    private val _recordedGesture = MutableStateFlow<List<TouchSample>?>(null)
    /**
     * List of recorded touch samples, or `null` if no gesture has been recorded yet / after consumption.
     */
    val recordedGesture: StateFlow<List<TouchSample>?> = _recordedGesture.asStateFlow()

    /**
     * Signals that the recording mirror should be shown with the specified mode.
     * Clears any stale recorded inputs first.
     */
    fun requestRecording(mode: TouchRecordingMode) {
        AppLog.i(TAG, "requestRecording mode=$mode")
        _recordedTap.value = null
        _recordedGesture.value = null
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
        _recordingRequested.value = false
    }

    /**
     * Called by [RecordingMirrorPresentation] when the user completes their gesture in GESTURE mode.
     * Stores the samples and clears the recording request.
     */
    fun onGestureRecorded(samples: List<TouchSample>) {
        AppLog.i(TAG, "onGestureRecorded samples=${samples.size}")
        _recordedGesture.value = samples
        _recordingRequested.value = false
    }

    /**
     * Cancels an in-progress recording. Clears both the request and any partial result.
     */
    fun cancelRecording() {
        AppLog.i(TAG, "cancelRecording")
        _recordingRequested.value = false
        _recordedTap.value = null
        _recordedGesture.value = null
    }

    /**
     * Called by the timeline editor after it has appended the [MacroStep.TouchTap].
     * Resets [recordedTap] to `null` so the `LaunchedEffect` does not re-trigger.
     */
    fun consumeRecordedTap() {
        AppLog.d(TAG, "consumeRecordedTap")
        _recordedTap.value = null
    }

    /**
     * Called by the timeline editor after it has appended the [MacroStep.TouchPath].
     * Resets [recordedGesture] to `null` so the `LaunchedEffect` does not re-trigger.
     */
    fun consumeRecordedGesture() {
        AppLog.d(TAG, "consumeRecordedGesture")
        _recordedGesture.value = null
    }
}
