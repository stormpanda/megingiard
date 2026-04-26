package com.stormpanda.megingiard.macropad

/**
 * Linux BTN_* constants for gamepad buttons as registered in `gamepadinjector.c`.
 *
 * Values match `<linux/input-event-codes.h>`.
 */
object GamepadKeycodes {

    // Face buttons (XInput / PlayStation names)
    const val BTN_SOUTH   = 304  // A  / Cross
    const val BTN_EAST    = 305  // B  / Circle
    const val BTN_NORTH   = 308  // Y  / Triangle
    const val BTN_WEST    = 307  // X  / Square

    // Shoulder buttons
    const val BTN_TL      = 310  // L1 / Left shoulder
    const val BTN_TR      = 311  // R1 / Right shoulder
    const val BTN_TL2     = 312  // L2 / Left trigger
    const val BTN_TR2     = 313  // R2 / Right trigger

    // Stick click
    const val BTN_THUMBL  = 317  // L3 / Left stick press
    const val BTN_THUMBR  = 318  // R3 / Right stick press

    // System buttons
    const val BTN_START   = 315
    const val BTN_SELECT  = 314
    const val BTN_MODE    = 316  // Guide / Home

    // -------------------------------------------------------------------------
    // Analog joystick axes (Linux ABS_* codes from <linux/input-event-codes.h>)
    // -------------------------------------------------------------------------

    const val ABS_X  = 0   // Left stick — horizontal
    const val ABS_Y  = 1   // Left stick — vertical
    const val ABS_Z  = 2   // Right stick — horizontal (Android standard: AXIS_Z)
    const val ABS_RZ = 5   // Right stick — vertical   (Android standard: AXIS_RZ)

    // -------------------------------------------------------------------------
    // Preset list — used by MacroPad editor to populate the gamepad-button picker
    // -------------------------------------------------------------------------

    data class GamepadButtonPreset(val code: Int, val label: String, val shortLabel: String)

    val PRESETS: List<GamepadButtonPreset> = listOf(
        GamepadButtonPreset(BTN_SOUTH,  "A / Cross",           "A"),
        GamepadButtonPreset(BTN_EAST,   "B / Circle",          "B"),
        GamepadButtonPreset(BTN_NORTH,  "Y / Triangle",        "Y"),
        GamepadButtonPreset(BTN_WEST,   "X / Square",          "X"),
        GamepadButtonPreset(BTN_TL,     "L1 (Left Shoulder)",  "L1"),
        GamepadButtonPreset(BTN_TR,     "R1 (Right Shoulder)", "R1"),
        GamepadButtonPreset(BTN_TL2,    "L2 (Left Trigger)",   "L2"),
        GamepadButtonPreset(BTN_TR2,    "R2 (Right Trigger)",  "R2"),
        GamepadButtonPreset(BTN_THUMBL, "L3 (Left Stick)",     "L3"),
        GamepadButtonPreset(BTN_THUMBR, "R3 (Right Stick)",    "R3"),
        GamepadButtonPreset(BTN_START,  "Start",               "ST"),
        GamepadButtonPreset(BTN_SELECT, "Select",              "SE"),
        GamepadButtonPreset(BTN_MODE,   "Guide / Home",        "🏠"),
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
fun GamepadKeycodes.GamepadButtonPreset.displayLabel(swapFaceButtons: Boolean): String = when {
    swapFaceButtons && code == GamepadKeycodes.BTN_SOUTH -> "B / Cross"
    swapFaceButtons && code == GamepadKeycodes.BTN_EAST  -> "A / Circle"
    swapFaceButtons && code == GamepadKeycodes.BTN_NORTH -> "X / Triangle"
    swapFaceButtons && code == GamepadKeycodes.BTN_WEST  -> "Y / Square"
    else                                                 -> label
}

/**
 * Returns the short display label of this preset, honouring the global face-button swap setting.
 *
 * Example: BTN_SOUTH ("A") → "B" when [swapFaceButtons] is `true`.
 */
fun GamepadKeycodes.GamepadButtonPreset.displayShortLabel(swapFaceButtons: Boolean): String = when {
    swapFaceButtons && code == GamepadKeycodes.BTN_SOUTH -> "B"
    swapFaceButtons && code == GamepadKeycodes.BTN_EAST  -> "A"
    swapFaceButtons && code == GamepadKeycodes.BTN_NORTH -> "X"
    swapFaceButtons && code == GamepadKeycodes.BTN_WEST  -> "Y"
    else                                                 -> shortLabel
}
