package com.stormpanda.megingiard.gamefocus

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testShouldHandlePackageBroadcast_skipsIntermediateReplacingEvents() {
        assertTrue(shouldHandlePackageBroadcast(Intent.ACTION_PACKAGE_ADDED, isReplacing = false))
        assertFalse(shouldHandlePackageBroadcast(Intent.ACTION_PACKAGE_ADDED, isReplacing = true))
        assertTrue(shouldHandlePackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, isReplacing = false))
        assertFalse(shouldHandlePackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, isReplacing = true))
        assertTrue(shouldHandlePackageBroadcast(Intent.ACTION_PACKAGE_REPLACED, isReplacing = true))
        assertTrue(shouldHandlePackageBroadcast(Intent.ACTION_PACKAGE_REPLACED, isReplacing = false))
        assertTrue(shouldHandlePackageBroadcast(Intent.ACTION_PACKAGE_CHANGED, isReplacing = false))
        assertFalse(shouldHandlePackageBroadcast(Intent.ACTION_MAIN, isReplacing = false))
        assertFalse(shouldHandlePackageBroadcast(null, isReplacing = false))
    }

    @Test
    fun testIsScreenReaderFeedbackMask_distinguishesScreenReadersFromGenericMonitors() {
        assertFalse(isScreenReaderFeedbackMask(0))
        assertFalse(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_GENERIC))
        assertFalse(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_HAPTIC))
        assertTrue(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_SPOKEN))
        assertTrue(isScreenReaderFeedbackMask(AccessibilityServiceInfo.FEEDBACK_BRAILLE))
        assertTrue(
            isScreenReaderFeedbackMask(
                AccessibilityServiceInfo.FEEDBACK_GENERIC or AccessibilityServiceInfo.FEEDBACK_SPOKEN,
            ),
        )
    }

    @Test
    fun testIsScreenReaderService_requiresWindowContentCapability() {
        // OEM foreground monitor declaring feedbackAllMask (-1) with capabilities = 0 (e.g. Retroid Game Assistant)
        assertFalse(
            isScreenReaderService(
                feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                capabilities = 0,
            ),
        )
        // Generic window monitor WITH window content capability (e.g. MegingiardAccessibilityService)
        assertFalse(
            isScreenReaderService(
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC,
                capabilities = AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT,
            ),
        )
        // Spoken screen reader (e.g. TalkBack) with window content capability
        assertTrue(
            isScreenReaderService(
                feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN,
                capabilities = AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT,
            ),
        )
        // Braille screen reader with window content capability
        assertTrue(
            isScreenReaderService(
                feedbackType = AccessibilityServiceInfo.FEEDBACK_BRAILLE,
                capabilities = AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT,
            ),
        )
    }
}
