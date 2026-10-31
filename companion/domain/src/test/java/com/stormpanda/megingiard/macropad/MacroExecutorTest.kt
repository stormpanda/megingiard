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
        PrivdClient.clearQueueForTest()
        MacroExecutor.setRunningMacroIdsForTest(emptySet())
    }

    @After
    fun tearDown() {
        MacroExecutor.stopAll()
        PrivdClient.clearQueueForTest()
        PrivdClient.isConnectedForTest = null
        Dispatchers.resetMain()
    }

    private fun loopMacro(id: String) = testMacro(id, durationMs = 50L).copy(loopEnabled = true)

    private fun profileWith(
        id: String,
        layoutIds: List<String>,
        macros: List<Macro> = emptyList(),
    ) = PadProfile(
        id = id,
        name = id,
        layouts = layoutIds.map { PadLayout(id = it, name = it) },
        activeLayoutId = layoutIds.first(),
        macros = macros,
    )

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
            val macro = testMacro(durationMs = 80L)
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

    @Test
    fun testStopAllCancelsEveryRunningMacro() =
        runBlocking {
            val m1 = loopMacro("loop-1")
            val m2 = loopMacro("loop-2")
            MacroExecutor.execute(m1)
            MacroExecutor.execute(m2)
            assertTrue(waitUntil(500) { MacroExecutor.isRunning(m1.id) && MacroExecutor.isRunning(m2.id) })

            MacroExecutor.stopAll()

            assertTrue(waitUntil(500) { MacroExecutor.runningMacroIds.value.isEmpty() })
        }

    @Test
    fun testStopAllWithNoRunningMacrosIsNoOp() {
        MacroExecutor.stopAll()
        assertTrue(MacroExecutor.runningMacroIds.value.isEmpty())
    }

    @Test
    fun testProfileSwitchStopsRunningMacros() =
        runBlocking {
            val macro = loopMacro("profile-loop")
            MacroPadState.loadFrom(
                listOf(profileWith("pA", listOf("lA"), listOf(macro)), profileWith("pB", listOf("lB"))),
                "pA",
            )
            MacroExecutor.execute(macro)
            assertTrue(waitUntil(500) { MacroExecutor.isRunning(macro.id) })

            MacroPadState.setActiveProfileId("pB")

            assertTrue(waitUntil(500) { !MacroExecutor.isRunning(macro.id) })
        }

    @Test
    fun testReselectingSameProfileKeepsMacrosRunning() =
        runBlocking {
            val macro = loopMacro("same-profile-loop")
            MacroPadState.loadFrom(listOf(profileWith("pA", listOf("lA"), listOf(macro))), "pA")
            MacroExecutor.execute(macro)
            assertTrue(waitUntil(500) { MacroExecutor.isRunning(macro.id) })

            MacroPadState.setActiveProfileId("pA")
            delay(100)

            assertTrue(MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testLayoutSwitchKeepsMacrosRunning() =
        runBlocking {
            val macro = loopMacro("layout-loop")
            MacroPadState.loadFrom(listOf(profileWith("pA", listOf("l1", "l2"), listOf(macro))), "pA")
            MacroExecutor.execute(macro)
            assertTrue(waitUntil(500) { MacroExecutor.isRunning(macro.id) })

            MacroPadState.nextLayout()
            delay(100)

            assertEquals("l2", MacroPadState.activeProfile.value?.activeLayoutId)
            assertTrue(MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testStopAllReleasesOnlyMacroTouchSlotsPreservingHeldTouchTapSlot() =
        runBlocking {
            val touchMacro =
                Macro(
                    id = "touch-hold-macro",
                    name = "Touch Hold Macro",
                    steps =
                        listOf(
                            MacroStep.TouchTap(
                                startTimeMs = 0L,
                                durationMs = 500L,
                                normX = 0.25f,
                                normY = 0.25f,
                            ),
                        ),
                )
            PrivdClient.clearQueueForTest()

            MacroExecutor.execute(touchMacro)
            assertTrue(waitUntil(500) { MacroExecutor.isRunning(touchMacro.id) && PrivdClient.queueSizeForTest > 0 })

            // Drain initial macro TOUCH_DOWN on slot 0, then simulate a concurrently held TouchTap button on slot 8
            val downCommands =
                buildList {
                    while (true) {
                        val cmd = PrivdClient.pollCommandForTest() ?: break
                        add(cmd)
                    }
                }
            assertTrue("Expected macro DOWN on slot 0, got $downCommands", downCommands.any { it.startsWith("D 0 ") })

            injectActionDown(PadAction.TouchTap(normX = 0.75f, normY = 0.75f), touchSlot = MP_TOUCH_TAP_SLOT)
            val buttonDown = PrivdClient.pollCommandForTest()
            assertTrue("Expected button TouchTap DOWN on slot 8, got $buttonDown", buttonDown?.startsWith("D 8 ") == true)

            // Stopping all macros must release slot 0 without nuking held TouchTap slot 8
            MacroExecutor.stopAll()
            assertTrue(waitUntil(500) { !MacroExecutor.isRunning(touchMacro.id) })

            val cleanupCommands =
                buildList {
                    while (true) {
                        val cmd = PrivdClient.pollCommandForTest() ?: break
                        add(cmd)
                    }
                }
            assertTrue("Expected macro cleanup to send U 0, got $cleanupCommands", "U 0\n" in cleanupCommands)
            assertFalse("Macro cleanup must not release held TouchTap slot 8, got $cleanupCommands", "U 8\n" in cleanupCommands)
        }
}
