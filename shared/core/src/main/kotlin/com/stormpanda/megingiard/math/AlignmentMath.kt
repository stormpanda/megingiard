package com.stormpanda.megingiard.math

import com.stormpanda.megingiard.macropad.GridMode
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.mirror.ScreenCutout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val ALIGNMENT_SNAP_THRESHOLD_PX = 10f
const val ALIGNMENT_VISUAL_TOLERANCE_PX = 2f
const val ALIGNMENT_NORMAL_STEP_PX = 10f
const val ALIGNMENT_FINE_STEP_PX = 1f
const val ALIGNMENT_CANVAS_WIDTH_PX = 1080f
const val ALIGNMENT_CANVAS_HEIGHT_PX = 1240f
const val ALIGNMENT_CANVAS_EDGE_MARGIN = 0.05f

// Backward-compatibility aliases for button-specific constants
const val BUTTON_ALIGNMENT_SNAP_THRESHOLD_PX = ALIGNMENT_SNAP_THRESHOLD_PX
const val BUTTON_ALIGNMENT_VISUAL_TOLERANCE_PX = ALIGNMENT_VISUAL_TOLERANCE_PX
const val MPE_NORMAL_STEP_PX = ALIGNMENT_NORMAL_STEP_PX
const val MPE_FINE_STEP_PX = ALIGNMENT_FINE_STEP_PX
const val MPE_CANVAS_WIDTH_PX = ALIGNMENT_CANVAS_WIDTH_PX
const val MPE_CANVAS_HEIGHT_PX = ALIGNMENT_CANVAS_HEIGHT_PX
const val BUTTON_CANVAS_EDGE_MARGIN = ALIGNMENT_CANVAS_EDGE_MARGIN

const val PC_RADIAL_CENTER_X = 0.5f
const val PC_RADIAL_CENTER_Y = 0.5f
const val PC_RADIAL_MIN_POINTS = 4

/**
 * Result of computing position snapping for a 2D element (button or screen cutout),
 * including any PowerPoint-style smart alignment guide line coordinates that are actively aligned.
 */
