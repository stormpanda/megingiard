package com.stormpanda.megingiard.ipc

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import com.stormpanda.megingiard.AppLog

private const val TAG = "IpcSettingsParser"

data class IpcSettingsConfig(
    val steamGridDbApiToken: String = "",
)

object IpcSettingsParser {
    fun parse(cursor: Cursor?): IpcSettingsConfig {
        cursor?.use {
            if (it.moveToFirst()) {
                val tokenIdx = it.getColumnIndex(MegingiardIpcContract.COLUMN_STEAMGRIDDB_TOKEN)
                val token = if (tokenIdx >= 0 && !it.isNull(tokenIdx)) it.getString(tokenIdx) ?: "" else ""
                AppLog.d(TAG, "Parsed IPC settings: steamGridDbApiToken=${if (token.isBlank()) "empty" else "configured"}")
                return IpcSettingsConfig(steamGridDbApiToken = token)
            }
        }
        return IpcSettingsConfig()
    }

    fun parse(
        resolver: ContentResolver,
        uri: Uri = MegingiardIpcContract.SETTINGS_URI,
    ): IpcSettingsConfig =
        try {
            val cursor = resolver.query(uri, null, null, null, null)
            parse(cursor)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to query or parse IPC settings: ${e.message}")
            IpcSettingsConfig()
        }
}
