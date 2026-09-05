package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ButtonColorStyle] serialization and [PadLayout] color fields.
 */
class ButtonColorStyleTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private inline fun <reified T> assertRoundTrip(value: T) {
        val encoded = json.encodeToString(value)
        val decoded = json.decodeFromString<T>(encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `enum round trip and serialized names`() {
        assertRoundTrip(ButtonColorStyle.ACCENTED)
        assertRoundTrip(ButtonColorStyle.NEUTRAL)
        assertTrue(json.encodeToString(ButtonColorStyle.ACCENTED).contains("ACCENTED"))
        assertTrue(json.encodeToString(ButtonColorStyle.NEUTRAL).contains("NEUTRAL"))
    }

    @Test
    fun `PadLayout with explicit button color styles survives round-trip`() {
        val layout1 =
            PadLayout(
                id = "layout-1",
                name = "Test Layout",
                buttonColorNoMirror = ButtonColorStyle.ACCENTED,
                buttonColorMirror = ButtonColorStyle.NEUTRAL,
            )
        assertRoundTrip(layout1)

        val layout2 =
            PadLayout(
                id = "layout-2",
                name = "Swapped",
                buttonColorNoMirror = ButtonColorStyle.NEUTRAL,
                buttonColorMirror = ButtonColorStyle.ACCENTED,
            )
        assertRoundTrip(layout2)
    }

    @Test
    fun `legacy PadLayout JSON without buttonColor fields deserializes with expected defaults`() {
        val legacyJson = """{"id":"old-layout","name":"Legacy","enabled":true,"buttons":[]}"""
        val layout = json.decodeFromString<PadLayout>(legacyJson)
        @Suppress("DEPRECATION")
        assertNull(layout.buttonColorNoMirror)
        @Suppress("DEPRECATION")
        assertNull(layout.buttonColorMirror)
        assertFalse(layout.invisibleButtons)
        assertEquals(ColorOption.Neutral, layout.buttonTextColor)
        assertEquals(ColorOption.Neutral, layout.buttonBorderColor)
        assertEquals(ColorOption.Neutral, layout.buttonBgColor)
    }

    @Test
    fun `ColorOption round-trips`() {
        assertRoundTrip<ColorOption>(ColorOption.Neutral)
        assertRoundTrip<ColorOption>(ColorOption.Accent)
        assertRoundTrip<ColorOption>(ColorOption.Custom(0x80FF0000.toInt()))
    }

    @Test
    fun `PadButton with custom color options survives JSON round-trip`() {
        val button =
            PadButton(
                id = "btn-1",
                label = "Color Btn",
                posX = 0.2f,
                posY = 0.3f,
                action = PadAction.GamepadButton(1, "A"),
                buttonTextColor = ColorOption.Custom(0xFF112233.toInt()),
                buttonBorderColor = ColorOption.Neutral,
                buttonBgColor = null,
            )
        assertRoundTrip(button)
    }

    @Test
    fun `legacy PadButton without color options deserializes with null defaults`() {
        val legacyJson = """{"id":"btn-old","label":"Old","posX":0.1,"posY":0.1,"action":{"type":"gamepad_button","btnCode":1,"label":"A"}}"""
        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertNull(decoded.buttonTextColor)
        assertNull(decoded.buttonBorderColor)
        assertNull(decoded.buttonBgColor)
        assertFalse(decoded.invisible)
    }
}
