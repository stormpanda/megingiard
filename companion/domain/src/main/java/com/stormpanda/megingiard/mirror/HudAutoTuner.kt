package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "HudAutoTuner"

private const val MIN_FRAMES_REQUIRED = 3
private const val COLOR_BYTE_MASK = 0xFF
private const val SHIFT_RED = 16
private const val SHIFT_GREEN = 8

private const val COLOR_CHANGE_THRESHOLD = 14
private const val TOTAL_COLOR_CHANGE_THRESHOLD = 24
private const val STATIC_SCENE_MIN_MOTION_PCT = 2

private const val MIN_DESPECKLE_NEIGHBORS = 2
private const val ALPHA_SHIFT = 24
private const val RGB_WHITE_MASK = 0x00FFFFFF
private const val FULL_ALPHA_BYTE = 255
private const val PERCENT_MULTIPLIER = 100

const val MASK_PIXEL_TRANSPARENT = 0x00000000
const val MASK_PIXEL_OPAQUE = -1 // 0xFFFFFFFF.toInt()
const val MIN_FEATHERING_PX = 0
const val MAX_FEATHERING_PX = 10

/**
 * Result returned by [HudAutoTuner.analyze].
 *
 * @param maskPixels The generated 2D ARGB transparency mask pixels, or null if insufficient data.
 * @param maskWidth Width of the mask image in pixels.
 * @param maskHeight Height of the mask image in pixels.
 * @param transparentPercent Percentage of pixels identified as moving background and made transparent.
 * @param isStaticScene True if no motion was observed during calibration.
 * @param summary Human-readable summary of the detection result for UI toasts and status.
 */
data class AutoTuneResult(
    val maskPixels: IntArray? = null,
    val maskWidth: Int = 0,
    val maskHeight: Int = 0,
    val transparentPercent: Int = 0,
    val isStaticScene: Boolean = false,
    val summary: String = "",
)

/**
 * Computer vision engine for automated HUD isolation via pixel-level color change detection.
 *
 * Evaluates a sequence of video frame crops captured during the tuning calibration window.
 * Compares the RGB color variance of every pixel across time:
 * - Pixels whose color changes (exceeding video compression noise) are moving 3D scenery and become transparent.
 * - Pixels whose color remains constant are stationary HUD elements and remain opaque.
 * - An edge-dilation pass softens anti-aliased boundaries around fine text and icons.
 */
