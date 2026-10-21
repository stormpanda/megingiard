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
        onHapticFeedback: () -> Unit = {},
        onModeChange: (KeyboardMode) -> Unit = {},
        activeMode: () -> KeyboardMode = { KeyboardMode.LETTERS },
    ) = KeyboardGestureProcessor(
        scope = this,
        isShiftActive = {
            KeyboardState.stateFor("lshift").value != ModifierState.INACTIVE ||
                KeyboardState.stateFor("rshift").value != ModifierState.INACTIVE
        },
        isCapsActive = { KeyboardState.stateFor("caps").value != ModifierState.INACTIVE },
        isAltGrActive = { KeyboardState.stateFor("altgr").value != ModifierState.INACTIVE },
        initialDensity = 1.0f,
        onInjectPopupSelection = onInjectPopupSelection,
        injector = injector,
        onHapticFeedback = onHapticFeedback,
        onModeChange = onModeChange,
        activeMode = activeMode,
    )

    @Test
    fun testInitialState() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            assertTrue(processor.pressedKeys.value.isEmpty())
        }

    @Test
    fun testOnKeyDownWithNullKeyDefReturnsFalse() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val handled = processor.onKeyDown(pointerId = 1L, keyDef = null)
            assertFalse(handled)
        }

    @Test
    fun testNormalKeyDownAndUp() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            val testKeyA = KeyDef(id = "a", label = "a", linuxKeycode = 30, type = KeyType.NORMAL)

            val handledDown = processor.onKeyDown(pointerId = 1L, keyDef = testKeyA)
            assertTrue(handledDown)
            assertTrue(processor.pressedKeys.value.contains("a"))
            assertEquals("a", processor.getKeyIdForPointer(1L))

            processor.onKeyUp(pointerId = 1L)
            assertFalse(processor.pressedKeys.value.contains("a"))
            assertEquals(listOf("down" to 30, "up" to 30), testInjector.calls)
        }

    @Test
    fun testKeyMoveBetweenNormalKeys() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val testKeyA = KeyDef(id = "a", label = "a", linuxKeycode = 30, type = KeyType.NORMAL)
            val testKeyB = KeyDef(id = "b", label = "b", linuxKeycode = 48, type = KeyType.NORMAL)

            processor.onKeyDown(pointerId = 1L, keyDef = testKeyA)
            assertTrue(processor.pressedKeys.value.contains("a"))

            val moveHandled =
                processor.onKeyMove(
                    pointerId = 1L,
                    newKeyDef = testKeyB,
                    deltaX = 10f,
                    deltaY = 0f,
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

            processor.onKeyDown(pointerId = 1L, keyDef = testKeyA)
            processor.dispose()
            assertTrue(processor.pressedKeys.value.isEmpty())
        }

    @Test
    fun testKeyMoveFromControlKeyToAnotherKey() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val testKeyUp = KeyDef(id = "up", label = "▲", linuxKeycode = 103, type = KeyType.NORMAL)
            val testKeyLeft = KeyDef(id = "left", label = "◀", linuxKeycode = 105, type = KeyType.NORMAL)

            val handledDown = processor.onKeyDown(pointerId = 1L, keyDef = testKeyUp)
            assertTrue(handledDown)
            assertTrue(processor.pressedKeys.value.contains("up"))
            assertEquals("up", processor.getKeyIdForPointer(1L))

            val moveHandled =
                processor.onKeyMove(
                    pointerId = 1L,
                    newKeyDef = testKeyLeft,
                    deltaX = 10f,
                    deltaY = 0f,
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

            val handledDown = processor.onKeyDown(pointerId = 1L, keyDef = testKeyDel)
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

    @Test
    fun `caps modifier hold injects KEY_LEFTSHIFT via injectedKeycode`() =
        runTest(testDispatcher) {
            KeyboardState.reset()
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            val capsDef = KeyDef("caps", "CAPS", LinuxKeycodes.KEY_CAPSLOCK, type = KeyType.MODIFIER)

            processor.onKeyDown(1L, capsDef, isFullLayout = false)
            testScheduler.advanceTimeBy(350L)
            testScheduler.runCurrent()

            assertEquals(listOf("down" to LinuxKeycodes.KEY_LEFTSHIFT), testInjector.calls)

            processor.onKeyUp(1L, isFullLayout = false)
            assertEquals(
                listOf("down" to LinuxKeycodes.KEY_LEFTSHIFT, "up" to LinuxKeycodes.KEY_LEFTSHIFT),
                testInjector.calls,
            )
            KeyboardState.reset()
        }

    @Test
    fun `findKeyAt resolves gaps and outer margins to nearest key rectangle edge`() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            processor.updateBounds("q", 0f, 0f, 50f, 40f)
            processor.updateBounds("w", 54f, 0f, 104f, 40f)
            processor.updateBounds("space", 0f, 46f, 150f, 86f)

            // Horizontal gutter between q (right=50) and w (left=54)
            assertEquals("q", processor.findKeyAt(grid, 51f, 20f)?.id)
            assertEquals("w", processor.findKeyAt(grid, 53f, 20f)?.id)

            // Vertical gutter near left end of wide spacebar (x=10 is far from space center=75,
            // but y=44 is 2px from space top=46 vs 4px from q bottom=40)
            assertEquals("space", processor.findKeyAt(grid, 10f, 44f)?.id)
            assertEquals("q", processor.findKeyAt(grid, 10f, 41f)?.id)

            // Outer left margin snaps to q
            assertEquals("q", processor.findKeyAt(grid, -3f, 20f)?.id)
        }

    @Test
    fun `onPress on already-active pointerId releases stale pointer before new press`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            processor.updateBounds("q", 0f, 0f, 50f, 40f)
            processor.updateBounds("w", 50f, 0f, 100f, 40f)

            // Simulate pointer 1L pressing q and never receiving onRelease (e.g. cancelled event)
            processor.onPress(1L, 25f, 20f, grid, isFullLayout = false)
            assertTrue(processor.isPointerActive(1L))

            // Next tap with same pointerId 1L on w should clean up q and press w
            processor.onPress(1L, 75f, 20f, grid, isFullLayout = false)
            assertEquals(setOf("w"), processor.pressedKeys.value)
            processor.onRelease(1L, isFullLayout = false)

            assertTrue(processor.pressedKeys.value.isEmpty())
            assertFalse(processor.isPointerActive(1L))
        }

    @Test
    fun `two-thumb rollover commits first character key immediately when second character key is pressed`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            processor.updateBounds("q", 0f, 0f, 50f, 40f)
            processor.updateBounds("w", 50f, 0f, 100f, 40f)

            // Thumb 1 presses 'q'
            processor.onPress(1L, 25f, 20f, grid, isFullLayout = false)
            assertTrue(testInjector.calls.isEmpty())

            // Before Thumb 1 lifts, Thumb 2 presses 'w' -> 'q' is committed immediately
            processor.onPress(2L, 75f, 20f, grid, isFullLayout = false)
            assertEquals(
                listOf("down" to LinuxKeycodes.KEY_Q, "up" to LinuxKeycodes.KEY_Q),
                testInjector.calls,
            )

            // Even if Thumb 2 lifts before Thumb 1, 'w' is injected after 'q' and Thumb 1's release does not double-inject 'q'
            processor.onRelease(2L, isFullLayout = false)
            processor.onRelease(1L, isFullLayout = false)

            assertEquals(
                listOf(
                    "down" to LinuxKeycodes.KEY_Q,
                    "up" to LinuxKeycodes.KEY_Q,
                    "down" to LinuxKeycodes.KEY_W,
                    "up" to LinuxKeycodes.KEY_W,
                ),
                testInjector.calls,
            )
            assertTrue(processor.pressedKeys.value.isEmpty())
        }

    @Test
    fun `haptic feedback fires on key press, long-press popup, modifier hold, and spacebar steps`() =
        runTest(testDispatcher) {
            KeyboardState.reset()
            var hapticCount = 0
            val processor = createProcessor(onHapticFeedback = { hapticCount++ })
            processor.updateBounds("q", 0f, 0f, 50f, 40f)
            processor.updateBounds("space", 0f, 50f, 150f, 100f)

            // 1. Key press + long-press popup open + option selection shift
            processor.onPress(1L, 25f, 20f, grid, isFullLayout = false)
            assertEquals(1, hapticCount)

            testScheduler.advanceTimeBy(500L)
            testScheduler.runCurrent()
            assertEquals(2, hapticCount)

            processor.onMove(1L, 50f, 20f, 25f, 0f, grid, isFullLayout = false)
            assertEquals(3, hapticCount)
            processor.onRelease(1L, isFullLayout = false)

            // 2. Spacebar press + cursor steps
            processor.onPress(2L, 75f, 75f, grid, isFullLayout = false)
            assertEquals(4, hapticCount)
            processor.onMove(2L, 50f, 75f, -25f, 0f, grid, isFullLayout = false)
            assertEquals(5, hapticCount)
            processor.onMove(2L, 30f, 75f, -20f, 0f, grid, isFullLayout = false)
            assertEquals(6, hapticCount)
            processor.onRelease(2L, isFullLayout = false)

            // 3. Modifier press + hold latch
            processor.onModifierPress("ctrl", LinuxKeycodes.KEY_LEFTCTRL, isFullLayout = false)
            assertEquals(7, hapticCount)
            testScheduler.advanceTimeBy(350L)
            testScheduler.runCurrent()
            assertEquals(8, hapticCount)
            processor.onModifierRelease("ctrl", LinuxKeycodes.KEY_LEFTCTRL, isFullLayout = false)
            KeyboardState.reset()
        }

    @Test
    fun `tapping space in SYMBOLS_1 mode injects space and auto-returns to LETTERS mode`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            var currentMode = KeyboardMode.SYMBOLS_1
            val processor =
                createProcessor(
                    injector = testInjector,
                    onModeChange = { currentMode = it },
                    activeMode = { currentMode },
                )
            processor.updateBounds("space", 0f, 50f, 150f, 100f)

            processor.onPress(1L, 75f, 75f, grid, isFullLayout = false)
            processor.onRelease(1L, isFullLayout = false)

            assertEquals(
                listOf("down" to LinuxKeycodes.KEY_SPACE, "up" to LinuxKeycodes.KEY_SPACE),
                testInjector.calls,
            )
            assertEquals(KeyboardMode.LETTERS, currentMode)
        }

    @Test
    fun `hold and slide from lshift onto letter key injects shifted letter and resets sticky shift`() =
        runTest(testDispatcher) {
            KeyboardState.reset()
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            val lshiftDef = KeyDef("lshift", "Shift", LinuxKeycodes.KEY_LEFTSHIFT, type = KeyType.MODIFIER)
            val qDef = KeyDef("q", "q", LinuxKeycodes.KEY_Q)
            val slideGrid = listOf(listOf(lshiftDef, qDef))
            processor.updateBounds("lshift", 0f, 0f, 50f, 40f)
            processor.updateBounds("q", 50f, 0f, 100f, 40f)

            processor.onPress(1L, 25f, 20f, slideGrid, isFullLayout = false)
            processor.onMove(1L, 75f, 20f, 50f, 0f, slideGrid, isFullLayout = false)
            assertEquals(ModifierState.STICKY, KeyboardState.stateFor("lshift").value)
            assertEquals(
                "Q",
                processor.activePopupState.value
                    ?.options
                    ?.firstOrNull(),
            )

            processor.onRelease(1L, isFullLayout = false)

            assertEquals(
                listOf(
                    "down" to LinuxKeycodes.KEY_LEFTSHIFT,
                    "down" to LinuxKeycodes.KEY_Q,
                    "up" to LinuxKeycodes.KEY_Q,
                    "up" to LinuxKeycodes.KEY_LEFTSHIFT,
                ),
                testInjector.calls,
            )
            assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("lshift").value)
            KeyboardState.reset()
        }

    @Test
    fun `hold and slide from mode_switch onto symbol key injects symbol and returns to LETTERS mode`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            var currentMode = KeyboardMode.LETTERS
            val processor =
                createProcessor(
                    injector = testInjector,
                    onModeChange = { currentMode = it },
                    activeMode = { currentMode },
                )
            val modeSwitchDef = KeyDef("mode_switch", "?123", 0)
            val num1Def = KeyDef("1", "1", LinuxKeycodes.KEY_1)
            val slideGrid = listOf(listOf(modeSwitchDef, num1Def))
            processor.updateBounds("mode_switch", 0f, 0f, 50f, 40f)
            processor.updateBounds("1", 50f, 0f, 100f, 40f)

            processor.onPress(1L, 25f, 20f, slideGrid, isFullLayout = false)
            assertEquals(KeyboardMode.SYMBOLS_1, currentMode)

            processor.onMove(1L, 75f, 20f, 50f, 0f, slideGrid, isFullLayout = false)
            processor.onRelease(1L, isFullLayout = false)

            assertEquals(
                listOf("down" to LinuxKeycodes.KEY_1, "up" to LinuxKeycodes.KEY_1),
                testInjector.calls,
            )
            assertEquals(KeyboardMode.LETTERS, currentMode)
        }

    @Test
    fun `spacebar cursor slide supports vertical up and down arrow steps after activating horizontal drag`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            processor.updateBounds("space", 0f, 50f, 150f, 100f)

            processor.onPress(1L, 75f, 75f, grid, isFullLayout = false)
            // Exceed 16dp horizontal threshold to activate isSpaceDragging
            processor.onMove(1L, 50f, 75f, -25f, 0f, grid, isFullLayout = false)
            assertTrue(processor.isSpaceDragging)
            testInjector.calls.clear()

            // Move up by 10dp (exceeds 8dp step) -> KEY_UP
            processor.onMove(1L, 50f, 65f, 0f, -10f, grid, isFullLayout = false)
            // Move down by 16dp (two 8dp steps, plus -2dp remainder = +14dp -> 1 step) -> KEY_DOWN
            processor.onMove(1L, 50f, 81f, 0f, 16f, grid, isFullLayout = false)
            processor.onRelease(1L, isFullLayout = false)

            assertEquals(
                listOf(
                    "down" to LinuxKeycodes.KEY_UP,
                    "up" to LinuxKeycodes.KEY_UP,
                    "down" to LinuxKeycodes.KEY_DOWN,
                    "up" to LinuxKeycodes.KEY_DOWN,
                ),
                testInjector.calls,
            )
        }

    @Test
    fun `sliding left from bksp key deletes whole words with Ctrl+Backspace`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            val mDef = KeyDef("m", "m", LinuxKeycodes.KEY_M)
            val bkspDef = KeyDef("bksp", "⌫", LinuxKeycodes.KEY_BACKSPACE)
            val bkspGrid = listOf(listOf(mDef, bkspDef))
            processor.updateBounds("m", 0f, 0f, 50f, 40f)
            processor.updateBounds("bksp", 50f, 0f, 100f, 40f)

            // Press bksp -> immediate KEY_BACKSPACE down
            processor.onPress(1L, 80f, 20f, bkspGrid, isFullLayout = false)
            assertEquals(listOf("down" to LinuxKeycodes.KEY_BACKSPACE), testInjector.calls)

            // Slide left by 30dp (exceeds 16dp threshold and 24dp word step) -> releases initial bksp and injects Ctrl+Backspace
            processor.onMove(1L, 50f, 20f, -30f, 0f, bkspGrid, isFullLayout = false)
            assertTrue(processor.isBkspDragging)
            processor.onRelease(1L, isFullLayout = false)

            assertEquals(
                listOf(
                    "down" to LinuxKeycodes.KEY_BACKSPACE,
                    "up" to LinuxKeycodes.KEY_BACKSPACE,
                    "down" to LinuxKeycodes.KEY_LEFTCTRL,
                    "down" to LinuxKeycodes.KEY_BACKSPACE,
                    "up" to LinuxKeycodes.KEY_BACKSPACE,
                    "up" to LinuxKeycodes.KEY_LEFTCTRL,
                ),
                testInjector.calls,
            )
        }

    @Test
    fun `two-thumb rollover onto enter commits pending character key before enter`() =
        runTest(testDispatcher) {
            val testInjector = TestKeyCodeInjector()
            val processor = createProcessor(injector = testInjector)
            val qDef = KeyDef("q", "q", LinuxKeycodes.KEY_Q)
            val enterDef = KeyDef("enter", "↵", LinuxKeycodes.KEY_ENTER)
            val enterGrid = listOf(listOf(qDef, enterDef))
            processor.updateBounds("q", 0f, 0f, 50f, 40f)
            processor.updateBounds("enter", 50f, 0f, 100f, 40f)

            // Pointer 1 presses 'q' (pending character key, not yet injected)
            processor.onPress(1L, 25f, 20f, enterGrid, isFullLayout = false)
            assertTrue(testInjector.calls.isEmpty())

            // Pointer 2 presses 'enter' before pointer 1 lifts -> commits 'q' first, then presses 'enter'
            processor.onPress(2L, 75f, 20f, enterGrid, isFullLayout = false)
            assertEquals(
                listOf(
                    "down" to LinuxKeycodes.KEY_Q,
                    "up" to LinuxKeycodes.KEY_Q,
                    "down" to LinuxKeycodes.KEY_ENTER,
                ),
                testInjector.calls,
            )

            // Releasing pointer 1 should not inject 'q' a second time
            processor.onRelease(1L, isFullLayout = false)
            processor.onRelease(2L, isFullLayout = false)
            assertEquals(
                listOf(
                    "down" to LinuxKeycodes.KEY_Q,
                    "up" to LinuxKeycodes.KEY_Q,
                    "down" to LinuxKeycodes.KEY_ENTER,
                    "up" to LinuxKeycodes.KEY_ENTER,
                ),
                testInjector.calls,
            )
        }

    @Test
    fun `sliding onto mode_switch key clears character preview popup`() =
        runTest(testDispatcher) {
            val processor = createProcessor()
            val qDef = KeyDef("q", "q", LinuxKeycodes.KEY_Q)
            val modeSwitchDef = KeyDef("mode_switch", "?123", 0)
            val slideGrid = listOf(listOf(qDef, modeSwitchDef))
            processor.updateBounds("q", 0f, 0f, 50f, 40f)
            processor.updateBounds("mode_switch", 50f, 0f, 100f, 40f)

            processor.onPress(1L, 25f, 20f, slideGrid, isFullLayout = false)
            assertEquals(
                "q",
                processor.activePopupState.value
                    ?.keyDef
                    ?.id,
            )

            processor.onMove(1L, 75f, 20f, 50f, 0f, slideGrid, isFullLayout = false)
            assertNull(processor.activePopupState.value)

            processor.onRelease(1L, isFullLayout = false)
        }
}
