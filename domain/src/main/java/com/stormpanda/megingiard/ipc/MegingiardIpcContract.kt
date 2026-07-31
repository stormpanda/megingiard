package com.stormpanda.megingiard.ipc

import android.net.Uri

object MegingiardIpcContract {
    const val AUTHORITY = "com.stormpanda.megingiard.provider"
    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")

    const val PATH_THEME = "theme"
    val THEME_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_THEME")

    const val PATH_SETTINGS = "settings"
    val SETTINGS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SETTINGS")

    // ── Integration API ──
    const val PATH_CLIENT_STATE = "client_state"
    val CLIENT_STATE_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_CLIENT_STATE")

    const val PATH_PROFILES = "profiles"
    val PROFILES_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_PROFILES")

    // Client State columns
    const val COLUMN_API_VERSION = "api_version"
    const val COLUMN_CLIENT_PACKAGE = "client_package"
    const val COLUMN_IS_ACTIVE = "is_active"
    const val COLUMN_FOCUSED_PACKAGE = "focused_package"
    const val COLUMN_FOCUSED_ROM_PATH = "focused_rom_path"
    const val COLUMN_HOVERED_PACKAGE = "hovered_package"
    const val COLUMN_HOVERED_LABEL = "hovered_label"
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
