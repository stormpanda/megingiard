package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val TAG = "MacroExecutorTest"

@OptIn(ExperimentalCoroutinesApi::class)
class MacroExecutorTest {
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
}
