package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JSON serialization and backward-compatible deserialization for [PadButton].
 */
class PadButtonSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `modern PadButton with stepless dimensions survives JSON round-trip`() {
        val button =
            PadButton(
                id = "btn-stepless-1",
                posX = 0.35f,
                posY = 0.65f,
                label = "Custom",
                action = PadAction.KeyboardKey(30, "A"),
                widthDp = 95f,
                heightDp = 140f,
            )

        val encoded = json.encodeToString(button)
        // Verify widthDp and heightDp are present, and buttonSize is NOT present
        assertTrue(encoded.contains("\"widthDp\":95.0") || encoded.contains("\"widthDp\":95"))
        assertTrue(encoded.contains("\"heightDp\":140.0") || encoded.contains("\"heightDp\":140"))
        assertFalse(encoded.contains("\"buttonSize\""))

        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(button, decoded)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_2X1 deserializes to 120x60 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-1",
                "posX": 0.5,
                "posY": 0.5,
                "label": "Wide",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 30,
                    "label": "A"
                },
                "buttonSize": "SIZE_2X1"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(120f, decoded.widthDp, 0.01f)
        assertEquals(60f, decoded.heightDp, 0.01f)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_1X2 deserializes to 60x120 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-2",
                "posX": 0.2,
                "posY": 0.3,
                "label": "Tall",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 31,
                    "label": "S"
                },
                "buttonSize": "SIZE_1X2"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(60f, decoded.widthDp, 0.01f)
        assertEquals(120f, decoded.heightDp, 0.01f)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_2X2 deserializes to 120x120 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-3",
                "posX": 0.4,
                "posY": 0.4,
                "label": "Quad",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 32,
                    "label": "D"
                },
                "buttonSize": "SIZE_2X2"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(120f, decoded.widthDp, 0.01f)
        assertEquals(120f, decoded.heightDp, 0.01f)
    }

    @Test
    fun `legacy TrackpointMove action migrates size to widthDp and heightDp`() {
        val trackpointSmallJson =
            """
            {
                "id": "btn-trackpoint-small",
                "posX": 0.5,
                "posY": 0.5,
                "label": "TP Small",
                "action": {
                    "type": "trackpoint",
                    "size": "SMALL"
                }
            }
            """.trimIndent()

        val decodedSmall = json.decodeFromString<PadButton>(trackpointSmallJson)
        assertEquals(90f, decodedSmall.widthDp, 0.01f)
        assertEquals(90f, decodedSmall.heightDp, 0.01f)

        val trackpointLargeJson =
            """
            {
                "id": "btn-trackpoint-large",
                "posX": 0.5,
                "posY": 0.5,
                "label": "TP Large",
                "action": {
                    "type": "trackpoint",
                    "size": "LARGE"
                }
            }
            """.trimIndent()

        val decodedLarge = json.decodeFromString<PadButton>(trackpointLargeJson)
        assertEquals(180f, decodedLarge.widthDp, 0.01f)
        assertEquals(180f, decodedLarge.heightDp, 0.01f)
    }

    @Test
    fun `legacy JSON without buttonSize or dimensions defaults to 60x60 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-default",
                "posX": 0.5,
                "posY": 0.5,
                "label": "Default",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 33,
                    "label": "F"
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.widthDp, 0.01f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.heightDp, 0.01f)
    }
}
