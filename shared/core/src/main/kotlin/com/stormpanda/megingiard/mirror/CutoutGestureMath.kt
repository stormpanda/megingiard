package com.stormpanda.megingiard.mirror

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val MIN_ZOOM_DIMENSION = 0.02f
const val MAX_ZOOM_FACTOR = 10.0f
const val ELASTIC_DAMPENING_FACTOR = 0.35f
const val SNAP_BACK_EPSILON = 0.0005f

data class NormalizedCrop(
    val srcX: Float,
    val srcY: Float,
    val srcWidth: Float,
    val srcHeight: Float,
) {
    companion object {
        fun fromCutout(cutout: ScreenCutout): NormalizedCrop =
            NormalizedCrop(
                srcX = cutout.srcX,
                srcY = cutout.srcY,
                srcWidth = cutout.srcWidth,
                srcHeight = cutout.srcHeight,
            )
    }
}

object CutoutGestureMath {
    /**
     * Applies a pan delta on the destination cutout to the current source crop.
     * Incorporates elastic resistance when dragging outside the primary screen boundaries.
     *
     * @param current The current active source crop.
     * @param deltaNormX Normalized pan X delta relative to cutout width (dx / destWidthPx).
     * @param deltaNormY Normalized pan Y delta relative to cutout height (dy / destHeightPx).
     * @return Updated source crop with pan and elastic dampening applied.
     */
    fun applyPan(
        current: NormalizedCrop,
        deltaNormX: Float,
        deltaNormY: Float,
    ): NormalizedCrop {
        val rawDx = -deltaNormX * current.srcWidth
        val rawDy = -deltaNormY * current.srcHeight

        val rawX = current.srcX + rawDx
        val rawY = current.srcY + rawDy

        val dampenedX = applyElasticResistance(rawX, current.srcWidth)
        val dampenedY = applyElasticResistance(rawY, current.srcHeight)

        return current.copy(srcX = dampenedX, srcY = dampenedY)
    }

    /**
     * Applies a pinch-to-zoom transformation around a normalized focal point.
     * Preserves aspect ratio and clamps zoom factor between 1.0x (full frame max) and 10.0x magnification.
     *
     * @param current The current active source crop.
     * @param defaultCrop The base/saved cutout crop anchor.
     * @param scaleFactor The zoom multiplier (> 1 means zooming in / magnifying).
     * @param focalNormX Normalized focal X inside destination cutout [0, 1].
     * @param focalNormY Normalized focal Y inside destination cutout [0, 1].
     * @return Updated source crop after zoom.
     */
    fun applyPinchZoom(
        current: NormalizedCrop,
        defaultCrop: NormalizedCrop,
        scaleFactor: Float,
        focalNormX: Float,
        focalNormY: Float,
    ): NormalizedCrop {
        if (scaleFactor <= 0f) return current

        val baseAspect = if (defaultCrop.srcHeight > 0f) defaultCrop.srcWidth / defaultCrop.srcHeight else 1f
        val minWidth = max(MIN_ZOOM_DIMENSION, defaultCrop.srcWidth / MAX_ZOOM_FACTOR)
        val maxWidth = min(1f, if (baseAspect > 1f) 1f else baseAspect)

        val targetWidth = (current.srcWidth / scaleFactor).coerceIn(minWidth, maxWidth)
        val targetHeight = (targetWidth / baseAspect).coerceIn(MIN_ZOOM_DIMENSION, 1f)
        val finalWidth = targetHeight * baseAspect

        val focalSrcX = current.srcX + focalNormX.coerceIn(0f, 1f) * current.srcWidth
        val focalSrcY = current.srcY + focalNormY.coerceIn(0f, 1f) * current.srcHeight

        val newSrcX = focalSrcX - focalNormX.coerceIn(0f, 1f) * finalWidth
        val newSrcY = focalSrcY - focalNormY.coerceIn(0f, 1f) * targetHeight

        val dampenedX = applyElasticResistance(newSrcX, finalWidth)
        val dampenedY = applyElasticResistance(newSrcY, targetHeight)

        return NormalizedCrop(
            srcX = dampenedX,
            srcY = dampenedY,
            srcWidth = finalWidth,
            srcHeight = targetHeight,
        )
    }

    /**
     * Clamps a crop strictly to valid primary screen bounds [0, 1].
     */
    fun clampToScreenBounds(crop: NormalizedCrop): NormalizedCrop {
        val clampedWidth = crop.srcWidth.coerceIn(MIN_ZOOM_DIMENSION, 1f)
        val clampedHeight = crop.srcHeight.coerceIn(MIN_ZOOM_DIMENSION, 1f)

        val maxX = (1f - clampedWidth).coerceAtLeast(0f)
        val maxY = (1f - clampedHeight).coerceAtLeast(0f)

        val clampedX = crop.srcX.coerceIn(0f, maxX)
        val clampedY = crop.srcY.coerceIn(0f, maxY)

        return NormalizedCrop(
            srcX = clampedX,
            srcY = clampedY,
            srcWidth = clampedWidth,
            srcHeight = clampedHeight,
        )
    }

    /**
     * Linearly interpolates between two crops.
     */
    fun lerp(
        start: NormalizedCrop,
        target: NormalizedCrop,
        fraction: Float,
    ): NormalizedCrop {
        val t = fraction.coerceIn(0f, 1f)
        return NormalizedCrop(
            srcX = start.srcX + (target.srcX - start.srcX) * t,
            srcY = start.srcY + (target.srcY - start.srcY) * t,
            srcWidth = start.srcWidth + (target.srcWidth - start.srcWidth) * t,
            srcHeight = start.srcHeight + (target.srcHeight - start.srcHeight) * t,
        )
    }

    /**
     * Checks if a crop is approximately equal to a target crop within SNAP_BACK_EPSILON.
     */
    fun isCloseTo(
        a: NormalizedCrop,
        b: NormalizedCrop,
        epsilon: Float = SNAP_BACK_EPSILON,
    ): Boolean =
        abs(a.srcX - b.srcX) < epsilon &&
            abs(a.srcY - b.srcY) < epsilon &&
            abs(a.srcWidth - b.srcWidth) < epsilon &&
            abs(a.srcHeight - b.srcHeight) < epsilon

    private fun applyElasticResistance(
        pos: Float,
        size: Float,
    ): Float {
        val maxPos = (1f - size).coerceAtLeast(0f)
        return when {
            pos < 0f -> pos * ELASTIC_DAMPENING_FACTOR
            pos > maxPos -> maxPos + (pos - maxPos) * ELASTIC_DAMPENING_FACTOR
            else -> pos
        }
    }
}
