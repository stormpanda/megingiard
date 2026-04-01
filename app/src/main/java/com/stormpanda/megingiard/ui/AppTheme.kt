package com.stormpanda.megingiard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Theme mode ──────────────────────────────────────────────────────────────

enum class ThemeMode { DARK, LIGHT }

// ─── Semantic tokens ─────────────────────────────────────────────────────────
//
// All UI colors are expressed through these tokens.  Each screen replaces its
// private file-scoped color constants with references to LocalAppTheme.current.
// Adding a new theme requires only a new ThemePalette entry below — no
// per-screen changes are necessary.

@Immutable
data class AppColors(
    /** Full-screen background. */
    val appBackground: Color,
    /** Card / panel / row surface. */
    val surface: Color,
    /** Elevated surface, e.g. item being dragged. */
    val surfaceVariant: Color,
    /** Primary text on the above surfaces. */
    val onSurface: Color,
    /** Secondary / hint text (reduced emphasis). */
    val onSurfaceSecondary: Color,
    /** Subtle divider lines. */
    val divider: Color,
    /** Semi-transparent floating control pill (mirror, carousel). */
    val controlOverlay: Color,
    /** Text / icons on the control overlay. */
    val onControlOverlay: Color,
    /** Finger-circle indicator in overlay. */
    val fingerCircle: Color,
    /** Keyboard key background (normal). */
    val keyBackground: Color,
    /** Keyboard key background when pressed. */
    val keyPressed: Color,
    /** Keyboard modifier key when active / sticky. */
    val keyModifierActive: Color,
    /** Touchpad surface. */
    val touchpadBackground: Color,
    /** Touchpad indicator dots / borders. */
    val touchpadIndicator: Color,
    /** Color-picker dialog background. */
    val pickerBackground: Color,
    /** Accent color swatch border. */
    val accentBorder: Color,
)

// ─── Palettes ─────────────────────────────────────────────────────────────────

private val darkPalette = AppColors(
    appBackground       = Color(0xFF121212),
    surface             = Color(0xFF1C1C1E),
    surfaceVariant      = Color(0xFF2C2C2E),
    onSurface           = Color.White,
    onSurfaceSecondary  = Color.White.copy(alpha = 0.6f),
    divider             = Color.White.copy(alpha = 0.08f),
    controlOverlay      = Color.Black.copy(alpha = 0.8f),
    onControlOverlay    = Color.White,
    fingerCircle        = Color.White.copy(alpha = 0.45f),
    keyBackground       = Color(0xFF2C2C2E),
    keyPressed          = Color(0xFF48484A),
    keyModifierActive   = Color(0xFF3A3A3C),
    touchpadBackground  = Color.Black,
    touchpadIndicator   = Color.White,
    pickerBackground    = Color(0xFF1C1C1E),
    accentBorder        = Color.White.copy(alpha = 0.3f),
)

private val lightPalette = AppColors(
    appBackground       = Color(0xFFF2F2F7),
    surface             = Color(0xFFFFFFFF),
    surfaceVariant      = Color(0xFFE5E5EA),
    onSurface           = Color(0xFF1C1C1E),
    onSurfaceSecondary  = Color(0xFF1C1C1E).copy(alpha = 0.55f),
    divider             = Color(0xFF1C1C1E).copy(alpha = 0.08f),
    controlOverlay      = Color.White.copy(alpha = 0.85f),
    onControlOverlay    = Color(0xFF1C1C1E),
    fingerCircle        = Color(0xFF1C1C1E).copy(alpha = 0.35f),
    keyBackground       = Color(0xFFE5E5EA),
    keyPressed          = Color(0xFFCECED3),
    keyModifierActive   = Color(0xFFD1D1D6),
    touchpadBackground  = Color(0xFFE5E5EA),
    touchpadIndicator   = Color(0xFF1C1C1E),
    pickerBackground    = Color(0xFFFFFFFF),
    accentBorder        = Color(0xFF1C1C1E).copy(alpha = 0.2f),
)

// ─── Palette selector ─────────────────────────────────────────────────────────

fun paletteFor(mode: ThemeMode): AppColors = when (mode) {
    ThemeMode.DARK  -> darkPalette
    ThemeMode.LIGHT -> lightPalette
}

// ─── Material 3 ColorScheme bridges ───────────────────────────────────────────

private val darkColorScheme: ColorScheme = darkColorScheme()

private val lightColorScheme: ColorScheme = lightColorScheme()

fun colorSchemeFor(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.DARK  -> darkColorScheme
    ThemeMode.LIGHT -> lightColorScheme
}

// ─── CompositionLocal ─────────────────────────────────────────────────────────

val LocalAppColors = compositionLocalOf<AppColors> { darkPalette }
