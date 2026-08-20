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
) {
    /** Subtle, non-accented border used for unfocused cards, pills, text fields, and chips. */
    val subduedBorder: Color
        get() = onSurface.copy(alpha = 0.15f)
}

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
        controlOverlayBorder = DEFAULT_DARK_LIGHT_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = Color.Transparent,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = Color.White,
        error = Color(0xFFCF6679),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = Color(0xFF2196F3),
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
        controlOverlayBorder = DEFAULT_DARK_LIGHT_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = Color.Transparent,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = DARK_OLED_TEXT,
        error = Color(0xFFCF6679),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = Color(0xFF2196F3),
        macroPadOnSurface = DARK_OLED_TEXT,
        macroPadAccentBorder = DARK_OLED_TEXT.copy(alpha = 0.3f),
        sectionHeaderColor = DEFAULT_DARK_LIGHT_ACCENT,
        settingsSeparator = DARK_OLED_TEXT.copy(alpha = 0.12f),
    )

private val NORSE_ACCENT = Color(0xFFE5B842) // Runic Gold / Amber
private val NORSE_QUICK_MENU_BG = Color(0xFF081C12) // Perfectly tuned deep Norse emerald green for Quick Menu overlay
private val NORSE_BG = Color(0xFF040C08) // Deepest dark forest background for full-screen menus
private val NORSE_SURFACE = Color(0xFF06140C) // Solid panel/menu surface matching Quick Menu interior
private val NORSE_SURFACE2 = Color(0xFF0B2015) // Elevated card/row surface
private val NORSE_KEY_PRESSED = Color(0xFF102B1D) // Key pressed surface
private val NORSE_KEY_MODIFIER = Color(0xFF143423) // Key modifier active
private val NORSE_TEXT = Color(0xFFE2EBE5) // Soft parchment silver text
private val NORSE_TEXT_SECONDARY = Color(0xFF7BA68C) // Muted emerald sage secondary text
private val NORSE_QM_BAR_IDLE = Color.White.copy(alpha = 0.4f)

private val megingiardPalette =
    AppColors(
        appBackground = NORSE_BG,
        surface = NORSE_SURFACE,
        surfaceVariant = NORSE_SURFACE2,
        onSurface = NORSE_TEXT,
        onSurfaceSecondary = NORSE_TEXT_SECONDARY,
        divider = NORSE_TEXT.copy(alpha = 0.10f),
        controlOverlay = NORSE_QUICK_MENU_BG.copy(alpha = 0.95f),
        onControlOverlay = NORSE_TEXT,
        fingerCircle = NORSE_TEXT.copy(alpha = 0.45f),
        keyBackground = NORSE_SURFACE,
        keyPressed = NORSE_KEY_PRESSED,
        keyModifierActive = NORSE_KEY_MODIFIER,
        keyboardBackground = NORSE_BG,
        touchpadBackground = NORSE_BG,
        touchpadIndicator = NORSE_ACCENT,
        pickerBackground = NORSE_SURFACE,
        accentBorder = NORSE_ACCENT.copy(alpha = 0.4f),
        accent = NORSE_ACCENT,
        onAccent = NORSE_SURFACE,
        quickMenuBarIdleColor = NORSE_QM_BAR_IDLE,
        controlIndicatorActive = NORSE_ACCENT,
        navQuickMenuBody = NORSE_SURFACE,
        buttonBody = NORSE_SURFACE,
        controlOverlayBorder = NORSE_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = NORSE_ACCENT,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = NORSE_ACCENT,
        error = Color(0xFFD94336),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = NORSE_ACCENT,
        macroPadOnSurface = NORSE_TEXT,
        macroPadAccentBorder = NORSE_ACCENT.copy(alpha = 0.4f),
        sectionHeaderColor = NORSE_ACCENT,
        settingsSeparator = NORSE_TEXT.copy(alpha = 0.12f),
    )

// ─── Mjölnir Steel palette ──────────────────────────────────────────────────
private val MJOLNIR_ACCENT = Color(0xFF00E5FF) // Electric Lightning Cyan
private val MJOLNIR_BG = Color(0xFF101418) // Dark metallic slate
private val MJOLNIR_SURFACE = Color(0xFF161E26) // Brushed titanium surface
private val MJOLNIR_SURFACE2 = Color(0xFF1F2B36) // Elevated steel surface
private val MJOLNIR_TEXT = Color(0xFFE2EBF2) // Icy white text
private val MJOLNIR_TEXT_SEC = Color(0xFF8FA3B5) // Cold steel gray secondary text

