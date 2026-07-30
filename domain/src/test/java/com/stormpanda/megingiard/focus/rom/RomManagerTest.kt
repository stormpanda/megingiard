package com.stormpanda.megingiard.focus.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomManagerTest {
    @Test
    fun testDetectSystem_snes() {
        val filenames = listOf("Super Mario World.sfc", "Zelda.smc", "otherfile.txt")
        val systemId = RomManager.detectSystem(filenames)
        assertEquals("snes", systemId)
    }

    @Test
    fun testDetectSystem_gba() {
        val filenames = listOf("Pokemon Emerald.gba", "Mario Kart.gba")
        val systemId = RomManager.detectSystem(filenames)
        assertEquals("gba", systemId)
    }

    @Test
    fun testDetectSystem_unknown() {
        val filenames = listOf("unknown.xyz", "document.pdf")
        val systemId = RomManager.detectSystem(filenames)
        assertNull(systemId)
    }

    @Test
    fun testDetectSystem_empty() {
        val filenames = emptyList<String>()
        val systemId = RomManager.detectSystem(filenames)
        assertNull(systemId)
    }

    @Test
    fun testDetectSystem_pc() {
        val filenames = listOf("Cyberpunk.steam", "Portal.lnk")
        val systemId = RomManager.detectSystem(filenames)
        assertEquals("pc", systemId)
    }
}
