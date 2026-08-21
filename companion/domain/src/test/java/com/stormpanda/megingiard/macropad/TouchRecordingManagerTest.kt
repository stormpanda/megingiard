package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TouchRecordingManagerTest {
    @Before
    fun resetManager() {
        TouchRecordingManager.cancelRecording()
        TouchRecordingManager.resetState()
        TouchRecordingManager.consumeRecordedTap()
        TouchScreenObserver.stopAll()
    }

    @Test
    fun `request recording enters recording state and clears stale result`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        val state = TouchRecordingManager.state.value
        assertTrue(state is TouchRecordingState.Recording)
        assertEquals(TouchRecordingMode.GESTURE, (state as TouchRecordingState.Recording).mode)
        assertEquals(0, state.recordedGestureCount)
        assertEquals(true, TouchRecordingManager.recordingRequested.value)
    }

    @Test
    fun `multiple gesture segments finish as separate touch path steps`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                    TouchSample(offsetMs = 10L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.2f),
                ),
            startOffsetMs = 0L,
        )
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.5f, normY = 0.6f),
                    TouchSample(offsetMs = 20L, pointerId = 0, action = TouchAction.MOVE, normX = 0.7f, normY = 0.8f),
                ),
            startOffsetMs = 100L,
        )

        TouchRecordingManager.finishRecording()

        val done = TouchRecordingManager.state.value as TouchRecordingState.Done
        assertEquals(2, done.steps.size)
        val first = done.steps[0] as MacroStep.TouchPath
        val second = done.steps[1] as MacroStep.TouchPath
        assertEquals(0L, first.startTimeMs)
        assertEquals(10L, first.durationMs)
        assertEquals(100L, second.startTimeMs)
        assertEquals(30L, second.durationMs)
        assertEquals(TouchAction.UP, second.samples.last().action)
        assertEquals(0.7f, second.samples.last().normX)
        assertEquals(0.8f, second.samples.last().normY)
    }

    @Test
    fun `cancel recording discards accumulated gestures`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                ),
            startOffsetMs = 0L,
        )

        TouchRecordingManager.cancelRecording()

        assertEquals(TouchRecordingState.Idle, TouchRecordingManager.state.value)
        assertEquals(false, TouchRecordingManager.recordingRequested.value)
    }

    @Test
    fun `leading idle time is trimmed on finish`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        // First gesture starts 500 ms into the session (user waited before touching)
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                    TouchSample(offsetMs = 10L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.2f),
                ),
            startOffsetMs = 500L,
        )
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.5f, normY = 0.6f),
                    TouchSample(offsetMs = 20L, pointerId = 0, action = TouchAction.UP, normX = 0.5f, normY = 0.6f),
                ),
            startOffsetMs = 800L,
        )

        TouchRecordingManager.finishRecording()

        val done = TouchRecordingManager.state.value as TouchRecordingState.Done
        val first = done.steps[0] as MacroStep.TouchPath
        val second = done.steps[1] as MacroStep.TouchPath
        // 500 ms leading idle should be trimmed: first step at 0, second step at 300
        assertEquals(0L, first.startTimeMs)
        assertEquals(300L, second.startTimeMs)
    }

    @Test
    fun `reset state returns done result to idle`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)
        TouchRecordingManager.finishRecording()
        assertTrue(TouchRecordingManager.state.value is TouchRecordingState.Done)

        TouchRecordingManager.resetState()

        assertEquals(TouchRecordingState.Idle, TouchRecordingManager.state.value)
    }

    @Test
    fun `recordGestureCompleted is ignored when recording mode is TAP`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)

        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                    TouchSample(offsetMs = 10L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.2f),
                ),
            startOffsetMs = 0L,
        )

        // State must still be Recording in TAP mode with zero accumulated gestures
        val state = TouchRecordingManager.state.value
        assertTrue(state is TouchRecordingState.Recording)
        assertEquals(TouchRecordingMode.TAP, (state as TouchRecordingState.Recording).mode)
        assertEquals(0, state.recordedGestureCount)
    }

    @Test
    fun `updateLiveTelemetry updates recording state fields`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        TouchRecordingManager.updateLiveTelemetry(
            normX = 0.45f,
            normY = 0.75f,
            isDown = true,
            activePointersCount = 1,
            activeTrailPoints = listOf(Pair(0.45f, 0.75f)),
        )

        val state = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(0.45f, state.liveNormX)
        assertEquals(0.75f, state.liveNormY)
        assertEquals(true, state.isTouchDown)
        assertEquals(1, state.activePointersCount)
        assertEquals(1, state.activeTrailPoints.size)
    }

    @Test
    fun `onTapRecorded records normalized coordinates and resets request`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
        assertEquals(true, TouchRecordingManager.recordingRequested.value)

        TouchRecordingManager.onTapRecorded(0.33f, 0.66f)

        assertEquals(Pair(0.33f, 0.66f), TouchRecordingManager.recordedTap.value)
        assertEquals(false, TouchRecordingManager.recordingRequested.value)
        assertEquals(TouchRecordingState.Idle, TouchRecordingManager.state.value)

        TouchRecordingManager.consumeRecordedTap()
        assertEquals(null, TouchRecordingManager.recordedTap.value)
    }

    @Test
    fun `onTouchEvent in TAP mode triggers onTapRecorded on touch event`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
        assertEquals(true, TouchRecordingManager.recordingRequested.value)

        TouchScreenObserver.onTouchEvent?.invoke(0, TouchAction.DOWN, 0.42f, 0.88f)

        assertEquals(Pair(0.42f, 0.88f), TouchRecordingManager.recordedTap.value)
        assertEquals(false, TouchRecordingManager.recordingRequested.value)
        assertEquals(TouchRecordingState.Idle, TouchRecordingManager.state.value)
    }

    @Test
    fun `onTouchEvent in GESTURE mode records gesture segment on pointer release`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        TouchScreenObserver.onTouchEvent?.invoke(0, TouchAction.DOWN, 0.1f, 0.2f)
        TouchScreenObserver.onTouchEvent?.invoke(0, TouchAction.MOVE, 0.3f, 0.4f)
        TouchScreenObserver.onTouchEvent?.invoke(0, TouchAction.UP, 0.5f, 0.6f)

        val state = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(1, state.recordedGestureCount)
        assertEquals(false, state.isTouchDown)

        TouchRecordingManager.finishRecording()
        val done = TouchRecordingManager.state.value as TouchRecordingState.Done
        assertEquals(1, done.steps.size)
        val path = done.steps[0] as MacroStep.TouchPath
        assertEquals(3, path.samples.size)
        assertEquals(0.1f, path.samples[0].normX)
        assertEquals(0.3f, path.samples[1].normX)
        assertEquals(0.5f, path.samples[2].normX)
    }
}
