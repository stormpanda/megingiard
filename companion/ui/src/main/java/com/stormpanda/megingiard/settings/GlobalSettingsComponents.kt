package com.stormpanda.megingiard.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.stormpanda.megingiard.R

val SettingsCategory.titleResId: Int
    get() =
        when (this) {
            SettingsCategory.GENERAL -> R.string.settings_jump_general
            SettingsCategory.INPUT -> R.string.settings_jump_input
            SettingsCategory.APPEARANCE -> R.string.settings_jump_appearance
            SettingsCategory.CONFIGURATION -> R.string.settings_jump_config
            SettingsCategory.SCRAPING -> R.string.settings_jump_scraping
            SettingsCategory.UPDATES -> R.string.settings_jump_updates
            SettingsCategory.DIAGNOSTICS -> R.string.settings_jump_diagnostics
        }

val SettingsCategory.icon: ImageVector
    get() =
        when (this) {
            SettingsCategory.GENERAL -> Icons.Rounded.Tune
            SettingsCategory.INPUT -> Icons.Rounded.Gamepad
            SettingsCategory.APPEARANCE -> Icons.Rounded.Palette
            SettingsCategory.CONFIGURATION -> Icons.Rounded.Build
            SettingsCategory.SCRAPING -> Icons.Rounded.ImageSearch
            SettingsCategory.UPDATES -> Icons.Rounded.SystemUpdate
            SettingsCategory.DIAGNOSTICS -> Icons.Rounded.HealthAndSafety
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
