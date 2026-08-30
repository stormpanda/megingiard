package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundScaleModeTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `FILL survives JSON round-trip`() {
        val encoded = json.encodeToString(BackgroundScaleMode.FILL)
        val decoded = json.decodeFromString<BackgroundScaleMode>(encoded)
        assertEquals(BackgroundScaleMode.FILL, decoded)
    }

    @Test
    fun `FIT survives JSON round-trip`() {
        val encoded = json.encodeToString(BackgroundScaleMode.FIT)
        val decoded = json.decodeFromString<BackgroundScaleMode>(encoded)
        assertEquals(BackgroundScaleMode.FIT, decoded)
    }

    @Test
    fun `STRETCH survives JSON round-trip`() {
        val encoded = json.encodeToString(BackgroundScaleMode.STRETCH)
        val decoded = json.decodeFromString<BackgroundScaleMode>(encoded)
        assertEquals(BackgroundScaleMode.STRETCH, decoded)
    }

    @Test
    fun `enum serialized names match stable names`() {
        assertTrue(json.encodeToString(BackgroundScaleMode.FILL).contains("FILL"))
        assertTrue(json.encodeToString(BackgroundScaleMode.FIT).contains("FIT"))
        assertTrue(json.encodeToString(BackgroundScaleMode.STRETCH).contains("STRETCH"))
    }

    @Test
    fun `PadLayout with explicit BackgroundScaleMode survives round-trip`() {
        for (mode in BackgroundScaleMode.entries) {
            val layout =
                PadLayout(
                    id = "layout-test",
                    name = "Scale Test Layout",
                    bgScaleMode = mode,
                )
            val encoded = json.encodeToString(layout)
            val decoded = json.decodeFromString<PadLayout>(encoded)
            assertEquals(mode, decoded.bgScaleMode)
        }
    }

    @Test
    fun `legacy PadLayout JSON without bgScaleMode field deserializes to FILL`() {
        val legacyJson =
            """
            {
                "id": "legacy-layout",
                "name": "Legacy Layout",
                "enabled": true,
                "buttons": [],
                "ambientDim": 0.0,
                "mirrorSavedScale": 1.0,
                "mirrorSavedOffsetX": 0.0,
                "mirrorSavedOffsetY": 0.0,
                "mirrorAutoStart": false,
                "mirrorFollowActive": false,
                "mirrorSmoothing": true,
                "mirrorCutouts": [],
                "mirrorConfigured": false,
                "mirrorMultiMode": false,
                "mirrorEdgeBlendWidth": 0.0,
                "mirrorMaxFps": 60,
                "mirrorSmoothingStrength": 85,
                "backgroundImagePath": "backgrounds/bg_legacy.png",
                "useBackgroundImageAsMask": false,
                "invisibleButtons": false,
                "bgImageScale": 1.0,
                "bgImageOffsetX": 0.0,
                "bgImageOffsetY": 0.0,
                "backgroundImageDim": 0.0
            }
            """.trimIndent()

        val layout = json.decodeFromString<PadLayout>(legacyJson)
        assertEquals(BackgroundScaleMode.FILL, layout.bgScaleMode)
    }
}