object HudAutoTuner {
    /**
     * Analyzes [frames] of size [width] x [height] and generates an optimal transparency mask
     * where all pixels that changed color become transparent.
     */
    fun analyze(
        frames: List<IntArray>,
        width: Int,
        height: Int,
        colorChangeThreshold: Int = COLOR_CHANGE_THRESHOLD,
    ): AutoTuneResult {
        if (frames.size < MIN_FRAMES_REQUIRED || width <= 0 || height <= 0) {
            AppLog.w(TAG, "Insufficient frames (${frames.size}) or invalid dimensions (${width}x$height)")
            return AutoTuneResult(
                summary = "Insufficient samples. Calibration cancelled.",
            )
        }

        val pixelCount = width * height

        // 1. Track per-pixel channel min and max across all sampled frames
        val minR = IntArray(pixelCount) { 255 }
        val maxR = IntArray(pixelCount) { 0 }
        val minG = IntArray(pixelCount) { 255 }
        val maxG = IntArray(pixelCount) { 0 }
        val minB = IntArray(pixelCount) { 255 }
        val maxB = IntArray(pixelCount) { 0 }

        for (frame in frames) {
            for (i in 0 until pixelCount) {
                val rgb = frame[i]
                val r = (rgb shr SHIFT_RED) and COLOR_BYTE_MASK
                val g = (rgb shr SHIFT_GREEN) and COLOR_BYTE_MASK
                val b = rgb and COLOR_BYTE_MASK

                if (r < minR[i]) minR[i] = r
                if (r > maxR[i]) maxR[i] = r
                if (g < minG[i]) minG[i] = g
                if (g > maxG[i]) maxG[i] = g
                if (b < minB[i]) minB[i] = b
                if (b > maxB[i]) maxB[i] = b
            }
        }

        // 2. Identify which pixels changed color
        val rawMask = IntArray(pixelCount)
        var changedCount = 0

        for (i in 0 until pixelCount) {
            val diffR = maxR[i] - minR[i]
            val diffG = maxG[i] - minG[i]
            val diffB = maxB[i] - minB[i]
            val maxDiff = maxOf(diffR, diffG, diffB)
            val totalDiff = diffR + diffG + diffB

            val hasChanged = maxDiff > colorChangeThreshold || totalDiff > TOTAL_COLOR_CHANGE_THRESHOLD
            if (hasChanged) {
                rawMask[i] = MASK_PIXEL_TRANSPARENT
                changedCount++
            } else {
                rawMask[i] = MASK_PIXEL_OPAQUE
            }
        }

        val motionPct = (changedCount * PERCENT_MULTIPLIER) / pixelCount
        val isStatic = motionPct < STATIC_SCENE_MIN_MOTION_PCT

        if (isStatic) {
            AppLog.i(TAG, "Static scene detected (motionPct=$motionPct%). All pixels stayed constant.")
            return AutoTuneResult(
                maskPixels = rawMask,
                maskWidth = width,
                maskHeight = height,
                transparentPercent = 0,
                isStaticScene = true,
                summary = "Static scene detected: No motion observed. (Tip: Move in-game during tuning).",
            )
        }

        // 3. Despeckle pass (Morphological opening): filter out isolated noise specks
        val cleanMask = IntArray(pixelCount)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (rawMask[idx] != MASK_PIXEL_TRANSPARENT) {
                    var neighborCount = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until width && ny in 0 until height) {
                                if (rawMask[ny * width + nx] != MASK_PIXEL_TRANSPARENT) {
                                    neighborCount++
                                }
                            }
                        }
                    }
                    cleanMask[idx] = if (neighborCount >= MIN_DESPECKLE_NEIGHBORS) FULL_ALPHA_BYTE else 0
                } else {
                    cleanMask[idx] = 0
                }
            }
        }

        // 4. Separable Gaussian Anti-Aliasing Filter: [1, 2, 1] / 4
        // Pass 1: Horizontal blur
        val tempH = IntArray(pixelCount)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val left = if (x > 0) cleanMask[rowOffset + x - 1] else cleanMask[rowOffset + x]
                val center = cleanMask[rowOffset + x]
                val right = if (x < width - 1) cleanMask[rowOffset + x + 1] else cleanMask[rowOffset + x]
                tempH[rowOffset + x] = (left + (center shl 1) + right) shr 2
            }
        }

        // Pass 2: Vertical blur + ARGB pack
        val finalMask = IntArray(pixelCount)
        var transparentCount = 0

        for (y in 0 until height) {
            val prevRow = if (y > 0) (y - 1) * width else y * width
            val currRow = y * width
            val nextRow = if (y < height - 1) (y + 1) * width else y * width

            for (x in 0 until width) {
                val top = tempH[prevRow + x]
                val mid = tempH[currRow + x]
                val bot = tempH[nextRow + x]
                val alpha = (top + (mid shl 1) + bot) shr 2

                if (alpha == 0) {
                    finalMask[currRow + x] = MASK_PIXEL_TRANSPARENT
                    transparentCount++
                } else {
                    finalMask[currRow + x] = (alpha shl ALPHA_SHIFT) or RGB_WHITE_MASK
                }
            }
        }

        val finalTransparentPct = (transparentCount * PERCENT_MULTIPLIER) / pixelCount
        AppLog.i(
            TAG,
            "Auto-Tune completed: $finalTransparentPct% background transparent (${width}x$height, $changedCount moving px)",
        )

        return AutoTuneResult(
            maskPixels = finalMask,
            maskWidth = width,
            maskHeight = height,
            transparentPercent = finalTransparentPct,
            isStaticScene = false,
            summary = "Tuned: $finalTransparentPct% background made transparent.",
        )
    }

    /**
     * Applies outward edge feathering to [baseMask] of size [width] x [height].
     *
     * Restores pixels that were cut (transparent) immediately adjacent to the HUD boundary
     * with decreasing opacity based on Euclidean distance up to [featheringPx].
     * Pixels that are already set to be visible in [baseMask] (alpha > 0) remain completely unaffected.
     */
    fun applyEdgeFeathering(
        baseMask: IntArray,
        width: Int,
        height: Int,
        featheringPx: Int,
    ): IntArray {
        val clampedFeathering = featheringPx.coerceIn(MIN_FEATHERING_PX, MAX_FEATHERING_PX)
        if (clampedFeathering <= 0 || width <= 0 || height <= 0 || baseMask.size != width * height) {
            return baseMask
        }

        val pixelCount = width * height
        val result = baseMask.clone()

        val nearestX = ShortArray(pixelCount) { -1 }
        val nearestY = ShortArray(pixelCount) { -1 }
        val distSq = FloatArray(pixelCount) { Float.MAX_VALUE }

        val queue = IntArray(pixelCount)
        var head = 0
        var tail = 0

        // Find initial cut pixels directly adjacent to visible pixels
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                val alpha = (baseMask[idx] ushr ALPHA_SHIFT) and COLOR_BYTE_MASK
                if (alpha == 0) continue

                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    val nRowOffset = ny * width

                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx !in 0 until width) continue

                        val nIdx = nRowOffset + nx
                        val nAlpha = (baseMask[nIdx] ushr ALPHA_SHIFT) and COLOR_BYTE_MASK
                        if (nAlpha == 0) {
                            val d = sqrt((dx * dx + dy * dy).toFloat())
                            if (d < distSq[nIdx]) {
                                if (distSq[nIdx] == Float.MAX_VALUE) {
                                    queue[tail++] = nIdx
                                }
                                distSq[nIdx] = d
                                nearestX[nIdx] = x.toShort()
                                nearestY[nIdx] = y.toShort()
                            }
                        }
                    }
                }
            }
        }

        val maxFeatherDist = clampedFeathering.toFloat()

        // Propagate outward up to clampedFeathering
        while (head < tail) {
            val currIdx = queue[head++]
            val cx = currIdx % width
            val cy = currIdx / width
            val sx = nearestX[currIdx].toInt()
            val sy = nearestY[currIdx].toInt()

            for (dy in -1..1) {
                val ny = cy + dy
                if (ny !in 0 until height) continue
                val nRowOffset = ny * width

                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    if (nx !in 0 until width) continue

                    val nIdx = nRowOffset + nx
                    val nAlpha = (baseMask[nIdx] ushr ALPHA_SHIFT) and COLOR_BYTE_MASK
                    if (nAlpha == 0) {
                        val diffX = nx - sx
                        val diffY = ny - sy
                        val d = sqrt((diffX * diffX + diffY * diffY).toFloat())
                        if (d <= maxFeatherDist && d < distSq[nIdx]) {
                            if (distSq[nIdx] == Float.MAX_VALUE) {
                                queue[tail++] = nIdx
                            }
                            distSq[nIdx] = d
                            nearestX[nIdx] = sx.toShort()
                            nearestY[nIdx] = sy.toShort()
                        }
                    }
                }
            }
        }

        // Apply decreased opacity to restored cut pixels
        val divisor = maxFeatherDist + 1.0f
        for (i in 0 until pixelCount) {
            val d = distSq[i]
            if (d <= maxFeatherDist) {
                val falloff = (1.0f - (d / divisor)).coerceIn(0f, 1f)
                val restoredAlpha = (falloff * FULL_ALPHA_BYTE).roundToInt()
                if (restoredAlpha > 0) {
                    result[i] = (restoredAlpha shl ALPHA_SHIFT) or RGB_WHITE_MASK
                }
            }
        }

        return result
    }
}