private val mjolnirPalette =
    AppColors(
        appBackground = MJOLNIR_BG,
        surface = MJOLNIR_SURFACE,
        surfaceVariant = MJOLNIR_SURFACE2,
        onSurface = MJOLNIR_TEXT,
        onSurfaceSecondary = MJOLNIR_TEXT_SEC,
        divider = MJOLNIR_TEXT.copy(alpha = 0.10f),
        controlOverlay = MJOLNIR_SURFACE.copy(alpha = 0.95f),
        onControlOverlay = MJOLNIR_TEXT,
        fingerCircle = MJOLNIR_TEXT.copy(alpha = 0.45f),
        keyBackground = MJOLNIR_SURFACE,
        keyPressed = MJOLNIR_SURFACE2,
        keyModifierActive = Color(0xFF1F3547),
        keyboardBackground = MJOLNIR_BG,
        touchpadBackground = MJOLNIR_BG,
        touchpadIndicator = MJOLNIR_ACCENT,
        pickerBackground = MJOLNIR_SURFACE,
        accentBorder = MJOLNIR_ACCENT.copy(alpha = 0.4f),
        accent = MJOLNIR_ACCENT,
        onAccent = Color(0xFF101418),
        quickMenuBarIdleColor = Color.White.copy(alpha = 0.4f),
        controlIndicatorActive = MJOLNIR_ACCENT,
        navQuickMenuBody = MJOLNIR_SURFACE,
        buttonBody = MJOLNIR_SURFACE,
        controlOverlayBorder = MJOLNIR_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = MJOLNIR_ACCENT,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = MJOLNIR_ACCENT,
        error = Color(0xFFE53935),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = MJOLNIR_ACCENT,
        macroPadOnSurface = MJOLNIR_TEXT,
        macroPadAccentBorder = MJOLNIR_ACCENT.copy(alpha = 0.4f),
        sectionHeaderColor = MJOLNIR_ACCENT,
        settingsSeparator = MJOLNIR_TEXT.copy(alpha = 0.12f),
    )

// ─── Valhalla Sunset palette ─────────────────────────────────────────────────
private val VALHALLA_ACCENT = Color(0xFFFFA726) // Glowing Bronze Amber
private val VALHALLA_BG = Color(0xFF140E0A) // Obsidian twilight background
private val VALHALLA_SURFACE = Color(0xFF1E1610) // Warm mahogany surface
private val VALHALLA_SURFACE2 = Color(0xFF2B2018) // Elevated mahogany surface
private val VALHALLA_TEXT = Color(0xFFF4E8D1) // Golden parchment text
private val VALHALLA_TEXT_SEC = Color(0xFFBCAAA4) // Warm twilight secondary text

private val valhallaPalette =
    AppColors(
        appBackground = VALHALLA_BG,
        surface = VALHALLA_SURFACE,
        surfaceVariant = VALHALLA_SURFACE2,
        onSurface = VALHALLA_TEXT,
        onSurfaceSecondary = VALHALLA_TEXT_SEC,
        divider = VALHALLA_TEXT.copy(alpha = 0.10f),
        controlOverlay = VALHALLA_SURFACE.copy(alpha = 0.95f),
        onControlOverlay = VALHALLA_TEXT,
        fingerCircle = VALHALLA_TEXT.copy(alpha = 0.45f),
        keyBackground = VALHALLA_SURFACE,
        keyPressed = VALHALLA_SURFACE2,
        keyModifierActive = Color(0xFF3B291A),
        keyboardBackground = VALHALLA_BG,
        touchpadBackground = VALHALLA_BG,
        touchpadIndicator = VALHALLA_ACCENT,
        pickerBackground = VALHALLA_SURFACE,
        accentBorder = VALHALLA_ACCENT.copy(alpha = 0.4f),
        accent = VALHALLA_ACCENT,
        onAccent = Color(0xFF140E0A),
        quickMenuBarIdleColor = Color.White.copy(alpha = 0.4f),
        controlIndicatorActive = VALHALLA_ACCENT,
        navQuickMenuBody = VALHALLA_SURFACE,
        buttonBody = VALHALLA_SURFACE,
        controlOverlayBorder = VALHALLA_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = VALHALLA_ACCENT,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = VALHALLA_ACCENT,
        error = Color(0xFFD84315),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = VALHALLA_ACCENT,
        macroPadOnSurface = VALHALLA_TEXT,
        macroPadAccentBorder = VALHALLA_ACCENT.copy(alpha = 0.4f),
        sectionHeaderColor = VALHALLA_ACCENT,
        settingsSeparator = VALHALLA_TEXT.copy(alpha = 0.12f),
    )

