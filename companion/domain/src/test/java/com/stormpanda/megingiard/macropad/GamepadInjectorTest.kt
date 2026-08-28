package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertFalse
import org.junit.Test

class GamepadInjectorTest {
    @Test
    fun testIsRunningInitialState() {
        assertFalse(GamepadInjector.isRunning)
    }

    @Test
    fun testGamepadButtonAndAxisActionsDoNotCrash() {
        GamepadInjector.buttonDown(GamepadKeycodes.BTN_SOUTH) // BTN_SOUTH (A button)
        GamepadInjector.buttonUp(GamepadKeycodes.BTN_SOUTH)
        GamepadInjector.hat(0, 1) // Hat X right
        GamepadInjector.hat(1, -1) // Hat Y up
        GamepadInjector.joystick(0, 16384) // ABS_X analog right

        // D-Pad buttons
        GamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_UP)
        GamepadInjector.buttonUp(GamepadKeycodes.BTN_DPAD_UP)
        GamepadInjector.buttonDown(GamepadKeycodes.BTN_DPAD_LEFT)
        GamepadInjector.buttonUp(GamepadKeycodes.BTN_DPAD_LEFT)

        // Stick direction codes
        GamepadInjector.buttonDown(GamepadKeycodes.CODE_LS_UP)
        GamepadInjector.buttonUp(GamepadKeycodes.CODE_LS_UP)
        GamepadInjector.buttonDown(GamepadKeycodes.CODE_RS_RIGHT)
        GamepadInjector.buttonUp(GamepadKeycodes.CODE_RS_RIGHT)

        // Diagonal codes
        GamepadInjector.buttonDown(GamepadKeycodes.CODE_DPAD_UP_LEFT)
        GamepadInjector.buttonUp(GamepadKeycodes.CODE_DPAD_UP_LEFT)
        GamepadInjector.buttonDown(GamepadKeycodes.CODE_LS_DOWN_RIGHT)
        GamepadInjector.buttonUp(GamepadKeycodes.CODE_LS_DOWN_RIGHT)
        GamepadInjector.buttonDown(GamepadKeycodes.CODE_RS_UP_LEFT)
        GamepadInjector.buttonUp(GamepadKeycodes.CODE_RS_UP_LEFT)
    }
}
