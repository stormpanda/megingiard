package com.stormpanda.megingiard.gamefocus

import android.view.KeyEvent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.gamefocus.viewmodel.FocusTopLauncherViewModel
import com.stormpanda.megingiard.gamefocus.viewmodel.LauncherScrollDirection
import com.stormpanda.megingiard.ui.GamePadButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "GFDispatcherTest"
private const val ONE_MS = 1L
private const val SHORT_TAP_GAP_MS = 40L
private const val QUICK_RELEASE_MS = 20L
private const val REMAINING_AFTER_DOUBLE_TAP_MS = 55L
private const val MID_TOGGLE_WAIT_MS = 150L
private const val POST_RELEASE_QUIET_MS = 500L
private const val MENU_ACTION_COUNT = 4
private const val DIAGONAL_LEFT_X = -0.85f
private const val DIAGONAL_DOWN_Y = 0.85f
private const val NEUTRAL_AXIS = 0f
private const val FULL_DOWN_Y = 0.9f
private const val REPEAT_COUNT_FIRST = 1
private const val REPEAT_COUNT_HELD = 5

@OptIn(ExperimentalCoroutinesApi::class)
class GameFocusControllerDispatcherTest {
    @Test
    fun galleryRightHoldRepeatFollowsInitialDelayAndIntervalCadence() =
        runTest {
            AppLog.d(TAG, "Testing gallery horizontal hold-repeat cadence")
            val harness = createHarness()
            try {
                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0))
                assertEquals(1, harness.viewModel.dpadStepRightTrigger.value)
                assertEquals(1, harness.host.tickHaptics)

                advanceMs(ITEM_INITIAL_HOLD_DELAY_MS - ONE_MS)
                assertEquals(1, harness.viewModel.dpadStepRightTrigger.value)

                advanceMs(ONE_MS)
                assertEquals(2, harness.viewModel.dpadStepRightTrigger.value)
                assertEquals(2, harness.host.tickHaptics)

                advanceMs(ITEM_REPEAT_INTERVAL_MS - ONE_MS)
                assertEquals(2, harness.viewModel.dpadStepRightTrigger.value)

                advanceMs(ONE_MS)
                assertEquals(3, harness.viewModel.dpadStepRightTrigger.value)

