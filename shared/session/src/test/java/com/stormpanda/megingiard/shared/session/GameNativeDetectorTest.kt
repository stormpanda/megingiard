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
        assertEquals("Baba Is You.steam", session?.romPath)
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
        assertEquals("BALL x PIT.steam", session?.romPath)
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
}
