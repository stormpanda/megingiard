package com.stormpanda.megingiard.macropad

import kotlin.math.sqrt

/**
 * Ramer–Douglas–Peucker polyline simplification for [PathSample] lists.
 *
 * Reduces the number of analog-stick samples recorded by
 * [PhysicalGamepadRecordingManager] while preserving the visual shape of the
 * path within [epsilon] units of normalised axis space (0.0–1.0 range).
 *
 * Recommended epsilon: 0.04f (keeps most direction changes, discards linear
 * interpolation points).
 *
 * This is a pure function with no Android dependencies — it can be unit-tested
 * directly in the `:core` JVM test source set.
 */
fun rdpDecimate(samples: List<PathSample>, epsilon: Float): List<PathSample> {
    if (samples.size <= 2) return samples
    return rdpRecursive(samples, 0, samples.lastIndex, epsilon)
}

private fun rdpRecursive(
    pts: List<PathSample>,
    start: Int,
    end: Int,
    epsilon: Float,
): List<PathSample> {
    if (end - start <= 1) return listOf(pts[start], pts[end])

    val ax = pts[start].x
    val ay = pts[start].y
    val bx = pts[end].x
    val by = pts[end].y
    val abx = bx - ax
    val aby = by - ay
    val abLenSq = abx * abx + aby * aby

    var maxDist = -1f
    var maxIdx = start

    for (i in start + 1 until end) {
        val dist: Float
        if (abLenSq < 1e-18f) {
            /* A and B are the same point — perpendicular distance is just distance from A. */
            val dx = pts[i].x - ax
            val dy = pts[i].y - ay
            dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        } else {
            /* Signed distance from point i to line segment A→B. */
            val t = ((pts[i].x - ax) * abx + (pts[i].y - ay) * aby) / abLenSq
            val px = ax + t * abx
            val py = ay + t * aby
            val dx = pts[i].x - px
            val dy = pts[i].y - py
            dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }
        if (dist > maxDist) {
            maxDist = dist
            maxIdx = i
        }
    }

    return if (maxDist <= epsilon) {
        listOf(pts[start], pts[end])
    } else {
        rdpRecursive(pts, start, maxIdx, epsilon).dropLast(1) +
            rdpRecursive(pts, maxIdx, end, epsilon)
    }
}
