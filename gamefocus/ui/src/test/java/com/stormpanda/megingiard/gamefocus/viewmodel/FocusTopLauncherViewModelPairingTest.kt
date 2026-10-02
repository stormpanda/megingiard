package com.stormpanda.megingiard.gamefocus.viewmodel

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TAG = "FocusTopLauncherViewModelPairingTest"
private const val INITIAL_PAIRING_INDEX = 2
private const val TOTAL_GRID_APPS = 12
private const val COLUMNS_COUNT = 5
private const val UNPAIRED_MENU_ITEMS = 4
private const val PAIRED_MENU_ITEMS = 5

class FocusTopLauncherViewModelPairingTest {
    private lateinit var viewModel: FocusTopLauncherViewModel

    private val sampleApp =
        InstalledAppInfo(
            packageName = "com.example.topgame",
            activityName = "MainActivity",
            label = "Top Game",
            isGame = true,
            isRom = false,
        )

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up FocusTopLauncherViewModelPairingTest")
        viewModel = FocusTopLauncherViewModel()
    }

    @Test
    fun testInitialPairingState() {
        assertNull(viewModel.pairingTargetApp.value)
        assertEquals(0, viewModel.pairingFocusedIndex.value)
        assertEquals(0, viewModel.confirmPairingTrigger.value)
    }

    @Test
    fun testOpenAndDismissPairingDialog() {
        viewModel.openPairingDialog(sampleApp, INITIAL_PAIRING_INDEX)
        assertNotNull(viewModel.pairingTargetApp.value)
        assertEquals(sampleApp, viewModel.pairingTargetApp.value)
        assertEquals(INITIAL_PAIRING_INDEX, viewModel.pairingFocusedIndex.value)

        viewModel.dismissPairingDialog()
        assertNull(viewModel.pairingTargetApp.value)
        assertEquals(0, viewModel.pairingFocusedIndex.value)
    }

    @Test
    fun testTriggerConfirmPairing() {
        assertEquals(0, viewModel.confirmPairingTrigger.value)
        viewModel.triggerConfirmPairing()
        assertEquals(1, viewModel.confirmPairingTrigger.value)
        viewModel.triggerConfirmPairing()
        assertEquals(2, viewModel.confirmPairingTrigger.value)
    }

    @Test
    fun testStepPairingFocusLeftRight() {
        viewModel.openPairingDialog(sampleApp, 0)
        assertEquals(0, viewModel.pairingFocusedIndex.value)

        // Stepping left at 0 should remain at 0
        viewModel.stepPairingFocus(LauncherScrollDirection.LEFT, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(0, viewModel.pairingFocusedIndex.value)

        // Step right: 0 -> 1 -> 2
        viewModel.stepPairingFocus(LauncherScrollDirection.RIGHT, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(1, viewModel.pairingFocusedIndex.value)
        viewModel.stepPairingFocus(LauncherScrollDirection.RIGHT, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(2, viewModel.pairingFocusedIndex.value)

        // Step left: 2 -> 1
        viewModel.stepPairingFocus(LauncherScrollDirection.LEFT, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(1, viewModel.pairingFocusedIndex.value)
    }

    @Test
    fun testStepPairingFocusUpDown() {
        viewModel.openPairingDialog(sampleApp, 1)
        assertEquals(1, viewModel.pairingFocusedIndex.value)

        // Stepping up when on top row should stay on top row (cannot go negative)
        viewModel.stepPairingFocus(LauncherScrollDirection.UP, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(1, viewModel.pairingFocusedIndex.value)

        // Stepping down should jump by columns (1 + 5 = 6)
        viewModel.stepPairingFocus(LauncherScrollDirection.DOWN, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(6, viewModel.pairingFocusedIndex.value)

        // Stepping down again (6 + 5 = 11)
        viewModel.stepPairingFocus(LauncherScrollDirection.DOWN, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(11, viewModel.pairingFocusedIndex.value)

        // Stepping down at bottom-most available element should clamp to last valid item or remain
        viewModel.stepPairingFocus(LauncherScrollDirection.DOWN, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(11, viewModel.pairingFocusedIndex.value)

        // Stepping up should jump back by 5 (11 - 5 = 6)
        viewModel.stepPairingFocus(LauncherScrollDirection.UP, TOTAL_GRID_APPS, COLUMNS_COUNT)
        assertEquals(6, viewModel.pairingFocusedIndex.value)
    }

    @Test
    fun testMainMenuNavigationUnpaired4Items() {
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)

        // Navigate down through 4 items: 0 -> 1 -> 2 -> 3
        viewModel.navigateMainMenuDown(UNPAIRED_MENU_ITEMS)
        assertEquals(1, viewModel.mainMenuSelectedIndex.value)
        viewModel.navigateMainMenuDown(UNPAIRED_MENU_ITEMS)
        assertEquals(2, viewModel.mainMenuSelectedIndex.value)
        viewModel.navigateMainMenuDown(UNPAIRED_MENU_ITEMS)
        assertEquals(3, viewModel.mainMenuSelectedIndex.value)

        // Wrap around back to 0
        viewModel.navigateMainMenuDown(UNPAIRED_MENU_ITEMS)
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)

        // Wrap backwards to 3
        viewModel.navigateMainMenuUp(UNPAIRED_MENU_ITEMS)
        assertEquals(3, viewModel.mainMenuSelectedIndex.value)
    }

    @Test
    fun testMainMenuNavigationPaired5Items() {
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)

        // Navigate down through 5 items: 0 -> 1 -> 2 -> 3 -> 4
        viewModel.navigateMainMenuDown(PAIRED_MENU_ITEMS)
        assertEquals(1, viewModel.mainMenuSelectedIndex.value)
        viewModel.navigateMainMenuDown(PAIRED_MENU_ITEMS)
        assertEquals(2, viewModel.mainMenuSelectedIndex.value)
        viewModel.navigateMainMenuDown(PAIRED_MENU_ITEMS)
        assertEquals(3, viewModel.mainMenuSelectedIndex.value)
        viewModel.navigateMainMenuDown(PAIRED_MENU_ITEMS)
        assertEquals(4, viewModel.mainMenuSelectedIndex.value)

        // Wrap around back to 0
        viewModel.navigateMainMenuDown(PAIRED_MENU_ITEMS)
        assertEquals(0, viewModel.mainMenuSelectedIndex.value)

        // Wrap backwards to 4
        viewModel.navigateMainMenuUp(PAIRED_MENU_ITEMS)
        assertEquals(4, viewModel.mainMenuSelectedIndex.value)
    }

    @Test
    fun testResetToGalleryClearsPairingDialog() {
        viewModel.openPairingDialog(sampleApp, 3)
        assertNotNull(viewModel.pairingTargetApp.value)

        val handled = viewModel.resetToGallery()
        assertTrue(handled)
        assertNull(viewModel.pairingTargetApp.value)
        assertEquals(0, viewModel.pairingFocusedIndex.value)
    }
}
