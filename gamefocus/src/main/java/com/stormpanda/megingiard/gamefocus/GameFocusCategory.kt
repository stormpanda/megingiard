package com.stormpanda.megingiard.gamefocus

import androidx.annotation.StringRes
import com.stormpanda.megingiard.gamefocus.R

/**
 * Categories available in Megingiard Game Focus launcher.
 */
enum class GameFocusCategory(
    @StringRes val stringResId: Int,
) {
    FAVORITES(R.string.gamefocus_cat_favorites),
    ALL_APPS(R.string.gamefocus_header_android_apps),
    LAST_USED(R.string.gamefocus_cat_last_used),
    ;

    fun previous(): GameFocusCategory {
        val entries = entries
        val prevOrdinal = Math.floorMod(ordinal - 1, entries.size)
        return entries[prevOrdinal]
    }

    fun next(): GameFocusCategory {
        val entries = entries
        val nextOrdinal = Math.floorMod(ordinal + 1, entries.size)
        return entries[nextOrdinal]
    }
}
