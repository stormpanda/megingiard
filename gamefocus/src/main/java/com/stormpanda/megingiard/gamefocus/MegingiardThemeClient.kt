package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "MegingiardThemeClient"
private val THEME_URI = Uri.parse("content://com.stormpanda.megingiard.provider/theme")

object MegingiardThemeClient {
    fun queryTheme(context: Context): Pair<ThemeMode, Color?> {
        try {
            val cursor = context.contentResolver.query(THEME_URI, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val modeIndex = it.getColumnIndex("theme_mode")
                    val accentIndex = it.getColumnIndex("accent_color")

                    val modeStr = if (modeIndex >= 0) it.getString(modeIndex) else null
                    val accentInt = if (accentIndex >= 0) it.getInt(accentIndex) else null

                    val mode = ThemeMode.entries.firstOrNull { m -> m.name == modeStr } ?: ThemeMode.DARK
                    val accentColor = accentInt?.let { argb -> Color(argb) }

                    AppLog.i(TAG, "Queried Megingiard theme from ContentProvider: mode=$mode, accent=$accentColor")
                    return Pair(mode, accentColor)
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to query Megingiard ThemeProvider: ${e.message}. Falling back to ThemeMode.DARK")
        }
        return Pair(ThemeMode.DARK, null)
    }

    fun observeTheme(context: Context): Flow<Pair<ThemeMode, Color?>> =
        callbackFlow {
            // Emit initial value
            trySend(queryTheme(context))

            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        AppLog.d(TAG, "Theme ContentObserver onChange triggered")
                        trySend(queryTheme(context))
                    }
                }

            try {
                context.contentResolver.registerContentObserver(THEME_URI, true, observer)
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to register ContentObserver for theme URI: ${e.message}")
            }

            awaitClose {
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to unregister ContentObserver: ${e.message}")
                }
            }
        }
}
