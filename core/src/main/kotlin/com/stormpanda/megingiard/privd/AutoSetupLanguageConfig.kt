package com.stormpanda.megingiard.privd

import java.util.Locale

/**
 * String configuration for Android System Settings auto-setup navigation and text scanning.
 *
 * Provides locale-specific search terms, keywords, and labels needed to locate and interact
 * with Android system settings screens across different country locales.
 *
 * @property localeTag BCP 47 locale tag incorporating language and country (e.g. "de-DE", "en-US", "es-ES", "fr-FR").
 * @property languageCode Primary 2-letter ISO language code (e.g., "en", "de", "es", "fr").
 * @property buildNumberQueryAndKeyword Search query and UI node matching string for Build Number.
 * @property wirelessDebuggingQueryAndKeyword Search query and UI node matching string for Wireless Debugging.
 * @property usbDebuggingQueryAndKeyword Search query and UI node matching string for USB Debugging.
 * @property pairDeviceKeywords List of UI text keywords used to identify the "Pair device with pairing code" row.
 * @property developerOptionsKeywords Lowercased UI text keywords identifying the Developer options screen,
 *   used to tell it apart from the Wireless debugging sub-screen (both carry the wireless debugging label).
 *   Defaults to the titles of the previously supported locales, so a new locale whose title is not
 *   among them must supply its own — otherwise Auto Setup mistakes Developer options for the sub-screen.
 * @property allowButtonKeywords List of UI text keywords used to confirm network trust dialogs.
 */
