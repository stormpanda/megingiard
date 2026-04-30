package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppressprivate const val TAG = "TouchRecordingManager"

/**
 * Singleton that coordinates the touch-tap recording flow for macro steps.
 *
 * Flow:
 * 1. [requestRecording] — called from the timeline editor when the user taps "Record Touch".
 *    Sets [recordingRequested] to `true`, which [ScreenCaptureService] observes to show
 *    [RecordingMirrorPresentation] on the secondary display.
 * 2. [onTapRecorded] — called by [RecordingMirrorPresentation] when the user taps the mirror.
 *    Stores the normalised coordinates in [recordedTap] and resets [recordingRequested].
 * 3. The timeline editor observes [recordedTap] via `LaunchedEffect` and appends a
 *    [MacroStep.TouchTap] to the step list, then calls [consumeRecordedTap] to clear the value.
 */
object TouchRecordingManager {

    private val _recordingRequested = MutableStateFlow(false)
    /** `true` while the recording mirror is expected to be shown. */
    val recordingRequested: StateFlow<Boolean> = _recordingRequested.asStateFlow()

    private val _recordedTap = MutableStateFlow<Pair<Float, Float>?>(null)
    /**
     * Normalised tap position (normX in 0..1, normY in 0..1) recorded by the user,
     * or `null` if no tap has been recorded yet / after consumption.
     */
    val recordedTap: StateFlow<Pair<Float, Float>?> = _recordedTap.asStateFlow()

    /**
     * Signals that the recording mirror should be shown. Clears any stale recorded tap first.
     */
    fun requestRecording() {
        AppLog.i(TAG, "requestRecording")
        _recordedTap.value = null
        _recordingRequested.value = true
    }

    /**
     * Called by [RecordingMirrorPresentation] when the user taps the mirror.
     * Stores the coordinates and clears the recording request.
     */
    fun onTapRecorded(normX: Float, normY: Float) {
        AppLog.i(TAG, "onTapRecorded normX=$normX normY=$normY")
        _recordedTap.value = Pair(normX, normY)
        _recordingRequested.value = false
    }

    /**
     * Cancels an in-progress recording. Clears both the request and any partial result.
     */
    fun cancelRecording() {
        AppLog.i(TAG, "cancelRecording")
        _recordingRequested.value = false
        _recordedTap.value = null
    }

    /**
     * Called by the timeline editor after it has appended the [MacroStep.TouchTap].
     * Resets [recordedTap] to `null` so the `LaunchedEffect` does not re-trigger.
     */
    fun consumeRecordedTap() {
        AppLog.d(TAG, "consumeRecordedTap")
        _recordedTap.value = null
    }
}
