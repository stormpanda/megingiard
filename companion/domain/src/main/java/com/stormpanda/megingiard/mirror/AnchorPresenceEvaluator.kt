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

    const val SPARSE_PROBE_SAMPLE_COUNT = 16
    const val SPARSE_PROBE_PHASE_COUNT = 4

    private const val STANDARD_SIGNATURE_SIZE = 64
    private const val BLOCKS_PER_ROW = 4
    private const val GRID_COLS = 8

    /**
     * Precomputed stratified index lookup table for standard 64-point (8x8) signatures.
     * Divides the 8x8 grid into sixteen 2x2 blocks. Each phase selects a distinct corner from
     * every 2x2 block, guaranteeing uniform spatial dispersion across the entire bounding box.
     * Together, the 4 phases partition all 64 indices {0..63} without overlap.
     */
    val STRATIFIED_SPARSE_INDICES: Array<IntArray> =
        Array(SPARSE_PROBE_PHASE_COUNT) { phase ->
            val dy = (phase shr 1) and 1
            val dx = phase and 1
            IntArray(SPARSE_PROBE_SAMPLE_COUNT) { b ->
                val by = b / BLOCKS_PER_ROW
                val bx = b % BLOCKS_PER_ROW
                val gy = 2 * by + dy
                val gx = 2 * bx + dx
                gy * GRID_COLS + gx
            }
        }

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
     * Rapidly checks if the signature is solidly present using a 16-point stratified, rotating subgrid.
     *
     * In an 8x8 signature (64 points), each phase [phase] (0..3) samples a spatially dispersed 16-point
     * grid (one point from each 2x2 block). By rotating [phase] across consecutive frames, 100% of all
     * 64 points are verified every 4 frames (66 ms at 60 Hz) while strictly sampling only 16 pixels per frame.
     *
     * Returns true if all sampled sparse points match within tolerance, bypassing full evaluation.
     */
    fun matchesSparseProbe(
        signature: VisualAnchorSignature,
        phase: Int = 0,
        pixelColorProvider: (u: Float, v: Float) -> Int,
    ): Boolean {
        val points = signature.points
        val total = points.size
        if (total < SPARSE_PROBE_SAMPLE_COUNT) return false

        val normalizedPhase = phase and (SPARSE_PROBE_PHASE_COUNT - 1)
        val indices = STRATIFIED_SPARSE_INDICES[normalizedPhase]

        for (i in 0 until SPARSE_PROBE_SAMPLE_COUNT) {
            val idx =
                if (total == STANDARD_SIGNATURE_SIZE) {
                    indices[i]
                } else {
                    (i * (total / SPARSE_PROBE_SAMPLE_COUNT) + normalizedPhase) % total
                }
            val pt = points[idx]
            val color = pixelColorProvider(pt.u, pt.v)
            val r = (color shr SHIFT_RED) and COLOR_BYTE_MASK
            val g = (color shr SHIFT_GREEN) and COLOR_BYTE_MASK
            val b = color and COLOR_BYTE_MASK
            val diff = abs(r - pt.r) + abs(g - pt.g) + abs(b - pt.b)
            if (diff > ANCHOR_DIFF_TOLERANCE) {
                return false
            }
        }
        return true
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
