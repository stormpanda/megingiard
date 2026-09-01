package com.stormpanda.megingiard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
    /** Primary interactive / accent colour (user-overridable accent). */
    val accent: Color,
    /** Text / icons on accent / highlighted buttons. */
    val onAccent: Color = surface,
    /** Subtle divider lines. */
    val divider: Color = onSurface.copy(alpha = 0.10f),
    /** Semi-transparent floating control Quick Menu (mirror, quick menu). */
    val controlOverlay: Color = surface.copy(alpha = 0.95f),
    /** Text / icons on the control overlay. */
    val onControlOverlay: Color = onSurface,
    /** Finger-circle indicator in overlay. */
    val fingerCircle: Color = onSurface.copy(alpha = 0.45f),
    /** Keyboard key background (normal). */
    val keyBackground: Color = surface,
    /** Keyboard key background when pressed. */
    val keyPressed: Color = surfaceVariant,
    /** Keyboard modifier key when active / sticky. */
    val keyModifierActive: Color = surfaceVariant,
    /** Keyboard container background. */
    val keyboardBackground: Color = appBackground,
    /** Touchpad surface. */
    val touchpadBackground: Color = appBackground,
    /** Touchpad indicator dots / borders. */
    val touchpadIndicator: Color = accent,
    /** Color-picker dialog background. */
    val pickerBackground: Color = surface,
    /** Accent color swatch border. */
    val accentBorder: Color = accent.copy(alpha = 0.4f),
    /** Always-visible pull-tab quick menu bar colour (already includes the desired alpha). */
    val quickMenuBarIdleColor: Color = Color.White.copy(alpha = 0.4f),
    /** Active mode indicator dot inside the navigation bar. */
    val controlIndicatorActive: Color = accent,
    /** Background of the navigation bar (overrides accent for custom-accent themes). */
    val navQuickMenuBody: Color = surface,
    /** Background of mirror control buttons. */
    val buttonBody: Color = surface,
    /** Border/outline of the quick menu control overlay container. */
    val controlOverlayBorder: Color = accent.copy(alpha = 0.3f),
    /** Border/outline of the navigation bar. */
    val navQuickMenuBorder: Color = accent,
    /** Border/outline of the mirror control bar. */
    val mirrorQuickMenuBorder: Color = Color.Transparent,
    /** Icon tint on mirror control buttons. */
    val buttonIconTint: Color = accent,
    /** Destructive / error action color (delete buttons, confirm-destructive text). */
    val error: Color = Color(0xFFCF6679),
    /** Content (text/icons) on error-colored surfaces. */
    val onError: Color = Color.White,
    /** Action-type badge color for gamepad / joystick macro steps. */
    val actionColorGamepad: Color = Color(0xFFFF9800),
    /** Action-type badge color for system / d-pad macro steps. */
    val actionColorSystem: Color = accent,
    /** MacroPad button-placement text/icons. */
    val macroPadOnSurface: Color = onSurface,
    /** MacroPad button-placement border. */
    val macroPadAccentBorder: Color = accent.copy(alpha = 0.4f),
    /**
     * Color used for section-header label text (uppercase strip above setting groups,
     * editor section dividers, etc.).  Equals [accent] for themes that support a custom
     * accent; fixed per-palette for themes like Cyberpunk that use a distinct header tint.
     */
    val sectionHeaderColor: Color = accent,
    /** Thin divider between transparent settings rows drawn on the default screen/dialog background. */
    val settingsSeparator: Color = onSurface.copy(alpha = 0.12f),
) {
    /** Subtle, non-accented border used for unfocused cards, pills, text fields, and chips. */
    val subduedBorder: Color
        get() = onSurface.copy(alpha = 0.15f)
}

// ─── Palettes ─────────────────────────────────────────────────────────────────

// Default accent for Dark/Light — overridden at runtime by SettingsManager.accentColor.
private val DEFAULT_DARK_LIGHT_ACCENT = Color(0xFFCC0000)

