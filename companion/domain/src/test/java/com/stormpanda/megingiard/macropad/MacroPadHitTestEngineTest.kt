package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.privd.PrivdClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MacroPadHitTestEngineTest {
    private val engine = MacroPadHitTestEngine(buttonUnitDpToPx = { it })
    private val canvasW = 1000f
    private val canvasH = 1000f

    private val enabledProfile =
        PadProfile(
            id = "p",
            name = "Test Profile",
            enableKeyboard = true,
            enableGamepad = true,
            enableMouse = true,
            enableTouch = true,
        )
    private val disabledTouchProfile = enabledProfile.copy(enableTouch = false)

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
        PrivdClient.isConnectedForTest = true
        PrivdClient.clearQueueForTest()
    }

    @After
    fun tearDown() {
        PrivdClient.clearQueueForTest()
        PrivdClient.isConnectedForTest = null
    }

    @Test
    fun `virtual touch trackpoint injects DOWN event at center initially`() {
        val blocked = engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        assertNull(blocked)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.DOWN, 540, 960)
    }

    @Test
    fun `virtual touch trackpoint accumulates moves and coerces bounds`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        PrivdClient.clearQueueForTest()

        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton), enabledProfile)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, 600, 1110)

        engine.onMove(0L, 1550f, 1480f, 1000f, 1000f, listOf(trackpointButton), enabledProfile)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, -540, 2880)

        engine.onMove(0L, 1500f, 1430f, -50f, -50f, listOf(trackpointButton), enabledProfile)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, 150, 1770)
    }

    @Test
    fun `virtual touch trackpoint scales movement with sensitivity setting`() {
        val highSensButton =
            centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH, sensitivity = 2.0f))
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(highSensButton), enabledProfile, false)
        PrivdClient.clearQueueForTest()

        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(highSensButton), enabledProfile)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.MOVE, 660, 1260)
    }

    @Test
    fun `virtual touch trackpoint release injects UP event at last position`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton), enabledProfile)
        PrivdClient.clearQueueForTest()

        engine.onRelease(0L, listOf(trackpointButton), enabledProfile)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.UP, 600, 1110)
    }

    @Test
    fun `virtual touch trackpoint keeps internally tracked position clamped on release`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        PrivdClient.clearQueueForTest()

        engine.onMove(0L, 1500f, 1500f, 1000f, 1000f, listOf(trackpointButton), enabledProfile)
        assertTouch(TouchAction.MOVE, -540, 2880)

        engine.onRelease(0L, listOf(trackpointButton), enabledProfile)
        assertTouch(TouchAction.UP, -540, 2880)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        assertTouch(TouchAction.DOWN, 0, 1920)
    }

    @Test
    fun `virtual touch trackpoint remembers position across swipes`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton), enabledProfile)
        engine.onRelease(0L, listOf(trackpointButton), enabledProfile)
        PrivdClient.clearQueueForTest()

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.DOWN, 600, 1110)
    }

    @Test
    fun `physical mouse trackpoint does not inject touch events`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(mouseButton), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(mouseButton), enabledProfile)
        engine.onRelease(0L, listOf(mouseButton), enabledProfile)
        val commands = mutableListOf<String>()
        while (true) {
            val cmd = PrivdClient.pollCommandForTest() ?: break
            commands.add(cmd)
        }
        assertTrue(commands.none { it.startsWith("D ") || it.startsWith("M ") || it.startsWith("U ") })
        assertTrue(commands.any { it.startsWith("MM ") })
    }

    @Test
    fun `disabled touch profile blocks virtual touch trackpoint`() {
        val blocked = engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), disabledTouchProfile, false)
        assertNotNull(blocked)
        assertEquals("btn-test", blocked?.id)
        assertEquals(0, PrivdClient.queueSizeForTest)
    }

    @Test
    fun `releaseAll with active virtual touch trackpoint injects UP event`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton), enabledProfile)
        PrivdClient.clearQueueForTest()

        engine.releaseAll(listOf(trackpointButton))
        assertEquals(1, PrivdClient.queueSizeForTest)
        assertTouch(TouchAction.UP, 600, 1110)
    }

    @Test
    fun `hitTest returns correct value when coordinates fall inside and outside button bounds`() {
        val button = centeredButton(PadAction.KeyboardKey(keycode = 1, label = "Test"))
        assertTrue(engine.hitTest(500f, 500f, canvasW, canvasH, listOf(button), false))
        assertTrue(engine.hitTest(480f, 520f, canvasW, canvasH, listOf(button), false))
        assertFalse(engine.hitTest(400f, 500f, canvasW, canvasH, listOf(button), false))
        assertFalse(engine.hitTest(500f, 540f, canvasW, canvasH, listOf(button), false))
    }

    @Test
    fun `isPointerTracked returns true when pointer is tracked and false otherwise`() {
        val button = centeredButton(PadAction.KeyboardKey(keycode = 1, label = "Test"))
        val pointerId = 42L

        assertFalse(engine.isPointerTracked(pointerId))
        engine.onPress(pointerId, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        assertTrue(engine.isPointerTracked(pointerId))
        engine.onRelease(pointerId, listOf(button), enabledProfile)
        assertFalse(engine.isPointerTracked(pointerId))
    }

    @Test
    fun `gamepad button is disabled when gamepad is disabled in profile`() {
        val gpAction = PadAction.GamepadButton(btnCode = 304, label = "A")
        val disabledGpProfile = enabledProfile.copy(enableGamepad = false)

        assertTrue(MacroPadHitTestEngine.isDeviceDisabled(gpAction, disabledGpProfile))
        assertEquals(DisabledReason.GAMEPAD, MacroPadHitTestEngine.deviceDisabledReason(gpAction, disabledGpProfile))

        assertFalse(MacroPadHitTestEngine.isDeviceDisabled(gpAction, enabledProfile))
        assertNull(MacroPadHitTestEngine.deviceDisabledReason(gpAction, enabledProfile))
    }

    @Test
    fun `deviceDisabledReason returns correct reason for each disabled device type`() {
        val kbAction = PadAction.KeyboardKey(keycode = 1, label = "K")
        val gpAction = PadAction.GamepadButton(btnCode = 304, label = "A")
        val mouseAction = PadAction.MouseButton(button = MouseButton.LEFT)

        val disabledKbProfile = enabledProfile.copy(enableKeyboard = false)
        val disabledGpProfile = enabledProfile.copy(enableGamepad = false)
        val disabledMouseProfile = enabledProfile.copy(enableMouse = false)

        assertEquals(DisabledReason.KEYBOARD, MacroPadHitTestEngine.deviceDisabledReason(kbAction, disabledKbProfile))
        assertEquals(DisabledReason.GAMEPAD, MacroPadHitTestEngine.deviceDisabledReason(gpAction, disabledGpProfile))
        assertEquals(DisabledReason.MOUSE, MacroPadHitTestEngine.deviceDisabledReason(mouseAction, disabledMouseProfile))

        // FullScreenKeyboard and FullScreenMouse are overlays and must never be blocked by missing key flags
        val fsKbAction = PadAction.FullScreenKeyboard()
        val fsMouseAction = PadAction.FullScreenMouse()
        assertNull(MacroPadHitTestEngine.deviceDisabledReason(fsKbAction, disabledKbProfile))
        assertNull(MacroPadHitTestEngine.deviceDisabledReason(fsMouseAction, disabledMouseProfile))
    }

    @Test
    fun `scroll wheel button handles press, drag, and release`() {
        val scrollBtn = centeredButton(PadAction.ScrollWheel)
        engine.onPress(10L, 500f, 500f, canvasW, canvasH, listOf(scrollBtn), enabledProfile, false)
        assertTrue(engine.isPointerTracked(10L))

        engine.onMove(10L, 500f, 450f, 0f, -50f, listOf(scrollBtn), enabledProfile)
        engine.onMove(10L, 500f, 550f, 0f, 100f, listOf(scrollBtn), enabledProfile)

        engine.onRelease(10L, listOf(scrollBtn), enabledProfile)
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

        engineWithHaptic.onPress(5L, 500f, 500f, canvasW, canvasH, listOf(hapticBtn), enabledProfile, false)
        assertTrue(hapticTriggered)
    }
}
