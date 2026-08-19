package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadKeycodesTest {
    @Test
    fun testPresetsListContainsAllCodes() {
        val codes = GamepadKeycodes.PRESETS.map { it.code }.toSet()

        assertTrue(codes.contains(GamepadKeycodes.BTN_SOUTH))
        assertTrue(codes.contains(GamepadKeycodes.BTN_EAST))
        assertTrue(codes.contains(GamepadKeycodes.BTN_NORTH))
        assertTrue(codes.contains(GamepadKeycodes.BTN_WEST))

        assertTrue(codes.contains(GamepadKeycodes.BTN_TL))
        assertTrue(codes.contains(GamepadKeycodes.BTN_TR))
        assertTrue(codes.contains(GamepadKeycodes.BTN_TL2))
        assertTrue(codes.contains(GamepadKeycodes.BTN_TR2))

        assertTrue(codes.contains(GamepadKeycodes.BTN_START))
        assertTrue(codes.contains(GamepadKeycodes.BTN_SELECT))
        assertTrue(codes.contains(GamepadKeycodes.BTN_MODE))

        assertTrue(codes.contains(GamepadKeycodes.BTN_THUMBL))
        assertTrue(codes.contains(GamepadKeycodes.BTN_THUMBR))

        assertTrue(codes.contains(GamepadKeycodes.BTN_DPAD_UP))
        assertTrue(codes.contains(GamepadKeycodes.BTN_DPAD_DOWN))
        assertTrue(codes.contains(GamepadKeycodes.BTN_DPAD_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.BTN_DPAD_RIGHT))

        assertTrue(codes.contains(GamepadKeycodes.CODE_DPAD_UP_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_DPAD_UP_RIGHT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_DPAD_DOWN_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_DPAD_DOWN_RIGHT))

        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_UP))
        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_DOWN))
        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_RIGHT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_UP_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_UP_RIGHT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_DOWN_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_LS_DOWN_RIGHT))

        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_UP))
        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_DOWN))
        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_RIGHT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_UP_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_UP_RIGHT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_DOWN_LEFT))
        assertTrue(codes.contains(GamepadKeycodes.CODE_RS_DOWN_RIGHT))
    }

    @Test
    fun testSelectAndStartShortLabels() {
        val selectPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SELECT }
        val startPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_START }

        assertEquals("Select", selectPreset.shortLabel)
        assertEquals("Start", startPreset.shortLabel)
    }

    @Test
    fun testFaceButtonSwapModifiesShortLabels() {
        val southPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SOUTH }
        val eastPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_EAST }
        val northPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_NORTH }
        val westPreset = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_WEST }

        // Normal (Xbox layout)
        assertEquals("A", southPreset.displayShortLabel(swapFaceButtons = false))
        assertEquals("B", eastPreset.displayShortLabel(swapFaceButtons = false))
        assertEquals("Y", northPreset.displayShortLabel(swapFaceButtons = false))
        assertEquals("X", westPreset.displayShortLabel(swapFaceButtons = false))

        // Swapped (Nintendo layout)
        assertEquals("B", southPreset.displayShortLabel(swapFaceButtons = true))
        assertEquals("A", eastPreset.displayShortLabel(swapFaceButtons = true))
        assertEquals("X", northPreset.displayShortLabel(swapFaceButtons = true))
        assertEquals("Y", westPreset.displayShortLabel(swapFaceButtons = true))
    }
}
