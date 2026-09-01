package com.stormpanda.megingiard.catalog

import android.database.MatrixCursor
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.IpcThemeParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IpcParsersTest {
    @Test
    fun parseSettings_nullCursor_returnsEmptyConfig() {
        val config = IpcSettingsParser.parse(null)
        assertEquals("", config.steamGridDbApiToken)
    }

    @Test
    fun parseSettings_validCursor_returnsToken() {
        val cursor = MatrixCursor(arrayOf(MegingiardIpcContract.COLUMN_STEAMGRIDDB_TOKEN))
        cursor.addRow(arrayOf("test_token_12345"))
        val config = IpcSettingsParser.parse(cursor)
        assertEquals("test_token_12345", config.steamGridDbApiToken)
    }

    @Test
    fun parseTheme_nullCursor_returnsDefaultDark() {
        val config = IpcThemeParser.parse(null)
        assertEquals(ThemeMode.DARK, config.themeMode)
        assertNull(config.userAccentArgb)
    }

    @Test
    fun parseTheme_validCursor_returnsModeAndAccent() {
        val cursor = MatrixCursor(arrayOf(MegingiardIpcContract.COLUMN_THEME_MODE, MegingiardIpcContract.COLUMN_ACCENT_COLOR))
        cursor.addRow(arrayOf("DARK_OLED", 0xFF00FF))
        val config = IpcThemeParser.parse(cursor)
        assertEquals(ThemeMode.DARK_OLED, config.themeMode)
        assertEquals(0xFF00FF, config.userAccentArgb)
    }

    @Test
    fun megingiardIpcContract_init() {
        val context = RuntimeEnvironment.getApplication()
        MegingiardIpcContract.init(context)
        assertEquals("content://com.stormpanda.megingiard.provider/theme", MegingiardIpcContract.THEME_URI.toString())
        assertEquals("content://com.stormpanda.megingiard.provider/settings", MegingiardIpcContract.SETTINGS_URI.toString())
    }

    @Test
    fun observeContentProvider_emitsInitialValue() =
        kotlinx.coroutines.test.runTest {
            val context = RuntimeEnvironment.getApplication()
            val uri = android.net.Uri.parse("content://test.provider/item")
            val flow =
                com.stormpanda.megingiard.ipc
                    .observeContentProvider(context, uri) { _, _ -> "parsed_value" }
            val first = flow.first()
            assertEquals("parsed_value", first)
        }
}
