package com.stormpanda.megingiard.focus.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameNativeDetectorTest {
    @Test
    fun parseSessionFromLog_appIdEnvVar_returnsCorrectFallbackSession() {
        val log =
            """
            08-04 10:00:00.123 1234 1234 I GameNative: Environment: STEAM_APP_ID=620
            08-04 10:00:00.456 1234 1234 D GameNative: wine: starting L"C:\Program Files\Steam\steamapps\common\Portal 2\portal2.exe"
            """.trimIndent()

        val session = GameNativeDetector.parseSessionFromLog("app.gamenative", log)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertEquals("620.steam", session?.romPath)
        assertEquals("Portal 2", session?.gameTitle)
    }

    @Test
    fun parseSessionFromLog_onlyExePath_returnsCorrectFallbackSession() {
        val log =
            """
            08-04 10:00:00.456 1234 1234 D GameNative: wine: starting L"C:\Program Files\GOG Galaxy\Games\The Witcher 3\witcher3.exe"
            """.trimIndent()

        val session = GameNativeDetector.parseSessionFromLog("app.gamenative", log)

        assertNotNull(session)
        assertEquals("app.gamenative", session?.packageName)
        assertEquals("pc", session?.systemId)
        assertEquals("The Witcher 3.steam", session?.romPath)
        assertEquals("The Witcher 3", session?.gameTitle)
    }

    @Test
    fun parseSessionFromLog_noAppIdOrPath_returnsNull() {
        val log =
            """
            08-04 10:00:00.123 1234 1234 I GameNative: Started container successfully.
            08-04 10:00:01.000 1234 1234 I GameNative: XServer started.
            """.trimIndent()

        val session = GameNativeDetector.parseSessionFromLog("app.gamenative", log)

        assertNull(session)
    }
}
