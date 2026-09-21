package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutGestureMathTest {
    @Test
    fun `applyPan shifts source crop proportional to normalized delta`() {
        val initial = NormalizedCrop(srcX = 0.3f, srcY = 0.3f, srcWidth = 0.4f, srcHeight = 0.4f)
        // Dragging finger right by 0.1 normalized should move crop left (reveal left content)
        val panned = CutoutGestureMath.applyPan(initial, deltaNormX = 0.1f, deltaNormY = 0.05f)

        // rawDx = -0.1 * 0.4 = -0.04 -> srcX = 0.26
        // rawDy = -0.05 * 0.4 = -0.02 -> srcY = 0.28
        assertEquals(0.26f, panned.srcX, 0.0001f)
        assertEquals(0.28f, panned.srcY, 0.0001f)
        assertEquals(0.4f, panned.srcWidth, 0.0001f)
        assertEquals(0.4f, panned.srcHeight, 0.0001f)
    }

    @Test
    fun `applyPan applies elastic dampening when dragged past boundary`() {
        val initial = NormalizedCrop(srcX = 0.01f, srcY = 0.0f, srcWidth = 0.5f, srcHeight = 0.5f)
        // Large drag right pulls srcX into negative
        val panned = CutoutGestureMath.applyPan(initial, deltaNormX = 0.2f, deltaNormY = 0.0f)

        // rawDx = -0.2 * 0.5 = -0.1 -> rawX = -0.09
        // Dampened: -0.09 * 0.35 = -0.0315
        assertTrue(panned.srcX < 0f)
        assertEquals(-0.09f * ELASTIC_DAMPENING_FACTOR, panned.srcX, 0.0001f)
    }

    @Test
    fun `applyPinchZoom magnifies around focal point while preserving aspect ratio`() {
        val defaultCrop = NormalizedCrop(srcX = 0.2f, srcY = 0.2f, srcWidth = 0.4f, srcHeight = 0.2f)
        val current = defaultCrop
        val zoomed =
            CutoutGestureMath.applyPinchZoom(
                current = current,
                defaultCrop = defaultCrop,
                scaleFactor = 2.0f,
                focalNormX = 0.5f,
                focalNormY = 0.5f,
            )

        // 2x magnification -> width halved (0.4 / 2 = 0.2), height halved (0.2 / 2 = 0.1)
        assertEquals(0.2f, zoomed.srcWidth, 0.0001f)
        assertEquals(0.1f, zoomed.srcHeight, 0.0001f)

        // Focal point (0.5, 0.5) in source was at (0.2 + 0.5 * 0.4 = 0.4, 0.2 + 0.5 * 0.2 = 0.3)
        // New srcX = 0.4 - 0.5 * 0.2 = 0.3
        // New srcY = 0.3 - 0.5 * 0.1 = 0.25
        assertEquals(0.3f, zoomed.srcX, 0.0001f)
        assertEquals(0.25f, zoomed.srcY, 0.0001f)
    }

    @Test
    fun `clampToScreenBounds clamps invalid negative and overflow coordinates`() {
        val outOfBounds = NormalizedCrop(srcX = -0.1f, srcY = 0.8f, srcWidth = 0.5f, srcHeight = 0.5f)
        val clamped = CutoutGestureMath.clampToScreenBounds(outOfBounds)

        assertEquals(0f, clamped.srcX, 0.0001f)
        // maxY = 1.0 - 0.5 = 0.5
        assertEquals(0.5f, clamped.srcY, 0.0001f)
        assertEquals(0.5f, clamped.srcWidth, 0.0001f)
        assertEquals(0.5f, clamped.srcHeight, 0.0001f)
    }

    @Test
    fun `lerp smoothly interpolates between two crops`() {
        val start = NormalizedCrop(srcX = 0.1f, srcY = 0.1f, srcWidth = 0.4f, srcHeight = 0.4f)
        val target = NormalizedCrop(srcX = 0.3f, srcY = 0.5f, srcWidth = 0.2f, srcHeight = 0.2f)

        val midpoint = CutoutGestureMath.lerp(start, target, 0.5f)
        assertEquals(0.2f, midpoint.srcX, 0.0001f)
        assertEquals(0.3f, midpoint.srcY, 0.0001f)
        assertEquals(0.3f, midpoint.srcWidth, 0.0001f)
        assertEquals(0.3f, midpoint.srcHeight, 0.0001f)

        assertTrue(CutoutGestureMath.isCloseTo(midpoint, NormalizedCrop(0.2f, 0.3f, 0.3f, 0.3f)))
        assertFalse(CutoutGestureMath.isCloseTo(midpoint, start))
    }
}
