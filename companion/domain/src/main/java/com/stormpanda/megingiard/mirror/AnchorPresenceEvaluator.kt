package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.ANCHOR_HYSTERESIS_GAP
import com.stormpanda.megingiard.macropad.DEFAULT_LAYOUT_ANCHOR_MATCH_THRESHOLD
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

    /** Minimum fraction of matching anchors required to confirm anchor is present by default (80%). */
    const val MATCH_THRESHOLD_PRESENT = DEFAULT_LAYOUT_ANCHOR_MATCH_THRESHOLD

    /** Default fraction of matching anchors below which anchor is considered absent (60%). */
    const val MATCH_THRESHOLD_LOST = 0.60f

    /** Minimum floor for the dynamic lost anchor threshold (35%). */
    const val MIN_MATCH_THRESHOLD_LOST = 0.35f

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
     * Evaluates each anchor point in [signature] against currently observed pixel colors
     * and returns the list of detailed match results.
     */
    fun evaluatePointMatches(
        signature: VisualAnchorSignature,
        pixelColorProvider: (u: Float, v: Float) -> Int,
    ): List<AnchorPointMatchResult> {
        if (signature.points.isEmpty()) return emptyList()
        return signature.points.map { pt ->
            val color = pixelColorProvider(pt.u, pt.v)
            val r = (color shr SHIFT_RED) and COLOR_BYTE_MASK
            val g = (color shr SHIFT_GREEN) and COLOR_BYTE_MASK
            val b = color and COLOR_BYTE_MASK
            val diff = abs(r - pt.r) + abs(g - pt.g) + abs(b - pt.b)
            AnchorPointMatchResult(
                point = pt,
                isMatch = diff <= ANCHOR_DIFF_TOLERANCE,
                diff = diff,
            )
        }
    }

    /**
     * Evaluates whether [signature] matches at or above [threshold] with mathematical early-bailout.
     * Stops immediately when the required matches threshold is reached or when remaining points
     * cannot mathematically reach [threshold], eliminating unnecessary pixel sampling.
     */
    fun matchesWithEarlyBailout(
        signature: VisualAnchorSignature,
        threshold: Float = MATCH_THRESHOLD_PRESENT,
        pixelColorProvider: (u: Float, v: Float) -> Int,
    ): Boolean {
        val points = signature.points
        val total = points.size
        if (total == 0) return false
        val requiredMatches = (total * threshold).toInt().coerceAtLeast(1)
        val maxMismatches = total - requiredMatches
        var matches = 0
        var mismatches = 0
        for (pt in points) {
            val color = pixelColorProvider(pt.u, pt.v)
            val r = (color shr SHIFT_RED) and COLOR_BYTE_MASK
            val g = (color shr SHIFT_GREEN) and COLOR_BYTE_MASK
            val b = color and COLOR_BYTE_MASK
            val diff = abs(r - pt.r) + abs(g - pt.g) + abs(b - pt.b)
            if (diff <= ANCHOR_DIFF_TOLERANCE) {
                matches++
                if (matches >= requiredMatches) {
                    return true
                }
            } else {
                mismatches++
                if (mismatches > maxMismatches) {
                    return false
                }
            }
        }
        return matches >= requiredMatches
    }

    /**
     * Evaluates a state transition based on [currentState], [consecutiveCount], [matchRatio],
     * and the configured [presentThreshold].
     *
     * Maintains a dynamic hysteresis gap ([ANCHOR_HYSTERESIS_GAP] = 20%) between present and lost states.
     *
     * @return A [Pair] containing the updated [AnchorPresenceState] and the updated consecutive counter.
     */
    fun transitionState(
        currentState: AnchorPresenceState,
        consecutiveCount: Int,
        matchRatio: Float,
        cutoutId: String = "",
        presentThreshold: Float = MATCH_THRESHOLD_PRESENT,
    ): Pair<AnchorPresenceState, Int> {
        val lostThreshold = (presentThreshold - ANCHOR_HYSTERESIS_GAP).coerceAtLeast(MIN_MATCH_THRESHOLD_LOST)
        return when (currentState) {
            AnchorPresenceState.PRESENT -> {
                if (matchRatio < lostThreshold) {
                    val nextCount = consecutiveCount + 1
                    if (nextCount >= HYSTERESIS_CONSECUTIVE_LOST) {
                        AppLog.i(
                            TAG,
                            "Layout $cutoutId anchor lost (matchRatio=${(matchRatio * 100).toInt()}%, threshold=${(lostThreshold * 100).toInt()}%) -> State: LOST",
                        )
                        AnchorPresenceState.LOST to 0
                    } else {
                        AnchorPresenceState.PRESENT to nextCount
                    }
                } else {
                    AnchorPresenceState.PRESENT to 0
                }
            }

            AnchorPresenceState.LOST -> {
                if (matchRatio >= presentThreshold) {
                    val nextCount = consecutiveCount + 1
                    if (nextCount >= HYSTERESIS_CONSECUTIVE_RECOVER) {
                        AppLog.i(
                            TAG,
                            "Layout $cutoutId anchor recovered (matchRatio=${(matchRatio * 100).toInt()}%, threshold=${(presentThreshold * 100).toInt()}%) -> State: PRESENT",
                        )
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
}
