package com.stormpanda.megingiard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ThemeMode] enum metadata and custom accent support.
 */
class ThemeModeTest {
    @Test
    fun `verify supportsCustomAccent property for all theme modes`() {
        assertTrue(ThemeMode.DARK.supportsCustomAccent)
        assertTrue(ThemeMode.DARK_OLED.supportsCustomAccent)
        assertFalse(ThemeMode.MEGINGIARD.supportsCustomAccent)
        assertFalse(ThemeMode.MJOLNIR.supportsCustomAccent)
        assertFalse(ThemeMode.VALHALLA.supportsCustomAccent)
        assertFalse(ThemeMode.AURORA.supportsCustomAccent)
        assertFalse(ThemeMode.RETRO_PHOSPHOR.supportsCustomAccent)
        assertFalse(ThemeMode.ROYAL_ASGARD.supportsCustomAccent)
    }

    @Test
    fun `verify theme mode count and names`() {
        val entries = ThemeMode.entries
        assertEquals(8, entries.size)
        assertEquals(ThemeMode.DARK, entries[0])
        assertEquals(ThemeMode.DARK_OLED, entries[1])
        assertEquals(ThemeMode.MEGINGIARD, entries[2])
        assertEquals(ThemeMode.MJOLNIR, entries[3])
        assertEquals(ThemeMode.VALHALLA, entries[4])
        assertEquals(ThemeMode.AURORA, entries[5])
        assertEquals(ThemeMode.RETRO_PHOSPHOR, entries[6])
        assertEquals(ThemeMode.ROYAL_ASGARD, entries[7])
    }

    @Test
    fun `verify fixed accent theme filtering for preset palette suggestions`() {
        val fixedThemes = ThemeMode.entries.filter { !it.supportsCustomAccent }
        assertEquals(6, fixedThemes.size)
        assertTrue(fixedThemes.contains(ThemeMode.MEGINGIARD))
        assertTrue(fixedThemes.contains(ThemeMode.MJOLNIR))
        assertTrue(fixedThemes.contains(ThemeMode.VALHALLA))
        assertTrue(fixedThemes.contains(ThemeMode.AURORA))
        assertTrue(fixedThemes.contains(ThemeMode.RETRO_PHOSPHOR))
        assertTrue(fixedThemes.contains(ThemeMode.ROYAL_ASGARD))
    }
}
