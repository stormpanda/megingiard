package com.stormpanda.megingiard.gamefocus.domain
import com.stormpanda.megingiard.catalog.RomLauncherRegistry

fun initGameFocusLaunchers() {
    RomLauncherRegistry.register(RetroArchLauncher())
    RomLauncherRegistry.register(GameNativeLauncher())
}
