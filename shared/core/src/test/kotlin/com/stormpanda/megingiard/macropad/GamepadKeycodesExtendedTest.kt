package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadKeycodesExtendedTest {
    @Test
    fun testFaceButtonDisplayLabels_standardVsSwapped() {
        val south = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_SOUTH }
        val east = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_EAST }
        val north = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_NORTH }
        val west = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_WEST }

        // Standard layout (Xbox / PC)
        assertEquals("A / Cross", south.displayLabel(swapFaceButtons = false))
        assertEquals("A", south.displayShortLabel(swapFaceButtons = false))
        assertEquals("B / Circle", east.displayLabel(swapFaceButtons = false))
        assertEquals("B", east.displayShortLabel(swapFaceButtons = false))
        assertEquals("Y / Triangle", north.displayLabel(swapFaceButtons = false))
        assertEquals("Y", north.displayShortLabel(swapFaceButtons = false))
        assertEquals("X / Square", west.displayLabel(swapFaceButtons = false))
        assertEquals("X", west.displayShortLabel(swapFaceButtons = false))

        // Swapped layout (Nintendo)
        assertEquals("B / Cross", south.displayLabel(swapFaceButtons = true))
        assertEquals("B", south.displayShortLabel(swapFaceButtons = true))
        assertEquals("A / Circle", east.displayLabel(swapFaceButtons = true))
        assertEquals("A", east.displayShortLabel(swapFaceButtons = true))
        assertEquals("X / Triangle", north.displayLabel(swapFaceButtons = true))
        assertEquals("X", north.displayShortLabel(swapFaceButtons = true))
        assertEquals("Y / Square", west.displayLabel(swapFaceButtons = true))
        assertEquals("Y", west.displayShortLabel(swapFaceButtons = true))
    }

    @Test
    fun testNonFaceButtonDisplayLabels_unaffectedBySwap() {
        val l1 = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_TL }
        val start = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_START }
        val dpadUp = GamepadKeycodes.PRESETS.first { it.code == GamepadKeycodes.BTN_DPAD_UP }

        assertEquals("L1 (Left Shoulder)", l1.displayLabel(swapFaceButtons = false))
        assertEquals("L1 (Left Shoulder)", l1.displayLabel(swapFaceButtons = true))
        assertEquals("Start", start.displayLabel(swapFaceButtons = true))
        assertEquals("D-Pad Up", dpadUp.displayLabel(swapFaceButtons = true))
    }

    @Test
    fun testAllPresetCodesAreDistinct() {
        val codes = GamepadKeycodes.PRESETS.map { it.code }
        val distinctCodes = codes.distinct()
        assertEquals(codes.size, distinctCodes.size)
    }

    @Test
    fun testUpdateGamepadButtonSlot_primarySlotUpdatesCodeAndLabel() {
        val initialAction = PadAction.GamepadButton(btnCode = GamepadKeycodes.BTN_SOUTH, label = "A")

        // Switch primary button to BTN_NORTH (Y)
        val updated =
            updateGamepadButtonSlot(
                currentAction = initialAction,
                slotIndex = 0,
                selectedCode = GamepadKeycodes.BTN_NORTH,
                swapFaceButtons = false,
            )

        assertEquals(GamepadKeycodes.BTN_NORTH, updated.btnCode)
        assertEquals("Y", updated.label)
        assertTrue(updated.extraBtnCodes.isEmpty())
    }

    @Test
    fun testUpdateGamepadButtonSlot_primarySlotRemovesDuplicateFromExtras() {
        val initialAction =
            PadAction.GamepadButton(
                btnCode = GamepadKeycodes.BTN_SOUTH,
                label = "A",
                extraBtnCodes = listOf(GamepadKeycodes.BTN_NORTH, GamepadKeycodes.BTN_TL),
            )

        // Switch primary button to BTN_NORTH (which was in extraBtnCodes)
        val updated =
            updateGamepadButtonSlot(
                currentAction = initialAction,
                slotIndex = 0,
                selectedCode = GamepadKeycodes.BTN_NORTH,
                swapFaceButtons = false,
            )

        assertEquals(GamepadKeycodes.BTN_NORTH, updated.btnCode)
        assertEquals(listOf(GamepadKeycodes.BTN_TL), updated.extraBtnCodes)
    }

    @Test
    fun testUpdateGamepadButtonSlot_extraSlotsAddAndToggleOff() {
        val initialAction = PadAction.GamepadButton(btnCode = GamepadKeycodes.BTN_SOUTH, label = "A")

        // Add extra button 1 (slotIndex = 1) -> BTN_TR
        val step1 =
            updateGamepadButtonSlot(
                currentAction = initialAction,
                slotIndex = 1,
                selectedCode = GamepadKeycodes.BTN_TR,
            )
        assertEquals(listOf(GamepadKeycodes.BTN_TR), step1.extraBtnCodes)

        // Add extra button 2 (slotIndex = 2) -> BTN_TL
        val step2 =
            updateGamepadButtonSlot(
                currentAction = step1,
                slotIndex = 2,
                selectedCode = GamepadKeycodes.BTN_TL,
            )
        assertEquals(listOf(GamepadKeycodes.BTN_TR, GamepadKeycodes.BTN_TL), step2.extraBtnCodes)

        // Tapping BTN_TR again on slotIndex 1 toggles it off
        val step3 =
            updateGamepadButtonSlot(
                currentAction = step2,
                slotIndex = 1,
                selectedCode = GamepadKeycodes.BTN_TR,
            )
        assertEquals(listOf(GamepadKeycodes.BTN_TL), step3.extraBtnCodes)
    }

    @Test
    fun testUpdateGamepadButtonSlot_extraSlotCannotAddPrimaryButton() {
        val initialAction = PadAction.GamepadButton(btnCode = GamepadKeycodes.BTN_SOUTH, label = "A")

        // Attempt to add primary button BTN_SOUTH as extra button 1
        val updated =
            updateGamepadButtonSlot(
                currentAction = initialAction,
                slotIndex = 1,
                selectedCode = GamepadKeycodes.BTN_SOUTH,
            )

        assertTrue(updated.extraBtnCodes.isEmpty())
    }
}
