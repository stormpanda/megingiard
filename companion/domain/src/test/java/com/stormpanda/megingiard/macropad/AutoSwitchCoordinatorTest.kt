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

    private fun testProfile(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Profile",
        packageName: String? = null,
        systemId: String? = null,
        romFileName: String? = null,
    ): PadProfile {
        val lId = UUID.randomUUID().toString()
        return PadProfile(
            id = id,
            name = name,
            layouts = listOf(PadLayout(id = lId, name = "Default")),
            activeLayoutId = lId,
            association =
                packageName?.let {
                    ProfileAssociation(
                        packageName = it,
                        systemId = systemId,
                        romFileName = romFileName,
                    )
                },
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AutoSwitchCoordinator.resetForTesting()
        SystemRoleClassifier.setLaunchersForTesting(setOf("com.android.launcher3"))
        AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)

        profile1 = testProfile(name = "Retro Gaming", packageName = "com.retroarch")
        profile2 = testProfile(name = "3DS Emu", packageName = "com.citra.emu")

        MacroPadState.loadFrom(listOf(profile1, profile2), profile1.id)
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
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        AutoSwitchCoordinator.onPackageChanged("com.android.chrome")
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged ignores self package focus`() {
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        AutoSwitchCoordinator.onPackageChanged("com.stormpanda.megingiard")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)

        AutoSwitchCoordinator.onPackageChanged("com.stormpanda.megingiard.debug")
        assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged ignores system and transient packages`() {
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        listOf(
            "com.android.systemui",
            "android",
            "com.odin.gameassistant",
            "com.odin.dualscreen.assistant",
            "com.odin.mapping",
            "com.odin.settings",
            "com.google.android.gms",
            "com.google.android.gms.auth.api.signin",
            "com.google.android.play.games",
        ).forEach { pkg ->
            AutoSwitchCoordinator.onPackageChanged(pkg)
            assertEquals(null, AutoSwitchCoordinator.foregroundApp.value)
        }
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
        runTest {
            val profile3 =
                testProfile(
                    name = "Super Mario World",
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romFileName = "Super Mario World.sfc",
                )
            MacroPadState.loadFrom(listOf(profile1, profile2, profile3), profile1.id)

            AutoSwitchCoordinator.onPackageChanged("com.android.chrome")

            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romPath = "/roms/snes/Super Mario World.sfc",
                    gameTitle = "Super Mario World",
                ),
            )
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")
            delay(150)

            assertEquals(profile3.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `onPackageChanged falls back to generic profile if no ROM specific profile matches`() =
        runTest {
            val profile3 =
                testProfile(
                    name = "Super Mario World",
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romFileName = "Super Mario World.sfc",
                )
            MacroPadState.loadFrom(listOf(profile1, profile3), profile3.id)

            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romPath = "/roms/snes/Zelda.sfc",
                    gameTitle = "Zelda",
                ),
            )
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")
            delay(150)

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
        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)

        AutoSwitchCoordinator.onPackageChanged("com.android.chrome")
        assertEquals("com.android.chrome", AppStateManager.focusedAppPackageName.value)
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged preserves focused game state when task switcher is opened`() {
        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)

        AutoSwitchCoordinator.onPackageChanged("com.android.launcher3")
        assertEquals("com.android.launcher3", AutoSwitchCoordinator.foregroundApp.value)
        assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged preserves active ROM path when window focus ticks occur for same emulator`() =
        runTest {
            val profile3 =
                testProfile(
                    name = "Ball x Pit",
                    packageName = "app.gamenative",
                    systemId = "pc",
                    romFileName = "BALL x PIT.steam",
                )
            MacroPadState.loadFrom(listOf(profile1, profile2, profile3), profile3.id)

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
            delay(150)

            assertEquals("app.gamenative", AppStateManager.focusedAppPackageName.value)
            assertEquals("BALLxPIT.steam", AppStateManager.focusedRomPath.value)

            AutoSwitchCoordinator.onPackageChanged("app.gamenative")
            assertEquals("app.gamenative", AppStateManager.focusedAppPackageName.value)
            assertEquals("BALLxPIT.steam", AppStateManager.focusedRomPath.value)
            assertEquals(profile3.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `onPackageChanged ignores auto profile switch when companionViewMode is not AUTO`() {
        AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
        assertEquals(profile1.id, MacroPadState.activeProfileId.value)

        AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)
        assertEquals(profile2.id, MacroPadState.activeProfileId.value)
    }

    @Test
    fun `onPackageChanged restores ROM session when returning to emulator from task switcher`() =
        runTest {
            val profile3 =
                testProfile(
                    name = "Super Mario World",
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romFileName = "Super Mario World.sfc",
                )
            MacroPadState.loadFrom(listOf(profile1, profile2, profile3), profile3.id)

            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romPath = "/roms/snes/Super Mario World.sfc",
                    gameTitle = "Super Mario World",
                ),
            )
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")
            delay(150)
            assertEquals(profile3.id, MacroPadState.activeProfileId.value)

            SystemRoleClassifier.setLaunchersForTesting(setOf("com.android.launcher3"))
            AutoSwitchCoordinator.onPackageChanged("com.android.launcher3")
            assertEquals("com.android.launcher3", AutoSwitchCoordinator.foregroundApp.value)
            assertEquals("com.retroarch", AppStateManager.focusedAppPackageName.value)

            AutoSwitchCoordinator.onPackageChanged("com.retroarch")
            delay(150)

            assertEquals("com.retroarch", AppStateManager.focusedAppPackageName.value)
            assertEquals("/roms/snes/Super Mario World.sfc", AppStateManager.focusedRomPath.value)
            assertEquals(profile3.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `pressing back from launcher to game preserves macropad and does not revert to hub`() =
        runTest {
            MacroPadState.setActiveProfileId(profile2.id)
            AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
            assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)
            assertFalse(AppStateManager.showIntegrationHome.value)

            AutoSwitchCoordinator.onPackageChanged("com.stormpanda.megingiard.gamefocus.debug")
            AppStateManager.setExternalClientState(
                isActive = true,
                packageName = "com.stormpanda.megingiard.gamefocus.debug",
                focusedApp = null,
                hoveredPackage = "com.test.game",
            )
            assertTrue(AppStateManager.showIntegrationHome.value)

            AutoSwitchCoordinator.onPackageChanged("com.citra.emu")
            AppStateManager.setExternalClientState(
                isActive = false,
                packageName = "com.stormpanda.megingiard.gamefocus.debug",
                focusedApp = null,
            )

            assertEquals(profile2.id, MacroPadState.activeProfileId.value)
            assertEquals("com.citra.emu", AppStateManager.focusedAppPackageName.value)
            assertFalse(AppStateManager.showIntegrationHome.value)
        }

    @Test
    fun `closing emulator session switches back to generic emulator profile`() =
        runTest {
            val genericProfile =
                testProfile(
                    name = "Generic RetroArch",
                    packageName = "com.retroarch",
                )
            val romProfile =
                testProfile(
                    name = "Super Mario World",
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romFileName = "Super Mario World.sfc",
                )
            val defaultProfile =
                testProfile(
                    name = "Default",
                )
            MacroPadState.loadFrom(listOf(defaultProfile, genericProfile, romProfile), defaultProfile.id)

            AutoSwitchCoordinator.onPackageChanged("com.retroarch")
            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "com.retroarch",
                    systemId = "snes",
                    romPath = "/roms/snes/Super Mario World.sfc",
                    gameTitle = "Super Mario World",
                ),
            )
            delay(150)
            assertEquals(romProfile.id, MacroPadState.activeProfileId.value)

            // When game session ends (game closed in emulator):
            EmulatorDetectionFunnel.setActiveSessionForTesting(null)
            delay(150)

            // Then profile switches to generic emulator profile
            assertEquals(genericProfile.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `closing emulator session without generic profile reverts to default profile`() =
        runTest {
            val romProfile =
                testProfile(
                    name = "Boltgun",
                    packageName = "app.gamenative",
                    systemId = "pc",
                    romFileName = "Boltgun.steam",
                )
            val defaultProfile =
                testProfile(
                    name = "Default",
                )
            MacroPadState.loadFrom(listOf(defaultProfile, romProfile), defaultProfile.id)

            AutoSwitchCoordinator.onPackageChanged("app.gamenative")
            EmulatorDetectionFunnel.setActiveSessionForTesting(
                ActiveGameSession(
                    packageName = "app.gamenative",
                    systemId = "pc",
                    romPath = null,
                    romIdentifier = "Boltgun.steam",
                    gameTitle = "Boltgun",
                ),
            )
            delay(150)
            assertEquals(romProfile.id, MacroPadState.activeProfileId.value)

            // When game session ends (game closed in GameNative):
            EmulatorDetectionFunnel.setActiveSessionForTesting(null)
            delay(150)

            // Then profile reverts to Default profile
            assertEquals(defaultProfile.id, MacroPadState.activeProfileId.value)
        }

    @Test
    fun `reevaluateAutoState on emulator with no ROM session reverts to default profile if no generic profile`() =
        runTest {
            val romProfile =
                testProfile(
                    name = "Boltgun",
                    packageName = "app.gamenative",
                    systemId = "pc",
                    romFileName = "Boltgun.steam",
                )
            val defaultProfile =
                testProfile(
                    name = "Default",
                )
            MacroPadState.loadFrom(listOf(defaultProfile, romProfile), romProfile.id)
            AppStateManager.setStandaloneForegroundState("app.gamenative", null)

            AutoSwitchCoordinator.reevaluateAutoState()

            assertEquals(defaultProfile.id, MacroPadState.activeProfileId.value)
        }
}