// ─── Aurora Borealis palette ─────────────────────────────────────────────────
private val AURORA_ACCENT = Color(0xFF00F5D4) // Glowing Aurora Teal
private val AURORA_BG = Color(0xFF0A0A14) // Cosmos midnight indigo background
private val AURORA_SURFACE = Color(0xFF121222) // Dark violet-indigo surface
private val AURORA_SURFACE2 = Color(0xFF1B1B32) // Elevated violet surface
private val AURORA_TEXT = Color(0xFFE6E6FA) // Starry lavender text
private val AURORA_TEXT_SEC = Color(0xFF9FA8DA) // Muted cosmic blue secondary text

private val auroraPalette =
    AppColors(
        appBackground = AURORA_BG,
        surface = AURORA_SURFACE,
        surfaceVariant = AURORA_SURFACE2,
        onSurface = AURORA_TEXT,
        onSurfaceSecondary = AURORA_TEXT_SEC,
        divider = AURORA_TEXT.copy(alpha = 0.10f),
        controlOverlay = AURORA_SURFACE.copy(alpha = 0.95f),
        onControlOverlay = AURORA_TEXT,
        fingerCircle = AURORA_TEXT.copy(alpha = 0.45f),
        keyBackground = AURORA_SURFACE,
        keyPressed = AURORA_SURFACE2,
        keyModifierActive = Color(0xFF26264A),
        keyboardBackground = AURORA_BG,
        touchpadBackground = AURORA_BG,
        touchpadIndicator = AURORA_ACCENT,
        pickerBackground = AURORA_SURFACE,
        accentBorder = AURORA_ACCENT.copy(alpha = 0.4f),
        accent = AURORA_ACCENT,
        onAccent = Color(0xFF0A0A14),
        quickMenuBarIdleColor = Color.White.copy(alpha = 0.4f),
        controlIndicatorActive = AURORA_ACCENT,
        navQuickMenuBody = AURORA_SURFACE,
        buttonBody = AURORA_SURFACE,
        controlOverlayBorder = AURORA_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = AURORA_ACCENT,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = AURORA_ACCENT,
        error = Color(0xFFFF5252),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = AURORA_ACCENT,
        macroPadOnSurface = AURORA_TEXT,
        macroPadAccentBorder = AURORA_ACCENT.copy(alpha = 0.4f),
        sectionHeaderColor = AURORA_ACCENT,
        settingsSeparator = AURORA_TEXT.copy(alpha = 0.12f),
    )

// ─── Retro Phosphor palette ──────────────────────────────────────────────────
private val RETRO_ACCENT = Color(0xFF8BAC0F) // Game Boy Phosphor Mint
private val RETRO_BG = Color(0xFF141712) // Dark dot-matrix olive background
private val RETRO_SURFACE = Color(0xFF1C2219) // Deep dot-matrix green surface
private val RETRO_SURFACE2 = Color(0xFF263022) // Elevated dot-matrix surface
private val RETRO_TEXT = Color(0xFFC0D890) // Phosphor mint text
private val RETRO_TEXT_SEC = Color(0xFF708850) // Muted phosphor green secondary text

