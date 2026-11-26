package com.stormpanda.megingiard.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSettingsComponentsTest {
    @Test
    fun testSettingsCategoryProperties() {
        for (category in SettingsCategory.entries) {
            assertTrue(category.titleResId > 0)
            assertNotNull(category.icon)
        }
        assertEquals(Icons.Rounded.Share, SettingsCategory.CONFIGURATION.icon)
    }

    @Test
    fun testThemeModeDisplayNameResId() {
        for (theme in ThemeMode.entries) {
            assertTrue(theme.displayNameResId() > 0)
        }
    }

    @Test
    fun testAppLanguageDisplayNameResId() {
        for (lang in AppLanguage.entries) {
            assertTrue(lang.displayNameResId() > 0)
        }
    }
}
