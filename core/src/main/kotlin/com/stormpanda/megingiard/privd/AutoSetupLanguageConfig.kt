package com.stormpanda.megingiard.privd

import java.util.Locale

/**
 * String configuration for Android System Settings auto-setup navigation and text scanning.
 *
 * Provides language-specific search terms, keywords, and labels needed to locate and interact
 * with Android system settings screens across different system languages.
 *
 * @property languageCode Primary 2-letter ISO language code (e.g., "en", "de").
 * @property buildNumberQueryAndKeyword Search query and UI node matching string for Build Number.
 * @property wirelessDebuggingQueryAndKeyword Search query and UI node matching string for Wireless Debugging.
 * @property pairDeviceKeywords List of UI text keywords used to identify the "Pair device with pairing code" row.
 * @property explicitPortKeywords List of UI text labels used by text scanner to extract explicit pairing ports.
 * @property searchBarKeywords List of UI text keywords used to fallback-detect search bar/button.
 */
data class AutoSetupLanguageConfig(
    val languageCode: String,
    val buildNumberQueryAndKeyword: String,
    val wirelessDebuggingQueryAndKeyword: String,
    val pairDeviceKeywords: List<String>,
    val explicitPortKeywords: List<String>,
    val searchBarKeywords: List<String>,
) {
    companion object {
        val ENGLISH =
            AutoSetupLanguageConfig(
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
            )

        val GERMAN =
            AutoSetupLanguageConfig(
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
            )

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for given [Locale].
         * Falls back to [ENGLISH] if language is unsupported.
         */
        fun fromLocale(locale: Locale): AutoSetupLanguageConfig {
            val lang = locale.language.lowercase()
            return when (lang) {
                "de" -> GERMAN
                "en" -> ENGLISH
                else -> ENGLISH
            }
        }

        /**
         * Selects appropriate [AutoSetupLanguageConfig] for a language/locale string tag (e.g., "de-DE", "de", "en-US").
         */
        fun fromLanguageTag(languageTag: String): AutoSetupLanguageConfig {
            val primaryLang = languageTag.split("-", "_").firstOrNull()?.lowercase() ?: ""
            return when (primaryLang) {
                "de" -> GERMAN
                "en" -> ENGLISH
                else -> ENGLISH
            }
        }
    }
}
