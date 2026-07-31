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
        val tabs = listOf(LibraryTab.ALL, LibraryTab.APPS, LibraryTab.GAMES)
        assertEquals(LibraryTab.APPS, LibraryTab.ALL.next(tabs))
        assertEquals(LibraryTab.GAMES, LibraryTab.APPS.next(tabs))
        assertEquals(LibraryTab.ALL, LibraryTab.GAMES.next(tabs))

        assertEquals(LibraryTab.GAMES, LibraryTab.ALL.previous(tabs))
        assertEquals(LibraryTab.ALL, LibraryTab.APPS.previous(tabs))
        assertEquals(LibraryTab.APPS, LibraryTab.GAMES.previous(tabs))
    }

    @Test
    fun testFilterAppsRomSystemOnly() {
        val romSnes =
            InstalledAppInfo(
                packageName = "rom.snes.super_mario",
                activityName = "",
                label = "Super Mario World",
                isGame = true,
                isRom = true,
                romPath = "/storage/emulated/0/Roms/snes/smw.sfc",
                systemId = "snes",
            )
        val game = makeApp("Game One", isGame = true)
        val apps = listOf(romSnes, game)

        val snesTab = LibraryTab.RomSystem(id = "ROM_snes", systemId = "snes", displayName = "SNES")
        val result = snesTab.filterApps(apps)

        assertEquals(1, result.size)
        assertEquals("Super Mario World", result[0].label)
    }
}
