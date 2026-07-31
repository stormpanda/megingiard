package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog

private const val TAG = "RomLauncherRegistry"

/**
 * Singleton registry for ROM launchers.
 */
object RomLauncherRegistry {
    private val launchers = mutableMapOf<String, RomLauncher>()

    init {
        AppLog.d(TAG, "Initializing ROM launcher registry")
        register(RetroArchLauncher())
        register(GameNativeLauncher())
    }

    fun register(launcher: RomLauncher) {
        AppLog.d(TAG, "register: id=${launcher.id}, displayName=${launcher.displayName}")
        launchers[launcher.id] = launcher
    }

    fun getLauncher(id: String): RomLauncher? = launchers[id]
}
