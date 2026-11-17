package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.gamefocus.domain.GalleryCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "GameFocusCatTest"

class GameFocusCategoryTest {
    private val categories = GameFocusCategory.builtIns

    @Test
    fun testNext_wrapsAroundFromLastToFirst() {
        AppLog.d(TAG, "testNext_wrapsAroundFromLastToFirst")
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

    @Test
    fun testBuildAppsByCategoryId_matchesFilterAppsAcrossAllCategoryTypes() {
        val game1 = testApp("com.game.one", isGame = true)
        val game2 = testApp("com.game.hidden", isGame = true)
        val app1 = testApp("com.app.one", isGame = false)
        val snesRom = testApp("rom.snes.chrono", isGame = true, isRom = true, systemId = "snes")
        val allApps = listOf(game1, game2, app1, snesRom)
        val favorites = setOf("com.game.hidden", "rom.snes.chrono")
        val hidden = setOf("com.game.hidden")
        val lastUsed = listOf("rom.snes.chrono", "com.game.hidden", "com.app.one", "com.missing.pkg")
        val snesCategory = GameFocusCategory.RomSystem(id = "rom_snes_1", systemId = "snes", displayName = "SNES", folderUri = "u")
        val nonEmptyCollection = GameFocusCategory.CustomCollection(id = "collection_c1", collectionId = "c1", displayName = "Picks")
        val emptyCollection = GameFocusCategory.CustomCollection(id = "collection_c2", collectionId = "c2", displayName = "Empty")
        val collectionMembers = mapOf("c1" to setOf("com.game.hidden", "com.app.one"))

        val categoriesToBuild =
            listOf(
                GameFocusCategory.GAMES,
                GameFocusCategory.APPS,
                GameFocusCategory.FAVORITES,
                GameFocusCategory.LAST_USED,
                snesCategory,
                nonEmptyCollection,
                emptyCollection,
                GameFocusCategory.GAMES,
            )

        val byId =
            GameFocusCategory.buildAppsByCategoryId(
                categories = categoriesToBuild,
                allApps = allApps,
                favorites = favorites,
                hidden = hidden,
                lastUsed = lastUsed,
                collectionMembers = collectionMembers,
            )

        assertEquals(7, byId.size)
        for (cat in categoriesToBuild.distinctBy { it.id }) {
            assertEquals(
                cat.filterApps(allApps, favorites, hidden, lastUsed, collectionMembers),
                byId[cat.id],
            )
        }
        assertEquals(listOf(snesRom, app1), byId[GameFocusCategory.LAST_USED.id])
        assertTrue(byId[emptyCollection.id]!!.isEmpty())
    }

    @Test
    fun testCategoryStepDirection_forwardBackwardWrapAndSingleItem() {
        assertEquals(1, categoryStepDirection(GameFocusCategory.GAMES, GameFocusCategory.APPS, categories))
        assertEquals(1, categoryStepDirection(GameFocusCategory.FAVORITES, GameFocusCategory.GAMES, categories))
        assertEquals(-1, categoryStepDirection(GameFocusCategory.APPS, GameFocusCategory.GAMES, categories))
        assertEquals(-1, categoryStepDirection(GameFocusCategory.GAMES, GameFocusCategory.FAVORITES, categories))
        assertEquals(0, categoryStepDirection(GameFocusCategory.GAMES, GameFocusCategory.GAMES, categories))
        assertEquals(0, categoryStepDirection(GameFocusCategory.GAMES, GameFocusCategory.APPS, listOf(GameFocusCategory.GAMES)))
    }

    @Test
    fun testCategoryTransitionController_prewarmRetainAndMidFlightReversal() {
        val controller = CategoryTransitionController(initialCategory = GameFocusCategory.GAMES, maxRetained = 3)
        assertEquals(1, controller.entries.size)

        // Pre-warm next neighbor while idle: stays inactive at +1f
        assertTrue(controller.prewarm(GameFocusCategory.APPS, initialOffset = 1f))
        assertFalse(controller.prewarm(GameFocusCategory.APPS, initialOffset = 1f))
        val prewarmedApps = controller.entries.first { it.category == GameFocusCategory.APPS }
        assertFalse(prewarmedApps.isActive)
        assertFalse(prewarmedApps.isSelected)
        assertEquals(1f, prewarmedApps.renderOffset, 0.0001f)

        // Step down GAMES -> APPS: reuses prewarmed entry without growing pool
        controller.onSelectedCategoryChanged(GameFocusCategory.APPS, categories)
        assertEquals(2, controller.entries.size)
        val outgoingGames = controller.entries.first { it.category == GameFocusCategory.GAMES }
        assertTrue(outgoingGames.isActive)
        assertFalse(outgoingGames.isSelected)
        assertEquals(-1f, outgoingGames.targetOffset, 0.0001f)
        assertTrue(prewarmedApps.isActive)
        assertTrue(prewarmedApps.isSelected)
        assertEquals(1f, prewarmedApps.renderOffset, 0.0001f)
        assertEquals(0f, prewarmedApps.targetOffset, 0.0001f)
        assertTrue(prewarmedApps.zOrder > outgoingGames.zOrder)

        // Simulate mid-flight positions (-0.45f and +0.55f) and reverse back UP to GAMES
        outgoingGames.renderOffset = -0.45f
        prewarmedApps.renderOffset = 0.55f
        controller.onSelectedCategoryChanged(GameFocusCategory.GAMES, categories)
        assertEquals(-0.45f, outgoingGames.renderOffset, 0.0001f)
        assertEquals(0f, outgoingGames.targetOffset, 0.0001f)
        assertTrue(outgoingGames.isSelected)
        assertEquals(0.55f, prewarmedApps.renderOffset, 0.0001f)
        assertEquals(1f, prewarmedApps.targetOffset, 0.0001f)
        assertFalse(prewarmedApps.isSelected)

        // Settle both entries: APPS becomes inactive at +1f, GAMES settles at 0f
        controller.onEntrySettled(prewarmedApps)
        controller.onEntrySettled(outgoingGames)
        assertFalse(prewarmedApps.isActive)
        assertEquals(1f, prewarmedApps.renderOffset, 0.0001f)
        assertTrue(outgoingGames.isActive)
        assertEquals(0f, outgoingGames.renderOffset, 0.0001f)

        // Pre-warm two more categories -> LRU evicts oldest inactive entry (APPS) down to maxRetained = 3
        assertTrue(controller.prewarm(GameFocusCategory.LAST_USED, initialOffset = 1f))
        assertTrue(controller.prewarm(GameFocusCategory.FAVORITES, initialOffset = -1f))
        assertEquals(3, controller.entries.size)
        assertEquals(
            setOf(GameFocusCategory.GAMES.id, GameFocusCategory.LAST_USED.id, GameFocusCategory.FAVORITES.id),
            controller.entries.map { it.category.id }.toSet(),
        )

        // Reset on dispose collapses pool back to active selected category only
        controller.resetToSelected()
        assertEquals(listOf(GameFocusCategory.GAMES.id), controller.entries.map { it.category.id })
        assertEquals(0f, controller.entries.single().renderOffset, 0.0001f)
    }

    @Test
    fun testCategoryTransitionController_threeCategoryReversalPreservesExitingEntryTarget() {
        val threeCategories =
            listOf(
                GameFocusCategory.GAMES,
                GameFocusCategory.APPS,
                GameFocusCategory.LAST_USED,
            )
        val controller = CategoryTransitionController(initialCategory = GameFocusCategory.GAMES, maxRetained = 4)

        // Step down: GAMES -> APPS
        controller.onSelectedCategoryChanged(GameFocusCategory.APPS, threeCategories)
        val gamesEntry = controller.entries.first { it.category == GameFocusCategory.GAMES }
        val appsEntry = controller.entries.first { it.category == GameFocusCategory.APPS }
        gamesEntry.renderOffset = -0.5f
        appsEntry.renderOffset = 0.5f

        // Step down again before GAMES settles: APPS -> LAST_USED
        controller.onSelectedCategoryChanged(GameFocusCategory.LAST_USED, threeCategories)
        val lastUsedEntry = controller.entries.first { it.category == GameFocusCategory.LAST_USED }
        assertFalse(gamesEntry.isActive)
        assertEquals(-1f, gamesEntry.renderOffset, 0.0001f)
        assertEquals(-1f, gamesEntry.targetOffset, 0.0001f)
        assertEquals(1f, lastUsedEntry.renderOffset, 0.0001f)
        assertEquals(0f, lastUsedEntry.targetOffset, 0.0001f)
        appsEntry.renderOffset = -0.2f
        lastUsedEntry.renderOffset = 0.8f

        // Reverse direction UP: LAST_USED -> APPS; GAMES stays parked at -1f rather than reversing across viewport
        controller.onSelectedCategoryChanged(GameFocusCategory.APPS, threeCategories)
        assertFalse(gamesEntry.isActive)
        assertEquals(-1f, gamesEntry.renderOffset, 0.0001f)
        assertEquals(-1f, gamesEntry.targetOffset, 0.0001f)
        assertFalse(gamesEntry.isSelected)
        assertEquals(0f, appsEntry.targetOffset, 0.0001f)
        assertTrue(appsEntry.isSelected)
        assertEquals(1f, lastUsedEntry.targetOffset, 0.0001f)
        assertFalse(lastUsedEntry.isSelected)
    }

    @Test
    fun testCategoryTransitionController_coldEntryTargetsZeroAndStaleSettleIsIgnored() {
        val controller = CategoryTransitionController(initialCategory = GameFocusCategory.GAMES, maxRetained = 4)

        // Cold switch without pre-warming: incoming APPS entry must start at +1f and target 0f
        controller.onSelectedCategoryChanged(GameFocusCategory.APPS, categories)
        val appsEntry = controller.entries.first { it.category == GameFocusCategory.APPS }
        assertEquals(1f, appsEntry.renderOffset, 0.0001f)
        assertEquals(0f, appsEntry.targetOffset, 0.0001f)
        assertTrue(appsEntry.isActive)
        assertTrue(appsEntry.isSelected)

        // Reverse back UP to GAMES; a late settle callback from APPS's older generation must be ignored
        val staleGen = appsEntry.animGeneration
        controller.onSelectedCategoryChanged(GameFocusCategory.GAMES, categories)
        appsEntry.renderOffset = 0.4f
        controller.onEntrySettled(appsEntry, settledGeneration = staleGen)
        assertTrue(appsEntry.isActive)
        assertEquals(0.4f, appsEntry.renderOffset, 0.0001f)
    }

    @Test
    fun testCategoryTransitionController_twoCategoryReversalWithDirectionHint() {
        val twoCategories = listOf(GameFocusCategory.GAMES, GameFocusCategory.APPS)
        val controller = CategoryTransitionController(initialCategory = GameFocusCategory.GAMES, maxRetained = 4)

        // Step down: GAMES -> APPS (directionHint = +1)
        controller.onSelectedCategoryChanged(GameFocusCategory.APPS, twoCategories, directionHint = 1)
        val gamesEntry = controller.entries.first { it.category == GameFocusCategory.GAMES }
        val appsEntry = controller.entries.first { it.category == GameFocusCategory.APPS }
        assertEquals(-1f, gamesEntry.targetOffset, 0.0001f)
        assertEquals(1f, appsEntry.renderOffset, 0.0001f)

        // Reverse mid-flight UP: APPS -> GAMES (directionHint = -1)
        gamesEntry.renderOffset = -0.5f
        appsEntry.renderOffset = 0.5f
        controller.onSelectedCategoryChanged(GameFocusCategory.GAMES, twoCategories, directionHint = -1)
        assertEquals(0f, gamesEntry.targetOffset, 0.0001f)
        assertTrue(gamesEntry.isSelected)
        assertEquals(1f, appsEntry.targetOffset, 0.0001f)
        assertFalse(appsEntry.isSelected)
    }

    @Test
    fun testCategoryTransitionController_wrapAroundReentryResetsOppositeSideOffset() {
        val threeCategories =
            listOf(
                GameFocusCategory.GAMES,
                GameFocusCategory.APPS,
                GameFocusCategory.LAST_USED,
            )
        val controller = CategoryTransitionController(initialCategory = GameFocusCategory.GAMES, maxRetained = 4)

        controller.onSelectedCategoryChanged(GameFocusCategory.APPS, threeCategories, directionHint = 1)
        val gamesEntry = controller.entries.first { it.category == GameFocusCategory.GAMES }
        gamesEntry.renderOffset = -0.5f

        controller.onSelectedCategoryChanged(GameFocusCategory.LAST_USED, threeCategories, directionHint = 1)
        gamesEntry.renderOffset = -0.85f

        // Wrap around DOWN: LAST_USED -> GAMES (+1) while GAMES is still active on the -1 side
        controller.onSelectedCategoryChanged(GameFocusCategory.GAMES, threeCategories, directionHint = 1)
        assertEquals(1f, gamesEntry.renderOffset, 0.0001f)
        assertEquals(0f, gamesEntry.targetOffset, 0.0001f)
        assertTrue(gamesEntry.isSelected)
    }
}
