package com.stormpanda.megingiard.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomSystemDefTest {
    @Test
    fun supportedSystems_isPopulated() {
        assertTrue(SUPPORTED_SYSTEMS.isNotEmpty())
        assertEquals(40, SUPPORTED_SYSTEMS.size)
    }

    @Test
    fun supportedSystems_allHaveUniqueIds() {
        val ids = SUPPORTED_SYSTEMS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun supportedSystems_allHaveNonEmptyDisplayNamesAndExtensions() {
        for (system in SUPPORTED_SYSTEMS) {
            assertTrue("System ${system.id} has blank displayName", system.displayName.isNotBlank())
            assertTrue("System ${system.id} has empty extensions", system.extensions.isNotEmpty())
            assertTrue("System ${system.id} has blank emulatorId", system.emulatorId.isNotBlank())
        }
    }

    @Test
    fun supportedSystems_containsMajorRetroSystems() {
        val systemMap = SUPPORTED_SYSTEMS.associateBy { it.id }
        assertNotNull(systemMap["snes"])
        assertNotNull(systemMap["nes"])
        assertNotNull(systemMap["gba"])
        assertNotNull(systemMap["ps1"])
        assertNotNull(systemMap["psp"])
        assertNotNull(systemMap["ps2"])
        assertNotNull(systemMap["n64"])
        assertNotNull(systemMap["switch"])
        assertNotNull(systemMap["pc"])

        assertEquals(EMULATOR_ID_RETROARCH, systemMap["snes"]?.emulatorId)
        assertEquals(EMULATOR_ID_YUZU, systemMap["switch"]?.emulatorId)
        assertEquals(EMULATOR_ID_GAMENATIVE, systemMap["pc"]?.emulatorId)
    }
}
