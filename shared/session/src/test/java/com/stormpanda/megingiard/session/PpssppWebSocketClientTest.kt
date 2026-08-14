package com.stormpanda.megingiard.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PpssppWebSocketClientTest {
    @Test
    fun queryActiveSession_serverNotRunning_returnsNull() =
        runBlocking {
            // Port 59999 should not have a listening server
            val session = PpssppWebSocketClient.queryActiveSession("org.ppsspp.ppsspp", port = 59999)
            assertNull(session)
        }

    @Test
    fun parseExactRomFilenameFromHttpListing_validListing_returnsCleanFilename() {
        val body = "/\n/Tactics Ogre - Let Us Cling Together (USA).iso\n/Virtua Tennis.iso\n"
        val filename = PpssppWebSocketClient.parseExactRomFilenameFromHttpListing(body)
        assertEquals("Tactics Ogre - Let Us Cling Together (USA).iso", filename)
    }

    @Test
    fun parseExactRomFilenameFromHttpListing_emptyOrSlashOnlyOrSystemEndpoints_returnsNull() {
        assertNull(PpssppWebSocketClient.parseExactRomFilenameFromHttpListing("/"))
        assertNull(PpssppWebSocketClient.parseExactRomFilenameFromHttpListing("/\n/debugger\n"))
        assertNull(PpssppWebSocketClient.parseExactRomFilenameFromHttpListing("/\n/upload\n"))
        assertNull(PpssppWebSocketClient.parseExactRomFilenameFromHttpListing("/\n\n"))
        assertNull(PpssppWebSocketClient.parseExactRomFilenameFromHttpListing(""))
    }

    @Test
    fun parseExactRomFilenameFromHttpListing_mixedEndpointsAndRom_returnsRomFilename() {
        val body = "/\n/Tactics Ogre (USA).iso\n/debugger\n"
        val filename = PpssppWebSocketClient.parseExactRomFilenameFromHttpListing(body)
        assertEquals("Tactics Ogre (USA).iso", filename)
    }
}
