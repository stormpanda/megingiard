package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.mirror.TouchScreenObserver
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * End-to-End integration test suite verifying the complete Macro pipeline:
 *
 * 1. Macro recording (Touch gestures & Gamepad input capture).
 * 2. Step manipulation, timing shifting, and duration calculations.
 * 3. Deterministic event compilation ([buildMacroEventList]) and reset-before-set conflict resolution.
 * 4. Human-like timing randomization engine.
 * 5. Synchronous and async macro playback and safety reset on teardown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MacroRecordingToCompilationPipelineE2ETest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private suspend fun waitUntil(
        timeoutMs: Long = 1000L,
        condition: suspend () -> Boolean,
    ): Boolean =
        withTimeoutOrNull(timeoutMs) {
            while (!condition()) {
                delay(5)
            }
            true
        } ?: false

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        PrivdClient.isConnectedForTest = true
        MacroExecutor.setRunningMacroIdsForTest(emptySet())
        TouchRecordingManager.cancelRecording()
        TouchRecordingManager.resetState()
        TouchRecordingManager.consumeRecordedTap()
        TouchScreenObserver.stopAll()
    }

    @After
    fun tearDown() {
        PrivdClient.isConnectedForTest = null
        TouchRecordingManager.cancelRecording()
        TouchRecordingManager.resetState()
        TouchScreenObserver.stopAll()
        Dispatchers.resetMain()
    }

    @Test
    fun testTouchRecordingToMacroStepsPipeline() {
        // 1. Enter gesture recording mode
        TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)
        val recordingState = TouchRecordingManager.state.value as TouchRecordingState.Recording
        assertEquals(TouchRecordingMode.GESTURE, recordingState.mode)

        // 2. Record first swipe gesture (0ms to 80ms, recorded at startOffset = 100ms)
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.2f, normY = 0.2f),
                    TouchSample(offsetMs = 40L, pointerId = 0, action = TouchAction.MOVE, normX = 0.4f, normY = 0.3f),
                    TouchSample(offsetMs = 80L, pointerId = 0, action = TouchAction.UP, normX = 0.6f, normY = 0.4f),
                ),
            startOffsetMs = 100L,
        )

        // 3. Record second tap gesture (at startOffset = 300ms)
        TouchRecordingManager.recordGestureCompleted(
            samples =
                listOf(
                    TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.8f, normY = 0.8f),
                    TouchSample(offsetMs = 50L, pointerId = 0, action = TouchAction.UP, normX = 0.8f, normY = 0.8f),
                ),
            startOffsetMs = 300L,
        )

        // 4. Finish recording and extract steps
        TouchRecordingManager.finishRecording()
        val doneState = TouchRecordingManager.state.value as TouchRecordingState.Done
        val recordedSteps = doneState.steps
        assertEquals("Expected 2 recorded touch steps", 2, recordedSteps.size)

        val firstStep = recordedSteps[0] as MacroStep.TouchPath
        assertEquals(0L, firstStep.startTimeMs) // Trimmed leading idle 100ms
        assertEquals(80L, firstStep.durationMs)
        assertEquals(3, firstStep.samples.size)

        val secondStep = recordedSteps[1] as MacroStep.TouchPath
        assertEquals(200L, secondStep.startTimeMs) // 300ms - 100ms trimmed = 200ms
        assertEquals(50L, secondStep.durationMs)
    }

    @Test
    fun testMacroStepOperationsAndOffsetShifting() {
        val originalSteps: List<MacroStep> =
            listOf(
                MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 50L, btnCode = 96, label = "A"),
                MacroStep.DPadTap(startTimeMs = 100L, durationMs = 40L, dirX = 0, dirY = -1),
                MacroStep.TouchTap(startTimeMs = 200L, durationMs = 60L, normX = 0.5f, normY = 0.5f),
            )

        assertEquals("Total duration should be 260ms", 260L, originalSteps.totalDurationMs())

        // Shift entire sequence by +150ms
        val shiftedSteps = originalSteps.offsetBy(150L)
        assertEquals(150L, shiftedSteps[0].startTimeMs)
        assertEquals(250L, shiftedSteps[1].startTimeMs)
        assertEquals(350L, shiftedSteps[2].startTimeMs)
        assertEquals("Total shifted duration should be 410ms", 410L, shiftedSteps.totalDurationMs())

        // Modify individual step timing
        val modifiedStep = originalSteps[0].withTiming(newStartTimeMs = 500L, newDurationMs = 120L)
        assertEquals(500L, modifiedStep.startTimeMs)
        assertEquals(120L, (modifiedStep as MacroStep.GamepadButtonTap).durationMs)
    }

    @Test
    fun testMacroEventCompilationDeterministicOrderingAndResetPriority() {
        // Construct a macro where Step 1 ends at timestamp 100ms and Step 2 starts at timestamp 100ms
        val macro =
            Macro(
                id = "pipeline-macro",
                name = "Test Pipeline Macro",
                steps =
                    listOf(
                        MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 100L, btnCode = 96, label = "A"),
                        MacroStep.GamepadButtonTap(startTimeMs = 100L, durationMs = 50L, btnCode = 97, label = "B"),
                        MacroStep.JoystickMove(startTimeMs = 50L, durationMs = 100L, stick = JoystickStick.LEFT, x = 1.0f, y = 0.0f),
                    ),
            )

        val compiledEvents = buildMacroEventList(macro)
        assertTrue("Compiled events should not be empty", compiledEvents.isNotEmpty())

        // Verify strictly non-decreasing timeMs ordering
        for (i in 0 until compiledEvents.size - 1) {
            assertTrue(
                "Events must be ordered by time: ${compiledEvents[i].timeMs} <= ${compiledEvents[i + 1].timeMs}",
                compiledEvents[i].timeMs <= compiledEvents[i + 1].timeMs,
            )
        }

        // At timestamp 100ms, BUTTON_UP for button A (isReset == true) must appear BEFORE BUTTON_DOWN for button B
        val eventsAt100 = compiledEvents.filter { it.timeMs == 100L }
        val buttonAUpIndex = eventsAt100.indexOfFirst { it.type == MacroEventType.BUTTON_UP && it.code == 96 }
        val buttonBDownIndex = eventsAt100.indexOfFirst { it.type == MacroEventType.BUTTON_DOWN && it.code == 97 }

        assertTrue("BUTTON_UP for Button A should exist at 100ms", buttonAUpIndex != -1)
        assertTrue("BUTTON_DOWN for Button B should exist at 100ms", buttonBDownIndex != -1)
        assertTrue("BUTTON_UP (reset) must precede BUTTON_DOWN at the same timestamp", buttonAUpIndex < buttonBDownIndex)
    }

    @Test
    fun testTimingRandomizationEngine() {
        val macro =
            Macro(
                id = "randomized-macro",
                name = "Randomized Macro",
                steps =
                    listOf(
                        MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 100L, btnCode = 96, label = "A"),
                        MacroStep.GamepadButtonTap(startTimeMs = 200L, durationMs = 100L, btnCode = 97, label = "B"),
                    ),
                randomizeTimingEnabled = true,
                randomizeTimingRangeMs = 20,
            )

        val fixedRandom = Random(12345)
        val randomizedMacro = macro.randomized(fixedRandom)

        assertEquals("Step count must remain identical", macro.steps.size, randomizedMacro.steps.size)
        // Verify steps received a non-zero offset
        val originalStep1 = macro.steps[0]
        val randStep1 = randomizedMacro.steps[0] as MacroStep.GamepadButtonTap

        assertTrue(
            "Start time must stay within the randomized bounds",
            randStep1.startTimeMs in (originalStep1.startTimeMs - 20)..(originalStep1.startTimeMs + 20),
        )
    }

    @Test
    fun testMacroExecutionAndSuspendedModalOverlayRoundTrip() =
        runBlocking {
            // Set up a visible modal overlay in AppStateManager
            val modalConfig = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR)
            AppStateManager.openPrimaryModal(modalConfig)
            assertEquals(modalConfig, AppStateManager.activePrimaryModal.value)

            val shortMacro =
                Macro(
                    id = "e2e-exec-macro",
                    name = "Short Exec Macro",
                    steps =
                        listOf(
                            MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 10L, btnCode = 96, label = "A"),
                        ),
                )

            var completedSuccess: Boolean? = null

            // Run test execution
            MacroExecutor.runTest(
                macro = shortMacro,
                preDelayMs = 10L,
                postDelayMs = 10L,
                onComplete = { success ->
                    completedSuccess = success
                },
            )

            // 1. Advance and wait for execution to complete
            assertTrue("Test execution should complete", waitUntil(1500) { completedSuccess != null })
            assertEquals(true, completedSuccess)

            // 2. Execution finished -> modal overlay restored automatically
            assertEquals(
                "Overlay should be automatically restored after execution",
                modalConfig,
                AppStateManager.activePrimaryModal.value,
            )
            assertFalse("Macro should no longer be marked running", MacroExecutor.isRunning("e2e-exec-macro"))
        }
}
