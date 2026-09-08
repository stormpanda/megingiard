package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class HudPresenceEvaluatorTest {
    private fun colorArgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @Test
    fun `evaluateMatchRatio with empty signature returns 0`() {
        val signature = HudAnchorSignature("test", emptyList())
        val ratio = HudPresenceEvaluator.evaluateMatchRatio(signature) { _, _ -> colorArgb(255, 255, 255) }
        assertEquals(0f, ratio, 0.001f)
    }

    @Test
    fun `evaluateMatchRatio with matching pixels returns 1`() {
        val points =
            listOf(
                AnchorPoint(0.1f, 0.1f, 255, 255, 255),
                AnchorPoint(0.5f, 0.5f, 100, 150, 200),
            )
        val signature = HudAnchorSignature("test", points)

        val ratio =
            HudPresenceEvaluator.evaluateMatchRatio(signature) { u, v ->
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
        val signature = HudAnchorSignature("test", points)

        // All sampled pixels are black (RGB 0,0,0) as in a black bar or dark cutscene
        val ratio = HudPresenceEvaluator.evaluateMatchRatio(signature) { _, _ -> colorArgb(0, 0, 0) }
        assertEquals(0.0f, ratio, 0.001f)
    }

    @Test
    fun `transitionState transitions immediately to LOST and requires 2 checks to recover`() {
        var state = HudPresenceState.PRESENT
        var count = 0

        // 1st check: low match ratio (e.g. 0.10) -> transitions immediately to LOST, count = 0
        val (state1, count1) = HudPresenceEvaluator.transitionState(state, count, 0.10f, "test")
        assertEquals(HudPresenceState.LOST, state1)
        assertEquals(0, count1)

        // 2nd check: high match ratio (e.g. 0.85) -> stays LOST, count becomes 1
        val (state2, count2) = HudPresenceEvaluator.transitionState(state1, count1, 0.85f, "test")
        assertEquals(HudPresenceState.LOST, state2)
        assertEquals(1, count2)

        // 3rd check: high match ratio again -> transitions back to PRESENT, count resets to 0
        val (state3, count3) = HudPresenceEvaluator.transitionState(state2, count2, 0.85f, "test")
        assertEquals(HudPresenceState.PRESENT, state3)
        assertEquals(0, count3)
    }

    @Test
    fun `transitionState resets consecutive counter if a glitch check occurs during recovery`() {
        var state = HudPresenceState.LOST
        var count = 0

        // 1st check: high match ratio -> count = 1
        val (state1, count1) = HudPresenceEvaluator.transitionState(state, count, 0.85f, "test")
        assertEquals(HudPresenceState.LOST, state1)
        assertEquals(1, count1)

        // 2nd check: glitch! Low match ratio -> count resets to 0, stays LOST
        val (state2, count2) = HudPresenceEvaluator.transitionState(state1, count1, 0.20f, "test")
        assertEquals(HudPresenceState.LOST, state2)
        assertEquals(0, count2)
    }
}
