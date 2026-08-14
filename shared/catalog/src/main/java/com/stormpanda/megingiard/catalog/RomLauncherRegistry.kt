package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.AppLog

private const val TAG = "RomLauncherRegistry"

/**
 * Singleton registry for ROM launchers.
 */
object RomLauncherRegistry {
    private val launchers = mutableMapOf<String, RomLauncher>()

    fun register(launcher: RomLauncher) {
        AppLog.d(TAG, "register: id=${launcher.id}, displayName=${launcher.displayName}")
        launchers[launcher.id] = launcher
    }

    fun getLauncher(id: String): RomLauncher? = launchers[id]
}
