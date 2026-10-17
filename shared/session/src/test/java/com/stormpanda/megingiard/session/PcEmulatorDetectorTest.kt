package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SYSTEM_ID_PC
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PcEmulatorDetectorTest {
    @Test
    fun parseSessionFromProcesses_validGame_returnsCorrectSession() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 29978 10142 start.exe /exec explorer /desktop=shell,1280x720 winhandler.exe
            PROC 30006 10142 C:\windows\system32\services.exe
            PROC 30101 10142 C:\Program Files (x86)\Steam\steamapps\common\Baba Is You\Baba Is You.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Baba Is You.steam", session?.romIdentifier)
        assertEquals("Baba Is You", session?.gameTitle)
    }

    @Test
    fun parseSessionFromProcesses_forwardSlashes_returnsCorrectSession() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 29978 10142 start.exe /exec explorer /desktop=shell,1280x720 winhandler.exe
            PROC 30006 10142 C:\windows\system32\services.exe
            PROC 30101 10142 Z:/home/sandbox/steamapps/common/BALL x PIT/BALLxPIT.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("BALL x PIT.steam", session?.romIdentifier)
        assertEquals("BALL x PIT", session?.gameTitle)
    }

    @Test
    fun parseSessionFromProcesses_onlySystemHelpers_returnsNull() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 29978 10142 start.exe /exec explorer /desktop=shell,1280x720 winhandler.exe
            PROC 30006 10142 C:\windows\system32\services.exe
            PROC 30022 10142 C:\windows\system32\winedevice.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNull(session)
    }

    @Test
    fun parseSessionFromProcesses_noMainProcess_returnsNull() {
        val procList =
            """
            PROC 30101 10142 C:\Program Files (x86)\Steam\steamapps\common\Baba Is You\Baba Is You.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNull(session)
    }

    @Test
    fun detectActiveSession_unsupportedPackage_returnsNull() =
        runTest {
            assertNull(PcEmulatorDetector.detectActiveSession("com.unsupported.pkg"))
        }

    @Test
    fun detectActiveSession_supportedPackage_readsProcessesAndResolves() =
        runTest {
            ProcessCmdlineProvider.runningProcessesProvider = {
                """
                PROC 29091 10142 app.gamenative
                PROC 30101 10142 C:\Steam\steamapps\common\Celeste\Celeste.exe
                """.trimIndent()
            }
            val session = PcEmulatorDetector.detectActiveSession("app.gamenative")
            assertNotNull(session)
            assertEquals("Celeste", session?.gameTitle)
            assertEquals("pc", session?.systemId)
        }

    @Test
    fun parseSessionFromProcesses_wineSystemDaemons_ignoresPlugplayAndRpcssAndResolvesGame() {
        val procList =
            """
            PROC 20932 10120 app.gamenative
            PROC 29680 10120 start.exe /exec explorer /desktop=shell,1280x720 winhandler.exe
            PROC 29714 10120 C:\windows\system32\services.exe
            PROC 29721 10120 C:\windows\system32\winedevice.exe
            PROC 29745 10120 C:\windows\system32\plugplay.exe
            PROC 29755 10120 C:\windows\system32\svchost.exe -k LocalServiceNetworkRestricted
            PROC 29766 10120 C:\windows\system32\winedevice.exe
            PROC 29785 10120 C:\windows\system32\explorer.exe /desktop=shell,1280x720 winhandler.exe "A:/Boltgun/Binaries/Win64/Boltgun-Win64-Shipping.exe"
            PROC 29795 10120 winhandler.exe "A:/Boltgun/Binaries/Win64/Boltgun-Win64-Shipping.exe"
            PROC 29803 10120 C:\windows\system32\rpcss.exe
            PROC 29816 10120 C:\windows\system32\tabtip.exe
            PROC 29828 10120 A:\Boltgun\Binaries\Win64\Boltgun-Win64-Shipping.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Boltgun.steam", session?.romIdentifier)
        assertEquals("Boltgun", session?.gameTitle)
    }

    @Test
    fun parseSessionFromProcesses_gameWithCommandLineArguments_returnsCorrectSession() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 30101 10142 "C:\Program Files (x86)\Steam\steamapps\common\Hollow Knight\hollow_knight.exe" -popupwindow -screen-fullscreen 1
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Hollow Knight.steam", session?.romIdentifier)
        assertEquals("Hollow Knight", session?.gameTitle)
    }

    @Test
    fun parseSessionFromProcesses_unrealShippingSuffixWithoutFolder_cleansSuffix() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 30101 10142 A:\Binaries\Win64\Boltgun-Win64-Shipping.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Boltgun.steam", session?.romIdentifier)
        assertEquals("Boltgun", session?.gameTitle)
    }

    @Test
    fun parseSessionFromProcesses_genericGamesFolder_resolvesSubFolder() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 30101 10142 C:\Games\Witcher 3\bin\x64\witcher3.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Witcher 3.steam", session?.romIdentifier)
        assertEquals("Witcher 3", session?.gameTitle)
    }

    @Test
    fun parseSessionFromProcesses_folderMismatch_correlatesWithFocusedRomContext() {
        val app =
            InstalledAppInfo(
                packageName = "rom:steam:20 Minutes Till Dawn",
                activityName = "",
                label = "20 Minutes Till Dawn",
                isRom = true,
                romPath = "/storage/emulated/0/ROMs/steam/20 Minutes Till Dawn.steam",
                systemId = SYSTEM_ID_PC,
            )
        RomManager.setRomAppsForTesting(listOf(app))
        try {
            val procList =
                """
                PROC 29091 10142 app.gamenative
                PROC 30101 10142 C:\Program Files (x86)\Steam\steamapps\common\20MinuteTillDawn\MinutesTillDawn.exe
                """.trimIndent()

            // When no focused ROM is provided and direct match fails, falls back to folderName
            val standaloneSession = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)
            assertNotNull(standaloneSession)
            assertEquals("20MinuteTillDawn.steam", standaloneSession?.romIdentifier)

            // When focused ROM context is provided, correlates cleanly to the focused ROM in RomManager
            val correlatedSession =
                PcEmulatorDetector.parseSessionFromProcesses(
                    packageName = "app.gamenative",
                    procList = procList,
                    focusedRomPath = "/storage/emulated/0/ROMs/steam/20 Minutes Till Dawn.steam",
                    focusedRomIdentifier = "20 Minutes Till Dawn.steam",
                )
            assertNotNull(correlatedSession)
            assertEquals("app.gamenative", correlatedSession?.packageName)
            assertEquals("pc", correlatedSession?.systemId)
            assertEquals("/storage/emulated/0/ROMs/steam/20 Minutes Till Dawn.steam", correlatedSession?.romPath)
            assertEquals("20 Minutes Till Dawn.steam", correlatedSession?.romIdentifier)
            assertEquals("20 Minutes Till Dawn", correlatedSession?.gameTitle)

            // Case-insensitive identifier matching also correlates successfully
            val caseInsensitiveSession =
                PcEmulatorDetector.parseSessionFromProcesses(
                    packageName = "app.gamenative",
                    procList = procList,
                    focusedRomPath = null,
                    focusedRomIdentifier = "20 minutes till dawn.steam",
                )
            assertNotNull(caseInsensitiveSession)
            assertEquals("/storage/emulated/0/ROMs/steam/20 Minutes Till Dawn.steam", caseInsensitiveSession?.romPath)
            assertEquals("20 Minutes Till Dawn.steam", caseInsensitiveSession?.romIdentifier)
        } finally {
            RomManager.setRomAppsForTesting(emptyList())
        }
    }

    @Test
    fun parseSessionFromProcesses_ignoresNonPcRomWithSameTitleInRomManager() {
        val switchRom =
            InstalledAppInfo(
                packageName = "rom:switch:Celeste",
                activityName = "",
                label = "Celeste",
                isRom = true,
                romPath = "/storage/emulated/0/ROMs/switch/Celeste [01002B00028F6000].nsp",
                systemId = SYSTEM_ID_SWITCH,
            )
        RomManager.setRomAppsForTesting(listOf(switchRom))
        try {
            val procList =
                """
                PROC 4100 10210 com.droiddeck.launcher
                PROC 4210 10210 /home/user/.local/share/Steam/steamapps/common/Celeste/Celeste.bin.x86_64
                """.trimIndent()

            val session = PcEmulatorDetector.parseSessionFromProcesses("com.droiddeck.launcher", procList)

            assertNotNull(session)
            assertEquals("com.droiddeck.launcher", session?.packageName)
            assertEquals(SYSTEM_ID_PC, session?.systemId)
            assertNull(session?.romPath)
            assertEquals("Celeste.steam", session?.romIdentifier)
            assertEquals("Celeste", session?.gameTitle)
        } finally {
            RomManager.setRomAppsForTesting(emptyList())
        }
    }

    @Test
    fun parseSessionFromProcesses_droidDeckProtonMultiSegmentCmdline_skipsProtonAndResolvesGame() {
        val procList =
            """
            PROC 4100 10210 com.droiddeck.launcher
            PROC 4120 10210 /home/user/.local/share/Steam/ubuntu12_32/steam -gamepadui
            PROC 4150 10210 C:\Program Files (x86)\Steam\steam.exe
            PROC 4180 10210 C:\Program Files (x86)\Steam\GameOverlayUI.exe -pid 4200
            PROC 4200 10210 /home/user/.local/share/Steam/ubuntu12_32/reaper SteamLaunch AppId=367520 -- "/home/user/.local/share/Steam/steamapps/common/Proton - Experimental/proton" waitforexitandrun "/home/user/.local/share/Steam/steamapps/common/Hollow Knight/hollow_knight.exe" -popupwindow
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("com.droiddeck.launcher", procList)

        assertNotNull(session)
        assertEquals("com.droiddeck.launcher", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Hollow Knight.steam", session?.romIdentifier)
        assertEquals("Hollow Knight", session?.gameTitle)
        assertEquals("DroidDeck", session?.coreOrBackend)
        assertEquals("367520", session?.titleId)
    }

    @Test
    fun parseSessionFromProcesses_droidDeckNativeLinuxGame_resolvesGameFromSteamappsCommon() {
        val procList =
            """
            PROC 4100 10210 com.droiddeck.launcher
            PROC 4120 10210 /home/user/.local/share/Steam/ubuntu12_32/steam -gamepadui
            PROC 4210 10210 /home/user/.local/share/Steam/steamapps/common/SteamLinuxRuntime_sniper/pressure-vessel/bin/pv-adverb -- /home/user/.local/share/Steam/steamapps/common/Celeste/Celeste.bin.x86_64
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("com.droiddeck.launcher", procList)

        assertNotNull(session)
        assertEquals("com.droiddeck.launcher", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Celeste.steam", session?.romIdentifier)
        assertEquals("Celeste", session?.gameTitle)
        assertEquals("DroidDeck", session?.coreOrBackend)
    }

    @Test
    fun parseSessionFromProcesses_droidDeckIdleBigPictureAndRuntimeHelpers_returnsNull() {
        val procList =
            """
            PROC 4100 10210 com.droiddeck.launcher
            PROC 4120 10210 /home/user/.local/share/Steam/ubuntu12_32/steam -gamepadui
            PROC 4130 10210 /home/user/.local/share/Steam/ubuntu12_64/steamwebhelper -lang=en_US
            PROC 4150 10210 C:\Program Files (x86)\Steam\steam.exe
            PROC 4160 10210 C:\Program Files (x86)\Steam\GameOverlayUI.exe
            PROC 4170 10210 C:\Program Files (x86)\Steam\steamerrorreporter64.exe
            PROC 4180 10210 /home/user/.local/share/Steam/steamapps/common/SteamLinuxRuntime_sniper/pressure-vessel/bin/pv-adverb
            PROC 4190 10210 /home/user/.local/share/Steam/steamapps/common/Steamworks Shared/_CommonRedist/vcredist/2022/vc_redist.x64.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("com.droiddeck.launcher", procList)

        assertNull(session)
    }

    @Test
    fun parseSessionFromProcesses_steamAppIdMatchesNumericSteamRomInRomManager() {
        val numericSteamRom =
            InstalledAppInfo(
                packageName = "rom:pc:367520",
                activityName = "",
                label = "Hollow Knight (Custom Label)",
                isRom = true,
                romPath = "/storage/emulated/0/ROMs/pc/367520.steam",
                systemId = SYSTEM_ID_PC,
            )
        RomManager.setRomAppsForTesting(listOf(numericSteamRom))
        try {
            val procList =
                """
                PROC 4100 10210 com.droiddeck.launcher
                PROC 4200 10210 /home/user/.local/share/Steam/ubuntu12_32/reaper SteamLaunch AppId=367520 -- "/home/user/.local/share/Steam/steamapps/common/Proton - Experimental/proton" waitforexitandrun "/home/user/.local/share/Steam/steamapps/common/Hollow_Knight_Dir/hk.exe"
                """.trimIndent()

            val session = PcEmulatorDetector.parseSessionFromProcesses("com.droiddeck.launcher", procList)

            assertNotNull(session)
            assertEquals("/storage/emulated/0/ROMs/pc/367520.steam", session?.romPath)
            assertEquals("367520.steam", session?.romIdentifier)
            assertEquals("Hollow Knight (Custom Label)", session?.gameTitle)
            assertEquals("367520", session?.titleId)
            assertEquals("DroidDeck", session?.coreOrBackend)
        } finally {
            RomManager.setRomAppsForTesting(emptyList())
        }
    }

    @Test
    fun parseSessionFromProcesses_steamappsCommonPreferredOverSubsequentGenericDriveProcess() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 30101 10142 C:\Program Files (x86)\Steam\steamapps\common\Celeste\Celeste.exe
            PROC 30250 10142 C:\Temp\SubprocessHelper\helper_tool.exe
            """.trimIndent()

        val session = PcEmulatorDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("Celeste", session?.gameTitle)
        assertEquals("Celeste.steam", session?.romIdentifier)
        assertEquals("GameNative", session?.coreOrBackend)
    }
}
