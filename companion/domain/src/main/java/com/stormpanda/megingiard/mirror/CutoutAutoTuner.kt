package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "CutoutAutoTuner"

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

const val MIN_CALIBRATION_FRAMES = 5
const val MASK_PIXEL_TRANSPARENT = 0x00000000
const val MASK_PIXEL_OPAQUE = -1 // 0xFFFFFFFF.toInt()
const val MIN_TRANSLUCENCY = 0
const val MAX_TRANSLUCENCY = 100
const val MIN_SENSITIVITY = 4
const val MAX_SENSITIVITY = 40
const val DEFAULT_SENSITIVITY = 14

private const val CLOSING_RADIUS = 3
private const val TRIMAP_TRANSITION_MAX = 35

private const val MAX_TRANSLUCENT_VARIANCE = 180
private const val MIN_TRANSLUCENT_ALPHA = 60

private const val SIGNATURE_GRID_COLS = 8
private const val SIGNATURE_GRID_ROWS = 8
private const val MAX_ANCHOR_VARIANCE = 10
private const val HALF_PIXEL_OFFSET = 0.5f

/**
 * Result returned by [CutoutAutoTuner.analyze].
 *
 * @param maskPixels The generated 2D ARGB transparency mask pixels, or null if insufficient data.
 * @param maskWidth Width of the mask image in pixels.
 * @param maskHeight Height of the mask image in pixels.
 * @param transparentPercent Percentage of pixels identified as moving background and made transparent.
 * @param isStaticScene True if no motion was observed during calibration.
 * @param summary Human-readable summary of the detection result for UI toasts and status.
 * @param varianceMap Raw per-pixel maximum color variation byte map used for dynamic translucency tuning.
 * @param anchorSignature Spatially distributed anchor sample points used for real-time presence detection.
 * @param referenceColorFrame Clean temporal average RGB frame calculated across all calibration samples.
 */
data class AutoTuneResult(
    val maskPixels: IntArray? = null,
    val maskWidth: Int = 0,
    val maskHeight: Int = 0,
    val transparentPercent: Int = 0,
    val isStaticScene: Boolean = false,
    val summary: String = "",
    val varianceMap: ByteArray? = null,
    val anchorSignature: VisualAnchorSignature? = null,
    val referenceColorFrame: IntArray? = null,
)

/**
 * Computer vision engine for automated foreground isolation via pixel-level color change detection.
 *
 * Evaluates a sequence of video frame crops captured during the tuning calibration window.
 * Compares the RGB color variance of every pixel across time:
 * - Pixels whose color changes (exceeding video compression noise) are moving scenery and become transparent.
 * - Pixels whose color remains constant are stationary foreground elements and remain opaque.
 * - An edge-dilation pass softens anti-aliased boundaries around fine text and icons.
 */
