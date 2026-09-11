package com.stormpanda.megingiard.macropad

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_SRC_X = 0.12f
private const val TEST_SRC_Y = 0.34f
private const val TEST_SRC_WIDTH = 0.25f
private const val TEST_SRC_HEIGHT = 0.20f
private const val TEST_STREAM_DELAY = 4
private const val EPSILON = 0.0001f

class LayoutVisualAnchorTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `verify default values of LayoutVisualAnchor`() {
        val anchor = LayoutVisualAnchor()
        assertFalse(anchor.enabled)
        assertEquals(0f, anchor.srcX, EPSILON)
        assertEquals(0f, anchor.srcY, EPSILON)
        assertEquals(DEFAULT_LAYOUT_ANCHOR_SIZE, anchor.srcWidth, EPSILON)
        assertEquals(DEFAULT_LAYOUT_ANCHOR_SIZE, anchor.srcHeight, EPSILON)
        assertEquals(DEFAULT_LAYOUT_STREAM_DELAY_FRAMES, anchor.streamDelayFrames)
        assertTrue(anchor.freezeCutoutsOnLoss)
    }

    @Test
    fun `verify serialization round-trip of LayoutVisualAnchor`() {
        val original =
            LayoutVisualAnchor(
                enabled = true,
                srcX = TEST_SRC_X,
                srcY = TEST_SRC_Y,
                srcWidth = TEST_SRC_WIDTH,
                srcHeight = TEST_SRC_HEIGHT,
                streamDelayFrames = TEST_STREAM_DELAY,
                freezeCutoutsOnLoss = false,
            )

        val serialized = json.encodeToString(original)
        assertTrue(serialized.contains("\"enabled\":true"))
        assertTrue(serialized.contains("\"streamDelayFrames\":$TEST_STREAM_DELAY"))
        assertTrue(serialized.contains("\"freezeCutoutsOnLoss\":false"))

        val deserialized = json.decodeFromString<LayoutVisualAnchor>(serialized)
        assertEquals(original, deserialized)
        assertEquals(TEST_SRC_X, deserialized.srcX, EPSILON)
        assertEquals(TEST_SRC_Y, deserialized.srcY, EPSILON)
        assertEquals(TEST_SRC_WIDTH, deserialized.srcWidth, EPSILON)
        assertEquals(TEST_SRC_HEIGHT, deserialized.srcHeight, EPSILON)
        assertEquals(TEST_STREAM_DELAY, deserialized.streamDelayFrames)
        assertFalse(deserialized.freezeCutoutsOnLoss)
    }

    @Test
    fun `verify PadLayout serialization round-trip with custom visual anchor`() {
        val layout =
            PadLayout(
                id = "layout-anchor-test",
                name = "Anchor Test Layout",
                visualAnchor =
                    LayoutVisualAnchor(
                        enabled = true,
                        srcX = TEST_SRC_X,
                        srcY = TEST_SRC_Y,
                        srcWidth = TEST_SRC_WIDTH,
                        srcHeight = TEST_SRC_HEIGHT,
                        streamDelayFrames = TEST_STREAM_DELAY,
                        freezeCutoutsOnLoss = true,
                    ),
            )

        val serialized = json.encodeToString(layout)
        assertTrue(serialized.contains("\"visualAnchor\""))

        val deserialized = json.decodeFromString<PadLayout>(serialized)
        assertEquals(layout, deserialized)
        assertTrue(deserialized.visualAnchor.enabled)
        assertEquals(TEST_SRC_X, deserialized.visualAnchor.srcX, EPSILON)
        assertEquals(TEST_STREAM_DELAY, deserialized.visualAnchor.streamDelayFrames)
        assertTrue(deserialized.visualAnchor.freezeCutoutsOnLoss)
    }

    @Test
    fun `verify backward compatibility when deserializing legacy PadLayout without visualAnchor`() {
        val legacyJson =
            """
            {
                "id": "legacy_layout_1",
                "name": "Legacy Layout",
                "enabled": true
            }
            """.trimIndent()

        val parsed = json.decodeFromString<PadLayout>(legacyJson)
        assertEquals("legacy_layout_1", parsed.id)
        assertEquals("Legacy Layout", parsed.name)
        assertFalse(parsed.visualAnchor.enabled)
        assertEquals(0f, parsed.visualAnchor.srcX, EPSILON)
        assertEquals(0f, parsed.visualAnchor.srcY, EPSILON)
        assertEquals(DEFAULT_LAYOUT_ANCHOR_SIZE, parsed.visualAnchor.srcWidth, EPSILON)
        assertEquals(DEFAULT_LAYOUT_ANCHOR_SIZE, parsed.visualAnchor.srcHeight, EPSILON)
        assertEquals(DEFAULT_LAYOUT_STREAM_DELAY_FRAMES, parsed.visualAnchor.streamDelayFrames)
        assertTrue(parsed.visualAnchor.freezeCutoutsOnLoss)
    }
}
