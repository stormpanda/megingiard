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