private val darkPalette =
    AppColors(
        appBackground = Color(0xFF121212),
        surface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurface = Color.White,
        onSurfaceSecondary = Color.White.copy(alpha = 0.6f),
        divider = Color.White.copy(alpha = 0.08f),
        controlOverlay = Color.Black.copy(alpha = 0.8f),
        keyPressed = Color(0xFF48484A),
        keyModifierActive = Color(0xFF3A3A3C),
        keyboardBackground = Color(0xFF1D1F26),
        touchpadBackground = Color.Black,
        touchpadIndicator = Color.White,
        accentBorder = Color.White.copy(alpha = 0.3f),
        accent = DEFAULT_DARK_LIGHT_ACCENT,
        onAccent = Color.White,
        controlIndicatorActive = Color.White,
        navQuickMenuBody = DEFAULT_DARK_LIGHT_ACCENT,
        buttonBody = DEFAULT_DARK_LIGHT_ACCENT,
        navQuickMenuBorder = Color.Transparent,
        buttonIconTint = Color.White,
        actionColorSystem = Color(0xFF2196F3),
        macroPadAccentBorder = Color.White.copy(alpha = 0.3f),
        settingsSeparator = Color.White.copy(alpha = 0.10f),
    )

private val DARK_OLED_TEXT = Color(0xFFE3E3E8) // Soft off-white to reduce eye strain on pitch-black OLED displays

private val darkOledPalette =
    AppColors(
        appBackground = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF161618),
        onSurface = DARK_OLED_TEXT,
        onSurfaceSecondary = DARK_OLED_TEXT.copy(alpha = 0.6f),
        keyPressed = Color(0xFF323235),
        keyModifierActive = Color(0xFF242426),
        accentBorder = DARK_OLED_TEXT.copy(alpha = 0.3f),
        accent = DEFAULT_DARK_LIGHT_ACCENT,
        onAccent = Color.White,
        navQuickMenuBody = DEFAULT_DARK_LIGHT_ACCENT,
        buttonBody = DEFAULT_DARK_LIGHT_ACCENT,
        navQuickMenuBorder = Color.Transparent,
        buttonIconTint = DARK_OLED_TEXT,
        actionColorSystem = Color(0xFF2196F3),
        macroPadAccentBorder = DARK_OLED_TEXT.copy(alpha = 0.3f),
    )

// ─── Megingiard (Norse Forest) palette ─────────────────────────────────────────
private val megingiardPalette =
    AppColors(
        appBackground = Color(0xFF040C08), // Deepest dark forest background
        surface = Color(0xFF06140C), // Solid panel/menu surface
        surfaceVariant = Color(0xFF0B2015), // Elevated card/row surface
        onSurface = Color(0xFFE2EBE5), // Soft parchment silver text
        onSurfaceSecondary = Color(0xFF7BA68C), // Muted emerald sage secondary text
        controlOverlay = Color(0xFF081C12).copy(alpha = 0.95f), // Norse emerald green overlay
        keyPressed = Color(0xFF102B1D), // Key pressed surface
        keyModifierActive = Color(0xFF143423), // Key modifier active
        accent = Color(0xFFE5B842), // Runic Gold / Amber
        error = Color(0xFFD94336),
    )

// ─── Mjölnir Steel palette ──────────────────────────────────────────────────
private val mjolnirPalette =
    AppColors(
        appBackground = Color(0xFF101418), // Dark metallic slate
        surface = Color(0xFF161E26), // Brushed titanium surface
        surfaceVariant = Color(0xFF1F2B36), // Elevated steel surface
        onSurface = Color(0xFFE2EBF2), // Icy white text
        onSurfaceSecondary = Color(0xFF8FA3B5), // Cold steel gray secondary text
        keyModifierActive = Color(0xFF1F3547),
        accent = Color(0xFF00E5FF), // Electric Lightning Cyan
        onAccent = Color(0xFF101418),
        error = Color(0xFFE53935),
    )

// ─── Valhalla Sunset palette ─────────────────────────────────────────────────
private val valhallaPalette =
    AppColors(
        appBackground = Color(0xFF140E0A), // Obsidian twilight background
        surface = Color(0xFF1E1610), // Warm mahogany surface
        surfaceVariant = Color(0xFF2B2018), // Elevated mahogany surface
        onSurface = Color(0xFFF4E8D1), // Golden parchment text
        onSurfaceSecondary = Color(0xFFBCAAA4), // Warm twilight secondary text
        keyModifierActive = Color(0xFF3B291A),
        accent = Color(0xFFFFA726), // Glowing Bronze Amber
        onAccent = Color(0xFF140E0A),
        error = Color(0xFFD84315),
    )

