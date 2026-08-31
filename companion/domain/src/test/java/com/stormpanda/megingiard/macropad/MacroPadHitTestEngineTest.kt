package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.ShellInputInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchCommand
import com.stormpanda.megingiard.privd.PrivdClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue

class MacroPadHitTestEngineTest {
    private val engine = MacroPadHitTestEngine(buttonUnitDpToPx = { it })
    private val canvasW = 1000f
    private val canvasH = 1000f

    private lateinit var queue: LinkedBlockingQueue<TouchCommand>
    private var originalRunning: Boolean = false

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
        val cmd = queue.poll()
        assertNotNull(cmd)
        assertEquals(action, cmd!!.action)
        assertEquals(x, cmd.x)
        assertEquals(y, cmd.y)
    }

    @Before
    fun setUp() {
        val superclass = ShellInputInjector::class.java.superclass
        val runningField = superclass.getDeclaredField("running").apply { isAccessible = true }
        originalRunning = runningField.get(ShellInputInjector) as Boolean
        runningField.set(ShellInputInjector, true)

        val queueField = superclass.getDeclaredField("queue").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        queue = queueField.get(ShellInputInjector) as LinkedBlockingQueue<TouchCommand>
        queue.clear()
    }

    @After
    fun tearDown() {
        val superclass = ShellInputInjector::class.java.superclass
        superclass.getDeclaredField("running").apply {
            isAccessible = true
            set(ShellInputInjector, originalRunning)
        }
        queue.clear()
        PrivdClient.isConnectedForTest = null
    }

    @Test
    fun `virtual touch trackpoint injects DOWN event at center initially`() {
        val blocked = engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        assertNull(blocked)
        assertEquals(1, queue.size)
        assertTouch(TouchAction.DOWN, 540, 960)
    }

    @Test
    fun `virtual touch trackpoint accumulates moves and coerces bounds`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        queue.clear()

        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton), enabledProfile)
        assertEquals(1, queue.size)
        assertTouch(TouchAction.MOVE, 600, 1110)

        engine.onMove(0L, 1550f, 1480f, 1000f, 1000f, listOf(trackpointButton), enabledProfile)
        assertEquals(1, queue.size)
        assertTouch(TouchAction.MOVE, -540, 2880)

        engine.onMove(0L, 1500f, 1430f, -50f, -50f, listOf(trackpointButton), enabledProfile)
        assertEquals(1, queue.size)
        assertTouch(TouchAction.MOVE, 150, 1770)
    }

    @Test
    fun `virtual touch trackpoint release injects UP event at last position`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton), enabledProfile)
        queue.clear()

        engine.onRelease(0L, listOf(trackpointButton), enabledProfile)
        assertEquals(1, queue.size)
        assertTouch(TouchAction.UP, 600, 1110)
    }

    @Test
    fun `virtual touch trackpoint keeps internally tracked position clamped on release`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        queue.clear()

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
        queue.clear()

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        assertEquals(1, queue.size)
        assertTouch(TouchAction.DOWN, 600, 1110)
    }

    @Test
    fun `physical mouse trackpoint does not inject touch events`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(mouseButton), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(mouseButton), enabledProfile)
        engine.onRelease(0L, listOf(mouseButton), enabledProfile)
        assertEquals(0, queue.size)
    }

    @Test
    fun `disabled touch profile blocks virtual touch trackpoint`() {
        val blocked = engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), disabledTouchProfile, false)
        assertNotNull(blocked)
        assertEquals("btn-test", blocked?.id)
        assertEquals(0, queue.size)
    }

    @Test
    fun `releaseAll with active virtual touch trackpoint injects UP event`() {
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(trackpointButton), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(trackpointButton), enabledProfile)
        queue.clear()

        engine.releaseAll(listOf(trackpointButton))
        assertEquals(1, queue.size)
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
    fun `macro button is disabled when Privileged Mode is disconnected`() {
        val macroAction = PadAction.Macro("macro-1")

        PrivdClient.isConnectedForTest = false
        assertTrue(MacroPadHitTestEngine.isDeviceDisabled(macroAction, enabledProfile))
        assertEquals(DisabledReason.MACRO_PRIVD, MacroPadHitTestEngine.deviceDisabledReason(macroAction, enabledProfile))

        PrivdClient.isConnectedForTest = true
        assertFalse(MacroPadHitTestEngine.isDeviceDisabled(macroAction, enabledProfile))
        assertNull(MacroPadHitTestEngine.deviceDisabledReason(macroAction, enabledProfile))
    }
}
