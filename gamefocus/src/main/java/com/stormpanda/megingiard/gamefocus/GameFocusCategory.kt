package com.stormpanda.megingiard.gamefocus

import androidx.annotation.StringRes
import com.stormpanda.megingiard.gamefocus.R

/**
 * Categories available in Megingiard Game Focus launcher.
 */
sealed class GameFocusCategory {
    abstract val id: String

    @get:StringRes abstract val stringResId: Int
    open val displayName: String? = null

    object GAMES : GameFocusCategory() {
        override val id = "GAMES"
        override val stringResId = R.string.gamefocus_header_android_games
    }

    object APPS : GameFocusCategory() {
        override val id = "APPS"
        override val stringResId = R.string.gamefocus_header_android_apps
    }

    object ALL_APPS : GameFocusCategory() {
        override val id = "ALL_APPS"
        override val stringResId = R.string.gamefocus_cat_all_apps
    }

    object FAVORITES : GameFocusCategory() {
        override val id = "FAVORITES"
        override val stringResId = R.string.gamefocus_cat_favorites
    }

    object LAST_USED : GameFocusCategory() {
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
        val builtIns: List<GameFocusCategory> get() = listOf(GAMES, APPS, ALL_APPS, FAVORITES, LAST_USED)
    }

    fun previous(categories: List<GameFocusCategory>): GameFocusCategory {
        val idx = categories.indexOf(this)
        if (idx == -1) return categories.firstOrNull() ?: GAMES
        val prevIdx = Math.floorMod(idx - 1, categories.size)
        return categories[prevIdx]
    }

    fun next(categories: List<GameFocusCategory>): GameFocusCategory {
        val idx = categories.indexOf(this)
        if (idx == -1) return categories.firstOrNull() ?: GAMES
        val nextIdx = Math.floorMod(idx + 1, categories.size)
        return categories[nextIdx]
    }
}
