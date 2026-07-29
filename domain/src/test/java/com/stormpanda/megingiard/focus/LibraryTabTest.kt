package com.stormpanda.megingiard.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "LibraryTabTest"

class LibraryTabTest {
    private fun makeApp(
        label: String,
        isGame: Boolean,
    ): InstalledAppInfo =
        InstalledAppInfo(
            packageName = "com.test.${label.lowercase().replace(" ", "")}",
            activityName = "MainActivity",
            label = label,
            icon = null,
            coverPath = null,
            isGame = isGame,
        )

    @Test
    fun testFilterAppsAll() {
        val game = makeApp("Game One", isGame = true)
        val app = makeApp("Tool App", isGame = false)
        val apps = listOf(game, app)

        val result = LibraryTab.ALL.filterApps(apps)
        assertEquals(2, result.size)
        assertEquals(listOf(game, app), result)
    }

    @Test
    fun testFilterAppsGamesOnly() {
        val game = makeApp("Game One", isGame = true)
        val app = makeApp("Tool App", isGame = false)
        val apps = listOf(game, app)

        val result = LibraryTab.GAMES.filterApps(apps)
        assertEquals(1, result.size)
        assertEquals("Game One", result[0].label)
    }

    @Test
    fun testFilterAppsAppsOnly() {
        val game = makeApp("Game One", isGame = true)
        val app = makeApp("Tool App", isGame = false)
        val apps = listOf(game, app)

        val result = LibraryTab.APPS.filterApps(apps)
        assertEquals(1, result.size)
        assertEquals("Tool App", result[0].label)
    }

    @Test
    fun testFilterAppsEmptyList() {
        assertTrue(LibraryTab.ALL.filterApps(emptyList()).isEmpty())
        assertTrue(LibraryTab.APPS.filterApps(emptyList()).isEmpty())
        assertTrue(LibraryTab.GAMES.filterApps(emptyList()).isEmpty())
    }

    @Test
    fun testTabNavigationOrder() {
        assertEquals(LibraryTab.APPS, LibraryTab.ALL.next())
        assertEquals(LibraryTab.GAMES, LibraryTab.APPS.next())
        assertEquals(LibraryTab.ALL, LibraryTab.GAMES.next())

        assertEquals(LibraryTab.GAMES, LibraryTab.ALL.previous())
        assertEquals(LibraryTab.ALL, LibraryTab.APPS.previous())
        assertEquals(LibraryTab.APPS, LibraryTab.GAMES.previous())
    }
}
