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
        assertTrue(ThemeMode.LIGHT.supportsCustomAccent)
        assertFalse(ThemeMode.CYBERPUNK.supportsCustomAccent)
    }

    @Test
    fun `verify theme mode count and names`() {
        val entries = ThemeMode.entries
        assertEquals(4, entries.size)
        assertEquals(ThemeMode.DARK, entries[0])
        assertEquals(ThemeMode.DARK_OLED, entries[1])
        assertEquals(ThemeMode.LIGHT, entries[2])
        assertEquals(ThemeMode.CYBERPUNK, entries[3])
    }
}
