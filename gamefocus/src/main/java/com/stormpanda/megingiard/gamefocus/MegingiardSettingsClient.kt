package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object MegingiardSettingsClient {
    private const val TAG = "MegingiardSettingsClient"

    fun observeSteamGridDbApiToken(context: Context): Flow<String> =
        observeContentProvider(
            context = context,
            uri = MegingiardIpcContract.SETTINGS_URI,
            parser = { resolver, uri -> IpcSettingsParser.parse(resolver, uri) },
        ).map { ipcConfig ->
            if (ipcConfig.steamGridDbApiToken.isNotBlank()) {
                ipcConfig.steamGridDbApiToken
            } else {
                SettingsManager.steamGridDbApiToken.value
            }
        }

    fun updateClientState(
        context: Context,
        isActive: Boolean,
        focusedPackage: String? = null,
        focusedRomPath: String? = null,
        hoveredPackage: String? = null,
        hoveredLabel: String? = null,
    ) {
        val uri = Uri.parse("content://${MegingiardIpcContract.AUTHORITY}")
        val extras =
            Bundle().apply {
                putInt(MegingiardIpcContract.COLUMN_API_VERSION, 1)
                putString(MegingiardIpcContract.COLUMN_CLIENT_PACKAGE, context.packageName)
                putBoolean(MegingiardIpcContract.COLUMN_IS_ACTIVE, isActive)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_PACKAGE, focusedPackage)
                putString(MegingiardIpcContract.COLUMN_FOCUSED_ROM_PATH, focusedRomPath)
                putString(MegingiardIpcContract.COLUMN_HOVERED_PACKAGE, hoveredPackage)
                putString(MegingiardIpcContract.COLUMN_HOVERED_LABEL, hoveredLabel)
            }
        try {
            AppLog.d(TAG, "updateClientState: isActive=$isActive, pkg=$focusedPackage, hovered=$hoveredLabel")
            context.contentResolver.call(uri, "updateClientState", null, extras)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to update integration client state", e)
        }
    }
}
