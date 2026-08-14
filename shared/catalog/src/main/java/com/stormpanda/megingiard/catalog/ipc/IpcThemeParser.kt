package com.stormpanda.megingiard.ipc

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.ThemeMode

private const val TAG = "IpcThemeParser"

data class IpcThemeConfig(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val userAccentArgb: Int? = null,
)

object IpcThemeParser {
    fun parse(cursor: Cursor?): IpcThemeConfig {
        cursor?.use {
            if (it.moveToFirst()) {
                val modeIdx = it.getColumnIndex(MegingiardIpcContract.COLUMN_THEME_MODE)
                val accentIdx = it.getColumnIndex(MegingiardIpcContract.COLUMN_ACCENT_COLOR)

                val modeStr = if (modeIdx >= 0) it.getString(modeIdx) else null
                val accentInt = if (accentIdx >= 0 && !it.isNull(accentIdx)) it.getInt(accentIdx) else null

                val mode = ThemeMode.entries.firstOrNull { m -> m.name == modeStr } ?: ThemeMode.DARK
                AppLog.d(TAG, "Parsed IPC theme: mode=$mode, accentArgb=$accentInt")
                return IpcThemeConfig(themeMode = mode, userAccentArgb = accentInt)
            }
        }
        return IpcThemeConfig()
    }

    fun parse(
        resolver: ContentResolver,
        uri: Uri = MegingiardIpcContract.THEME_URI,
    ): IpcThemeConfig =
        try {
            val cursor = resolver.query(uri, null, null, null, null)
            parse(cursor)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to query or parse IPC theme: ${e.message}")
            IpcThemeConfig()
        }
}
