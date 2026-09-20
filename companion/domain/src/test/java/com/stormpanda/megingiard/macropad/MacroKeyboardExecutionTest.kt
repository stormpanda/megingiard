package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.privd.PrivdClient
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
class MacroKeyboardExecutionTest {
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
    fun testKeyInjectorValidationSupportsFullLinuxKeycodeRange() {
        // Valid keycodes: 1 to KEY_MAX (464)
        assertTrue(KeyInjector.isValidKeycode(LinuxKeycodes.KEY_ESC)) // 1
        assertTrue(KeyInjector.isValidKeycode(LinuxKeycodes.KEY_SPACE)) // 57
        assertTrue(KeyInjector.isValidKeycode(LinuxKeycodes.KEY_FN)) // 464
        assertTrue(KeyInjector.isValidKeycode(LinuxKeycodes.KEY_MAX)) // 464

        // Invalid keycodes
        assertFalse(KeyInjector.isValidKeycode(0))
        assertFalse(KeyInjector.isValidKeycode(-1))
        assertFalse(KeyInjector.isValidKeycode(465))
        assertFalse(KeyInjector.isValidKeycode(1000))
    }

    @Test
    fun testKeyboardMacroExecutesAndCompletes() =
        runBlocking {
            val macro =
                Macro(
                    id = "kb-macro-test",
                    name = "Type Hello",
                    steps =
                        listOf(
                            MacroStep.KeyboardKeyTap(
                                startTimeMs = 0L,
                                durationMs = 10L,
                                keycode = LinuxKeycodes.KEY_H,
                                label = "H",
                            ),
                            MacroStep.KeyboardKeyTap(
                                startTimeMs = 15L,
                                durationMs = 10L,
                                keycode = LinuxKeycodes.KEY_E,
                                label = "E",
                            ),
                        ),
                )

            MacroExecutor.execute(macro)
            assertTrue(waitUntil(500) { MacroExecutor.isRunning(macro.id) })
            assertTrue(waitUntil(1000) { !MacroExecutor.isRunning(macro.id) })
            assertFalse(MacroExecutor.isRunning(macro.id))
        }

    @Test
    fun testKeyboardMacroEarlyStopClearsRunningState() =
        runBlocking {
            val macro =
                Macro(
                    id = "kb-long-macro",
                    name = "Long Typing Sequence",
                    steps =
                        listOf(
                            MacroStep.KeyboardKeyTap(
                                startTimeMs = 0L,
                                durationMs = 300L,
                                keycode = LinuxKeycodes.KEY_A,
                                label = "A",
                                modifiers = listOf(LinuxKeycodes.KEY_LEFTCTRL),
                            ),
                        ),
                )

            MacroExecutor.execute(macro)
            assertTrue(waitUntil(200) { MacroExecutor.isRunning(macro.id) })
            MacroExecutor.stop(macro.id)
            assertTrue(waitUntil(200) { !MacroExecutor.isRunning(macro.id) })
            assertFalse(MacroExecutor.isRunning(macro.id))
        }
}
