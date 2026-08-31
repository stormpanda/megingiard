package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.CompanionSurfaceMode
import com.stormpanda.megingiard.macropad.MouseButton
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.TrackpointMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectorLifecycleManagerTest {
    private fun createLayout(
        hasKeyboard: Boolean = false,
        hasMouse: Boolean = false,
    ): PadLayout {
        val buttons = mutableListOf<PadButton>()
        if (hasKeyboard) {
            buttons.add(PadButton(id = "btn_kb", label = "Key A", posX = 0f, posY = 0f, action = PadAction.KeyboardKey(30, "A")))
        }
        if (hasMouse) {
            buttons.add(PadButton(id = "btn_mouse", label = "LMB", posX = 1f, posY = 1f, action = PadAction.MouseButton(MouseButton.LEFT)))
        }
        return PadLayout(id = "test_layout", name = "Test Layout", buttons = buttons)
    }

    private fun calcStates(
        surfaceMode: CompanionSurfaceMode = CompanionSurfaceMode.MACROPAD,
        isModalOpen: Boolean = false,
        isQuickMenuOpen: Boolean = false,
        promptInFlight: Boolean = false,
        activeLayout: PadLayout? = null,
    ) = InjectorLifecycleManager.calculateInjectorStates(
        surfaceMode = surfaceMode,
        isModalOpen = isModalOpen,
        isQuickMenuOpen = isQuickMenuOpen,
        promptInFlight = promptInFlight,
        activeLayout = activeLayout,
    )

    @Test
    fun testFullscreenKeyboardActive_startsKeyboardInjector() {
        val states = calcStates(surfaceMode = CompanionSurfaceMode.KEYBOARD, activeLayout = createLayout())
        assertTrue(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testFullscreenKeyboardActive_withSettingsModalOpen_keepsKeyboardInjectorActive() {
        val states = calcStates(surfaceMode = CompanionSurfaceMode.KEYBOARD, isModalOpen = true, activeLayout = createLayout())
        assertTrue(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testFullscreenTouchpadActive_startsMouseInjector() {
        val states = calcStates(surfaceMode = CompanionSurfaceMode.TOUCHPAD, activeLayout = createLayout())
        assertFalse(states.startKeyboard)
        assertTrue(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testFullscreenTouchpadActive_withSettingsModalOpen_keepsMouseInjectorActive() {
        val states = calcStates(surfaceMode = CompanionSurfaceMode.TOUCHPAD, isModalOpen = true, activeLayout = createLayout())
        assertFalse(states.startKeyboard)
        assertTrue(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testMacroPadWithKeyboardKeys_inUseMode_startsKeyboardInjector() {
        val states = calcStates(activeLayout = createLayout(hasKeyboard = true, hasMouse = true))
        assertTrue(states.startKeyboard)
        assertTrue(states.startMouse)
    }

    @Test
    fun testBlockingModalOpen_stopsAllMacroInjectors() {
        val states = calcStates(isModalOpen = true, activeLayout = createLayout(hasKeyboard = true, hasMouse = true))
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
        assertFalse(states.startTouch)
    }

    @Test
    fun testPromptInFlight_stopsAllInjectors() {
        val states = calcStates(promptInFlight = true, activeLayout = createLayout(hasKeyboard = true, hasMouse = true))
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
        assertFalse(states.startTouch)
    }

    @Test
    fun testMacroPadWithoutKeyboardKeys_keepsKeyboardInjectorOff() {
        val states = calcStates(activeLayout = createLayout(hasKeyboard = false, hasMouse = true))
        assertFalse(states.startKeyboard)
        assertTrue(states.startMouse)
    }

    @Test
    fun testQuickMenuOpen_stopsAllMacroInjectors() {
        val states = calcStates(isQuickMenuOpen = true, activeLayout = createLayout(hasKeyboard = true, hasMouse = true))
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
        assertFalse(states.startTouch)
    }

    @Test
    fun testViewportEditActive_stopsAllMacroInjectors() {
        val states =
            calcStates(surfaceMode = CompanionSurfaceMode.VIEWPORT_EDIT, activeLayout = createLayout(hasKeyboard = true, hasMouse = true))
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
        assertFalse(states.startTouch)
    }

    @Test
    fun testGamepadMacros_startsGamepadInjector() {
        val layout =
            PadLayout(
                id = "gamepad_layout",
                name = "Gamepad Layout",
                buttons = listOf(PadButton(id = "btn_pad", label = "A", posX = 0f, posY = 0f, action = PadAction.GamepadButton(304, "A"))),
            )
        val states = calcStates(activeLayout = layout)
        assertTrue(states.startGamepad)
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
    }

    @Test
    fun testVirtualTouchTrackpoint_startsTouchInjector() {
        val layout =
            PadLayout(
                id = "touch_layout",
                name = "Touch Layout",
                buttons =
                    listOf(
                        PadButton(
                            id = "btn_touch",
                            label = "Stick",
                            posX = 0f,
                            posY = 0f,
                            action = PadAction.TrackpointMove(mode = TrackpointMode.VIRTUAL_TOUCH),
                        ),
                    ),
            )
        val states = calcStates(activeLayout = layout)
        assertTrue(states.startTouch)
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
    }
}
