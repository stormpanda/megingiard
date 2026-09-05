package com.stormpanda.megingiard.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameNativeDetectorTest {
    @Test
    fun parseSessionFromProcesses_validGame_returnsCorrectSession() {
        val procList =
            """
            PROC 29091 10142 app.gamenative
            PROC 29978 10142 start.exe /exec explorer /desktop=shell,1280x720 winhandler.exe
            PROC 30006 10142 C:\windows\system32\services.exe
            PROC 30101 10142 C:\Program Files (x86)\Steam\steamapps\common\Baba Is You\Baba Is You.exe
            """.trimIndent()

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

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

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

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

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNull(session)
    }

    @Test
    fun parseSessionFromProcesses_noMainProcess_returnsNull() {
        val procList =
            """
            PROC 30101 10142 C:\Program Files (x86)\Steam\steamapps\common\Baba Is You\Baba Is You.exe
            """.trimIndent()

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNull(session)
    }

    @Test
    fun detectActiveSession_unsupportedPackage_returnsNull() =
        kotlinx.coroutines.test.runTest {
            assertNull(GameNativeDetector.detectActiveSession("com.unsupported.pkg"))
        }

    @Test
    fun detectActiveSession_supportedPackage_readsProcessesAndResolves() =
        kotlinx.coroutines.test.runTest {
            ProcessCmdlineProvider.runningProcessesProvider = {
                """
                PROC 29091 10142 app.gamenative
                PROC 30101 10142 C:\Steam\steamapps\common\Celeste\Celeste.exe
                """.trimIndent()
            }
            val session = GameNativeDetector.detectActiveSession("app.gamenative")
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

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

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

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

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

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

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

        val session = GameNativeDetector.parseSessionFromProcesses("app.gamenative", procList)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertNull(session?.romPath)
        assertEquals("Witcher 3.steam", session?.romIdentifier)
        assertEquals("Witcher 3", session?.gameTitle)
    }
}
