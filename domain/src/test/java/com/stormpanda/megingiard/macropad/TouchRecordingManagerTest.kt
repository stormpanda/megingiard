package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
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
            samples = listOf(
                TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                TouchSample(offsetMs = 10L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.2f),
            ),
            startOffsetMs = 0L,
        )
        TouchRecordingManager.recordGestureCompleted(
            samples = listOf(
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
            samples = listOf(
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
            samples = listOf(
                TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.1f, normY = 0.2f),
                TouchSample(offsetMs = 10L, pointerId = 0, action = TouchAction.UP, normX = 0.1f, normY = 0.2f),
            ),
            startOffsetMs = 500L,
        )
        TouchRecordingManager.recordGestureCompleted(
            samples = listOf(
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
            samples = listOf(
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
}
