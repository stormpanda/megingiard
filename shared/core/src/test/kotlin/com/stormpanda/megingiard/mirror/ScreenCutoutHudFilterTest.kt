package com.stormpanda.megingiard.mirror

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCutoutHudFilterTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `verify default transparency mask on ScreenCutout is false`() {
        val cutout = ScreenCutout.FULLSCREEN
        assertFalse(cutout.hasTransparencyMask)
        assertEquals(0, cutout.maskFeathering)
        assertEquals(0, cutout.maskTranslucency)
    }

    @Test
    fun `verify serialization roundtrip with hasTransparencyMask`() {
        val original =
            ScreenCutout(
                id = "hud_minimap",
                name = "Minimap HUD",
                srcX = 0.02f,
                srcY = 0.02f,
                srcWidth = 0.25f,
                srcHeight = 0.25f,
                destX = 0.1f,
                destY = 0.1f,
                destWidth = 0.4f,
                destHeight = 0.4f,
                hasTransparencyMask = true,
                maskFeathering = 4,
                maskTranslucency = 65,
            )

        val serialized = json.encodeToString(ScreenCutout.serializer(), original)
        assertTrue(serialized.contains("hasTransparencyMask"))
        assertTrue(serialized.contains("maskFeathering"))
        assertTrue(serialized.contains("maskTranslucency"))

        val deserialized = json.decodeFromString(ScreenCutout.serializer(), serialized)
        assertEquals(original, deserialized)
        assertTrue(deserialized.hasTransparencyMask)
        assertEquals(4, deserialized.maskFeathering)
        assertEquals(65, deserialized.maskTranslucency)
    }

    @Test
    fun `verify backwards compatibility when deserializing legacy JSON without hasTransparencyMask`() {
        val legacyJson =
            """
            {
                "id": "legacy_cutout",
                "name": "Old Cutout",
                "srcX": 0.0,
                "srcY": 0.0,
                "srcWidth": 1.0,
                "srcHeight": 1.0,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0,
                "opacity": 1.0,
                "keepAspectRatio": false,
                "motionSmoothing": false,
                "shape": "RECTANGLE"
            }
            """.trimIndent()

        val parsed = json.decodeFromString(ScreenCutout.serializer(), legacyJson)
        assertEquals("legacy_cutout", parsed.id)
        assertFalse(parsed.hasTransparencyMask)
        assertEquals(0, parsed.maskFeathering)
        assertEquals(0, parsed.maskTranslucency)
    }

    @Test
    fun `verify backwards compatibility when deserializing legacy JSON with old manual HUD fields`() {
        val legacyWithOldFields =
            """
            {
                "id": "cutout_with_old_hud",
                "name": "Old HUD Cutout",
                "srcX": 0.0,
                "srcY": 0.0,
                "srcWidth": 1.0,
                "srcHeight": 1.0,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0,
                "hudFilterMode": "LUMA_KEY",
                "lumaThreshold": 0.35,
                "chromaKeyColorHex": "#00FF00",
                "chromaTolerance": 0.2,
                "temporalSensitivity": 3.0,
                "edgeSmoothness": 0.05,
                "hasTransparencyMask": true
            }
            """.trimIndent()

        val parsed = json.decodeFromString(ScreenCutout.serializer(), legacyWithOldFields)
        assertEquals("cutout_with_old_hud", parsed.id)
        assertTrue(parsed.hasTransparencyMask)
    }
}
