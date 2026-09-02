package com.stormpanda.megingiard.ui

import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

private const val TAG = "AppThemeTest"

class AppThemeTest {
    @Test
    fun testPaletteFor_returnsAppropriatePalette() {
        AppLog.d(TAG, "Testing paletteFor returns appropriate palette across ThemeMode presets")
        val darkPalette = paletteFor(ThemeMode.DARK, null)
        assertNotNull(darkPalette)
        assertEquals(Color(0xFF121212), darkPalette.appBackground)

        val oledPalette = paletteFor(ThemeMode.DARK_OLED, null)
        assertNotNull(oledPalette)
        assertEquals(Color.Black, oledPalette.appBackground)

        val megingiardPalette = paletteFor(ThemeMode.MEGINGIARD, null)
        assertNotNull(megingiardPalette)
        assertEquals(Color(0xFF040C08), megingiardPalette.appBackground)
    }

    @Test
    fun testPaletteFor_customAccentApplied() {
        val customAccent = Color(0xFFFF5500)
        val palette = paletteFor(ThemeMode.DARK, customAccent)
        assertEquals(customAccent, palette.accent)
    }

    @Test
    fun testGamePadButton_enumMapping() {
        assertEquals("A", (GamePadButton.BUTTON_A.iconSpec as GamePadButtonIconSpec.Letter).text)
        assertEquals("B", (GamePadButton.BUTTON_B.iconSpec as GamePadButtonIconSpec.Letter).text)
        assertEquals("X", (GamePadButton.BUTTON_X.iconSpec as GamePadButtonIconSpec.Letter).text)
        assertEquals("Y", (GamePadButton.BUTTON_Y.iconSpec as GamePadButtonIconSpec.Letter).text)
        assertEquals("game_button_l1", (GamePadButton.BUTTON_L1.iconSpec as GamePadButtonIconSpec.Symbol).name)
        assertEquals("game_button_r1", (GamePadButton.BUTTON_R1.iconSpec as GamePadButtonIconSpec.Symbol).name)
    }
}
