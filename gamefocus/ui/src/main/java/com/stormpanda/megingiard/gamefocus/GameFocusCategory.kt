package com.stormpanda.megingiard.gamefocus

import androidx.annotation.StringRes
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.math.nextItem
import com.stormpanda.megingiard.math.prevItem

/**
 * Categories available in Megingiard Game Focus launcher.
 */
sealed class GameFocusCategory {
    abstract val id: String

    @get:StringRes abstract val stringResId: Int
    open val displayName: String? = null

    data object GAMES : GameFocusCategory() {
        override val id = "GAMES"
        override val stringResId = R.string.gamefocus_header_android_games
    }

    data object APPS : GameFocusCategory() {
        override val id = "APPS"
        override val stringResId = R.string.gamefocus_header_android_apps
    }

    data object FAVORITES : GameFocusCategory() {
        override val id = "FAVORITES"
        override val stringResId = R.string.gamefocus_cat_favorites
    }

    data object LAST_USED : GameFocusCategory() {
        override val id = "LAST_USED"
        override val stringResId = R.string.gamefocus_cat_last_used
    }

    data class RomSystem(
        override val id: String,
        val systemId: String,
        override val displayName: String,
        val folderUri: String,
    ) : GameFocusCategory() {
        override val stringResId = 0
    }

    companion object {
        val builtIns: List<GameFocusCategory> get() = listOf(LAST_USED, FAVORITES, GAMES, APPS)
    }

    fun filterApps(
        allApps: List<InstalledAppInfo>,
        favorites: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        lastUsed: List<String> = emptyList(),
    ): List<InstalledAppInfo> =
        when (this) {
            GAMES -> allApps.filter { it.isGame && !it.isRom && it.packageName !in hidden }
            APPS -> allApps.filter { !it.isGame && !it.isRom && it.packageName !in hidden }
            FAVORITES -> allApps.filter { it.packageName in favorites }
            LAST_USED -> lastUsed.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }.filter { it.packageName !in hidden }
            is RomSystem -> allApps.filter { it.isRom && it.systemId == this.systemId && it.packageName !in hidden }
        }

    fun previous(categories: List<GameFocusCategory>): GameFocusCategory = categories.prevItem(this)

    fun next(categories: List<GameFocusCategory>): GameFocusCategory = categories.nextItem(this)
}
