package com.stormpanda.megingiard.services

import com.stormpanda.megingiard.privd.AutoSetupLanguageConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MegingiardAccessibilityServiceTest {
    @Test
    fun autoSetupLanguageConfig_supportedLocales() {
        val spanishLocale = Locale.forLanguageTag("es-ES")
        val config = AutoSetupLanguageConfig.fromLocaleOrNull(spanishLocale)
        assertNotNull(config)
        assertEquals("es-ES", config?.localeTag)
        assertTrue(
            config?.wirelessDebuggingQueryAndKeyword?.lowercase()?.contains("depuración") == true ||
                config?.wirelessDebuggingQueryAndKeyword?.lowercase()?.contains("debugging") == true,
        )
    }

    @Test
    fun autoSetupLanguageConfig_germanLocale() {
        val germanLocale = Locale.GERMAN
        val config = AutoSetupLanguageConfig.fromLocaleOrNull(germanLocale)
        assertNotNull(config)
        assertEquals("de-DE", config?.localeTag)
    }

    @Test
    fun autoSetupLanguageConfig_englishLocale() {
        val englishLocale = Locale.ENGLISH
        val config = AutoSetupLanguageConfig.fromLocaleOrNull(englishLocale)
        assertNotNull(config)
        assertEquals("en-US", config?.localeTag)
    }
}
