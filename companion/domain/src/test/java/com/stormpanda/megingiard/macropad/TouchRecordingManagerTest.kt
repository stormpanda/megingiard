package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TouchRecordingManagerTest {
    private fun sample(
        offsetMs: Long = 0L,
        pointerId: Int = 0,
        action: TouchAction = TouchAction.DOWN,
        normX: Float = 0.1f,
        normY: Float = 0.2f,
    ) = TouchSample(offsetMs, pointerId, action, normX, normY)

    private fun sendTouch(
        slot: Int = 0,
        action: TouchAction,
        normX: Float,
        normY: Float,
    ) {
        TouchScreenObserver.onTouchEvent?.invoke(slot, action, normX, normY)
    }

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

        val state = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(TouchRecordingMode.GESTURE, state.mode)
        assertEquals(0, state.recordedGestureCount)
        assertEquals(true, TouchRecordingManager.recordingRequested.value)
    }

    @Test
    fun `multiple gesture segments finish as separate touch path steps`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        TouchRecordingManager.recordGestureCompleted(
            samples = listOf(sample(0L, action = TouchAction.DOWN), sample(10L, action = TouchAction.UP)),
            startOffsetMs = 0L,
        )
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    sample(0L, action = TouchAction.DOWN, normX = 0.5f, normY = 0.6f),
                    sample(20L, action = TouchAction.MOVE, normX = 0.7f, normY = 0.8f),
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
        TouchRecordingManager.recordGestureCompleted(samples = listOf(sample()), startOffsetMs = 0L)

        TouchRecordingManager.cancelRecording()

        assertEquals(TouchRecordingState.Idle, TouchRecordingManager.state.value)
        assertEquals(false, TouchRecordingManager.recordingRequested.value)
    }

    @Test
    fun `leading idle time is trimmed on finish`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        TouchRecordingManager.recordGestureCompleted(
            samples = listOf(sample(0L, action = TouchAction.DOWN), sample(10L, action = TouchAction.UP)),
            startOffsetMs = 500L,
        )
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    sample(0L, action = TouchAction.DOWN, normX = 0.5f, normY = 0.6f),
                    sample(20L, action = TouchAction.UP, normX = 0.5f, normY = 0.6f),
                ),
            startOffsetMs = 800L,
        )

        TouchRecordingManager.finishRecording()

        val done = TouchRecordingManager.state.value as TouchRecordingState.Done
        val first = done.steps[0] as MacroStep.TouchPath
        val second = done.steps[1] as MacroStep.TouchPath
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
            samples = listOf(sample(0L, action = TouchAction.DOWN), sample(10L, action = TouchAction.UP)),
            startOffsetMs = 0L,
        )

        val state = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(TouchRecordingMode.TAP, state.mode)
        assertEquals(0, state.recordedGestureCount)
    }

    @Test
    fun `updateLivePointerState updates recording state fields`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        TouchRecordingManager.updateLivePointerState(
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
        assertEquals(1, state.activePointers.size)
    }

    @Test
    fun `updateLivePointers updates multi-touch pointer states with isolated trails`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        val pointer0 = TouchPointerState(slot = 0, normX = 0.2f, normY = 0.3f, trail = listOf(Pair(0.2f, 0.3f), Pair(0.25f, 0.35f)))
        val pointer1 = TouchPointerState(slot = 1, normX = 0.8f, normY = 0.7f, trail = listOf(Pair(0.8f, 0.7f), Pair(0.75f, 0.65f)))

        TouchRecordingManager.updateLivePointers(listOf(pointer0, pointer1))

        val state = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(2, state.activePointersCount)
        assertEquals(2, state.activePointers.size)
        assertEquals(true, state.isTouchDown)
        assertEquals(0.2f, state.activePointers[0].normX)
        assertEquals(0.8f, state.activePointers[1].normX)
        assertEquals(2, state.activePointers[0].trail.size)
        assertEquals(2, state.activePointers[1].trail.size)
        assertEquals(4, state.activeTrailPoints.size)
    }

    @Test
    fun `onTapRecorded records normalized coordinates and resets request`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
        assertEquals(true, TouchRecordingManager.recordingRequested.value)

        TouchRecordingManager.onTapRecorded(0.33f, 0.66f)

        assertEquals(Pair(0.33f, 0.66f), TouchRecordingManager.recordedTap.value)
        assertEquals(false, TouchRecordingManager.recordingRequested.value)
        val done = TouchRecordingManager.state.value as TouchRecordingState.Done
        assertEquals(1, done.steps.size)
        val tapStep = done.steps.first() as MacroStep.TouchTap
        assertEquals(0.33f, tapStep.normX, 0.001f)
        assertEquals(0.66f, tapStep.normY, 0.001f)

        TouchRecordingManager.consumeRecordedTap()
        assertEquals(null, TouchRecordingManager.recordedTap.value)
    }

    @Test
    fun `onTouchEvent in TAP mode triggers onTapRecorded on touch event`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
        assertEquals(true, TouchRecordingManager.recordingRequested.value)

        sendTouch(0, TouchAction.DOWN, 0.42f, 0.88f)

        assertEquals(Pair(0.42f, 0.88f), TouchRecordingManager.recordedTap.value)
        assertEquals(false, TouchRecordingManager.recordingRequested.value)
        val done = TouchRecordingManager.state.value as TouchRecordingState.Done
        assertEquals(1, done.steps.size)
        val tapStep = done.steps.first() as MacroStep.TouchTap
        assertEquals(0.42f, tapStep.normX, 0.001f)
        assertEquals(0.88f, tapStep.normY, 0.001f)
    }

    @Test
    fun `onTouchEvent in GESTURE mode records gesture segment on pointer release`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        sendTouch(0, TouchAction.DOWN, 0.1f, 0.2f)
        sendTouch(0, TouchAction.MOVE, 0.3f, 0.4f)
        sendTouch(0, TouchAction.UP, 0.5f, 0.6f)

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

    @Test
    fun `onTouchEvent in GESTURE mode tracks simultaneous multi-touch pointers and maintains separate trails`() {
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)

        sendTouch(0, TouchAction.DOWN, 0.1f, 0.2f)
        sendTouch(0, TouchAction.MOVE, 0.15f, 0.25f)
        sendTouch(1, TouchAction.DOWN, 0.8f, 0.9f)
        sendTouch(1, TouchAction.MOVE, 0.85f, 0.95f)

        val recordingState = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(2, recordingState.activePointersCount)
        assertEquals(2, recordingState.activePointers.size)
        assertEquals(true, recordingState.isTouchDown)

        val p0 = recordingState.activePointers.first { it.slot == 0 }
        val p1 = recordingState.activePointers.first { it.slot == 1 }
        assertEquals(0.15f, p0.normX)
        assertEquals(0.25f, p0.normY)
        assertEquals(2, p0.trail.size)
        assertEquals(0.85f, p1.normX)
        assertEquals(0.95f, p1.normY)
        assertEquals(2, p1.trail.size)

        sendTouch(0, TouchAction.UP, 0.15f, 0.25f)
        val midState = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(1, midState.activePointersCount)
        assertEquals(1, midState.activePointers.size)
        assertEquals(0, midState.recordedGestureCount)

        sendTouch(1, TouchAction.UP, 0.85f, 0.95f)
        val finalState = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(0, finalState.activePointersCount)
        assertEquals(1, finalState.recordedGestureCount)

        TouchRecordingManager.finishRecording()
        val done = TouchRecordingManager.state.value as TouchRecordingState.Done
        assertEquals(1, done.steps.size)
        val path = done.steps[0] as MacroStep.TouchPath
        assertEquals(6, path.samples.size)
        assertEquals(3, path.samples.count { it.pointerId == 0 })
        assertEquals(3, path.samples.count { it.pointerId == 1 })
    }
}
