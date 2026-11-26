package com.stormpanda.megingiard.settings

import android.content.res.Configuration
import com.stormpanda.megingiard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeDescriptionFormattingTest {
    @Test
    fun testFormatThemeNamesList_usesLocaleAwareConjunctionAndOrder() {
        val items = listOf("Dark", "Dark OLED", "Niflheim Dusk")

        val english = formatThemeNamesList(items, Locale.US)
        assertEquals("Dark, Dark OLED, and Niflheim Dusk", english)

        val german = formatThemeNamesList(items, Locale.GERMAN)
        assertEquals("Dark, Dark OLED und Niflheim Dusk", german)

        val traditionalChinese = formatThemeNamesList(items, Locale.TRADITIONAL_CHINESE)
        assertEquals("Dark、Dark OLED和Niflheim Dusk", traditionalChinese)
    }

    @Test
    fun testHelpSettingsThemeDesc_formatsAllThemesAcrossLocales() {
        val baseApp = RuntimeEnvironment.getApplication()
        val locales =
            listOf(
                Locale.ENGLISH,
                Locale.GERMAN,
                Locale.TRADITIONAL_CHINESE,
            )

        for (locale in locales) {
            val config = Configuration(baseApp.resources.configuration)
            config.setLocale(locale)
            val localizedContext = baseApp.createConfigurationContext(config)
            val themeNames = ThemeMode.entries.map { localizedContext.getString(it.displayNameResId()) }
            val formattedList = formatThemeNamesList(themeNames, locale)
            val description =
                localizedContext.getString(
                    R.string.help_settings_theme_desc,
                    ThemeMode.entries.size,
                    formattedList,
                )

            assertTrue(
                "Missing theme count for locale $locale in: $description",
                description.contains(ThemeMode.entries.size.toString()),
            )
            for (name in themeNames) {
                assertTrue(
                    "Missing theme '$name' for locale $locale in: $description",
                    description.contains(name),
                )
            }
        }
    }
}
