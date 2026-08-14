package com.stormpanda.megingiard.rom

import org.junit.Assert.assertEquals
import org.junit.Test

class RomNameCleanerTest {
    @Test
    fun testCleanRomName_RemovesRegionAndDump() {
        assertEquals("Pokemon - Emerald Version", cleanRomName("Pokemon - Emerald Version (USA, Europe)"))
        assertEquals("Super Mario World", cleanRomName("Super Mario World (USA) [!]"))
        assertEquals("Chrono Trigger", cleanRomName("Chrono Trigger (U) [!]"))
        assertEquals("Sonic the Hedgehog", cleanRomName("Sonic the Hedgehog (Europe) (En,Fr,De,Es,It)"))
    }

    @Test
    fun testCleanRomName_RemovesVersionInfo() {
        assertEquals("The Legend of Zelda", cleanRomName("The Legend of Zelda (Rev 1)"))
        assertEquals("Metroid Fusion", cleanRomName("Metroid Fusion (USA) (v1.1)"))
        assertEquals("Grand Theft Auto", cleanRomName("Grand Theft Auto [v1.0.2] (USA)"))
    }

    @Test
    fun testCleanRomName_PreservesDiscAndPart() {
        assertEquals("Final Fantasy VII (Disc 1)", cleanRomName("Final Fantasy VII (USA) (Disc 1)"))
        assertEquals("Metal Gear Solid (Disc 2)", cleanRomName("Metal Gear Solid (Disc 2)"))
        assertEquals("Silent Hill (Disk A)", cleanRomName("Silent Hill (Disk A)"))
        assertEquals("Resident Evil (Disc 1 of 2)", cleanRomName("Resident Evil (USA) (Disc 1 of 2)"))
        assertEquals("Double Dragon (Part II)", cleanRomName("Double Dragon (Part II)"))
        assertEquals("Sonic Adventure (CD 2)", cleanRomName("Sonic Adventure (CD 2)"))
        assertEquals("Shenmue (D2)", cleanRomName("Shenmue (D2)"))
        assertEquals("Grandia (CD1)", cleanRomName("Grandia (CD1)"))
        assertEquals("Ridge Racer (Side A)", cleanRomName("Ridge Racer (Side A)"))
        assertEquals("Castlevania (Tape 1)", cleanRomName("Castlevania (Tape 1)"))
        assertEquals("Gran Turismo (p2)", cleanRomName("Gran Turismo (p2)"))
    }

    @Test
    fun testCleanRomName_WhitespaceNormalization() {
        assertEquals("Super Mario World", cleanRomName("  Super   Mario   World (USA)  "))
        assertEquals("Pokemon Emerald", cleanRomName("Pokemon   Emerald  (USA)"))
        assertEquals("Chrono Trigger", cleanRomName("Chrono Trigger(USA)"))
    }

    @Test
    fun testCleanRomName_BlankOrEmpty() {
        assertEquals("", cleanRomName(""))
        assertEquals("   ", cleanRomName("   "))
    }
}
