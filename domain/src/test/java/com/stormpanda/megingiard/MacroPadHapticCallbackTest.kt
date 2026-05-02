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
 * with the correct magnitude argument for each action type:
 *
 * - Regular button press → magnitude = 0f (fire immediately)
 * - TrackpointMove       → magnitude = sqrt(dx² + dy²) of the rounded injector delta
 * - ScrollWheel          → magnitude = abs(scrollUnits).toFloat()
 *
 * The native injectors (MouseInjector, KeyInjector, …) are safe to call here
 * because their internal `enqueue()` is a no-op when the binary is not running.
 * AppLog.d() is also a no-op at the default log level (WARN).
 */
class MacroPadHapticCallbackTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Canvas / engine helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** 1 dp = 1 px for simplicity. */
    private val dummyDpToPx: (Float) -> Float = { it }

    private val canvasW = 1000f
    private val canvasH = 1000f

    /** Button centered on the canvas (posX=0.5, posY=0.5). */
    private fun centeredButton(action: PadAction, strength: HapticStrength) = PadButton(
        id = "btn-test",
        label = "T",
        posX = 0.5f,
        posY = 0.5f,
        action = action,
        hapticStrength = strength,
    )

    /** Profile with all devices enabled so hit-test is never blocked. */
    private val enabledProfile = PadProfile(
        id = "p",
        name = "Test Profile",
        enableKeyboard = true,
        enableGamepad = true,
        enableMouse = true,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Regular button press
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `button press with haptic LIGHT fires callback with magnitude 0`() {
        val captured = mutableListOf<Pair<HapticStrength, Float>>()
        val engine = MacroPadHitTestEngine(dummyDpToPx) { strength, mag ->
            captured += strength to mag
        }
        val button = centeredButton(PadAction.KeyboardKey(keycode = 28, label = "Enter"), HapticStrength.LIGHT)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)

        assertEquals(1, captured.size)
        assertEquals(HapticStrength.LIGHT, captured[0].first)
        assertEquals(0f, captured[0].second, 0.001f)
    }

    @Test
    fun `button press with haptic OFF does not fire callback`() {
        var called = false
        val engine = MacroPadHitTestEngine(dummyDpToPx) { _, _ -> called = true }
        val button = centeredButton(PadAction.KeyboardKey(keycode = 28, label = "Enter"), HapticStrength.OFF)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)

        assertTrue("callback must not fire when hapticStrength is OFF", !called)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TrackpointMove — magnitude = sqrt(dx² + dy²)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `trackpoint move fires callback with sqrt magnitude`() {
        val captured = mutableListOf<Pair<HapticStrength, Float>>()
        val engine = MacroPadHitTestEngine(dummyDpToPx) { strength, mag ->
            captured += strength to mag
        }
        // TrackpointSize.MEDIUM multiplier = 2.0 → chip = 120px, half = 60px
        // Center at (500,500); hit range [440,560]×[440,560]
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM), HapticStrength.MEDIUM)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)

        // deltaX=5f → dx = (5f * 3f).roundToInt() = 15; deltaY=0f → dy=0
        // expected magnitude = sqrt(15² + 0²) = 15f
        engine.onMove(0L, 505f, 500f, 5f, 0f, listOf(button), enabledProfile)

        assertEquals(1, captured.size)
        assertEquals(HapticStrength.MEDIUM, captured[0].first)
        assertEquals(sqrt(15f * 15f), captured[0].second, 0.001f)
    }

    @Test
    fun `trackpoint move with zero delta does not fire callback`() {
        var called = false
        val engine = MacroPadHitTestEngine(dummyDpToPx) { _, _ -> called = true }
        val button = centeredButton(PadAction.TrackpointMove(TrackpointSize.MEDIUM), HapticStrength.STRONG)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        // deltaX and deltaY small enough that rounded dx=0, dy=0
        engine.onMove(0L, 500.1f, 500f, 0.1f, 0f, listOf(button), enabledProfile)

        assertTrue("callback must not fire for zero-delta trackpoint", !called)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ScrollWheel — magnitude = abs(units).toFloat()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `scroll wheel fires callback with unit magnitude`() {
        val captured = mutableListOf<Pair<HapticStrength, Float>>()
        val engine = MacroPadHitTestEngine(dummyDpToPx) { strength, mag ->
            captured += strength to mag
        }
        // ScrollWheel default size is SIZE_1X1; chip = 60×60px, center at (500,500)
        // MP_SCROLL_SENSITIVITY_PX = 12f → need totalDeltaY = 12f for 1 unit
        // startY = 500f; new py = 488f → totalDeltaY = 12f, units = 1
        val button = centeredButton(PadAction.ScrollWheel, HapticStrength.STRONG)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        engine.onMove(0L, 500f, 488f, 0f, -12f, listOf(button), enabledProfile)

        assertEquals(1, captured.size)
        assertEquals(HapticStrength.STRONG, captured[0].first)
        assertEquals(1f, captured[0].second, 0.001f)
    }

    @Test
    fun `scroll wheel with haptic OFF does not fire callback`() {
        var called = false
        val engine = MacroPadHitTestEngine(dummyDpToPx) { _, _ -> called = true }
        val button = centeredButton(PadAction.ScrollWheel, HapticStrength.OFF)

        engine.onPress(0L, 500f, 500f, canvasW, canvasH, listOf(button), enabledProfile, false)
        engine.onMove(0L, 500f, 488f, 0f, -12f, listOf(button), enabledProfile)

        assertTrue("callback must not fire when hapticStrength is OFF", !called)
    }
}