object CutoutAutoTuner {
    /**
     * Analyzes [frames] of size [width] x [height] and generates an optimal transparency mask
     * where all pixels that changed color become transparent.
     */
    fun analyze(
        frames: List<IntArray>,
        width: Int,
        height: Int,
        colorChangeThreshold: Int = COLOR_CHANGE_THRESHOLD,
        cutoutId: String = "",
    ): AutoTuneResult {
        if (frames.size < MIN_FRAMES_REQUIRED || width <= 0 || height <= 0) {
            AppLog.w(TAG, "Insufficient frames (${frames.size}) or invalid dimensions (${width}x$height)")
            return AutoTuneResult(
                summary = "Insufficient samples. Calibration cancelled.",
            )
        }

        val pixelCount = width * height

        // 1. Track per-pixel channel min, max, and sum across all sampled frames
        val minR = IntArray(pixelCount) { 255 }
        val maxR = IntArray(pixelCount) { 0 }
        val minG = IntArray(pixelCount) { 255 }
        val maxG = IntArray(pixelCount) { 0 }
        val minB = IntArray(pixelCount) { 255 }
        val maxB = IntArray(pixelCount) { 0 }

        val sumR = IntArray(pixelCount)
        val sumG = IntArray(pixelCount)
        val sumB = IntArray(pixelCount)

        for (frame in frames) {
            for (i in 0 until pixelCount) {
                val rgb = frame[i]
                val r = (rgb shr SHIFT_RED) and COLOR_BYTE_MASK
                val g = (rgb shr SHIFT_GREEN) and COLOR_BYTE_MASK
                val b = rgb and COLOR_BYTE_MASK

                sumR[i] += r
                sumG[i] += g
                sumB[i] += b

                if (r < minR[i]) minR[i] = r
                if (r > maxR[i]) maxR[i] = r
                if (g < minG[i]) minG[i] = g
                if (g > maxG[i]) maxG[i] = g
                if (b < minB[i]) minB[i] = b
                if (b > maxB[i]) maxB[i] = b
            }
        }

        val frameCount = frames.size
        val referenceColorFrame = IntArray(pixelCount)
        for (i in 0 until pixelCount) {
            val avgR = (sumR[i] / frameCount).coerceIn(0, 255)
            val avgG = (sumG[i] / frameCount).coerceIn(0, 255)
            val avgB = (sumB[i] / frameCount).coerceIn(0, 255)
            referenceColorFrame[i] = (FULL_ALPHA_BYTE shl ALPHA_SHIFT) or (avgR shl SHIFT_RED) or (avgG shl SHIFT_GREEN) or avgB
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

        // Always extract stationary anchor signature points regardless of whether motion occurred,
        // because visual anchors are reference crops of static elements (e.g. icons, menus, portraits)
        // where 0% motion is optimal rather than a failure.
        val signature =
            extractAnchorSignature(
                varianceMap = varianceMap,
                frames = frames,
                width = width,
                height = height,
                cutoutId = cutoutId,
            )

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
                anchorSignature = signature,
                referenceColorFrame = referenceColorFrame,
            )
        }

        // 3. Build base mask with despeckle and anti-aliasing
        val finalMask =
            buildMask(
                varianceMap = varianceMap,
                width = width,
                height = height,
                translucency = MIN_TRANSLUCENCY,
                colorChangeThreshold = colorChangeThreshold,
            )

        var transparentCount = 0
        for (i in 0 until pixelCount) {
            if (finalMask[i] == MASK_PIXEL_TRANSPARENT) {
                transparentCount++
            }
        }

        val rawTransparentPct = (transparentCount * PERCENT_MULTIPLIER) / pixelCount
        val finalTransparentPct = rawTransparentPct.coerceIn(0, 100)
        AppLog.i(
            TAG,
            "Auto-Tune completed: $finalTransparentPct% background transparent (${width}x$height, ${signature.points.size} anchors)",
        )

