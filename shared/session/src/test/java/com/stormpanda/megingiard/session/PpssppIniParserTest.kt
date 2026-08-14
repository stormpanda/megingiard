package com.stormpanda.megingiard.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PpssppIniParserTest {
    @Test
    fun parseMostRecentSession_validPpssppIni_returnsMostRecentActiveGameSession() {
        val ini =
            """
            [General]
            FirstRun = False

            [Recent]
            FileName0 = /storage/emulated/0/ROMs/psp/Crisis Core - Final Fantasy VII (USA).iso
            FileName1 = /storage/emulated/0/ROMs/psp/Tekken 6.cso
            FileName2 =

            [Graphics]
            GraphicsBackend = 0
            """.trimIndent()

        val session = PpssppIniParser.parseMostRecentSession("org.ppsspp.ppsspp", ini)

        assertNotNull(session)
        assertEquals("org.ppsspp.ppsspp", session?.packageName)
        assertEquals("Crisis Core - Final Fantasy VII (USA)", session?.gameTitle)
        assertEquals("psp", session?.systemId)
        assertEquals("PPSSPP", session?.coreOrBackend)
        assertEquals("/storage/emulated/0/ROMs/psp/Crisis Core - Final Fantasy VII (USA).iso", session?.romPath)
    }

    @Test
    fun parseMostRecentSession_emptyOrInvalidIni_returnsNull() {
        assertNull(PpssppIniParser.parseMostRecentSession("org.ppsspp.ppsspp", ""))
        assertNull(PpssppIniParser.parseMostRecentSession("org.ppsspp.ppsspp", "   "))
        assertNull(PpssppIniParser.parseMostRecentSession("org.ppsspp.ppsspp", "[Graphics]\nBackend=1"))
    }

    @Test
    fun parseMostRecentSession_blankFileName0_returnsNull() {
        val ini =
            """
            [Recent]
            FileName0 =
            FileName1 = /storage/emulated/0/ROMs/psp/Game.iso
            """.trimIndent()

        assertNull(PpssppIniParser.parseMostRecentSession("org.ppsspp.ppsspp", ini))
    }

    @Test
    fun parseMostRecentSession_quotedPathAndCaseInsensitiveSection_parsesCorrectly() {
        val ini =
            """
            [recent]
            FileName0 = "/storage/6914-318F/ROMs/PSP/God of War - Ghost of Sparta.cso"
            """.trimIndent()

        val session = PpssppIniParser.parseMostRecentSession("org.ppsspp.ppssppgold", ini)

        assertNotNull(session)
        assertEquals("org.ppsspp.ppssppgold", session?.packageName)
        assertEquals("God of War - Ghost of Sparta", session?.gameTitle)
        assertEquals("psp", session?.systemId)
        assertEquals("/storage/6914-318F/ROMs/PSP/God of War - Ghost of Sparta.cso", session?.romPath)
    }

    @Test
    fun deriveGameTitle_handlesVariousExtensionsAndPathSeparators() {
        assertEquals("Tekken 6", PpssppIniParser.deriveGameTitle("/storage/sdcard/roms/psp/Tekken 6.cso"))
        assertEquals("Persona 3 Portable", PpssppIniParser.deriveGameTitle("C:\\Emulators\\PSP\\ROMs\\Persona 3 Portable.iso"))
        assertEquals("Castlevania - Dracula X Chronicles", PpssppIniParser.deriveGameTitle("/roms/Castlevania - Dracula X Chronicles.pbp"))
        assertEquals("Homebrew", PpssppIniParser.deriveGameTitle("/roms/Homebrew.elf"))
    }
}
