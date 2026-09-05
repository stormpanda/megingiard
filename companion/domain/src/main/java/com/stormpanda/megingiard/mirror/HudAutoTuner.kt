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
const val MIN_TRANSLUCENCY = 0
const val MAX_TRANSLUCENCY = 100

private const val MIN_HALO_RADIUS = 4
private const val MAX_HALO_RADIUS = 48
private const val MAX_HALO_VARIANCE_BOOST = 165
private const val ENCLOSED_BASE_THRESHOLD = 30
private const val ENCLOSED_MAX_VARIANCE_BOOST = 120
private const val ENCLOSED_MAX_BARRIER_BOOST = 55
private const val MIN_PARTIAL_ALPHA = 40

/**
 * Result returned by [HudAutoTuner.analyze].
 *
 * @param maskPixels The generated 2D ARGB transparency mask pixels, or null if insufficient data.
 * @param maskWidth Width of the mask image in pixels.
 * @param maskHeight Height of the mask image in pixels.
 * @param transparentPercent Percentage of pixels identified as moving background and made transparent.
 * @param isStaticScene True if no motion was observed during calibration.
 * @param summary Human-readable summary of the detection result for UI toasts and status.
 * @param varianceMap Raw per-pixel maximum color variation byte map used for dynamic translucency tuning.
 */
