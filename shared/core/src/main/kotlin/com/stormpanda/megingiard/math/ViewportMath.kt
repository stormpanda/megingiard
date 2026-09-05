package com.stormpanda.megingiard.math

import kotlin.math.max
import kotlin.math.min

/**
 * Pure math helper for aspect ratio, scaling, and offset calculations across viewport displays.
 */
object ViewportMath {
    /**
     * Calculates the scale factor to fit content entirely within container bounds (letterbox).
     */
    fun calculateAspectFitScale(
        containerW: Float,
        containerH: Float,
        contentW: Float,
        contentH: Float,
    ): Float {
        if (contentW <= 0f || contentH <= 0f || containerW <= 0f || containerH <= 0f) return 1f
        return min(containerW / contentW, containerH / contentH)
    }

    /**
     * Calculates the scale factor to fill container bounds completely (crop/zoom).
     */
    fun calculateAspectFillScale(
        containerW: Float,
        containerH: Float,
        contentW: Float,
        contentH: Float,
    ): Float {
        if (contentW <= 0f || contentH <= 0f || containerW <= 0f || containerH <= 0f) return 1f
        return max(containerW / contentW, containerH / contentH)
    }

    /**
     * Calculates maximum pan translation offset bounds for content scaled within a container.
     */
    fun getMaxOffsets(
        containerW: Float,
        containerH: Float,
        contentW: Float,
        contentH: Float,
        scale: Float,
    ): Pair<Float, Float> {
        val scaledW = contentW * scale
        val scaledH = contentH * scale
        val maxTx = max(0f, (scaledW - containerW) / 2f)
        val maxTy = max(0f, (scaledH - containerH) / 2f)
        return Pair(maxTx, maxTy)
    }
}

/**
 * Calculates the floor modulo of an integer.
 * Equivalent to java.lang.Math.floorMod, avoiding platform-specific java.lang.Math dependency.
 */
fun Int.floorMod(other: Int): Int {
    if (other == 0) return this
    val r = this % other
    return if ((r xor other) < 0 && r != 0) r + other else r
}

/**
 * Returns the previous element in this list cycling to the last element when at the start,
 * or [current] if the list is empty.
 */
fun <T> List<T>.prevItem(current: T): T {
    if (isEmpty()) return current
    val idx = indexOf(current)
    if (idx < 0) return first()
    return this[(idx - 1 + size) % size]
}

/**
 * Returns the next element in this list cycling to the first element when at the end,
 * or [current] if the list is empty.
 */
fun <T> List<T>.nextItem(current: T): T {
    if (isEmpty()) return current
    val idx = indexOf(current)
    if (idx < 0) return first()
    return this[(idx + 1) % size]
}
