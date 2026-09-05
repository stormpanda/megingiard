package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.catalog.SystemRoleClassifier
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * End-to-End integration test suite verifying the game session detection and
 * automatic MacroPad profile switching pipeline:
 *
 * 1. File/Process inspection via [ProcessCmdlineProvider] and [EmulatorDetectionFunnel].
 * 2. Deterministic playlist/recent game parsing via [RetroArchDetector] and [Pcsx2AndroidDetector].
 * 3. Session emission to [EmulatorDetectionFunnel.activeSession].
 * 4. Reactive profile association matching and layout activation in [AutoSwitchCoordinator] and [MacroPadState].
 * 5. State synchronization with [AppStateManager].
 * 6. Clean teardown and default profile restoration when returning to launcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionAutoSwitchPipelineE2ETest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var defaultProfile: PadProfile
    private lateinit var ps1Profile: PadProfile
    private lateinit var ps2Profile: PadProfile
    private lateinit var browserProfile: PadProfile

    private fun buildProfile(
        name: String,
        packageName: String,
        systemId: String? = null,
        romFileName: String? = null,
    ): PadProfile {
        val pId = UUID.randomUUID().toString()
        val lId = UUID.randomUUID().toString()
        return PadProfile(
            id = pId,
            name = name,
            layouts = listOf(PadLayout(id = lId, name = "Main Layout")),
            activeLayoutId = lId,
            association =
                ProfileAssociation(
                    packageName = packageName,
                    systemId = systemId,
                    romFileName = romFileName,
                ),
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AutoSwitchCoordinator.resetForTesting()
        EmulatorDetectionFunnel.resetForTesting()
        SystemRoleClassifier.setLaunchersForTesting(setOf("com.android.launcher3", "org.es_de.frontend"))
        AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)

        defaultProfile =
            PadProfile(
                id = "profile-default-desktop",
                name = "Desktop Default",
                layouts = listOf(PadLayout(id = "layout-default", name = "Default")),
                activeLayoutId = "layout-default",
            )

        ps1Profile =
            buildProfile(
                name = "PS1 Castlevania SotN",
                packageName = "com.retroarch",
                systemId = "retroarch",
                romFileName = "Castlevania.chd",
            )

        ps2Profile =
            buildProfile(
                name = "PS2 Tactics Ogre",
                packageName = "xyz.aethersx2.android",
                systemId = "ps2",
                romFileName = "Tactics Ogre.iso",
            )

        browserProfile =
            buildProfile(
                name = "Web Browser",
                packageName = "com.android.chrome",
            )

        MacroPadState.loadFrom(listOf(defaultProfile, ps1Profile, ps2Profile, browserProfile), defaultProfile.id)
    }

    @After
    fun tearDown() {
        ProcessCmdlineProvider.textFileReader = null
        ProcessCmdlineProvider.runningProcessesProvider = null
        AutoSwitchCoordinator.resetForTesting()
        EmulatorDetectionFunnel.resetForTesting()
        AppStateManager.setCompanionViewMode(CompanionViewMode.AUTO)
        AppStateManager.setStandaloneForegroundState(null, null)
        Dispatchers.resetMain()
    }

    @Test
    fun testRetroArchDetectionToAutoProfileSwitchPipelineE2E() =
        runTest {
            // 1. Mock RetroArch content_history.lpl JSON content
            val retroArchHistoryJson =
                """
                {
                  "version": "1.5",
                  "default_core_path": "/data/data/com.retroarch/cores/mednafen_psx_hw_libretro_android.so",
                  "default_core_name": "Sony - PlayStation (Beetle PSX HW)",
                  "items": [
                    {
                      "path": "/storage/emulated/0/ROMs/PS1/Castlevania.chd",
                      "label": "Castlevania - Symphony of the Night (USA)",
                      "core_path": "/data/data/com.retroarch/cores/mednafen_psx_hw_libretro_android.so",
                      "core_name": "Sony - PlayStation (Beetle PSX HW)",
                      "crc32": "00000000|crc",
                      "db_name": "Sony - PlayStation.lpl"
                    }
                  ]
                }
                """.trimIndent()

            ProcessCmdlineProvider.textFileReader = { path ->
                if (path.contains("content_history.lpl")) {
                    retroArchHistoryJson
                } else {
                    null
                }
            }

            // Assert starting at default profile
            assertEquals(defaultProfile.id, MacroPadState.activeProfileId.value)

            // 2. RetroArch comes to foreground
            AutoSwitchCoordinator.onPackageChanged("com.retroarch")

            // 3. Verify EmulatorDetectionFunnel parses active session
            val session = EmulatorDetectionFunnel.activeSession.value
            assertNotNull("Expected active game session detected for RetroArch", session)
            assertEquals("com.retroarch", session?.packageName)
            assertEquals("Castlevania - Symphony of the Night (USA)", session?.gameTitle)
            assertEquals("retroarch", session?.systemId)
            assertEquals("/storage/emulated/0/ROMs/PS1/Castlevania.chd", session?.romPath)
            assertEquals("Castlevania.chd", session?.romIdentifier)

            // 4. Verify AutoSwitchCoordinator switched MacroPadState to PS1 profile
            assertEquals(ps1Profile.id, MacroPadState.activeProfileId.value)

            // 5. Verify AppStateManager state is updated
            assertEquals("com.retroarch", AppStateManager.focusedAppPackageName.value)
            assertEquals("/storage/emulated/0/ROMs/PS1/Castlevania.chd", AppStateManager.focusedRomPath.value)
            assertEquals("Castlevania.chd", AppStateManager.focusedRomIdentifier.value)

            // 6. User switches to browser app -> session cleared and auto-switches to browser profile
            AutoSwitchCoordinator.onPackageChanged("com.android.chrome")
            assertNull("Expected active game session cleared when exiting to browser", EmulatorDetectionFunnel.activeSession.value)
            assertEquals(browserProfile.id, MacroPadState.activeProfileId.value)
            assertEquals("com.android.chrome", AppStateManager.focusedAppPackageName.value)
        }

    @Test
    fun testPcsx2DetectionToAutoProfileSwitchPipelineE2E() =
        runTest {
            // 1. Mock PCSX2 / NetherSX2 recent games JSON
            val pcsx2RecentGamesJson =
                """
                [
                  {
                    "title": "Tactics Ogre: Let Us Cling Together",
                    "uri": "/storage/emulated/0/ROMs/PS2/Tactics Ogre.iso",
                    "serial": "SLUS-21345"
                  }
                ]
                """.trimIndent()

            ProcessCmdlineProvider.textFileReader = { path ->
                if (path.contains("recent") || path.contains("aethersx2") || path.contains("nether")) {
                    pcsx2RecentGamesJson
                } else {
                    null
                }
            }

            assertEquals(defaultProfile.id, MacroPadState.activeProfileId.value)

            // 2. NetherSX2 comes to foreground
            AutoSwitchCoordinator.onPackageChanged("xyz.aethersx2.android")

            // 3. Verify session detected
            val session = EmulatorDetectionFunnel.activeSession.value
            assertNotNull("Expected active game session for PCSX2/NetherSX2", session)
            assertEquals("xyz.aethersx2.android", session?.packageName)
            assertEquals("Tactics Ogre: Let Us Cling Together", session?.gameTitle)
            assertEquals("ps2", session?.systemId)
            assertEquals("/storage/emulated/0/ROMs/PS2/Tactics Ogre.iso", session?.romPath)
            assertEquals("Tactics Ogre.iso", session?.romIdentifier)

            // 4. Verify MacroPadState switched to PS2 profile
            assertEquals(ps2Profile.id, MacroPadState.activeProfileId.value)
            assertEquals("xyz.aethersx2.android", AppStateManager.focusedAppPackageName.value)

            // 5. User switches to browser -> session cleared and switches to browser profile
            AutoSwitchCoordinator.onPackageChanged("com.android.chrome")
            assertNull(EmulatorDetectionFunnel.activeSession.value)
            assertEquals(browserProfile.id, MacroPadState.activeProfileId.value)
        }
}
