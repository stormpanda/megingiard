package com.stormpanda.megingiard.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.stormpanda.megingiard.R

internal enum class SettingsCategory(
    @StringRes val titleResId: Int,
    val icon: ImageVector,
) {
    GENERAL(R.string.settings_jump_general, Icons.Rounded.Tune),
    INPUT(R.string.settings_jump_input, Icons.Rounded.Gamepad),
    APPEARANCE(R.string.settings_jump_appearance, Icons.Rounded.Palette),
    DATA(R.string.settings_jump_data, Icons.Rounded.Storage),
    CONFIGURATION(R.string.settings_jump_config, Icons.Rounded.Build),
    UPDATES(R.string.settings_jump_updates, Icons.Rounded.SystemUpdate),
    DIAGNOSTICS(R.string.settings_jump_diagnostics, Icons.Rounded.HealthAndSafety),
}

internal fun ThemeMode.displayNameResId(): Int =
    when (this) {
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.DARK_OLED -> R.string.theme_dark_oled
        ThemeMode.MEGINGIARD -> R.string.theme_megingiard
        ThemeMode.MJOLNIR -> R.string.theme_mjolnir
        ThemeMode.VALHALLA -> R.string.theme_valhalla
        ThemeMode.AURORA -> R.string.theme_aurora
        ThemeMode.RETRO_PHOSPHOR -> R.string.theme_retro_phosphor
        ThemeMode.ROYAL_ASGARD -> R.string.theme_royal_asgard
    }

internal fun AppLanguage.displayNameResId(): Int =
    when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.EN -> R.string.settings_language_en
        AppLanguage.DE -> R.string.settings_language_de
        AppLanguage.ZH_TW -> R.string.settings_language_zh_tw
    }
