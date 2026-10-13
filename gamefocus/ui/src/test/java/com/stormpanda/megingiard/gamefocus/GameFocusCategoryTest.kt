package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.gamefocus.domain.GalleryCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "GameFocusCategoryTest"

class GameFocusCategoryTest {
    private val categories = GameFocusCategory.builtIns

    @Test
    fun testNext_wrapsAroundFromLastToFirst() {
        var category: GameFocusCategory = GameFocusCategory.GAMES
        category = category.next(categories)
        assertEquals(GameFocusCategory.APPS, category)

        category = category.next(categories)
        assertEquals(GameFocusCategory.LAST_USED, category)

        category = category.next(categories)
        assertEquals(GameFocusCategory.FAVORITES, category)

        category = category.next(categories)
        assertEquals(GameFocusCategory.GAMES, category)
    }

    @Test
    fun testPrevious_wrapsAroundFromFirstToLast() {
        var category: GameFocusCategory = GameFocusCategory.GAMES
        category = category.previous(categories)
        assertEquals(GameFocusCategory.FAVORITES, category)

        category = category.previous(categories)
        assertEquals(GameFocusCategory.LAST_USED, category)

        category = category.previous(categories)
        assertEquals(GameFocusCategory.APPS, category)

        category = category.previous(categories)
        assertEquals(GameFocusCategory.GAMES, category)
    }

    @Test
    fun testAllEntriesHaveValidStringResId() {
        categories.forEach { entry ->
            assertNotEquals("Res ID for ${entry.id} should be non-zero", 0, entry.stringResId)
        }
    }

    @Test
    fun testFilterAppsAcrossAllCategories() {
        val gameApp =
            InstalledAppInfo(
                packageName = "com.game.test",
                activityName = "MainActivity",
                label = "Test Game",
                isGame = true,
                isRom = false,
            )
        val nonGameApp =
            InstalledAppInfo(
                packageName = "com.app.test",
                activityName = "MainActivity",
                label = "Test App",
                isGame = false,
                isRom = false,
            )
        val romApp =
            InstalledAppInfo(
                packageName = "rom.snes.test",
                activityName = "MainActivity",
                label = "Test Rom",
                isGame = true,
                isRom = true,
                systemId = "snes",
            )
        val allApps = listOf(gameApp, nonGameApp, romApp)

        // GAMES category
        val games = GameFocusCategory.GAMES.filterApps(allApps)
        assertEquals(listOf(gameApp), games)

        // APPS category
        val apps = GameFocusCategory.APPS.filterApps(allApps)
        assertEquals(listOf(nonGameApp), apps)

        // FAVORITES category
        val favs = GameFocusCategory.FAVORITES.filterApps(allApps, favorites = setOf("com.game.test", "rom.snes.test"))
        assertEquals(listOf(gameApp, romApp), favs)

        // LAST_USED category
        val lastUsed = GameFocusCategory.LAST_USED.filterApps(allApps, lastUsed = listOf("com.app.test", "com.game.test"))
        assertEquals(listOf(nonGameApp, gameApp), lastUsed)

        // RomSystem category
        val snesCat = GameFocusCategory.RomSystem(id = "snes", systemId = "snes", displayName = "SNES", folderUri = "content://...")
        val snesGames = snesCat.filterApps(allApps)
        assertEquals(listOf(romApp), snesGames)

        // Hidden filter
        val gamesWithoutHidden = GameFocusCategory.GAMES.filterApps(allApps, hidden = setOf("com.game.test"))
        assertTrue(gamesWithoutHidden.isEmpty())
    }

    private fun testApp(
        packageName: String,
        isGame: Boolean,
        isRom: Boolean = false,
        systemId: String? = null,
    ): InstalledAppInfo =
        InstalledAppInfo(
            packageName = packageName,
            activityName = "MainActivity",
            label = packageName,
            isGame = isGame,
            isRom = isRom,
            systemId = systemId,
        )

    @Test
    fun testFilterApps_customCollection_includesHiddenMembersInCatalogOrder() {
        val gameApp = testApp("com.game.test", isGame = true)
        val nonGameApp = testApp("com.app.test", isGame = false)
        val romApp = testApp("rom.snes.test", isGame = true, isRom = true, systemId = "snes")
        val collection = GameFocusCategory.CustomCollection(id = "collection_c1", collectionId = "c1", displayName = "Mix")

        val result =
            collection.filterApps(
                listOf(gameApp, nonGameApp, romApp),
                hidden = setOf("com.game.test"),
                collectionMembers = mapOf("c1" to setOf("rom.snes.test", "com.game.test")),
            )

        assertEquals(listOf(gameApp, romApp), result)
    }

    @Test
    fun testFilterApps_customCollection_unknownCollectionIsEmpty() {
        val collection = GameFocusCategory.CustomCollection(id = "collection_c9", collectionId = "c9", displayName = "None")
        assertTrue(collection.filterApps(listOf(testApp("com.game.test", isGame = true))).isEmpty())
    }

    @Test
    fun testDefaultGalleries_orderAndRomIdFormat() {
        val snes = CustomRomFolder(uriString = "content://snes", folderPath = "/roms/snes", systemId = "snes", systemName = "SNES")
        val gba = CustomRomFolder(uriString = "content://gba", folderPath = "/roms/gba", systemId = "gba", systemName = "GBA")
        val collections = listOf(GalleryCollection(id = "c2", name = "Zelda"), GalleryCollection(id = "c1", name = "action"))

        val ids = GameFocusCategory.defaultGalleries(listOf(snes, gba), collections).map { it.id }

        assertEquals(
            listOf(
                "LAST_USED",
                "FAVORITES",
                "collection_c1",
                "collection_c2",
                "GAMES",
                "APPS",
                "rom_gba_${"content://gba".hashCode()}",
                "rom_snes_${"content://snes".hashCode()}",
            ),
            ids,
        )
    }

    @Test
    fun testBuildOrderedGalleries_savedOrderFirstThenDefaults() {
        val collections = listOf(GalleryCollection(id = "c1", name = "action"))

        val ids =
            GameFocusCategory
                .buildOrderedGalleries(emptyList(), collections, listOf("GAMES", "collection_c1", "rom_missing_1"))
                .map { it.id }

        assertEquals(listOf("GAMES", "collection_c1", "LAST_USED", "FAVORITES", "APPS"), ids)
    }

    @Test
    fun testVisibleGalleries_filtersHiddenAndNeverEmpty() {
        val ordered = GameFocusCategory.builtIns
        assertEquals(
            listOf(GameFocusCategory.LAST_USED, GameFocusCategory.GAMES),
            GameFocusCategory.visibleGalleries(ordered, setOf("FAVORITES", "APPS")),
        )
        assertEquals(ordered, GameFocusCategory.visibleGalleries(ordered, setOf("LAST_USED", "FAVORITES", "GAMES", "APPS")))
    }

    @Test
    fun testCustomCollection_equalityIgnoresDisplayName() {
        val before = GameFocusCategory.CustomCollection(id = "collection_c1", collectionId = "c1", displayName = "Old")
        val after = GameFocusCategory.CustomCollection(id = "collection_c1", collectionId = "c1", displayName = "New")
        assertEquals(before, after)
        assertEquals(before.hashCode(), after.hashCode())
    }
}
