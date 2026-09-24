package com.stormpanda.megingiard.mirror

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCutoutTest {
    @Test
    fun `verify ScreenCutout FULLSCREEN constant properties`() {
        val fullscreen = ScreenCutout.FULLSCREEN
        assertEquals("fullscreen_master", fullscreen.id)
        assertEquals(0f, fullscreen.srcX, 0.0001f)
        assertEquals(0f, fullscreen.srcY, 0.0001f)
        assertEquals(1f, fullscreen.srcWidth, 0.0001f)
        assertEquals(1f, fullscreen.srcHeight, 0.0001f)
        assertEquals(0f, fullscreen.destX, 0.0001f)
        assertEquals(0f, fullscreen.destY, 0.0001f)
        assertEquals(1f, fullscreen.destWidth, 0.0001f)
        assertEquals(1f, fullscreen.destHeight, 0.0001f)
        assertEquals(1f, fullscreen.opacity, 0.0001f)
        assertEquals(CutoutShape.RECTANGLE, fullscreen.shape)
        assertEquals(AspectRatioMode.TOP, fullscreen.aspectRatioMode)
        assertFalse(fullscreen.motionSmoothing)
        assertFalse(fullscreen.followTouch)
        assertFalse(fullscreen.touchProjectionEnabled)
        assertFalse(fullscreen.renderAboveMask)
        assertEquals(0, fullscreen.rotation)
        assertFalse(fullscreen.flipHorizontal)
        assertFalse(fullscreen.flipVertical)
        assertEquals(CutoutFlipMode.NONE, fullscreen.flipMode)
    }

    @Test
    fun `verify createDefault fits aspect ratio correctly`() {
        val cutout =
            ScreenCutout.createDefault(
                srcPixelWidth = 1920f,
                srcPixelHeight = 1080f,
                bottomPixelWidth = 1000f,
                bottomPixelHeight = 1000f,
            )
        assertEquals(0f, cutout.srcX, 0.0001f)
        assertEquals(0f, cutout.srcY, 0.0001f)
        assertEquals(1f, cutout.srcWidth, 0.0001f)
        assertEquals(1f, cutout.srcHeight, 0.0001f)
        assertEquals(0f, cutout.destX, 0.0001f)
        assertEquals(1f, cutout.destWidth, 0.0001f)
        // 1080 / 1920 = 0.5625
        assertEquals(0.5625f, cutout.destHeight, 0.0001f)
        assertEquals((1f - 0.5625f) / 2f, cutout.destY, 0.0001f)
        assertFalse(cutout.renderAboveMask)
        assertEquals(0, cutout.rotation)
        assertFalse(cutout.flipHorizontal)
        assertFalse(cutout.flipVertical)
        assertEquals(CutoutFlipMode.NONE, cutout.flipMode)
    }

    @Test
    fun `verify CutoutFlipMode fromBooleans mapping`() {
        assertEquals(CutoutFlipMode.NONE, CutoutFlipMode.fromBooleans(horizontal = false, vertical = false))
        assertEquals(CutoutFlipMode.HORIZONTAL, CutoutFlipMode.fromBooleans(horizontal = true, vertical = false))
        assertEquals(CutoutFlipMode.VERTICAL, CutoutFlipMode.fromBooleans(horizontal = false, vertical = true))
        assertEquals(CutoutFlipMode.BOTH, CutoutFlipMode.fromBooleans(horizontal = true, vertical = true))
    }

    @Test
    fun `verify json serialization round trip and backward compatibility`() {
        val json = Json { ignoreUnknownKeys = true }
        val custom =
            ScreenCutout.FULLSCREEN.copy(
                id = "rotated_cutout",
                rotation = 90,
                flipHorizontal = true,
                flipVertical = false,
            )
        val serialized = json.encodeToString(ScreenCutout.serializer(), custom)
        val deserialized = json.decodeFromString(ScreenCutout.serializer(), serialized)
        assertEquals(90, deserialized.rotation)
        assertTrue(deserialized.flipHorizontal)
        assertFalse(deserialized.flipVertical)
        assertEquals(CutoutFlipMode.HORIZONTAL, deserialized.flipMode)

        // Legacy JSON without rotation or flip fields defaults to 0 and false
        val legacyJson =
            """
            {
                "id": "legacy_cutout",
                "srcX": 0.0,
                "srcY": 0.0,
                "srcWidth": 1.0,
                "srcHeight": 1.0,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0
            }
            """.trimIndent()
        val legacyParsed = json.decodeFromString(ScreenCutout.serializer(), legacyJson)
        assertEquals(0, legacyParsed.rotation)
        assertFalse(legacyParsed.flipHorizontal)
        assertFalse(legacyParsed.flipVertical)
        assertEquals(CutoutFlipMode.NONE, legacyParsed.flipMode)
    }
}
