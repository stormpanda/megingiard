package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.mirror.AnchorPoint
import com.stormpanda.megingiard.mirror.VisualAnchorSignature
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals(DEFAULT_LOST_ANCHOR_EFFECTS, anchor.lostAnchorEffects)
        assertNull(anchor.signature)
        assertFalse(anchor.isCalibrated)
        assertTrue(anchor.hasEffect(CutoutLostAnchorEffect.FREEZE))
        assertTrue(anchor.hasEffect(CutoutLostAnchorEffect.BLUR))
        assertTrue(anchor.freezeCutoutsOnLoss)
        assertTrue(anchor.blurCutoutsOnLoss)
    }

    @Test
    fun `verify withEffect and hasEffect helpers`() {
        val anchor = LayoutVisualAnchor()
        assertTrue(anchor.hasEffect(CutoutLostAnchorEffect.FREEZE))
        assertTrue(anchor.hasEffect(CutoutLostAnchorEffect.BLUR))

        val noFreeze = anchor.withEffect(CutoutLostAnchorEffect.FREEZE, false)
        assertFalse(noFreeze.hasEffect(CutoutLostAnchorEffect.FREEZE))
        assertTrue(noFreeze.hasEffect(CutoutLostAnchorEffect.BLUR))
        assertFalse(noFreeze.freezeCutoutsOnLoss)
        assertTrue(noFreeze.blurCutoutsOnLoss)

        val noEffects = noFreeze.withEffect(CutoutLostAnchorEffect.BLUR, false)
        assertFalse(noEffects.hasEffect(CutoutLostAnchorEffect.FREEZE))
        assertFalse(noEffects.hasEffect(CutoutLostAnchorEffect.BLUR))
        assertEquals(emptySet<CutoutLostAnchorEffect>(), noEffects.lostAnchorEffects)
        assertFalse(noEffects.freezeCutoutsOnLoss)
        assertFalse(noEffects.blurCutoutsOnLoss)

        val reAdded = noEffects.withEffect(CutoutLostAnchorEffect.FREEZE, true)
        assertTrue(reAdded.hasEffect(CutoutLostAnchorEffect.FREEZE))
        assertFalse(reAdded.hasEffect(CutoutLostAnchorEffect.BLUR))
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
                lostAnchorEffects = setOf(CutoutLostAnchorEffect.FREEZE),
                blurCutoutsOnLoss = false,
            )

        val serialized = json.encodeToString(original)
        assertTrue(serialized.contains("\"enabled\":true"))
        assertTrue(serialized.contains("\"streamDelayFrames\":$TEST_STREAM_DELAY"))
        assertTrue(serialized.contains("\"lostAnchorEffects\":[\"freeze\"]"))

        val deserialized = json.decodeFromString<LayoutVisualAnchor>(serialized)
        assertEquals(original, deserialized)
        assertEquals(TEST_SRC_X, deserialized.srcX, EPSILON)
        assertEquals(TEST_SRC_Y, deserialized.srcY, EPSILON)
        assertEquals(TEST_SRC_WIDTH, deserialized.srcWidth, EPSILON)
        assertEquals(TEST_SRC_HEIGHT, deserialized.srcHeight, EPSILON)
        assertEquals(TEST_STREAM_DELAY, deserialized.streamDelayFrames)
        assertEquals(setOf(CutoutLostAnchorEffect.FREEZE), deserialized.lostAnchorEffects)
        assertTrue(deserialized.hasEffect(CutoutLostAnchorEffect.FREEZE))
        assertFalse(deserialized.hasEffect(CutoutLostAnchorEffect.BLUR))
    }

    @Test
    fun `verify serialization round-trip of LayoutVisualAnchor with embedded signature`() {
        val points =
            listOf(
                AnchorPoint(0.2f, 0.3f, 10, 20, 30),
                AnchorPoint(0.4f, 0.5f, 40, 50, 60),
            )
        val original =
            LayoutVisualAnchor(
                enabled = true,
                srcX = TEST_SRC_X,
                srcY = TEST_SRC_Y,
                srcWidth = TEST_SRC_WIDTH,
                srcHeight = TEST_SRC_HEIGHT,
                signature = VisualAnchorSignature(cutoutId = "test_layout", points = points),
            )

        assertTrue(original.isCalibrated)
        val serialized = json.encodeToString(original)
        assertTrue(serialized.contains("\"signature\""))
        assertTrue(serialized.contains("\"points\""))

        val deserialized = json.decodeFromString<LayoutVisualAnchor>(serialized)
        assertEquals(original, deserialized)
        assertTrue(deserialized.isCalibrated)
        assertNotNull(deserialized.signature)
        assertEquals("test_layout", deserialized.signature!!.cutoutId)
        assertEquals(2, deserialized.signature!!.points.size)
        assertEquals(10, deserialized.signature!!.points[0].r)
        assertEquals(60, deserialized.signature!!.points[1].b)
    }

    @Test
    fun `verify isCalibrated returns false when signature has empty points`() {
        val emptySignatureAnchor =
            LayoutVisualAnchor(
                enabled = true,
                signature = VisualAnchorSignature(cutoutId = "empty", points = emptyList()),
            )
        assertFalse(emptySignatureAnchor.isCalibrated)
    }

    @Test
    fun `verify backward compatibility when deserializing legacy LayoutVisualAnchor with only blurCutoutsOnLoss`() {
        val legacyJson =
            """
            {
                "enabled": true,
                "srcX": 0.1,
                "srcY": 0.2,
                "blurCutoutsOnLoss": false
            }
            """.trimIndent()

        val parsed = json.decodeFromString<LayoutVisualAnchor>(legacyJson)
        assertTrue(parsed.enabled)
        assertFalse(parsed.hasEffect(CutoutLostAnchorEffect.BLUR))
        assertTrue(parsed.hasEffect(CutoutLostAnchorEffect.FREEZE))
        assertTrue(parsed.freezeCutoutsOnLoss)
        assertFalse(parsed.blurCutoutsOnLoss)
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
                        blurCutoutsOnLoss = true,
                    ),
            )

        val serialized = json.encodeToString(layout)
        assertTrue(serialized.contains("\"visualAnchor\""))

        val deserialized = json.decodeFromString<PadLayout>(serialized)
        assertEquals(layout, deserialized)
        assertTrue(deserialized.visualAnchor.enabled)
        assertEquals(TEST_SRC_X, deserialized.visualAnchor.srcX, EPSILON)
        assertEquals(TEST_STREAM_DELAY, deserialized.visualAnchor.streamDelayFrames)
        assertTrue(deserialized.visualAnchor.blurCutoutsOnLoss)
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
        assertTrue(parsed.visualAnchor.blurCutoutsOnLoss)
    }

    @Test
    fun `verify PadProfile serialization round-trip with autoLayoutSwitching`() {
        val profile =
            PadProfile(
                id = "profile_auto_switch",
                name = "Auto Switch Profile",
                autoLayoutSwitching = true,
            )

        val serialized = json.encodeToString(profile)
        assertTrue(serialized.contains("\"autoLayoutSwitching\":true"))

        val deserialized = json.decodeFromString<PadProfile>(serialized)
        assertEquals(profile, deserialized)
        assertTrue(deserialized.autoLayoutSwitching)
    }

    @Test
    fun `verify backward compatibility when deserializing legacy PadProfile without autoLayoutSwitching`() {
        val legacyJson =
            """
            {
                "id": "legacy_prof_1",
                "name": "Legacy Profile"
            }
            """.trimIndent()

        val parsed = json.decodeFromString<PadProfile>(legacyJson)
        assertEquals("legacy_prof_1", parsed.id)
        assertEquals("Legacy Profile", parsed.name)
        assertFalse(parsed.autoLayoutSwitching)
    }
}
