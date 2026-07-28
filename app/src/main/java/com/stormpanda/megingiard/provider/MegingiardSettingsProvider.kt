package com.stormpanda.megingiard.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.settings.SettingsManager

private const val TAG = "MegingiardSettingsProvider"

class MegingiardSettingsProvider : ContentProvider() {
    companion object {
        fun notifyThemeChanged(context: Context) {
            try {
                context.contentResolver.notifyChange(MegingiardIpcContract.THEME_URI, null)
                AppLog.d(TAG, "Notified theme change on ${MegingiardIpcContract.THEME_URI}")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to notify theme change: ${e.message}")
            }
        }

        fun notifySettingsChanged(context: Context) {
            try {
                context.contentResolver.notifyChange(MegingiardIpcContract.SETTINGS_URI, null)
                AppLog.d(TAG, "Notified settings change on ${MegingiardIpcContract.SETTINGS_URI}")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to notify settings change: ${e.message}")
            }
        }
    }

    override fun onCreate(): Boolean {
        AppLog.i(TAG, "MegingiardSettingsProvider created")
        context?.let { SettingsManager.init(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        SettingsManager.init(context)
        when (uri.path) {
            "/${MegingiardIpcContract.PATH_THEME}" -> {
                val cursor =
                    MatrixCursor(
                        arrayOf(
                            MegingiardIpcContract.COLUMN_THEME_MODE,
                            MegingiardIpcContract.COLUMN_ACCENT_COLOR,
                        ),
                    )
                cursor.addRow(
                    arrayOf(
                        SettingsManager.themeMode.value.name,
                        SettingsManager.accentColor.value,
                    ),
                )
                cursor.setNotificationUri(context.contentResolver, MegingiardIpcContract.THEME_URI)
                AppLog.d(TAG, "Theme queried: mode=${SettingsManager.themeMode.value.name}, accent=${SettingsManager.accentColor.value}")
                return cursor
            }

            "/${MegingiardIpcContract.PATH_SETTINGS}" -> {
                val cursor =
                    MatrixCursor(
                        arrayOf(
                            MegingiardIpcContract.COLUMN_STEAMGRIDDB_TOKEN,
                        ),
                    )
                cursor.addRow(
                    arrayOf(
                        SettingsManager.steamGridDbApiToken.value,
                    ),
                )
                cursor.setNotificationUri(context.contentResolver, MegingiardIpcContract.SETTINGS_URI)
                AppLog.d(
                    TAG,
                    "Settings queried: steamGridDbApiToken=${if (SettingsManager.steamGridDbApiToken.value.isBlank()) "empty" else "configured"}",
                )
                return cursor
            }
        }
        return null
    }

    override fun getType(uri: Uri): String? =
        when (uri.path) {
            "/${MegingiardIpcContract.PATH_THEME}" -> "vnd.android.cursor.item/vnd.com.stormpanda.megingiard.theme"
            "/${MegingiardIpcContract.PATH_SETTINGS}" -> "vnd.android.cursor.item/vnd.com.stormpanda.megingiard.settings"
            else -> null
        }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
