package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.privd.PrivdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacroPadHitTestEngineTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val engine = MacroPadHitTestEngine(buttonUnitDpToPx = { it })
    private val canvasW = 1000f
    private val canvasH = 1000f

    private fun centeredButton(action: PadAction) =
        PadButton(
            id = "btn-test",
            label = "T",
            posX = 0.5f,
            posY = 0.5f,
            action = action,
            hapticStrength = HapticStrength.OFF,
            hapticCustomDurationMs = 0,
            hapticCustomAmplitude = 0,
        )

    private val trackpointButton = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH))
    private val mouseButton = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.PHYSICAL_MOUSE))

    private fun assertTouch(
        action: TouchAction,
        x: Int,
        y: Int,
    ) {
        val cmd = PrivdClient.pollCommandForTest()
        assertNotNull(cmd)
        if (action == TouchAction.UP) {
            assertEquals("U 0\n", cmd)
        } else {
            val prefix = if (action == TouchAction.DOWN) "D" else "M"
            assertEquals("$prefix 0 $x $y\n", cmd)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        PrivdClient.isConnectedForTest = true
        PrivdClient.clearQueueForTest()
    }

    @After
    fun tearDown() {
        PrivdClient.clearQueueForTest()
        PrivdClient.isConnectedForTest = null
        Dispatchers.resetMain()
    }

    @Test
    fun `virtual touch trackpoint injects DOWN event at center initially`() {
        val consumed = engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        assertTrue(consumed)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.DOWN, 540, 960)
    }

    @Test
    fun `virtual touch trackpoint accumulates moves and coerces bounds`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        PrivdClient.clearQueueForTest()

        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton))
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, 600, 1110)

        engine.onMove(0L, 1550f, 1480f, 1000f, 1000f, listOf(trackpointButton))
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, -540, 2880)

        engine.onMove(0L, 1500f, 1430f, -50f, -50f, listOf(trackpointButton))
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, 150, 1770)
    }

    @Test
    fun `virtual touch trackpoint scales movement with sensitivity setting`() {
        val highSensButton =
            centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH, sensitivity = 2.0f))
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(highSensButton), false)
        PrivdClient.clearQueueForTest()

        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(highSensButton))
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, 660, 1260)
    }

    @Test
    fun `virtual touch trackpoint release injects UP event at last position`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton))
        PrivdClient.clearQueueForTest()

        engine.onRelease(0L, listOf(trackpointButton))
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.UP, 600, 1110)
    }

    @Test
    fun `virtual touch trackpoint keeps internally tracked position clamped on release`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        PrivdClient.clearQueueForTest()

        engine.onMove(0L, 1500f, 1500f, 1000f, 1000f, listOf(trackpointButton))
        assertTouch(TouchAction.MOVE, -540, 2880)

        engine.onRelease(0L, listOf(trackpointButton))
        assertTouch(TouchAction.UP, -540, 2880)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        assertTouch(TouchAction.DOWN, 0, 1920)
    }

    @Test
    fun `virtual touch trackpoint remembers position across swipes`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton))
        engine.onRelease(0L, listOf(trackpointButton))
        PrivdClient.clearQueueForTest()

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.DOWN, 600, 1110)
    }

    @Test
    fun `physical mouse trackpoint does not inject touch events`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(mouseButton), false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(mouseButton))
        engine.onRelease(0L, listOf(mouseButton))
        val commands = mutableListOf<String>()
        while (true) {
            val cmd = PrivdClient.pollCommandForTest() ?: break
            commands.add(cmd)
        }
        assertTrue(commands.none { it.startsWith("D ") || it.startsWith("M ") || it.startsWith("U ") })
        assertTrue(commands.any { it.startsWith("MM ") })
    }

    @Test
    fun `releaseAll with active virtual touch trackpoint injects UP event`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton))
        PrivdClient.clearQueueForTest()

        engine.releaseAll(listOf(trackpointButton))
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.UP, 600, 1110)
    }

    @Test
    fun `onPress returns true when coordinates fall inside button bounds and false outside`() {
        val button = centeredButton(PadAction.KeyboardKey(keycode = 1, label = "Test"))
        assertTrue(engine.onPress(1L, 500f, 500f, canvasW, canvasH, listOf(button), false))
        assertTrue(engine.onPress(2L, 480f, 520f, canvasW, canvasH, listOf(button), false))
        assertFalse(engine.onPress(3L, 400f, 500f, canvasW, canvasH, listOf(button), false))
        assertFalse(engine.onPress(4L, 500f, 540f, canvasW, canvasH, listOf(button), false))
    }

    @Test
    fun `isPointerTracked returns true when pointer is tracked and false otherwise`() {
        val button = centeredButton(PadAction.KeyboardKey(keycode = 1, label = "Test"))
        val pointerId = 42L

        assertFalse(engine.isPointerTracked(pointerId))
        engine.onPress(pointerId, 500f, 500f, canvasW, canvasH, listOf(button), false)
        assertTrue(engine.isPointerTracked(pointerId))
        engine.onRelease(pointerId, listOf(button))
        assertFalse(engine.isPointerTracked(pointerId))
    }

    @Test
    fun `scroll wheel button handles press, drag, and release`() {
        val scrollBtn = centeredButton(PadAction.ScrollWheel)
        engine.onPress(10L, 500f, 500f, canvasW, canvasH, listOf(scrollBtn), false)
        assertTrue(engine.isPointerTracked(10L))

        engine.onMove(10L, 500f, 450f, 0f, -50f, listOf(scrollBtn))
        engine.onMove(10L, 500f, 550f, 0f, 100f, listOf(scrollBtn))

        engine.onRelease(10L, listOf(scrollBtn))
        assertFalse(engine.isPointerTracked(10L))
    }

    @Test
    fun `haptic callback is invoked on button press with non-off strength`() {
        var hapticTriggered = false
        val engineWithHaptic =
            MacroPadHitTestEngine(
                buttonUnitDpToPx = { it },
                onHapticFeedback = { id, strength, duration, amplitude, magnitude ->
                    hapticTriggered = true
                    assertEquals("btn-haptic", id)
                    assertEquals(HapticStrength.MEDIUM, strength)
                },
            )
        val hapticBtn =
            PadButton(
                id = "btn-haptic",
                label = "H",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.KeyboardKey(keycode = 30, label = "H"),
                hapticStrength = HapticStrength.MEDIUM,
            )

        engineWithHaptic.onPress(5L, 500f, 500f, canvasW, canvasH, listOf(hapticBtn), false)
        assertTrue(hapticTriggered)
    }

    @Test
    fun `shape-aware hit test rejects empty bounding-box corners for DIAMOND and accepts for SQUARE`() {
        // 60x60 button centered at (500, 500) -> AABB is [470, 530] x [470, 530].
        // Point (475, 475) is inside the AABB, but outside the diamond (|dx| + |dy| = 25 + 25 = 50 > 30).
        val diamondBtn =
            centeredButton(PadAction.KeyboardKey(keycode = 30, label = "A")).copy(
                id = "btn-diamond",
                buttonShape = ButtonShape.DIAMOND,
            )
        val squareBtn =
            centeredButton(PadAction.KeyboardKey(keycode = 31, label = "B")).copy(
                id = "btn-square",
                buttonShape = ButtonShape.SQUARE,
            )

        assertFalse(engine.onPress(1L, 475f, 475f, canvasW, canvasH, listOf(diamondBtn), false))
        assertTrue(engine.onPress(2L, 500f, 500f, canvasW, canvasH, listOf(diamondBtn), false))
        engine.onRelease(2L, listOf(diamondBtn))

        assertTrue(engine.onPress(3L, 475f, 475f, canvasW, canvasH, listOf(squareBtn), false))
        engine.onRelease(3L, listOf(squareBtn))
    }

    @Test
    fun `overlapping triangle buttons resolve press to the polygon containing the tap point`() {
        // Two 60x60 triangles sharing the exact same [470, 530] x [470, 530] bounding box:
        // TRIANGLE_DOWN is listed first, TRIANGLE_UP is listed second.
        // Tapping bottom-left (475, 525) is outside TRIANGLE_DOWN but inside TRIANGLE_UP.
        val triDown =
            centeredButton(PadAction.KeyboardKey(keycode = 30, label = "Down")).copy(
                id = "btn-tri-down",
                buttonShape = ButtonShape.TRIANGLE_DOWN,
            )
        val triUp =
            centeredButton(PadAction.KeyboardKey(keycode = 31, label = "Up")).copy(
                id = "btn-tri-up",
                buttonShape = ButtonShape.TRIANGLE_UP,
            )
        val buttons = listOf(triDown, triUp)

        assertTrue(engine.onPress(1L, 475f, 525f, canvasW, canvasH, buttons, false))
        assertEquals(setOf("btn-tri-up"), engine.pressedIds.value)
        engine.onRelease(1L, buttons)
    }

    @Test
    fun `button pressed on layout A still dispatches release when layout B buttons are passed to onRelease`() {
        val btnA =
            centeredButton(PadAction.GamepadButton(btnCode = 304, label = "A")).copy(
                id = "btn-layout-a",
            )
        val layoutAButtons = listOf(btnA)
        val layoutBButtons =
            listOf(
                centeredButton(PadAction.GamepadButton(btnCode = 305, label = "B")).copy(id = "btn-layout-b"),
            )

        assertTrue(engine.onPress(7L, 500f, 500f, canvasW, canvasH, layoutAButtons, false))
        assertEquals("GD 304\n", PrivdClient.pollCommandForTest())

        // Release occurs while layout B is active (layoutBButtons does not contain btnA)
        engine.onRelease(7L, layoutBButtons)
        assertEquals("GU 304\n", PrivdClient.pollCommandForTest())
        assertFalse(engine.isPointerTracked(7L))
    }

    @Test
    fun `concurrent TouchTap buttons allocate distinct slots and release independently`() {
        val tapLeft =
            PadButton(
                id = "btn-tap-left",
                label = "Left Tap",
                posX = 0.2f,
                posY = 0.5f,
                action = PadAction.TouchTap(normX = 0.25f, normY = 0.5f),
                hapticStrength = HapticStrength.OFF,
            )
        val tapRight =
            PadButton(
                id = "btn-tap-right",
                label = "Right Tap",
                posX = 0.8f,
                posY = 0.5f,
                action = PadAction.TouchTap(normX = 0.75f, normY = 0.5f),
                hapticStrength = HapticStrength.OFF,
            )
        val buttons = listOf(tapLeft, tapRight)

        // Finger 1 holds Left Tap (slot 8)
        assertTrue(engine.onPress(1L, 200f, 500f, canvasW, canvasH, buttons, false))
        val down1 = PrivdClient.pollCommandForTest()
        assertNotNull(down1)
        assertTrue("Expected slot 8 DOWN, got $down1", down1!!.startsWith("D 8 "))

        // Finger 2 holds Right Tap simultaneously (slot 9, without colliding with slot 8)
        assertTrue(engine.onPress(2L, 800f, 500f, canvasW, canvasH, buttons, false))
        val down2 = PrivdClient.pollCommandForTest()
        assertNotNull(down2)
        assertTrue("Expected slot 9 DOWN, got $down2", down2!!.startsWith("D 9 "))
        assertEquals(setOf("btn-tap-left", "btn-tap-right"), engine.pressedIds.value)

        // Finger 1 lifts Left Tap -> releases slot 8 while slot 9 remains held
        engine.onRelease(1L, buttons)
        assertEquals("U 8\n", PrivdClient.pollCommandForTest())
        assertNull(PrivdClient.pollCommandForTest())
        assertEquals(setOf("btn-tap-right"), engine.pressedIds.value)

        // Finger 3 presses Left Tap again -> reuses freed slot 8
        assertTrue(engine.onPress(3L, 200f, 500f, canvasW, canvasH, buttons, false))
        val down3 = PrivdClient.pollCommandForTest()
        assertNotNull(down3)
        assertTrue("Expected freed slot 8 DOWN, got $down3", down3!!.startsWith("D 8 "))

        // releaseAll lifts both active TouchTap slots (9 and 8)
        engine.releaseAll(buttons)
        val releasedCommands =
            buildList {
                while (true) {
                    val cmd = PrivdClient.pollCommandForTest() ?: break
                    add(cmd)
                }
            }
        assertEquals(setOf("U 8\n", "U 9\n"), releasedCommands.toSet())
        assertTrue(engine.pressedIds.value.isEmpty())
    }

    @Test
    fun `multi-pointer press on same HoldToShift button pops all stacked entries on releaseAll`() {
        val baseLayout = PadLayout(id = "l-base", name = "Base")
        val shiftBtn =
            centeredButton(
                PadAction.SwitchToLayout(layoutId = "l-shift", holdToShift = true),
            ).copy(id = "btn-hold-shift")
        val shiftLayout = PadLayout(id = "l-shift", name = "Shift", buttons = listOf(shiftBtn))
        val profile =
            PadProfile(
                id = "p-shift",
                name = "ShiftProfile",
                layouts = listOf(baseLayout, shiftLayout),
                activeLayoutId = "l-base",
            )
        MacroPadState.loadFrom(listOf(profile), "p-shift")

        // Two pointers press the same Hold-to-Shift button -> stack depth becomes 2
        assertTrue(engine.onPress(1L, 500f, 500f, canvasW, canvasH, listOf(shiftBtn), false))
        assertTrue(engine.onPress(2L, 505f, 505f, canvasW, canvasH, listOf(shiftBtn), false))
        assertEquals("l-shift", MacroPadState.activeLayout.value?.id)

        // releaseAll must pop both Hold-to-Shift entries and restore the base layout
        engine.releaseAll(listOf(shiftBtn))
        assertEquals("l-base", MacroPadState.activeLayout.value?.id)
        assertTrue(engine.pressedIds.value.isEmpty())
    }

    @Test
    fun `multi-pointer press on same KeyboardKey button keeps key held until last pointer lifts`() {
        val keyBtn =
            centeredButton(
                PadAction.KeyboardKey(keycode = 30, label = "A"),
            ).copy(id = "btn-key-a")
        val buttons = listOf(keyBtn)

        // First pointer presses -> sends KD 30
        assertTrue(engine.onPress(1L, 500f, 500f, canvasW, canvasH, buttons, false))
        assertEquals("KD 30\n", PrivdClient.pollCommandForTest())
        assertNull(PrivdClient.pollCommandForTest())

        // Second pointer presses same button -> stays pressed without duplicate KD 30
        assertTrue(engine.onPress(2L, 505f, 505f, canvasW, canvasH, buttons, false))
        assertNull(PrivdClient.pollCommandForTest())
        assertEquals(setOf("btn-key-a"), engine.pressedIds.value)

        // First pointer lifts while second still holds -> key stays held (no premature KU 30)
        engine.onRelease(1L, buttons)
        assertNull(PrivdClient.pollCommandForTest())
        assertEquals(setOf("btn-key-a"), engine.pressedIds.value)

        // Second pointer lifts -> sends KU 30 and clears pressedIds
        engine.onRelease(2L, buttons)
        assertEquals("KU 30\n", PrivdClient.pollCommandForTest())
        assertNull(PrivdClient.pollCommandForTest())
        assertTrue(engine.pressedIds.value.isEmpty())
    }
}
