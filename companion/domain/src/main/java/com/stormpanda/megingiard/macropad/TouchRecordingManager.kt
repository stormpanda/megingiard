package com.stormpanda.megingiard.macropad

import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TouchRecordingManager"
private const val TRM_MAX_TRAIL_POINTS = 40

enum class TouchRecordingMode { TAP, GESTURE }

sealed interface TouchRecordingState {
    data object Idle : TouchRecordingState

    data class Recording(
        val mode: TouchRecordingMode,
        val recordedGestureCount: Int = 0,
        val totalRecordedSampleCount: Int = 0,
        val startElapsedRealtime: Long = 0L,
        val liveNormX: Float? = null,
        val liveNormY: Float? = null,
        val isTouchDown: Boolean = false,
        val activePointersCount: Int = 0,
        val activeTrailPoints: List<Pair<Float, Float>> = emptyList(),
    ) : TouchRecordingState

    data class Done(
        val steps: List<MacroStep>,
    ) : TouchRecordingState
}

/**
 * Singleton that coordinates the touch recording flow (taps and gestures) for macro steps.
 *
 * Flow:
 * 1. [requestRecording] — called from the timeline editor when the user taps "Record Touch"
 *    and selects a mode. Sets [recordingRequested] to `true`, suspends the top editor, and starts
 *    [TouchScreenObserver] on `/dev/input/event6`.
 * 2. On Display 0 (Primary Display), touches pass straight to the foreground game/app with zero lag.
 * 3. On Display 4 (Secondary Display), [MainAppScreen] observes [recordingRequested] and displays
 *    [TouchRecordingSheet] with live telemetry, a 16:9 touch radar monitor, and Cancel / Stop controls.
 * 4. [TouchScreenObserver] streams evdev touch events to [handleObservedTouch] in real time.
 * 5. On finish or cancel, [TouchScreenObserver] stops and the top-screen editor is resumed.
 */
object TouchRecordingManager {
    private val _recordingRequested = MutableStateFlow(false)

    /** `true` while the recording sheet is shown on Display 4. */
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

    private var sessionStartEpochMs = 0L
    private var gestureSegmentStartEpochMs = 0L
    private var gestureSegmentStartOffsetMs = 0L
    private var isGestureSegmentRecording = false
    private val activeSlots = mutableSetOf<Int>()
    private val activeTrail = mutableListOf<Pair<Float, Float>>()
    private val currentGestureSamples = mutableListOf<TouchSample>()

    /**
     * Signals that the recording session should begin with the specified mode.
     * Clears any stale recorded inputs first, initializes the session epoch, and starts [TouchScreenObserver].
     */
    fun requestRecording(mode: TouchRecordingMode) {
        val now = SystemClock.elapsedRealtime()
        AppLog.i(TAG, "requestRecording mode=$mode startElapsedRealtime=$now")
        sessionStartEpochMs = now
        gestureSegmentStartEpochMs = 0L
        gestureSegmentStartOffsetMs = 0L
        isGestureSegmentRecording = false
        activeSlots.clear()
        activeTrail.clear()
        currentGestureSamples.clear()
        _recordedTap.value = null
        recordedGestureSteps.clear()
        _state.value =
            TouchRecordingState.Recording(
                mode = mode,
                recordedGestureCount = 0,
                totalRecordedSampleCount = 0,
                startElapsedRealtime = now,
            )
        _recordingMode.value = mode
        _recordingRequested.value = true

        TouchScreenObserver.onTouchEvent = { slot, action, normX, normY ->
            handleObservedTouch(slot, action, normX, normY)
        }
        TouchScreenObserver.start()
    }

