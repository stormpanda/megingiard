package com.stormpanda.megingiard.macropad

/**
 * Linux BTN_* constants for gamepad buttons as registered in `gamepadinjector.c`.
 *
 * Values match `<linux/input-event-codes.h>`.
 */
object GamepadKeycodes {
    // Face buttons (XInput / PlayStation names)
    const val BTN_SOUTH = 304 // A  / Cross
    const val BTN_EAST = 305 // B  / Circle
    const val BTN_NORTH = 308 // Y  / Triangle
    const val BTN_WEST = 307 // X  / Square

    // Shoulder buttons
    const val BTN_TL = 310 // L1 / Left shoulder
    const val BTN_TR = 311 // R1 / Right shoulder
    const val BTN_TL2 = 312 // L2 / Left trigger
    const val BTN_TR2 = 313 // R2 / Right trigger

    // Stick click
    const val BTN_THUMBL = 317 // L3 / Left stick press
    const val BTN_THUMBR = 318 // R3 / Right stick press

    // System buttons
    const val BTN_START = 315
    const val BTN_SELECT = 314
    const val BTN_MODE = 316 // Guide / Home

    // D-Pad buttons (Linux BTN_DPAD_* codes)
    const val BTN_DPAD_UP = 544
    const val BTN_DPAD_DOWN = 545
    const val BTN_DPAD_LEFT = 546
    const val BTN_DPAD_RIGHT = 547

    // D-Pad diagonal pseudo-codes
    const val CODE_DPAD_UP_LEFT = 616
    const val CODE_DPAD_UP_RIGHT = 617
    const val CODE_DPAD_DOWN_LEFT = 618
    const val CODE_DPAD_DOWN_RIGHT = 619

    // Left Stick directional pseudo-codes
    const val CODE_LS_UP = 600
    const val CODE_LS_DOWN = 601
    const val CODE_LS_LEFT = 602
    const val CODE_LS_RIGHT = 603
    const val CODE_LS_UP_LEFT = 608
    const val CODE_LS_UP_RIGHT = 609
    const val CODE_LS_DOWN_LEFT = 610
    const val CODE_LS_DOWN_RIGHT = 611

    // Right Stick directional pseudo-codes
    const val CODE_RS_UP = 604
    const val CODE_RS_DOWN = 605
    const val CODE_RS_LEFT = 606
    const val CODE_RS_RIGHT = 607
    const val CODE_RS_UP_LEFT = 612
    const val CODE_RS_UP_RIGHT = 613
    const val CODE_RS_DOWN_LEFT = 614
    const val CODE_RS_DOWN_RIGHT = 615

    // -------------------------------------------------------------------------
    // Analog joystick axes (Linux ABS_* codes from <linux/input-event-codes.h>)
    // -------------------------------------------------------------------------

    const val ABS_X = 0 // Left stick — horizontal
    const val ABS_Y = 1 // Left stick — vertical
    const val ABS_Z = 2 // Right stick — horizontal (Android standard: AXIS_Z)
    const val ABS_RZ = 5 // Right stick — vertical   (Android standard: AXIS_RZ)

    // -------------------------------------------------------------------------
    // Preset list — used by MacroPad editor to populate the gamepad-button picker
    // -------------------------------------------------------------------------

    data class GamepadButtonPreset(
        val code: Int,
        val label: String,
        val shortLabel: String,
    )

    val PRESETS: List<GamepadButtonPreset> =
        listOf(
            GamepadButtonPreset(BTN_SOUTH, "A / Cross", "A"),
            GamepadButtonPreset(BTN_EAST, "B / Circle", "B"),
            GamepadButtonPreset(BTN_NORTH, "Y / Triangle", "Y"),
            GamepadButtonPreset(BTN_WEST, "X / Square", "X"),
            GamepadButtonPreset(BTN_TL, "L1 (Left Shoulder)", "L1"),
            GamepadButtonPreset(BTN_TR, "R1 (Right Shoulder)", "R1"),
            GamepadButtonPreset(BTN_TL2, "L2 (Left Trigger)", "L2"),
            GamepadButtonPreset(BTN_TR2, "R2 (Right Trigger)", "R2"),
            GamepadButtonPreset(BTN_THUMBL, "L3 (Left Stick Click)", "L3"),
            GamepadButtonPreset(BTN_THUMBR, "R3 (Right Stick Click)", "R3"),
            GamepadButtonPreset(BTN_START, "Start", "Start"),
            GamepadButtonPreset(BTN_SELECT, "Select", "Select"),
            GamepadButtonPreset(BTN_MODE, "Guide / Home", "🏠"),
            GamepadButtonPreset(BTN_DPAD_UP, "D-Pad Up", "D-Up"),
            GamepadButtonPreset(BTN_DPAD_DOWN, "D-Pad Down", "D-Down"),
            GamepadButtonPreset(BTN_DPAD_LEFT, "D-Pad Left", "D-Left"),
            GamepadButtonPreset(BTN_DPAD_RIGHT, "D-Pad Right", "D-Right"),
            GamepadButtonPreset(CODE_DPAD_UP_LEFT, "D-Pad Up-Left", "D-UL"),
            GamepadButtonPreset(CODE_DPAD_UP_RIGHT, "D-Pad Up-Right", "D-UR"),
            GamepadButtonPreset(CODE_DPAD_DOWN_LEFT, "D-Pad Down-Left", "D-DL"),
            GamepadButtonPreset(CODE_DPAD_DOWN_RIGHT, "D-Pad Down-Right", "D-DR"),
            GamepadButtonPreset(CODE_LS_UP, "Left Stick Up", "LS Up"),
            GamepadButtonPreset(CODE_LS_DOWN, "Left Stick Down", "LS Down"),
            GamepadButtonPreset(CODE_LS_LEFT, "Left Stick Left", "LS Left"),
            GamepadButtonPreset(CODE_LS_RIGHT, "Left Stick Right", "LS Right"),
            GamepadButtonPreset(CODE_LS_UP_LEFT, "Left Stick Up-Left", "LS UL"),
            GamepadButtonPreset(CODE_LS_UP_RIGHT, "Left Stick Up-Right", "LS UR"),
            GamepadButtonPreset(CODE_LS_DOWN_LEFT, "Left Stick Down-Left", "LS DL"),
            GamepadButtonPreset(CODE_LS_DOWN_RIGHT, "Left Stick Down-Right", "LS DR"),
            GamepadButtonPreset(CODE_RS_UP, "Right Stick Up", "RS Up"),
            GamepadButtonPreset(CODE_RS_DOWN, "Right Stick Down", "RS Down"),
            GamepadButtonPreset(CODE_RS_LEFT, "Right Stick Left", "RS Left"),
            GamepadButtonPreset(CODE_RS_RIGHT, "Right Stick Right", "RS Right"),
            GamepadButtonPreset(CODE_RS_UP_LEFT, "Right Stick Up-Left", "RS UL"),
            GamepadButtonPreset(CODE_RS_UP_RIGHT, "Right Stick Up-Right", "RS UR"),
            GamepadButtonPreset(CODE_RS_DOWN_LEFT, "Right Stick Down-Left", "RS DL"),
            GamepadButtonPreset(CODE_RS_DOWN_RIGHT, "Right Stick Down-Right", "RS DR"),
        )
}

