package com.stormpanda.megingiard.math

import kotlin.math.max
import kotlin.math.min

private const val VM_DEFAULT_SCALE = 1f
private const val VM_MIN_DIMENSION_PX = 1f
private const val VM_CENTER_DIVISOR = 2f

/**
 * Centered aspect-fitted viewport mapping a layout's authoring `designCanvas` (`[0..1] × [0..1]`)
 * into a physical host secondary display (`[0..hostWidthPx] × [0..hostHeightPx]`).
 *
 * When the host display is wider or taller than the `designCanvas` aspect ratio, [minNormX]/[maxNormX]
 * or [minNormY]/[maxNormY] extend beyond `0f..1f` (`< 0f` and `> 1f`) to represent the active
 * pillarbox/letterbox bar regions in normalized `designCanvas` coordinates.
 */
data class DesignViewport(
    val hostWidthPx: Float,
    val hostHeightPx: Float,
    val designWidthPx: Float,
    val designHeightPx: Float,
    val scale: Float,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val leftPx: Float,
    val topPx: Float,
    val minNormX: Float,
    val maxNormX: Float,
    val minNormY: Float,
    val maxNormY: Float,
) {
    /** Converts a normalized `designCanvas` X coordinate (`0..1`, or `< 0` / `> 1` in bars) to host screen pixels. */
    fun normToScreenX(normX: Float): Float = leftPx + normX * viewportWidthPx

    /** Converts a normalized `designCanvas` Y coordinate (`0..1`, or `< 0` / `> 1` in bars) to host screen pixels. */
    fun normToScreenY(normY: Float): Float = topPx + normY * viewportHeightPx

    /** Converts a host screen X pixel coordinate to normalized `designCanvas` coordinates. */
    fun screenToNormX(screenX: Float): Float = if (viewportWidthPx > 0f) (screenX - leftPx) / viewportWidthPx else 0f

    /** Converts a host screen Y pixel coordinate to normalized `designCanvas` coordinates. */
    fun screenToNormY(screenY: Float): Float = if (viewportHeightPx > 0f) (screenY - topPx) / viewportHeightPx else 0f
}

/**
 * Pure math helper for aspect ratio, scaling, and offset calculations across viewport displays.
 */
object ViewportMath {
    /**
     * Computes the centered, aspect-fitted [DesignViewport] for rendering and hit-testing a layout
     * authored at `(designWidthPx × designHeightPx)` inside a host container of `(hostWidthPx × hostHeightPx)`.
     *
     * Returns a safe `1:1` fallback viewport when any host or design dimension is `<= 0f`.
     */
    fun calculateDesignViewport(
        hostWidthPx: Float,
        hostHeightPx: Float,
        designWidthPx: Float,
        designHeightPx: Float,
    ): DesignViewport {
        if (hostWidthPx <= 0f || hostHeightPx <= 0f || designWidthPx <= 0f || designHeightPx <= 0f) {
            val safeW =
                when {
                    hostWidthPx > 0f -> hostWidthPx
                    designWidthPx > 0f -> designWidthPx
                    else -> VM_MIN_DIMENSION_PX
                }
            val safeH =
                when {
                    hostHeightPx > 0f -> hostHeightPx
                    designHeightPx > 0f -> designHeightPx
                    else -> VM_MIN_DIMENSION_PX
                }
            return DesignViewport(
                hostWidthPx = safeW,
                hostHeightPx = safeH,
                designWidthPx = safeW,
                designHeightPx = safeH,
                scale = VM_DEFAULT_SCALE,
                viewportWidthPx = safeW,
                viewportHeightPx = safeH,
                leftPx = 0f,
                topPx = 0f,
                minNormX = 0f,
                maxNormX = 1f,
                minNormY = 0f,
                maxNormY = 1f,
            )
        }

        val scaleX = hostWidthPx / designWidthPx
        val scaleY = hostHeightPx / designHeightPx
        val scale = min(scaleX, scaleY)

        val viewportW: Float
        val viewportH: Float
        val leftPx: Float
        val topPx: Float
        val minNormX: Float
        val maxNormX: Float
        val minNormY: Float
        val maxNormY: Float

        when {
            scaleX == scaleY -> {
                viewportW = hostWidthPx
                viewportH = hostHeightPx
                leftPx = 0f
                topPx = 0f
                minNormX = 0f
                maxNormX = 1f
                minNormY = 0f
                maxNormY = 1f
            }

            scaleX < scaleY -> {
                viewportW = hostWidthPx
                viewportH = designHeightPx * scale
                leftPx = 0f
                topPx = (hostHeightPx - viewportH) / VM_CENTER_DIVISOR
                minNormX = 0f
                maxNormX = 1f
                minNormY = if (topPx == 0f) 0f else -topPx / viewportH
                maxNormY = (hostHeightPx - topPx) / viewportH
            }

            else -> {
                viewportW = designWidthPx * scale
                viewportH = hostHeightPx
                leftPx = (hostWidthPx - viewportW) / VM_CENTER_DIVISOR
                topPx = 0f
                minNormX = if (leftPx == 0f) 0f else -leftPx / viewportW
                maxNormX = (hostWidthPx - leftPx) / viewportW
                minNormY = 0f
                maxNormY = 1f
            }
        }

        return DesignViewport(
            hostWidthPx = hostWidthPx,
            hostHeightPx = hostHeightPx,
            designWidthPx = designWidthPx,
            designHeightPx = designHeightPx,
            scale = scale,
            viewportWidthPx = viewportW,
            viewportHeightPx = viewportH,
            leftPx = leftPx,
            topPx = topPx,
            minNormX = minNormX,
            maxNormX = maxNormX,
            minNormY = minNormY,
            maxNormY = maxNormY,
        )
    }

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
        val maxTx = max(0f, (scaledW - containerW) / VM_CENTER_DIVISOR)
        val maxTy = max(0f, (scaledH - containerH) / VM_CENTER_DIVISOR)
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
