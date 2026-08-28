package com.stormpanda.megingiard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEnumsTest {
    @Test
    fun `SettingsCategory enum entries and ordering are correct`() {
        val expectedCategories =
            listOf(
                SettingsCategory.GENERAL,
                SettingsCategory.INPUT,
                SettingsCategory.APPEARANCE,
                SettingsCategory.CONFIGURATION,
                SettingsCategory.SCRAPING,
                SettingsCategory.UPDATES,
                SettingsCategory.DIAGNOSTICS,
            )
        assertEquals(expectedCategories, SettingsCategory.entries)
    }

    @Test
    fun `SettingsSubPage parentCategory mappings are correct`() {
        assertEquals(SettingsCategory.INPUT, SettingsSubPage.DEADZONES.parentCategory)
        assertEquals(SettingsCategory.SCRAPING, SettingsSubPage.STEAMGRIDDB_TOKEN.parentCategory)
        assertEquals(SettingsCategory.APPEARANCE, SettingsSubPage.CUSTOM_ACCENT.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.CREATE_BACKUP.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.SHARE_PROFILE.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.RESTORE_BACKUP.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.RESTORE_REVIEW.parentCategory)
        assertEquals(SettingsCategory.UPDATES, SettingsSubPage.UPDATE_AVAILABLE.parentCategory)
    }
}
