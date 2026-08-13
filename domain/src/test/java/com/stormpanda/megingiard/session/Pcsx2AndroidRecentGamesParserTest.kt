package com.stormpanda.megingiard.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Pcsx2AndroidRecentGamesParserTest {
    @Test
    fun parseMostRecentSession_validPcsx2AndroidJson_returnsMostRecentActiveGameSession() {
        val json =
            """
            [
              {
                "uri": "content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fps2/document/6914-318F%3AROMs%2Fps2%2FMarvel%20vs.%20Capcom%202%20-%20New%20Age%20of%20Heroes%20(Europe).bin",
                "title": "Marvel vs. Capcom 2",
                "serial": "SLES-51174",
                "ext": "BIN",
                "platform": "ps2"
              },
              {
                "uri": "content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fps2/document/6914-318F%3AROMs%2Fps2%2FMidnight%20Club%203%20-%20DUB%20Edition%20Remix%20(USA).iso",
                "title": "Midnight Club 3 - DUB Edition Remix",
                "serial": "SLUS-21355",
                "ext": "ISO",
                "platform": "ps2"
              }
            ]
            """.trimIndent()

        val session = Pcsx2AndroidRecentGamesParser.parseMostRecentSession("com.armsx2", json)

        assertNotNull(session)
        assertEquals("com.armsx2", session?.packageName)
        assertEquals("Marvel vs. Capcom 2", session?.gameTitle)
        assertEquals("ps2", session?.systemId)
        assertEquals("PCSX2", session?.coreOrBackend)
        assertEquals("/storage/6914-318F/ROMs/ps2/Marvel vs. Capcom 2 - New Age of Heroes (Europe).bin", session?.romPath)
    }

    @Test
    fun parseMostRecentSession_emptyOrInvalidJson_returnsNull() {
        assertNull(Pcsx2AndroidRecentGamesParser.parseMostRecentSession("com.armsx2", ""))
        assertNull(Pcsx2AndroidRecentGamesParser.parseMostRecentSession("com.armsx2", "[]"))
        assertNull(Pcsx2AndroidRecentGamesParser.parseMostRecentSession("com.armsx2", "{ \"invalid\": true }"))
    }

    @Test
    fun parseMostRecentSession_missingTitle_derivesTitleFromUriOrSerial() {
        val jsonWithUriOnly =
            """
            [
              {
                "uri": "content://com.android.externalstorage.documents/tree/6914-318F%3AROMs%2Fps2/document/6914-318F%3AROMs%2Fps2%2FGrand%20Theft%20Auto%20-%20San%20Andreas.iso",
                "serial": "SLUS-20946"
              }
            ]
            """.trimIndent()

        val session = Pcsx2AndroidRecentGamesParser.parseMostRecentSession("xyz.aethersx2.android", jsonWithUriOnly)
        assertNotNull(session)
        assertEquals("Grand Theft Auto - San Andreas", session?.gameTitle)

        val jsonWithSerialOnly =
            """
            [
              {
                "serial": "SLUS-20946"
              }
            ]
            """.trimIndent()

        val sessionSerial = Pcsx2AndroidRecentGamesParser.parseMostRecentSession("net.nethersx2.android", jsonWithSerialOnly)
        assertNotNull(sessionSerial)
        assertEquals("SLUS-20946", sessionSerial?.gameTitle)
    }
}
