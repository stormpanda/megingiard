package com.stormpanda.megingiard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.settings.ThemeMode

// ─── Semantic color tokens ────────────────────────────────────────────────────
//
// All UI colors are expressed through these tokens.  Each screen reads colors
// from LocalAppColors.current or MaterialTheme.colorScheme — never hardcodes
// Color(0xFF…) inline.  Adding a new theme requires only a new AppColors
// instance below — no per-screen changes are necessary.

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
    /** Semi-transparent floating control pill (mirror, pill menu). */
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
    /** Border/outline of the pill menu control overlay container. */
    val controlOverlayBorder: Color,
    /** Border/outline of the navigation pill. */
    val navPillBorder: Color,
    /** Border/outline of the mirror control pill. */
    val mirrorPillBorder: Color,
    /** Icon tint on mirror control buttons. */
    val buttonIconTint: Color,
    /** Destructive / error action color (delete buttons, confirm-destructive text). */
    val error: Color,
    /** Content (text/icons) on error-colored surfaces. */
    val onError: Color,
    /** Action-type badge color for gamepad / joystick macro steps. */
    val actionColorGamepad: Color,
    /** Action-type badge color for system / d-pad macro steps. */
    val actionColorSystem: Color,
    /** MacroPad button-placement surface. */
    val macroPadSurface: Color,
    /** MacroPad button-placement text/icons. */
    val macroPadOnSurface: Color,
    /** MacroPad button-placement border. */
    val macroPadAccentBorder: Color,
    /**
     * Color used for section-header label text (uppercase strip above setting groups,
     * editor section dividers, etc.).  Equals [accent] for themes that support a custom
     * accent; fixed per-palette for themes like Cyberpunk that use a distinct header tint.
     */
    val sectionHeaderColor: Color,
    /** Thin divider between transparent settings rows drawn on the default screen/dialog background. */
    val settingsSeparator: Color,
)

// ─── Palettes ─────────────────────────────────────────────────────────────────

// Default accent for Dark/Light — overridden at runtime by SettingsManager.accentColor.
private val DEFAULT_DARK_LIGHT_ACCENT = Color(0xFFCC0000)

private val darkPalette = AppColors(
    appBackground          = Color(0xFF121212),
    surface                = Color(0xFF1C1C1E),
    surfaceVariant         = Color(0xFF2C2C2E),
    onSurface              = Color.White,
    onSurfaceSecondary     = Color.White.copy(alpha = 0.6f),
    divider                = Color.White.copy(alpha = 0.08f),
    controlOverlay         = Color.Black.copy(alpha = 0.8f),
    onControlOverlay       = Color.White,
    fingerCircle           = Color.White.copy(alpha = 0.45f),
    keyBackground          = Color(0xFF2C2C2E),
    keyPressed             = Color(0xFF48484A),
    keyModifierActive      = Color(0xFF3A3A3C),
    touchpadBackground     = Color.Black,
    touchpadIndicator      = Color.White,
    pickerBackground       = Color(0xFF1C1C1E),
    accentBorder           = Color.White.copy(alpha = 0.3f),
    accent                 = DEFAULT_DARK_LIGHT_ACCENT,
    onAccent               = Color.White,
    pillIdleColor          = Color.White.copy(alpha = 0.4f),
    controlIndicatorActive = Color.White,
    navPillBody            = DEFAULT_DARK_LIGHT_ACCENT,
    buttonBody             = DEFAULT_DARK_LIGHT_ACCENT,
    controlOverlayBorder   = Color.Transparent,
    navPillBorder          = Color.Transparent,
    mirrorPillBorder       = Color.Transparent,
    buttonIconTint         = Color.White,
    error                  = Color(0xFFCF6679),
    onError                = Color.White,
    actionColorGamepad     = Color(0xFFFF9800),
    actionColorSystem      = Color(0xFF2196F3),
    macroPadSurface        = Color(0xFF1C1C1E),
    macroPadOnSurface      = Color.White,
    macroPadAccentBorder   = Color.White.copy(alpha = 0.3f),
    sectionHeaderColor     = DEFAULT_DARK_LIGHT_ACCENT,
    settingsSeparator      = Color.White.copy(alpha = 0.10f),
)

