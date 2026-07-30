package com.stormpanda.megingiard.focus

import com.stormpanda.megingiard.math.floorMod

/**
 * Tabs available in the Megingiard Game Focus Library view.
 */
sealed class LibraryTab {
    abstract val id: String

    object ALL : LibraryTab() {
        override val id = "ALL"
    }

    object APPS : LibraryTab() {
        override val id = "APPS"
    }

    object GAMES : LibraryTab() {
        override val id = "GAMES"
    }

    data class RomSystem(
        override val id: String,
        val systemId: String,
        val displayName: String,
    ) : LibraryTab()

    /**
     * Filters the given list of installed applications according to the active tab.
     */
    fun filterApps(apps: List<InstalledAppInfo>): List<InstalledAppInfo> =
        when (this) {
            ALL -> apps
            APPS -> apps.filter { !it.isGame && !it.isRom }
            GAMES -> apps.filter { it.isGame && !it.isRom }
            is RomSystem -> apps.filter { it.isRom && it.systemId == this.systemId }
        }

    /**
     * Returns the previous tab in wrap-around order.
     */
    fun previous(tabs: List<LibraryTab>): LibraryTab {
        val idx = tabs.indexOf(this)
        if (idx == -1) return tabs.firstOrNull() ?: ALL
        val prevIdx = (idx - 1).floorMod(tabs.size)
        return tabs[prevIdx]
    }

    /**
     * Returns the next tab in wrap-around order.
     */
    fun next(tabs: List<LibraryTab>): LibraryTab {
        val idx = tabs.indexOf(this)
        if (idx == -1) return tabs.firstOrNull() ?: ALL
        val nextIdx = (idx + 1).floorMod(tabs.size)
        return tabs[nextIdx]
    }
}
