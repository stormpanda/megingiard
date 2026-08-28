package com.stormpanda.megingiard.ipc

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

object MegingiardIpcContract {
    @Volatile
    private var isInitialized = false

    @Volatile
    var AUTHORITY = "com.stormpanda.megingiard.provider"
        private set

    @Volatile
    var BASE_URI: Uri = Uri.parse("content://$AUTHORITY")
        private set

    const val PATH_THEME = "theme"

    @Volatile
    var THEME_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_THEME")
        private set

    const val PATH_SETTINGS = "settings"

    @Volatile
    var SETTINGS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SETTINGS")
        private set

    // ── Integration API ──
    const val PATH_CLIENT_STATE = "client_state"

    @Volatile
    var CLIENT_STATE_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_CLIENT_STATE")
        private set

    const val PATH_PROFILES = "profiles"

    @Volatile
    var PROFILES_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_PROFILES")
        private set

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return

        val pm = context.packageManager
        val isDebug = context.packageName.endsWith(".debug") || context.packageName.contains(".debug")

        val isHostCompanionApp =
            context.packageName == "com.stormpanda.megingiard" || context.packageName == "com.stormpanda.megingiard.debug"

        AUTHORITY =
            if (isHostCompanionApp) {
                if (isDebug) "com.stormpanda.megingiard.debug.provider" else "com.stormpanda.megingiard.provider"
            } else {
                val releaseInstalled = isPackageInstalled(pm, "com.stormpanda.megingiard")
                val debugInstalled = isPackageInstalled(pm, "com.stormpanda.megingiard.debug")
                when {
                    isDebug && debugInstalled -> "com.stormpanda.megingiard.debug.provider"
                    !isDebug && releaseInstalled -> "com.stormpanda.megingiard.provider"
                    releaseInstalled -> "com.stormpanda.megingiard.provider"
                    debugInstalled -> "com.stormpanda.megingiard.debug.provider"
                    else -> if (isDebug) "com.stormpanda.megingiard.debug.provider" else "com.stormpanda.megingiard.provider"
                }
            }

        BASE_URI = Uri.parse("content://$AUTHORITY")
        THEME_URI = Uri.parse("content://$AUTHORITY/$PATH_THEME")
        SETTINGS_URI = Uri.parse("content://$AUTHORITY/$PATH_SETTINGS")
        CLIENT_STATE_URI = Uri.parse("content://$AUTHORITY/$PATH_CLIENT_STATE")
        PROFILES_URI = Uri.parse("content://$AUTHORITY/$PATH_PROFILES")

        isInitialized = true
    }

    private fun isPackageInstalled(
        pm: PackageManager,
        packageName: String,
    ): Boolean =
        try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

    // Client State columns
    const val COLUMN_API_VERSION = "api_version"
    const val DEFAULT_API_VERSION = 1
    const val COLUMN_CLIENT_PACKAGE = "client_package"
    const val COLUMN_IS_ACTIVE = "is_active"
    const val COLUMN_FOCUSED_PACKAGE = "focused_package"
    const val COLUMN_FOCUSED_ROM_PATH = "focused_rom_path"
    const val COLUMN_FOCUSED_ROM_IDENTIFIER = "focused_rom_identifier"
    const val COLUMN_HOVERED_PACKAGE = "hovered_package"
    const val COLUMN_HOVERED_LABEL = "hovered_label"
    const val COLUMN_HOVERED_ROM_PATH = "hovered_rom_path"
    const val COLUMN_HOVERED_ROM_IDENTIFIER = "hovered_rom_identifier"
    const val COLUMN_HOVERED_SYSTEM_ID = "hovered_system_id"
    const val COLUMN_HOVERED_PRIMARY_COLOR = "hovered_primary_color"
    const val COLUMN_HOVERED_SECONDARY_COLOR = "hovered_secondary_color"

    const val GAMEFOCUS_PACKAGE = "com.stormpanda.megingiard.gamefocus"

    // Profiles columns
    const val COLUMN_PROFILE_ID = "profile_id"
    const val COLUMN_PROFILE_NAME = "profile_name"
    const val COLUMN_ASSOCIATED_PACKAGE = "associated_package"

    // Theme columns
    const val COLUMN_THEME_MODE = "theme_mode"
    const val COLUMN_ACCENT_COLOR = "accent_color"

    // Settings columns
    const val COLUMN_STEAMGRIDDB_TOKEN = "steamgriddb_api_token"
}
