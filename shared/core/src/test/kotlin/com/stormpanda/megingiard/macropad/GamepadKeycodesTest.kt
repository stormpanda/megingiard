package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadKeycodesTest {
    private fun updateSlot(
        action: PadAction.GamepadButton,
        slotIndex: Int,
        selectedCode: Int?,
        swap: Boolean = false,
    ) = updateGamepadButtonSlot(
        currentAction = action,
        slotIndex = slotIndex,
        selectedCode = selectedCode,
        swapFaceButtons = swap,
    )

    @Test
    fun testPresetsListContainsAllCodes() {
        val codes = GamepadKeycodes.PRESETS.map { it.code }.toSet()
        val expected =
            setOf(
                GamepadKeycodes.BTN_SOUTH,
                GamepadKeycodes.BTN_EAST,
                GamepadKeycodes.BTN_NORTH,
                GamepadKeycodes.BTN_WEST,
                GamepadKeycodes.BTN_TL,
                GamepadKeycodes.BTN_TR,
                GamepadKeycodes.BTN_TL2,
                GamepadKeycodes.BTN_TR2,
                GamepadKeycodes.BTN_START,
                GamepadKeycodes.BTN_SELECT,
                GamepadKeycodes.BTN_MODE,
                GamepadKeycodes.BTN_THUMBL,
                GamepadKeycodes.BTN_THUMBR,
                GamepadKeycodes.BTN_DPAD_UP,
                GamepadKeycodes.BTN_DPAD_DOWN,
                GamepadKeycodes.BTN_DPAD_LEFT,
                GamepadKeycodes.BTN_DPAD_RIGHT,
                GamepadKeycodes.CODE_DPAD_UP_LEFT,
                GamepadKeycodes.CODE_DPAD_UP_RIGHT,
                GamepadKeycodes.CODE_DPAD_DOWN_LEFT,
                GamepadKeycodes.CODE_DPAD_DOWN_RIGHT,
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
        assertTrue(codes.containsAll(expected))
    }

    @Test
    fun testSelectAndStartShortLabels() {
        assertEquals("Select", GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SELECT }.shortLabel)
        assertEquals("Start", GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_START }.shortLabel)
    }

    @Test
    fun testFaceButtonSwapModifiesShortLabels() {
        val south = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SOUTH }
        val east = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_EAST }
        val north = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_NORTH }
        val west = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_WEST }

        // Normal (Xbox layout)
        assertEquals(listOf("A", "B", "Y", "X"), listOf(south, east, north, west).map { it.displayShortLabel(false) })
        // Swapped (Nintendo layout)
        assertEquals(listOf("B", "A", "X", "Y"), listOf(south, east, north, west).map { it.displayShortLabel(true) })
    }

    @Test
    fun testUpdateGamepadButtonSlotPrimary() {
        val initial =
            PadAction.GamepadButton(
                btnCode = GamepadKeycodes.BTN_SOUTH,
                label = "A",
                extraBtnCodes = listOf(GamepadKeycodes.BTN_NORTH, GamepadKeycodes.BTN_TL),
            )
        val updated = updateSlot(initial, slotIndex = 0, selectedCode = GamepadKeycodes.BTN_NORTH)

        assertEquals(GamepadKeycodes.BTN_NORTH, updated.btnCode)
        assertEquals("Y", updated.label)
        assertEquals(listOf(GamepadKeycodes.BTN_TL), updated.extraBtnCodes)
    }

    @Test
    fun testUpdateGamepadButtonSlotExtras() {
        val initial = PadAction.GamepadButton(btnCode = GamepadKeycodes.BTN_SOUTH, label = "A", extraBtnCodes = emptyList())

        val withExtra1 = updateSlot(initial, slotIndex = 1, selectedCode = GamepadKeycodes.BTN_TL)
        assertEquals(listOf(GamepadKeycodes.BTN_TL), withExtra1.extraBtnCodes)

        val withExtra2 = updateSlot(withExtra1, slotIndex = 2, selectedCode = GamepadKeycodes.BTN_TR)
        assertEquals(listOf(GamepadKeycodes.BTN_TL, GamepadKeycodes.BTN_TR), withExtra2.extraBtnCodes)

        val withExtra3 = updateSlot(withExtra2, slotIndex = 3, selectedCode = GamepadKeycodes.BTN_TL2)
        assertEquals(listOf(GamepadKeycodes.BTN_TL, GamepadKeycodes.BTN_TR, GamepadKeycodes.BTN_TL2), withExtra3.extraBtnCodes)

        val tryAddPrimaryAsExtra = updateSlot(withExtra3, slotIndex = 1, selectedCode = GamepadKeycodes.BTN_SOUTH)
        assertEquals(listOf(GamepadKeycodes.BTN_TR, GamepadKeycodes.BTN_TL2), tryAddPrimaryAsExtra.extraBtnCodes)
    }

    @Test
    fun testUpdateGamepadButtonSlotToggleAndClear() {
        val initial =
            PadAction.GamepadButton(
                btnCode = GamepadKeycodes.BTN_SOUTH,
                label = "A",
                extraBtnCodes = listOf(GamepadKeycodes.BTN_TL, GamepadKeycodes.BTN_TR),
            )

        val toggled = updateSlot(initial, slotIndex = 1, selectedCode = GamepadKeycodes.BTN_TL)
        assertEquals(listOf(GamepadKeycodes.BTN_TR), toggled.extraBtnCodes)

        val cleared = updateSlot(initial, slotIndex = 2, selectedCode = null)
        assertEquals(listOf(GamepadKeycodes.BTN_TL), cleared.extraBtnCodes)
    }
}
