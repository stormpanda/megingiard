package com.stormpanda.megingiard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
    /** Semi-transparent floating control Quick Menu (mirror, quick menu). */
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
    /** Keyboard container background. */
    val keyboardBackground: Color,
    /** Touchpad surface. */
    val touchpadBackground: Color,
    /** Touchpad indicator dots / borders. */
    val touchpadIndicator: Color,
    /** Color-picker dialog background. */
    val pickerBackground: Color,
    /** Accent color swatch border. */
    val accentBorder: Color,
    /** Primary interactive / accent colour (user-overridable accent). */
    val accent: Color,
    /** Text / icons on accent / highlighted buttons. */
    val onAccent: Color,
    /** Always-visible pull-tab quick menu bar colour (already includes the desired alpha). */
    val quickMenuBarIdleColor: Color,
    /** Active mode indicator dot inside the navigation bar. */
    val controlIndicatorActive: Color,
    /** Background of the navigation bar (overrides accent for custom-accent themes). */
    val navQuickMenuBody: Color,
    /** Background of mirror control buttons. */
    val buttonBody: Color,
    /** Border/outline of the quick menu control overlay container. */
    val controlOverlayBorder: Color,
    /** Border/outline of the navigation bar. */
    val navQuickMenuBorder: Color,
    /** Border/outline of the mirror control bar. */
    val mirrorQuickMenuBorder: Color,
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

private val DARK_QM_BAR_IDLE = Color.White.copy(alpha = 0.4f)

private val darkPalette =
    AppColors(
        appBackground = Color(0xFF121212),
        surface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurface = Color.White,
        onSurfaceSecondary = Color.White.copy(alpha = 0.6f),
        divider = Color.White.copy(alpha = 0.08f),
        controlOverlay = Color.Black.copy(alpha = 0.8f),
        onControlOverlay = Color.White,
        fingerCircle = Color.White.copy(alpha = 0.45f),
        keyBackground = Color(0xFF2C2C2E),
        keyPressed = Color(0xFF48484A),
        keyModifierActive = Color(0xFF3A3A3C),
        keyboardBackground = Color(0xFF1D1F26),
        touchpadBackground = Color.Black,
        touchpadIndicator = Color.White,
        pickerBackground = Color(0xFF1C1C1E),
        accentBorder = Color.White.copy(alpha = 0.3f),
        accent = DEFAULT_DARK_LIGHT_ACCENT,
        onAccent = Color.White,
        quickMenuBarIdleColor = DARK_QM_BAR_IDLE,
        controlIndicatorActive = Color.White,
        navQuickMenuBody = DEFAULT_DARK_LIGHT_ACCENT,
        buttonBody = DEFAULT_DARK_LIGHT_ACCENT,
        controlOverlayBorder = Color.Transparent,
        navQuickMenuBorder = Color.Transparent,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = Color.White,
        error = Color(0xFFCF6679),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = Color(0xFF2196F3),
        macroPadSurface = Color(0xFF1C1C1E),
        macroPadOnSurface = Color.White,
        macroPadAccentBorder = Color.White.copy(alpha = 0.3f),
        sectionHeaderColor = DEFAULT_DARK_LIGHT_ACCENT,
        settingsSeparator = Color.White.copy(alpha = 0.10f),
    )

private val DARK_OLED_QM_BAR_IDLE = Color.White.copy(alpha = 0.4f)
private val DARK_OLED_TEXT = Color(0xFFE3E3E8) // Soft off-white to reduce eye strain on pitch-black OLED displays

