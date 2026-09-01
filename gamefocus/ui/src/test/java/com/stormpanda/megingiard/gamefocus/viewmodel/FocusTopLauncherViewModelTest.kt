package com.stormpanda.megingiard.gamefocus.viewmodel

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
        viewModel = FocusTopLauncherViewModel()
    }

    @Test
    fun testInitialState() {
        assertEquals(GameFocusCategory.LAST_USED, viewModel.selectedCategory.value)
        assertFalse(viewModel.isMainOptionsMenuExpanded.value)
        assertFalse(viewModel.isOptionsMenuExpanded.value)
        assertFalse(viewModel.isLibraryOpen.value)
        assertEquals(LibraryTab.GAMES, viewModel.librarySelectedTab.value)
        assertEquals(0, viewModel.libraryFocusedIndex.value)
        assertNull(viewModel.editingAppInfo.value)
        assertNull(viewModel.focusedApp.value)
        assertNull(viewModel.newlyAddedFolder.value)
        assertFalse(viewModel.isRemoveRomFolderDialogOpen.value)
        assertEquals(INITIAL_LOOP_OFFSET, viewModel.dialogVirtualIndex.value)
    }

    @Test
    fun testCategoryNavigation() {
        val categories = GameFocusCategory.builtIns

        viewModel.setSelectedCategory(GameFocusCategory.GAMES)
        assertEquals(GameFocusCategory.GAMES, viewModel.selectedCategory.value)

        viewModel.cycleCategoryDown(categories)
        assertEquals(GameFocusCategory.APPS, viewModel.selectedCategory.value)

        viewModel.cycleCategoryUp(categories)
        assertEquals(GameFocusCategory.GAMES, viewModel.selectedCategory.value)
    }

    @Test
    fun testLibraryTabNavigation() {
        val tabs = listOf(LibraryTab.GAMES, LibraryTab.APPS)

        viewModel.setLibrarySelectedTab(LibraryTab.GAMES)
        assertEquals(LibraryTab.GAMES, viewModel.librarySelectedTab.value)

        viewModel.cycleLibraryTabDown(tabs)
        assertEquals(LibraryTab.APPS, viewModel.librarySelectedTab.value)

        viewModel.cycleLibraryTabUp(tabs)
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
    fun testOptionsMenuToggle() {
        assertFalse(viewModel.isOptionsMenuExpanded.value)

        viewModel.toggleOptionsMenu()
        assertTrue(viewModel.isOptionsMenuExpanded.value)

        viewModel.setOptionsMenuExpanded(false)
        assertFalse(viewModel.isOptionsMenuExpanded.value)
    }

    @Test
    fun testArtworkDialogOpen() {
        val app =
            InstalledAppInfo(
                packageName = "com.test.app",
                activityName = "MainActivity",
                label = "Test App",
                isGame = true,
                isRom = false,
            )

        viewModel.setOptionsMenuExpanded(true)
        viewModel.openArtworkDialog(app)

        assertEquals(app, viewModel.editingAppInfo.value)
        assertFalse(viewModel.isOptionsMenuExpanded.value)
        assertEquals(INITIAL_LOOP_OFFSET, viewModel.dialogVirtualIndex.value)
        assertEquals(0, viewModel.confirmDialogTrigger.value)
        assertEquals(0, viewModel.dialogL1Trigger.value)
        assertEquals(0, viewModel.dialogR1Trigger.value)
    }

    @Test
    fun testTriggersIncrement() {
        assertEquals(0, viewModel.confirmDialogTrigger.value)
        viewModel.triggerConfirmDialog()
        assertEquals(1, viewModel.confirmDialogTrigger.value)

        assertEquals(0, viewModel.dialogL1Trigger.value)
        viewModel.triggerDialogL1()
        assertEquals(1, viewModel.dialogL1Trigger.value)

        assertEquals(0, viewModel.dialogR1Trigger.value)
        viewModel.triggerDialogR1()
        assertEquals(1, viewModel.dialogR1Trigger.value)

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

        // Step right
        viewModel.stepLibraryFocus(LauncherScrollDirection.RIGHT, total = 10, columns = 6)
        assertEquals(1, viewModel.libraryFocusedIndex.value)

        // Step down
        viewModel.stepLibraryFocus(LauncherScrollDirection.DOWN, total = 10, columns = 6)
        assertEquals(7, viewModel.libraryFocusedIndex.value)

        // Step left
        viewModel.stepLibraryFocus(LauncherScrollDirection.LEFT, total = 10, columns = 6)
        assertEquals(6, viewModel.libraryFocusedIndex.value)

        // Step up
        viewModel.stepLibraryFocus(LauncherScrollDirection.UP, total = 10, columns = 6)
        assertEquals(0, viewModel.libraryFocusedIndex.value)
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
    fun testStepArtworkDialogVirtualIndex() {
        viewModel.setDialogVirtualIndex(INITIAL_LOOP_OFFSET)

        viewModel.stepArtworkDialogVirtualIndex(1)
        assertEquals(INITIAL_LOOP_OFFSET + 1, viewModel.dialogVirtualIndex.value)

        viewModel.stepArtworkDialogVirtualIndex(-2)
        assertEquals(INITIAL_LOOP_OFFSET - 1, viewModel.dialogVirtualIndex.value)
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
}
