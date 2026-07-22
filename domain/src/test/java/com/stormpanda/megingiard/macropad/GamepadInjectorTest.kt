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
        GamepadInjector.buttonDown(304) // BTN_SOUTH (A button)
        GamepadInjector.buttonUp(304)
        GamepadInjector.hat(0, 1) // Hat X right
        GamepadInjector.hat(1, -1) // Hat Y up
        GamepadInjector.joystick(0, 16384) // ABS_X analog right
    }
}