private val retroPhosphorPalette =
    AppColors(
        appBackground = RETRO_BG,
        surface = RETRO_SURFACE,
        surfaceVariant = RETRO_SURFACE2,
        onSurface = RETRO_TEXT,
        onSurfaceSecondary = RETRO_TEXT_SEC,
        divider = RETRO_TEXT.copy(alpha = 0.10f),
        controlOverlay = RETRO_SURFACE.copy(alpha = 0.95f),
        onControlOverlay = RETRO_TEXT,
        fingerCircle = RETRO_TEXT.copy(alpha = 0.45f),
        keyBackground = RETRO_SURFACE,
        keyPressed = RETRO_SURFACE2,
        keyModifierActive = Color(0xFF32402C),
        keyboardBackground = RETRO_BG,
        touchpadBackground = RETRO_BG,
        touchpadIndicator = RETRO_ACCENT,
        pickerBackground = RETRO_SURFACE,
        accentBorder = RETRO_ACCENT.copy(alpha = 0.4f),
        accent = RETRO_ACCENT,
        onAccent = Color(0xFF141712),
        quickMenuBarIdleColor = Color.White.copy(alpha = 0.4f),
        controlIndicatorActive = RETRO_ACCENT,
        navQuickMenuBody = RETRO_SURFACE,
        buttonBody = RETRO_SURFACE,
        controlOverlayBorder = RETRO_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = RETRO_ACCENT,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = RETRO_ACCENT,
        error = Color(0xFFE57373),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = RETRO_ACCENT,
        macroPadOnSurface = RETRO_TEXT,
        macroPadAccentBorder = RETRO_ACCENT.copy(alpha = 0.4f),
        sectionHeaderColor = RETRO_ACCENT,
        settingsSeparator = RETRO_TEXT.copy(alpha = 0.12f),
    )

// ─── Royal Asgard palette ────────────────────────────────────────────────────
private val ROYAL_ACCENT = Color(0xFFFFD700) // Polished Royal Gold
private val ROYAL_BG = Color(0xFF000000) // Pitch black background
private val ROYAL_SURFACE = Color(0xFF12110E) // Charcoal gold-tinted surface
private val ROYAL_SURFACE2 = Color(0xFF1C1A16) // Elevated charcoal gold surface
private val ROYAL_TEXT = Color(0xFFF7F3E9) // Warm ivory text
private val ROYAL_TEXT_SEC = Color(0xFFC5BCA8) // Muted champagne secondary text

private val royalAsgardPalette =
    AppColors(
        appBackground = ROYAL_BG,
        surface = ROYAL_SURFACE,
        surfaceVariant = ROYAL_SURFACE2,
        onSurface = ROYAL_TEXT,
        onSurfaceSecondary = ROYAL_TEXT_SEC,
        divider = ROYAL_TEXT.copy(alpha = 0.10f),
        controlOverlay = ROYAL_SURFACE.copy(alpha = 0.95f),
        onControlOverlay = ROYAL_TEXT,
        fingerCircle = ROYAL_TEXT.copy(alpha = 0.45f),
        keyBackground = ROYAL_SURFACE,
        keyPressed = ROYAL_SURFACE2,
        keyModifierActive = Color(0xFF2D2A22),
        keyboardBackground = ROYAL_BG,
        touchpadBackground = ROYAL_BG,
        touchpadIndicator = ROYAL_ACCENT,
        pickerBackground = ROYAL_SURFACE,
        accentBorder = ROYAL_ACCENT.copy(alpha = 0.4f),
        accent = ROYAL_ACCENT,
        onAccent = Color(0xFF000000),
        quickMenuBarIdleColor = Color.White.copy(alpha = 0.4f),
        controlIndicatorActive = ROYAL_ACCENT,
        navQuickMenuBody = ROYAL_SURFACE,
        buttonBody = ROYAL_SURFACE,
        controlOverlayBorder = ROYAL_ACCENT.copy(alpha = 0.3f),
        navQuickMenuBorder = ROYAL_ACCENT,
        mirrorQuickMenuBorder = Color.Transparent,
        buttonIconTint = ROYAL_ACCENT,
        error = Color(0xFFD32F2F),
        onError = Color.White,
        actionColorGamepad = Color(0xFFFF9800),
        actionColorSystem = ROYAL_ACCENT,
        macroPadOnSurface = ROYAL_TEXT,
        macroPadAccentBorder = ROYAL_ACCENT.copy(alpha = 0.4f),
        sectionHeaderColor = ROYAL_ACCENT,
        settingsSeparator = ROYAL_TEXT.copy(alpha = 0.12f),
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
