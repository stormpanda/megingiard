package com.stormpanda.megingiard.settings

import com.stormpanda.megingiard.R

internal enum class SettingsSectionFilter {
    GENERAL,
    INPUT,
    APPEARANCE,
    DATA,
    CONFIGURATION,
    UPDATES,
    DIAGNOSTICS,
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
