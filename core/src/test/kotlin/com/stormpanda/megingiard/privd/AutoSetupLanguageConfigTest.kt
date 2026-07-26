package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AutoSetupLanguageConfigTest {
    @Test
    fun fromLocale_returnsGermanForGermanLocales() {
        val configDe = AutoSetupLanguageConfig.fromLocale(Locale.GERMANY)
        val configDeAt = AutoSetupLanguageConfig.fromLocale(Locale("de", "AT"))

        assertEquals("de", configDe.languageCode)
        assertEquals("Build-Nummer", configDe.buildNumberQueryAndKeyword)
        assertEquals("Debugging über WLAN", configDe.wirelessDebuggingQueryAndKeyword)

        assertEquals("de", configDeAt.languageCode)
    }

    @Test
    fun fromLocale_returnsEnglishForEnglishLocales() {
        val configUs = AutoSetupLanguageConfig.fromLocale(Locale.US)
        val configUk = AutoSetupLanguageConfig.fromLocale(Locale.UK)

        assertEquals("en", configUs.languageCode)
        assertEquals("Build number", configUs.buildNumberQueryAndKeyword)
        assertEquals("Wireless debugging", configUs.wirelessDebuggingQueryAndKeyword)

        assertEquals("en", configUk.languageCode)
    }

    @Test
    fun fromLocale_fallsBackToEnglishForUnsupportedLocales() {
        val configFr = AutoSetupLanguageConfig.fromLocale(Locale.FRANCE)
        val configJa = AutoSetupLanguageConfig.fromLocale(Locale.JAPAN)

        assertEquals("en", configFr.languageCode)
        assertEquals("en", configJa.languageCode)
    }

    @Test
    fun fromLanguageTag_parsesLocaleTagsCorrectly() {
        val deTag = AutoSetupLanguageConfig.fromLanguageTag("de-DE")
        val enTag = AutoSetupLanguageConfig.fromLanguageTag("en-GB")
        val esTag = AutoSetupLanguageConfig.fromLanguageTag("es-ES")

        assertEquals("de", deTag.languageCode)
        assertEquals("en", enTag.languageCode)
        assertEquals("en", esTag.languageCode)
    }
}