// ─────────────────────────────────────────────────────────────────────────────
// Display helpers — apply global A/B and X/Y swap settings to a preset
//
// Only the display labels are affected; injected keycodes (BTN_*) stay unchanged.
// These are pure functions so they can be used in both :core and :app without
// any Android or Compose dependency.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the full display label of this preset, honouring the global face-button swap setting.
 *
 * Example: BTN_SOUTH ("A / Cross") → "B / Cross" when [swapFaceButtons] is `true`.
 */
fun GamepadKeycodes.GamepadButtonPreset.displayLabel(swapFaceButtons: Boolean): String =
    when {
        swapFaceButtons && code == GamepadKeycodes.BTN_SOUTH -> "B / Cross"
        swapFaceButtons && code == GamepadKeycodes.BTN_EAST -> "A / Circle"
        swapFaceButtons && code == GamepadKeycodes.BTN_NORTH -> "X / Triangle"
        swapFaceButtons && code == GamepadKeycodes.BTN_WEST -> "Y / Square"
        else -> label
    }

/**
 * Returns the short display label of this preset, honouring the global face-button swap setting.
 *
 * Example: BTN_SOUTH ("A") → "B" when [swapFaceButtons] is `true`.
 */
fun GamepadKeycodes.GamepadButtonPreset.displayShortLabel(swapFaceButtons: Boolean): String =
    when {
        swapFaceButtons && code == GamepadKeycodes.BTN_SOUTH -> "B"
        swapFaceButtons && code == GamepadKeycodes.BTN_EAST -> "A"
        swapFaceButtons && code == GamepadKeycodes.BTN_NORTH -> "X"
        swapFaceButtons && code == GamepadKeycodes.BTN_WEST -> "Y"
        else -> shortLabel
    }

/**
 * Updates a gamepad button action's slot ([slotIndex]: 0 = primary, 1 = extra 1, 2 = extra 2, 3 = extra 3)
 * with the specified [selectedCode] (or `null` to clear).
 *
 * Tapping an already selected code on an extra slot toggles it off (`null`).
 * The primary button code is automatically excluded from extra buttons.
 */
fun updateGamepadButtonSlot(
    currentAction: PadAction.GamepadButton,
    slotIndex: Int,
    selectedCode: Int?,
    swapFaceButtons: Boolean = false,
): PadAction.GamepadButton {
    if (slotIndex == 0) {
        val preset =
            GamepadKeycodes.PRESETS.firstOrNull { it.code == selectedCode }
                ?: GamepadKeycodes.PRESETS.first()
        val newExtras = currentAction.extraBtnCodes.filter { it != preset.code }
        return currentAction.copy(
            btnCode = preset.code,
            label = preset.displayShortLabel(swapFaceButtons),
            extraBtnCodes = newExtras,
        )
    }

    val extraIdx = slotIndex - 1
    var e1 = currentAction.extraBtnCodes.getOrNull(0)
    var e2 = currentAction.extraBtnCodes.getOrNull(1)
    var e3 = currentAction.extraBtnCodes.getOrNull(2)

    when (extraIdx) {
        0 -> {
            e1 = if (selectedCode == e1) null else selectedCode
            if (e2 == selectedCode) e2 = null
            if (e3 == selectedCode) e3 = null
        }

        1 -> {
            e2 = if (selectedCode == e2) null else selectedCode
            if (e1 == selectedCode) e1 = null
            if (e3 == selectedCode) e3 = null
        }

        2 -> {
            e3 = if (selectedCode == e3) null else selectedCode
            if (e1 == selectedCode) e1 = null
            if (e2 == selectedCode) e2 = null
        }
    }

    val newExtras = listOfNotNull(e1, e2, e3).filter { it != currentAction.btnCode }
    return currentAction.copy(extraBtnCodes = newExtras)
}
