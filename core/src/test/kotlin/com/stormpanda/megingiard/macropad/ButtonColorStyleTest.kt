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
    fun `legacy PadLayout JSON without buttonColor fields deserializes with default ACCENTED no-mirror`() {
        // A JSON object that predates the buttonColorNoMirror / buttonColorMirror fields.
        val legacyJson = """{"id":"old-layout","name":"Legacy","enabled":true,"buttons":[]}"""
        val layout = json.decodeFromString<PadLayout>(legacyJson)
        assertEquals(ButtonColorStyle.ACCENTED, layout.buttonColorNoMirror)
    }

    @Test
    fun `legacy PadLayout JSON without buttonColor fields deserializes with default NEUTRAL mirror`() {
        val legacyJson = """{"id":"old-layout","name":"Legacy","enabled":true,"buttons":[]}"""
        val layout = json.decodeFromString<PadLayout>(legacyJson)
        assertEquals(ButtonColorStyle.NEUTRAL, layout.buttonColorMirror)
    }
}
