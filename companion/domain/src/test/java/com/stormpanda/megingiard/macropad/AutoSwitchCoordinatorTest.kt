package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.catalog.SystemRoleClassifier
import com.stormpanda.megingiard.session.ActiveGameSession
import com.stormpanda.megingiard.session.EmulatorDetectionFunnel
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
        SystemRoleClassifier.setLaunchersForTesting(setOf("com.android.launcher3"))
        AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)

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
        AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)
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
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)

        // When Thor dualscreen assistant focus occurs
        AutoSwitchCoordinator.onPackageChanged("com.odin.dualscreen.assistant")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)

        // When Thor button mapping focus occurs
        AutoSwitchCoordinator.onPackageChanged("com.odin.mapping")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)

        // When Thor hardware settings focus occurs
        AutoSwitchCoordinator.onPackageChanged("com.odin.settings")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)

        // When Google Play Services or Play Games sign-in overlay occurs
        AutoSwitchCoordinator.onPackageChanged("com.google.android.gms")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)
        AutoSwitchCoordinator.onPackageChanged("com.google.android.gms.auth.api.signin")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)
        AutoSwitchCoordinator.onPackageChanged("com.google.android.play.games")
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
    fun `onPackageChanged preserves client integration state when task switcher is opened`() {
        // Given integration client (GameFocus) is active and Genshin is focused game
        AppStateManager.setExternalClientState(
            isActive = true,
            packageName = "com.stormpanda.megingiard.gamefocus.debug",
            focusedApp = "com.miHoYo.GenshinImpact",
        )

        // When user opens Task Switcher (com.android.launcher3)
        AutoSwitchCoordinator.onPackageChanged("com.android.launcher3")

        // Then integration state remains active, focusedApp remains Genshin, and foreground app records launcher3
        assertTrue(AppStateManager.isExternalClientActive.value)
        assertEquals("com.stormpanda.megingiard.gamefocus.debug", AppStateManager.externalClientPackage.value)
        assertEquals("com.miHoYo.GenshinImpact", AppStateManager.focusedAppPackageName.value)
        assertEquals("com.android.launcher3", AutoSwitchCoordinator.foregroundApp.value)
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

        // Then client state is deactivated and standalone foreground app is recorded
        assertFalse(AppStateManager.isExternalClientActive.value)
        assertEquals(null, AppStateManager.externalClientPackage.value)
        assertEquals("com.android.chrome", AppStateManager.focusedAppPackageName.value)
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

        // Then integration state is deactivated and standalone foreground app is recorded
        assertFalse(AppStateManager.isExternalClientActive.value)
        assertEquals(null, AppStateManager.externalClientPackage.value)
        assertEquals("com.android.chrome", AppStateManager.focusedAppPackageName.value)
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

    @Test
    fun `onPackageChanged updates AppStateManager focusedAppPackageName for standalone apps`() {
        AutoSwitchCoordinator.onPackageChanged("org.es_de.frontend")
        assertEquals("org.es_de.frontend", AppStateManager.focusedAppPackageName.value)

        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)
    }

    @Test
    fun `onPackageChanged preserves active profile selection when switching to unmapped app`() {
        // Given active profile is profile2 (associated with com.citra.emu)
        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)

        // When switching to an unmapped app (chrome)
        AutoSwitchCoordinator.onPackageChanged("com.android.chrome")

        // Then standalone focused app is updated to chrome, but active profile ID remains profile2
        assertEquals("com.android.chrome", AppStateManager.focusedAppPackageName.value)
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged preserves focused game state when task switcher is opened`() {
        // Given active profile is profile2 (associated with com.citra.emu)
        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)

        // When switching to task switcher (com.android.launcher3)
        AutoSwitchCoordinator.onPackageChanged("com.android.launcher3")

        // Then foreground app is set to launcher3, but focused game state remains com.citra.emu
        assertEquals("com.android.launcher3", AutoSwitchCoordinator.foregroundApp.value)
        assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged preserves active ROM path when window focus ticks occur for same emulator`() =
        runTest {
            val p3Id = UUID.randomUUID().toString()
            val l3Id = UUID.randomUUID().toString()
            val profile3 =
                PadProfile(
                    id = p3Id,
                    name = "Ball x Pit",
                    layouts = listOf(PadLayout(id = l3Id, name = "PC Layout")),
                    activeLayoutId = l3Id,
                    association =
                        ProfileAssociation(
                            packageName = "app.gamenative",
                            romFileName = "BALL x PIT.steam",
                            systemId = "pc",
                        ),
                )
            MacroPadState.loadFrom(listOf(profile1, profile2, profile3), profile3.id)

            // Given GameNative session is active with BALLxPIT.steam
            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "app.gamenative",
                    systemId = "pc",
                    romPath = null,
                    romIdentifier = "BALLxPIT.steam",
                    gameTitle = "Ball x Pit",
                ),
            )
            AutoSwitchCoordinator.onPackageChanged("app.gamenative")
            kotlinx.coroutines.delay(150)

            assertEquals("app.gamenative", AppStateManager.focusedAppPackageName.value)
            assertEquals("BALLxPIT.steam", AppStateManager.focusedRomPath.value)

            // When a window focus tick occurs for app.gamenative without active session update
            AutoSwitchCoordinator.onPackageChanged("app.gamenative")

            // Then focusedRomPath is preserved
            assertEquals("app.gamenative", AppStateManager.focusedAppPackageName.value)
            assertEquals("BALLxPIT.steam", AppStateManager.focusedRomPath.value)
            assertEquals(profile3.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `onPackageChanged ignores auto profile switch when companionViewMode is not AUTO`() {
        // Given companionViewMode is set to MACROPAD (Auto Mode OFF)
        AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        // When a mapped app (com.citra.emu -> profile2) is opened while Auto Mode is OFF
        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")

        // Then profile remains profile1 (auto profile switch is bypassed)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        // When Auto Mode is re-enabled via setCompanionViewMode(AUTO)
        AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)

        // Then reevaluateAutoState triggers and switches active profile to profile2
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged restores ROM session when returning to emulator from task switcher`() =
        kotlinx.coroutines.runBlocking {
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
            MacroPadState.loadFrom(listOf(profile1, profile2, profile3), profile3.id)

            // Given RetroArch is running Super Mario World
            val session =
                ActiveGameSession(
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romPath = "/roms/snes/Super Mario World.sfc",
                    gameTitle = "Super Mario World",
                )
            EmulatorDetectionFunnel.setActiveSessionForTesting(session)
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")
            delay(150)
            assertEquals(profile3.id, MacroPadState.activeProfileId.value)

            // When Task Switcher (com.android.launcher3) is opened
            SystemRoleClassifier.setLaunchersForTesting(setOf("com.android.launcher3"))
            AutoSwitchCoordinator.onPackageChanged("com.android.launcher3")
            assertEquals("com.android.launcher3", AutoSwitchCoordinator.foregroundApp.value)
            assertEquals("com.retroarch", AppStateManager.focusedAppPackageName.value)

            // When user returns to RetroArch from Task Switcher
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")
            delay(150)

            // Then ROM session and active profile profile3 are restored
            assertEquals("com.retroarch", AppStateManager.focusedAppPackageName.value)
            assertEquals("/roms/snes/Super Mario World.sfc", AppStateManager.focusedRomPath.value)
            assertEquals(profile3.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `pressing back from launcher to game preserves macropad and does not revert to hub`() =
        runTest {
            // Given profile2 is associated with com.citra.emu
            MacroPadState.setActiveProfileId(profile2.id)
            AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
            assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)
            assertFalse(AppStateManager.showIntegrationHome.value)

            // When user presses Home and launcher opens
            AutoSwitchCoordinator.onPackageChanged("com.stormpanda.megingiard.gamefocus.debug")
            AppStateManager.setExternalClientState(
                isActive = true,
                packageName = "com.stormpanda.megingiard.gamefocus.debug",
                focusedApp = null,
                hoveredPackage = "com.test.game",
            )
            assertTrue(AppStateManager.showIntegrationHome.value)

            // When user presses Back: OS foreground switches to game, then launcher sends onStop (isActive=false, focusedPackage=null)
            AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
            AppStateManager.setExternalClientState(
                isActive = false,
                packageName = "com.stormpanda.megingiard.gamefocus.debug",
                focusedApp = null,
            )

            // Then game profile remains active and hub is NOT shown
            assertEquals(profile2.id, MacroPadState.activeProfileId.value)
            assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)
            assertFalse(AppStateManager.showIntegrationHome.value)
        }
}
