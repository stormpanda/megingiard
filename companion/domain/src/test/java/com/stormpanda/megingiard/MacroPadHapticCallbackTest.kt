package com.stormpanda.megingiard

import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.MacroPadHitTestEngine
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.TrackpointSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Verifies that [MacroPadHitTestEngine] invokes its `onHapticFeedback` callback
 * with the correct arguments for each action type.
 */
class MacroPadHapticCallbackTest {
    private data class HapticCall(
        val buttonId: String,
        val strength: HapticStrength,
        val customDurationMs: Int,
        val customAmplitude: Int,
        val magnitude: Float,
    )

    private val canvasW = 1000f
    private val canvasH = 1000f

    private fun centeredButton(
        action: PadAction,
        strength: HapticStrength,
        customDurationMs: Int = 10,
        customAmplitude: Int = 25,
    ) = PadButton(
        id = "btn-test",
        label = "T",
        posX = 0.5f,
        posY = 0.5f,
        action = action,
        hapticStrength = strength,
        hapticCustomDurationMs = customDurationMs,
        hapticCustomAmplitude = customAmplitude,
    )

    private val enabledProfile =
        PadProfile(id = "p", name = "Test Profile", enableKeyboard = true, enableGamepad = true, enableMouse = true)

    private fun captureEngine(): Pair<MutableList<HapticCall>, MacroPadHitTestEngine> {
        val captured = mutableListOf<HapticCall>()
        val engine =
            MacroPadHitTestEngine({ it }) { buttonId, strength, customDurationMs, customAmplitude, magnitude ->
                captured += HapticCall(buttonId, strength, customDurationMs, customAmplitude, magnitude)
            }
        return captured to engine
    }

    private fun MacroPadHitTestEngine.press(
        button: PadButton,
        profile: PadProfile = enabledProfile,
        x: Float = 500f,
        y: Float = 500f,
    ) = onPress(0L, x, y, canvasW, canvasH, listOf(button), profile, false)

    private fun MacroPadHitTestEngine.move(
        button: PadButton,
        profile: PadProfile = enabledProfile,
        x: Float = 500f,
        y: Float = 500f,
        dx: Float = 0f,
        dy: Float = 0f,
    ) = onMove(0L, x, y, dx, dy, listOf(button), profile)

    @Test
    fun `button press with haptic LIGHT fires callback with magnitude 0`() {
        val (captured, engine) = captureEngine()
        val button = centeredButton(PadAction.KeyboardKey(28, "Enter"), HapticStrength.LIGHT)
        engine.press(button)

        assertEquals(1, captured.size)
        assertEquals("btn-test", captured[0].buttonId)
        assertEquals(HapticStrength.LIGHT, captured[0].strength)
        assertEquals(0f, captured[0].magnitude, 0.001f)
    }

    @Test
    fun `button press with haptic OFF does not fire callback`() {
        val (captured, engine) = captureEngine()
        engine.press(centeredButton(PadAction.KeyboardKey(28, "Enter"), HapticStrength.OFF))
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `disabled device button with haptic enabled does not fire callback`() {
        val (captured, engine) = captureEngine()
        val button = centeredButton(PadAction.KeyboardKey(28, "Enter"), HapticStrength.LIGHT)
        val disabledButton = engine.press(button, profile = enabledProfile.copy(enableKeyboard = false))

        assertEquals("btn-test", disabledButton?.id)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `button press with CUSTOM strength forwards custom duration and amplitude`() {
        val (captured, engine) = captureEngine()
        val button = centeredButton(PadAction.KeyboardKey(28, "Enter"), HapticStrength.CUSTOM, 42, 75)
        engine.press(button)

        assertEquals(1, captured.size)
        assertEquals("btn-test", captured[0].buttonId)
        assertEquals(HapticStrength.CUSTOM, captured[0].strength)
        assertEquals(42, captured[0].customDurationMs)
        assertEquals(75, captured[0].customAmplitude)
        assertEquals(0f, captured[0].magnitude, 0.001f)
    }

    @Test
    fun `trackpoint move fires callback with sqrt magnitude`() {
        val (captured, engine) = captureEngine()
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM), HapticStrength.MEDIUM)

        engine.press(button)
        engine.move(button, x = 505f, dx = 5f)

        assertEquals(1, captured.size)
        assertEquals("btn-test", captured[0].buttonId)
        assertEquals(HapticStrength.MEDIUM, captured[0].strength)
        assertEquals(sqrt(15f * 15f), captured[0].magnitude, 0.001f)
    }

    @Test
    fun `trackpoint move with zero delta does not fire callback`() {
        val (captured, engine) = captureEngine()
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM), HapticStrength.STRONG)

        engine.press(button)
        engine.move(button, x = 500.1f, dx = 0.1f)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `scroll wheel fires callback with magnitude 0 for immediate fire`() {
        val (captured, engine) = captureEngine()
        val button = centeredButton(PadAction.ScrollWheel, HapticStrength.STRONG)

        engine.press(button)
        engine.move(button, y = 488f, dy = -12f)

        assertEquals(1, captured.size)
        assertEquals("btn-test", captured[0].buttonId)
        assertEquals(HapticStrength.STRONG, captured[0].strength)
        assertEquals(0f, captured[0].magnitude, 0.001f)
    }

    @Test
    fun `scroll wheel with haptic OFF does not fire callback`() {
        val (captured, engine) = captureEngine()
        val button = centeredButton(PadAction.ScrollWheel, HapticStrength.OFF)

        engine.press(button)
        engine.move(button, y = 488f, dy = -12f)
        assertTrue(captured.isEmpty())
    }
}
