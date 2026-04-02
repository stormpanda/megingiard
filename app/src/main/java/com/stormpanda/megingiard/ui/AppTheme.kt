package com.stormpanda.megingiard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Theme mode ──────────────────────────────────────────────────────────────

/**
 * [supportsCustomAccent] – true when the user can override the palette's built-in
 * accent colour from Global Settings. false means the theme ships its own accent
 * and the colour picker is hidden.
 */
enum class ThemeMode(val supportsCustomAccent: Boolean) {
    DARK(supportsCustomAccent = true),
    LIGHT(supportsCustomAccent = true),
    CYBERPUNK(supportsCustomAccent = false),
}

// ─── Semantic tokens ─────────────────────────────────────────────────────────
//
// All UI colors are expressed through these tokens.  Each screen replaces its
// private file-scoped color constants with references to LocalAppColors.current.
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
    /**
     * Primary interactive / accent colour. For [ThemeMode.DARK] and [ThemeMode.LIGHT] this
     * is the user-overridable accent. For fixed themes like [ThemeMode.CYBERPUNK] it is
     * baked into the palette and cannot be changed by the user.
     */
    val accent: Color,
    /** Text / icons on accent / highlighted buttons. */
    val onAccent: Color,
    /** Always-visible pull-tab pill colour (already includes the desired alpha). */
    val pillIdleColor: Color,
    /** Active mode indicator dot inside the navigation pill. */
    val controlIndicatorActive: Color,
    /** Background of the navigation pill (overrides accent for custom-accent themes). */
    val navPillBody: Color,
    /** Background of mirror control buttons. */
    val buttonBody: Color,
    /** Border/outline of the carousel control overlay container. */
    val controlOverlayBorder: Color,
    /** Border/outline of the navigation pill. */
    val navPillBorder: Color,
    /** Border/outline of the mirror control pill. */
    val mirrorPillBorder: Color,
    /** Icon tint on mirror control buttons. */
    val buttonIconTint: Color,
)

// ─── Palettes ─────────────────────────────────────────────────────────────────

// Default accent for Dark/Light — overridden at runtime by SettingsManager.accentColor.
private val DEFAULT_DARK_LIGHT_ACCENT = Color(0xFFCC0000)

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
    accentBorder               = Color.White.copy(alpha = 0.3f),
    accent                     = DEFAULT_DARK_LIGHT_ACCENT,
    onAccent                   = Color.White,
    pillIdleColor              = Color.White.copy(alpha = 0.4f),
    controlIndicatorActive     = Color.White,
    navPillBody                = DEFAULT_DARK_LIGHT_ACCENT,
    buttonBody                 = DEFAULT_DARK_LIGHT_ACCENT,
    controlOverlayBorder       = Color.Transparent,
    navPillBorder              = Color.Transparent,
    mirrorPillBorder           = Color.Transparent,
    buttonIconTint             = Color.White,
)

private val lightPalette = AppColors(
    appBackground       = Color(0xFFF2F2F7),
    surface             = Color(0xFFFFFFFF),
    surfaceVariant      = Color(0xFFE5E5EA),
    onSurface           = Color(0xFF1C1C1E),
    onSurfaceSecondary  = Color(0xFF1C1C1E).copy(alpha = 0.55f),
    divider             = Color(0xFF1C1C1E).copy(alpha = 0.08f),
    controlOverlay      = Color.Black.copy(alpha = 0.8f),
    onControlOverlay    = Color.White,
    fingerCircle        = Color.White.copy(alpha = 0.45f),
    keyBackground       = Color(0xFFE5E5EA),
    keyPressed          = Color(0xFFCECED3),
    keyModifierActive   = Color(0xFFD1D1D6),
    touchpadBackground  = Color(0xFFE5E5EA),
    touchpadIndicator   = Color(0xFF1C1C1E),
    pickerBackground    = Color(0xFFFFFFFF),
    accentBorder               = Color(0xFF1C1C1E).copy(alpha = 0.2f),
    accent                     = DEFAULT_DARK_LIGHT_ACCENT,
    onAccent                   = Color.White,
    pillIdleColor              = Color.White.copy(alpha = 0.4f),
    controlIndicatorActive     = Color.White,
    navPillBody                = DEFAULT_DARK_LIGHT_ACCENT,
    buttonBody                 = DEFAULT_DARK_LIGHT_ACCENT,
    controlOverlayBorder       = Color.Transparent,
    navPillBorder              = Color.Transparent,
    mirrorPillBorder           = Color.Transparent,
    buttonIconTint             = Color.White,
)

