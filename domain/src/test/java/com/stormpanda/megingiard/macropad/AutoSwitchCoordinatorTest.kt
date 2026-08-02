package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [AutoSwitchCoordinator] verifying package changes trigger
 * active profile switches under the correct conditions (auto-switch active, correct mappings).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSwitchCoordinatorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var profile1: PadProfile
    private lateinit var profile2: PadProfile

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AutoSwitchCoordinator.resetForTesting()

        // Setup mock profiles with app mappings
        val p1Id = UUID.randomUUID().toString()
        val l1Id = UUID.randomUUID().toString()
        profile1 =
            PadProfile(
                id = p1Id,
                name = "Retro Gaming",
                layouts = listOf(PadLayout(id = l1Id, name = "Default")),
                activeLayoutId = l1Id,
                associatedPackage = "com.retroarch",
            )

        val p2Id = UUID.randomUUID().toString()
        val l2Id = UUID.randomUUID().toString()
        profile2 =
            PadProfile(
                id = p2Id,
                name = "3DS Emu",
                layouts = listOf(PadLayout(id = l2Id, name = "Default")),
                activeLayoutId = l2Id,
                associatedPackage = "com.citra.emu",
            )

        MacroPadState.loadFrom(listOf(profile1, profile2), p1Id)

        // Enable auto-switch settings for test runs
        SettingsManager.setAutoSwitchProfiles(true)
    }

    @After
    fun tearDown() {
        AutoSwitchCoordinator.resetForTesting()
        AppStateManager.setExternalClientState(
            isActive = false,
            packageName = null,
            focusedApp = null,
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `onPackageChanged switches active profile when mapping exists and auto switch is enabled`() {
        // Given we are currently on profile1
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        // When an unmapped app is opened
        AutoSwitchCoordinator.onPackageChanged("com.android.chrome")
        // Then active profile remains profile1
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        // When a mapped app (com.citra.emu -> profile2) is opened
        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        // Then active profile switches to profile2
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged does not switch active profile when auto switch is disabled`() {
        // Given auto switch is disabled
        SettingsManager.setAutoSwitchProfiles(false)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        // When a mapped app (com.citra.emu -> profile2) is opened
        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")

        // Then active profile remains profile1
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged ignores self package focus`() {
        // Given we are currently on profile1
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        // When Megingiard itself (release or debug) gains focus
        AutoSwitchCoordinator.onPackageChanged("com.stormpanda.megingiard")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)

        AutoSwitchCoordinator.onPackageChanged("com.stormpanda.megingiard.debug")
        // Then it is ignored and foreground app state does not record it
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged ignores system and transient packages`() {
        // Given we are currently on profile1
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        // When a system UI focus occurs
        AutoSwitchCoordinator.onPackageChanged("com.android.systemui")

        // Then it is ignored and foreground app state remains null
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)

        // When android core system focus occurs
        AutoSwitchCoordinator.onPackageChanged("android")

        // Then it is ignored and foreground app state remains null
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged ignores client package events when focused game is running`() {
        // Given integration client is active and retroarch is focused game
        AppStateManager.setExternalClientState(
            isActive = true,
            packageName = "com.test.launcher",
            focusedApp = "com.retroarch",
        )
        MacroPadState.setActiveProfileId(profile1.id) // profile1 associatedPackage is com.retroarch

        // When focus change event is reported for the client package
        AutoSwitchCoordinator.onPackageChanged("com.test.launcher")

        // Then focus change event is ignored, and active profile remains retroarch (profile1)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged deactivates client state when switching to unrelated app`() {
        // Given integration client is active and retroarch is focused game
        AppStateManager.setExternalClientState(
            isActive = true,
            packageName = "com.test.launcher",
            focusedApp = "com.retroarch",
        )
        MacroPadState.setActiveProfileId(profile1.id)

        // When user switches to an unrelated app (e.g. chrome)
        AutoSwitchCoordinator.onPackageChanged("com.android.chrome")

        // Then client state is deactivated
        assertFalse(AppStateManager.isExternalClientActive.value)
        assertEquals(null, AppStateManager.externalClientPackage.value)
        assertEquals(null, AppStateManager.focusedAppPackageName.value)
    }
}
