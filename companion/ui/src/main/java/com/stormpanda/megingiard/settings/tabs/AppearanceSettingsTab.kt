package com.stormpanda.megingiard.settings.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.ColorWheelSubPageContent
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.settings.displayNameResId
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadColorPaletteCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem

val GS_ACCENT_PALETTE_PRESETS =
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

@Composable
fun AppearanceSettingsTab(
    themeMode: ThemeMode,
    accentColorArgb: Int,
    customAccentColorArgb: Int,
    overlayAtBottom: Boolean,
    overlayFadeOut: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (Int) -> Unit,
    onOpenCustomAccent: () -> Unit,
    onOverlayAtBottomChange: (Boolean) -> Unit,
    onOverlayFadeOutChange: (Boolean) -> Unit,
) {
    val accentColor = Color(accentColorArgb)
    val customAccentColor = Color(customAccentColorArgb)

    GamepadChoiceCard(
        title = stringResource(R.string.settings_theme),
        description = stringResource(R.string.help_settings_theme_desc),
        selectedText = stringResource(themeMode.displayNameResId()),
        icon = Icons.Rounded.Palette,
        onPrevious = { onThemeModeChange(ThemeMode.entries.cycle(themeMode, BumperDirection.PREV)) },
        onNext = { onThemeModeChange(ThemeMode.entries.cycle(themeMode, BumperDirection.NEXT)) },
        modifier = Modifier.firstDeckItem(),
    )

    if (themeMode.supportsCustomAccent) {
        val isCustomAccent =
            accentColorArgb == customAccentColorArgb && accentColor !in GS_ACCENT_PALETTE_PRESETS

        GamepadColorPaletteCard(
            title = stringResource(R.string.settings_accent_color),
            description = stringResource(R.string.settings_accent_color_desc),
            icon = Icons.Rounded.FormatColorFill,
            paletteColors = GS_ACCENT_PALETTE_PRESETS,
            selectedColor = accentColor,
            onColorSelected = { onAccentColorChange(it.toArgb()) },
        )

        GamepadActionCard(
            title = stringResource(R.string.settings_accent_custom_title),
            description = stringResource(R.string.settings_accent_custom_desc),
            icon = Icons.Rounded.Colorize,
            actionLeadingContent = {
                GamepadColorSwatch(
                    color = customAccentColor,
                    isSelected = isCustomAccent,
                )
            },
            onClick = onOpenCustomAccent,
        )
    }

    GamepadToggleCard(
        title = stringResource(R.string.settings_overlay_position),
        description = stringResource(R.string.help_settings_overlay_position_desc),
        checked = overlayAtBottom,
        icon = Icons.Rounded.VerticalAlignBottom,
        onCheckedChange = onOverlayAtBottomChange,
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_overlay_fade_out),
        description = stringResource(R.string.settings_overlay_fade_out_desc),
        checked = overlayFadeOut,
        icon = Icons.Rounded.Animation,
        onCheckedChange = onOverlayFadeOutChange,
    )
}

@Composable
fun CustomAccentSubPage(
    initialColor: Color,
    onSaveColor: (Color) -> Unit,
) {
    ColorWheelSubPageContent(
        initialColor = initialColor,
        showAlphaSlider = false,
        onSaveColor = onSaveColor,
    )
}