// ─── Cyberpunk palette ────────────────────────────────────────────────────────
// Colours derived from the Cyberpunk 2077 main menu:
//   Background → dark blood red     ~0xFF160709
//   Menu text  → vivid red          ~0xFFED2224
//   Selection  → cyan               ~0xFF00CCFF
private val CP_ACCENT      = Color(0xFF00CCFF)   // cyan — primary interactive / accent
private val CP_BG          = Color(0xFF160709)   // dark blood-red background
private val CP_SURFACE     = Color(0xFF220C0F)   // slightly lighter surface
private val CP_SURFACE2    = Color(0xFF2E1115)   // elevated / dragged surface
private val CP_TEXT        = Color(0xFFED2224)   // vivid red text
private val CP_DARK_RED    = Color(0xFF8B0000)   // dark red for overlay and button text
internal val CP_TP_YELLOW   = Color(0xFFFFED00)   // bright yellow for trackpoint dot only

private val cyberpunkPalette = AppColors(
    appBackground       = CP_BG,
    surface             = CP_SURFACE,
    surfaceVariant      = CP_SURFACE2,
    onSurface           = CP_TEXT,
    onSurfaceSecondary  = CP_TEXT.copy(alpha = 0.55f),
    divider             = CP_TEXT.copy(alpha = 0.10f),
    controlOverlay      = CP_SURFACE,
    onControlOverlay    = CP_DARK_RED,
    fingerCircle        = Color.White.copy(alpha = 0.45f),
    keyBackground       = CP_SURFACE,
    keyPressed          = CP_SURFACE2,
    keyModifierActive   = CP_SURFACE2,
    touchpadBackground  = CP_BG,
    touchpadIndicator   = CP_ACCENT,
    pickerBackground    = CP_SURFACE,
    accentBorder               = CP_ACCENT.copy(alpha = 0.35f),
    accent                     = CP_ACCENT,
    onAccent                   = CP_DARK_RED,
    pillIdleColor              = CP_TP_YELLOW,
    controlIndicatorActive     = CP_ACCENT,
    navPillBody                = CP_SURFACE,
    buttonBody                 = CP_SURFACE,
    controlOverlayBorder       = CP_DARK_RED,
    navPillBorder              = CP_ACCENT,
    mirrorPillBorder           = Color.Transparent,
    buttonIconTint             = CP_ACCENT,
)

// ─── Palette selector ─────────────────────────────────────────────────────────

/**
 * Returns the [AppColors] palette for [mode], optionally overriding the accent
 * token with a user-chosen colour.  The override is only applied when
 * [ThemeMode.supportsCustomAccent] is true; fixed themes (e.g. Cyberpunk) ignore it.
 */
fun paletteFor(mode: ThemeMode, userAccent: Color? = null): AppColors {
    val base = when (mode) {
        ThemeMode.DARK      -> darkPalette
        ThemeMode.LIGHT     -> lightPalette
        ThemeMode.CYBERPUNK -> cyberpunkPalette
    }
    return if (mode.supportsCustomAccent) {
        val eff = userAccent ?: base.accent
        base.copy(accent = eff, navPillBody = eff, buttonBody = eff)
    } else base
}

// ─── Material 3 ColorScheme bridges ───────────────────────────────────────────

private val darkColorScheme: ColorScheme = darkColorScheme()

private val lightColorScheme: ColorScheme = lightColorScheme()

fun colorSchemeFor(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.DARK      -> darkColorScheme
    ThemeMode.LIGHT     -> lightColorScheme
    ThemeMode.CYBERPUNK -> darkColorScheme  // Cyberpunk is dark-based
}

// ─── CompositionLocal ─────────────────────────────────────────────────────────

val LocalAppColors = compositionLocalOf<AppColors> { darkPalette }
