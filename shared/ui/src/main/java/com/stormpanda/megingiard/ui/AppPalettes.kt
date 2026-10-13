package com.stormpanda.megingiard.ui

import androidx.compose.ui.graphics.Color

/**
 * Fixed, theme-independent color palettes of the design system.
 *
 * [AppColors] holds the themed roles (surface, accent, …) that change with the active theme.
 * This object holds the colors that intentionally **do not** follow the theme: user-selectable
 * swatches, hardware-mimicking colors, data-visualisation palettes and status probes. Screen
 * code must reference these tokens instead of declaring `Color(0x…)` literals (AGENTS.md §16.2,
 * enforced by `verifyArchitectureRules` → `HARDCODED_COLOR`).
 */
object AppPalettes {
    /** Accent color presets offered in Appearance settings (red → pink). */
    val accentPresets: List<Color> =
        listOf(
            Color(0xFFFF5252), // Red
            Color(0xFFFF7043), // Deep Orange
            Color(0xFFFFA726), // Orange
            Color(0xFFFFCA28), // Amber
            Color(0xFF66BB6A), // Green
            Color(0xFF26A69A), // Teal
            Color(0xFF29B6F6), // Light Blue
            Color(0xFF42A5F5), // Blue
            Color(0xFF7E57C2), // Deep Purple
            Color(0xFFEC407A), // Pink
        )

    /** Distinct per-pointer colors for multi-touch recording, indexed by slot (slot 1 first). */
    val touchPointerSlots: List<Color> =
        listOf(
            Color(0xFFFFB300), // Amber (Slot 1)
            Color(0xFF00E676), // Emerald Green (Slot 2)
            Color(0xFFFF4081), // Magenta / Pink (Slot 3)
            Color(0xFF00E5FF), // Cyan / Sky (Slot 4)
            Color(0xFFFF9100), // Deep Orange (Slot 5)
            Color(0xFFD500F9), // Purple / Violet (Slot 6)
            Color(0xFFAEEA00), // Lime (Slot 7)
            Color(0xFFFF6E40), // Coral (Slot 8)
            Color(0xFF1DE9B6), // Teal (Slot 9)
        )

    /** Full hue spectrum stops (red → yellow → green → cyan → blue → magenta → red) for hue sliders. */
    val hueSpectrum: List<Color> =
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF00FFFF),
            Color(0xFF0000FF),
            Color(0xFFFF00FF),
            Color(0xFFFF0000),
        )

    /** Face-button colors of the visual gamepad picker. */
    val gamepadFaceBlue = Color(0xFF42A5F5)
    val gamepadFaceGreen = Color(0xFF66BB6A)
    val gamepadFaceRed = Color(0xFFEF5350)
    val gamepadFaceYellow = Color(0xFFFFD54F)

    /** MacroPad ambient-neutral button colors used when a layout has no explicit color option. */
    val macroPadNeutralBackground = Color(0xB3FFFFFF) // 70% white (matching previous default resting level)
    val macroPadNeutralBorder = Color(0x99AAAAAA)
    val macroPadNeutralText = Color(0xFFDDDDDD).copy(alpha = 0.9f)

    /** Semi-transparent dark backing plate behind MacroPad button faces. */
    val padButtonBacking = Color(0x80121212)

    /** Anchor-testing probe results and probe outline. */
    val probeMatched = Color(0xFF00E676)
    val probeMismatched = Color(0xFFFF5252)
    val probeOutline = Color(0xCC000000)

    /** Battery level indicator below the low-battery threshold. */
    val batteryLow = Color(0xFFE57373)

    /** Polaroid-style screenshot preview frame (intentionally light in every theme). */
    val polaroidFrame = Color(0xFFFCFBF9) // Clean off-white Polaroid frame
    val polaroidFrameBorder = Color(0xFFE5E4E0)
    val polaroidImageBorder = Color(0x22000000)
}
