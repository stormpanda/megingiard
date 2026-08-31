package com.stormpanda.megingiard.media

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SteamGridDbClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testMapHttpError() {
        assertEquals(
            "Invalid API key",
            (mapHttpError(401, """{"success":false,"errors":["Invalid API key"]}""") as SteamGridDbException.Unauthorized).message,
        )
        assertEquals("Invalid or unauthorized API key", (mapHttpError(401, "") as SteamGridDbException.Unauthorized).message)
        assertTrue(mapHttpError(429, "Too many requests") is SteamGridDbException.RateLimited)
        listOf(502 to "Bad Gateway", 503 to "Service Unavailable", 504 to "Gateway Timeout").forEach { (code, body) ->
            assertTrue(mapHttpError(code, body) is SteamGridDbException.ServiceUnavailable)
        }
        assertEquals("HTTP 400: Bad Request", (mapHttpError(400, "Bad Request") as SteamGridDbException.ApiError).message)
        assertEquals(
            "HTTP 500: Internal Server Error",
            (mapHttpError(500, "Internal Server Error") as SteamGridDbException.ApiError).message,
        )
    }

    @Test
    fun testParseErrorText() {
        assertEquals("Invalid key format", parseErrorText("""{"success":false,"errors":["Invalid key format"]}"""))
        assertEquals("First error; Second error", parseErrorText("""{"success":false,"errors":["First error","Second error"]}"""))
        assertEquals("Plain text error", parseErrorText("Plain text error"))
        assertEquals("", parseErrorText(""))
    }

    @Test
    fun testMapNetworkError() {
        assertTrue(mapNetworkError(UnknownHostException("No address")) is SteamGridDbException.Offline)
        assertTrue(mapNetworkError(ConnectException("Connection refused")) is SteamGridDbException.ServiceUnavailable)
        assertTrue(mapNetworkError(SocketTimeoutException("Read timed out")) is SteamGridDbException.ServiceUnavailable)
        val genericErr = mapNetworkError(NullPointerException("Oops")) as SteamGridDbException.Unknown
        assertEquals("Oops", genericErr.cause?.message)
    }

    @Test
    fun testDeserializeGameAutocompleteResponse() {
        val jsonResponse =
            """
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
        val jsonResponse =
            """
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

    @Test
    fun testCleanSearchQuery() {
        assertEquals("Google Chrome", SteamGridDbClient.cleanSearchQuery("Google Chrome (Android)"))
        assertEquals("Galaxy Attack", SteamGridDbClient.cleanSearchQuery("Galaxy Attack (Premium)"))
        assertEquals("Super Mario World", SteamGridDbClient.cleanSearchQuery("Super Mario World [v1.0.2] (USA)"))
        assertEquals("Citra", SteamGridDbClient.cleanSearchQuery("Citra Emulator Mobile"))
        assertEquals("Minecraft", SteamGridDbClient.cleanSearchQuery("Minecraft Mobile Edition"))
        assertEquals("PPSSPP - PSP", SteamGridDbClient.cleanSearchQuery("PPSSPP - PSP Emulator"))
        assertEquals("SimpleApp", SteamGridDbClient.cleanSearchQuery("SimpleApp"))
        assertEquals("Mobile", SteamGridDbClient.cleanSearchQuery("Mobile"))
    }

    @Test
    fun testValidateTokenBlankKeyFails() {
        kotlinx.coroutines.runBlocking {
            val result = SteamGridDbClient.validateToken("")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SteamGridDbException.Unauthorized)
        }
    }
}
