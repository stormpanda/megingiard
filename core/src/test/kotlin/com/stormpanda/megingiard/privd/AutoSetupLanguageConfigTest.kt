package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
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
}
