package com.stormpanda.megingiard.macropad

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val BUTTON_ALIGNMENT_SNAP_THRESHOLD_PX = 10f
const val BUTTON_ALIGNMENT_VISUAL_TOLERANCE_PX = 2f
const val MPE_NORMAL_STEP_PX = 10f
const val MPE_FINE_STEP_PX = 1f
const val MPE_CANVAS_WIDTH_PX = 1080f
const val MPE_CANVAS_HEIGHT_PX = 1240f
const val BUTTON_CANVAS_EDGE_MARGIN = 0.05f

const val PC_RADIAL_CENTER_X = 0.5f
const val PC_RADIAL_CENTER_Y = 0.5f
const val PC_RADIAL_MIN_POINTS = 4

/**
 * Result of computing position snapping for a button, including any PowerPoint-style
 * smart alignment guide line coordinates that are actively aligned.
 */
data class AlignmentSnapResult(
    val snappedNormX: Float,
    val snappedNormY: Float,
    val alignedXNorms: List<Float> = emptyList(),
    val alignedYNorms: List<Float> = emptyList(),
)

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
    snapThresholdPx: Float = BUTTON_ALIGNMENT_SNAP_THRESHOLD_PX,
    visualTolerancePx: Float = BUTTON_ALIGNMENT_VISUAL_TOLERANCE_PX,
): AlignmentSnapResult {
    val effectiveW = canvasW.coerceAtLeast(1f)
    val effectiveH = canvasH.coerceAtLeast(1f)

    var snappedX = rawNormX
    var snappedY = rawNormY
    var snappedToBtnX = false
    var snappedToBtnY = false

    val activeAlignedXs = mutableListOf<Float>()
    val activeAlignedYs = mutableListOf<Float>()

    val candidates = otherButtons.filter { it.id != movingButtonId }

    if (alignmentSnappingEnabled && candidates.isNotEmpty()) {
        val matchingX =
            candidates
                .map { it.posX }
                .filter { abs(rawNormX - it) * effectiveW <= snapThresholdPx }

        if (matchingX.isNotEmpty()) {
            val closestX = matchingX.minByOrNull { abs(rawNormX - it) }!!
            snappedX = closestX
            snappedToBtnX = true
            activeAlignedXs.add(closestX)
        }

        val matchingY =
            candidates
                .map { it.posY }
                .filter { abs(rawNormY - it) * effectiveH <= snapThresholdPx }

        if (matchingY.isNotEmpty()) {
            val closestY = matchingY.minByOrNull { abs(rawNormY - it) }!!
            snappedY = closestY
            snappedToBtnY = true
            activeAlignedYs.add(closestY)
        }
    }

    // Apply grid snap for any axes that did not snap to a sibling button
    if (gridMode != GridMode.OFF && gridStepPx > 0f) {
        val (gridX, gridY) = snapPosition(snappedX, snappedY, effectiveW, effectiveH, gridMode, gridStepPx)
        if (!snappedToBtnX) snappedX = gridX
        if (!snappedToBtnY) snappedY = gridY
    }

    // Populate visual alignment guides if not already added (e.g. within visual tolerance)
    if (candidates.isNotEmpty()) {
        candidates.forEach { btn ->
            if (abs(snappedX - btn.posX) * effectiveW <= visualTolerancePx && !activeAlignedXs.contains(btn.posX)) {
                activeAlignedXs.add(btn.posX)
            }
            if (abs(snappedY - btn.posY) * effectiveH <= visualTolerancePx && !activeAlignedYs.contains(btn.posY)) {
                activeAlignedYs.add(btn.posY)
            }
        }
    }

    return AlignmentSnapResult(
        snappedNormX = snappedX.coerceIn(BUTTON_CANVAS_EDGE_MARGIN, 1f - BUTTON_CANVAS_EDGE_MARGIN),
        snappedNormY = snappedY.coerceIn(BUTTON_CANVAS_EDGE_MARGIN, 1f - BUTTON_CANVAS_EDGE_MARGIN),
        alignedXNorms = activeAlignedXs.distinct(),
        alignedYNorms = activeAlignedYs.distinct(),
    )
}

