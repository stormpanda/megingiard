package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.ShellInputInjector
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchCommand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue

class MacroPadHitTestEngineTest {

    private val dummyDpToPx: (Float) -> Float = { it }
    private val canvasW = 1000f
    private val canvasH = 1000f

    private lateinit var queue: LinkedBlockingQueue<TouchCommand>
    private var originalRunning: Boolean = false

    @Before
    fun setUp() {
        // Use reflection to enable queueing in ShellInputInjector during tests
        val superclass = ShellInputInjector::class.java.superclass
        val runningField = superclass.getDeclaredField("running")
        runningField.isAccessible = true
        originalRunning = runningField.get(ShellInputInjector) as Boolean
        runningField.set(ShellInputInjector, true)

        val queueField = superclass.getDeclaredField("queue")
        queueField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        queue = queueField.get(ShellInputInjector) as LinkedBlockingQueue<TouchCommand>
        queue.clear()
    }

    @After
    fun tearDown() {
        val superclass = ShellInputInjector::class.java.superclass
        val runningField = superclass.getDeclaredField("running")
        runningField.isAccessible = true
        runningField.set(ShellInputInjector, originalRunning)
        queue.clear()
    }

    private fun centeredButton(action: PadAction) = PadButton(
        id = "btn-test",
        label = "T",
        posX = 0.5f,
        posY = 0.5f,
        action = action,
        hapticStrength = HapticStrength.OFF,
        hapticCustomDurationMs = 0,
        hapticCustomAmplitude = 0,
    )

    private val enabledProfile = PadProfile(
        id = "p",
        name = "Test Profile",
        enableKeyboard = true,
        enableGamepad = true,
        enableMouse = true,
        enableTouch = true,
    )

    private val disabledTouchProfile = enabledProfile.copy(enableTouch = false)

    @Test
    fun `virtual touch trackpoint injects DOWN event at center initially`() {
        val engine = MacroPadHitTestEngine(dummyDpToPx)
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH))

        val blocked = engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        assertNull(blocked)

        assertEquals(1, queue.size)
        val cmd = queue.poll()
        assertNotNull(cmd)
        assertEquals(TouchAction.DOWN, cmd!!.action)
        // Center of screen (0.5, 0.5)
        // px = (1 - 0.5) * 1080 = 540
        // py = 0.5 * 1920 = 960
        assertEquals(540, cmd.x)
        assertEquals(960, cmd.y)
    }

    @Test
    fun `virtual touch trackpoint accumulates moves and coerces bounds`() {
        val engine = MacroPadHitTestEngine(dummyDpToPx)
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH))

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        queue.clear()

        // Move 1: deltaX = 50f, deltaY = -20f
        // dxNormalized = (50 * 3) / 1920 = 150 / 1920 = 0.078125
        // dyNormalized = (-20 * 3) / 1080 = -60 / 1080 = -0.05555556
        // cursorX = 0.5 + 0.078125 = 0.578125
        // cursorY = 0.5 - 0.05555556 = 0.44444444
        // px = (1 - 0.44444444) * 1080 = 600
        // py = 0.578125 * 1920 = 1110
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(button), enabledProfile)
        assertEquals(1, queue.size)
        val cmd1 = queue.poll()
        assertNotNull(cmd1)
        assertEquals(TouchAction.MOVE, cmd1!!.action)
        assertEquals(600, cmd1.x)
        assertEquals(1110, cmd1.y)

        // Move 2: Drag way off screen to test coercion
        // deltaX = 1000f, deltaY = 1000f -> should coerce cursor to (1.0, 1.0)
        // px = (1 - 1.0) * 1080 = 0
        // py = 1.0 * 1920 = 1920
        engine.onMove(0L, 1550f, 1480f, 1000f, 1000f, listOf(button), enabledProfile)
        assertEquals(1, queue.size)
        val cmd2 = queue.poll()
        assertNotNull(cmd2)
        assertEquals(TouchAction.MOVE, cmd2!!.action)
        assertEquals(0, cmd2.x)
        assertEquals(1920, cmd2.y)
    }

    @Test
    fun `virtual touch trackpoint release injects UP event at last position`() {
        val engine = MacroPadHitTestEngine(dummyDpToPx)
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH))

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(button), enabledProfile)
        queue.clear()

        engine.onRelease(0L, listOf(button), enabledProfile)
        assertEquals(1, queue.size)
        val cmd = queue.poll()
        assertNotNull(cmd)
        assertEquals(TouchAction.UP, cmd!!.action)
        assertEquals(600, cmd.x)
        assertEquals(1110, cmd.y)
    }

    @Test
    fun `virtual touch trackpoint remembers position across swipes`() {
        val engine = MacroPadHitTestEngine(dummyDpToPx)
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH))

        // First swipe
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(button), enabledProfile)
        engine.onRelease(0L, listOf(button), enabledProfile)
        queue.clear()

        // Second swipe starts: should DOWN at (0.578125, 0.44444444) -> (600, 1110)
        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        assertEquals(1, queue.size)
        val cmd = queue.poll()
        assertNotNull(cmd)
        assertEquals(TouchAction.DOWN, cmd!!.action)
        assertEquals(600, cmd.x)
        assertEquals(1110, cmd.y)
    }

    @Test
    fun `physical mouse trackpoint does not inject touch events`() {
        val engine = MacroPadHitTestEngine(dummyDpToPx)
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.PHYSICAL_MOUSE))

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        engine.onMove(0L, 550f, 480f, 50f, -20f, listOf(button), enabledProfile)
        engine.onRelease(0L, listOf(button), enabledProfile)

        assertEquals(0, queue.size)
    }

    @Test
    fun `disabled touch profile blocks virtual touch trackpoint`() {
        val engine = MacroPadHitTestEngine(dummyDpToPx)
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM, TrackpointMode.VIRTUAL_TOUCH))

        val blocked = engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), disabledTouchProfile, false)
        assertNotNull(blocked)
        assertEquals("btn-test", blocked?.id)
        assertEquals(0, queue.size)
    }
}
