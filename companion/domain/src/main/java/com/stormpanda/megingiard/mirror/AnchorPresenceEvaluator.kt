package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import kotlin.math.abs

private const val TAG = "AnchorPresenceEvaluator"

/**
 * State representing whether a visual reference anchor is currently detected on the primary screen.
 */
enum class AnchorPresenceState {
    PRESENT,
    LOST,
}

/**
 * Pure evaluation engine for real-time visual anchor presence detection via signature matching.
 *
 * Compares sampled pixel colors against expected reference colors at solid anchor coordinates,
 * and maintains a hysteresis state machine to prevent flickering during transient frame drops.
 */
object AnchorPresenceEvaluator {
    /** Maximum allowed sum of absolute RGB differences (|ΔR| + |ΔG| + |ΔB|) for an anchor to match. */
    const val ANCHOR_DIFF_TOLERANCE = 45

    /** Minimum fraction of matching anchors required to confirm anchor is present (65%). */
    const val MATCH_THRESHOLD_PRESENT = 0.65f

    /** Fraction of matching anchors below which anchor is considered absent (45%). */
    const val MATCH_THRESHOLD_LOST = 0.45f

    /** Number of consecutive checks required to confirm anchor absence (1 check = immediate freeze, preventing content transition leak). */
    const val HYSTERESIS_CONSECUTIVE_LOST = 1

    /** Number of consecutive checks required to confirm recovery back to PRESENT (prevents flickering). */
    const val HYSTERESIS_CONSECUTIVE_RECOVER = 2

    private const val COLOR_BYTE_MASK = 0xFF
    private const val SHIFT_RED = 16
    private const val SHIFT_GREEN = 8

    /**
     * Evaluates the match ratio between [signature] and currently observed pixel colors.
     *
     * @param signature The reference anchor signature for the layout.
     * @param pixelColorProvider Function that takes normalized (u, v) [0.0, 1.0] and returns current packed ARGB Int.
     * @return Match ratio [0.0, 1.0], or 0.0f if signature has no points.
     */
    fun evaluateMatchRatio(
        signature: VisualAnchorSignature,
        pixelColorProvider: (u: Float, v: Float) -> Int,
    ): Float {
        if (signature.points.isEmpty()) return 0f
        var matchCount = 0
        for (pt in signature.points) {
            val color = pixelColorProvider(pt.u, pt.v)
            val r = (color shr SHIFT_RED) and COLOR_BYTE_MASK
            val g = (color shr SHIFT_GREEN) and COLOR_BYTE_MASK
            val b = color and COLOR_BYTE_MASK
            val diff = abs(r - pt.r) + abs(g - pt.g) + abs(b - pt.b)
            if (diff <= ANCHOR_DIFF_TOLERANCE) {
                matchCount++
            }
        }
        return matchCount.toFloat() / signature.points.size.toFloat()
    }

    /**
     * Evaluates a state transition based on [currentState], [consecutiveCount], and [matchRatio].
     *
     * @return A [Pair] containing the updated [AnchorPresenceState] and the updated consecutive counter.
     */
    fun transitionState(
        currentState: AnchorPresenceState,
        consecutiveCount: Int,
        matchRatio: Float,
        cutoutId: String = "",
    ): Pair<AnchorPresenceState, Int> =
        when (currentState) {
            AnchorPresenceState.PRESENT -> {
                if (matchRatio < MATCH_THRESHOLD_LOST) {
                    val nextCount = consecutiveCount + 1
                    if (nextCount >= HYSTERESIS_CONSECUTIVE_LOST) {
                        AppLog.i(TAG, "Layout $cutoutId anchor lost (matchRatio=${(matchRatio * 100).toInt()}%) -> State: LOST")
                        AnchorPresenceState.LOST to 0
                    } else {
                        AnchorPresenceState.PRESENT to nextCount
                    }
                } else {
                    AnchorPresenceState.PRESENT to 0
                }
            }

            AnchorPresenceState.LOST -> {
                if (matchRatio >= MATCH_THRESHOLD_PRESENT) {
                    val nextCount = consecutiveCount + 1
                    if (nextCount >= HYSTERESIS_CONSECUTIVE_RECOVER) {
                        AppLog.i(TAG, "Layout $cutoutId anchor recovered (matchRatio=${(matchRatio * 100).toInt()}%) -> State: PRESENT")
                        AnchorPresenceState.PRESENT to 0
                    } else {
                        AnchorPresenceState.LOST to nextCount
                    }
                } else {
                    AnchorPresenceState.LOST to 0
                }
            }
        }
}