/**
 * Calculates button movement for gamepad/D-pad navigation.
 *
 * Adapts step size based on [stepMultiplierPx] (e.g. 10px normal vs 1px fine-tuned).
 * When [alignmentSnappingEnabled] is true, checks if the movement step steps into or crosses
 * any sibling button's center axis. If so, snaps directly to that axis coordinate, and on
 * subsequent steps in the same direction steps off that coordinate.
 */
fun calculateGamepadButtonMove(
    currentNormX: Float,
    currentNormY: Float,
    dirX: Int,
    dirY: Int,
    stepMultiplierPx: Float,
    movingButtonId: String?,
    otherButtons: List<PadButton>,
    canvasW: Float = MPE_CANVAS_WIDTH_PX,
    canvasH: Float = MPE_CANVAS_HEIGHT_PX,
    alignmentSnappingEnabled: Boolean = true,
    snapThresholdPx: Float = BUTTON_ALIGNMENT_SNAP_THRESHOLD_PX,
): Pair<Float, Float> {
    val effectiveW = canvasW.coerceAtLeast(1f)
    val effectiveH = canvasH.coerceAtLeast(1f)
    val candidates = otherButtons.filter { it.id != movingButtonId }

    var nextX = currentNormX
    var nextY = currentNormY

    if (dirX != 0) {
        val stepX = dirX * (stepMultiplierPx / effectiveW)
        val rawTargetX = currentNormX + stepX

        if (alignmentSnappingEnabled && candidates.isNotEmpty()) {
            val snappedCandidateX =
                candidates.map { it.posX }.firstOrNull { targetX ->
                    val distPx = abs(rawTargetX - targetX) * effectiveW
                    val isCrossing =
                        (dirX > 0 && currentNormX < targetX && rawTargetX >= targetX) ||
                            (dirX < 0 && currentNormX > targetX && rawTargetX <= targetX)
                    distPx <= snapThresholdPx || isCrossing
                }

            nextX =
                if (snappedCandidateX != null && currentNormX != snappedCandidateX) {
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
        val rawTargetY = currentNormY + stepY

        if (alignmentSnappingEnabled && candidates.isNotEmpty()) {
            val snappedCandidateY =
                candidates.map { it.posY }.firstOrNull { targetY ->
                    val distPx = abs(rawTargetY - targetY) * effectiveH
                    val isCrossing =
                        (dirY > 0 && currentNormY < targetY && rawTargetY >= targetY) ||
                            (dirY < 0 && currentNormY > targetY && rawTargetY <= targetY)
                    distPx <= snapThresholdPx || isCrossing
                }

            nextY =
                if (snappedCandidateY != null && currentNormY != snappedCandidateY) {
                    snappedCandidateY
                } else {
                    rawTargetY
                }
        } else {
            nextY = rawTargetY
        }
    }

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
    tolerancePx: Float = BUTTON_ALIGNMENT_VISUAL_TOLERANCE_PX,
): Pair<List<Float>, List<Float>> {
    if (activeButtonId == null || buttons.isEmpty()) {
        return emptyList<Float>() to emptyList<Float>()
    }
    val activeBtn = buttons.firstOrNull { it.id == activeButtonId } ?: return emptyList<Float>() to emptyList<Float>()
    val others = buttons.filter { it.id != activeButtonId }
    val effectiveW = canvasW.coerceAtLeast(1f)
    val effectiveH = canvasH.coerceAtLeast(1f)

    val alignedXs =
        others
            .filter { abs(activeBtn.posX - it.posX) * effectiveW <= tolerancePx }
            .map { it.posX }
            .distinct()

    val alignedYs =
        others
            .filter { abs(activeBtn.posY - it.posY) * effectiveH <= tolerancePx }
            .map { it.posY }
            .distinct()

    return alignedXs to alignedYs
}

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
