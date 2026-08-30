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
        hasKeyboard: Boolean,
        hasMouse: Boolean,
    ): PadLayout {
        val buttons = mutableListOf<PadButton>()
        if (hasKeyboard) {
            buttons.add(
                PadButton(
                    id = "btn_kb",
                    label = "Key A",
                    posX = 0f,
                    posY = 0f,
                    action = PadAction.KeyboardKey(keycode = 30, label = "A"),
                ),
            )
        }
        if (hasMouse) {
            buttons.add(
                PadButton(
                    id = "btn_mouse",
                    label = "LMB",
                    posX = 1f,
                    posY = 1f,
                    action = PadAction.MouseButton(button = MouseButton.LEFT),
                ),
            )
        }
        return PadLayout(
            id = "test_layout",
            name = "Test Layout",
            buttons = buttons,
        )
    }

    @Test
    fun testFullscreenKeyboardActive_startsKeyboardInjector() {
        val layout = createLayout(hasKeyboard = false, hasMouse = false)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.KEYBOARD,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertTrue(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testFullscreenKeyboardActive_withSettingsModalOpen_keepsKeyboardInjectorActive() {
        val layout = createLayout(hasKeyboard = false, hasMouse = false)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.KEYBOARD,
                isModalOpen = true,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertTrue(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testFullscreenTouchpadActive_startsMouseInjector() {
        val layout = createLayout(hasKeyboard = false, hasMouse = false)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.TOUCHPAD,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertFalse(states.startKeyboard)
        assertTrue(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testFullscreenTouchpadActive_withSettingsModalOpen_keepsMouseInjectorActive() {
        val layout = createLayout(hasKeyboard = false, hasMouse = false)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.TOUCHPAD,
                isModalOpen = true,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertFalse(states.startKeyboard)
        assertTrue(states.startMouse)
        assertFalse(states.startGamepad)
    }

    @Test
    fun testMacroPadWithKeyboardKeys_inUseMode_startsKeyboardInjector() {
        val layout = createLayout(hasKeyboard = true, hasMouse = true)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.MACROPAD,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertTrue(states.startKeyboard)
        assertTrue(states.startMouse)
    }

    @Test
    fun testBlockingModalOpen_stopsAllMacroInjectors() {
        val layout = createLayout(hasKeyboard = true, hasMouse = true)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.MACROPAD,
                isModalOpen = true,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
        assertFalse(states.startTouch)
    }

    @Test
    fun testPromptInFlight_stopsAllInjectors() {
        val layout = createLayout(hasKeyboard = true, hasMouse = true)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.MACROPAD,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = true,
                activeLayout = layout,
            )

        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
        assertFalse(states.startTouch)
    }

    @Test
    fun testMacroPadWithoutKeyboardKeys_keepsKeyboardInjectorOff() {
        val layout = createLayout(hasKeyboard = false, hasMouse = true)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.MACROPAD,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertFalse(states.startKeyboard)
        assertTrue(states.startMouse)
    }

    @Test
    fun testQuickMenuOpen_stopsAllMacroInjectors() {
        val layout = createLayout(hasKeyboard = true, hasMouse = true)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.MACROPAD,
                isModalOpen = false,
                isQuickMenuOpen = true,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
        assertFalse(states.startTouch)
    }

    @Test
    fun testViewportEditActive_stopsAllMacroInjectors() {
        val layout = createLayout(hasKeyboard = true, hasMouse = true)
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.VIEWPORT_EDIT,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

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
                buttons =
                    listOf(
                        PadButton(
                            id = "btn_pad",
                            label = "A",
                            posX = 0f,
                            posY = 0f,
                            action = PadAction.GamepadButton(btnCode = 304, label = "A"),
                        ),
                    ),
            )
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.MACROPAD,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

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
                            action =
                                PadAction.TrackpointMove(
                                    mode = TrackpointMode.VIRTUAL_TOUCH,
                                ),
                        ),
                    ),
            )
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                surfaceMode = CompanionSurfaceMode.MACROPAD,
                isModalOpen = false,
                isQuickMenuOpen = false,
                promptInFlight = false,
                activeLayout = layout,
            )

        assertTrue(states.startTouch)
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startGamepad)
    }
}
