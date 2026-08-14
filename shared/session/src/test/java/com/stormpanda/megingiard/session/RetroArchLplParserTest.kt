package com.stormpanda.megingiard.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RetroArchLplParserTest {
    @Test
    fun parseMostRecentSession_validSnesPlaylist_returnsCorrectActiveGameSession() {
        val json =
            """
            {
              "version": "1.5",
              "items": [
                {
                  "path": "/storage/6914-318F/ROMs/SNES/Legend of Zelda, The - A Link to the Past (Germany).sfc",
                  "label": "Legend of Zelda, The - A Link to the Past (Germany)",
                  "core_path": "/data/data/com.retroarch.aarch64/cores/bsnes_libretro_android.so",
                  "core_name": "Nintendo - SNES / SFC (bsnes)"
                },
                {
                  "path": "/storage/6914-318F/ROMs/SNES/Super Mario World.sfc",
                  "label": "Super Mario World",
                  "core_path": "/data/data/com.retroarch.aarch64/cores/snes9x_libretro_android.so",
                  "core_name": "Nintendo - SNES / SFC (Snes9x)"
                }
              ]
            }
            """.trimIndent()

        val session = RetroArchLplParser.parseMostRecentSession("com.retroarch.aarch64", json)

        assertNotNull(session)
        assertEquals("com.retroarch.aarch64", session?.packageName)
        assertEquals("Legend of Zelda, The - A Link to the Past (Germany)", session?.gameTitle)
        assertEquals("snes", session?.systemId)
        assertEquals("Nintendo - SNES / SFC (bsnes)", session?.coreOrBackend)
        assertEquals(
            "/storage/6914-318F/ROMs/SNES/Legend of Zelda, The - A Link to the Past (Germany).sfc",
            session?.romPath,
        )
        assertEquals("Legend of Zelda, The - A Link to the Past (Germany).sfc", session?.romIdentifier)
    }

    @Test
    fun parseMostRecentSession_emptyOrInvalidJson_returnsNull() {
        assertNull(RetroArchLplParser.parseMostRecentSession("com.retroarch", ""))
        assertNull(RetroArchLplParser.parseMostRecentSession("com.retroarch", "{ \"items\": [] }"))
        assertNull(RetroArchLplParser.parseMostRecentSession("com.retroarch", "invalid json"))
    }

    @Test
    fun parseMostRecentSession_realDeviceLplWithDefaultFields_returnsCorrectSession() {
        val json =
            """
            {
              "version": "1.5",
              "default_core_path": "/data/data/com.retroarch.aarch64/cores/some_default.so",
              "default_core_name": "Some Default Core",
              "items": [
                {
                  "path": "/storage/emulated/0/ROMs/GBC/Super Mario Land 3.gb",
                  "label": "",
                  "core_path": "/data/data/com.retroarch.aarch64/cores/gambatte_libretro_android.so",
                  "core_name": "Nintendo - Game Boy / Color (Gambatte)"
                }
              ]
            }
            """.trimIndent()

        val session = RetroArchLplParser.parseMostRecentSession("com.retroarch.aarch64", json)

        assertNotNull(session)
        assertEquals("Super Mario Land 3", session?.gameTitle)
        assertEquals("gbc", session?.systemId)
        assertEquals("/storage/emulated/0/ROMs/GBC/Super Mario Land 3.gb", session?.romPath)
        assertEquals("Nintendo - Game Boy / Color (Gambatte)", session?.coreOrBackend)
    }

    @Test
    fun resolveSystemId_detectsSystemByExtensionAndCore() {
        assertEquals("snes", RetroArchLplParser.resolveSystemId("/roms/game.sfc", null, "bsnes"))
        assertEquals("n64", RetroArchLplParser.resolveSystemId("/roms/mario.z64", null, "Mupen64Plus"))
        assertEquals("gba", RetroArchLplParser.resolveSystemId("/roms/pokemon.gba", null, "mGBA"))
        assertEquals("nes", RetroArchLplParser.resolveSystemId("/roms/mario.nes", null, "Nestopia"))
        assertEquals("retroarch", RetroArchLplParser.resolveSystemId("/roms/game.unknown", null, "custom_core"))
    }

    @Test
    fun deriveGameTitle_usesLabelOrFallbackToFilename() {
        assertEquals("Super Mario", RetroArchLplParser.deriveGameTitle("Super Mario", "/path/to/game.sfc"))
        assertEquals("game", RetroArchLplParser.deriveGameTitle("DETECT", "/path/to/game.sfc"))
        assertEquals("game", RetroArchLplParser.deriveGameTitle(null, "/path/to/game.sfc"))
        assertNull(RetroArchLplParser.deriveGameTitle(null, null))
    }
}