        return AutoTuneResult(
            maskPixels = finalMask,
            maskWidth = width,
            maskHeight = height,
            transparentPercent = finalTransparentPct,
            isStaticScene = false,
            summary = "Tuned: $finalTransparentPct% background made transparent.",
            varianceMap = varianceMap,
            anchorSignature = signature,
            referenceColorFrame = referenceColorFrame,
        )
    }

    /**
     * Extracts a compact, spatially stratified [VisualAnchorSignature] from stationary pixels
     * across an 8x8 grid over the cutout.
     */
    fun extractAnchorSignature(
        varianceMap: ByteArray,
        frames: List<IntArray>,
        width: Int,
        height: Int,
        cutoutId: String = "",
    ): VisualAnchorSignature {
        if (varianceMap.isEmpty() || frames.isEmpty() || width <= 0 || height <= 0) {
            return VisualAnchorSignature(cutoutId, emptyList())
        }

        val points = ArrayList<AnchorPoint>()
        val cellW = width.toFloat() / SIGNATURE_GRID_COLS.toFloat()
        val cellH = height.toFloat() / SIGNATURE_GRID_ROWS.toFloat()

        for (gy in 0 until SIGNATURE_GRID_ROWS) {
            val yStart = (gy * cellH).toInt().coerceIn(0, height - 1)
            val yEnd = ((gy + 1) * cellH).toInt().coerceIn(yStart + 1, height)

            for (gx in 0 until SIGNATURE_GRID_COLS) {
                val xStart = (gx * cellW).toInt().coerceIn(0, width - 1)
                val xEnd = ((gx + 1) * cellW).toInt().coerceIn(xStart + 1, width)

                var bestX = -1
                var bestY = -1
                var minVar = Int.MAX_VALUE

                for (y in yStart until yEnd) {
                    val rowOffset = y * width
                    for (x in xStart until xEnd) {
                        val v = varianceMap[rowOffset + x].toInt() and COLOR_BYTE_MASK
                        if (v <= MAX_ANCHOR_VARIANCE && v < minVar) {
                            minVar = v
                            bestX = x
                            bestY = y
                        }
                    }
                }

                if (bestX >= 0 && bestY >= 0) {
                    val bestIdx = bestY * width + bestX
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    for (frame in frames) {
                        val rgb = frame[bestIdx]
                        sumR += (rgb shr SHIFT_RED) and COLOR_BYTE_MASK
                        sumG += (rgb shr SHIFT_GREEN) and COLOR_BYTE_MASK
                        sumB += rgb and COLOR_BYTE_MASK
                    }
                    val frameCount = frames.size
                    val avgR = sumR / frameCount
                    val avgG = sumG / frameCount
                    val avgB = sumB / frameCount

                    val u = (bestX + HALF_PIXEL_OFFSET) / width.toFloat()
                    val v = (bestY + HALF_PIXEL_OFFSET) / height.toFloat()
                    points.add(AnchorPoint(u = u, v = v, r = avgR, g = avgG, b = avgB))
                }
            }
        }

        AppLog.d(TAG, "Extracted ${points.size} anchor signature points for cutout '$cutoutId' (${width}x$height)")
        return VisualAnchorSignature(cutoutId, points)
    }

    /**
     * Combines an opaque reference RGB frame ([baseRgbFrame]) with a computed transparency mask ([maskAlphaPixels])
     * of size [width] x [height], producing a 32-bit ARGB pre-rendered static asset image.
     *
     * Pixels with alpha == 0 become fully transparent (0x00000000).
     */
    fun buildStaticAsset(
        baseRgbFrame: IntArray,
        maskAlphaPixels: IntArray,
        width: Int,
        height: Int,
    ): IntArray {
        val pixelCount = width * height
        if (baseRgbFrame.size != pixelCount || maskAlphaPixels.size != pixelCount || width <= 0 || height <= 0) {
            return IntArray(0)
        }

        val result = IntArray(pixelCount)
        for (i in 0 until pixelCount) {
            val alpha = (maskAlphaPixels[i] ushr ALPHA_SHIFT) and COLOR_BYTE_MASK
            if (alpha == 0) {
                result[i] = MASK_PIXEL_TRANSPARENT
            } else {
                val rgb = baseRgbFrame[i] and RGB_WHITE_MASK
                result[i] = (alpha shl ALPHA_SHIFT) or rgb
            }
        }
        return result
    }

    /**
     * Builds a 2D ARGB transparency mask from a raw per-pixel [varianceMap].
     *
     * Stationary foreground pixels (variance <= [colorChangeThreshold]) form solid core anchors.
     * When [translucency] > 0, the allowed variance threshold is proportionally scaled up to
     * [MAX_TRANSLUCENT_VARIANCE] across the entire crop without distance constraints, recovering
     * standalone semi-transparent elements, floating sparkles, glass backplates, and glowing icons.
     *
     * Morphological despeckling and separable Gaussian anti-aliasing are applied.
     */
    fun buildMask(
        varianceMap: ByteArray,
        width: Int,
        height: Int,
        translucency: Int = MIN_TRANSLUCENCY,
        colorChangeThreshold: Int = DEFAULT_SENSITIVITY,
        cavityHealing: Boolean = false,
    ): IntArray {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val effectiveThreshold = colorChangeThreshold.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        val pixelCount = width * height
        if (width <= 0 || height <= 0 || varianceMap.size != pixelCount) {
            return IntArray(0)
        }

        val candidateAlpha = IntArray(pixelCount)
        val maxAllowedVariance =
            if (clampedTranslucency > 0) {
                effectiveThreshold + (clampedTranslucency * (MAX_TRANSLUCENT_VARIANCE - effectiveThreshold)) / MAX_TRANSLUCENCY
            } else {
                effectiveThreshold
            }

        // 1. Recover solid and translucent UI foreground based on sensitivity and translucency tolerance
        for (i in 0 until pixelCount) {
            val v = varianceMap[i].toInt() and COLOR_BYTE_MASK
            if (v <= effectiveThreshold) {
                candidateAlpha[i] = FULL_ALPHA_BYTE
            } else if (clampedTranslucency > 0 && v <= maxAllowedVariance) {
                val falloff = 1.0f - (v - effectiveThreshold).toFloat() / (maxAllowedVariance - effectiveThreshold + 1).toFloat()
                val partialAlpha = (falloff * (FULL_ALPHA_BYTE - MIN_TRANSLUCENT_ALPHA)).toInt() + MIN_TRANSLUCENT_ALPHA
                candidateAlpha[i] = partialAlpha.coerceIn(0, FULL_ALPHA_BYTE)
            }
        }

        // 2. Optional Topological Cavity & Gauge Healing
        if (cavityHealing) {
            applyCavityHealing(candidateAlpha, width, height)
        }

        // 3. Trimap Sub-Pixel Alpha Matting (Always on)
        applyAlphaMatting(
            candidateAlpha = candidateAlpha,
            varianceMap = varianceMap,
            width = width,
            height = height,
            effectiveThreshold = effectiveThreshold,
        )

        // 4. Morphological Despeckle: Remove single isolated noise pixels
        val despeckled = IntArray(pixelCount)
        for (y in 0 until height) {
            val prevRow = if (y > 0) (y - 1) * width else y * width
            val currRow = y * width
            val nextRow = if (y < height - 1) (y + 1) * width else y * width

            for (x in 0 until width) {
                val idx = currRow + x
                if (candidateAlpha[idx] > 0) {
                    var neighbors = 0
                    if (x > 0 && candidateAlpha[currRow + x - 1] > 0) neighbors++
                    if (x < width - 1 && candidateAlpha[currRow + x + 1] > 0) neighbors++
                    if (y > 0 && candidateAlpha[prevRow + x] > 0) neighbors++
                    if (y < height - 1 && candidateAlpha[nextRow + x] > 0) neighbors++

                    if (neighbors >= MIN_DESPECKLE_NEIGHBORS) {
                        despeckled[idx] = candidateAlpha[idx]
                    }
                }
            }
        }

        // 5. Separable Gaussian Anti-Aliasing (Horizontal then Vertical pass)
        val tempH = IntArray(pixelCount)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val left = if (x > 0) despeckled[rowOffset + x - 1] else despeckled[rowOffset + x]
                val mid = despeckled[rowOffset + x]
                val right = if (x < width - 1) despeckled[rowOffset + x + 1] else despeckled[rowOffset + x]
                tempH[rowOffset + x] = (left + (mid shl 1) + right) shr 2
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

        return finalMask
    }

    private fun applyCavityHealing(
        candidateAlpha: IntArray,
        width: Int,
        height: Int,
    ) {
        val pixelCount = width * height
        val dilatedBarrier = BooleanArray(pixelCount)

        // Dilate solid core anchors by CLOSING_RADIUS to bridge open brackets and gauge gaps
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if (candidateAlpha[rowOffset + x] == FULL_ALPHA_BYTE) {
                    for (dy in -CLOSING_RADIUS..CLOSING_RADIUS) {
                        val ny = y + dy
                        if (ny !in 0 until height) continue
                        val nRowOffset = ny * width
                        for (dx in -CLOSING_RADIUS..CLOSING_RADIUS) {
                            val nx = x + dx
                            if (nx !in 0 until width) continue
                            if (dx * dx + dy * dy <= CLOSING_RADIUS * CLOSING_RADIUS) {
                                dilatedBarrier[nRowOffset + nx] = true
                            }
                        }
                    }
                }
            }
        }

        // Flood fill exterior from borders on the dilated barrier map
        val exterior = BooleanArray(pixelCount)
        val queue = IntArray(pixelCount)
        var head = 0
        var tail = 0

        for (x in 0 until width) {
            val topIdx = x
            if (!dilatedBarrier[topIdx] && !exterior[topIdx]) {
                exterior[topIdx] = true
                queue[tail++] = topIdx
            }
            val botIdx = (height - 1) * width + x
            if (!dilatedBarrier[botIdx] && !exterior[botIdx]) {
                exterior[botIdx] = true
                queue[tail++] = botIdx
            }
        }
        for (y in 0 until height) {
            val leftIdx = y * width
            if (!dilatedBarrier[leftIdx] && !exterior[leftIdx]) {
                exterior[leftIdx] = true
                queue[tail++] = leftIdx
            }
            val rightIdx = y * width + (width - 1)
            if (!dilatedBarrier[rightIdx] && !exterior[rightIdx]) {
                exterior[rightIdx] = true
                queue[tail++] = rightIdx
            }
        }

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % width
            val cy = curr / width

            val up = (cy - 1) * width + cx
            if (cy > 0 && !exterior[up] && !dilatedBarrier[up]) {
                exterior[up] = true
                queue[tail++] = up
            }
            val down = (cy + 1) * width + cx
            if (cy < height - 1 && !exterior[down] && !dilatedBarrier[down]) {
                exterior[down] = true
                queue[tail++] = down
            }
            val left = cy * width + (cx - 1)
            if (cx > 0 && !exterior[left] && !dilatedBarrier[left]) {
                exterior[left] = true
                queue[tail++] = left
            }
            val right = cy * width + (cx + 1)
            if (cx < width - 1 && !exterior[right] && !dilatedBarrier[right]) {
                exterior[right] = true
                queue[tail++] = right
            }
        }

        // Erode the enclosed map (!exterior) by CLOSING_RADIUS to recover enclosed cavities
        // without artificially expanding outer borders or isolated noise specks.
        val enclosed = BooleanArray(pixelCount) { !exterior[it] }
        val healedInterior = BooleanArray(pixelCount)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                var allContained = true
                for (dy in -CLOSING_RADIUS..CLOSING_RADIUS) {
                    val ny = y + dy
                    if (ny !in 0 until height) {
                        allContained = false
                        break
                    }
                    val nRowOffset = ny * width
                    for (dx in -CLOSING_RADIUS..CLOSING_RADIUS) {
                        val nx = x + dx
                        if (nx !in 0 until width) {
                            allContained = false
                            break
                        }
                        if (dx * dx + dy * dy <= CLOSING_RADIUS * CLOSING_RADIUS) {
                            if (!enclosed[nRowOffset + nx]) {
                                allContained = false
                                break
                            }
                        }
                    }
                    if (!allContained) break
                }
                if (allContained) {
                    healedInterior[rowOffset + x] = true
                }
            }
        }

        // Mark healed interior cavity pixels as solid
        for (i in 0 until pixelCount) {
            if (healedInterior[i]) {
                candidateAlpha[i] = FULL_ALPHA_BYTE
            }
        }
    }



    private fun applyAlphaMatting(
        candidateAlpha: IntArray,
        varianceMap: ByteArray,
        width: Int,
        height: Int,
        effectiveThreshold: Int,
    ) {
        val pixelCount = width * height
        val isAdjacentToCore = BooleanArray(pixelCount)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if (candidateAlpha[rowOffset + x] == FULL_ALPHA_BYTE) {
                    for (dy in -1..1) {
                        val ny = y + dy
                        if (ny !in 0 until height) continue
                        val nRowOffset = ny * width
                        for (dx in -1..1) {
                            val nx = x + dx
                            if (nx !in 0 until width) continue
                            isAdjacentToCore[nRowOffset + nx] = true
                        }
                    }
                }
            }
        }

        val transitionMax = effectiveThreshold + TRIMAP_TRANSITION_MAX
        for (i in 0 until pixelCount) {
            if (candidateAlpha[i] < FULL_ALPHA_BYTE && isAdjacentToCore[i]) {
                val v = varianceMap[i].toInt() and COLOR_BYTE_MASK
                if (v in (effectiveThreshold + 1)..transitionMax) {
                    val falloff = 1.0f - ((v - effectiveThreshold).toFloat() / TRIMAP_TRANSITION_MAX.toFloat())
                    val calculatedAlpha = (falloff * FULL_ALPHA_BYTE).toInt().coerceIn(0, FULL_ALPHA_BYTE)
                    candidateAlpha[i] = maxOf(candidateAlpha[i], calculatedAlpha)
                }
            }
        }
    }
}

