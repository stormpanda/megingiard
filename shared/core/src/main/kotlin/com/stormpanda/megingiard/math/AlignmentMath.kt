package com.stormpanda.megingiard.math

import com.stormpanda.megingiard.macropad.GridMode
import com.stormpanda.megingiard.macropad.MP_THOR_MIN_BUTTON_SIZE_PX
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.mirror.ScreenCutout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val ALIGNMENT_SNAP_THRESHOLD_PX = 10f
const val ALIGNMENT_VISUAL_TOLERANCE_PX = 2f
const val ALIGNMENT_NORMAL_STEP_PX = 10f
const val ALIGNMENT_FINE_STEP_PX = 1f

private const val AM_MIN_CANVAS_PX = 1f
private const val AM_MIN_BUTTON_PX = 1f
private const val AM_HALF_DIVISOR = 2f

/**
 * Headless / unit-test fallback reference dimensions for the secondary MacroPad canvas.
 * Runtime UI callers pass `PadLayout.effectiveDesignCanvas` bounds; these constants apply
 * only before layout measurement or in JVM tests.
 */
const val ALIGNMENT_CANVAS_WIDTH_PX = 1240f
const val ALIGNMENT_CANVAS_HEIGHT_PX = 1080f
const val ALIGNMENT_CANVAS_EDGE_MARGIN = 0f

