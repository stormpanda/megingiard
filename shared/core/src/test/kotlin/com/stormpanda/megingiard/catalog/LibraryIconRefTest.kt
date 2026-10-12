package com.stormpanda.megingiard.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIconRefTest {
    @Test
    fun testSerializationAndParsing() {
        val map =
            mapOf(
                "com.example.app" to LibraryIconRef(id = 1234, type = LibraryIconRef.TYPE_ICONS),
                "rom.snes.smw" to LibraryIconRef(id = 5678, type = LibraryIconRef.TYPE_LOGOS),
            )
        val json = LibraryIconRef.serializeMap(map)
        assertTrue(json.contains("1234"))
        assertTrue(json.contains("icons"))
        assertTrue(json.contains("5678"))
        assertTrue(json.contains("logos"))

        val parsed = LibraryIconRef.parseMap(json)
        assertEquals(2, parsed.size)
        assertEquals(1234, parsed["com.example.app"]?.id)
        assertEquals(LibraryIconRef.TYPE_ICONS, parsed["com.example.app"]?.type)
        assertEquals(5678, parsed["rom.snes.smw"]?.id)
        assertEquals(LibraryIconRef.TYPE_LOGOS, parsed["rom.snes.smw"]?.type)
    }

    @Test
    fun testParseInvalidJsonReturnsEmptyMap() {
        val parsed = LibraryIconRef.parseMap("invalid-json{not_a_map")
        assertNotNull(parsed)
        assertTrue(parsed.isEmpty())
    }
}
