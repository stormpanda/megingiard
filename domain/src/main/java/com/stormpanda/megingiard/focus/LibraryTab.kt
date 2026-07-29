package com.stormpanda.megingiard.focus

/**
 * Static tabs available in the Megingiard Game Focus Library view.
 */
enum class LibraryTab {
    ALL,
    APPS,
    GAMES,
    ;

    /**
     * Filters the given list of installed applications according to the active tab.
     */
    fun filterApps(apps: List<InstalledAppInfo>): List<InstalledAppInfo> =
        when (this) {
            ALL -> apps
            APPS -> apps.filter { !it.isGame }
            GAMES -> apps.filter { it.isGame }
        }

    /**
     * Returns the previous tab in wrap-around order.
     */
    fun previous(): LibraryTab {
        val allEntries = entries
        val prevOrdinal = Math.floorMod(ordinal - 1, allEntries.size)
        return allEntries[prevOrdinal]
    }

    /**
     * Returns the next tab in wrap-around order.
     */
    fun next(): LibraryTab {
        val allEntries = entries
        val nextOrdinal = Math.floorMod(ordinal + 1, allEntries.size)
        return allEntries[nextOrdinal]
    }
}
