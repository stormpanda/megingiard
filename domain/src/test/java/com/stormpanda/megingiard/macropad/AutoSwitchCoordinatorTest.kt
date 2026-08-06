package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.focus.rom.ActiveGameSession
import com.stormpanda.megingiard.focus.rom.EmulatorDetectionFunnel
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
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
                association = ProfileAssociation(packageName = "com.retroarch"),
            )

        val p2Id = UUID.randomUUID().toString()
        val l2Id = UUID.randomUUID().toString()
        profile2 =
            PadProfile(
                id = p2Id,
                name = "3DS Emu",
                layouts = listOf(PadLayout(id = l2Id, name = "Default")),
                activeLayoutId = l2Id,
                association = ProfileAssociation(packageName = "com.citra.emu"),
            )

        MacroPadState.loadFrom(listOf(profile1, profile2), p1Id)
    }

    @After
    fun tearDown() {
        AutoSwitchCoordinator.resetForTesting()
        EmulatorDetectionFunnel.resetForTesting()
        AppStateManager.setExternalClientState(
            isActive = false,
            packageName = null,
            focusedApp = null,
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `onPackageChanged switches active profile when mapping exists`() {
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

        // When game assistant focus occurs
        AutoSwitchCoordinator.onPackageChanged("com.odin.gameassistant")

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

    @Test
    fun `onPackageChanged deactivates client state when switching to unrelated app from Game`() {
        // Given integration client is active and a game package is focused
        AppStateManager.setExternalClientState(
            isActive = true,
            packageName = "com.test.launcher",
            focusedApp = "com.citra.emu",
        )
        MacroPadState.setActiveProfileId(profile1.id)

        // When focus change event is reported for an unrelated app (Chrome)
        AutoSwitchCoordinator.onPackageChanged("com.android.chrome")

        // Then integration state is deactivated
        assertFalse(AppStateManager.isExternalClientActive.value)
        assertEquals(null, AppStateManager.externalClientPackage.value)
        assertEquals(null, AppStateManager.focusedAppPackageName.value)
    }

    @Test
    fun `onPackageChanged with emulator matches ROM specific profile first`() =
        kotlinx.coroutines.runBlocking {
            val p3Id = UUID.randomUUID().toString()
            val l3Id = UUID.randomUUID().toString()
            // ROM-specific profile for Super Mario World on RetroArch
            val profile3 =
                PadProfile(
                    id = p3Id,
                    name = "Super Mario World",
                    layouts = listOf(PadLayout(id = l3Id, name = "Mario Layout")),
                    activeLayoutId = l3Id,
                    association =
                        ProfileAssociation(
                            packageName = "com.retroarch",
                            systemId = "snes",
                            romFileName = "Super Mario World.sfc",
                        ),
                )
            MacroPadState.loadFrom(listOf(profile1, profile2, profile3), profile1.id)

            // Given Chrome is active and we have no ROM session
            AutoSwitchCoordinator.onPackageChanged("com.android.chrome")

            // When RetroArch is opened with a Super Mario World snes ROM session
            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romPath = "/roms/snes/Super Mario World.sfc",
                    gameTitle = "Super Mario World",
                ),
            )
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")

            // Wait for background collector to run on Dispatchers.Default
            kotlinx.coroutines.delay(150)

            // Then active profile switches to the ROM-specific profile (profile3)
            assertEquals(profile3.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `onPackageChanged falls back to generic profile if no ROM specific profile matches`() =
        kotlinx.coroutines.runBlocking {
            // Given we load profile1 (generic retroarch) and profile3 (Mario specific)
            val p3Id = UUID.randomUUID().toString()
            val l3Id = UUID.randomUUID().toString()
            val profile3 =
                PadProfile(
                    id = p3Id,
                    name = "Super Mario World",
                    layouts = listOf(PadLayout(id = l3Id, name = "Mario Layout")),
                    activeLayoutId = l3Id,
                    association =
                        ProfileAssociation(
                            packageName = "com.retroarch",
                            systemId = "snes",
                            romFileName = "Super Mario World.sfc",
                        ),
                )
            MacroPadState.loadFrom(listOf(profile1, profile3), profile3.id)

            // When RetroArch is opened with a different ROM (e.g. Zelda)
            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romPath = "/roms/snes/Zelda.sfc",
                    gameTitle = "Zelda",
                ),
            )
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")

            // Wait for background collector to run on Dispatchers.Default
            kotlinx.coroutines.delay(150)

            // Then active profile falls back to the generic retroarch profile (profile1)
            assertEquals(profile1.id, MacroPadState.activeProfileId.value)
        }
}
