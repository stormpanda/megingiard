package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AutoSetupLanguageConfigTest {
    @Test
    fun fromLocale_returnsCountrySpecificGermanConfigs() {
        val config = AutoSetupLanguageConfig.fromLocale(Locale.GERMANY)
        assertEquals("de-DE", config.localeTag)
        assertEquals("de", config.languageCode)
        assertEquals("Build-Nummer", config.buildNumberQueryAndKeyword)
        assertEquals("Debugging über WLAN", config.wirelessDebuggingQueryAndKeyword)
        assertEquals("USB-Debugging", config.usbDebuggingQueryAndKeyword)
        assertEquals("de-AT", AutoSetupLanguageConfig.fromLocale(Locale("de", "AT")).localeTag)
        assertEquals("de-CH", AutoSetupLanguageConfig.fromLocale(Locale("de", "CH")).localeTag)
    }

    @Test
    fun fromLocale_returnsCountrySpecificSpanishConfigs() {
        val config = AutoSetupLanguageConfig.fromLocale(Locale("es", "ES"))
        assertEquals("es-ES", config.localeTag)
        assertEquals("es", config.languageCode)
        assertEquals("Número de compilación", config.buildNumberQueryAndKeyword)
        assertEquals("Depuración inalámbrica", config.wirelessDebuggingQueryAndKeyword)
        assertEquals("Depuración por USB", config.usbDebuggingQueryAndKeyword)
        assertEquals("es-MX", AutoSetupLanguageConfig.fromLocale(Locale("es", "MX")).localeTag)
        assertEquals("es-US", AutoSetupLanguageConfig.fromLocale(Locale("es", "US")).localeTag)
    }

    @Test
    fun fromLocale_returnsCountrySpecificFrenchConfigs() {
        val config = AutoSetupLanguageConfig.fromLocale(Locale.FRANCE)
        assertEquals("fr-FR", config.localeTag)
        assertEquals("fr", config.languageCode)
        assertEquals("Numéro de build", config.buildNumberQueryAndKeyword)
        assertEquals("Débogage sans fil", config.wirelessDebuggingQueryAndKeyword)
        assertEquals("Débogage USB", config.usbDebuggingQueryAndKeyword)
        assertEquals("fr-CA", AutoSetupLanguageConfig.fromLocale(Locale.CANADA_FRENCH).localeTag)
    }

    @Test
    fun fromLocale_returnsCountrySpecificEnglishConfigs() {
        val config = AutoSetupLanguageConfig.fromLocale(Locale.US)
        assertEquals("en-US", config.localeTag)
        assertEquals("en-GB", AutoSetupLanguageConfig.fromLocale(Locale.UK).localeTag)
        assertEquals("en-CA", AutoSetupLanguageConfig.fromLocale(Locale.CANADA).localeTag)
        assertEquals("Build number", config.buildNumberQueryAndKeyword)
        assertEquals("Wireless debugging", config.wirelessDebuggingQueryAndKeyword)
        assertEquals("USB debugging", config.usbDebuggingQueryAndKeyword)
    }

    @Test
    fun fromLocale_fallsBackForUnsupportedLocales() {
        assertEquals("en-US", AutoSetupLanguageConfig.fromLocale(Locale.ITALY).localeTag)
        assertEquals("en-US", AutoSetupLanguageConfig.fromLocale(Locale.JAPAN).localeTag)
    }

    @Test
    fun fromLanguageTag_parsesFullLocaleTags() {
        val expected =
            listOf(
                "de-DE" to "de-DE",
                "de-AT" to "de-AT",
                "es-ES" to "es-ES",
                "es-MX" to "es-MX",
                "fr-FR" to "fr-FR",
                "fr-CA" to "fr-CA",
                "en-GB" to "en-GB",
            )
        for ((tag, exp) in expected) {
            assertEquals(exp, AutoSetupLanguageConfig.fromLanguageTag(tag).localeTag)
        }
    }

    @Test
    fun fromLocaleOrNull_returnsNullForUnsupportedLocales() {
        listOf(Locale.ITALY, Locale.JAPAN, Locale.SIMPLIFIED_CHINESE, Locale("ru", "RU")).forEach {
            assertNull(AutoSetupLanguageConfig.fromLocaleOrNull(it))
        }
        listOf(Locale.GERMANY, Locale.FRANCE, Locale.US, Locale("es", "ES")).forEach {
            assertNotNull(AutoSetupLanguageConfig.fromLocaleOrNull(it))
        }
    }

    @Test
    fun fromLanguageTagOrNull_returnsNullForUnsupportedLanguageTags() {
        listOf("ja-JP", "zh-CN", "ru-RU", "it-IT").forEach {
            assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull(it))
        }
        listOf("de-DE", "es-ES", "fr-FR", "en-US").forEach {
            assertNotNull(AutoSetupLanguageConfig.fromLanguageTagOrNull(it))
        }
    }

    @Test
    fun fromLocale_returnsRegionSpecificTraditionalChineseConfigs() {
        val config = AutoSetupLanguageConfig.fromLocale(Locale.TRADITIONAL_CHINESE)
        assertEquals("zh-TW", config.localeTag)
        assertEquals("zh", config.languageCode)
        assertEquals("版本號碼", config.buildNumberQueryAndKeyword)
        assertEquals("無線偵錯", config.wirelessDebuggingQueryAndKeyword)
        assertEquals("USB 偵錯", config.usbDebuggingQueryAndKeyword)
        assertEquals("zh-HK", AutoSetupLanguageConfig.fromLocale(Locale("zh", "HK")).localeTag)
        assertEquals("zh-MO", AutoSetupLanguageConfig.fromLocale(Locale("zh", "MO")).localeTag)
    }

    @Test
    fun fromLanguageTag_resolvesTraditionalChineseTags() {
        assertEquals("zh-TW", AutoSetupLanguageConfig.fromLanguageTag("zh-TW").localeTag)
        assertEquals("zh-HK", AutoSetupLanguageConfig.fromLanguageTag("zh-HK").localeTag)
        assertEquals("zh-TW", AutoSetupLanguageConfig.fromLanguageTag("zh-Hant").localeTag)
        assertEquals("zh-TW", AutoSetupLanguageConfig.fromLanguageTag("zh-Hant-TW").localeTag)
    }

    @Test
    fun simplifiedChineseStaysUnsupportedAndDoesNotFallBackToTraditional() {
        listOf(Locale.SIMPLIFIED_CHINESE, Locale("zh", "SG")).forEach {
            assertNull(AutoSetupLanguageConfig.fromLocaleOrNull(it))
        }
        listOf("zh-CN", "zh-Hans", "zh-Hans-CN", "zh").forEach {
            assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull(it))
        }
    }
}