private val lightPalette = AppColors(
    appBackground          = Color(0xFFF2F2F7),
    surface                = Color(0xFFFFFFFF),
    surfaceVariant         = Color(0xFFE5E5EA),
    onSurface              = Color(0xFF1C1C1E),
    onSurfaceSecondary     = Color(0xFF1C1C1E).copy(alpha = 0.55f),
    divider                = Color(0xFF1C1C1E).copy(alpha = 0.08f),
    controlOverlay         = Color.White.copy(alpha = 0.97f),
    onControlOverlay       = Color(0xFF1C1C1E),
    fingerCircle           = Color.White.copy(alpha = 0.45f),
    keyBackground          = Color(0xFFE5E5EA),
    keyPressed             = Color(0xFFCECED3),
    keyModifierActive      = Color(0xFFD1D1D6),
    touchpadBackground     = Color(0xFFE5E5EA),
    touchpadIndicator      = Color(0xFF1C1C1E),
    pickerBackground       = Color(0xFFFFFFFF),
    accentBorder           = Color(0xFF1C1C1E).copy(alpha = 0.2f),
    accent                 = DEFAULT_DARK_LIGHT_ACCENT,
    onAccent               = Color.White,
    pillIdleColor          = Color(0xFF1C1C1E).copy(alpha = 0.4f),
    controlIndicatorActive = Color(0xFF1C1C1E),
    navPillBody            = DEFAULT_DARK_LIGHT_ACCENT,
    buttonBody             = DEFAULT_DARK_LIGHT_ACCENT,
    controlOverlayBorder   = Color(0xFF1C1C1E).copy(alpha = 0.12f),
    navPillBorder          = Color(0xFF1C1C1E).copy(alpha = 0.12f),
    mirrorPillBorder       = Color.Transparent,
    buttonIconTint         = Color.White,
    error                  = Color(0xFFB00020),
    onError                = Color.White,
    actionColorGamepad     = Color(0xFFFF9800),
    actionColorSystem      = Color(0xFF2196F3),
    macroPadSurface        = Color(0xFF1C1C1E),
    macroPadOnSurface      = Color.White,
    macroPadAccentBorder   = Color.White.copy(alpha = 0.3f),
    sectionHeaderColor     = DEFAULT_DARK_LIGHT_ACCENT,
    settingsSeparator      = Color(0xFF1C1C1E).copy(alpha = 0.10f),
)

// ─── Cyberpunk palette ────────────────────────────────────────────────────────
// Colours derived from the Cyberpunk 2077 main menu:
//   Background → dark blood red     ~0xFF160709
//   Menu text  → vivid red          ~0xFFED2224
//   Selection  → cyan               ~0xFF00CCFF
private val CP_ACCENT          = Color(0xFF8CF4FF)   // cyan — primary interactive / accent
private val CP_BG              = Color(0xFF160709)   // dark blood-red background
private val CP_SURFACE         = Color(0xFF220C0F)   // slightly lighter surface
private val CP_SURFACE2        = Color(0xFF2E1115)   // elevated / dragged surface
private val CP_TEXT            = Color(0xFFB41B1D)   // vivid red text
private val CP_DARK_RED        = Color(0xFFA00000)   // dark red for overlay and button text
private val CP_SECTION_HEADER  = Color(0xFFEEEEEE)   // off-white for section headers and pill idle color

private val cyberpunkPalette = AppColors(
    appBackground          = CP_BG,
    surface                = CP_SURFACE,
    surfaceVariant         = CP_SURFACE2,
    onSurface              = CP_TEXT,
    onSurfaceSecondary     = CP_TEXT.copy(alpha = 0.55f),
    divider                = CP_TEXT.copy(alpha = 0.10f),
    controlOverlay         = CP_SURFACE,
    onControlOverlay       = CP_DARK_RED,
    fingerCircle           = Color.White.copy(alpha = 0.45f),
    keyBackground          = CP_SURFACE,
    keyPressed             = CP_SURFACE2,
    keyModifierActive      = CP_SURFACE2,
    touchpadBackground     = CP_BG,
    touchpadIndicator      = CP_ACCENT,
    pickerBackground       = CP_SURFACE,
    accentBorder           = CP_ACCENT.copy(alpha = 0.35f),
    accent                 = CP_ACCENT,
    onAccent               = CP_DARK_RED,
    pillIdleColor          = CP_SECTION_HEADER,
    controlIndicatorActive = CP_ACCENT,
    navPillBody            = CP_SURFACE,
    buttonBody             = CP_SURFACE,
    controlOverlayBorder   = CP_DARK_RED,
    navPillBorder          = CP_ACCENT,
    mirrorPillBorder       = Color.Transparent,
    buttonIconTint         = CP_ACCENT,
    error                  = CP_ACCENT,
    onError                = CP_DARK_RED,
    actionColorGamepad     = Color(0xFFFF9800),
    actionColorSystem      = CP_ACCENT,
    macroPadSurface        = CP_SURFACE,
    macroPadOnSurface      = CP_TEXT,
    macroPadAccentBorder   = CP_ACCENT.copy(alpha = 0.35f),
    sectionHeaderColor     = CP_SECTION_HEADER,
    settingsSeparator      = CP_SECTION_HEADER.copy(alpha = 0.12f),
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
        base.copy(accent = eff, navPillBody = eff, buttonBody = eff, sectionHeaderColor = eff)
    } else base
}

