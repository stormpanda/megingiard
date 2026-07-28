package com.stormpanda.megingiard.ipc

import android.database.MatrixCursor
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IpcParsersTest {
    @Test
    fun `parse theme with valid mode and accent ARGB`() {
        val cursor = MatrixCursor(arrayOf("theme_mode", "accent_color"))
        val expectedAccent = 0xFF00E5FF.toInt()
        cursor.addRow(arrayOf("MJOLNIR", expectedAccent))

        val result = IpcThemeParser.parse(cursor)
        assertEquals(ThemeMode.MJOLNIR, result.themeMode)
        assertEquals(expectedAccent, result.userAccentArgb)
    }

    @Test
    fun `parse theme returns fallback DARK when cursor is empty`() {
        val cursor = MatrixCursor(arrayOf("theme_mode", "accent_color"))

        val result = IpcThemeParser.parse(cursor)
        assertEquals(ThemeMode.DARK, result.themeMode)
        assertNull(result.userAccentArgb)
    }

    @Test
    fun `parse settings with valid steamGridDbApiToken`() {
        val cursor = MatrixCursor(arrayOf("steamgriddb_api_token"))
        cursor.addRow(arrayOf("test_api_key_12345"))

        val result = IpcSettingsParser.parse(cursor)
        assertEquals("test_api_key_12345", result.steamGridDbApiToken)
    }
}
