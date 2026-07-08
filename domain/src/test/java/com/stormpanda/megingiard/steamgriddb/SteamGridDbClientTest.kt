package com.stormpanda.megingiard.steamgriddb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGridDbClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDeserializeGameAutocompleteResponse() {
        val jsonResponse = """
            {
              "success": true,
              "data": [
                {
                  "id": 2254,
                  "name": "Half-Life 2",
                  "types": ["steam"],
                  "verified": true
                },
                {
                  "id": 21207,
                  "name": "Half-Life",
                  "types": ["steam"],
                  "verified": false
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SteamGridDbResponse<List<SteamGridDbGame>>>(jsonResponse)
        assertTrue(parsed.success)
        assertEquals(2, parsed.data.size)

        val game1 = parsed.data[0]
        assertEquals(2254, game1.id)
        assertEquals("Half-Life 2", game1.name)
        assertTrue(game1.verified)

        val game2 = parsed.data[1]
        assertEquals(21207, game2.id)
        assertEquals("Half-Life", game2.name)
        assertTrue(!game2.verified)
    }

    @Test
    fun testDeserializeGridsResponse() {
        val jsonResponse = """
            {
              "success": true,
              "data": [
                {
                  "id": 12345,
                  "score": 10,
                  "style": "alternate",
                  "width": 600,
                  "height": 900,
                  "mime": "image/png",
                  "thumb": "https://cdn.steamgriddb.com/thumb/1.png",
                  "url": "https://cdn.steamgriddb.com/grid/1.png"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SteamGridDbResponse<List<SteamGridDbImage>>>(jsonResponse)
        assertTrue(parsed.success)
        assertEquals(1, parsed.data.size)

        val image = parsed.data[0]
        assertEquals(12345, image.id)
        assertEquals(10, image.score)
        assertEquals("alternate", image.style)
        assertEquals(600, image.width)
        assertEquals(900, image.height)
        assertEquals("image/png", image.mime)
        assertEquals("https://cdn.steamgriddb.com/thumb/1.png", image.thumb)
        assertEquals("https://cdn.steamgriddb.com/grid/1.png", image.url)
    }
}
