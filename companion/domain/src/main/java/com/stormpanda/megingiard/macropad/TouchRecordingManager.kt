package com.stormpanda.megingiard.macropad

import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TouchRecordingManager"
private const val TRM_MAX_TRAIL_POINTS = 40
private const val TRM_DEFAULT_TAP_DURATION_MS = 100L
private const val CLIENT_TOKEN = "TouchRecordingManager"

enum class TouchRecordingMode { TAP, GESTURE }

/**
 * Live state for a single active touch pointer on the screen.
 *
 * @property slot Pointer slot index (0..9) identifying the finger.
 * @property normX Normalised X coordinate [0.0, 1.0].
 * @property normY Normalised Y coordinate [0.0, 1.0].
 * @property trail Historical path coordinates for this pointer's current stroke.
 */
data class TouchPointerState(
    val slot: Int,
    val normX: Float,
    val normY: Float,
    val trail: List<Pair<Float, Float>> = emptyList(),
)

sealed interface TouchRecordingState {
    data object Idle : TouchRecordingState

    data class Recording(
        val mode: TouchRecordingMode,
        val recordedGestureCount: Int = 0,
        val totalRecordedSampleCount: Int = 0,
        val startElapsedRealtime: Long = 0L,
        val activePointers: List<TouchPointerState> = emptyList(),
        val liveNormX: Float? = activePointers.firstOrNull()?.normX,
        val liveNormY: Float? = activePointers.firstOrNull()?.normY,
        val isTouchDown: Boolean = activePointers.isNotEmpty(),
        val activePointersCount: Int = activePointers.size,
        val activeTrailPoints: List<Pair<Float, Float>> = activePointers.flatMap { it.trail },
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
 *    [TouchRecordingSheet] with live pointer indicators, a 16:9 touch radar monitor, and Cancel / Stop controls.
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

    private class PointerTracker(
        val slot: Int,
        var normX: Float,
        var normY: Float,
        val trail: MutableList<Pair<Float, Float>> = mutableListOf(),
    ) {
        fun toPointerState(): TouchPointerState =
            TouchPointerState(
                slot = slot,
                normX = normX,
                normY = normY,
                trail = trail.toList(),
            )
    }

    private var sessionStartEpochMs = 0L
    private var gestureSegmentStartEpochMs = 0L
    private var gestureSegmentStartOffsetMs = 0L
    private var isGestureSegmentRecording = false
    private val activePointers = mutableMapOf<Int, PointerTracker>()
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
        activePointers.clear()
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
        TouchScreenObserver.start(CLIENT_TOKEN)
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
                        val tracker = PointerTracker(slot, normX, normY)
                        tracker.trail.add(Pair(normX, normY))
                        activePointers[slot] = tracker

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
                        val tracker = activePointers[slot]
                        if (tracker != null) {
                            tracker.normX = normX
                            tracker.normY = normY
                            tracker.trail.add(Pair(normX, normY))
                            if (tracker.trail.size > TRM_MAX_TRAIL_POINTS) tracker.trail.removeAt(0)

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
                        val tracker = activePointers.remove(slot)
                        if (tracker != null) {
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

                            if (activePointers.isEmpty() && isGestureSegmentRecording && currentGestureSamples.isNotEmpty()) {
                                isGestureSegmentRecording = false
                                recordGestureCompleted(
                                    samples = currentGestureSamples.toList(),
                                    startOffsetMs = gestureSegmentStartOffsetMs,
                                )
                                currentGestureSamples.clear()
                            }
                        }
                    }
                }

                updateLivePointers(activePointers.values.map { it.toPointerState() })
            }
        }
    }

    /**
     * Updates live touch pointer states for the secondary companion monitor radar.
     */
    fun updateLivePointers(pointers: List<TouchPointerState>) {
        val current = _state.value as? TouchRecordingState.Recording ?: return
        val primary = pointers.firstOrNull()
        val allTrail = pointers.flatMap { it.trail }
        _state.value =
            current.copy(
                activePointers = pointers,
                liveNormX = primary?.normX,
                liveNormY = primary?.normY,
                isTouchDown = pointers.isNotEmpty(),
                activePointersCount = pointers.size,
                activeTrailPoints = allTrail,
            )
    }

    /**
     * Updates live touch pointer states for testing or explicit updates.
     */
    fun updateLivePointerState(
        normX: Float?,
        normY: Float?,
        isDown: Boolean,
        activePointersCount: Int,
        activeTrailPoints: List<Pair<Float, Float>>,
    ) {
        val current = _state.value as? TouchRecordingState.Recording ?: return
        val pointers =
            if (normX != null && normY != null && isDown) {
                listOf(TouchPointerState(slot = 0, normX = normX, normY = normY, trail = activeTrailPoints))
            } else {
                emptyList()
            }
        _state.value =
            current.copy(
                activePointers = pointers,
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
        TouchScreenObserver.stop(CLIENT_TOKEN)
        val step =
            MacroStep.TouchTap(
                startTimeMs = 0L,
                durationMs = TRM_DEFAULT_TAP_DURATION_MS,
                normX = normX,
                normY = normY,
            )
        _recordedTap.value = Pair(normX, normY)
        _state.value = TouchRecordingState.Done(listOf(step))
        _recordingRequested.value = false
        AppStateManager.resumeSuspended()
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
                activePointers = emptyList(),
                isTouchDown = false,
                activePointersCount = 0,
                activeTrailPoints = emptyList(),
                liveNormX = null,
                liveNormY = null,
            )
    }

    fun finishRecording() {
        if (_state.value !is TouchRecordingState.Recording) {
            AppLog.w(TAG, "finishRecording called while not recording — ignored")
            return
        }
        TouchScreenObserver.stop(CLIENT_TOKEN)
        AppLog.i(TAG, "finishRecording steps=${recordedGestureSteps.size}")
        val sorted = recordedGestureSteps.sortedBy { it.startTimeMs }
        val trimmed = trimLeadingIdle(sorted)
        _state.value = TouchRecordingState.Done(trimmed)
        recordedGestureSteps.clear()
        activePointers.clear()
        _recordingRequested.value = false
    }

    /**
     * Cancels an in-progress recording. Clears both the request and any partial result.
     */
    fun cancelRecording() {
        AppLog.i(TAG, "cancelRecording")
        TouchScreenObserver.stop(CLIENT_TOKEN)
        _recordingRequested.value = false
        _recordedTap.value = null
        recordedGestureSteps.clear()
        activePointers.clear()
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
        TouchScreenObserver.stop(CLIENT_TOKEN)
        recordedGestureSteps.clear()
        activePointers.clear()
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