// ─── Material 3 ColorScheme bridge ────────────────────────────────────────────
//
// Maps AppColors tokens to the M3 ColorScheme so that all Material 3
// components (Switch, Slider, OutlinedTextField, AlertDialog, TextButton, …)
// automatically use the correct theme colors without per-call overrides.

fun colorSchemeFor(colors: AppColors, mode: ThemeMode): ColorScheme {
    val base = if (mode == ThemeMode.LIGHT) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary            = colors.accent,
        onPrimary          = colors.onAccent,
        primaryContainer   = colors.accent.copy(alpha = 0.2f),
        onPrimaryContainer = colors.onAccent,
        secondary          = colors.accent,
        onSecondary        = colors.onAccent,
        background         = colors.appBackground,
        onBackground       = colors.onSurface,
        surface            = colors.surface,
        onSurface          = colors.onSurface,
        surfaceVariant     = colors.surfaceVariant,
        onSurfaceVariant   = colors.onSurfaceSecondary,
        error              = colors.error,
        onError            = colors.onError,
        outline            = colors.divider.copy(alpha = 0.4f),
        outlineVariant     = colors.divider,
    )
}

// ─── Typography ───────────────────────────────────────────────────────────────
//
// Semantic text styles used throughout the app.  Composables reference
// MaterialTheme.typography.bodyMedium etc. — never inline fontSize = XX.sp.
//
// Mapping:
//   titleLarge   18sp SemiBold — dialog titles
//   titleMedium  16sp SemiBold — section titles, ambient overlay titles
//   titleSmall   14sp Medium   — subsection titles
//   bodyLarge    15sp Normal   — macro names, list items
//   bodyMedium   14sp Normal   — standard row labels (most common)
//   bodySmall    12sp Normal   — secondary descriptions, hints
//   labelLarge   14sp Medium   — button labels
//   labelMedium  13sp Medium   — dialog subtitles, key labels
//   labelSmall   11sp Normal   — category headers (add letterSpacing 1.sp), axis labels

val megingiardTypography = Typography(
    titleLarge   = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium  = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium  = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
)

// ─── Dimension tokens ─────────────────────────────────────────────────────────
//
// All spacing, corner-radius, elevation, and icon-size values are expressed
// through these tokens.  Composables reference LocalAppDimens.current.paddingLarge
// etc. — never hardcode 16.dp inline.

@Immutable
data class AppDimens(
    // Padding / spacing
    val paddingXSmall: Dp  = 4.dp,
    val paddingSmall: Dp   = 8.dp,
    val paddingMedium: Dp  = 12.dp,
    val paddingLarge: Dp   = 16.dp,
    val paddingXLarge: Dp  = 20.dp,
    val paddingXXLarge: Dp = 24.dp,
    // Corner radii
    val cornerSmall: Dp    = 8.dp,
    val cornerMedium: Dp   = 12.dp,
    val cornerLarge: Dp    = 16.dp,
    val cornerXLarge: Dp   = 20.dp,
    val cornerFull: Dp     = 50.dp,
    // Elevation / shadow
    val elevationLow: Dp   = 2.dp,
    val elevationMedium: Dp = 4.dp,
    val elevationHigh: Dp  = 8.dp,
    // Icon sizes
    val iconSizeSmall: Dp  = 16.dp,
    val iconSizeMedium: Dp = 20.dp,
    val iconSizeLarge: Dp  = 24.dp,
    // Divider
    val dividerThickness: Dp = 1.dp,
)

// ─── CompositionLocals ────────────────────────────────────────────────────────

val LocalAppColors = compositionLocalOf<AppColors> { darkPalette }
val LocalAppDimens = compositionLocalOf { AppDimens() }
