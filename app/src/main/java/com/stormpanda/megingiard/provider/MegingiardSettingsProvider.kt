package com.stormpanda.megingiard.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.macropad.MacroPadState
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

            "/${MegingiardIpcContract.PATH_PROFILES}" -> {
                val cursor =
                    MatrixCursor(
                        arrayOf(
                            MegingiardIpcContract.COLUMN_PROFILE_ID,
                            MegingiardIpcContract.COLUMN_PROFILE_NAME,
                            MegingiardIpcContract.COLUMN_ASSOCIATED_PACKAGE,
                        ),
                    )
                MacroPadState.profiles.value.forEach { profile ->
                    cursor.addRow(
                        arrayOf(
                            profile.id,
                            profile.name,
                            profile.associatedPackage ?: "",
                        ),
                    )
                }
                cursor.setNotificationUri(context.contentResolver, MegingiardIpcContract.PROFILES_URI)
                AppLog.d(TAG, "Profiles queried: count=${MacroPadState.profiles.value.size}")
                return cursor
            }
        }
        return null
    }

    override fun getType(uri: Uri): String? =
        when (uri.path) {
            "/${MegingiardIpcContract.PATH_THEME}" -> "vnd.android.cursor.item/vnd.com.stormpanda.megingiard.theme"
            "/${MegingiardIpcContract.PATH_SETTINGS}" -> "vnd.android.cursor.item/vnd.com.stormpanda.megingiard.settings"
            "/${MegingiardIpcContract.PATH_PROFILES}" -> "vnd.android.cursor.dir/vnd.com.stormpanda.megingiard.profile"
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

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val context = context ?: return null
        if (method == "updateClientState" && extras != null) {
            try {
                val apiVersion = extras.getInt(MegingiardIpcContract.COLUMN_API_VERSION, 1)
                AppLog.i(TAG, "updateClientState received with API version: $apiVersion")

                val clientPackage = extras.getString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE)
                val isActive = extras.getBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, false)
                val focusedPackage = extras.getString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE)
                val romPath = extras.getString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_PATH)
                val hoveredPackage = extras.getString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE)
                val hoveredLabel = extras.getString(MegingiardIpcContract.COLUMN_HOVERED_LABEL)

                val hoveredPrimary =
                    if (extras.containsKey(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR)) {
                        extras.getInt(MegingiardIpcContract.COLUMN_HOVERED_PRIMARY_COLOR)
                    } else {
                        null
                    }
                val hoveredSecondary =
                    if (extras.containsKey(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR)) {
                        extras.getInt(MegingiardIpcContract.COLUMN_HOVERED_SECONDARY_COLOR)
                    } else {
                        null
                    }

                AppStateManager.setExternalClientState(
                    isActive = isActive,
                    packageName = clientPackage,
                    focusedApp = focusedPackage,
                    hoveredPackage = hoveredPackage,
                    hoveredLabel = hoveredLabel,
                    hoveredPrimaryColor = hoveredPrimary,
                    hoveredSecondaryColor = hoveredSecondary,
                )

                if (focusedPackage != null) {
                    AutoSwitchCoordinator.onPackageChanged(focusedPackage)
                }

                // Notify observers
                context.contentResolver.notifyChange(MegingiardIpcContract.CLIENT_STATE_URI, null)

                val result =
                    Bundle().apply {
                        putBoolean("success", true)
                        putInt(MegingiardIpcContract.COLUMN_API_VERSION, 1) // highest supported version
                    }
                if (apiVersion > 1) {
                    result.putString(
                        "warning",
                        "Requested API version $apiVersion is higher than supported (1). Used fallback compatibility mode.",
                    )
                }
                return result
            } catch (e: Exception) {
                AppLog.e(TAG, "Error handling updateClientState call", e)
                return Bundle().apply {
                    putBoolean("success", false)
                    putString("error", e.message)
                }
            }
        }
        return super.call(method, arg, extras)
    }
}
