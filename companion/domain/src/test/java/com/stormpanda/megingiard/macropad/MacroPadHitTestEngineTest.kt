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
}
