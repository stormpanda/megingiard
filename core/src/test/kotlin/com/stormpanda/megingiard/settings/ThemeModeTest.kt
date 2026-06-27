package com.stormpanda.megingiard.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun testThemeModeProperties() {
        assertTrue(ThemeMode.DARK.supportsCustomAccent)
        assertTrue(ThemeMode.LIGHT.supportsCustomAccent)
        assertFalse(ThemeMode.CYBERPUNK.supportsCustomAccent)
        assertFalse(ThemeMode.STEAM_OS.supportsCustomAccent)
    }
}
