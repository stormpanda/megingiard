package com.stormpanda.megingiard.mirror

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCutoutAnchorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `verify default anchor properties on ScreenCutout`() {
        val cutout = ScreenCutout.FULLSCREEN
        assertFalse(cutout.customAnchorEnabled)
        assertEquals(0f, cutout.anchorSrcX, 0.001f)
        assertEquals(0f, cutout.anchorSrcY, 0.001f)
        assertEquals(DEFAULT_ANCHOR_SIZE, cutout.anchorSrcWidth, 0.001f)
        assertEquals(DEFAULT_ANCHOR_SIZE, cutout.anchorSrcHeight, 0.001f)
        assertNull(cutout.anchorCutoutId)
    }

    @Test
    fun `getEffectiveAnchorCrop returns cutout bounds when customAnchorEnabled is false`() {
        val cutout =
            ScreenCutout(
                id = "map",
                srcX = 0.1f,
                srcY = 0.2f,
                srcWidth = 0.3f,
                srcHeight = 0.4f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                customAnchorEnabled = false,
                anchorSrcX = 0.8f,
                anchorSrcY = 0.8f,
            )
        val crop = cutout.getEffectiveAnchorCrop()
        assertEquals(0.1f, crop.x, 0.001f)
        assertEquals(0.2f, crop.y, 0.001f)
        assertEquals(0.3f, crop.width, 0.001f)
        assertEquals(0.4f, crop.height, 0.001f)
    }

    @Test
    fun `getEffectiveAnchorCrop returns custom anchor bounds when customAnchorEnabled is true`() {
        val cutout =
            ScreenCutout(
                id = "map",
                srcX = 0.1f,
                srcY = 0.2f,
                srcWidth = 0.3f,
                srcHeight = 0.4f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                customAnchorEnabled = true,
                anchorSrcX = 0.02f,
                anchorSrcY = 0.03f,
                anchorSrcWidth = 0.12f,
                anchorSrcHeight = 0.12f,
            )
        val crop = cutout.getEffectiveAnchorCrop()
        assertEquals(0.02f, crop.x, 0.001f)
        assertEquals(0.03f, crop.y, 0.001f)
        assertEquals(0.12f, crop.width, 0.001f)
        assertEquals(0.12f, crop.height, 0.001f)
    }

    @Test
    fun `getEffectiveAnchorCrop resolves linked cutout bounds`() {
        val headPortrait =
            ScreenCutout(
                id = "paimon_head",
                srcX = 0.01f,
                srcY = 0.01f,
                srcWidth = 0.08f,
                srcHeight = 0.08f,
                destX = 0f,
                destY = 0f,
                destWidth = 0.2f,
                destHeight = 0.2f,
            )
        val minimap =
            ScreenCutout(
                id = "minimap",
                srcX = 0.05f,
                srcY = 0.05f,
                srcWidth = 0.35f,
                srcHeight = 0.35f,
                destX = 0.2f,
                destY = 0.2f,
                destWidth = 0.8f,
                destHeight = 0.8f,
                customAnchorEnabled = true,
                anchorCutoutId = "paimon_head",
            )
        val allCutouts = listOf(headPortrait, minimap)
        val crop = minimap.getEffectiveAnchorCrop(allCutouts)
        assertEquals(0.01f, crop.x, 0.001f)
        assertEquals(0.01f, crop.y, 0.001f)
        assertEquals(0.08f, crop.width, 0.001f)
        assertEquals(0.08f, crop.height, 0.001f)
    }

    @Test
    fun `getEffectiveAnchorCrop protects against cyclic linked cutouts`() {
        val cutoutA =
            ScreenCutout(
                id = "cutout_a",
                srcX = 0.1f,
                srcY = 0.1f,
                srcWidth = 0.2f,
                srcHeight = 0.2f,
                destX = 0f,
                destY = 0f,
                destWidth = 0.5f,
                destHeight = 0.5f,
                customAnchorEnabled = true,
                anchorCutoutId = "cutout_b",
                anchorSrcX = 0.15f,
                anchorSrcY = 0.15f,
                anchorSrcWidth = 0.25f,
                anchorSrcHeight = 0.25f,
            )
        val cutoutB =
            ScreenCutout(
                id = "cutout_b",
                srcX = 0.3f,
                srcY = 0.3f,
                srcWidth = 0.4f,
                srcHeight = 0.4f,
                destX = 0.5f,
                destY = 0.5f,
                destWidth = 0.5f,
                destHeight = 0.5f,
                customAnchorEnabled = true,
                anchorCutoutId = "cutout_a",
            )
        val allCutouts = listOf(cutoutA, cutoutB)
        // Should terminate safely without StackOverflowError or infinite loop
        val cropA = cutoutA.getEffectiveAnchorCrop(allCutouts)
        assertEquals(0.15f, cropA.x, 0.001f)
        assertEquals(0.15f, cropA.y, 0.001f)
    }

    @Test
    fun `verify serialization roundtrip with custom reference anchor properties`() {
        val original =
            ScreenCutout(
                id = "minimap_anchor",
                name = "Genshin Minimap",
                srcX = 0.05f,
                srcY = 0.05f,
                srcWidth = 0.35f,
                srcHeight = 0.35f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                freezeOnHudLoss = true,
                customAnchorEnabled = true,
                anchorSrcX = 0.01f,
                anchorSrcY = 0.02f,
                anchorSrcWidth = 0.09f,
                anchorSrcHeight = 0.11f,
                anchorCutoutId = "paimon_head",
            )

        val serialized = json.encodeToString(ScreenCutout.serializer(), original)
        assertTrue(serialized.contains("customAnchorEnabled"))
        assertTrue(serialized.contains("anchorSrcX"))
        assertTrue(serialized.contains("anchorCutoutId"))

        val deserialized = json.decodeFromString(ScreenCutout.serializer(), serialized)
        assertEquals(original, deserialized)
        assertTrue(deserialized.customAnchorEnabled)
        assertEquals(0.01f, deserialized.anchorSrcX, 0.001f)
        assertEquals(0.02f, deserialized.anchorSrcY, 0.001f)
        assertEquals(0.09f, deserialized.anchorSrcWidth, 0.001f)
        assertEquals(0.11f, deserialized.anchorSrcHeight, 0.001f)
        assertEquals("paimon_head", deserialized.anchorCutoutId)
    }

    @Test
    fun `verify backwards compatibility when deserializing legacy JSON without anchor properties`() {
        val legacyJson =
            """
            {
                "id": "legacy_cutout_v1",
                "name": "Old Cutout",
                "srcX": 0.1,
                "srcY": 0.2,
                "srcWidth": 0.5,
                "srcHeight": 0.5,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0
            }
            """.trimIndent()

        val parsed = json.decodeFromString(ScreenCutout.serializer(), legacyJson)
        assertEquals("legacy_cutout_v1", parsed.id)
        assertFalse(parsed.customAnchorEnabled)
        assertEquals(0f, parsed.anchorSrcX, 0.001f)
        assertEquals(0f, parsed.anchorSrcY, 0.001f)
        assertEquals(DEFAULT_ANCHOR_SIZE, parsed.anchorSrcWidth, 0.001f)
        assertEquals(DEFAULT_ANCHOR_SIZE, parsed.anchorSrcHeight, 0.001f)
        assertNull(parsed.anchorCutoutId)
    }
}
