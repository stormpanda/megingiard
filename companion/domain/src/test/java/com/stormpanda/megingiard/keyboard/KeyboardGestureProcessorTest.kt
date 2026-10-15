package com.stormpanda.megingiard.keyboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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

    private fun TestScope.createProcessor(
        injector: KeyCodeInjector = TestKeyCodeInjector(),
        onInjectPopupSelection: (KeyDef, String) -> Unit = { _, _ -> },
    ) = KeyboardGestureProcessor(
        scope = this,
        kbRepeatEnabled = { true },
        isShiftActive = { false },
        isCapsActive = { false },
        isAltGrActive = { false },
        initialDensity = 1.0f,
        onInjectPopupSelection = onInjectPopupSelection,
        injector = injector,
    )

    @Test
    fun testInitialState() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            assertTrue(processor.pressedKeys.value.isEmpty())
            assertFalse(processor.trackpointVisible.value)
        }

    @Test
    fun testOnKeyDownWithNullKeyDefReturnsFalse() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val handled = processor.onKeyDown(pointerId = 1L, keyDef = null, repeatEnabled = true)
            assertFalse(handled)
        }

    @Test
    fun testNormalKeyDownAndUp() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            val testKeyA = KeyDef(id = "a", label = "a", linuxKeycode = 30, type = KeyType.NORMAL)

            val handledDown = processor.onKeyDown(pointerId = 1L, keyDef = testKeyA, repeatEnabled = true)
            assertTrue(handledDown)
            assertTrue(processor.pressedKeys.value.contains("a"))
            assertEquals("a", processor.getKeyIdForPointer(1L))

            processor.onKeyUp(pointerId = 1L)
            assertFalse(processor.pressedKeys.value.contains("a"))
            assertEquals(listOf("down" to 30, "up" to 30), testInjector.calls)
        }

    @Test
    fun testTrackpointKeyDownAndUp() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val trackpointKey = KeyDef(id = "trackpoint", label = "•", linuxKeycode = 0, type = KeyType.TRACKPOINT)

            val handled = processor.onKeyDown(pointerId = 2L, keyDef = trackpointKey, repeatEnabled = true)
            assertTrue(handled)
            assertTrue(processor.trackpointVisible.value)
            assertTrue(processor.isTrackpointPointer(2L))

            processor.onKeyUp(pointerId = 2L)
            assertFalse(processor.trackpointVisible.value)
        }

    @Test
    fun testKeyMoveBetweenNormalKeys() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val testKeyA = KeyDef(id = "a", label = "a", linuxKeycode = 30, type = KeyType.NORMAL)
            val testKeyB = KeyDef(id = "b", label = "b", linuxKeycode = 48, type = KeyType.NORMAL)

            processor.onKeyDown(pointerId = 1L, keyDef = testKeyA, repeatEnabled = true)
            assertTrue(processor.pressedKeys.value.contains("a"))

            val moveHandled =
                processor.onKeyMove(
                    pointerId = 1L,
                    newKeyDef = testKeyB,
                    deltaX = 10f,
                    deltaY = 0f,
                    repeatEnabled = true,
                )
            assertTrue(moveHandled)
            assertFalse(processor.pressedKeys.value.contains("a"))
            assertTrue(processor.pressedKeys.value.contains("b"))
            assertEquals("b", processor.getKeyIdForPointer(1L))
        }

    @Test
    fun testDisposeClearsState() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val testKeyA = KeyDef(id = "a", label = "a", linuxKeycode = 30, type = KeyType.NORMAL)

            processor.onKeyDown(pointerId = 1L, keyDef = testKeyA, repeatEnabled = true)
            processor.dispose()
            assertTrue(processor.pressedKeys.value.isEmpty())
            assertFalse(processor.trackpointVisible.value)
        }

    @Test
    fun testKeyMoveFromControlKeyToAnotherKey() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val testKeyUp = KeyDef(id = "up", label = "▲", linuxKeycode = 103, type = KeyType.NORMAL)
            val testKeyLeft = KeyDef(id = "left", label = "◀", linuxKeycode = 105, type = KeyType.NORMAL)

            val handledDown = processor.onKeyDown(pointerId = 1L, keyDef = testKeyUp, repeatEnabled = true)
            assertTrue(handledDown)
            assertTrue(processor.pressedKeys.value.contains("up"))
            assertEquals("up", processor.getKeyIdForPointer(1L))

            val moveHandled =
                processor.onKeyMove(
                    pointerId = 1L,
                    newKeyDef = testKeyLeft,
                    deltaX = 10f,
                    deltaY = 0f,
                    repeatEnabled = true,
                )
            assertTrue(moveHandled)
            assertFalse(processor.pressedKeys.value.contains("up"))
            assertTrue(processor.pressedKeys.value.contains("left"))
            assertEquals("left", processor.getKeyIdForPointer(1L))
        }

    @Test
    fun testOnKeyUpWithSkipInjectionOnControlKey() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            val testKeyDel = KeyDef(id = "del", label = "Del", linuxKeycode = 111, type = KeyType.NORMAL)

            val handledDown = processor.onKeyDown(pointerId = 1L, keyDef = testKeyDel, repeatEnabled = true)
            assertTrue(handledDown)
            assertTrue(processor.pressedKeys.value.contains("del"))
            assertEquals("del", processor.getKeyIdForPointer(1L))

            val handledUp = processor.onKeyUp(pointerId = 1L, skipInjection = true)
            assertTrue(handledUp)
            assertFalse(processor.pressedKeys.value.contains("del"))
            assertNull(processor.getKeyIdForPointer(1L))
            assertEquals(listOf("down" to 111, "up" to 111), testInjector.calls)
        }

    @Test
    fun `bounds updates populate key bounds map`() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            processor.updateBounds("q", 0f, 0f, 50f, 50f)
            assertEquals(KeyBounds(0f, 0f, 50f, 50f), processor.keyBounds["q"])
        }

    @Test
    fun `press on normal character key starts long press timer`() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            processor.updateBounds("q", 0f, 0f, 50f, 50f)
            assertNull(processor.activePopupState.value)

            processor.onPress(1L, 25f, 25f, grid, isFullLayout = false)

            assertNotNull(processor.activePopupState.value)
            val initialPopup = processor.activePopupState.value!!
            assertEquals("q", initialPopup.keyDef.id)
            assertFalse(initialPopup.isLongPress)

            testScheduler.advanceTimeBy(500L)
            testScheduler.runCurrent()

            assertNotNull(processor.activePopupState.value)
            val popup = processor.activePopupState.value!!
            assertEquals("q", popup.keyDef.id)
            assertTrue(popup.isLongPress)
            assertEquals(1L, popup.pointerId)
        }

    @Test
    fun `slide on space bar triggers left and right arrow key injections`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)

            processor.updateBounds("space", 0f, 50f, 150f, 100f)
            processor.onPress(1L, 75f, 75f, grid, isFullLayout = false)
            assertFalse(processor.isSpaceDragging)

            processor.onMove(1L, 50f, 75f, -25f, 0f, grid, isFullLayout = false)
            assertTrue(processor.isSpaceDragging)

            processor.onMove(1L, 30f, 75f, -20f, 0f, grid, isFullLayout = false)

            assertTrue(testInjector.calls.isNotEmpty())
            assertEquals("down", testInjector.calls[0].first)
            assertEquals(LinuxKeycodes.KEY_LEFT, testInjector.calls[0].second)
        }

    @Test
    fun `cancel releases all active keys`() =
        runTest(testDispatcher) {
            val processor = createProcessor()

            processor.updateBounds("q", 0f, 0f, 50f, 50f)
            processor.onPress(1L, 25f, 25f, grid, isFullLayout = false)

            assertTrue(processor.pressedKeys.value.contains("q"))

            processor.onCancel()

            assertTrue(processor.pressedKeys.value.isEmpty())
            assertNull(processor.activePopupState.value)
        }

    @Test
    fun `long press on character key and release injects secondary popup option`() =
        runTest(testDispatcher) {
            var injectedChar: String? = null
            var injectedKeyDef: KeyDef? = null
            val processor =
                createProcessor(
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

            processor.onRelease(1L)

            assertEquals("1", injectedChar)
            assertEquals("q", injectedKeyDef?.id)
            assertNull(processor.activePopupState.value)
        }

    @Test
    fun `short press on character key and release does not inject secondary popup option`() =
        runTest(testDispatcher) {
            var injectedChar: String? = null
            val processor = createProcessor(onInjectPopupSelection = { _, char -> injectedChar = char })

            val qKey = KeyDef("q", "q", LinuxKeycodes.KEY_Q, superscript = "1")
            val testGrid = listOf(listOf(qKey))
            processor.updateBounds("q", 0f, 0f, 50f, 50f)

            processor.onPress(1L, 25f, 25f, testGrid, isFullLayout = false)
            processor.onRelease(1L)

            assertNull(injectedChar)
            assertNull(processor.activePopupState.value)
        }
}