    private fun handleObservedTouch(
        slot: Int,
        action: TouchAction,
        normX: Float,
        normY: Float,
    ) {
        val current = _state.value as? TouchRecordingState.Recording ?: return
        val now = SystemClock.elapsedRealtime()

        when (current.mode) {
            TouchRecordingMode.TAP -> {
                if (action == TouchAction.DOWN || action == TouchAction.UP) {
                    AppLog.i(TAG, "Tap recorded via evdev normX=$normX normY=$normY action=$action")
                    onTapRecorded(normX, normY)
                }
            }

            TouchRecordingMode.GESTURE -> {
                when (action) {
                    TouchAction.DOWN -> {
                        if (!isGestureSegmentRecording) {
                            isGestureSegmentRecording = true
                            gestureSegmentStartEpochMs = now
                            gestureSegmentStartOffsetMs = (now - sessionStartEpochMs).coerceAtLeast(0L)
                            currentGestureSamples.clear()
                        }
                        activeSlots.add(slot)
                        activeTrail.add(Pair(normX, normY))
                        if (activeTrail.size > TRM_MAX_TRAIL_POINTS) activeTrail.removeAt(0)

                        val offsetMs = (now - gestureSegmentStartEpochMs).coerceAtLeast(0L)
                        currentGestureSamples.add(
                            TouchSample(
                                offsetMs = offsetMs,
                                pointerId = slot,
                                action = TouchAction.DOWN,
                                normX = normX,
                                normY = normY,
                            ),
                        )
                    }

                    TouchAction.MOVE -> {
                        if (activeSlots.contains(slot)) {
                            activeTrail.add(Pair(normX, normY))
                            if (activeTrail.size > TRM_MAX_TRAIL_POINTS) activeTrail.removeAt(0)

                            val offsetMs = (now - gestureSegmentStartEpochMs).coerceAtLeast(0L)
                            currentGestureSamples.add(
                                TouchSample(
                                    offsetMs = offsetMs,
                                    pointerId = slot,
                                    action = TouchAction.MOVE,
                                    normX = normX,
                                    normY = normY,
                                ),
                            )
                        }
                    }

                    TouchAction.UP -> {
                        if (activeSlots.contains(slot)) {
                            activeSlots.remove(slot)
                            val offsetMs = (now - gestureSegmentStartEpochMs).coerceAtLeast(0L)
                            currentGestureSamples.add(
                                TouchSample(
                                    offsetMs = offsetMs,
                                    pointerId = slot,
                                    action = TouchAction.UP,
                                    normX = normX,
                                    normY = normY,
                                ),
                            )

                            if (activeSlots.isEmpty() && isGestureSegmentRecording && currentGestureSamples.isNotEmpty()) {
                                isGestureSegmentRecording = false
                                recordGestureCompleted(
                                    samples = currentGestureSamples.toList(),
                                    startOffsetMs = gestureSegmentStartOffsetMs,
                                )
                                currentGestureSamples.clear()
                                activeTrail.clear()
                            }
                        }
                    }
                }

                updateLiveTelemetry(
                    normX = normX,
                    normY = normY,
                    isDown = activeSlots.isNotEmpty(),
                    activePointersCount = activeSlots.size,
                    activeTrailPoints = activeTrail.toList(),
                )
            }
        }
    }

    /**
     * Updates live touch telemetry for the secondary companion monitor radar.
     */
    fun updateLiveTelemetry(
        normX: Float?,
        normY: Float?,
        isDown: Boolean,
        activePointersCount: Int,
        activeTrailPoints: List<Pair<Float, Float>>,
    ) {
        val current = _state.value as? TouchRecordingState.Recording ?: return
        _state.value =
            current.copy(
                liveNormX = normX,
                liveNormY = normY,
                isTouchDown = isDown,
                activePointersCount = activePointersCount,
                activeTrailPoints = activeTrailPoints,
            )
    }

    /**
     * Called when a tap is recorded on the primary display in TAP mode.
     * Stores the coordinates and clears the recording request.
     */
    fun onTapRecorded(
        normX: Float,
        normY: Float,
    ) {
        AppLog.i(TAG, "onTapRecorded normX=$normX normY=$normY")
        TouchScreenObserver.stop()
        _recordedTap.value = Pair(normX, normY)
        _state.value = TouchRecordingState.Idle
        _recordingRequested.value = false
    }

    /**
     * Called when a gesture segment is completed.
     * The recording session stays open so more gestures can be added before Stop & Save.
     */
    fun recordGestureCompleted(
        samples: List<TouchSample>,
        startOffsetMs: Long,
    ) {
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
        recordedGestureSteps +=
            MacroStep.TouchPath(
                startTimeMs = startOffsetMs,
                durationMs = durationMs,
                samples = completedSamples,
            )
        val totalSamples = recordedGestureSteps.sumOf { it.samples.size }
        AppLog.i(
            TAG,
            "recordGestureCompleted samples=${samples.size} completedSamples=${completedSamples.size} startOffsetMs=$startOffsetMs totalSteps=${recordedGestureSteps.size}",
        )
        _state.value =
            currentState.copy(
                recordedGestureCount = recordedGestureSteps.size,
                totalRecordedSampleCount = totalSamples,
                isTouchDown = false,
                activePointersCount = 0,
                activeTrailPoints = emptyList(),
            )
    }

    fun finishRecording() {
        if (_state.value !is TouchRecordingState.Recording) {
            AppLog.w(TAG, "finishRecording called while not recording — ignored")
            return
        }
        TouchScreenObserver.stop()
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
        TouchScreenObserver.stop()
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
        TouchScreenObserver.stop()
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