                assertTrue(harness.dispatcher.handleKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT))
                advanceMs(POST_RELEASE_QUIET_MS)
                assertEquals(3, harness.viewModel.dpadStepRightTrigger.value)
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    @Test
    fun rapidDoubleTapIsAbsorbedWhileTapThenHoldFiresAtExactRemainingCooldown() =
        runTest {
            AppLog.d(TAG, "Testing rapid double-tap absorption vs tap-then-hold remainingCooldownMs deferral")
            val harness = createHarness()
            try {
                // Initial tap at t = 0 ms executes immediately
                harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0)
                harness.dispatcher.handleKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals(1, harness.viewModel.dpadStepRightTrigger.value)

                // Second quick tap inside 115 ms window released before expiry is absorbed
                advanceMs(SHORT_TAP_GAP_MS)
                harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0)
                advanceMs(QUICK_RELEASE_MS)
                harness.dispatcher.handleKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals(1, harness.viewModel.dpadStepRightTrigger.value)

                // Holding during the remaining 55 ms cooldown defers step 1 to exact cooldown expiry (t = 115 ms)
                harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0)
                assertEquals(1, harness.viewModel.dpadStepRightTrigger.value)

                advanceMs(REMAINING_AFTER_DOUBLE_TAP_MS - ONE_MS)
                assertEquals(1, harness.viewModel.dpadStepRightTrigger.value)

                advanceMs(ONE_MS)
                assertEquals(2, harness.viewModel.dpadStepRightTrigger.value)

                advanceMs(ITEM_REPEAT_INTERVAL_MS)
                assertEquals(3, harness.viewModel.dpadStepRightTrigger.value)
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    @Test
    fun crossChannelAxisSwitchExecutesWithZeroLatency() =
        runTest {
            AppLog.d(TAG, "Testing zero-latency cross-channel switching from category roll to horizontal and letter steps")
            val harness = createHarness()
            try {
                harness.viewModel.setSelectedCategory(GameFocusCategory.GAMES)

                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(KeyEvent.KEYCODE_DPAD_DOWN))
                assertEquals(GameFocusCategory.APPS, harness.viewModel.selectedCategory.value)
                assertEquals(1, harness.host.clickHaptics)

                // Zero time elapsed: switching to ITEM_HORIZONTAL and GALLERY_LETTER fires immediately
                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT))
                assertEquals(1, harness.viewModel.dpadStepRightTrigger.value)

                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R1.keyCode, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_R1.keyCode))
                assertEquals(1, harness.viewModel.nextLetterTrigger.value)
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    @Test
    fun r2ToggleEnforcesOneShotAnd400msCooldownWhileBackClosesImmediately() =
        runTest {
            AppLog.d(TAG, "Testing R2 one-shot hold suppression, 400ms cooldown, and immediate Back close")
            val harness = createHarness()
            try {
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = 0))
                assertTrue(harness.viewModel.isLibraryOpen.value)

                // Holding R2 past 400 ms never re-toggles Library (one-shot per physical press)
                advanceMs(POST_RELEASE_QUIET_MS)
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = REPEAT_COUNT_FIRST))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = REPEAT_COUNT_HELD))
                assertTrue(harness.viewModel.isLibraryOpen.value)
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_R2.keyCode))

                // Close via R2 after cooldown, then verify rapid re-press inside 400 ms is rejected
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_R2.keyCode))
                assertFalse(harness.viewModel.isLibraryOpen.value)

                advanceMs(MID_TOGGLE_WAIT_MS)
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_R2.keyCode))
                assertFalse(harness.viewModel.isLibraryOpen.value)

                advanceMs(LIBRARY_TOGGLE_COOLDOWN_MS)
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_R2.keyCode))
                assertTrue(harness.viewModel.isLibraryOpen.value)

                // Back/B inside Library dismisses immediately even during active R2 cooldown and commits LIBRARY_TOGGLE
                advanceMs(SHORT_TAP_GAP_MS)
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_B.keyCode, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_B.keyCode))
                assertFalse(harness.viewModel.isLibraryOpen.value)

                // Immediate R2 re-open after Back dismissal is guarded by the committed 400 ms cooldown
                advanceMs(MID_TOGGLE_WAIT_MS)
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = 0))
                assertFalse(harness.viewModel.isLibraryOpen.value)

                advanceMs(LIBRARY_TOGGLE_COOLDOWN_MS)
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R2.keyCode, repeatCount = 0))
                assertTrue(harness.viewModel.isLibraryOpen.value)
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    @Test
    fun osKeyRepeatGateSuppressesGamepadHoldsWhilePassingSystemVolumeThrough() =
        runTest {
            AppLog.d(TAG, "Testing OS repeatCount > 0 suppression vs system volume pass-through")
            val harness = createHarness()
            try {
                harness.viewModel.setFocusedApp(harness.host.galleryApps.first())

                // Held gamepad action keys are consumed without re-triggering launches, menus, or Home resets
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_A.keyCode, repeatCount = REPEAT_COUNT_FIRST))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_X.keyCode, repeatCount = REPEAT_COUNT_FIRST))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_Y.keyCode, repeatCount = REPEAT_COUNT_FIRST))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_B.keyCode, repeatCount = REPEAT_COUNT_FIRST))
                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_HOME, repeatCount = REPEAT_COUNT_FIRST))
                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_BUTTON_THUMBR, repeatCount = REPEAT_COUNT_FIRST))

                assertTrue(harness.host.launchedGalleryTopApps.isEmpty())
                assertTrue(harness.host.launchedBottomApps.isEmpty())
                assertFalse(harness.viewModel.isMainOptionsMenuExpanded.value)
                assertEquals(0, harness.host.galleryDismissCount)
                assertEquals(0, harness.host.homeNavigationCount)
                assertEquals(0, harness.host.promptToggleCount)

                // System volume keys return false on the root Gallery for both initial press and hold-repeats
                assertFalse(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 0))
                assertFalse(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_VOLUME_UP, repeatCount = REPEAT_COUNT_FIRST))
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    @Test
    fun primaryOverlayDelegatesNavigationExceptDismissAndButtonAFocusRecovery() =
        runTest {
            AppLog.d(TAG, "Testing primary overlay key delegation and Button A focus recovery")
            val harness = createHarness()
            try {
                harness.viewModel.openEditGameOverlay(harness.host.galleryApps.first())
                assertTrue(harness.dispatcher.isPrimaryOverlayOpen())
                assertTrue(harness.dispatcher.consumesUnhandledMotionEvents())

                // Directional keys return false so Compose overlay focus traversal handles them
                assertFalse(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0))

                // Button A dispatches synthetic DPAD_CENTER down once and recovers focus when up is unhandled
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_A.keyCode, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_A.keyCode, repeatCount = REPEAT_COUNT_FIRST))
                assertEquals(1, harness.host.primaryOverlayCenterDownCount)

                harness.host.primaryOverlayCenterUpHandled = false
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_A.keyCode))
                assertEquals(listOf(GamePadButton.BUTTON_A.keyCode), harness.host.focusRecoveryKeys)

                harness.host.primaryOverlayCenterUpHandled = true
                assertTrue(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_A.keyCode))
                assertEquals(1, harness.host.focusRecoveryKeys.size)

                // Back/B invokes primary overlay dismiss once per press
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_B.keyCode, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_B.keyCode, repeatCount = REPEAT_COUNT_FIRST))
                assertEquals(1, harness.host.primaryOverlayBackCount)
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    @Test
    fun verticalMenusAndSecondaryModalsRouteDirectionsAndDiagonalStickCleanly() =
        runTest {
            AppLog.d(TAG, "Testing vertical options menu diagonal stick routing, pairing grid, and folder removal modal")
            val harness = createHarness()
            try {
                // Open Gallery options menu via Y
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_Y.keyCode, repeatCount = 0))
                assertTrue(harness.viewModel.isMainOptionsMenuExpanded.value)

                // Horizontal keys and L1/R1 are ignored in vertical menus
                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R1.keyCode, repeatCount = 0))
                assertEquals(0, harness.viewModel.dpadLeftTrigger.value)
                assertEquals(0, harness.viewModel.nextLetterTrigger.value)

                // Diagonal stick (-0.85, +0.85) resolves to vertical DOWN in vertical-only menu
                assertTrue(harness.dispatcher.handleJoystickAxes(DIAGONAL_LEFT_X, DIAGONAL_DOWN_Y))
                assertEquals(1, harness.viewModel.mainMenuSelectedIndex.value)
                assertTrue(harness.dispatcher.handleJoystickAxes(NEUTRAL_AXIS, NEUTRAL_AXIS))

                // Confirm menu item 1 with Button A
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_A.keyCode, repeatCount = 0))
                assertEquals(listOf(1), harness.host.invokedGalleryMenuIndices)
                assertFalse(harness.viewModel.isMainOptionsMenuExpanded.value)

                // Pairing dialog routes horizontal navigation and confirms selected Android app
                val targetApp = harness.host.galleryApps.first()
                harness.viewModel.openPairingDialog(targetApp)
                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0))
                assertTrue(harness.dispatcher.handleKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT))
                assertEquals(1, harness.viewModel.pairingFocusedIndex.value)
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_A.keyCode, repeatCount = 0))
                assertEquals(Pair(targetApp, harness.host.androidApps[1]), harness.host.confirmedPair)
                assertNull(harness.viewModel.pairingTargetApp.value)

                // Folder removal confirmation disables repeat/joystick stepping and confirms removal on Button A
                val folder =
                    CustomRomFolder(
                        uriString = "content://roms/snes",
                        folderPath = "/storage/emulated/0/ROMS/snes",
                        systemId = "snes",
                        systemName = "Super Nintendo",
                    )
                harness.viewModel.setFolderToRemove(folder)
                assertTrue(harness.dispatcher.handleJoystickAxes(NEUTRAL_AXIS, FULL_DOWN_Y))
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_A.keyCode, repeatCount = 0))
                assertEquals(listOf(folder), harness.host.removedFolders)
                assertNull(harness.viewModel.folderToRemove.value)
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    @Test
    fun primaryOverlayAndGalleryDismissStopActiveRepeatsCleanly() =
        runTest {
            AppLog.d(TAG, "Testing L1/R1 release while primary overlay is open, overlay repeat guard, and gallery dismiss stopRepeat")
            val harness = createHarness()
            try {
                // Start R1 hold-repeat on Gallery
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_R1.keyCode, repeatCount = 0))
                assertEquals(1, harness.viewModel.nextLetterTrigger.value)

                // Touch-opening Edit Game primary overlay while R1 is held:
                // releasing R1 must cancel repeat AND return false so overlay key-up handling is unaffected
                harness.viewModel.openEditGameOverlay(harness.host.galleryApps.first())
                assertFalse(harness.dispatcher.handleKeyUp(GamePadButton.BUTTON_R1.keyCode))

                // Close overlay and advance past hold delay -> cancelled R1 repeat must NOT fire
                harness.viewModel.setEditingAppInfo(null)
                advanceMs(POST_RELEASE_QUIET_MS)
                assertEquals(1, harness.viewModel.nextLetterTrigger.value)

                // Joystick deflection while primary overlay is open must not step category or start repeat
                val categoryBeforeOverlay = harness.viewModel.selectedCategory.value
                harness.viewModel.setManageGalleriesOpen(true)
                assertTrue(harness.dispatcher.handleJoystickAxes(NEUTRAL_AXIS, FULL_DOWN_Y))
                harness.viewModel.setManageGalleriesOpen(false)
                advanceMs(POST_RELEASE_QUIET_MS)
                assertEquals(categoryBeforeOverlay, harness.viewModel.selectedCategory.value)

                // Pressing Dismiss/Back on main gallery while holding D-pad RIGHT stops repeat immediately
                assertTrue(harness.dispatcher.handleKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0))
                val stepsBeforeDismiss = harness.viewModel.dpadStepRightTrigger.value
                assertTrue(harness.dispatcher.handleKeyDown(GamePadButton.BUTTON_B.keyCode, repeatCount = 0))
                assertEquals(1, harness.host.galleryDismissCount)
                advanceMs(POST_RELEASE_QUIET_MS)
                assertEquals(stepsBeforeDismiss, harness.viewModel.dpadStepRightTrigger.value)
            } finally {
                harness.dispatcher.stopRepeat()
            }
        }

    private fun TestScope.createHarness(): DispatcherHarness {
        val pacer = GameFocusInputPacer { testScheduler.currentTime }
        val viewModel = FocusTopLauncherViewModel(pacer)
        val host = FakeControllerHost()
        val dispatcher = GameFocusControllerDispatcher(viewModel = viewModel, scope = this, host = host)
        return DispatcherHarness(viewModel = viewModel, host = host, dispatcher = dispatcher)
    }

    private fun TestScope.advanceMs(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }
}

