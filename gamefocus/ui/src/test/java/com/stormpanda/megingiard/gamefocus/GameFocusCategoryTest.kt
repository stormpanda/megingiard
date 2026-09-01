package com.stormpanda.megingiard.gamefocus

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
            com.stormpanda.megingiard.catalog.InstalledAppInfo(
                packageName = "com.game.test",
                activityName = "MainActivity",
                label = "Test Game",
                isGame = true,
                isRom = false,
            )
        val nonGameApp =
            com.stormpanda.megingiard.catalog.InstalledAppInfo(
                packageName = "com.app.test",
                activityName = "MainActivity",
                label = "Test App",
                isGame = false,
                isRom = false,
            )
        val romApp =
            com.stormpanda.megingiard.catalog.InstalledAppInfo(
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
}
