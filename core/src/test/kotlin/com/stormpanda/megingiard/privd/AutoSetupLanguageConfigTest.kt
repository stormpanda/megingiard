package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AutoSetupLanguageConfigTest {
    @Test
    fun fromLocale_returnsCountrySpecificConfigs() {
        val configDeDe = AutoSetupLanguageConfig.fromLocale(Locale.GERMANY)
        val configDeAt = AutoSetupLanguageConfig.fromLocale(Locale("de", "AT"))
        val configDeCh = AutoSetupLanguageConfig.fromLocale(Locale("de", "CH"))

        assertEquals("de-DE", configDeDe.localeTag)
        assertEquals("de", configDeDe.languageCode)
        assertEquals("Build-Nummer", configDeDe.buildNumberQueryAndKeyword)
        assertEquals("Debugging über WLAN", configDeDe.wirelessDebuggingQueryAndKeyword)

        assertEquals("de-AT", configDeAt.localeTag)
        assertEquals("de-CH", configDeCh.localeTag)
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
    }

    @Test
    fun fromLocale_fallsBackForUnsupportedLocales() {
        val configFr = AutoSetupLanguageConfig.fromLocale(Locale.FRANCE)
        val configJa = AutoSetupLanguageConfig.fromLocale(Locale.JAPAN)

        assertEquals("en-US", configFr.localeTag)
        assertEquals("en-US", configJa.localeTag)
    }

    @Test
    fun fromLanguageTag_parsesFullLocaleTags() {
        val deDeTag = AutoSetupLanguageConfig.fromLanguageTag("de-DE")
        val deAtTag = AutoSetupLanguageConfig.fromLanguageTag("de-AT")
        val enGbTag = AutoSetupLanguageConfig.fromLanguageTag("en-GB")
        val esEsTag = AutoSetupLanguageConfig.fromLanguageTag("es-ES")

        assertEquals("de-DE", deDeTag.localeTag)
        assertEquals("de-AT", deAtTag.localeTag)
        assertEquals("en-GB", enGbTag.localeTag)
        assertEquals("en-US", esEsTag.localeTag)
    }
}
