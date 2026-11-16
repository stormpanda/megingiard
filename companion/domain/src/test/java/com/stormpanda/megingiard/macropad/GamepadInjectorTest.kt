package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.privd.PrivdClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GamepadInjectorTest {
    @After
    fun tearDown() {
        PrivdClient.clearQueueForTest()
        PrivdClient.isConnectedForTest = null
        GamepadInjector.isRecordingActive = false
    }

    @Test
    fun buttonDownAndUp_dpadDirections() {
        val dpadCodes =
            listOf(
                GamepadKeycodes.BTN_DPAD_UP,
                GamepadKeycodes.BTN_DPAD_DOWN,
                GamepadKeycodes.BTN_DPAD_LEFT,
                GamepadKeycodes.BTN_DPAD_RIGHT,
                GamepadKeycodes.CODE_DPAD_UP_LEFT,
                GamepadKeycodes.CODE_DPAD_UP_RIGHT,
                GamepadKeycodes.CODE_DPAD_DOWN_LEFT,
                GamepadKeycodes.CODE_DPAD_DOWN_RIGHT,
            )
        dpadCodes.forEach { code ->
            GamepadInjector.buttonDown(code)
            GamepadInjector.buttonUp(code)
        }
    }

    @Test
    fun buttonDownAndUp_joystickDirections() {
        val stickCodes =
            listOf(
                GamepadKeycodes.CODE_LS_UP,
                GamepadKeycodes.CODE_LS_DOWN,
                GamepadKeycodes.CODE_LS_LEFT,
                GamepadKeycodes.CODE_LS_RIGHT,
                GamepadKeycodes.CODE_LS_UP_LEFT,
                GamepadKeycodes.CODE_LS_UP_RIGHT,
                GamepadKeycodes.CODE_LS_DOWN_LEFT,
                GamepadKeycodes.CODE_LS_DOWN_RIGHT,
                GamepadKeycodes.CODE_RS_UP,
                GamepadKeycodes.CODE_RS_DOWN,
                GamepadKeycodes.CODE_RS_LEFT,
                GamepadKeycodes.CODE_RS_RIGHT,
                GamepadKeycodes.CODE_RS_UP_LEFT,
                GamepadKeycodes.CODE_RS_UP_RIGHT,
                GamepadKeycodes.CODE_RS_DOWN_LEFT,
                GamepadKeycodes.CODE_RS_DOWN_RIGHT,
            )
        stickCodes.forEach { code ->
            GamepadInjector.buttonDown(code)
            GamepadInjector.buttonUp(code)
        }
    }

    @Test
    fun buttonDownAndUp_faceAndShoulderButtons() {
        val standardCodes =
            listOf(
                GamepadKeycodes.BTN_SOUTH,
                GamepadKeycodes.BTN_EAST,
                GamepadKeycodes.BTN_NORTH,
                GamepadKeycodes.BTN_WEST,
                GamepadKeycodes.BTN_TL,
                GamepadKeycodes.BTN_TR,
                GamepadKeycodes.BTN_TL2,
                GamepadKeycodes.BTN_TR2,
                GamepadKeycodes.BTN_SELECT,
                GamepadKeycodes.BTN_START,
                GamepadKeycodes.BTN_THUMBL,
                GamepadKeycodes.BTN_THUMBR,
            )
        standardCodes.forEach { code ->
            GamepadInjector.buttonDown(code)
            GamepadInjector.buttonUp(code)
        }
    }

    @Test
    fun joystickAndHatMethods() {
        GamepadInjector.joystick(GamepadKeycodes.ABS_X, 1000)
        GamepadInjector.hat(0, 1)
        assertFalse(GamepadInjector.isRunning)
    }

    @Test
    fun buttonDownAndUp_l2AndR2EmitBothAnalogTriggerAndDigitalKey() {
        PrivdClient.isConnectedForTest = true
        PrivdClient.clearQueueForTest()

        GamepadInjector.buttonDown(GamepadKeycodes.BTN_TL2)
        GamepadInjector.buttonUp(GamepadKeycodes.BTN_TL2)
        GamepadInjector.buttonDown(GamepadKeycodes.BTN_TR2)
        GamepadInjector.buttonUp(GamepadKeycodes.BTN_TR2)

        val cmds =
            buildList {
                while (true) {
                    val cmd = PrivdClient.pollCommandForTest() ?: break
                    add(cmd)
                }
            }
        assertEquals(
            listOf(
                "JS 10 32767\n",
                "GD 312\n",
                "JS 10 0\n",
                "GU 312\n",
                "JS 9 32767\n",
                "GD 313\n",
                "JS 9 0\n",
                "GU 313\n",
            ),
            cmds,
        )
    }

    @Test
    fun digitalButtonDownAndUp_emitOnlyDigitalKeyWithoutOverwritingAxis() {
        PrivdClient.isConnectedForTest = true
        PrivdClient.clearQueueForTest()

        GamepadInjector.digitalButtonDown(GamepadKeycodes.BTN_TL2)
        GamepadInjector.digitalButtonUp(GamepadKeycodes.BTN_TL2)

        val cmds =
            buildList {
                while (true) {
                    val cmd = PrivdClient.pollCommandForTest() ?: break
                    add(cmd)
                }
            }
        assertEquals(listOf("GD 312\n", "GU 312\n"), cmds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun joystick_rejectsNegativeTriggerValue() {
        PrivdClient.isConnectedForTest = true
        GamepadInjector.joystick(GamepadKeycodes.ABS_BRAKE, -1)
    }
}
