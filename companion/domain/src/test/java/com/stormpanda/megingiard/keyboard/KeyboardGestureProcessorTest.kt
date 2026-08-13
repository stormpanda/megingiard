package com.stormpanda.megingiard.keyboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardGestureProcessorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    class TestKeyCodeInjector : KeyCodeInjector {
        val calls = mutableListOf<Pair<String, Int>>()

        override fun keyDown(keycode: Int) {
            calls.add("down" to keycode)
        }

        override fun keyUp(keycode: Int) {
            calls.add("up" to keycode)
        }
    }

    private val grid =
        listOf(
            listOf(
                KeyDef("q", "q", LinuxKeycodes.KEY_Q, popupOptions = listOf("q", "1", "2")),
                KeyDef("w", "w", LinuxKeycodes.KEY_W),
                KeyDef("e", "e", LinuxKeycodes.KEY_E),
            ),
            listOf(
                KeyDef("space", "space", LinuxKeycodes.KEY_SPACE, widthWeight = 3f),
            ),
        )

    @Test
    fun `bounds updates populate key bounds map`() =
        runTest(testDispatcher) {
            val controller = KeyRepeatController(this)
            val processor =
                KeyboardGestureProcessor(
                    controller = controller,
                    scope = this,
                    kbRepeatEnabled = { true },
                    isShiftActive = { false },
                    isCapsActive = { false },
                    isAltGrActive = { false },
                    initialDensity = 1.0f,
                    onInjectPopupSelection = { _, _ -> },
                )

            processor.updateBounds("q", 0f, 0f, 50f, 50f)
            assertEquals(KeyBounds(0f, 0f, 50f, 50f), processor.keyBounds["q"])
        }

    @Test
    fun `press on normal character key starts long press timer`() =
        runTest(testDispatcher) {
            val controller = KeyRepeatController(this)
            val processor =
                KeyboardGestureProcessor(
                    controller = controller,
                    scope = this,
                    kbRepeatEnabled = { true },
                    isShiftActive = { false },
                    isCapsActive = { false },
                    isAltGrActive = { false },
                    initialDensity = 1.0f,
                    onInjectPopupSelection = { _, _ -> },
                )

            processor.updateBounds("q", 0f, 0f, 50f, 50f)
            assertNull(processor.activePopupState.value)

            // Press on 'q'
            processor.onPress(1L, 25f, 25f, grid, isFullLayout = false)

            // Right after press, the immediate preview popup should be shown
            assertNotNull(processor.activePopupState.value)
            val initialPopup = processor.activePopupState.value!!
            assertEquals("q", initialPopup.keyDef.id)
            assertFalse(initialPopup.isLongPress)

            // Advance virtual time by 500ms to trigger long-press timer delay
            testScheduler.advanceTimeBy(500L)
            testScheduler.runCurrent()

            // Under UnconfinedTestDispatcher, the delay(400) runs after advancing time and running current
            assertNotNull(processor.activePopupState.value)
            val popup = processor.activePopupState.value!!
            assertEquals("q", popup.keyDef.id)
            assertTrue(popup.isLongPress)
            assertEquals(1L, popup.pointerId)
        }

    @Test
    fun `slide on space bar triggers left and right arrow key injections`() =
        runTest(testDispatcher) {
            val controller = KeyRepeatController(this)
            val testInjector = TestKeyCodeInjector()
            val processor =
                KeyboardGestureProcessor(
                    controller = controller,
                    scope = this,
                    kbRepeatEnabled = { true },
                    isShiftActive = { false },
                    isCapsActive = { false },
                    isAltGrActive = { false },
                    initialDensity = 1.0f,
                    onInjectPopupSelection = { _, _ -> },
                    injector = testInjector,
                )

            processor.updateBounds("space", 0f, 50f, 150f, 100f)

            // Press on space
            processor.onPress(1L, 75f, 75f, grid, isFullLayout = false)
            assertFalse(processor.isSpaceDragging)

            // Slide left beyond swipeThreshold (16 pixels)
            // 75f to 50f is delta -25f, which is > 16f
            processor.onMove(1L, 50f, 75f, -25f, 0f, grid, isFullLayout = false)
            assertTrue(processor.isSpaceDragging)

            // Slide left more to trigger step (8 pixels)
            // 50f to 30f is delta -20f
            processor.onMove(1L, 30f, 75f, -20f, 0f, grid, isFullLayout = false)

            // Should have triggered LEFT key injections
            assertTrue(testInjector.calls.isNotEmpty())
            assertEquals("down", testInjector.calls[0].first)
            assertEquals(LinuxKeycodes.KEY_LEFT, testInjector.calls[0].second)
        }

    @Test
    fun `cancel releases all active keys`() =
        runTest(testDispatcher) {
            val controller = KeyRepeatController(this)
            val processor =
                KeyboardGestureProcessor(
                    controller = controller,
                    scope = this,
                    kbRepeatEnabled = { true },
                    isShiftActive = { false },
                    isCapsActive = { false },
                    isAltGrActive = { false },
                    initialDensity = 1.0f,
                    onInjectPopupSelection = { _, _ -> },
                )

            processor.updateBounds("q", 0f, 0f, 50f, 50f)
            processor.onPress(1L, 25f, 25f, grid, isFullLayout = false)

            assertTrue(controller.pressedKeys.value.contains("q"))

            // Cancel event stream
            processor.onCancel(grid)

            assertTrue(controller.pressedKeys.value.isEmpty())
            assertNull(processor.activePopupState.value)
        }

    @Test
    fun `long press on character key and release injects secondary popup option`() =
        runTest(testDispatcher) {
            val controller = KeyRepeatController(this)
            var injectedChar: String? = null
            var injectedKeyDef: KeyDef? = null
            val processor =
                KeyboardGestureProcessor(
                    controller = controller,
                    scope = this,
                    kbRepeatEnabled = { true },
                    isShiftActive = { false },
                    isCapsActive = { false },
                    isAltGrActive = { false },
                    initialDensity = 1.0f,
                    onInjectPopupSelection = { keyDef, char ->
                        injectedKeyDef = keyDef
                        injectedChar = char
                    },
                )

            val qKey = KeyDef("q", "q", LinuxKeycodes.KEY_Q, superscript = "1")
            val testGrid = listOf(listOf(qKey))
            processor.updateBounds("q", 0f, 0f, 50f, 50f)

            processor.onPress(1L, 25f, 25f, testGrid, isFullLayout = false)
            testScheduler.advanceTimeBy(500L)
            testScheduler.runCurrent()

            val popup = processor.activePopupState.value
            assertNotNull(popup)
            assertTrue(popup!!.isLongPress)
            assertEquals(listOf("1"), popup.options)

            processor.onRelease(1L, testGrid)

            assertEquals("1", injectedChar)
            assertEquals("q", injectedKeyDef?.id)
            assertNull(processor.activePopupState.value)
        }

    @Test
    fun `short press on character key and release does not inject secondary popup option`() =
        runTest(testDispatcher) {
            val controller = KeyRepeatController(this)
            var injectedChar: String? = null
            val processor =
                KeyboardGestureProcessor(
                    controller = controller,
                    scope = this,
                    kbRepeatEnabled = { true },
                    isShiftActive = { false },
                    isCapsActive = { false },
                    isAltGrActive = { false },
                    initialDensity = 1.0f,
                    onInjectPopupSelection = { _, char -> injectedChar = char },
                )

            val qKey = KeyDef("q", "q", LinuxKeycodes.KEY_Q, superscript = "1")
            val testGrid = listOf(listOf(qKey))
            processor.updateBounds("q", 0f, 0f, 50f, 50f)

            processor.onPress(1L, 25f, 25f, testGrid, isFullLayout = false)
            processor.onRelease(1L, testGrid)

            assertNull(injectedChar)
            assertNull(processor.activePopupState.value)
        }
}
