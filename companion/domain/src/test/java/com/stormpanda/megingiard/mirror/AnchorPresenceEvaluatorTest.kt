package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorPresenceEvaluatorTest {
    private fun colorArgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @Test
    fun `evaluateMatchRatio with empty signature returns 0`() {
        val signature = VisualAnchorSignature("test", emptyList())
        val ratio = AnchorPresenceEvaluator.evaluateMatchRatio(signature) { _, _ -> colorArgb(255, 255, 255) }
        assertEquals(0f, ratio, 0.001f)
    }

    @Test
    fun `evaluateMatchRatio with matching pixels returns 1`() {
        val points =
            listOf(
                AnchorPoint(0.1f, 0.1f, 255, 255, 255),
                AnchorPoint(0.5f, 0.5f, 100, 150, 200),
            )
        val signature = VisualAnchorSignature("test", points)

        val ratio =
            AnchorPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                if (u < 0.3f) colorArgb(250, 250, 250) else colorArgb(105, 145, 195)
            }
        assertEquals(1.0f, ratio, 0.001f)
    }

    @Test
    fun `evaluateMatchRatio with completely divergent pixels returns 0`() {
        val points =
            listOf(
                AnchorPoint(0.1f, 0.1f, 255, 255, 255),
                AnchorPoint(0.5f, 0.5f, 100, 150, 200),
            )
        val signature = VisualAnchorSignature("test", points)

        // All sampled pixels are black (RGB 0,0,0) as in a black bar or dark scene
        val ratio = AnchorPresenceEvaluator.evaluateMatchRatio(signature) { _, _ -> colorArgb(0, 0, 0) }
        assertEquals(0.0f, ratio, 0.001f)
    }

    @Test
    fun `transitionState transitions immediately to LOST and requires 2 checks to recover`() {
        var state = AnchorPresenceState.PRESENT
        var count = 0

        // 1st check: low match ratio (e.g. 0.10) -> transitions immediately to LOST, count = 0
        val (state1, count1) = AnchorPresenceEvaluator.transitionState(state, count, 0.10f, "test")
        assertEquals(AnchorPresenceState.LOST, state1)
        assertEquals(0, count1)

        // 2nd check: high match ratio (e.g. 0.85) -> stays LOST, count becomes 1
        val (state2, count2) = AnchorPresenceEvaluator.transitionState(state1, count1, 0.85f, "test")
        assertEquals(AnchorPresenceState.LOST, state2)
        assertEquals(1, count2)

        // 3rd check: high match ratio again -> transitions back to PRESENT, count resets to 0
        val (state3, count3) = AnchorPresenceEvaluator.transitionState(state2, count2, 0.85f, "test")
        assertEquals(AnchorPresenceState.PRESENT, state3)
        assertEquals(0, count3)
    }

    @Test
    fun `transitionState resets consecutive counter if a glitch check occurs during recovery`() {
        var state = AnchorPresenceState.LOST
        var count = 0

        // 1st check: high match ratio -> count = 1
        val (state1, count1) = AnchorPresenceEvaluator.transitionState(state, count, 0.85f, "test")
        assertEquals(AnchorPresenceState.LOST, state1)
        assertEquals(1, count1)

        // 2nd check: glitch! Low match ratio -> count resets to 0, stays LOST
        val (state2, count2) = AnchorPresenceEvaluator.transitionState(state1, count1, 0.20f, "test")
        assertEquals(AnchorPresenceState.LOST, state2)
        assertEquals(0, count2)
    }

    @Test
    fun `transitionState respects default dynamic hysteresis of 0_60f for 0_80f present threshold`() {
        var state = AnchorPresenceState.PRESENT
        var count = 0

        // Ratio just above 0.60 (0.61) remains PRESENT
        val (state1, count1) = AnchorPresenceEvaluator.transitionState(state, count, 0.61f, "test")
        assertEquals(AnchorPresenceState.PRESENT, state1)
        assertEquals(0, count1)

        // Ratio below 0.60 (0.59) transitions immediately to LOST
        val (state2, count2) = AnchorPresenceEvaluator.transitionState(state1, count1, 0.59f, "test")
        assertEquals(AnchorPresenceState.LOST, state2)
        assertEquals(0, count2)
    }

    @Test
    fun `transitionState respects custom present threshold and dynamic hysteresis gap`() {
        var state = AnchorPresenceState.PRESENT
        var count = 0

        // Legacy 0.65f threshold -> lostThreshold = 0.65 - 0.20 = 0.45f
        val (state1, count1) =
            AnchorPresenceEvaluator.transitionState(state, count, 0.46f, "test", presentThreshold = 0.65f)
        assertEquals(AnchorPresenceState.PRESENT, state1)
        assertEquals(0, count1)

        val (state2, count2) =
            AnchorPresenceEvaluator.transitionState(state1, count1, 0.44f, "test", presentThreshold = 0.65f)
        assertEquals(AnchorPresenceState.LOST, state2)
        assertEquals(0, count2)

        // Clamped minimum threshold: present 0.55f -> lostThreshold = 0.35f
        val (state3, count3) =
            AnchorPresenceEvaluator.transitionState(state, count, 0.36f, "test", presentThreshold = 0.55f)
        assertEquals(AnchorPresenceState.PRESENT, state3)
        assertEquals(0, count3)

        val (state4, count4) =
            AnchorPresenceEvaluator.transitionState(state3, count3, 0.34f, "test", presentThreshold = 0.55f)
        assertEquals(AnchorPresenceState.LOST, state4)
        assertEquals(0, count4)
    }

    @Test
    fun `evaluateMatchRatio evaluates signature sampled from custom anchor crop`() {
        // Anchor point at center of anchor box (u=0.5, v=0.5)
        val signature = VisualAnchorSignature("minimap", listOf(AnchorPoint(0.5f, 0.5f, 200, 200, 200)))
        val anchorCrop = AnchorCrop(x = 0.02f, y = 0.02f, width = 0.10f, height = 0.10f)

        // Pixel provider maps anchor crop coordinates
        val sampledCoordinates = mutableListOf<Pair<Float, Float>>()
        val ratio =
            AnchorPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
                val globalU = anchorCrop.x + u * anchorCrop.width
                val globalV = anchorCrop.y + v * anchorCrop.height
                sampledCoordinates.add(globalU to globalV)
                colorArgb(200, 200, 200)
            }

        assertEquals(1.0f, ratio, 0.001f)
        assertEquals(1, sampledCoordinates.size)
        // 0.02 + 0.5 * 0.10 = 0.07
        assertEquals(0.07f, sampledCoordinates[0].first, 0.001f)
        assertEquals(0.07f, sampledCoordinates[0].second, 0.001f)
    }

    @Test
    fun `transitionState handles real-world gameplay match ratio recovery and content absence loss`() {
        var state = AnchorPresenceState.PRESENT
        var count = 0

        // In gameplay with real-world rendering variances (e.g. 82% match ratio), state remains PRESENT
        val (state1, count1) = AnchorPresenceEvaluator.transitionState(state, count, 0.82f, "minimap")
        assertEquals(AnchorPresenceState.PRESENT, state1)
        assertEquals(0, count1)

        // Content transition begins: anchor disappears, match ratio drops to 17% -> transitions immediately to LOST
        val (state2, count2) = AnchorPresenceEvaluator.transitionState(state1, count1, 0.17f, "minimap")
        assertEquals(AnchorPresenceState.LOST, state2)
        assertEquals(0, count2)

        // Content returns: anchor returns at 81% (>= MATCH_THRESHOLD_PRESENT 0.80)
        // 1st recovery frame -> count = 1, state still LOST
        val (state3, count3) = AnchorPresenceEvaluator.transitionState(state2, count2, 0.81f, "minimap")
        assertEquals(AnchorPresenceState.LOST, state3)
        assertEquals(1, count3)

        // 2nd recovery frame at 82% -> recovers to PRESENT
        val (state4, count4) = AnchorPresenceEvaluator.transitionState(state3, count3, 0.82f, "minimap")
        assertEquals(AnchorPresenceState.PRESENT, state4)
        assertEquals(0, count4)
    }

    @Test
    fun `matchesSparseProbe returns false when point count is less than 16`() {
        val points = (0 until 15).map { AnchorPoint(it * 0.05f, it * 0.05f, 255, 255, 255) }
        val signature = VisualAnchorSignature("small", points)
        val matched = AnchorPresenceEvaluator.matchesSparseProbe(signature) { _, _ -> colorArgb(255, 255, 255) }
        assertFalse(matched)
    }

    @Test
    fun `matchesSparseProbe returns true when all 16 sparse probe points match`() {
        val points = (0 until 64).map { AnchorPoint(it * 0.01f, it * 0.01f, 200, 200, 200) }
        val signature = VisualAnchorSignature("grid", points)
        val sampledIndices = mutableListOf<Float>()

        val matched =
            AnchorPresenceEvaluator.matchesSparseProbe(signature) { u, _ ->
                sampledIndices.add(u)
                colorArgb(200, 200, 200)
            }

        assertTrue(matched)
        // Exactly 16 points sampled instead of 64
        assertEquals(16, sampledIndices.size)
    }

    @Test
    fun `matchesSparseProbe returns false immediately when a single sparse probe point diverges`() {
        val points = (0 until 64).map { AnchorPoint(it * 0.01f, it * 0.01f, 200, 200, 200) }
        val signature = VisualAnchorSignature("grid", points)
        var sampleCount = 0

        val matched =
            AnchorPresenceEvaluator.matchesSparseProbe(signature) { _, _ ->
                sampleCount++
                if (sampleCount == 3) {
                    colorArgb(0, 0, 0) // Divergent pixel
                } else {
                    colorArgb(200, 200, 200)
                }
            }

        assertFalse(matched)
        // Aborted early on 3rd sample
        assertEquals(3, sampleCount)
    }

    @Test
    fun `STRATIFIED_SPARSE_INDICES partitions all 64 indices across 4 phases without overlap`() {
        val table = AnchorPresenceEvaluator.STRATIFIED_SPARSE_INDICES
        assertEquals(4, table.size)

        val allIndices = mutableListOf<Int>()
        for (phase in 0 until 4) {
            val phaseIndices = table[phase]
            assertEquals(16, phaseIndices.size)
            allIndices.addAll(phaseIndices.toList())
        }

        assertEquals(64, allIndices.size)
        // Verify every index from 0 to 63 appears exactly once
        val uniqueSet = allIndices.toSet()
        assertEquals(64, uniqueSet.size)
        for (i in 0 until 64) {
            assertTrue(uniqueSet.contains(i))
        }
    }

    @Test
    fun `matchesSparseProbe rotates through 4 distinct phases catching phase-specific divergence`() {
        val points = (0 until 64).map { AnchorPoint(it * 0.01f, it * 0.01f, 200, 200, 200) }
        val signature = VisualAnchorSignature("grid", points)

        // Corrupt an index that belongs exclusively to Phase 1 (e.g. index 1)
        val corruptedIndex = AnchorPresenceEvaluator.STRATIFIED_SPARSE_INDICES[1][0]

        val matchedPhase0 =
            AnchorPresenceEvaluator.matchesSparseProbe(signature, phase = 0) { u, _ ->
                val idx = (u / 0.01f).toInt()
                if (idx == corruptedIndex) colorArgb(0, 0, 0) else colorArgb(200, 200, 200)
            }
        // Phase 0 does NOT sample the corrupted index -> remains true
        assertTrue(matchedPhase0)

        val matchedPhase1 =
            AnchorPresenceEvaluator.matchesSparseProbe(signature, phase = 1) { u, _ ->
                val idx = (u / 0.01f).toInt()
                if (idx == corruptedIndex) colorArgb(0, 0, 0) else colorArgb(200, 200, 200)
            }
        // Phase 1 DOES sample the corrupted index -> catches divergence and returns false!
        assertFalse(matchedPhase1)
    }

    @Test
    fun `matchesWithEarlyBailout returns true early when required matches are reached`() {
        val points = (0 until 64).map { AnchorPoint(it * 0.01f, it * 0.01f, 200, 200, 200) }
        val signature = VisualAnchorSignature("grid", points)
        var sampleCount = 0

        val matched =
            AnchorPresenceEvaluator.matchesWithEarlyBailout(signature) { _, _ ->
                sampleCount++
                colorArgb(200, 200, 200)
            }

        assertTrue(matched)
        // Default threshold 0.80: required matches = (64 * 0.80).toInt() = 51. Terminates at sample 51, saving 13 pixel reads!
        assertEquals(51, sampleCount)
    }

    @Test
    fun `matchesWithEarlyBailout returns false early when max mismatches are exceeded`() {
        val points = (0 until 64).map { AnchorPoint(it * 0.01f, it * 0.01f, 200, 200, 200) }
        val signature = VisualAnchorSignature("grid", points)
        var sampleCount = 0

        val matched =
            AnchorPresenceEvaluator.matchesWithEarlyBailout(signature) { _, _ ->
                sampleCount++
                colorArgb(0, 0, 0) // All divergent
            }

        assertFalse(matched)
        // Default threshold 0.80: required matches = 51. Max mismatches = 64 - 51 = 13.
        // On 14th mismatch, it aborts early! Saves 50 pixel reads!
        assertEquals(14, sampleCount)
    }

    @Test
    fun `matchesWithEarlyBailout respects custom threshold`() {
        val points = (0 until 64).map { AnchorPoint(it * 0.01f, it * 0.01f, 200, 200, 200) }
        val signature = VisualAnchorSignature("grid", points)

        var matchSampleCount = 0
        val matched =
            AnchorPresenceEvaluator.matchesWithEarlyBailout(signature, threshold = 0.65f) { _, _ ->
                matchSampleCount++
                colorArgb(200, 200, 200)
            }
        assertTrue(matched)
        // (64 * 0.65).toInt() = 41
        assertEquals(41, matchSampleCount)

        var mismatchSampleCount = 0
        val mismatched =
            AnchorPresenceEvaluator.matchesWithEarlyBailout(signature, threshold = 0.65f) { _, _ ->
                mismatchSampleCount++
                colorArgb(0, 0, 0)
            }
        assertFalse(mismatched)
        // 64 - 41 = 23 max mismatches -> aborts on 24th
        assertEquals(24, mismatchSampleCount)
    }

    @Test
    fun `evaluatePointMatches with empty signature returns empty list`() {
        val signature = VisualAnchorSignature("test", emptyList())
        val results = AnchorPresenceEvaluator.evaluatePointMatches(signature) { _, _ -> colorArgb(255, 255, 255) }
        assertTrue(results.isEmpty())
    }

    @Test
    fun `evaluatePointMatches returns detailed point match results with correct diff and tolerance`() {
        val points =
            listOf(
                AnchorPoint(0.1f, 0.1f, 200, 200, 200),
                AnchorPoint(0.5f, 0.5f, 100, 100, 100),
            )
        val signature = VisualAnchorSignature("test", points)
        // Point 0: provider returns (210, 210, 210) -> diff = 10 + 10 + 10 = 30 <= 45 -> isMatch = true
        // Point 1: provider returns (0, 0, 0) -> diff = 100 + 100 + 100 = 300 > 45 -> isMatch = false
        val results =
            AnchorPresenceEvaluator.evaluatePointMatches(signature) { u, _ ->
                if (u < 0.3f) colorArgb(210, 210, 210) else colorArgb(0, 0, 0)
            }
        assertEquals(2, results.size)
        assertEquals(points[0], results[0].point)
        assertTrue(results[0].isMatch)
        assertEquals(30, results[0].diff)

        assertEquals(points[1], results[1].point)
        assertFalse(results[1].isMatch)
        assertEquals(300, results[1].diff)
    }

    @Test
    fun `coordinate reconstruction via integer truncation recovers exact pixel index across resolutions`() {
        val widths = intArrayOf(64, 100, 160, 200, 1920)
        val heights = intArrayOf(64, 100, 90, 150, 1080)
        val halfPixelOffset = 0.5f

        for (wIdx in widths.indices) {
            val width = widths[wIdx]
            val height = heights[wIdx]

            for (bestX in 0 until width step 7) {
                for (bestY in 0 until height step 7) {
                    val u = (bestX + halfPixelOffset) / width.toFloat()
                    val v = (bestY + halfPixelOffset) / height.toFloat()

                    val reconstructedX = (u * width).toInt()
                    val reconstructedY = (v * height).toInt()

                    assertEquals("Reconstructed X must equal original bestX ($bestX vs $reconstructedX at w=$width)", bestX, reconstructedX)
                    assertEquals(
                        "Reconstructed Y must equal original bestY ($bestY vs $reconstructedY at h=$height)",
                        bestY,
                        reconstructedY,
                    )
                }
            }
        }
    }
}