const val BUTTON_ALIGNMENT_SNAP_THRESHOLD_PX = ALIGNMENT_SNAP_THRESHOLD_PX
const val BUTTON_ALIGNMENT_VISUAL_TOLERANCE_PX = ALIGNMENT_VISUAL_TOLERANCE_PX
const val MPE_NORMAL_STEP_PX = ALIGNMENT_NORMAL_STEP_PX
const val MPE_FINE_STEP_PX = ALIGNMENT_FINE_STEP_PX
const val MPE_RESIZE_NORMAL_STEP_PX = 10f
const val MPE_RESIZE_FINE_STEP_PX = 1f
const val MPE_CANVAS_WIDTH_PX = 1240f
const val MPE_CANVAS_HEIGHT_PX = 1080f
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
    minNormX: Float = 0f,
    maxNormX: Float = 1f,
    minNormY: Float = 0f,
    maxNormY: Float = 1f,
): AlignmentSnapResult {
    val effectiveW = canvasW.coerceAtLeast(AM_MIN_CANVAS_PX)
    val effectiveH = canvasH.coerceAtLeast(AM_MIN_CANVAS_PX)
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

    val safeMinX = min(minNormX, maxNormX) + BUTTON_CANVAS_EDGE_MARGIN
    val safeMaxX = (max(minNormX, maxNormX) - BUTTON_CANVAS_EDGE_MARGIN).coerceAtLeast(safeMinX)
    val safeMinY = min(minNormY, maxNormY) + BUTTON_CANVAS_EDGE_MARGIN
    val safeMaxY = (max(minNormY, maxNormY) - BUTTON_CANVAS_EDGE_MARGIN).coerceAtLeast(safeMinY)

    val movingButton = if (movingButtonId != null) otherButtons.firstOrNull { it.id == movingButtonId } else null
    val effMinX = if (movingButton != null) min(movingButton.posX, safeMinX) else safeMinX
    val effMaxX = if (movingButton != null) max(movingButton.posX, safeMaxX) else safeMaxX
    val effMinY = if (movingButton != null) min(movingButton.posY, safeMinY) else safeMinY
    val effMaxY = if (movingButton != null) max(movingButton.posY, safeMaxY) else safeMaxY

    return AlignmentSnapResult(
        snappedNormX = snappedX.coerceIn(effMinX, effMaxX),
        snappedNormY = snappedY.coerceIn(effMinY, effMaxY),
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
    minNormX: Float = 0f,
    maxNormX: Float = 1f,
    minNormY: Float = 0f,
    maxNormY: Float = 1f,
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

    val safeMinX = min(minNormX, maxNormX) + BUTTON_CANVAS_EDGE_MARGIN
    val safeMaxX = (max(minNormX, maxNormX) - BUTTON_CANVAS_EDGE_MARGIN).coerceAtLeast(safeMinX)
    val safeMinY = min(minNormY, maxNormY) + BUTTON_CANVAS_EDGE_MARGIN
    val safeMaxY = (max(minNormY, maxNormY) - BUTTON_CANVAS_EDGE_MARGIN).coerceAtLeast(safeMinY)

    val effMinX = min(currentNormX, safeMinX)
    val effMaxX = max(currentNormX, safeMaxX)
    val effMinY = min(currentNormY, safeMinY)
    val effMaxY = max(currentNormY, safeMaxY)

    return nextX.coerceIn(effMinX, effMaxX) to nextY.coerceIn(effMinY, effMaxY)
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
    minNormX: Float = 0f,
    maxNormX: Float = 1f,
    minNormY: Float = 0f,
    maxNormY: Float = 1f,
): AlignmentSnapResult {
    val rawCenterX = rawDestX + destWidth / AM_HALF_DIVISOR
    val rawCenterY = rawDestY + destHeight / AM_HALF_DIVISOR
    val candidateCenters =
        otherCutouts
            .filter { it.id != movingCutoutId }
            .map { (it.destX + it.destWidth / AM_HALF_DIVISOR) to (it.destY + it.destHeight / AM_HALF_DIVISOR) }

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

    val snappedDestX = centerResult.snappedNormX - destWidth / AM_HALF_DIVISOR
    val snappedDestY = centerResult.snappedNormY - destHeight / AM_HALF_DIVISOR

    val safeMinX = min(minNormX, maxNormX)
    val safeMaxX = max(minNormX, maxNormX)
    val safeMinY = min(minNormY, maxNormY)
    val safeMaxY = max(minNormY, maxNormY)
    val boundMaxX = (safeMaxX - destWidth).coerceAtLeast(safeMinX)
    val boundMaxY = (safeMaxY - destHeight).coerceAtLeast(safeMinY)

    val movingCutout = if (movingCutoutId != null) otherCutouts.firstOrNull { it.id == movingCutoutId } else null
    val effMinX = if (movingCutout != null) min(movingCutout.destX, safeMinX) else safeMinX
    val effMaxX = if (movingCutout != null) max(movingCutout.destX, boundMaxX) else boundMaxX
    val effMinY = if (movingCutout != null) min(movingCutout.destY, safeMinY) else safeMinY
    val effMaxY = if (movingCutout != null) max(movingCutout.destY, boundMaxY) else boundMaxY

    return AlignmentSnapResult(
        snappedNormX = snappedDestX.coerceIn(effMinX, effMaxX),
        snappedNormY = snappedDestY.coerceIn(effMinY, effMaxY),
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
    minNormX: Float = 0f,
    maxNormX: Float = 1f,
    minNormY: Float = 0f,
    maxNormY: Float = 1f,
): Pair<Float, Float> {
    val currentCenterX = currentDestX + destWidth / AM_HALF_DIVISOR
    val currentCenterY = currentDestY + destHeight / AM_HALF_DIVISOR
    val candidateCenters =
        otherCutouts
            .filter { it.id != movingCutoutId }
            .map { (it.destX + it.destWidth / AM_HALF_DIVISOR) to (it.destY + it.destHeight / AM_HALF_DIVISOR) }

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

    val nextDestX = nextCenterX - destWidth / AM_HALF_DIVISOR
    val nextDestY = nextCenterY - destHeight / AM_HALF_DIVISOR

    val safeMinX = min(minNormX, maxNormX)
    val safeMaxX = max(minNormX, maxNormX)
    val safeMinY = min(minNormY, maxNormY)
    val safeMaxY = max(minNormY, maxNormY)
    val boundMaxX = (safeMaxX - destWidth).coerceAtLeast(safeMinX)
    val boundMaxY = (safeMaxY - destHeight).coerceAtLeast(safeMinY)

    val effMinX = min(currentDestX, safeMinX)
    val effMaxX = max(currentDestX, boundMaxX)
    val effMinY = min(currentDestY, safeMinY)
    val effMaxY = max(currentDestY, boundMaxY)

    return nextDestX.coerceIn(effMinX, effMaxX) to nextDestY.coerceIn(effMinY, effMaxY)
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

/**
 * Handle positions for stepless button edge resizing.
 */
enum class ButtonResizeHandle {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
}

const val BUTTON_ASPECT_SNAP_THRESHOLD_PX = 9f

data class ButtonResizeResult(
    val newNormX: Float,
    val newNormY: Float,
    val newWidthPx: Float,
    val newHeightPx: Float,
    val isAspectSnapped: Boolean = false,
)

/**
 * Calculates updated button bounds in `designCanvas` pixel space when dragging an edge resize handle.
 * Anchors the opposite edge (e.g. dragging the right edge keeps the left edge fixed).
 * Clamps width and height between [minWidthPx] / [minHeightPx] and host screen pixel bounds
 * `[minNormX * designCanvasW .. maxNormX * designCanvasW]` × `[minNormY * designCanvasH .. maxNormY * designCanvasH]`,
 * while preserving partially-offscreen edges/centers without jumping when a button placed in wider-screen
 * bars is viewed on a narrower screen.
 * Magnetically snaps dimension within [aspectSnapThresholdPx] for 1:1 aspect ratio when [lockAspect] is false.
 * When [lockAspect] is true (e.g. for circular trackpoints), both dimensions are strictly locked equal.
 */
fun calculateButtonEdgeResize(
    handle: ButtonResizeHandle,
    originalNormX: Float,
    originalNormY: Float,
    originalWidthPx: Float,
    originalHeightPx: Float,
    dragDeltaDesignPx: Float,
    designCanvasW: Float,
    designCanvasH: Float,
    minWidthPx: Float = MP_THOR_MIN_BUTTON_SIZE_PX,
    minHeightPx: Float = minWidthPx,
    aspectSnapThresholdPx: Float = BUTTON_ASPECT_SNAP_THRESHOLD_PX,
    lockAspect: Boolean = false,
    minNormX: Float = 0f,
    maxNormX: Float = 1f,
    minNormY: Float = 0f,
    maxNormY: Float = 1f,
): ButtonResizeResult {
    val safeCanvasW = designCanvasW.coerceAtLeast(AM_MIN_CANVAS_PX)
    val safeCanvasH = designCanvasH.coerceAtLeast(AM_MIN_CANVAS_PX)
    val safeMinWidthPx = minWidthPx.coerceAtLeast(AM_MIN_BUTTON_PX)
    val safeMinHeightPx = minHeightPx.coerceAtLeast(AM_MIN_BUTTON_PX)

    val safeMinNormX = min(minNormX, maxNormX)
    val safeMaxNormX = max(minNormX, maxNormX)
    val safeMinNormY = min(minNormY, maxNormY)
    val safeMaxNormY = max(minNormY, maxNormY)

    val hostMinXPx = safeMinNormX * safeCanvasW
    val hostMaxXPx = safeMaxNormX * safeCanvasW
    val hostMinYPx = safeMinNormY * safeCanvasH
    val hostMaxYPx = safeMaxNormY * safeCanvasH
    val hostSpanWPx = (hostMaxXPx - hostMinXPx).coerceAtLeast(AM_MIN_CANVAS_PX)
    val hostSpanHPx = (hostMaxYPx - hostMinYPx).coerceAtLeast(AM_MIN_CANVAS_PX)

    val isXAxis = handle == ButtonResizeHandle.LEFT || handle == ButtonResizeHandle.RIGHT
    val isPositiveEdge = handle == ButtonResizeHandle.RIGHT || handle == ButtonResizeHandle.BOTTOM

    val canvasSize = if (isXAxis) safeCanvasW else safeCanvasH
    val hostMinPx = if (isXAxis) hostMinXPx else hostMinYPx
    val hostMaxPx = if (isXAxis) hostMaxXPx else hostMaxYPx
    val hostSpanPx = if (isXAxis) hostSpanWPx else hostSpanHPx
    val otherHostSpanPx = if (isXAxis) hostSpanHPx else hostSpanWPx
    val minActivePx = if (isXAxis) safeMinWidthPx else safeMinHeightPx
    val minOtherPx = if (isXAxis) safeMinHeightPx else safeMinWidthPx
    val effectiveMinPx = if (lockAspect) max(minActivePx, minOtherPx) else minActivePx

    val normPos = if (isXAxis) originalNormX else originalNormY
    val otherNormPos = if (isXAxis) originalNormY else originalNormX
    val currentDimPx = if (isXAxis) originalWidthPx else originalHeightPx
    val otherDimPx = if (isXAxis) originalHeightPx else originalWidthPx
    val activeMinNorm = if (isXAxis) safeMinNormX else safeMinNormY
    val activeMaxNorm = if (isXAxis) safeMaxNormX else safeMaxNormY

    val anchorPx =
        if (isPositiveEdge) {
            (normPos * canvasSize) - currentDimPx / AM_HALF_DIVISOR
        } else {
            (normPos * canvasSize) + currentDimPx / AM_HALF_DIVISOR
        }

    val rawEdgeLimitPx =
        if (isPositiveEdge) {
            hostMaxPx - anchorPx
        } else {
            anchorPx - hostMinPx
        }
    val edgeLimitPx = rawEdgeLimitPx.coerceIn(effectiveMinPx, max(effectiveMinPx, hostSpanPx))
    val maxAllowedDimPx = maxOf(currentDimPx, edgeLimitPx, effectiveMinPx)

    val rawCandEdgePx =
        if (isPositiveEdge) {
            anchorPx + currentDimPx + dragDeltaDesignPx
        } else {
            anchorPx - currentDimPx + dragDeltaDesignPx
        }

    val rawDimPx =
        if (isPositiveEdge) {
            (rawCandEdgePx - anchorPx).coerceIn(effectiveMinPx, maxAllowedDimPx)
        } else {
            (anchorPx - rawCandEdgePx).coerceIn(effectiveMinPx, maxAllowedDimPx)
        }

    var candDimPx = rawDimPx.roundToInt().toFloat().coerceIn(effectiveMinPx, maxAllowedDimPx)
    if (candDimPx > maxAllowedDimPx) {
        candDimPx = floor(maxAllowedDimPx).coerceAtLeast(effectiveMinPx)
    }
    var isSnapped = false

    if (lockAspect) {
        val maxAllowedOtherPx = maxOf(otherDimPx, otherHostSpanPx, effectiveMinPx)
        if (candDimPx > maxAllowedOtherPx) {
            // floor (never round up) so the locked dimension can never exceed the other canvas axis
            candDimPx = floor(maxAllowedOtherPx).coerceAtLeast(effectiveMinPx)
        }
    } else if (abs(candDimPx - otherDimPx) <= aspectSnapThresholdPx) {
        if (otherDimPx in effectiveMinPx..maxAllowedDimPx) {
            candDimPx = otherDimPx
            isSnapped = true
        }
    }

    candDimPx = candDimPx.coerceIn(effectiveMinPx, maxAllowedDimPx).roundToInt().toFloat()
    if (candDimPx > maxAllowedDimPx) {
        candDimPx = floor(maxAllowedDimPx).coerceAtLeast(effectiveMinPx)
    }

    // Derive the center from the final rounded dimension so the anchored edge stays exactly fixed.
    val newCenterPx = if (isPositiveEdge) anchorPx + candDimPx / AM_HALF_DIVISOR else anchorPx - candDimPx / AM_HALF_DIVISOR
    val effMinNorm = min(normPos, activeMinNorm)
    val effMaxNorm = max(normPos, activeMaxNorm)
    val newNorm = (newCenterPx / canvasSize).coerceIn(effMinNorm, effMaxNorm)

    val newOtherDimPx = if (lockAspect) candDimPx else otherDimPx
    val newOtherNorm = otherNormPos

    return if (isXAxis) {
        ButtonResizeResult(
            newNormX = newNorm,
            newNormY = newOtherNorm,
            newWidthPx = candDimPx,
            newHeightPx = newOtherDimPx,
            isAspectSnapped = isSnapped,
        )
    } else {
        ButtonResizeResult(
            newNormX = newOtherNorm,
            newNormY = newNorm,
            newWidthPx = newOtherDimPx,
            newHeightPx = candDimPx,
            isAspectSnapped = isSnapped,
        )
    }
}

data class GamepadButtonResizeResult(
    val newNormX: Float,
    val newNormY: Float,
    val newWidthPx: Float,
    val newHeightPx: Float,
)

/**
 * Calculates button dimension updates in `designCanvas` pixels when resizing via gamepad (R2 + D-pad).
 * Resizes symmetrically around the button's center in clean [stepPx] (default 1 px) increments.
 * Clamps within host pixel boundaries while preserving existing partially-offscreen centers without jumping.
 * [dirX] > 0 expands width, [dirX] < 0 shrinks width.
 * [dirY] < 0 (D-pad UP) expands height, [dirY] > 0 (D-pad DOWN) shrinks height.
 */
fun calculateGamepadButtonResize(
    currentNormX: Float,
    currentNormY: Float,
    currentWidthPx: Float,
    currentHeightPx: Float,
    dirX: Int,
    dirY: Int,
    stepPx: Float = MPE_RESIZE_FINE_STEP_PX,
    designCanvasW: Float = MPE_CANVAS_WIDTH_PX,
    designCanvasH: Float = MPE_CANVAS_HEIGHT_PX,
    minWidthPx: Float = MP_THOR_MIN_BUTTON_SIZE_PX,
    minHeightPx: Float = minWidthPx,
    lockAspect: Boolean = false,
    minNormX: Float = 0f,
    maxNormX: Float = 1f,
    minNormY: Float = 0f,
    maxNormY: Float = 1f,
): GamepadButtonResizeResult {
    val safeCanvasW = designCanvasW.coerceAtLeast(AM_MIN_CANVAS_PX)
    val safeCanvasH = designCanvasH.coerceAtLeast(AM_MIN_CANVAS_PX)
    val safeMinW = if (lockAspect) maxOf(AM_MIN_BUTTON_PX, minWidthPx, minHeightPx) else minWidthPx.coerceAtLeast(AM_MIN_BUTTON_PX)
    val safeMinH = if (lockAspect) maxOf(AM_MIN_BUTTON_PX, minWidthPx, minHeightPx) else minHeightPx.coerceAtLeast(AM_MIN_BUTTON_PX)

    val safeMinNormX = min(minNormX, maxNormX)
    val safeMaxNormX = max(minNormX, maxNormX)
    val safeMinNormY = min(minNormY, maxNormY)
    val safeMaxNormY = max(minNormY, maxNormY)
    val hostSpanWPx = ((safeMaxNormX - safeMinNormX) * safeCanvasW).coerceAtLeast(AM_MIN_CANVAS_PX)
    val hostSpanHPx = ((safeMaxNormY - safeMinNormY) * safeCanvasH).coerceAtLeast(AM_MIN_CANVAS_PX)

    val widthDeltaPx = dirX * stepPx
    val heightDeltaPx = -dirY * stepPx

    val targetW: Float
    val targetH: Float
    if (lockAspect) {
        val aspectDelta = if (dirX != 0) widthDeltaPx else heightDeltaPx
        val baseSize = max(currentWidthPx, currentHeightPx)
        val aspectTarget = (baseSize + aspectDelta).roundToInt().toFloat()
        targetW = aspectTarget
        targetH = aspectTarget
    } else {
        targetW = (currentWidthPx + widthDeltaPx).roundToInt().toFloat()
        targetH = (currentHeightPx + heightDeltaPx).roundToInt().toFloat()
    }

    val rawMaxWPx = if (lockAspect) floor(min(hostSpanWPx, hostSpanHPx)) else floor(hostSpanWPx)
    val rawMaxHPx = if (lockAspect) floor(min(hostSpanWPx, hostSpanHPx)) else floor(hostSpanHPx)
    val maxWPx = maxOf(safeMinW, currentWidthPx, rawMaxWPx)
    val maxHPx = maxOf(safeMinH, currentHeightPx, rawMaxHPx)

    val clampedWPx = targetW.coerceIn(safeMinW, maxWPx)
    val clampedHPx = targetH.coerceIn(safeMinH, maxHPx)

    val effMinNormX = min(currentNormX, safeMinNormX)
    val effMaxNormX = max(currentNormX, safeMaxNormX)
    val effMinNormY = min(currentNormY, safeMinNormY)
    val effMaxNormY = max(currentNormY, safeMaxNormY)
    val newNormX = currentNormX.coerceIn(effMinNormX, effMaxNormX)
    val newNormY = currentNormY.coerceIn(effMinNormY, effMaxNormY)

    return GamepadButtonResizeResult(
        newNormX = newNormX,
        newNormY = newNormY,
        newWidthPx = clampedWPx,
        newHeightPx = clampedHPx,
    )
}

/**
 * Formats a button dimension in design-canvas pixels for display in the editor, HUD, and labels.
 * Formats as an integer pixel value (e.g. "136", "68").
 */
fun formatButtonDimensionPx(px: Float): String = px.roundToInt().toString()
