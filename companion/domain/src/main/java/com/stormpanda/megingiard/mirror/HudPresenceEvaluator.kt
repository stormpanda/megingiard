package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import kotlin.math.abs

private const val TAG = "HudPresenceEvaluator"

/**
 * State representing whether a mirrored HUD element is currently detected on the primary screen.
 */
enum class HudPresenceState {
    PRESENT,
    LOST,
}

/**
 * Pure evaluation engine for real-time HUD presence detection via anchor signature matching.
 *
 * Compares sampled pixel colors against expected reference colors at solid anchor coordinates,
 * and maintains a hysteresis state machine to prevent flickering during transient frame drops.
 */
object HudPresenceEvaluator {
    /** Maximum allowed sum of absolute RGB differences (|ΔR| + |ΔG| + |ΔB|) for an anchor to match. */
    const val ANCHOR_DIFF_TOLERANCE = 45

    /** Minimum fraction of matching anchors required to confirm HUD is present (65%). */
    const val MATCH_THRESHOLD_PRESENT = 0.65f

    /** Fraction of matching anchors below which HUD is considered absent (45%). */
    const val MATCH_THRESHOLD_LOST = 0.45f

    /** Number of consecutive checks required to confirm HUD absence (1 check = immediate freeze, preventing cutscene leak). */
    const val HYSTERESIS_CONSECUTIVE_LOST = 1

    /** Number of consecutive checks required to confirm recovery back to PRESENT (prevents flickering). */
    const val HYSTERESIS_CONSECUTIVE_RECOVER = 2

    private const val COLOR_BYTE_MASK = 0xFF
    private const val SHIFT_RED = 16
    private const val SHIFT_GREEN = 8

    /**
     * Evaluates the match ratio between [signature] and currently observed pixel colors.
     *
     * @param signature The reference anchor signature for the cutout.
     * @param pixelColorProvider Function that takes normalized (u, v) [0.0, 1.0] and returns current packed ARGB Int.
     * @return Match ratio [0.0, 1.0], or 0.0f if signature has no points.
     */
    fun evaluateMatchRatio(
        signature: HudAnchorSignature,
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
     * @return A [Pair] containing the updated [HudPresenceState] and the updated consecutive counter.
     */
    fun transitionState(
        currentState: HudPresenceState,
        consecutiveCount: Int,
        matchRatio: Float,
        cutoutId: String = "",
    ): Pair<HudPresenceState, Int> =
        when (currentState) {
            HudPresenceState.PRESENT -> {
                if (matchRatio < MATCH_THRESHOLD_LOST) {
                    val nextCount = consecutiveCount + 1
                    if (nextCount >= HYSTERESIS_CONSECUTIVE_LOST) {
                        AppLog.i(TAG, "Cutout $cutoutId HUD lost (matchRatio=${(matchRatio * 100).toInt()}%) -> State: LOST")
                        HudPresenceState.LOST to 0
                    } else {
                        HudPresenceState.PRESENT to nextCount
                    }
                } else {
                    HudPresenceState.PRESENT to 0
                }
            }

            HudPresenceState.LOST -> {
                if (matchRatio >= MATCH_THRESHOLD_PRESENT) {
                    val nextCount = consecutiveCount + 1
                    if (nextCount >= HYSTERESIS_CONSECUTIVE_RECOVER) {
                        AppLog.i(TAG, "Cutout $cutoutId HUD recovered (matchRatio=${(matchRatio * 100).toInt()}%) -> State: PRESENT")
                        HudPresenceState.PRESENT to 0
                    } else {
                        HudPresenceState.LOST to nextCount
                    }
                } else {
                    HudPresenceState.LOST to 0
                }
            }
        }
}
