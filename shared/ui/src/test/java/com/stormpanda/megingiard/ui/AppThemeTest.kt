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
    fun testContrastingContentColor_darkAndLightBackgrounds() {
        AppLog.d(TAG, "Testing contrastingContentColor across dark and light background colors")
        // Dark backgrounds -> light content (Color.White)
        assertEquals(Color.White, Color.Black.contrastingContentColor())
        assertEquals(Color.White, Color(0xFF121212).contrastingContentColor())
        assertEquals(Color.White, Color(0xFFCC0000).contrastingContentColor()) // Default red accent
        assertEquals(Color.White, Color.Blue.contrastingContentColor())

        // Bright backgrounds -> dark content (default Color(0xFF121212))
        assertEquals(Color(0xFF121212), Color.White.contrastingContentColor())
        assertEquals(Color(0xFF121212), Color.Yellow.contrastingContentColor())
        assertEquals(Color(0xFF121212), Color.Cyan.contrastingContentColor())
    }

    @Test
    fun testPaletteFor_brightCustomAccentCalculatesHighContrastOnAccent() {
        AppLog.d(TAG, "Testing paletteFor dynamic onAccent contrast for bright custom accents")
        // Bright yellow accent in Dark theme should resolve to appBackground (0xFF121212)
        val darkYellowPalette = paletteFor(ThemeMode.DARK, Color.Yellow)
        assertEquals(Color.Yellow, darkYellowPalette.accent)
        assertEquals(Color(0xFF121212), darkYellowPalette.onAccent)
        assertEquals(Color(0xFF121212), darkYellowPalette.buttonIconTint)
        assertEquals(Color(0xFF121212), darkYellowPalette.controlIndicatorActive)

        // Bright yellow accent in Dark OLED theme should resolve to appBackground (Color.Black)
        val oledYellowPalette = paletteFor(ThemeMode.DARK_OLED, Color.Yellow)
        assertEquals(Color.Yellow, oledYellowPalette.accent)
        assertEquals(Color.Black, oledYellowPalette.onAccent)
        assertEquals(Color.Black, oledYellowPalette.buttonIconTint)
        assertEquals(Color.Black, oledYellowPalette.controlIndicatorActive)
    }

    @Test
    fun testPaletteFor_darkCustomAccentPreservesWhiteOnAccent() {
        AppLog.d(TAG, "Testing paletteFor dynamic onAccent contrast for dark custom accents")
        // Default red accent or dark accent should produce Color.White
        val darkRedPalette = paletteFor(ThemeMode.DARK, Color(0xFFCC0000))
        assertEquals(Color(0xFFCC0000), darkRedPalette.accent)
        assertEquals(Color.White, darkRedPalette.onAccent)
        assertEquals(Color.White, darkRedPalette.buttonIconTint)

        // Null custom accent falls back to default red with Color.White
        val defaultDarkPalette = paletteFor(ThemeMode.DARK, null)
        assertEquals(Color(0xFFCC0000), defaultDarkPalette.accent)
        assertEquals(Color.White, defaultDarkPalette.onAccent)
        assertEquals(Color.White, defaultDarkPalette.buttonIconTint)
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
