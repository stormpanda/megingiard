package com.stormpanda.megingiard.steamgriddb

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
        // 429 -> RateLimited
        val err429 = mapHttpError(429, "Too many requests")
        assertTrue(err429 is SteamGridDbException.RateLimited)

        // 502, 503, 504 -> ServiceUnavailable
        val err502 = mapHttpError(502, "Bad Gateway")
        assertTrue(err502 is SteamGridDbException.ServiceUnavailable)
        val err503 = mapHttpError(503, "Service Unavailable")
        assertTrue(err503 is SteamGridDbException.ServiceUnavailable)
        val err504 = mapHttpError(504, "Gateway Timeout")
        assertTrue(err504 is SteamGridDbException.ServiceUnavailable)

        // Other status codes -> ApiError
        val err400 = mapHttpError(400, "Bad Request")
        assertTrue(err400 is SteamGridDbException.ApiError)
        assertEquals("HTTP error 400: Bad Request", err400.message)

        val err500 = mapHttpError(500, "Internal Server Error")
        assertTrue(err500 is SteamGridDbException.ApiError)
        assertEquals("HTTP error 500: Internal Server Error", err500.message)
    }

    @Test
    fun testMapNetworkError() {
        // UnknownHostException -> Offline
        val errOffline = mapNetworkError(UnknownHostException("No address associated with hostname"))
        assertTrue(errOffline is SteamGridDbException.Offline)

        // ConnectException -> ServiceUnavailable
        val errConnect = mapNetworkError(ConnectException("Connection refused"))
        assertTrue(errConnect is SteamGridDbException.ServiceUnavailable)

        // SocketTimeoutException -> ServiceUnavailable
        val errTimeout = mapNetworkError(SocketTimeoutException("Read timed out"))
        assertTrue(errTimeout is SteamGridDbException.ServiceUnavailable)

        // Other exceptions -> Unknown
        val genericErr = mapNetworkError(NullPointerException("Oops"))
        assertTrue(genericErr is SteamGridDbException.Unknown)
        assertEquals("Oops", genericErr.cause?.message)
    }

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
