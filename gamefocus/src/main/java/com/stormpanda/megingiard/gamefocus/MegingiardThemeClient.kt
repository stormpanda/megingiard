package com.stormpanda.megingiard.gamefocus

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.ipc.IpcThemeParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger

object MegingiardThemeClient {
    fun observeTheme(context: Context): Flow<Pair<ThemeMode, Color?>> =
        observeContentProvider(
            context = context,
            uri = MegingiardIpcContract.THEME_URI,
            parser = { resolver, uri -> IpcThemeParser.parse(resolver, uri) },
        ).map { config ->
            Pair(
                config.themeMode,
                config.userAccentArgb?.let { Color(it) },
            )
        }

    fun observeThemeUpdates(context: Context): Flow<Int> {
        val count = AtomicInteger(0)
        return observeContentProvider(
            context = context,
            uri = MegingiardIpcContract.THEME_URI,
            parser = { _, _ -> count.incrementAndGet() },
        )
    }
}
