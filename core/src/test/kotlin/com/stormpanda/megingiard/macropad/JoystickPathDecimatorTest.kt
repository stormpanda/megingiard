package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Ramer–Douglas–Peucker [rdpDecimate] implementation.
 */
class JoystickPathDecimatorTest {

    @Test
    fun `empty list returns empty`() {
        assertEquals(emptyList<PathSample>(), rdpDecimate(emptyList(), epsilon = 0.04f))
    }

    @Test
    fun `single sample returns single sample`() {
        val samples = listOf(PathSample(0L, 0.5f, 0.5f))
        assertEquals(samples, rdpDecimate(samples, epsilon = 0.04f))
    }

    @Test
    fun `two samples are always preserved`() {
        val samples = listOf(
            PathSample(0L, 0f, 0f),
            PathSample(100L, 1f, 1f),
        )
        assertEquals(samples, rdpDecimate(samples, epsilon = 0.04f))
    }

    @Test
    fun `collinear points are simplified to endpoints`() {
        /* Straight line from (0,0) to (1,0) with a midpoint at (0.5,0). */
        val samples = listOf(
            PathSample(0L, 0f, 0f),
            PathSample(50L, 0.5f, 0f),
            PathSample(100L, 1f, 0f),
        )
        val result = rdpDecimate(samples, epsilon = 0.04f)
        assertEquals(2, result.size)
        assertEquals(PathSample(0L, 0f, 0f), result.first())
        assertEquals(PathSample(100L, 1f, 0f), result.last())
    }

    @Test
    fun `sharp bend is preserved`() {
        /* L-shaped path: right then down — corner point must be kept. */
        val samples = listOf(
            PathSample(0L, 0f, 0f),
            PathSample(50L, 1f, 0f),  // corner
            PathSample(100L, 1f, 1f),
        )
        val result = rdpDecimate(samples, epsilon = 0.04f)
        /* All 3 points must be preserved — the corner deviates significantly from the line. */
        assertEquals(3, result.size)
        assertTrue(result.contains(PathSample(50L, 1f, 0f)))
    }

    @Test
    fun `linear segment in longer path is collapsed`() {
        /* Five collinear points along y=0. */
        val samples = (0..4).map { i ->
            PathSample(offsetMs = i * 25L, x = i * 0.25f, y = 0f)
        }
        val result = rdpDecimate(samples, epsilon = 0.04f)
        assertEquals(2, result.size)
        assertEquals(samples.first(), result.first())
        assertEquals(samples.last(), result.last())
    }

    @Test
    fun `epsilon zero preserves all points`() {
        val samples = listOf(
            PathSample(0L, 0f, 0f),
            PathSample(50L, 0.5f, 0.01f),  // tiny deviation
            PathSample(100L, 1f, 0f),
        )
        val result = rdpDecimate(samples, epsilon = 0f)
        assertEquals(3, result.size)
    }

    @Test
    fun `large epsilon collapses near-line paths to endpoints`() {
        /* Points with a small bump — should be collapsed with large epsilon. */
        val samples = listOf(
            PathSample(0L, 0f, 0f),
            PathSample(50L, 0.5f, 0.03f),
            PathSample(100L, 1f, 0f),
        )
        val result = rdpDecimate(samples, epsilon = 0.1f)
        assertEquals(2, result.size)
    }
}