private class DispatcherHarness(
    val viewModel: FocusTopLauncherViewModel,
    val host: FakeControllerHost,
    val dispatcher: GameFocusControllerDispatcher,
)

private class FakeControllerHost : GameFocusControllerHost {
    val galleryApps: List<InstalledAppInfo> =
        listOf(
            InstalledAppInfo(packageName = "com.game.one", activityName = "Main", label = "Alpha", isGame = true),
            InstalledAppInfo(packageName = "com.game.two", activityName = "Main", label = "Beta", isGame = true),
            InstalledAppInfo(packageName = "com.game.three", activityName = "Main", label = "Gamma", isGame = true),
        )
    val androidApps: List<InstalledAppInfo> =
        listOf(
            InstalledAppInfo(packageName = "com.companion.one", activityName = "Main", label = "Browser", isGame = false),
            InstalledAppInfo(packageName = "com.companion.two", activityName = "Main", label = "Notes", isGame = false),
        )

    override val activeCategories: List<GameFocusCategory> = GameFocusCategory.builtIns
    override val activeLibraryTabs: List<LibraryTab> = listOf(LibraryTab.GAMES, LibraryTab.APPS)

    var tickHaptics: Int = 0
    var clickHaptics: Int = 0
    var promptToggleCount: Int = 0
    var homeNavigationCount: Int = 0
    var galleryDismissCount: Int = 0
    var primaryOverlayBackCount: Int = 0
    var primaryOverlayCenterDownCount: Int = 0
    var primaryOverlayCenterUpHandled: Boolean = true
    val focusRecoveryKeys: MutableList<Int> = mutableListOf()
    val invokedGalleryMenuIndices: MutableList<Int> = mutableListOf()
    val invokedLibraryMenuIndices: MutableList<Int> = mutableListOf()
    val removedFolders: MutableList<CustomRomFolder> = mutableListOf()
    var confirmedPair: Pair<InstalledAppInfo, InstalledAppInfo>? = null
    val launchedGalleryTopApps: MutableList<InstalledAppInfo> = mutableListOf()
    val launchedLibraryTopApps: MutableList<InstalledAppInfo> = mutableListOf()
    val launchedBottomApps: MutableList<InstalledAppInfo> = mutableListOf()

