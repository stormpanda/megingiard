package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
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
    fun `transitionState respects MATCH_THRESHOLD_LOST of 0_45f`() {
        var state = AnchorPresenceState.PRESENT
        var count = 0

        // Ratio just above 0.45 (0.46) remains PRESENT
        val (state1, count1) = AnchorPresenceEvaluator.transitionState(state, count, 0.46f, "test")
        assertEquals(AnchorPresenceState.PRESENT, state1)
        assertEquals(0, count1)

        // Ratio below 0.45 (0.44) transitions immediately to LOST
        val (state2, count2) = AnchorPresenceEvaluator.transitionState(state1, count1, 0.44f, "test")
        assertEquals(AnchorPresenceState.LOST, state2)
        assertEquals(0, count2)
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

        // In gameplay with real-world rendering variances (e.g. 68% match ratio), state remains PRESENT
        val (state1, count1) = AnchorPresenceEvaluator.transitionState(state, count, 0.68f, "minimap")
        assertEquals(AnchorPresenceState.PRESENT, state1)
        assertEquals(0, count1)

        // Content transition begins: anchor disappears, match ratio drops to 17% -> transitions immediately to LOST
        val (state2, count2) = AnchorPresenceEvaluator.transitionState(state1, count1, 0.17f, "minimap")
        assertEquals(AnchorPresenceState.LOST, state2)
        assertEquals(0, count2)

        // Content returns: anchor returns at 67% (>= MATCH_THRESHOLD_PRESENT 0.65)
        // 1st recovery frame -> count = 1, state still LOST
        val (state3, count3) = AnchorPresenceEvaluator.transitionState(state2, count2, 0.67f, "minimap")
        assertEquals(AnchorPresenceState.LOST, state3)
        assertEquals(1, count3)

        // 2nd recovery frame at 68% -> recovers to PRESENT
        val (state4, count4) = AnchorPresenceEvaluator.transitionState(state3, count3, 0.68f, "minimap")
        assertEquals(AnchorPresenceState.PRESENT, state4)
        assertEquals(0, count4)
    }
}