data class AutoSetupLanguageConfig(
    val localeTag: String,
    val languageCode: String,
    val buildNumberQueryAndKeyword: String,
    val wirelessDebuggingQueryAndKeyword: String,
    val usbDebuggingQueryAndKeyword: String,
    val pairDeviceKeywords: List<String>,
    val developerOptionsKeywords: List<String>,
    val allowButtonKeywords: List<String>,
) {
    companion object {
        val GERMAN_DE =
            AutoSetupLanguageConfig(
                localeTag = "de-DE",
                languageCode = "de",
                buildNumberQueryAndKeyword = "Build-Nummer",
                wirelessDebuggingQueryAndKeyword = "Debugging über WLAN",
                usbDebuggingQueryAndKeyword = "USB-Debugging",
                pairDeviceKeywords =
                    listOf(
                        "gerät über einen kopplungscode koppeln",
                        "gerät über einen kopplungscode koppen",
                        "geräte-kopplungscode",
                        "kopplungscode koppeln",
                        "mit kopplungscode koppeln",
                        "wlan-kopplungscode",
                    ),
                developerOptionsKeywords =
                    listOf(
                        "entwickleroptionen",
                    ),
                allowButtonKeywords =
                    listOf(
                        "zulassen",
                        "ok",
                    ),
            )

        val GERMAN_AT = GERMAN_DE.copy(localeTag = "de-AT")
        val GERMAN_CH = GERMAN_DE.copy(localeTag = "de-CH")

        val GERMAN = GERMAN_DE

        val SPANISH_ES =
            AutoSetupLanguageConfig(
                localeTag = "es-ES",
                languageCode = "es",
                buildNumberQueryAndKeyword = "Número de compilación",
                wirelessDebuggingQueryAndKeyword = "Depuración inalámbrica",
                usbDebuggingQueryAndKeyword = "Depuración por USB",
                pairDeviceKeywords =
                    listOf(
                        "vincular dispositivo con un código de vinculación",
                        "vincular dispositivo con código de vinculación",
                        "código de vinculación",
                        "emparejar dispositivo con un código de sincronización",
                        "código de sincronización",
                    ),
                developerOptionsKeywords =
                    listOf(
                        "opciones de desarrollador",
                    ),
                allowButtonKeywords =
                    listOf(
                        "permitir",
                        "ok",
                    ),
            )

        val SPANISH_MX = SPANISH_ES.copy(localeTag = "es-MX")
        val SPANISH_US = SPANISH_ES.copy(localeTag = "es-US")

        val SPANISH = SPANISH_ES

        val FRENCH_FR =
            AutoSetupLanguageConfig(
                localeTag = "fr-FR",
                languageCode = "fr",
                buildNumberQueryAndKeyword = "Numéro de build",
                wirelessDebuggingQueryAndKeyword = "Débogage sans fil",
                usbDebuggingQueryAndKeyword = "Débogage USB",
                pairDeviceKeywords =
                    listOf(
                        "associer l'appareil avec un code d'association",
                        "associer le périphérique avec un code d'association",
                        "appairer l'appareil avec un code de synchronisation",
                        "code d'association",
                        "code de synchronisation",
                    ),
                developerOptionsKeywords =
                    listOf(
                        "options pour les développeurs",
                    ),
                allowButtonKeywords =
                    listOf(
                        "autoriser",
                        "ok",
                    ),
            )

        val FRENCH_CA = FRENCH_FR.copy(localeTag = "fr-CA")

        val FRENCH = FRENCH_FR

        val ENGLISH_US =
            AutoSetupLanguageConfig(
                localeTag = "en-US",
                languageCode = "en",
                buildNumberQueryAndKeyword = "Build number",
                wirelessDebuggingQueryAndKeyword = "Wireless debugging",
                usbDebuggingQueryAndKeyword = "USB debugging",
                pairDeviceKeywords =
                    listOf(
                        "pair device with pairing code",
                        "pair with pairing code",
                    ),
                developerOptionsKeywords =
                    listOf(
                        "developer options",
                    ),
                allowButtonKeywords =
                    listOf(
                        "allow",
                        "ok",
                    ),
            )

        val ENGLISH_GB = ENGLISH_US.copy(localeTag = "en-GB")
        val ENGLISH_CA = ENGLISH_US.copy(localeTag = "en-CA")
        val ENGLISH_AU = ENGLISH_US.copy(localeTag = "en-AU")

        val ENGLISH = ENGLISH_US

        val CHINESE_TW =
            AutoSetupLanguageConfig(
                localeTag = "zh-TW",
                languageCode = "zh",
                buildNumberQueryAndKeyword = "版本號碼",
                wirelessDebuggingQueryAndKeyword = "無線偵錯",
                usbDebuggingQueryAndKeyword = "USB 偵錯",
                pairDeviceKeywords =
                    listOf(
                        "使用配對碼配對裝置",
                        "配對碼配對",
                        "使用配對碼",
                        "裝置配對碼",
                        "配對碼",
                    ),
                developerOptionsKeywords =
                    listOf(
                        "開發人員選項",
                    ),
                allowButtonKeywords =
                    listOf(
                        "允許",
                        "確定",
                    ),
            )

        val CHINESE_HK = CHINESE_TW.copy(localeTag = "zh-HK")
        val CHINESE_MO = CHINESE_TW.copy(localeTag = "zh-MO")

        /**
         * Traditional Chinese only. Simplified Chinese (zh-CN, zh-SG) uses different
         * Settings wording ("版本号", "无线调试", …) and is deliberately not mapped here,
         * so there is no alias for a bare `CHINESE`.
         */
        val CHINESE_TRADITIONAL = CHINESE_TW

        private val TRADITIONAL_CHINESE_SUBTAGS = setOf("tw", "hk", "mo", "hant")

        private val CONFIGS_BY_TAG: Map<String, AutoSetupLanguageConfig> =
            listOf(
                GERMAN_DE,
                GERMAN_AT,
                GERMAN_CH,
                SPANISH_ES,
                SPANISH_MX,
                SPANISH_US,
                FRENCH_FR,
                FRENCH_CA,
                ENGLISH_US,
                ENGLISH_GB,
                ENGLISH_CA,
                ENGLISH_AU,
                CHINESE_TW,
                CHINESE_HK,
                CHINESE_MO,
            ).associateBy { it.localeTag.lowercase() }

        /**
         * Resolves a `zh` locale to the Traditional Chinese config only when the region or
         * script subtag says so. Simplified Chinese must return null rather than fall back
         * to Traditional keywords: the Settings entries it would search for do not exist on
         * a Simplified device, so Auto Setup would stall instead of reporting the language
         * as unsupported.
         */
        private fun traditionalChineseOrNull(subtags: List<String>): AutoSetupLanguageConfig? =
            if (subtags.any { it in TRADITIONAL_CHINESE_SUBTAGS }) CHINESE_TW else null

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for given [Locale], or null if unsupported.
         */
        fun fromLocaleOrNull(locale: Locale): AutoSetupLanguageConfig? {
            val fullTag = locale.toLanguageTag().lowercase().replace("_", "-")
            val matchedByTag = CONFIGS_BY_TAG[fullTag]
            if (matchedByTag != null) {
                return matchedByTag
            }

            val lang = locale.language.lowercase()
            return when (lang) {
                "de" -> GERMAN_DE
                "es" -> SPANISH_ES
                "fr" -> FRENCH_FR
                "en" -> ENGLISH_US
                "zh" ->
                    traditionalChineseOrNull(
                        listOf(locale.country.lowercase(), locale.script.lowercase()),
                    )
                else -> null
            }
        }

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for given [Locale].
         * Matches full language tag (language + country, e.g. "de-DE", "es-ES", "fr-FR", "en-US") first.
         */
        fun fromLocale(locale: Locale): AutoSetupLanguageConfig = fromLocaleOrNull(locale) ?: ENGLISH_US

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for a locale string tag, or null if unsupported.
         */
        fun fromLanguageTagOrNull(languageTag: String): AutoSetupLanguageConfig? {
            val cleanTag = languageTag.trim().lowercase().replace("_", "-")
            val matchedByTag = CONFIGS_BY_TAG[cleanTag]
            if (matchedByTag != null) {
                return matchedByTag
            }

            val subtags = cleanTag.split("-")
            val primaryLang = subtags.firstOrNull() ?: ""
            return when (primaryLang) {
                "de" -> GERMAN_DE
                "es" -> SPANISH_ES
                "fr" -> FRENCH_FR
                "en" -> ENGLISH_US
                "zh" -> traditionalChineseOrNull(subtags)
                else -> null
            }
        }

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for a locale string tag (e.g. "de-DE", "es-ES", "fr-FR", "en-GB").
         */
        fun fromLanguageTag(languageTag: String): AutoSetupLanguageConfig = fromLanguageTagOrNull(languageTag) ?: ENGLISH_US
    }
}
