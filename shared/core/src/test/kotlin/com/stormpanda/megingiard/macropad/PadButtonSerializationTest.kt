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

    @Test
    fun `dimensions below minimum or negative are clamped to MP_BUTTON_MIN_SIZE_DP`() {
        val jsonStr =
            """
            {
                "id": "btn-too-small",
                "label": "Small",
                "posX": 0.5,
                "posY": 0.5,
                "action": { "type": "keyboard_key", "keycode": 30, "label": "A" },
                "widthDp": 10.0,
                "heightDp": -50.0
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(jsonStr)
        assertEquals(MP_BUTTON_MIN_SIZE_DP, decoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_MIN_SIZE_DP, decoded.heightDp, 0.001f)
    }

    @Test
    fun `partial dimensions deserialize with missing dimension falling back to base unit`() {
        val jsonStr =
            """
            {
                "id": "btn-partial",
                "label": "Partial",
                "posX": 0.5,
                "posY": 0.5,
                "action": { "type": "keyboard_key", "keycode": 30, "label": "A" },
                "widthDp": 90.0
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(jsonStr)
        assertEquals(90f, decoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.heightDp, 0.001f)
    }

    @Test
    fun `trackpoint button normalizes non-square dimensions to equal max size`() {
        val jsonStr =
            """
            {
                "id": "btn-trackpoint-nonsquare",
                "label": "TP",
                "posX": 0.5,
                "posY": 0.5,
                "action": {
                    "type": "trackpoint",
                    "mode": "virtual_touch"
                },
                "widthDp": 100.0,
                "heightDp": 150.0
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(jsonStr)
        assertEquals(150f, decoded.widthDp, 0.001f)
        assertEquals(150f, decoded.heightDp, 0.001f)
    }

    @Test
    fun `dimensions with Infinity or NaN deserialize clamped to base unit`() {
        val specialJson =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                allowSpecialFloatingPointValues = true
            }
        val button =
            PadButton(
                id = "btn-inf",
                label = "Inf",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = Float.POSITIVE_INFINITY,
                heightDp = Float.NEGATIVE_INFINITY,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val encoded = specialJson.encodeToString(button)
        val decoded = specialJson.decodeFromString<PadButton>(encoded)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.heightDp, 0.001f)

        val nanButton =
            PadButton(
                id = "btn-nan",
                label = "NaN",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = Float.NaN,
                heightDp = Float.NaN,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val nanEncoded = specialJson.encodeToString(nanButton)
        val nanDecoded = specialJson.decodeFromString<PadButton>(nanEncoded)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, nanDecoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, nanDecoded.heightDp, 0.001f)
    }

    @Test
    fun `withActionSizing for ScrollWheel enforces minimum height of 120 dp`() {
        val button =
            PadButton(
                id = "btn-sw",
                label = "SW",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = 60f,
                heightDp = 60f,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val updated = button.withActionSizing(PadAction.ScrollWheel)
        assertEquals(60f, updated.widthDp, 0.001f)
        assertEquals(MP_SCROLL_WHEEL_MIN_HEIGHT_DP, updated.heightDp, 0.001f)

        // Preserves taller height if already >= 120 dp
        val tallButton = button.copy(heightDp = 180f)
        val updatedTall = tallButton.withActionSizing(PadAction.ScrollWheel)
        assertEquals(180f, updatedTall.heightDp, 0.001f)
    }

    @Test
    fun `withActionSizing for TrackpointMove enforces 1 to 1 aspect ratio, CIRCLE shape, and minimum 120 dp`() {
        val button =
            PadButton(
                id = "btn-tp",
                label = "TP",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = 60f,
                heightDp = 80f,
                buttonShape = ButtonShape.SQUARE,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val updated = button.withActionSizing(PadAction.TrackpointMove())
        assertEquals(MP_TRACKPOINT_DEFAULT_SIZE_DP, updated.widthDp, 0.001f)
        assertEquals(MP_TRACKPOINT_DEFAULT_SIZE_DP, updated.heightDp, 0.001f)
        assertEquals(ButtonShape.CIRCLE, updated.buttonShape)

        // Expands both dimensions to the larger one if > 120 dp
        val largeButton = button.copy(widthDp = 160f, heightDp = 100f)
        val updatedLarge = largeButton.withActionSizing(PadAction.TrackpointMove())
        assertEquals(160f, updatedLarge.widthDp, 0.001f)
        assertEquals(160f, updatedLarge.heightDp, 0.001f)
    }

    @Test
    fun `withActionSizing for KeyboardKey leaves existing dimensions and shape unchanged`() {
        val button =
            PadButton(
                id = "btn-norm",
                label = "K",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = 75f,
                heightDp = 95f,
                buttonShape = ButtonShape.ICON_ONLY,
                action = PadAction.ScrollWheel,
            )
        val updated = button.withActionSizing(PadAction.KeyboardKey(30, "A"))
        assertEquals(75f, updated.widthDp, 0.001f)
        assertEquals(95f, updated.heightDp, 0.001f)
        assertEquals(ButtonShape.ICON_ONLY, updated.buttonShape)
    }
}
