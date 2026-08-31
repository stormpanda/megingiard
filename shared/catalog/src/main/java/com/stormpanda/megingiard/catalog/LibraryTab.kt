package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.math.nextItem
import com.stormpanda.megingiard.math.prevItem

/**
 * Tabs available in the Megingiard Game Focus Library view.
 */
sealed class LibraryTab {
    abstract val id: String

    data object APPS : LibraryTab() {
        override val id = "APPS"
    }

    data object GAMES : LibraryTab() {
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
            APPS -> apps.filter { !it.isGame && !it.isRom }
            GAMES -> apps.filter { it.isGame && !it.isRom }
            is RomSystem -> apps.filter { it.isRom && it.systemId == this.systemId }
        }

    fun previous(tabs: List<LibraryTab>): LibraryTab = tabs.prevItem(this)

    fun next(tabs: List<LibraryTab>): LibraryTab = tabs.nextItem(this)
}
