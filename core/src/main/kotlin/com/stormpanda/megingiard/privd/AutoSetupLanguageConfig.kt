package com.stormpanda.megingiard.privd

import java.util.Locale

/**
 * String configuration for Android System Settings auto-setup navigation and text scanning.
 *
 * Provides locale-specific search terms, keywords, and labels needed to locate and interact
 * with Android system settings screens across different country locales.
 *
 * @property localeTag BCP 47 locale tag incorporating language and country (e.g. "de-DE", "en-US").
 * @property languageCode Primary 2-letter ISO language code (e.g., "en", "de").
 * @property buildNumberQueryAndKeyword Search query and UI node matching string for Build Number.
 * @property wirelessDebuggingQueryAndKeyword Search query and UI node matching string for Wireless Debugging.
 * @property pairDeviceKeywords List of UI text keywords used to identify the "Pair device with pairing code" row.
 * @property explicitPortKeywords List of UI text labels used by text scanner to extract explicit pairing ports.
 * @property searchBarKeywords List of UI text keywords used to fallback-detect search bar/button.
 * @property allowButtonKeywords List of UI text keywords used to confirm network trust dialogs.
 */
data class AutoSetupLanguageConfig(
    val localeTag: String,
    val languageCode: String,
    val buildNumberQueryAndKeyword: String,
    val wirelessDebuggingQueryAndKeyword: String,
    val pairDeviceKeywords: List<String>,
    val explicitPortKeywords: List<String>,
    val searchBarKeywords: List<String>,
    val allowButtonKeywords: List<String>,
) {
    companion object {
        val GERMAN_DE =
            AutoSetupLanguageConfig(
                localeTag = "de-DE",
                languageCode = "de",
                buildNumberQueryAndKeyword = "Build-Nummer",
                wirelessDebuggingQueryAndKeyword = "Debugging über WLAN",
                pairDeviceKeywords =
                    listOf(
                        "gerät über einen kopplungscode koppeln",
                        "gerät über einen kopplungscode koppen",
                        "geräte-kopplungscode",
                        "kopplungscode koppeln",
                        "mit kopplungscode koppeln",
                        "wlan-kopplungscode",
                    ),
                explicitPortKeywords =
                    listOf(
                        "ip-adresse & port",
                        "ip-adresse und port",
                        "port",
                        "adresse & port",
                    ),
                searchBarKeywords =
                    listOf(
                        "einstellungen durchsuchen",
                        "einstellungen suchen",
                        "suchen",
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

        val ENGLISH_US =
            AutoSetupLanguageConfig(
                localeTag = "en-US",
                languageCode = "en",
                buildNumberQueryAndKeyword = "Build number",
                wirelessDebuggingQueryAndKeyword = "Wireless debugging",
                pairDeviceKeywords =
                    listOf(
                        "pair device with pairing code",
                        "pair with pairing code",
                    ),
                explicitPortKeywords =
                    listOf(
                        "port",
                        "ip address & port",
                        "address & port",
                    ),
                searchBarKeywords =
                    listOf(
                        "search settings",
                        "search",
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

        private val CONFIGS_BY_TAG: Map<String, AutoSetupLanguageConfig> =
            listOf(
                GERMAN_DE,
                GERMAN_AT,
                GERMAN_CH,
                ENGLISH_US,
                ENGLISH_GB,
                ENGLISH_CA,
                ENGLISH_AU,
            ).associateBy { it.localeTag.lowercase() }

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for given [Locale].
         * Matches full language tag (language + country, e.g. "de-DE", "de-AT") first.
         */
        fun fromLocale(locale: Locale): AutoSetupLanguageConfig {
            val fullTag = locale.toLanguageTag().lowercase().replace("_", "-")
            val matchedByTag = CONFIGS_BY_TAG[fullTag]
            if (matchedByTag != null) {
                return matchedByTag
            }

            val lang = locale.language.lowercase()
            return when (lang) {
                "de" -> GERMAN_DE
                "en" -> ENGLISH_US
                else -> ENGLISH_US
            }
        }

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for a locale string tag (e.g. "de-DE", "de-AT", "en-GB").
         */
        fun fromLanguageTag(languageTag: String): AutoSetupLanguageConfig {
            val cleanTag = languageTag.trim().lowercase().replace("_", "-")
            val matchedByTag = CONFIGS_BY_TAG[cleanTag]
            if (matchedByTag != null) {
                return matchedByTag
            }

            val primaryLang = cleanTag.split("-").firstOrNull() ?: ""
            return when (primaryLang) {
                "de" -> GERMAN_DE
                "en" -> ENGLISH_US
                else -> ENGLISH_US
            }
        }
    }
}