/**
 * Stateful engine tracking per-pixel RGB min/max variances across incoming frame crops during calibration.
 * Produces an incremental ARGB preview image where dynamic pixels turn transparent over time
 * while stationary foreground pixels remain opaque with their base color.
 */
class CalibrationPreviewTracker(
    val width: Int,
    val height: Int,
    val colorChangeThreshold: Int = COLOR_CHANGE_THRESHOLD,
) {
    private val pixelCount = width * height
    private val minR = IntArray(pixelCount) { 255 }
    private val maxR = IntArray(pixelCount) { 0 }
    private val minG = IntArray(pixelCount) { 255 }
    private val maxG = IntArray(pixelCount) { 0 }
    private val minB = IntArray(pixelCount) { 255 }
    private val maxB = IntArray(pixelCount) { 0 }
    private var basePixels: IntArray? = null

    var frameCount: Int = 0
        private set

    var transparentPixelPercent: Int = 0
        private set

    /**
     * Ingests a new frame crop, updates channel variances, and writes the current preview into [outPixels].
     * Stationary pixels retain the RGB color from the first frame with 100% opacity.
     * Dynamic pixels whose variance exceeds [colorChangeThreshold] become 100% transparent (`0x00000000`).
     */
    fun ingestFrame(
        framePixels: IntArray,
        outPixels: IntArray,
    ) {
        if (framePixels.size != pixelCount || outPixels.size != pixelCount) return
        if (basePixels == null) {
            basePixels = framePixels.clone()
        }
        val base = basePixels ?: return
        frameCount++

        var transparentCount = 0
        for (i in 0 until pixelCount) {
            val rgb = framePixels[i]
            val r = (rgb shr SHIFT_RED) and COLOR_BYTE_MASK
            val g = (rgb shr SHIFT_GREEN) and COLOR_BYTE_MASK
            val b = rgb and COLOR_BYTE_MASK

            if (r < minR[i]) minR[i] = r
            if (r > maxR[i]) maxR[i] = r
            if (g < minG[i]) minG[i] = g
            if (g > maxG[i]) maxG[i] = g
            if (b < minB[i]) minB[i] = b
            if (b > maxB[i]) maxB[i] = b

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

            if (effectiveDiff > colorChangeThreshold) {
                outPixels[i] = MASK_PIXEL_TRANSPARENT
                transparentCount++
            } else {
                outPixels[i] = (FULL_ALPHA_BYTE shl ALPHA_SHIFT) or (base[i] and RGB_WHITE_MASK)
            }
        }
        transparentPixelPercent = if (pixelCount > 0) (transparentCount * PERCENT_MULTIPLIER) / pixelCount else 0
    }
}
