package com.stormpanda.megingiard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.KeyboardSettings
import com.stormpanda.megingiard.settings.MacroPadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateManagerTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dummyDataStore =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = emptyFlow()

                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = emptyPreferences()
            }
        KeyboardSettings.init(dummyDataStore, CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changing active layout closes active modals in AppStateManager`() {
        // Set up initial profile with two layouts
        val layout1Id = UUID.randomUUID().toString()
        val layout2Id = UUID.randomUUID().toString()
        val profileId = UUID.randomUUID().toString()
        val testProfile =
            PadProfile(
                id = profileId,
                name = "Test Profile",
                layouts =
                    listOf(
                        PadLayout(id = layout1Id, name = "Layout 1"),
                        PadLayout(id = layout2Id, name = "Layout 2"),
                    ),
                activeLayoutId = layout1Id,
            )

        // Load into MacroPadState
        MacroPadState.loadFrom(listOf(testProfile), profileId)

        // Verify active layout is Layout 1
        assertTrue(MacroPadState.activeLayout.value?.id == layout1Id)

        // Activate viewport edit mode in AppStateManager
        AppStateManager.setViewportEditActive(true)
        assertTrue(AppStateManager.isViewportEditActive.value)

        // Switch layout to Layout 2
        MacroPadState.setActiveLayoutId(layout2Id)
        assertTrue(MacroPadState.activeLayout.value?.id == layout2Id)

        // Verify that AppStateManager has closed the viewport edit active modal
        assertFalse(AppStateManager.isViewportEditActive.value)
    }

    @Test
    fun `reconnect prompt dialog stays active during transitions and auto-resets on success`() =
        runTest {
            // Reset states
            AppStateManager.setHasAdbCredentials(true)
            AppStateManager.setPrivdPromptDismissed(false)
            AppStateManager.setBackgroundSettingsActive(false)
            PrivdManager.setStateForTesting(PrivdState.OFF)

            // Yield to allow combine collection to initialize
            testScheduler.advanceUntilIdle()

            // Initially prompt is not active
            assertFalse(AppStateManager.isPrivdPromptActive.value)

            // 1. Transition to FAILED -> prompt should show
            PrivdManager.setStateForTesting(PrivdState.FAILED)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 2. Transition to CONNECTING -> prompt must STAY active (regression check)
            PrivdManager.setStateForTesting(PrivdState.CONNECTING)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 3. Transition to RUNNING -> prompt must STAY active until clicked Done
            PrivdManager.setStateForTesting(PrivdState.RUNNING)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 4. Click Done (or Skip) -> prompt turns off
            AppStateManager.setPrivdPromptDismissed(true)
            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)

            // 5. RUNNING state automatically resets dismissed to false for future drops
            assertFalse(AppStateManager.isPrivdPromptDismissed.value)

            // 6. Transition to FAILED again -> prompt should show again since dismissed was reset
            PrivdManager.setStateForTesting(PrivdState.FAILED)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 7. Open settings overlay -> prompt should hide and mark dismissed = true
            AppStateManager.setBackgroundSettingsActive(true)
            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)
            assertTrue(AppStateManager.isPrivdPromptDismissed.value)
        }

    @Test
    fun `setFullscreenKeyboardActive falls back to KeyboardSettings layout when null`() =
        runTest {
            // Assert initial state
            AppStateManager.setFullscreenKeyboardActive(false)

            // Set layout explicitly
            AppStateManager.setFullscreenKeyboardActive(true, KbLayout.AZERTY)
            assertEquals(KbLayout.AZERTY, AppStateManager.fullscreenKeyboardLayout.value)

            // Reset
            AppStateManager.setFullscreenKeyboardActive(false)

            // Activate with null/default layout, it should fall back to KeyboardSettings (default QWERTZ)
            AppStateManager.setFullscreenKeyboardActive(true)
            assertEquals(KbLayout.QWERTZ, AppStateManager.fullscreenKeyboardLayout.value)
        }

    @Test
    fun `fullscreenKeyboardLayout updates dynamically when KeyboardSettings layout changes`() =
        runTest {
            // Activate keyboard with no layout override (null)
            AppStateManager.setFullscreenKeyboardActive(true)
            assertEquals(KbLayout.QWERTZ, AppStateManager.fullscreenKeyboardLayout.value)

            // Change persistent setting layout
            KeyboardSettings.setKbLayout(KbLayout.AZERTY)

            // Verify fullscreenKeyboardLayout changes immediately
            assertEquals(KbLayout.AZERTY, AppStateManager.fullscreenKeyboardLayout.value)

            // Clean up
            AppStateManager.setFullscreenKeyboardActive(false)
            KeyboardSettings.setKbLayout(KbLayout.QWERTZ)
        }

    @Test
    fun `requestShutOff sets flag and consumeShutOffRequest resets it`() =
        runTest {
            assertFalse(AppStateManager.shutOffRequested.value)

            AppStateManager.requestShutOff()
            assertTrue(AppStateManager.shutOffRequested.value)

            AppStateManager.consumeShutOffRequest()
            assertFalse(AppStateManager.shutOffRequested.value)
        }

    @Test
    fun `resetPrivdPromptState clears prompt showing and dismissed flags`() =
        runTest {
            AppStateManager.setHasAdbCredentials(true)
            AppStateManager.setPrivdPromptDismissed(false)
            AppStateManager.setBackgroundSettingsActive(false)
            PrivdManager.setStateForTesting(PrivdState.FAILED)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            PrivdManager.setStateForTesting(PrivdState.OFF)
            AppStateManager.resetPrivdPromptState()
            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)
            assertFalse(AppStateManager.isPrivdPromptDismissed.value)
        }

    @Test
    fun `setAccessibilityActive updates isAccessibilityActive flow`() =
        runTest {
            AppStateManager.setAccessibilityActive(true)
            assertTrue(AppStateManager.isAccessibilityActive.value)

            AppStateManager.setAccessibilityActive(false)
            assertFalse(AppStateManager.isAccessibilityActive.value)

            AppStateManager.setAccessibilityActive(true)
            assertTrue(AppStateManager.isAccessibilityActive.value)
        }

    @Test
    fun `deactivating accessibility service triggers reconnect prompt even when Privd is RUNNING`() =
        runTest {
            // Reset states
            AppStateManager.resetPrivdPromptState()
            AppStateManager.setHasAdbCredentials(true)
            AppStateManager.setBackgroundSettingsActive(false)
            AppStateManager.setAccessibilityActive(true)
            PrivdManager.setStateForTesting(PrivdState.RUNNING)

            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)

            // Deactivate Accessibility Service -> Prompt becomes active immediately
            AppStateManager.setAccessibilityActive(false)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // Re-enable Accessibility Service and dismiss prompt
            AppStateManager.setAccessibilityActive(true)
            AppStateManager.setPrivdPromptDismissed(true)
            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)
        }

    @Test
    fun `attempting to dismiss prompt while accessibility is disabled keeps prompt active`() =
        runTest {
            // Setup initial running state with accessibility active
            AppStateManager.resetPrivdPromptState()
            AppStateManager.setHasAdbCredentials(true)
            AppStateManager.setBackgroundSettingsActive(false)
            AppStateManager.setAccessibilityActive(true)
            PrivdManager.setStateForTesting(PrivdState.RUNNING)

            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)

            // Disable Accessibility Service -> Prompt becomes active
            AppStateManager.setAccessibilityActive(false)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // Attempt to dismiss prompt while accessibility is still false
            AppStateManager.setPrivdPromptDismissed(true)
            testScheduler.advanceUntilIdle()

            // Prompt MUST remain active because Accessibility Service is mandatory!
            assertTrue(AppStateManager.isPrivdPromptActive.value)
            assertFalse(AppStateManager.isPrivdPromptDismissed.value)

            // Cleanup
            AppStateManager.setAccessibilityActive(true)
        }

    @Test
    fun `setting external client state updates AppStateManager correctly`() =
        runTest {
            // Verify default/initial state
            assertFalse(AppStateManager.isExternalClientActive.value)
            assertEquals(null, AppStateManager.externalClientPackage.value)
            assertEquals(null, AppStateManager.focusedAppPackageName.value)
            assertEquals(null, AppStateManager.hoveredAppPackageName.value)
            assertEquals(null, AppStateManager.hoveredAppLabel.value)
            assertEquals(null, AppStateManager.hoveredAppPrimaryColor.value)
            assertEquals(null, AppStateManager.hoveredAppSecondaryColor.value)

            // Update client state
            AppStateManager.setExternalClientState(
                isActive = true,
                packageName = "com.test.launcher",
                focusedApp = "com.test.game",
                hoveredPackage = "com.test.hover",
                hoveredLabel = "Hovered Game",
                hoveredPrimaryColor = 0xFF112233.toInt(),
                hoveredSecondaryColor = 0xFF445566.toInt(),
            )

            // Verify updated values
            assertTrue(AppStateManager.isExternalClientActive.value)
            assertEquals("com.test.launcher", AppStateManager.externalClientPackage.value)
            assertEquals("com.test.game", AppStateManager.focusedAppPackageName.value)
            assertEquals("com.test.hover", AppStateManager.hoveredAppPackageName.value)
            assertEquals("Hovered Game", AppStateManager.hoveredAppLabel.value)
            assertEquals(0xFF112233.toInt(), AppStateManager.hoveredAppPrimaryColor.value)
            assertEquals(0xFF445566.toInt(), AppStateManager.hoveredAppSecondaryColor.value)

            // Reset client state
            AppStateManager.setExternalClientState(
                isActive = false,
                packageName = null,
                focusedApp = null,
                hoveredPackage = null,
                hoveredLabel = null,
                hoveredPrimaryColor = null,
                hoveredSecondaryColor = null,
            )

            assertFalse(AppStateManager.isExternalClientActive.value)
            assertEquals(null, AppStateManager.externalClientPackage.value)
            assertEquals(null, AppStateManager.focusedAppPackageName.value)
            assertEquals(null, AppStateManager.hoveredAppPackageName.value)
            assertEquals(null, AppStateManager.hoveredAppLabel.value)
            assertEquals(null, AppStateManager.hoveredAppPrimaryColor.value)
            assertEquals(null, AppStateManager.hoveredAppSecondaryColor.value)
        }

    @Test
    fun `companionViewMode state persists across focus changes until explicit reset`() =
        runTest {
            assertEquals(CompanionViewMode.AUTO, AppStateManager.companionViewMode.value)

            // Set companionViewMode to MACROPAD
            AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
            assertEquals(CompanionViewMode.MACROPAD, AppStateManager.companionViewMode.value)

            // Update external client state -> should preserve MACROPAD mode (sticky toggle)
            AppStateManager.setExternalClientState(
                isActive = true,
                packageName = "com.test.launcher",
                focusedApp = "com.test.game",
            )
            assertEquals(CompanionViewMode.MACROPAD, AppStateManager.companionViewMode.value)

            // Set companionViewMode to DASHBOARD
            AppStateManager.setCompanionViewMode(CompanionViewMode.DASHBOARD)
            assertEquals(CompanionViewMode.DASHBOARD, AppStateManager.companionViewMode.value)

            // Deactivate client -> should preserve DASHBOARD mode
            AppStateManager.setExternalClientState(
                isActive = false,
                packageName = null,
                focusedApp = null,
            )
            assertEquals(CompanionViewMode.DASHBOARD, AppStateManager.companionViewMode.value)

            // Explicitly set AUTO -> should revert to AUTO
            AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)
            assertEquals(CompanionViewMode.AUTO, AppStateManager.companionViewMode.value)
        }

    @Test
    fun `shouldShowIntegrationHome returns expected values across view modes`() {
        val associatedProfile =
            PadProfile(
                id = "2",
                name = "Game",
                layouts = emptyList(),
                association =
                    com.stormpanda.megingiard.macropad
                        .ProfileAssociation(packageName = "com.test.game"),
            )

        assertFalse(CompanionViewMode.MACROPAD.shouldShowIntegrationHome("com.test.game", null, associatedProfile))
        assertTrue(CompanionViewMode.DASHBOARD.shouldShowIntegrationHome("com.test.game", null, associatedProfile))

        // AUTO mode: true when focusedApp is null (idle)
        assertTrue(CompanionViewMode.AUTO.shouldShowIntegrationHome(null, null, associatedProfile))

        // AUTO mode: true when focusedApp (e.g. launcher) does not match activeProfile
        assertTrue(CompanionViewMode.AUTO.shouldShowIntegrationHome("com.android.launcher3", null, associatedProfile))

        // AUTO mode: false when focusedApp matches activeProfile
        assertFalse(CompanionViewMode.AUTO.shouldShowIntegrationHome("com.test.game", null, associatedProfile))
    }

    @Test
    fun `shouldShowIntegrationHome in AUTO handles GameNative ROM active profiles`() {
        val gameNativeProfile =
            PadProfile(
                id = "gn-1",
                name = "Ball x Pit",
                layouts = emptyList(),
                association =
                    com.stormpanda.megingiard.macropad.ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "BALL x PIT.steam",
                        systemId = "pc",
                    ),
            )

        // When focused package is app.gamenative and activeProfile is Ball x Pit:
        // Returns false (shows MacroPad) even if focusedRomPath is null due to isActiveProfile fallback
        assertFalse(CompanionViewMode.AUTO.shouldShowIntegrationHome("app.gamenative", null, gameNativeProfile))
        assertFalse(CompanionViewMode.AUTO.shouldShowIntegrationHome("app.gamenative", "BALLxPIT.steam", gameNativeProfile))

        // When focused package changes to home launcher (e.g. com.android.launcher3):
        // Returns true (shows Companion Hub) while gameNativeProfile remains activeProfile
        assertTrue(CompanionViewMode.AUTO.shouldShowIntegrationHome("com.android.launcher3", null, gameNativeProfile))
    }

    @Test
    fun `closeActiveModal resets all modal states and overlay selections simultaneously`() =
        runTest {
            AppStateManager.setFullscreenKeyboardActive(true)
            AppStateManager.setFullscreenMouseActive(true)
            AppStateManager.setViewportEditActive(true)
            AppStateManager.setBackgroundSettingsActive(true)
            AppStateManager.setGlobalSettingsOpen(true)
            AppStateManager.setKeyboardSettingsOpen(true)
            AppStateManager.setTouchpadSettingsOpen(true)
            AppStateManager.setActiveCropCutoutId("cutout_1")
            AppStateManager.setSelectedCutoutId("cutout_2")

            AppStateManager.closeActiveModal()

            assertFalse(AppStateManager.isFullscreenKeyboardActive.value)
            assertFalse(AppStateManager.isFullscreenMouseActive.value)
            assertFalse(AppStateManager.isViewportEditActive.value)
            assertFalse(AppStateManager.isBackgroundSettingsActive.value)
            assertFalse(AppStateManager.isGlobalSettingsOpen.value)
            assertFalse(AppStateManager.isKeyboardSettingsOpen.value)
            assertFalse(AppStateManager.isTouchpadSettingsOpen.value)
            assertEquals(null, AppStateManager.activeCropCutoutId.value)
            assertEquals(null, AppStateManager.selectedCutoutId.value)
        }

    @Test
    fun `isAnyModalActive evaluates true for all modals and false for standard use`() =
        runTest {
            AppStateManager.closeActiveModal()
            assertFalse(AppStateManager.isAnyModalActive.value)

            AppStateManager.setGlobalSettingsOpen(true)
            assertTrue(AppStateManager.isAnyModalActive.value)
            AppStateManager.closeActiveModal()

            AppStateManager.setFullscreenKeyboardActive(true)
            assertTrue(AppStateManager.isAnyModalActive.value)
            AppStateManager.closeActiveModal()

            AppStateManager.setFullscreenMouseActive(true)
            assertTrue(AppStateManager.isAnyModalActive.value)
            AppStateManager.closeActiveModal()

            AppStateManager.setViewportEditActive(true)
            assertTrue(AppStateManager.isAnyModalActive.value)
            AppStateManager.closeActiveModal()

            assertFalse(AppStateManager.isAnyModalActive.value)
        }

    @Test
    fun `isAnyMenuOpen evaluates true for settings and editors and false for fullscreen input overlays`() =
        runTest {
            AppStateManager.closeActiveModal()
            assertFalse(AppStateManager.isAnyMenuOpen.value)

            AppStateManager.setGlobalSettingsOpen(true)
            assertTrue(AppStateManager.isAnyMenuOpen.value)
            AppStateManager.closeActiveModal()

            AppStateManager.setEditorActive(true)
            assertTrue(AppStateManager.isAnyMenuOpen.value)
            AppStateManager.setEditorActive(false)

            AppStateManager.openQuickMenu()
            assertTrue(AppStateManager.isAnyMenuOpen.value)
            AppStateManager.closeQuickMenu()

            // Fullscreen keyboard or mouse should NOT count as menu open
            AppStateManager.setFullscreenKeyboardActive(true)
            assertFalse(AppStateManager.isAnyMenuOpen.value)
            AppStateManager.closeActiveModal()
        }

    @Test
    fun `handleEdgeSwipe prioritizes closing active modals over toggling quick menu`() =
        runTest {
            AppStateManager.closeActiveModal()
            AppStateManager.closeQuickMenu()

            // Case 1: No modal, Quick Menu closed -> handleEdgeSwipe opens Quick Menu
            AppStateManager.handleEdgeSwipe()
            assertTrue(AppStateManager.isQuickMenuOpen.value)

            // Case 2: Quick Menu open -> handleEdgeSwipe closes Quick Menu
            AppStateManager.handleEdgeSwipe()
            assertFalse(AppStateManager.isQuickMenuOpen.value)

            // Case 3: Modal active (e.g. Fullscreen Keyboard) -> handleEdgeSwipe closes modal
            AppStateManager.setFullscreenKeyboardActive(true)
            assertTrue(AppStateManager.isAnyModalActive.value)
            AppStateManager.handleEdgeSwipe()
            assertFalse(AppStateManager.isFullscreenKeyboardActive.value)
            assertFalse(AppStateManager.isQuickMenuOpen.value)
        }

    @Test
    fun `activeLayout change does not side effect uiMode or close active overlay`() =
        runTest {
            AppStateManager.closeActiveModal()
            AppStateManager.openQuickMenu()
            assertTrue(AppStateManager.isQuickMenuOpen.value)

            val profile =
                PadProfile(
                    id = "p_test_qm",
                    name = "Test Profile",
                    layouts =
                        listOf(
                            PadLayout(id = "l_test_qm_1", name = "Layout 1"),
                            PadLayout(id = "l_test_qm_2", name = "Layout 2"),
                        ),
                    activeLayoutId = "l_test_qm_1",
                )
            MacroPadState.addProfile(profile)
            MacroPadState.setActiveProfileId("p_test_qm")
            MacroPadState.setActiveLayoutId("l_test_qm_2")

            assertTrue(AppStateManager.isQuickMenuOpen.value)
            AppStateManager.closeQuickMenu()
        }

    @Test
    fun `shouldShowIntegrationHome evaluates true for launcher packages in AUTO mode`() =
        runTest {
            val autoMode = CompanionViewMode.AUTO

            // GameFocus package -> should show Companion Hub
            assertTrue(
                autoMode.shouldShowIntegrationHome(
                    focusedAppPackageName = "com.stormpanda.megingiard.gamefocus.debug",
                    focusedRomPath = null,
                    activeProfile = null,
                ),
            )

            // System UI / Android Launcher -> should show Companion Hub
            assertTrue(
                autoMode.shouldShowIntegrationHome(
                    focusedAppPackageName = "com.android.launcher3",
                    focusedRomPath = null,
                    activeProfile = null,
                ),
            )

            // Null package -> should show Companion Hub
            assertTrue(
                autoMode.shouldShowIntegrationHome(
                    focusedAppPackageName = null,
                    focusedRomPath = null,
                    activeProfile = null,
                ),
            )
        }
}