// ─── Aurora Borealis palette ─────────────────────────────────────────────────
private val auroraPalette =
    AppColors(
        appBackground = Color(0xFF0A0A14), // Cosmos midnight indigo background
        surface = Color(0xFF121222), // Dark violet-indigo surface
        surfaceVariant = Color(0xFF1B1B32), // Elevated violet surface
        onSurface = Color(0xFFE6E6FA), // Starry lavender text
        onSurfaceSecondary = Color(0xFF9FA8DA), // Muted cosmic blue secondary text
        keyModifierActive = Color(0xFF26264A),
        accent = Color(0xFF00F5D4), // Glowing Aurora Teal
        onAccent = Color(0xFF0A0A14),
        error = Color(0xFFFF5252),
    )

// ─── Retro Phosphor palette ──────────────────────────────────────────────────
private val retroPhosphorPalette =
    AppColors(
        appBackground = Color(0xFF141712), // Dark dot-matrix olive background
        surface = Color(0xFF1C2219), // Deep dot-matrix green surface
        surfaceVariant = Color(0xFF263022), // Elevated dot-matrix surface
        onSurface = Color(0xFFC0D890), // Phosphor mint text
        onSurfaceSecondary = Color(0xFF708850), // Muted phosphor green secondary text
        keyModifierActive = Color(0xFF32402C),
        accent = Color(0xFF8BAC0F), // Game Boy Phosphor Mint
        onAccent = Color(0xFF141712),
        error = Color(0xFFE57373),
    )

// ─── Royal Asgard palette ────────────────────────────────────────────────────
private val royalAsgardPalette =
    AppColors(
        appBackground = Color.Black, // Pitch black background
        surface = Color(0xFF12110E), // Charcoal gold-tinted surface
        surfaceVariant = Color(0xFF1C1A16), // Elevated charcoal gold surface
        onSurface = Color(0xFFF7F3E9), // Warm ivory text
        onSurfaceSecondary = Color(0xFFC5BCA8), // Muted champagne secondary text
        keyModifierActive = Color(0xFF2D2A22),
        accent = Color(0xFFFFD700), // Polished Royal Gold
        onAccent = Color.Black,
        error = Color(0xFFD32F2F),
    )

// ─── Palette selector ─────────────────────────────────────────────────────────

/**
 * Returns the [AppColors] palette for [mode], optionally overriding the accent
 * token with a user-chosen colour. The override is only applied when
 * [ThemeMode.supportsCustomAccent] is true; fixed themes ignore it.
 */
fun paletteFor(
    mode: ThemeMode,
    userAccent: Color? = null,
): AppColors {
    val base =
        when (mode) {
            ThemeMode.DARK -> darkPalette
            ThemeMode.DARK_OLED -> darkOledPalette
            ThemeMode.MEGINGIARD -> megingiardPalette
            ThemeMode.MJOLNIR -> mjolnirPalette
            ThemeMode.VALHALLA -> valhallaPalette
            ThemeMode.AURORA -> auroraPalette
            ThemeMode.RETRO_PHOSPHOR -> retroPhosphorPalette
            ThemeMode.ROYAL_ASGARD -> royalAsgardPalette
        }
    return if (mode.supportsCustomAccent) {
        val eff = userAccent ?: base.accent
        base.copy(
            accent = eff,
            navQuickMenuBody = eff,
            buttonBody = eff,
            sectionHeaderColor = eff,
            controlOverlayBorder = eff.copy(alpha = 0.3f),
        )
    } else {
        base
    }
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

@Composable
fun appSwitchColors(colors: AppColors = LocalAppColors.current): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = colors.onAccent,
        checkedTrackColor = colors.accent,
        checkedBorderColor = colors.accent,
        uncheckedThumbColor = colors.onSurfaceSecondary,
        uncheckedTrackColor = colors.surfaceVariant,
        uncheckedBorderColor = colors.divider,
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
