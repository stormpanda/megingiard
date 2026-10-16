package com.stormpanda.megingiard.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.settings.SettingsManager

private const val TAG = "MegingiardSettingsProv"

class MegingiardSettingsProvider : ContentProvider() {
    internal fun isCallerSignatureTrusted(
        context: Context,
        callingUid: Int = Binder.getCallingUid(),
    ): Boolean {
        if (callingUid == Process.myUid()) return true
        return context.packageManager.checkSignatures(Process.myUid(), callingUid) ==
            PackageManager.SIGNATURE_MATCH
    }

    internal fun isCallerPackageValid(
        context: Context,
        clientPackage: String?,
        callingUid: Int = Binder.getCallingUid(),
    ): Boolean {
        if (callingUid == Process.myUid()) return true
        if (clientPackage.isNullOrBlank()) return false
        val packages = context.packageManager.getPackagesForUid(callingUid) ?: return false
        return clientPackage in packages
    }

    companion object {
        private fun notifyUriChanged(
            context: Context,
            uri: Uri,
            label: String,
        ) {
            try {
                context.contentResolver.notifyChange(uri, null)
                AppLog.d(TAG, "Notified $label change on $uri")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to notify $label change: ${e.message}")
            }
        }

        fun notifyThemeChanged(context: Context) = notifyUriChanged(context, MegingiardIpcContract.THEME_URI, "theme")

        fun notifySettingsChanged(context: Context) = notifyUriChanged(context, MegingiardIpcContract.SETTINGS_URI, "settings")

        fun notifyProfilesChanged(context: Context) = notifyUriChanged(context, MegingiardIpcContract.PROFILES_URI, "profiles")
    }

    override fun onCreate(): Boolean {
        AppLog.i(TAG, "MegingiardSettingsProvider created")
        context?.let { ctx ->
            MegingiardIpcContract.init(ctx)
            SettingsManager.init(ctx)
        }
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
                if (!isCallerSignatureTrusted(context)) {
                    AppLog.w(TAG, "Rejected /settings query from untrusted caller uid=${Binder.getCallingUid()}")
                    return null
                }
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
                            profile.association?.packageName ?: "",
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
                val apiVersion =
                    extras.getInt(
                        MegingiardIpcContract.COLUMN_API_VERSION,
                        MegingiardIpcContract.DEFAULT_API_VERSION,
                    )
                AppLog.i(TAG, "updateClientState received with API version: $apiVersion")

                val clientPackage = extras.getString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE)
                if (!isCallerPackageValid(context, clientPackage)) {
                    AppLog.w(TAG, "Rejected updateClientState with mismatched package=$clientPackage from uid=${Binder.getCallingUid()}")
                    return Bundle().apply {
                        putBoolean("success", false)
                        putString("error", "Caller UID does not match client_package")
                    }
                }
                val isActive = extras.getBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, false)
                val focusedPackage = extras.getString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE)
                val romPath = extras.getString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_PATH)
                val focusedRomIdentifier = extras.getString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_IDENTIFIER)
                val hoveredPackage = extras.getString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE)
                val hoveredLabel = extras.getString(MegingiardIpcContract.COLUMN_HOVERED_LABEL)
                val hoveredRomPath = extras.getString(MegingiardIpcContract.COLUMN_HOVERED_ROM_PATH)
                val hoveredRomIdentifier = extras.getString(MegingiardIpcContract.COLUMN_HOVERED_ROM_IDENTIFIER)
                val hoveredSystemId = extras.getString(MegingiardIpcContract.COLUMN_HOVERED_SYSTEM_ID)

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
                    focusedRomPath = romPath,
                    focusedRomIdentifier = focusedRomIdentifier,
                    hoveredPackage = hoveredPackage,
                    hoveredLabel = hoveredLabel,
                    hoveredRomPath = hoveredRomPath,
                    hoveredRomIdentifier = hoveredRomIdentifier,
                    hoveredSystemId = hoveredSystemId,
                    hoveredPrimaryColor = hoveredPrimary,
                    hoveredSecondaryColor = hoveredSecondary,
                )

                if (focusedPackage != null) {
                    AutoSwitchCoordinator.onPackageChanged(focusedPackage)
                } else if (!isActive) {
                    AutoSwitchCoordinator.reevaluateAutoState()
                }

                // Notify observers
                context.contentResolver.notifyChange(MegingiardIpcContract.CLIENT_STATE_URI, null)

                val result =
                    Bundle().apply {
                        putBoolean("success", true)
                        putInt(
                            MegingiardIpcContract.COLUMN_API_VERSION,
                            MegingiardIpcContract.DEFAULT_API_VERSION,
                        ) // highest supported version
                    }
                if (apiVersion > MegingiardIpcContract.DEFAULT_API_VERSION) {
                    result.putString(
                        "warning",
                        "Requested API version $apiVersion is higher than supported (${MegingiardIpcContract.DEFAULT_API_VERSION}). Used fallback compatibility mode.",
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