private val darkOledPalette =
    AppColors(
        appBackground = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF161618),
        onSurface = DARK_OLED_TEXT,
        onSurfaceSecondary = DARK_OLED_TEXT.copy(alpha = 0.6f),
        divider = DARK_OLED_TEXT.copy(alpha = 0.10f),
        controlOverlay = Color.Black.copy(alpha = 0.95f),
        onControlOverlay = DARK_OLED_TEXT,
        fingerCircle = DARK_OLED_TEXT.copy(alpha = 0.45f),
        keyBackground = Color(0xFF161618),
        keyPressed = Color(0xFF323235),
        keyModifierActive = Color(0xFF242426),
        keyboardBackground = Color.Black,
        touchpadBackground = Color.Black,
        touchpadIndicator = DARK_OLED_TEXT,
        pickerBackground = Color.Black,
        accentBorder = DARK_OLED_TEXT.copy(alpha = 0.3f),
        accent = DEFAULT_DARK_LIGHT_ACCENT,
        onAccent = Color.White,
        quickMenuBarIdleColor = DARK_OLED_QM_BAR_IDLE,
        controlIndicatorActive = DARK_OLED_TEXT,
        navQuickMenuBody = DEFAULT_DARK_LIGHT_ACCENT,
        buttonBody = DEFAULT_DARK_LIGHT_ACCENT,
        controlOverlayBorder = Color.Transparent,
        navQuickMenuBorder = Color.Transparent,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = DARK_OLED_TEXT,
        error = Color(0xFFCF6679),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = Color(0xFF2196F3),
        macroPadSurface = Color.Black,
        macroPadOnSurface = DARK_OLED_TEXT,
        macroPadAccentBorder = DARK_OLED_TEXT.copy(alpha = 0.3f),
        sectionHeaderColor = DEFAULT_DARK_LIGHT_ACCENT,
        settingsSeparator = DARK_OLED_TEXT.copy(alpha = 0.12f),
    )

// ─── Palette selector ─────────────────────────────────────────────────────────

/**
 * Returns the [AppColors] palette for [mode], applying the user-chosen accent colour.
 */
fun paletteFor(
    mode: ThemeMode,
    userAccent: Color? = null,
): AppColors {
    val base =
        when (mode) {
            ThemeMode.DARK -> darkPalette
            ThemeMode.DARK_OLED -> darkOledPalette
        }
    val eff = userAccent ?: base.accent
    return base.copy(accent = eff, navQuickMenuBody = eff, buttonBody = eff, sectionHeaderColor = eff)
}

// ─── Material 3 ColorScheme bridge ────────────────────────────────────────────
//
// Maps AppColors tokens to the M3 ColorScheme so that all Material 3
// components (Switch, Slider, OutlinedTextField, AlertDialog, TextButton, …)
// automatically use the correct theme colors without per-call overrides.

fun colorSchemeFor(
    colors: AppColors,
    mode: ThemeMode,
): ColorScheme =
    darkColorScheme().copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accent.copy(alpha = 0.2f),
        onPrimaryContainer = colors.onAccent,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        background = colors.appBackground,
        onBackground = colors.onSurface,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.onSurfaceSecondary,
        error = colors.error,
        onError = colors.onError,
        outline = colors.divider.copy(alpha = 0.4f),
        outlineVariant = colors.divider,
    )

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

val megingiardTypography =
    Typography(
        titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
    )

// ─── Dimension tokens ─────────────────────────────────────────────────────────
//
// All spacing, corner-radius, elevation, and icon-size values are expressed
// through these tokens.  Composables reference LocalAppDimens.current.paddingLarge
// etc. — never hardcode 16.dp inline.

@Immutable
data class AppDimens(
    // Padding / spacing
    val paddingXSmall: Dp = 4.dp,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 12.dp,
    val paddingLarge: Dp = 16.dp,
    val paddingXLarge: Dp = 20.dp,
    val paddingXXLarge: Dp = 24.dp,
    // Corner radii
    val cornerSmall: Dp = 8.dp,
    val cornerMedium: Dp = 12.dp,
    val cornerLarge: Dp = 16.dp,
    val cornerXLarge: Dp = 20.dp,
    val cornerFull: Dp = 50.dp,
    // Elevation / shadow
    val elevationLow: Dp = 2.dp,
    val elevationMedium: Dp = 4.dp,
    val elevationHigh: Dp = 8.dp,
    // Icon sizes
    val iconSizeSmall: Dp = 16.dp,
    val iconSizeMedium: Dp = 20.dp,
    val iconSizeLarge: Dp = 24.dp,
    // Divider
    val dividerThickness: Dp = 1.dp,
)

// ─── CompositionLocals ────────────────────────────────────────────────────────

val LocalAppColors = compositionLocalOf<AppColors> { darkPalette }
val LocalAppDimens = compositionLocalOf { AppDimens() }
