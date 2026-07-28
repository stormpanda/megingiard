package com.stormpanda.megingiard.ipc

import android.net.Uri

object MegingiardIpcContract {
    const val AUTHORITY = "com.stormpanda.megingiard.provider"
    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")

    const val PATH_THEME = "theme"
    val THEME_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_THEME")

    const val PATH_SETTINGS = "settings"
    val SETTINGS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SETTINGS")

    // Theme columns
    const val COLUMN_THEME_MODE = "theme_mode"
    const val COLUMN_ACCENT_COLOR = "accent_color"

    // Settings columns
    const val COLUMN_STEAMGRIDDB_TOKEN = "steamgriddb_api_token"
}
