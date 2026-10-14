package com.stormpanda.megingiard.gamefocus

import androidx.annotation.StringRes
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.gamefocus.domain.GalleryCollection
import com.stormpanda.megingiard.gamefocus.domain.GalleryOrdering
import com.stormpanda.megingiard.gamefocus.domain.collectionCategoryId
import com.stormpanda.megingiard.math.nextItem
import com.stormpanda.megingiard.math.prevItem

private const val TAG = "GameFocusCategory"

/**
 * Categories (galleries) available in Megingiard Game Focus launcher.
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

    /**
     * A user-created collection gallery. Equality is by [id] only so renames never change the
     * identity of the selected gallery. Members are supplied to [filterApps] separately.
     */
    class CustomCollection(
        override val id: String,
        val collectionId: String,
        override val displayName: String,
    ) : GameFocusCategory() {
        override val stringResId = 0

        override fun equals(other: Any?): Boolean = other is CustomCollection && other.id == id

        override fun hashCode(): Int = id.hashCode()

        override fun toString(): String = "CustomCollection(id=$id, displayName=$displayName)"
    }

    companion object {
        val builtIns: List<GameFocusCategory> get() = listOf(LAST_USED, FAVORITES, GAMES, APPS)

        fun romSystemCategory(folder: CustomRomFolder): RomSystem =
            RomSystem(
                id = "rom_${folder.systemId}_${folder.uriString.hashCode()}",
                systemId = folder.systemId,
                displayName = folder.systemName,
                folderUri = folder.uriString,
            )

        fun collectionCategory(collection: GalleryCollection): CustomCollection =
            CustomCollection(
                id = collectionCategoryId(collection.id),
                collectionId = collection.id,
                displayName = collection.name,
            )

        /** Default gallery order: Recently Used, Favorites, collections A-Z, Games, Apps, ROM systems A-Z. */
        fun defaultGalleries(
            romFolders: List<CustomRomFolder>,
            collections: List<GalleryCollection>,
        ): List<GameFocusCategory> =
            listOf(LAST_USED, FAVORITES) +
                collections
                    .map { collectionCategory(it) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }) +
                listOf(GAMES, APPS) +
                romFolders
                    .map { romSystemCategory(it) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

        /** All existing galleries (visible and hidden) in effective user order. */
        fun buildOrderedGalleries(
            romFolders: List<CustomRomFolder>,
            collections: List<GalleryCollection>,
            savedOrder: List<String>,
        ): List<GameFocusCategory> {
            val defaults = defaultGalleries(romFolders, collections)
            val byId = defaults.associateBy { it.id }
            val ordered = GalleryOrdering.applyOrder(defaults.map { it.id }, savedOrder).mapNotNull { byId[it] }
            AppLog.d(TAG, "Built ${ordered.size} ordered galleries: ${ordered.map { it.id }}")
            return ordered
        }

        /** [ordered] without hidden galleries; never empty when [ordered] is not empty. */
        fun visibleGalleries(
            ordered: List<GameFocusCategory>,
            hidden: Set<String>,
        ): List<GameFocusCategory> {
            val visibleIds = GalleryOrdering.visibleIds(ordered.map { it.id }, hidden).toSet()
            return ordered.filter { it.id in visibleIds }
        }
    }

    fun filterApps(
        allApps: List<InstalledAppInfo>,
        favorites: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        lastUsed: List<String> = emptyList(),
        collectionMembers: Map<String, Set<String>> = emptyMap(),
    ): List<InstalledAppInfo> =
        when (this) {
            GAMES -> {
                allApps.filter { it.isGame && !it.isRom && it.packageName !in hidden }
            }

            APPS -> {
                allApps.filter { !it.isGame && !it.isRom && it.packageName !in hidden }
            }

            FAVORITES -> {
                allApps.filter { it.packageName in favorites }
            }

            LAST_USED -> {
                lastUsed.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }.filter { it.packageName !in hidden }
            }

            is RomSystem -> {
                allApps.filter { it.isRom && it.systemId == this.systemId && it.packageName !in hidden }
            }

            is CustomCollection -> {
                // Like Favorites: explicit curation wins, hidden apps stay visible; catalog order is kept.
                val members = collectionMembers[collectionId].orEmpty()
                allApps.filter { it.packageName in members }
            }
        }

    fun previous(categories: List<GameFocusCategory>): GameFocusCategory = categories.prevItem(this)

    fun next(categories: List<GameFocusCategory>): GameFocusCategory = categories.nextItem(this)
}