    override fun currentGalleryApps(): List<InstalledAppInfo> = galleryApps

    override fun currentLibraryApps(): List<InstalledAppInfo> = galleryApps

    override fun currentAvailableAndroidApps(): List<InstalledAppInfo> = androidApps

    override fun romFolders(): List<CustomRomFolder> = emptyList()

    override fun galleryMenuActionCount(): Int = MENU_ACTION_COUNT

    override fun invokeGalleryMenuAction(index: Int) {
        invokedGalleryMenuIndices += index
    }

    override fun libraryMenuActionCount(focusedApp: InstalledAppInfo?): Int = MENU_ACTION_COUNT

    override fun invokeLibraryMenuAction(
        focusedApp: InstalledAppInfo?,
        index: Int,
    ) {
        invokedLibraryMenuIndices += index
    }

    override fun stepCoreChooserFocus(direction: LauncherScrollDirection): Boolean = true

    override fun stepRemoveRomFolderFocus(direction: LauncherScrollDirection): Boolean = true

    override fun removeRomFolder(folder: CustomRomFolder) {
        removedFolders += folder
    }

    override fun confirmAppPair(
        targetApp: InstalledAppInfo,
        chosenApp: InstalledAppInfo,
    ) {
        confirmedPair = Pair(targetApp, chosenApp)
    }

    override fun launchGalleryTopApp(appInfo: InstalledAppInfo) {
        launchedGalleryTopApps += appInfo
    }

    override fun launchLibraryTopApp(appInfo: InstalledAppInfo) {
        launchedLibraryTopApps += appInfo
    }

    override fun launchBottomApp(
        appInfo: InstalledAppInfo,
        source: String,
    ) {
        launchedBottomApps += appInfo
    }

    override fun toggleButtonPrompts() {
        promptToggleCount++
    }

    override fun onHomeNavigation() {
        homeNavigationCount++
    }

    override fun onGalleryDismiss() {
        galleryDismissCount++
    }

    override fun onPrimaryOverlayBack() {
        primaryOverlayBackCount++
    }

    override fun dispatchPrimaryOverlayCenterDown() {
        primaryOverlayCenterDownCount++
    }

    override fun dispatchPrimaryOverlayCenterUp(): Boolean = primaryOverlayCenterUpHandled

    override fun sendPrimaryOverlayFocusRecovery(keyCode: Int) {
        focusRecoveryKeys += keyCode
    }

    override fun playTickHaptic() {
        tickHaptics++
    }

    override fun playClickHaptic() {
        clickHaptics++
    }
}
