package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
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
import org.junit.Before
import org.junit.Test

private const val TAG = "MacroExecutorTest"

@OptIn(ExperimentalCoroutinesApi::class)
class MacroExecutorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMacroExecutionCompletesAndClearsRunningId() =
        runBlocking {
            val macro =
                Macro(
                    id = "test-macro",
                    name = "Test Macro",
                    steps =
                        listOf(
                            MacroStep.GamepadButtonTap(
                                startTimeMs = 0L,
                                durationMs = 5L,
                                btnCode = 96,
                                label = "A",
                            ),
                        ),
                )

            // Reset state before running
            MacroExecutor.setRunningMacroIdsForTest(emptySet())

            AppLog.d(TAG, "testMacroExecutionCompletesAndClearsRunningId: starting execute")
            // Execute macro
            MacroExecutor.execute(macro)

            // Ensure it has started running
            val started =
                withTimeoutOrNull(500) {
                    while (!MacroExecutor.isRunning(macro.id)) {
                        delay(5)
                    }
                    true
                }
            assertEquals("Macro should start running", true, started)

            // Wait/poll for the macro to finish executing
            val success =
                withTimeoutOrNull(1000) {
                    while (MacroExecutor.isRunning(macro.id)) {
                        delay(5)
                    }
                    true
                }

            assertEquals("Macro should complete within timeout", true, success)
            assertFalse("Running ID should be cleared from MacroExecutor state", MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testMacroStopClearsRunningId() =
        runBlocking {
            val macro =
                Macro(
                    id = "long-macro",
                    name = "Long Macro",
                    steps =
                        listOf(
                            MacroStep.GamepadButtonTap(
                                startTimeMs = 0L,
                                durationMs = 500L,
                                btnCode = 96,
                                label = "A",
                            ),
                        ),
                )

            // Reset state before running
            MacroExecutor.setRunningMacroIdsForTest(emptySet())

            AppLog.d(TAG, "testMacroStopClearsRunningId: starting execute")
            // Execute macro
            MacroExecutor.execute(macro)

            // Ensure it has started running
            val started =
                withTimeoutOrNull(200) {
                    while (!MacroExecutor.isRunning(macro.id)) {
                        delay(5)
                    }
                    true
                }
            assertEquals("Macro should start running", true, started)

            // Stop the macro
            MacroExecutor.stop(macro.id)

            // Wait/poll for the macro to stop and clear its state
            val stopped =
                withTimeoutOrNull(200) {
                    while (MacroExecutor.isRunning(macro.id)) {
                        delay(5)
                    }
                    true
                }

            assertEquals("Macro should stop within timeout", true, stopped)
            assertFalse("Running ID should be cleared after stopping", MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testExecuteAndWaitSuspendsUntilMacroFinishes() =
        runBlocking {
            val macro =
                Macro(
                    id = "sync-macro",
                    name = "Sync Macro",
                    steps =
                        listOf(
                            MacroStep.GamepadButtonTap(
                                startTimeMs = 0L,
                                durationMs = 20L,
                                btnCode = 96,
                                label = "A",
                            ),
                        ),
                )

            MacroExecutor.setRunningMacroIdsForTest(emptySet())
            MacroExecutor.executeAndWait(macro)

            assertFalse("Macro should have finished and cleared running status", MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testRunTestRunExecutesAndCallsCompletion() =
        runBlocking {
            val macro =
                Macro(
                    id = "test-run-macro",
                    name = "Test Run Macro",
                    steps =
                        listOf(
                            MacroStep.GamepadButtonTap(
                                startTimeMs = 0L,
                                durationMs = 10L,
                                btnCode = 96,
                                label = "A",
                            ),
                        ),
                )

            var completed = false
            MacroExecutor.setRunningMacroIdsForTest(emptySet())
            MacroExecutor.runTestRun(
                macro = macro,
                preDelayMs = 10L,
                postDelayMs = 10L,
                onComplete = { completed = true },
            )

            // Wait for completion
            val finished =
                withTimeoutOrNull(1000) {
                    while (!completed) {
                        delay(10)
                    }
                    true
                }

            assertEquals("Test Run should invoke completion callback", true, finished)
        }
}