data class AlignmentSnapResult(
    val snappedNormX: Float,
    val snappedNormY: Float,
    val alignedXNorms: List<Float> = emptyList(),
    val alignedYNorms: List<Float> = emptyList(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Generic Center-to-Center Alignment Mathematics
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Calculates snapped normalized center position `(X, Y)` for a 2D element.
 *
 * If [alignmentSnappingEnabled] is true, checks sibling element center positions. If the raw
 * center position is within [snapThresholdPx] of any sibling's center X or Y, it magnetically snaps
 * to that sibling's center axis.
 *
 * Also discovers all visual alignment guide lines within [visualTolerancePx] for on-canvas rendering.
 */
fun calculateCenterAlignmentSnap(
    rawCenterX: Float,
    rawCenterY: Float,
    otherCenters: List<Pair<Float, Float>>,
    canvasW: Float,
    canvasH: Float,
    alignmentSnappingEnabled: Boolean,
    snapThresholdPx: Float = ALIGNMENT_SNAP_THRESHOLD_PX,
    visualTolerancePx: Float = ALIGNMENT_VISUAL_TOLERANCE_PX,
): AlignmentSnapResult {
    val effectiveW = canvasW.coerceAtLeast(1f)
    val effectiveH = canvasH.coerceAtLeast(1f)

    var snappedX = rawCenterX
    var snappedY = rawCenterY
    val activeAlignedXs = mutableListOf<Float>()
    val activeAlignedYs = mutableListOf<Float>()

    if (alignmentSnappingEnabled && otherCenters.isNotEmpty()) {
        val matchingX =
            otherCenters
                .map { it.first }
                .filter { abs(rawCenterX - it) * effectiveW <= snapThresholdPx }

        if (matchingX.isNotEmpty()) {
            val closestX = matchingX.minByOrNull { abs(rawCenterX - it) }!!
            snappedX = closestX
            activeAlignedXs.add(closestX)
        }

        val matchingY =
            otherCenters
                .map { it.second }
                .filter { abs(rawCenterY - it) * effectiveH <= snapThresholdPx }

        if (matchingY.isNotEmpty()) {
            val closestY = matchingY.minByOrNull { abs(rawCenterY - it) }!!
            snappedY = closestY
            activeAlignedYs.add(closestY)
        }
    }

    // Populate visual alignment guides within visual tolerance
    if (otherCenters.isNotEmpty()) {
        otherCenters.forEach { (otherX, otherY) ->
            if (abs(snappedX - otherX) * effectiveW <= visualTolerancePx && !activeAlignedXs.contains(otherX)) {
                activeAlignedXs.add(otherX)
            }
            if (abs(snappedY - otherY) * effectiveH <= visualTolerancePx && !activeAlignedYs.contains(otherY)) {
                activeAlignedYs.add(otherY)
            }
        }
    }

    return AlignmentSnapResult(
        snappedNormX = snappedX,
        snappedNormY = snappedY,
        alignedXNorms = activeAlignedXs.distinct(),
        alignedYNorms = activeAlignedYs.distinct(),
    )
}

/**
 * Calculates center movement for gamepad/D-pad navigation.
 *
 * Adapts step size based on [stepMultiplierPx] (e.g. 10px normal vs 1px fine-tuned).
 * When [alignmentSnappingEnabled] is true, checks if the movement step steps into or crosses
 * any sibling center axis. If so, snaps directly to that axis coordinate, and on
 * subsequent steps in the same direction steps off that coordinate.
 */
fun calculateGamepadCenterMove(
    currentCenterX: Float,
    currentCenterY: Float,
    dirX: Int,
    dirY: Int,
    stepMultiplierPx: Float,
    otherCenters: List<Pair<Float, Float>>,
    canvasW: Float = ALIGNMENT_CANVAS_WIDTH_PX,
    canvasH: Float = ALIGNMENT_CANVAS_HEIGHT_PX,
    alignmentSnappingEnabled: Boolean = true,
    snapThresholdPx: Float = ALIGNMENT_SNAP_THRESHOLD_PX,
): Pair<Float, Float> {
    val effectiveW = canvasW.coerceAtLeast(1f)
    val effectiveH = canvasH.coerceAtLeast(1f)

    var nextX = currentCenterX
    var nextY = currentCenterY

    if (dirX != 0) {
        val stepX = dirX * (stepMultiplierPx / effectiveW)
        val rawTargetX = currentCenterX + stepX

        if (alignmentSnappingEnabled && otherCenters.isNotEmpty()) {
            val snappedCandidateX =
                otherCenters.map { it.first }.firstOrNull { targetX ->
                    val distPx = abs(rawTargetX - targetX) * effectiveW
                    val isCrossing =
                        (dirX > 0 && currentCenterX < targetX && rawTargetX >= targetX) ||
                            (dirX < 0 && currentCenterX > targetX && rawTargetX <= targetX)
                    distPx <= snapThresholdPx || isCrossing
                }

            nextX =
                if (snappedCandidateX != null && currentCenterX != snappedCandidateX) {
                    snappedCandidateX
                } else {
                    rawTargetX
                }
        } else {
            nextX = rawTargetX
        }
    }

    if (dirY != 0) {
        val stepY = dirY * (stepMultiplierPx / effectiveH)
        val rawTargetY = currentCenterY + stepY

        if (alignmentSnappingEnabled && otherCenters.isNotEmpty()) {
            val snappedCandidateY =
                otherCenters.map { it.second }.firstOrNull { targetY ->
                    val distPx = abs(rawTargetY - targetY) * effectiveH
                    val isCrossing =
                        (dirY > 0 && currentCenterY < targetY && rawTargetY >= targetY) ||
                            (dirY < 0 && currentCenterY > targetY && rawTargetY <= targetY)
                    distPx <= snapThresholdPx || isCrossing
                }

            nextY =
                if (snappedCandidateY != null && currentCenterY != snappedCandidateY) {
                    snappedCandidateY
                } else {
                    rawTargetY
                }
        } else {
            nextY = rawTargetY
        }
    }

    return nextX to nextY
}

/**
 * Discovers all sibling center coordinates that align with [activeCenterX] or [activeCenterY]
 * within [tolerancePx] on canvas.
 */
fun findAlignedCenters(
    activeCenterX: Float,
    activeCenterY: Float,
    otherCenters: List<Pair<Float, Float>>,
    canvasW: Float,
    canvasH: Float,
    tolerancePx: Float = ALIGNMENT_VISUAL_TOLERANCE_PX,
): Pair<List<Float>, List<Float>> {
    if (otherCenters.isEmpty()) {
        return emptyList<Float>() to emptyList<Float>()
    }
    val effectiveW = canvasW.coerceAtLeast(1f)
    val effectiveH = canvasH.coerceAtLeast(1f)

    val alignedXs =
        otherCenters
            .filter { abs(activeCenterX - it.first) * effectiveW <= tolerancePx }
            .map { it.first }
            .distinct()

    val alignedYs =
        otherCenters
            .filter { abs(activeCenterY - it.second) * effectiveH <= tolerancePx }
            .map { it.second }
            .distinct()

    return alignedXs to alignedYs
}

// ─────────────────────────────────────────────────────────────────────────────
// Button-Specific Adapters
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Calculates snapped normalized position `(X, Y)` for a button during touch dragging or editing.
 *
 * If [alignmentSnappingEnabled] is true, checks sibling button center positions. If the raw
 * position is within [snapThresholdPx] of any sibling's center X or Y, it magnetically snaps
 * to that sibling's axis.
 *
 * For any axis not snapped to a sibling button, falls back to [gridMode] grid snapping if active.
 * Also discovers all visual alignment guides within tolerance for on-canvas rendering.
 */
fun calculateButtonAlignmentSnap(
    rawNormX: Float,
    rawNormY: Float,
    movingButtonId: String?,
    otherButtons: List<PadButton>,
    canvasW: Float,
    canvasH: Float,
    alignmentSnappingEnabled: Boolean,
    gridMode: GridMode = GridMode.OFF,
    gridStepPx: Float = 0f,
    snapThresholdPx: Float = ALIGNMENT_SNAP_THRESHOLD_PX,
    visualTolerancePx: Float = ALIGNMENT_VISUAL_TOLERANCE_PX,
): AlignmentSnapResult {
    val effectiveW = canvasW.coerceAtLeast(1f)
    val effectiveH = canvasH.coerceAtLeast(1f)
    val candidates = otherButtons.filter { it.id != movingButtonId }
    val candidateCenters = candidates.map { it.posX to it.posY }

    val centerResult =
        calculateCenterAlignmentSnap(
            rawCenterX = rawNormX,
            rawCenterY = rawNormY,
            otherCenters = candidateCenters,
            canvasW = effectiveW,
            canvasH = effectiveH,
            alignmentSnappingEnabled = alignmentSnappingEnabled,
            snapThresholdPx = snapThresholdPx,
            visualTolerancePx = visualTolerancePx,
        )

    var snappedX = centerResult.snappedNormX
    var snappedY = centerResult.snappedNormY
    val snappedToBtnX = centerResult.alignedXNorms.any { abs(snappedX - it) * effectiveW <= snapThresholdPx }
    val snappedToBtnY = centerResult.alignedYNorms.any { abs(snappedY - it) * effectiveH <= snapThresholdPx }

    // Apply grid snap for any axes that did not snap to a sibling button
    if (gridMode != GridMode.OFF && gridStepPx > 0f) {
        val (gridX, gridY) = snapPosition(snappedX, snappedY, effectiveW, effectiveH, gridMode, gridStepPx)
        if (!snappedToBtnX) snappedX = gridX
        if (!snappedToBtnY) snappedY = gridY
    }

    return AlignmentSnapResult(
        snappedNormX = snappedX.coerceIn(BUTTON_CANVAS_EDGE_MARGIN, 1f - BUTTON_CANVAS_EDGE_MARGIN),
        snappedNormY = snappedY.coerceIn(BUTTON_CANVAS_EDGE_MARGIN, 1f - BUTTON_CANVAS_EDGE_MARGIN),
        alignedXNorms = centerResult.alignedXNorms,
        alignedYNorms = centerResult.alignedYNorms,
    )
}

/**
 * Calculates button movement for gamepad/D-pad navigation.
 */
fun calculateGamepadButtonMove(
    currentNormX: Float,
    currentNormY: Float,
    dirX: Int,
    dirY: Int,
    stepMultiplierPx: Float,
    movingButtonId: String?,
    otherButtons: List<PadButton>,
    canvasW: Float = ALIGNMENT_CANVAS_WIDTH_PX,
    canvasH: Float = ALIGNMENT_CANVAS_HEIGHT_PX,
    alignmentSnappingEnabled: Boolean = true,
    snapThresholdPx: Float = ALIGNMENT_SNAP_THRESHOLD_PX,
): Pair<Float, Float> {
    val candidates = otherButtons.filter { it.id != movingButtonId }
    val candidateCenters = candidates.map { it.posX to it.posY }

    val (nextX, nextY) =
        calculateGamepadCenterMove(
            currentCenterX = currentNormX,
            currentCenterY = currentNormY,
            dirX = dirX,
            dirY = dirY,
            stepMultiplierPx = stepMultiplierPx,
            otherCenters = candidateCenters,
            canvasW = canvasW,
            canvasH = canvasH,
            alignmentSnappingEnabled = alignmentSnappingEnabled,
            snapThresholdPx = snapThresholdPx,
        )

    return (
        nextX.coerceIn(BUTTON_CANVAS_EDGE_MARGIN, 1f - BUTTON_CANVAS_EDGE_MARGIN) to
            nextY.coerceIn(BUTTON_CANVAS_EDGE_MARGIN, 1f - BUTTON_CANVAS_EDGE_MARGIN)
    )
}

/**
 * Discovers all sibling button center coordinates that align with [activeButtonId]
 * within [tolerancePx] on canvas.
 */
fun findAlignedCenterGuides(
    activeButtonId: String?,
    buttons: List<PadButton>,
    canvasW: Float,
    canvasH: Float,
    tolerancePx: Float = ALIGNMENT_VISUAL_TOLERANCE_PX,
): Pair<List<Float>, List<Float>> {
    if (activeButtonId == null || buttons.isEmpty()) {
        return emptyList<Float>() to emptyList<Float>()
    }
    val activeBtn = buttons.firstOrNull { it.id == activeButtonId } ?: return emptyList<Float>() to emptyList<Float>()
    val others = buttons.filter { it.id != activeButtonId }.map { it.posX to it.posY }
    return findAlignedCenters(activeBtn.posX, activeBtn.posY, others, canvasW, canvasH, tolerancePx)
}

// ─────────────────────────────────────────────────────────────────────────────
// Cutout-Specific Adapters
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Calculates snapped normalized destination bounds `(destX, destY)` for a screen cutout
 * during touch dragging or editing on the secondary display.
 *
 * Snaps based on the cutout's center `(destX + destWidth / 2f, destY + destHeight / 2f)`
 * aligning with the center coordinates of any sibling cutouts.
 */
fun calculateCutoutAlignmentSnap(
    rawDestX: Float,
    rawDestY: Float,
    destWidth: Float,
    destHeight: Float,
    movingCutoutId: String?,
    otherCutouts: List<ScreenCutout>,
    canvasW: Float,
    canvasH: Float,
    alignmentSnappingEnabled: Boolean,
    snapThresholdPx: Float = ALIGNMENT_SNAP_THRESHOLD_PX,
    visualTolerancePx: Float = ALIGNMENT_VISUAL_TOLERANCE_PX,
): AlignmentSnapResult {
    val rawCenterX = rawDestX + destWidth / 2f
    val rawCenterY = rawDestY + destHeight / 2f
    val candidateCenters =
        otherCutouts
            .filter { it.id != movingCutoutId }
            .map { (it.destX + it.destWidth / 2f) to (it.destY + it.destHeight / 2f) }

    val centerResult =
        calculateCenterAlignmentSnap(
            rawCenterX = rawCenterX,
            rawCenterY = rawCenterY,
            otherCenters = candidateCenters,
            canvasW = canvasW,
            canvasH = canvasH,
            alignmentSnappingEnabled = alignmentSnappingEnabled,
            snapThresholdPx = snapThresholdPx,
            visualTolerancePx = visualTolerancePx,
        )

    val snappedDestX = centerResult.snappedNormX - destWidth / 2f
    val snappedDestY = centerResult.snappedNormY - destHeight / 2f

    return AlignmentSnapResult(
        snappedNormX = snappedDestX.coerceIn(0f, (1f - destWidth).coerceAtLeast(0f)),
        snappedNormY = snappedDestY.coerceIn(0f, (1f - destHeight).coerceAtLeast(0f)),
        alignedXNorms = centerResult.alignedXNorms,
        alignedYNorms = centerResult.alignedYNorms,
    )
}

/**
 * Calculates cutout destination bounds movement for gamepad/D-pad navigation.
 */
fun calculateGamepadCutoutMove(
    currentDestX: Float,
    currentDestY: Float,
    destWidth: Float,
    destHeight: Float,
    dirX: Int,
    dirY: Int,
    stepMultiplierPx: Float,
    movingCutoutId: String?,
    otherCutouts: List<ScreenCutout>,
    canvasW: Float = ALIGNMENT_CANVAS_WIDTH_PX,
    canvasH: Float = ALIGNMENT_CANVAS_HEIGHT_PX,
    alignmentSnappingEnabled: Boolean = true,
    snapThresholdPx: Float = ALIGNMENT_SNAP_THRESHOLD_PX,
): Pair<Float, Float> {
    val currentCenterX = currentDestX + destWidth / 2f
    val currentCenterY = currentDestY + destHeight / 2f
    val candidateCenters =
        otherCutouts
            .filter { it.id != movingCutoutId }
            .map { (it.destX + it.destWidth / 2f) to (it.destY + it.destHeight / 2f) }

    val (nextCenterX, nextCenterY) =
        calculateGamepadCenterMove(
            currentCenterX = currentCenterX,
            currentCenterY = currentCenterY,
            dirX = dirX,
            dirY = dirY,
            stepMultiplierPx = stepMultiplierPx,
            otherCenters = candidateCenters,
            canvasW = canvasW,
            canvasH = canvasH,
            alignmentSnappingEnabled = alignmentSnappingEnabled,
            snapThresholdPx = snapThresholdPx,
        )

    val nextDestX = nextCenterX - destWidth / 2f
    val nextDestY = nextCenterY - destHeight / 2f

    return (
        nextDestX.coerceIn(0f, (1f - destWidth).coerceAtLeast(0f)) to
            nextDestY.coerceIn(0f, (1f - destHeight).coerceAtLeast(0f))
    )
}

/**
 * Discovers all sibling cutout center coordinates that align with [activeCutoutId]
 * within [tolerancePx] on canvas.
 */
fun findAlignedCutoutCenterGuides(
    activeCutoutId: String?,
    cutouts: List<ScreenCutout>,
    canvasW: Float,
    canvasH: Float,
    tolerancePx: Float = ALIGNMENT_VISUAL_TOLERANCE_PX,
): Pair<List<Float>, List<Float>> {
    if (activeCutoutId == null || cutouts.isEmpty()) {
        return emptyList<Float>() to emptyList<Float>()
    }
    val active = cutouts.firstOrNull { it.id == activeCutoutId } ?: return emptyList<Float>() to emptyList<Float>()
    val activeCenterX = active.destX + active.destWidth / 2f
    val activeCenterY = active.destY + active.destHeight / 2f
    val otherCenters =
        cutouts
            .filter { it.id != activeCutoutId }
            .map { (it.destX + it.destWidth / 2f) to (it.destY + it.destHeight / 2f) }

    return findAlignedCenters(activeCenterX, activeCenterY, otherCenters, canvasW, canvasH, tolerancePx)
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid Mathematics
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Master snap function for MacroPad grid modes.
 */
fun snapPosition(
    rawNormX: Float,
    rawNormY: Float,
    canvasW: Float,
    canvasH: Float,
    gridMode: GridMode,
    gridStepPx: Float,
): Pair<Float, Float> =
    when (gridMode) {
        GridMode.OFF -> rawNormX to rawNormY
        GridMode.RECTANGULAR -> snapRectangular(rawNormX, rawNormY, canvasW, canvasH, gridStepPx)
        GridMode.RADIAL -> snapRadial(rawNormX, rawNormY, canvasW, canvasH, gridStepPx)
    }

/**
 * Round to nearest grid intersection. The grid is centred on the canvas midpoint
 * (same origin as the radial circles) so the centre is always a cross-point.
 */
fun snapRectangular(
    rawNormX: Float,
    rawNormY: Float,
    canvasW: Float,
    canvasH: Float,
    gridStepPx: Float,
): Pair<Float, Float> {
    if (gridStepPx <= 0f) return rawNormX to rawNormY
    val rawPxX = rawNormX * canvasW
    val rawPxY = rawNormY * canvasH
    val cx = canvasW * PC_RADIAL_CENTER_X
    val cy = canvasH * PC_RADIAL_CENTER_Y
    val snappedPxX = cx + ((rawPxX - cx) / gridStepPx).roundToInt() * gridStepPx
    val snappedPxY = cy + ((rawPxY - cy) / gridStepPx).roundToInt() * gridStepPx
    return (snappedPxX / canvasW) to (snappedPxY / canvasH)
}

/**
 * Snap to the nearest evenly-distributed point on a concentric circle, or to the
 * center point. Circles alternate phase:
 *   odd  (1, 3, 5 …) → 45° offset → diagonal anchors
 *   even (2, 4, 6 …) → 0° offset  → cardinal anchors
 */
fun snapRadial(
    rawNormX: Float,
    rawNormY: Float,
    canvasW: Float,
    canvasH: Float,
    gridStepPx: Float,
): Pair<Float, Float> {
    if (gridStepPx <= 0f) return rawNormX to rawNormY
    val rawPxX = rawNormX * canvasW
    val rawPxY = rawNormY * canvasH
    val cx = canvasW * PC_RADIAL_CENTER_X
    val cy = canvasH * PC_RADIAL_CENTER_Y

    val dx = rawPxX - cx
    val dy = rawPxY - cy
    val rawRadius = sqrt(dx * dx + dy * dy)

    val buttonUnitPx = gridStepPx * 2f
    val snappedRadius = (round(rawRadius / gridStepPx) * gridStepPx)

    if (snappedRadius < gridStepPx * 0.5f) {
        return (cx / canvasW) to (cy / canvasH)
    }

    val circleIndex = round(snappedRadius / gridStepPx).toInt()
    val phaseOffset = if (circleIndex % 2 == 1) PI / 4.0 else 0.0

    val n = radialPointCount(snappedRadius, buttonUnitPx)
    val angleStep = 2.0 * PI / n

    val rawAngle = atan2(dy.toDouble(), dx.toDouble())
    val relAngle = rawAngle - phaseOffset
    val relAnglePos = if (relAngle < 0) relAngle + 2 * PI else relAngle
    val nearestIndex = round(relAnglePos / angleStep).toInt() % n
    val snappedAngle = phaseOffset + nearestIndex * angleStep

    val snappedPxX = cx + snappedRadius * cos(snappedAngle).toFloat()
    val snappedPxY = cy + snappedRadius * sin(snappedAngle).toFloat()

    val distToCircle = dist(rawPxX, rawPxY, snappedPxX, snappedPxY)
    val distToCenter = dist(rawPxX, rawPxY, cx, cy)
    return if (distToCenter < distToCircle) {
        (cx / canvasW) to (cy / canvasH)
    } else {
        (snappedPxX / canvasW) to (snappedPxY / canvasH)
    }
}

/** Euclidean distance between two points. */
private fun dist(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}

/**
 * How many evenly-distributed snap points to place on a circle of the given radius.
 */
fun radialPointCount(
    radiusPx: Float,
    buttonUnitPx: Float,
): Int {
    val circumference = (2.0 * PI * radiusPx).toFloat()
    val raw = round(circumference / buttonUnitPx).toInt().coerceAtLeast(1)
    val rounded4 = ((raw + 2) / 4) * 4
    return maxOf(PC_RADIAL_MIN_POINTS, rounded4)
}
