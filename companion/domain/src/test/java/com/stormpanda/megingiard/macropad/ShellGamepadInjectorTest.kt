package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellGamepadInjectorTest {
    @Test
    fun gamepadKeycodes_constants() {
        assertEquals(0, GamepadKeycodes.ABS_X)
        assertEquals(1, GamepadKeycodes.ABS_Y)
        assertEquals(2, GamepadKeycodes.ABS_Z)
        assertEquals(5, GamepadKeycodes.ABS_RZ)
        assertEquals(304, GamepadKeycodes.BTN_SOUTH)
        assertEquals(305, GamepadKeycodes.BTN_EAST)
        assertEquals(307, GamepadKeycodes.BTN_WEST)
        assertEquals(308, GamepadKeycodes.BTN_NORTH)
    }

    @Test(expected = IllegalArgumentException::class)
    fun hat_invalidAxis_throws() {
        ShellGamepadInjector.hat(axis = 2, value = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun hat_invalidValue_throws() {
        ShellGamepadInjector.hat(axis = 0, value = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun joystick_invalidAxis_throws() {
        ShellGamepadInjector.joystick(axisCode = 99, value = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun joystick_outOfRangeValue_throws() {
        ShellGamepadInjector.joystick(axisCode = GamepadKeycodes.ABS_X, value = 40000)
    }
}
