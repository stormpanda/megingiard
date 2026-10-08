package com.stormpanda.megingiard.gamefocus.viewmodel

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.gamefocus.GameFocusCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TAG = "FocusTopLauncherViewModelTest"

class FocusTopLauncherViewModelTest {
    private lateinit var viewModel: FocusTopLauncherViewModel

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up FocusTopLauncherViewModelTest")
        viewModel = FocusTopLauncherViewModel()
    }

    @Test
    fun testInitialState() {
        assertEquals(GameFocusCategory.LAST_USED, viewModel.selectedCategory.value)
        assertFalse(viewModel.isMainOptionsMenuExpanded.value)
        assertFalse(viewModel.isLibraryOpen.value)
        assertEquals(LibraryTab.GAMES, viewModel.librarySelectedTab.value)
        assertEquals(0, viewModel.libraryFocusedIndex.value)
        assertNull(viewModel.editingAppInfo.value)
        assertNull(viewModel.focusedApp.value)
        assertNull(viewModel.newlyAddedFolder.value)
        assertFalse(viewModel.isRemoveRomFolderDialogOpen.value)
    }

    @Test
    fun testCategoryNavigation() {
        val categories = GameFocusCategory.builtIns

        viewModel.setSelectedCategory(GameFocusCategory.GAMES)
        assertEquals(GameFocusCategory.GAMES, viewModel.selectedCategory.value)

        assertTrue(viewModel.cycleCategoryDown(categories))
        assertEquals(GameFocusCategory.APPS, viewModel.selectedCategory.value)

        assertTrue(viewModel.cycleCategoryUp(categories))
        assertEquals(GameFocusCategory.GAMES, viewModel.selectedCategory.value)

        // Single-item list boundary test: cycling does not change category and returns false
        val singleCategoryList = listOf(GameFocusCategory.GAMES)
        assertFalse(viewModel.cycleCategoryDown(singleCategoryList))
        assertEquals(GameFocusCategory.GAMES, viewModel.selectedCategory.value)
        assertFalse(viewModel.cycleCategoryUp(singleCategoryList))
        assertEquals(GameFocusCategory.GAMES, viewModel.selectedCategory.value)
    }

    @Test
    fun testLibraryTabNavigation() {
        val tabs = listOf(LibraryTab.GAMES, LibraryTab.APPS)

        viewModel.setLibrarySelectedTab(LibraryTab.GAMES)
        assertEquals(LibraryTab.GAMES, viewModel.librarySelectedTab.value)

        assertTrue(viewModel.cycleLibraryTabDown(tabs))
        assertEquals(LibraryTab.APPS, viewModel.librarySelectedTab.value)

        assertTrue(viewModel.cycleLibraryTabUp(tabs))
        assertEquals(LibraryTab.GAMES, viewModel.librarySelectedTab.value)

        // Single-tab list boundary test: cycling does not change tab and returns false
        val singleTabList = listOf(LibraryTab.GAMES)
        assertFalse(viewModel.cycleLibraryTabDown(singleTabList))
        assertEquals(LibraryTab.GAMES, viewModel.librarySelectedTab.value)
        assertFalse(viewModel.cycleLibraryTabUp(singleTabList))
        assertEquals(LibraryTab.GAMES, viewModel.librarySelectedTab.value)
    }

    @Test
    fun testMainOptionsMenuToggle() {
        assertFalse(viewModel.isMainOptionsMenuExpanded.value)

        viewModel.toggleMainOptionsMenu()
        assertTrue(viewModel.isMainOptionsMenuExpanded.value)

        viewModel.setMainOptionsMenuExpanded(false)
        assertFalse(viewModel.isMainOptionsMenuExpanded.value)
    }

    @Test
    fun testEditGameOverlayOpenAndDismiss() {
        val app =
            InstalledAppInfo(
                packageName = "com.test.app",
                activityName = "MainActivity",
                label = "Test App",
                isGame = true,
                isRom = false,
            )

        viewModel.openEditGameOverlay(app)
        assertEquals(app, viewModel.editingAppInfo.value)

        viewModel.setEditingAppInfo(null)
        assertNull(viewModel.editingAppInfo.value)
    }

    @Test
    fun testTriggersIncrement() {
        assertEquals(0, viewModel.prevLetterTrigger.value)
        viewModel.triggerPrevLetter()
        assertEquals(1, viewModel.prevLetterTrigger.value)

        assertEquals(0, viewModel.nextLetterTrigger.value)
        viewModel.triggerNextLetter()
        assertEquals(1, viewModel.nextLetterTrigger.value)

        assertEquals(0, viewModel.dpadLeftTrigger.value)
        viewModel.triggerDpadLeft()
        assertEquals(1, viewModel.dpadLeftTrigger.value)

        assertEquals(0, viewModel.dpadStepRightTrigger.value)
        viewModel.triggerDpadStepRight()
        assertEquals(1, viewModel.dpadStepRightTrigger.value)
    }

    @Test
    fun testRomFolderDialogState() {
        val folder =
            CustomRomFolder(
                uriString = "content://test",
                folderPath = "/storage/emulated/0/ROMS/snes",
                systemId = "snes",
                systemName = "Super Nintendo",
            )

        viewModel.setNewlyAddedFolder(folder)
        assertEquals(folder, viewModel.newlyAddedFolder.value)

        viewModel.setFolderToRemove(folder)
        assertEquals(folder, viewModel.folderToRemove.value)

        viewModel.setRemoveRomFolderDialogOpen(true)
        assertTrue(viewModel.isRemoveRomFolderDialogOpen.value)

        viewModel.setRemoveRomFolderDialogSelectedIndex(2)
        assertEquals(2, viewModel.removeRomFolderDialogSelectedIndex.value)
    }

    @Test
    fun testLifecycleState() {
        assertFalse(viewModel.isResumed.value)
        assertFalse(viewModel.isStarted.value)

        viewModel.setResumed(true)
        assertTrue(viewModel.isResumed.value)

        viewModel.setStarted(true)
        assertTrue(viewModel.isStarted.value)
    }

    @Test
    fun testStepLibraryFocus() {
        viewModel.setLibraryFocusedIndex(0)

        // Boundary test: stepping left or up at index 0 should return false (no movement)
        assertFalse(viewModel.stepLibraryFocus(LauncherScrollDirection.LEFT, total = 10, columns = 6))
        assertEquals(0, viewModel.libraryFocusedIndex.value)
        assertFalse(viewModel.stepLibraryFocus(LauncherScrollDirection.UP, total = 10, columns = 6))
        assertEquals(0, viewModel.libraryFocusedIndex.value)

        // Step right -> true
        assertTrue(viewModel.stepLibraryFocus(LauncherScrollDirection.RIGHT, total = 10, columns = 6))
        assertEquals(1, viewModel.libraryFocusedIndex.value)

        // Step down -> true
        assertTrue(viewModel.stepLibraryFocus(LauncherScrollDirection.DOWN, total = 10, columns = 6))
        assertEquals(7, viewModel.libraryFocusedIndex.value)

        // Step left -> true
        assertTrue(viewModel.stepLibraryFocus(LauncherScrollDirection.LEFT, total = 10, columns = 6))
        assertEquals(6, viewModel.libraryFocusedIndex.value)

        // Step up -> true
        assertTrue(viewModel.stepLibraryFocus(LauncherScrollDirection.UP, total = 10, columns = 6))
        assertEquals(0, viewModel.libraryFocusedIndex.value)

        // Boundary test at end of grid (total = 10, last index is 9)
        viewModel.setLibraryFocusedIndex(9)
        assertFalse(viewModel.stepLibraryFocus(LauncherScrollDirection.RIGHT, total = 10, columns = 6))
        assertEquals(9, viewModel.libraryFocusedIndex.value)
        assertFalse(viewModel.stepLibraryFocus(LauncherScrollDirection.DOWN, total = 10, columns = 6))
        assertEquals(9, viewModel.libraryFocusedIndex.value)
    }

    @Test
    fun testStepCoreChooserFocus() {
        viewModel.setCoreChooserDialogSelectedIndex(0)

        // Step down with 3 cores -> 1
        viewModel.stepCoreChooserFocus(LauncherScrollDirection.DOWN, coreCount = 3)
        assertEquals(1, viewModel.coreChooserDialogSelectedIndex.value)

        // Step down again -> 2
        viewModel.stepCoreChooserFocus(LauncherScrollDirection.DOWN, coreCount = 3)
        assertEquals(2, viewModel.coreChooserDialogSelectedIndex.value)

        // Step down wraps to 0
        viewModel.stepCoreChooserFocus(LauncherScrollDirection.DOWN, coreCount = 3)
        assertEquals(0, viewModel.coreChooserDialogSelectedIndex.value)

        // Step up wraps to 2
        viewModel.stepCoreChooserFocus(LauncherScrollDirection.UP, coreCount = 3)
        assertEquals(2, viewModel.coreChooserDialogSelectedIndex.value)
    }

    @Test
    fun testStepRemoveRomFolderFocus() {
        viewModel.setRemoveRomFolderDialogSelectedIndex(0)

        // Step down with 2 folders -> 1
        viewModel.stepRemoveRomFolderFocus(LauncherScrollDirection.DOWN, foldersCount = 2)
        assertEquals(1, viewModel.removeRomFolderDialogSelectedIndex.value)

        // Step down wraps to 0
        viewModel.stepRemoveRomFolderFocus(LauncherScrollDirection.DOWN, foldersCount = 2)
        assertEquals(0, viewModel.removeRomFolderDialogSelectedIndex.value)

        // Step up wraps to 1
        viewModel.stepRemoveRomFolderFocus(LauncherScrollDirection.UP, foldersCount = 2)
        assertEquals(1, viewModel.removeRomFolderDialogSelectedIndex.value)
    }

    @Test
    fun testResetToGallery() {
        assertFalse(viewModel.resetToGallery())

        viewModel.setLibraryOpen(true)
        assertTrue(viewModel.resetToGallery())
        assertFalse(viewModel.isLibraryOpen.value)

        viewModel.setMainOptionsMenuExpanded(true)
        assertTrue(viewModel.resetToGallery())
        assertFalse(viewModel.isMainOptionsMenuExpanded.value)
    }

    @Test
    fun testMainMenuNavigation() {
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)

        viewModel.navigateMainMenuDown(4)
        assertEquals(1, viewModel.mainMenuSelectedIndex.value)

        viewModel.navigateMainMenuDown(4)
        assertEquals(2, viewModel.mainMenuSelectedIndex.value)

        viewModel.navigateMainMenuDown(4)
        assertEquals(3, viewModel.mainMenuSelectedIndex.value)

        // Wrap around to 0
        viewModel.navigateMainMenuDown(4)
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)

        // Wrap around to 3
        viewModel.navigateMainMenuUp(4)
        assertEquals(3, viewModel.mainMenuSelectedIndex.value)

        viewModel.navigateMainMenuUp(4)
        assertEquals(2, viewModel.mainMenuSelectedIndex.value)

        // Opening menu resets index to 0
        viewModel.setMainOptionsMenuExpanded(true)
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)
    }

    @Test
    fun testLibraryMenuNavigation() {
        assertEquals(0, viewModel.libraryMenuSelectedIndex.value)

        viewModel.navigateLibraryMenuDown(3)
        assertEquals(1, viewModel.libraryMenuSelectedIndex.value)

        viewModel.navigateLibraryMenuDown(3)
        assertEquals(2, viewModel.libraryMenuSelectedIndex.value)

        // Wrap around to 0
        viewModel.navigateLibraryMenuDown(3)
        assertEquals(0, viewModel.libraryMenuSelectedIndex.value)

        // Wrap around to 2
        viewModel.navigateLibraryMenuUp(3)
        assertEquals(2, viewModel.libraryMenuSelectedIndex.value)

        // Opening menu resets index to 0
        viewModel.setLibraryOptionsMenuExpanded(true)
        assertEquals(0, viewModel.libraryMenuSelectedIndex.value)
    }

    @Test
    fun testResetToGalleryClosesEditOverlay() {
        val app =
            InstalledAppInfo(
                packageName = "com.test.app2",
                activityName = "MainActivity",
                label = "Test App 2",
                isGame = true,
                isRom = false,
            )
        viewModel.openEditGameOverlay(app)

        assertTrue(viewModel.resetToGallery())
        assertNull(viewModel.editingAppInfo.value)
    }

    @Test
    fun testResetToGalleryResetsMenuIndices() {
        viewModel.setMainMenuSelectedIndex(2)
        viewModel.setLibraryMenuSelectedIndex(1)
        viewModel.setMainOptionsMenuExpanded(true)

        assertTrue(viewModel.resetToGallery())
        assertFalse(viewModel.isMainOptionsMenuExpanded.value)
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)
        assertEquals(0, viewModel.libraryMenuSelectedIndex.value)
    }

    @Test
    fun testButtonPromptsVisibilityToggle() {
        assertTrue(viewModel.areButtonPromptsVisible.value)

        val toggledFalse = viewModel.toggleButtonPromptsVisible()
        assertFalse(toggledFalse)
        assertFalse(viewModel.areButtonPromptsVisible.value)

        val toggledTrue = viewModel.toggleButtonPromptsVisible()
        assertTrue(toggledTrue)
        assertTrue(viewModel.areButtonPromptsVisible.value)

        viewModel.setButtonPromptsVisible(false)
        assertFalse(viewModel.areButtonPromptsVisible.value)
    }
}
