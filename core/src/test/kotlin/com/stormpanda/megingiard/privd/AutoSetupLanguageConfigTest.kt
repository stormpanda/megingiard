package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AutoSetupLanguageConfigTest {
    @Test
    fun fromLocale_returnsCountrySpecificGermanConfigs() {
        val configDeDe = AutoSetupLanguageConfig.fromLocale(Locale.GERMANY)
        val configDeAt = AutoSetupLanguageConfig.fromLocale(Locale("de", "AT"))
        val configDeCh = AutoSetupLanguageConfig.fromLocale(Locale("de", "CH"))

        assertEquals("de-DE", configDeDe.localeTag)
        assertEquals("de", configDeDe.languageCode)
        assertEquals("Build-Nummer", configDeDe.buildNumberQueryAndKeyword)
        assertEquals("Debugging über WLAN", configDeDe.wirelessDebuggingQueryAndKeyword)
        assertEquals("USB-Debugging", configDeDe.usbDebuggingQueryAndKeyword)

        assertEquals("de-AT", configDeAt.localeTag)
        assertEquals("de-CH", configDeCh.localeTag)
    }

    @Test
    fun fromLocale_returnsCountrySpecificSpanishConfigs() {
        val configEsEs = AutoSetupLanguageConfig.fromLocale(Locale("es", "ES"))
        val configEsMx = AutoSetupLanguageConfig.fromLocale(Locale("es", "MX"))
        val configEsUs = AutoSetupLanguageConfig.fromLocale(Locale("es", "US"))

        assertEquals("es-ES", configEsEs.localeTag)
        assertEquals("es", configEsEs.languageCode)
        assertEquals("Número de compilación", configEsEs.buildNumberQueryAndKeyword)
        assertEquals("Depuración inalámbrica", configEsEs.wirelessDebuggingQueryAndKeyword)
        assertEquals("Depuración por USB", configEsEs.usbDebuggingQueryAndKeyword)

        assertEquals("es-MX", configEsMx.localeTag)
        assertEquals("es-US", configEsUs.localeTag)
    }

    @Test
    fun fromLocale_returnsCountrySpecificFrenchConfigs() {
        val configFrFr = AutoSetupLanguageConfig.fromLocale(Locale.FRANCE)
        val configFrCa = AutoSetupLanguageConfig.fromLocale(Locale.CANADA_FRENCH)

        assertEquals("fr-FR", configFrFr.localeTag)
        assertEquals("fr", configFrFr.languageCode)
        assertEquals("Numéro de build", configFrFr.buildNumberQueryAndKeyword)
        assertEquals("Débogage sans fil", configFrFr.wirelessDebuggingQueryAndKeyword)
        assertEquals("Débogage USB", configFrFr.usbDebuggingQueryAndKeyword)

        assertEquals("fr-CA", configFrCa.localeTag)
    }

    @Test
    fun fromLocale_returnsCountrySpecificEnglishConfigs() {
        val configUs = AutoSetupLanguageConfig.fromLocale(Locale.US)
        val configUk = AutoSetupLanguageConfig.fromLocale(Locale.UK)
        val configCa = AutoSetupLanguageConfig.fromLocale(Locale.CANADA)

        assertEquals("en-US", configUs.localeTag)
        assertEquals("en-GB", configUk.localeTag)
        assertEquals("en-CA", configCa.localeTag)

        assertEquals("Build number", configUs.buildNumberQueryAndKeyword)
        assertEquals("Wireless debugging", configUs.wirelessDebuggingQueryAndKeyword)
        assertEquals("USB debugging", configUs.usbDebuggingQueryAndKeyword)
    }

    @Test
    fun fromLocale_fallsBackForUnsupportedLocales() {
        val configIt = AutoSetupLanguageConfig.fromLocale(Locale.ITALY)
        val configJa = AutoSetupLanguageConfig.fromLocale(Locale.JAPAN)

        assertEquals("en-US", configIt.localeTag)
        assertEquals("en-US", configJa.localeTag)
    }

    @Test
    fun fromLanguageTag_parsesFullLocaleTags() {
        val deDeTag = AutoSetupLanguageConfig.fromLanguageTag("de-DE")
        val deAtTag = AutoSetupLanguageConfig.fromLanguageTag("de-AT")
        val esEsTag = AutoSetupLanguageConfig.fromLanguageTag("es-ES")
        val esMxTag = AutoSetupLanguageConfig.fromLanguageTag("es-MX")
        val frFrTag = AutoSetupLanguageConfig.fromLanguageTag("fr-FR")
        val frCaTag = AutoSetupLanguageConfig.fromLanguageTag("fr-CA")
        val enGbTag = AutoSetupLanguageConfig.fromLanguageTag("en-GB")

        assertEquals("de-DE", deDeTag.localeTag)
        assertEquals("de-AT", deAtTag.localeTag)
        assertEquals("es-ES", esEsTag.localeTag)
        assertEquals("es-MX", esMxTag.localeTag)
        assertEquals("fr-FR", frFrTag.localeTag)
        assertEquals("fr-CA", frCaTag.localeTag)
        assertEquals("en-GB", enGbTag.localeTag)
    }

    @Test
    fun fromLocaleOrNull_returnsNullForUnsupportedLocales() {
        val configIt = AutoSetupLanguageConfig.fromLocaleOrNull(Locale.ITALY)
        val configJa = AutoSetupLanguageConfig.fromLocaleOrNull(Locale.JAPAN)
        val configZh = AutoSetupLanguageConfig.fromLocaleOrNull(Locale.SIMPLIFIED_CHINESE)
        val configRu = AutoSetupLanguageConfig.fromLocaleOrNull(Locale("ru", "RU"))

        org.junit.Assert.assertNull(configIt)
        org.junit.Assert.assertNull(configJa)
        org.junit.Assert.assertNull(configZh)
        org.junit.Assert.assertNull(configRu)

        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLocaleOrNull(Locale.GERMANY))
        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLocaleOrNull(Locale.FRANCE))
        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLocaleOrNull(Locale.US))
        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLocaleOrNull(Locale("es", "ES")))
    }

    @Test
    fun fromLanguageTagOrNull_returnsNullForUnsupportedLanguageTags() {
        org.junit.Assert.assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("ja-JP"))
        org.junit.Assert.assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("zh-CN"))
        org.junit.Assert.assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("ru-RU"))
        org.junit.Assert.assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("it-IT"))

        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("de-DE"))
        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("es-ES"))
        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("fr-FR"))
        org.junit.Assert.assertNotNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("en-US"))
    }

    @Test
    fun everyConfigCarriesDeveloperOptionsKeywords() {
        // Auto Setup tells the Wireless debugging sub-screen apart from Developer options by
        // looking for the Developer options title, because both screens show the wireless
        // debugging label. A locale missing this keyword reports the sub-screen while still on
        // Developer options and then scrolls forever looking for the pairing row.
        val configs =
            listOf(
                AutoSetupLanguageConfig.GERMAN_DE,
                AutoSetupLanguageConfig.SPANISH_ES,
                AutoSetupLanguageConfig.FRENCH_FR,
                AutoSetupLanguageConfig.ENGLISH_US,
                AutoSetupLanguageConfig.CHINESE_TW,
            )

        configs.forEach { config ->
            val keywords = config.developerOptionsKeywords
            org.junit.Assert.assertTrue(
                "${config.localeTag} has no developerOptionsKeywords",
                keywords.isNotEmpty(),
            )
            keywords.forEach { keyword ->
                org.junit.Assert.assertTrue(
                    "${config.localeTag} keyword '$keyword' must be lowercase — matching lowercases the screen text",
                    keyword == keyword.lowercase(),
                )
            }
        }

        // The locales that predate this field keep the shared default, so their behaviour is
        // unchanged; Traditional Chinese overrides it because its title is not in that default.
        org.junit.Assert.assertTrue(
            AutoSetupLanguageConfig.ENGLISH_US.developerOptionsKeywords.contains("developer options"),
        )
        org.junit.Assert.assertTrue(
            AutoSetupLanguageConfig.GERMAN_DE.developerOptionsKeywords.contains("entwickleroptionen"),
        )
        assertEquals(listOf("開發人員選項"), AutoSetupLanguageConfig.CHINESE_TW.developerOptionsKeywords)
    }

    @Test
    fun fromLocale_returnsRegionSpecificTraditionalChineseConfigs() {
        val configZhTw = AutoSetupLanguageConfig.fromLocale(Locale.TRADITIONAL_CHINESE)
        val configZhHk = AutoSetupLanguageConfig.fromLocale(Locale("zh", "HK"))
        val configZhMo = AutoSetupLanguageConfig.fromLocale(Locale("zh", "MO"))

        assertEquals("zh-TW", configZhTw.localeTag)
        assertEquals("zh", configZhTw.languageCode)
        assertEquals("版本號碼", configZhTw.buildNumberQueryAndKeyword)
        assertEquals("無線偵錯", configZhTw.wirelessDebuggingQueryAndKeyword)
        assertEquals("USB 偵錯", configZhTw.usbDebuggingQueryAndKeyword)

        assertEquals("zh-HK", configZhHk.localeTag)
        assertEquals("zh-MO", configZhMo.localeTag)
    }

    @Test
    fun fromLanguageTag_resolvesTraditionalChineseTags() {
        assertEquals("zh-TW", AutoSetupLanguageConfig.fromLanguageTag("zh-TW").localeTag)
        assertEquals("zh-HK", AutoSetupLanguageConfig.fromLanguageTag("zh-HK").localeTag)

        // Script subtag present but no region, and region present alongside a script subtag.
        assertEquals("zh-TW", AutoSetupLanguageConfig.fromLanguageTag("zh-Hant").localeTag)
        assertEquals("zh-TW", AutoSetupLanguageConfig.fromLanguageTag("zh-Hant-TW").localeTag)
    }

    @Test
    fun simplifiedChineseStaysUnsupportedAndDoesNotFallBackToTraditional() {
        // Simplified Settings entries read "版本号" / "无线调试", so the Traditional keywords
        // would never match. Reporting the language as unsupported is correct; silently
        // handing back the Traditional config would stall Auto Setup mid-flow.
        assertNull(AutoSetupLanguageConfig.fromLocaleOrNull(Locale.SIMPLIFIED_CHINESE))
        assertNull(AutoSetupLanguageConfig.fromLocaleOrNull(Locale("zh", "SG")))
        assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("zh-CN"))
        assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("zh-Hans"))
        assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("zh-Hans-CN"))
        assertNull(AutoSetupLanguageConfig.fromLanguageTagOrNull("zh"))
    }
}
