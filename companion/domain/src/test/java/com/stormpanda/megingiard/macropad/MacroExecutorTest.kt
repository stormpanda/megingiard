package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppStateManager
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

@OptIn(ExperimentalCoroutinesApi::class)
class MacroExecutorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun testMacro(
        id: String = "test-macro",
        durationMs: Long = 5L,
    ) = Macro(
        id = id,
        name = "Test Macro",
        steps = listOf(MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = durationMs, btnCode = 96, label = "A")),
    )

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
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        PrivdClient.isConnectedForTest = true
        MacroExecutor.setRunningMacroIdsForTest(emptySet())
    }

    @After
    fun tearDown() {
        PrivdClient.isConnectedForTest = null
        Dispatchers.resetMain()
    }

    @Test
    fun testMacroExecutionRejectedWhenPrivdDisconnected() =
        runBlocking {
            PrivdClient.isConnectedForTest = false
            val macro = testMacro("unprivileged-macro")

            MacroExecutor.execute(macro)
            assertFalse(MacroExecutor.isRunning(macro.id))

            var testRunSuccess: Boolean? = null
            MacroExecutor.runTest(macro = macro, onComplete = { testRunSuccess = it })
            assertEquals(false, testRunSuccess)
        }

    @Test
    fun testMacroExecutionCompletesAndClearsRunningId() =
        runBlocking {
            val macro = testMacro()
            MacroExecutor.execute(macro)

            assertTrue(waitUntil(500) { MacroExecutor.isRunning(macro.id) })
            assertTrue(waitUntil(1000) { !MacroExecutor.isRunning(macro.id) })
            assertFalse(MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testMacroStopClearsRunningId() =
        runBlocking {
            val macro = testMacro("long-macro", durationMs = 500L)
            MacroExecutor.execute(macro)

            assertTrue(waitUntil(200) { MacroExecutor.isRunning(macro.id) })
            MacroExecutor.stop(macro.id)
            assertTrue(waitUntil(200) { !MacroExecutor.isRunning(macro.id) })
            assertFalse(MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testExecuteAndWaitSuspendsUntilMacroFinishes() =
        runBlocking {
            val macro = testMacro("sync-macro", durationMs = 20L)
            MacroExecutor.executeAndWait(macro)
            assertFalse(MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testRunTestSuspendsAndResumesModal() =
        runBlocking {
            val modalConfig = PrimaryModalConfig(type = PrimaryModalType.MACROPAD_EDITOR)
            AppStateManager.openPrimaryModal(modalConfig)
            assertEquals(modalConfig, AppStateManager.activePrimaryModal.value)

            val macro = testMacro("test-run-macro", durationMs = 10L)
            var completedSuccess: Boolean? = null
            MacroExecutor.runTest(
                macro = macro,
                preDelayMs = 5L,
                postDelayMs = 5L,
                onComplete = { completedSuccess = it },
            )

            assertTrue(waitUntil(1000) { completedSuccess != null })
            assertEquals(true, completedSuccess)
            assertEquals(modalConfig, AppStateManager.activePrimaryModal.value)
        }
}
