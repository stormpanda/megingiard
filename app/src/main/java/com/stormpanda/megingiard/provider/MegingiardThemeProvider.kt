package com.stormpanda.megingiard.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.SettingsManager

private const val TAG = "MegingiardThemeProvider"
private const val AUTHORITY = "com.stormpanda.megingiard.provider"
val THEME_URI: Uri = Uri.parse("content://$AUTHORITY/theme")

class MegingiardThemeProvider : ContentProvider() {
    companion object {
        fun notifyThemeChanged(context: Context) {
            try {
                context.contentResolver.notifyChange(THEME_URI, null)
                AppLog.d(TAG, "Notified theme change on $THEME_URI")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to notify theme change: ${e.message}")
            }
        }
    }

    override fun onCreate(): Boolean {
        AppLog.i(TAG, "MegingiardThemeProvider created")
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
        if (uri.path == "/theme") {
            val cursor = MatrixCursor(arrayOf("theme_mode", "accent_color"))
            cursor.addRow(
                arrayOf(
                    SettingsManager.themeMode.value.name,
                    SettingsManager.accentColor.value,
                ),
            )
            cursor.setNotificationUri(context.contentResolver, THEME_URI)
            AppLog.d(TAG, "Theme queried: mode=${SettingsManager.themeMode.value.name}, accent=${SettingsManager.accentColor.value}")
            return cursor
        }
        return null
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.com.stormpanda.megingiard.theme"

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
