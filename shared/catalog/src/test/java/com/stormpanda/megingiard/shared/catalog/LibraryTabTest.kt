package com.stormpanda.megingiard.catalog

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
            coverPath = null,
            isGame = isGame,
        )

    @Test
    fun testFilterAppsEmptyList() {
        assertTrue(LibraryTab.APPS.filterApps(emptyList()).isEmpty())
        assertTrue(LibraryTab.GAMES.filterApps(emptyList()).isEmpty())
    }

    @Test
    fun testTabNavigationOrder() {
        val tabs = listOf(LibraryTab.GAMES, LibraryTab.APPS)
        assertEquals(LibraryTab.APPS, LibraryTab.GAMES.next(tabs))
        assertEquals(LibraryTab.GAMES, LibraryTab.APPS.next(tabs))

        assertEquals(LibraryTab.APPS, LibraryTab.GAMES.previous(tabs))
        assertEquals(LibraryTab.GAMES, LibraryTab.APPS.previous(tabs))
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
