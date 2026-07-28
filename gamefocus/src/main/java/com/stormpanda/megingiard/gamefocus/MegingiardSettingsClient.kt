package com.stormpanda.megingiard.gamefocus

import android.content.Context
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object MegingiardSettingsClient {
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
}
