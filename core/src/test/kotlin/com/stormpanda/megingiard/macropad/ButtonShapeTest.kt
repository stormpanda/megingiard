package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ButtonShape] serialization and backward compatibility.
 */
class ButtonShapeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `ICON_ONLY survives JSON round-trip`() {
        val encoded = json.encodeToString(ButtonShape.ICON_ONLY)
        val decoded = json.decodeFromString<ButtonShape>(encoded)
        assertEquals(ButtonShape.ICON_ONLY, decoded)
    }

    @Test
    fun `enum serialized name matches Kotlin name`() {
        assertTrue(json.encodeToString(ButtonShape.ICON_ONLY).contains("ICON_ONLY"))
    }

    @Test
    fun `PadButton with ICON_ONLY shape survives JSON round-trip`() {
        val button = PadButton(
            id = "btn-icon-only-1",
            label = "Icon Button",
            iconName = "home",
            posX = 0.5f,
            posY = 0.5f,
            buttonShape = ButtonShape.ICON_ONLY,
            action = PadAction.BackgroundPeek
        )
        val encoded = json.encodeToString(button)
        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(button, decoded)
        assertEquals(ButtonShape.ICON_ONLY, decoded.buttonShape)
    }
}
