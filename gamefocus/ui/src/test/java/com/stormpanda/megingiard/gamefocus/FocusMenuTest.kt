package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "FocusMenuTest"

class FocusMenuTest {
    private val nativeApp =
        InstalledAppInfo(
            label = "Native Game",
            packageName = "com.example.game",
            activityName = "com.example.game.MainActivity",
            isGame = true,
            isRom = false,
        )

    private val retroArchRomApp =
        InstalledAppInfo(
            label = "Super Mario World",
            packageName = "rom.snes.smw",
            activityName = "",
            isGame = true,
            isRom = true,
            systemId = "snes",
        )

    private val pcRomApp =
        InstalledAppInfo(
            label = "PC Game",
            packageName = "rom.pc.game",
            activityName = "",
            isGame = true,
            isRom = true,
            systemId = "pc",
        )

    private val snesFolder =
        CustomRomFolder(
            uriString = "content://snes",
            systemId = "snes",
            systemName = "Super Nintendo",
            folderPath = "/roms/snes",
        )

    private val pcFolder =
        CustomRomFolder(
            uriString = "content://pc",
            systemId = "pc",
            systemName = "PC",
            folderPath = "/roms/pc",
        )

    @Test
    fun testBuildLibraryMenuActionsNativeAppWithRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: native app with ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = nativeApp,
                selectedTab = LibraryTab.GAMES,
                romFolders = listOf(snesFolder),
            )
        assertEquals(7, actions.size)
        assertEquals("view_carousel", actions.last().iconSymbol)
    }

    @Test
    fun testBuildLibraryMenuActionsNativeAppWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: native app without ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = nativeApp,
                selectedTab = LibraryTab.GAMES,
                romFolders = emptyList(),
            )
        assertEquals(6, actions.size)
    }

    @Test
    fun testBuildLibraryMenuActionsRomAppRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: RetroArch ROM app with ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = retroArchRomApp,
                selectedTab = LibraryTab.RomSystem("rom_snes", "snes", "SNES"),
                romFolders = listOf(snesFolder),
            )
        assertEquals(6, actions.size)
    }

    @Test
    fun testBuildLibraryMenuActionsRomAppNonRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: non-RetroArch ROM app with ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = pcRomApp,
                selectedTab = LibraryTab.RomSystem("rom_pc", "pc", "PC"),
                romFolders = listOf(pcFolder),
            )
        assertEquals(6, actions.size)
    }

    @Test
    fun testBuildLibraryMenuActionsRomAppNonRetroArchWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: non-RetroArch ROM app without ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = pcRomApp,
                selectedTab = LibraryTab.GAMES,
                romFolders = emptyList(),
            )
        assertEquals(4, actions.size)
    }

    @Test
    fun testBuildLibraryMenuActionsNoAppRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: no app, RetroArch tab with ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = null,
                selectedTab = LibraryTab.RomSystem("rom_snes", "snes", "SNES"),
                romFolders = listOf(snesFolder),
            )
        assertEquals(4, actions.size)
    }

    @Test
    fun testBuildLibraryMenuActionsNoAppNonRetroArchWithRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: no app with ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = null,
                selectedTab = LibraryTab.GAMES,
                romFolders = listOf(pcFolder),
            )
        assertEquals(3, actions.size)
    }

    @Test
    fun testBuildLibraryMenuActionsNoAppWithoutRomFolders() {
        AppLog.d(TAG, "Testing library menu actions: no app without ROM folders")
        val actions =
            buildLibraryMenuActions(
                focusedApp = null,
                selectedTab = LibraryTab.GAMES,
                romFolders = emptyList(),
            )
        assertEquals(2, actions.size)
    }

    @Test
    fun testBuildGalleryMenuActionsWithoutApp() {
        AppLog.d(TAG, "Testing gallery menu actions: empty gallery shows only Manage Galleries")
        var manageGalleriesOpened = false
        val actions =
            buildGalleryMenuActions(
                currentApp = null,
                isPaired = false,
                onOpenManageGalleries = { manageGalleriesOpened = true },
            )
        assertEquals(1, actions.size)
        actions.last().onClick()
        assertTrue(manageGalleriesOpened)
    }

    @Test
    fun testBuildGalleryMenuActionsUnpairedApp() {
        AppLog.d(TAG, "Testing gallery menu actions: unpaired app")
        val actions =
            buildGalleryMenuActions(
                currentApp = nativeApp,
                isPaired = false,
            )
        assertEquals(5, actions.size)
        assertEquals("view_carousel", actions.last().iconSymbol)
    }

    @Test
    fun testBuildGalleryMenuActionsPairedApp() {
        AppLog.d(TAG, "Testing gallery menu actions: paired app")
        var removedPkg: String? = null
        val actions =
            buildGalleryMenuActions(
                currentApp = nativeApp,
                isPaired = true,
                onRemovePair = { removedPkg = it.packageName },
            )
        assertEquals(6, actions.size)
        assertTrue(actions[4].isDestructive)
        actions[4].onClick()
        assertEquals(nativeApp.packageName, removedPkg)
    }
}
