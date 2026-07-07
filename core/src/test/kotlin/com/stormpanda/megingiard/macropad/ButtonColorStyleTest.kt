package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ButtonColorStyle] serialization and the [PadLayout] fields
 * [PadLayout.buttonColorNoMirror] and [PadLayout.buttonColorMirror] introduced
 * in the per-layout button color style feature.
 *
 * Key invariants:
 * - Both enum values survive a JSON round-trip unchanged.
 * - [PadLayout] fields survive a full round-trip.
 * - Legacy JSON without those fields deserializes to the documented defaults
 *   ([ButtonColorStyle.ACCENTED] for no-mirror, [ButtonColorStyle.NEUTRAL] for mirror).
 */
class ButtonColorStyleTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── ButtonColorStyle enum ─────────────────────────────────────────────

    @Test
    fun `ACCENTED survives JSON round-trip`() {
        val encoded = json.encodeToString(ButtonColorStyle.ACCENTED)
        val decoded = json.decodeFromString<ButtonColorStyle>(encoded)
        assertEquals(ButtonColorStyle.ACCENTED, decoded)
    }

    @Test
    fun `NEUTRAL survives JSON round-trip`() {
        val encoded = json.encodeToString(ButtonColorStyle.NEUTRAL)
        val decoded = json.decodeFromString<ButtonColorStyle>(encoded)
        assertEquals(ButtonColorStyle.NEUTRAL, decoded)
    }

    @Test
    fun `enum serialized name matches Kotlin name`() {
        // The JSON representation must be the stable Kotlin name so that existing
        // exports written by future app versions remain readable by older ones.
        assertTrue(json.encodeToString(ButtonColorStyle.ACCENTED).contains("ACCENTED"))
        assertTrue(json.encodeToString(ButtonColorStyle.NEUTRAL).contains("NEUTRAL"))
    }

    // ── PadLayout round-trip ──────────────────────────────────────────────

    @Test
    fun `PadLayout with explicit ACCENTED no-mirror and NEUTRAL mirror survives round-trip`() {
        val layout = PadLayout(
            id = "layout-1",
            name = "Test Layout",
            buttonColorNoMirror = ButtonColorStyle.ACCENTED,
            buttonColorMirror = ButtonColorStyle.NEUTRAL,
        )
        val decoded = json.decodeFromString<PadLayout>(json.encodeToString(layout))
        assertEquals(layout, decoded)
    }

    @Test
    fun `PadLayout with swapped styles survives round-trip`() {
        val layout = PadLayout(
            id = "layout-2",
            name = "Swapped",
            buttonColorNoMirror = ButtonColorStyle.NEUTRAL,
            buttonColorMirror = ButtonColorStyle.ACCENTED,
        )
        val decoded = json.decodeFromString<PadLayout>(json.encodeToString(layout))
        assertEquals(layout, decoded)
        assertEquals(ButtonColorStyle.NEUTRAL, decoded.buttonColorNoMirror)
        assertEquals(ButtonColorStyle.ACCENTED, decoded.buttonColorMirror)
    }

    // ── Backward-compatibility: legacy JSON without new fields ────────────

    @Test
    fun `legacy PadLayout JSON without buttonColor fields deserializes with null deprecated colors`() {
        // A JSON object that predates the buttonColorNoMirror / buttonColorMirror fields.
        val legacyJson = """{"id":"old-layout","name":"Legacy","enabled":true,"buttons":[]}"""
        val layout = json.decodeFromString<PadLayout>(legacyJson)
        @Suppress("DEPRECATION")
        assertEquals(null, layout.buttonColorNoMirror)
        @Suppress("DEPRECATION")
        assertEquals(null, layout.buttonColorMirror)
        assertEquals(false, layout.invisibleButtons)
    }

    @Test
    fun `legacy PadLayout JSON without color options deserializes with default NEUTRAL options`() {
        val legacyJson = """{"id":"old-layout","name":"Legacy","enabled":true,"buttons":[]}"""
        val layout = json.decodeFromString<PadLayout>(legacyJson)
        assertEquals(ColorOption.Neutral, layout.buttonTextColor)
        assertEquals(ColorOption.Neutral, layout.buttonBorderColor)
        assertEquals(ColorOption.Neutral, layout.buttonBgColor)
    }

    // ── ColorOption serialization ─────────────────────────────────────────

    @Test
    fun `ColorOption Neutral round-trip`() {
        val encoded = json.encodeToString<ColorOption>(ColorOption.Neutral)
        val decoded = json.decodeFromString<ColorOption>(encoded)
        assertEquals(ColorOption.Neutral, decoded)
    }

    @Test
    fun `ColorOption Accent round-trip`() {
        val encoded = json.encodeToString<ColorOption>(ColorOption.Accent)
        val decoded = json.decodeFromString<ColorOption>(encoded)
        assertEquals(ColorOption.Accent, decoded)
    }

    @Test
    fun `ColorOption Custom round-trip`() {
        val custom = ColorOption.Custom(0xFFFF0000.toInt())
        val encoded = json.encodeToString<ColorOption>(custom)
        val decoded = json.decodeFromString<ColorOption>(encoded)
        assertEquals(custom, decoded)
    }

    @Test
    fun `PadButton with custom color options survives JSON round-trip`() {
        val button = PadButton(
            id = "btn-1",
            label = "Color Btn",
            posX = 0.2f,
            posY = 0.3f,
            action = PadAction.GamepadButton(1, "A"),
            buttonTextColor = ColorOption.Custom(0xFF112233.toInt()),
            buttonBorderColor = ColorOption.Neutral,
            buttonBgColor = null, // Default layout fallback
        )
        val decoded = json.decodeFromString<PadButton>(json.encodeToString(button))
        assertEquals(button, decoded)
        assertEquals(ColorOption.Custom(0xFF112233.toInt()), decoded.buttonTextColor)
        assertEquals(ColorOption.Neutral, decoded.buttonBorderColor)
        assertEquals(null, decoded.buttonBgColor)
    }

    @Test
    fun `legacy PadButton without color options deserializes with null defaults`() {
        val buttonWithoutColors = PadButton(
            id = "btn-old",
            label = "Old",
            posX = 0.1f,
            posY = 0.1f,
            action = PadAction.GamepadButton(1, "A"),
        )
        val jsonStr = json.encodeToString(buttonWithoutColors)
        val legacyJson = jsonStr
            .replace("\"buttonTextColor\":null,", "")
            .replace("\"buttonBorderColor\":null,", "")
            .replace("\"buttonBgColor\":null,", "")
            .replace(",\"buttonTextColor\":null", "")
            .replace(",\"buttonBorderColor\":null", "")
            .replace(",\"invisible\":false", "")
            .replace("\"invisible\":false,", "")

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(null, decoded.buttonTextColor)
        assertEquals(null, decoded.buttonBorderColor)
        assertEquals(null, decoded.buttonBgColor)
        assertEquals(false, decoded.invisible)
    }
}

