package com.stormpanda.megingiard.focus.rom

/**
 * Singleton registry for ROM launchers.
 */
object RomLauncherRegistry {
    private val launchers = mutableMapOf<String, RomLauncher>()

    init {
        register(RetroArchLauncher())
        register(GameNativeLauncher())
    }

    fun register(launcher: RomLauncher) {
        launchers[launcher.id] = launcher
    }

    fun getLauncher(id: String): RomLauncher? = launchers[id]
}