data class AutoTuneResult(
    val maskPixels: IntArray? = null,
    val maskWidth: Int = 0,
    val maskHeight: Int = 0,
    val transparentPercent: Int = 0,
    val isStaticScene: Boolean = false,
    val summary: String = "",
    val varianceMap: ByteArray? = null,
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

        // 2. Identify which pixels changed color and capture per-pixel variance map
        val rawMask = IntArray(pixelCount)
        val varianceMap = ByteArray(pixelCount)
        var changedCount = 0

        for (i in 0 until pixelCount) {
            val diffR = maxR[i] - minR[i]
            val diffG = maxG[i] - minG[i]
            val diffB = maxB[i] - minB[i]
            val maxDiff = maxOf(diffR, diffG, diffB)
            val totalDiff = diffR + diffG + diffB
            val effectiveDiff =
                maxOf(
                    maxDiff,
                    (totalDiff * COLOR_CHANGE_THRESHOLD + (TOTAL_COLOR_CHANGE_THRESHOLD - 1)) / TOTAL_COLOR_CHANGE_THRESHOLD,
                )
            varianceMap[i] = effectiveDiff.coerceIn(0, 255).toByte()

            val hasChanged = effectiveDiff > colorChangeThreshold
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
                varianceMap = varianceMap,
            )
        }

        // 3. Build base mask with despeckle and anti-aliasing
        val finalMask =
            buildMask(
                varianceMap = varianceMap,
                width = width,
                height = height,
                translucency = MIN_TRANSLUCENCY,
                featheringPx = MIN_FEATHERING_PX,
                colorChangeThreshold = colorChangeThreshold,
            )

        var transparentCount = 0
        for (i in 0 until pixelCount) {
            if (finalMask[i] == MASK_PIXEL_TRANSPARENT) {
                transparentCount++
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
            varianceMap = varianceMap,
        )
    }

    /**
     * Builds a 2D ARGB transparency mask from a raw per-pixel [varianceMap].
     *
     * Stationary HUD pixels (variance <= [colorChangeThreshold]) form solid core anchors.
     * When [translucency] > 0:
     * - Geometric Enclosure Infill: Pixels enclosed within outer HUD borders (cannot reach the outer crop
     *   boundaries without crossing solid core pixels) have their allowed variance relaxed up to
     *   [ENCLOSED_BASE_THRESHOLD] + [translucency] * [ENCLOSED_STEP_MULTIPLIER], capturing inner dials/cavities.
     * - Proximity Halo: Pixels within a spatial radius (3..12 px) of solid core anchors are allowed damped
     *   variance up to [colorChangeThreshold] + [translucency] * [HALO_VARIANCE_MULTIPLIER], smoothly recovering
     *   semi-transparent borders, starburst glows, and translucent glass panels.
     *
     * Morphological despeckling and separable Gaussian anti-aliasing are applied, followed by optional
     * outward edge [featheringPx].
     */
    fun buildMask(
        varianceMap: ByteArray,
        width: Int,
        height: Int,
        translucency: Int = MIN_TRANSLUCENCY,
        featheringPx: Int = MIN_FEATHERING_PX,
        colorChangeThreshold: Int = COLOR_CHANGE_THRESHOLD,
    ): IntArray {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedFeathering = featheringPx.coerceIn(MIN_FEATHERING_PX, MAX_FEATHERING_PX)
        val pixelCount = width * height
        if (width <= 0 || height <= 0 || varianceMap.size != pixelCount) {
            return IntArray(0)
        }

        val candidateAlpha = IntArray(pixelCount)

        // 1. Core stationary HUD anchors
        for (i in 0 until pixelCount) {
            val v = varianceMap[i].toInt() and COLOR_BYTE_MASK
            if (v <= colorChangeThreshold) {
                candidateAlpha[i] = FULL_ALPHA_BYTE
            }
        }

        // 2. Translucency recovery (Enclosure infill & Proximity halo)
        if (clampedTranslucency > 0) {
            // A. Geometric Enclosure Infill: Flood fill from borders to identify exterior background.
            // A soft barrier threshold allows semi-transparent outer boundary rings (e.g. dial circles) to seal the interior cavity.
            val barrierThreshold =
                colorChangeThreshold + (clampedTranslucency * ENCLOSED_MAX_BARRIER_BOOST) / MAX_TRANSLUCENCY

            val exteriorQueue = IntArray(pixelCount)
            var extHead = 0
            var extTail = 0
            val isExterior = BooleanArray(pixelCount)

            fun tryEnqueueExterior(
                x: Int,
                y: Int,
            ) {
                val idx = y * width + x
                if (!isExterior[idx]) {
                    val v = varianceMap[idx].toInt() and COLOR_BYTE_MASK
                    if (v > barrierThreshold) {
                        isExterior[idx] = true
                        exteriorQueue[extTail++] = idx
                    }
                }
            }

            for (x in 0 until width) {
                tryEnqueueExterior(x, 0)
                tryEnqueueExterior(x, height - 1)
            }
            for (y in 0 until height) {
                tryEnqueueExterior(0, y)
                tryEnqueueExterior(width - 1, y)
            }

            while (extHead < extTail) {
                val curr = exteriorQueue[extHead++]
                val cx = curr % width
                val cy = curr / width

                if (cx > 0) {
                    val n = curr - 1
                    if (!isExterior[n] && (varianceMap[n].toInt() and COLOR_BYTE_MASK) > barrierThreshold) {
                        isExterior[n] = true
                        exteriorQueue[extTail++] = n
                    }
                }
                if (cx < width - 1) {
                    val n = curr + 1
                    if (!isExterior[n] && (varianceMap[n].toInt() and COLOR_BYTE_MASK) > barrierThreshold) {
                        isExterior[n] = true
                        exteriorQueue[extTail++] = n
                    }
                }
                if (cy > 0) {
                    val n = curr - width
                    if (!isExterior[n] && (varianceMap[n].toInt() and COLOR_BYTE_MASK) > barrierThreshold) {
                        isExterior[n] = true
                        exteriorQueue[extTail++] = n
                    }
                }
                if (cy < height - 1) {
                    val n = curr + width
                    if (!isExterior[n] && (varianceMap[n].toInt() and COLOR_BYTE_MASK) > barrierThreshold) {
                        isExterior[n] = true
                        exteriorQueue[extTail++] = n
                    }
                }
            }

            val cavityThreshold =
                ENCLOSED_BASE_THRESHOLD + (clampedTranslucency * ENCLOSED_MAX_VARIANCE_BOOST) / MAX_TRANSLUCENCY
            for (i in 0 until pixelCount) {
                if (!isExterior[i] && candidateAlpha[i] == 0) {
                    val v = varianceMap[i].toInt() and COLOR_BYTE_MASK
                    if (v <= cavityThreshold) {
                        candidateAlpha[i] = FULL_ALPHA_BYTE
                    }
                }
            }

            // B. Proximity Halo: Outward distance expansion from core anchors
            val haloRadius =
                MIN_HALO_RADIUS + (clampedTranslucency * (MAX_HALO_RADIUS - MIN_HALO_RADIUS)) / MAX_TRANSLUCENCY
            val maxVarianceBoost = (clampedTranslucency * MAX_HALO_VARIANCE_BOOST) / MAX_TRANSLUCENCY
            val haloDist = ShortArray(pixelCount) { -1 }
            val haloQueue = IntArray(pixelCount)
            var haloHead = 0
            var haloTail = 0

            for (i in 0 until pixelCount) {
                if (candidateAlpha[i] == FULL_ALPHA_BYTE) {
                    haloDist[i] = 0
                    haloQueue[haloTail++] = i
                }
            }

            while (haloHead < haloTail) {
                val curr = haloQueue[haloHead++]
                val cd = haloDist[curr].toInt()
                if (cd >= haloRadius) continue

                val cx = curr % width
                val cy = curr / width

                val neighbors =
                    intArrayOf(
                        if (cx > 0) curr - 1 else -1,
                        if (cx < width - 1) curr + 1 else -1,
                        if (cy > 0) curr - width else -1,
                        if (cy < height - 1) curr + width else -1,
                    )

                for (n in neighbors) {
                    if (n != -1 && haloDist[n].toInt() == -1) {
                        val nd = (cd + 1).toShort()
                        haloDist[n] = nd
                        haloQueue[haloTail++] = n

                        val v = varianceMap[n].toInt() and COLOR_BYTE_MASK
                        val distFactor = (haloRadius - nd + 1).toFloat() / (haloRadius + 1).toFloat()
                        val allowedVariance = colorChangeThreshold + (maxVarianceBoost * distFactor).roundToInt()
                        if (v <= allowedVariance) {
                            val alpha =
                                (FULL_ALPHA_BYTE * distFactor)
                                    .roundToInt()
                                    .coerceIn(MIN_PARTIAL_ALPHA, FULL_ALPHA_BYTE)
                            if (alpha > candidateAlpha[n]) {
                                candidateAlpha[n] = alpha
                            }
                        }
                    }
                }
            }
        }

        // 3. Morphological Despeckle (Filter isolated noise specks)
        val cleanMask = IntArray(pixelCount)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                val alpha = candidateAlpha[idx]
                if (alpha > 0) {
                    var neighborCount = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until width && ny in 0 until height) {
                                if (candidateAlpha[ny * width + nx] > 0) {
                                    neighborCount++
                                }
                            }
                        }
                    }
                    cleanMask[idx] = if (neighborCount >= MIN_DESPECKLE_NEIGHBORS) alpha else 0
                }
            }
        }

        // 4. Separable Gaussian Anti-Aliasing Filter: [1, 2, 1] / 4
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

        val finalMask = IntArray(pixelCount)
        for (y in 0 until height) {
            val prevRow = if (y > 0) (y - 1) * width else y * width
            val currRow = y * width
            val nextRow = if (y < height - 1) (y + 1) * width else y * width

            for (x in 0 until width) {
                val top = tempH[prevRow + x]
                val mid = tempH[currRow + x]
                val bot = tempH[nextRow + x]
                val alpha = (top + (mid shl 1) + bot) shr 2

                finalMask[currRow + x] =
                    if (alpha == 0) {
                        MASK_PIXEL_TRANSPARENT
                    } else {
                        (alpha shl ALPHA_SHIFT) or RGB_WHITE_MASK
                    }
            }
        }

        // 5. Edge Feathering
        return if (clampedFeathering > 0) {
            applyEdgeFeathering(finalMask, width, height, clampedFeathering)
        } else {
            finalMask
        }
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
