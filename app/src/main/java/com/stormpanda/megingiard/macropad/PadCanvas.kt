package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ED_BUTTON_UNIT_DP      = 60.dp
private val ED_BTN_SQUARE_RADIUS   = 4.dp
// Reuse the shared screen padding so the editor canvas remains pixel-identical to use mode.
private val PC_SCREEN_PADDING      = MP_SCREEN_PADDING
private const val ED_BTN_DISABLED_ALPHA = 0.38f
private const val ED_EDGE_MARGIN        = 0.05f

// Grid: half a button unit — two steps apart = buttons touch exactly
private val PC_GRID_STEP_DP        = 30.dp
private const val PC_GRID_LINE_ALPHA    = 0.12f
private const val PC_GRID_STROKE_PX     = 1f
private const val PC_RADIAL_CENTER_X    = 0.5f
private const val PC_RADIAL_CENTER_Y    = 0.5f

// Radial grid: snap points evenly distributed along each circle
private val PC_RADIAL_DOT_RADIUS    = 2.dp
private val PC_RADIAL_CENTER_DOT    = 3.dp
private const val PC_RADIAL_DOT_ALPHA   = 0.35f
private const val PC_RADIAL_MIN_POINTS  = 4

// ─────────────────────────────────────────────────────────────────────────────
// Grid mode
// ─────────────────────────────────────────────────────────────────────────────

internal enum class GridMode { OFF, RECTANGULAR, RADIAL }

// ─────────────────────────────────────────────────────────────────────────────
// Pad canvas — drag buttons to reposition
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PadCanvas(profile: PadProfile, accentColor: Color, gridMode: GridMode) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val colors     = LocalAppColors.current
    val density    = LocalDensity.current
    val configuration = LocalConfiguration.current
    val padWidth      = configuration.screenWidthDp.dp  - PC_SCREEN_PADDING * 2
    val padHeight     = configuration.screenHeightDp.dp - PC_SCREEN_PADDING * 2
    val gridStepPx    = with(density) { PC_GRID_STEP_DP.toPx() }

    val padModifier = Modifier
        .width(padWidth)
        .height(padHeight)
        .border(1.dp, colors.accentBorder, RoundedCornerShape(4.dp))
        .clip(RoundedCornerShape(4.dp))
        .background(colors.surface)
        .onSizeChanged { canvasSize = it }

    Box(modifier = padModifier) {
        // Grid overlay — drawn behind buttons
        if (gridMode != GridMode.OFF && canvasSize.width > 0 && canvasSize.height > 0) {
            GridOverlay(
                gridMode   = gridMode,
                gridStepPx = gridStepPx,
                gridColor  = accentColor.copy(alpha = PC_GRID_LINE_ALPHA),
            )
        }

        // Render each button as a draggable chip
        profile.buttons.forEach { btn ->
            DraggableButton(
                btn            = btn,
                canvasSize     = canvasSize,
                accentColor    = accentColor,
                enableKeyboard = profile.enableKeyboard,
                enableGamepad  = profile.enableGamepad,
                enableMouse    = profile.enableMouse,
                gridMode       = gridMode,
                gridStepPx     = gridStepPx,
                onPositionChanged = { nx, ny ->
                    MacroPadState.updateProfile(
                        profile.copy(
                            buttons = profile.buttons.map { b ->
                                if (b.id == btn.id) b.copy(posX = nx, posY = ny) else b
                            }
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun DraggableButton(
    btn:               PadButton,
    canvasSize:        IntSize,
    accentColor:       Color,
    enableKeyboard:    Boolean,
    enableGamepad:     Boolean,
    enableMouse:       Boolean,
    gridMode:          GridMode,
    gridStepPx:        Float,
    onPositionChanged: (Float, Float) -> Unit,
) {
    val colors = LocalAppColors.current
    // rememberUpdatedState lets the pointerInput closure (keyed only on btn.id +
    // canvasSize) see the live btn even though its lambda is NOT restarted when
    // btn.posX/posY change between drags.
    val currentBtn = rememberUpdatedState(btn)
    // Always call the latest onPositionChanged so PadCanvas's stale-profile
    // closure (captured by pointerInput) doesn't revert sibling button positions.
    val currentOnPositionChanged = rememberUpdatedState(onPositionChanged)
    val currentGridMode = rememberUpdatedState(gridMode)
    val currentGridStepPx = rememberUpdatedState(gridStepPx)
    // Anchor position captured at the moment the finger goes down.
    var startPosX by remember(btn.id) { mutableFloatStateOf(btn.posX) }
    var startPosY by remember(btn.id) { mutableFloatStateOf(btn.posY) }
    var dragOffsetX by remember(btn.id) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(btn.id) { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val isDeviceDisabled = when (btn.action) {
        is PadAction.KeyboardKey                 -> !enableKeyboard
        is PadAction.GamepadButton               -> !enableGamepad
        is PadAction.MouseButton,
        is PadAction.ScrollWheel,
        is PadAction.TrackpointMove,
        is PadAction.MouseLeftClick,
        is PadAction.MouseRightClick             -> !enableMouse
        is PadAction.Macro                       -> !enableGamepad
        is PadAction.AmbientPeek                 -> false
    }
    val tpMultiplier = if (isTrackpoint) (btn.action as PadAction.TrackpointMove).size.multiplier else 1f
    val chipWidthPx  = with(density) {
        if (isTrackpoint) (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
        else (ED_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx()
    }
    val chipHeightPx = with(density) {
        if (isTrackpoint) (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
        else (ED_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx()
    }

    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)

    // Top-left position in canvas pixels (centre adjusted by half-chip)
    val left = btn.posX * w - chipWidthPx / 2f
    val top  = btn.posY * h - chipHeightPx / 2f

    val chipShape = if (isTrackpoint) CircleShape else when (btn.buttonShape) {
        ButtonShape.SQUARE -> RoundedCornerShape(ED_BTN_SQUARE_RADIUS)
        ButtonShape.CIRCLE -> when (btn.buttonSize) {
            ButtonSize.SIZE_2X2                      -> CircleShape
            ButtonSize.SIZE_2X1, ButtonSize.SIZE_1X2 -> RoundedCornerShape(percent = 50)
            ButtonSize.SIZE_1X1                      -> CircleShape
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .width(if (isTrackpoint) ED_BUTTON_UNIT_DP * tpMultiplier else ED_BUTTON_UNIT_DP * btn.buttonSize.cols)
            .height(if (isTrackpoint) ED_BUTTON_UNIT_DP * tpMultiplier else ED_BUTTON_UNIT_DP * btn.buttonSize.rows)
            .drawWithContent {
                if (isDeviceDisabled) {
                    val p = Paint().apply {
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        this.alpha = ED_BTN_DISABLED_ALPHA
                    }
                    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), p)
                    drawContent()
                    drawContext.canvas.restore()
                } else {
                    drawContent()
                }
            }
            .clip(chipShape)
            .background(accentColor.copy(alpha = 0.25f))
            .border(1.dp, accentColor, chipShape)
            .pointerInput(btn.id, canvasSize) {
                detectDragGestures(
                    onDragStart = {
                        // Capture the current (live) position as anchor so the
                        // accumulated delta is always relative to this drag's start.
                        startPosX = currentBtn.value.posX
                        startPosY = currentBtn.value.posY
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        dragOffsetX += drag.x
                        dragOffsetY += drag.y
                        val rawX = (startPosX + dragOffsetX / w).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN)
                        val rawY = (startPosY + dragOffsetY / h).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN)
                        val (snappedX, snappedY) = snapPosition(
                            rawX, rawY, w, h,
                            currentGridMode.value, currentGridStepPx.value,
                        )
                        currentOnPositionChanged.value(
                            snappedX.coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                            snappedY.coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                        )
                    },
                )
            }
    ) {
        if (btn.action is PadAction.TrackpointMove) {
            Text("●", color = accentColor, fontSize = 14.sp)
        } else if (btn.action is PadAction.ScrollWheel) {
            // Show mini scroll icon in editor chip
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp,   contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(14.dp))
                Icon(Icons.Rounded.KeyboardArrowUp,   contentDescription = null, tint = colors.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(14.dp))
            }
        } else {
            if (btn.iconName != null) {
                MaterialSymbol(
                    name = btn.iconName,
                    size = MP_BUTTON_UNIT_DP * 0.73f * minOf(btn.buttonSize.cols, btn.buttonSize.rows),
                    tint = colors.onSurface,
                    filled = iconsFilledState.value,
                )
            } else {
                Text(btn.label, color = colors.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GridOverlay(gridMode: GridMode, gridStepPx: Float, gridColor: Color) {
    val density = LocalDensity.current
    val dotRadiusPx  = with(density) { PC_RADIAL_DOT_RADIUS.toPx() }
    val centerDotPx  = with(density) { PC_RADIAL_CENTER_DOT.toPx() }
    val buttonUnitPx = with(density) { ED_BUTTON_UNIT_DP.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (gridMode) {
            GridMode.OFF -> { /* no-op */ }

            GridMode.RECTANGULAR -> {
                // Lines are centred on the canvas midpoint so the rectangular
                // grid shares its origin with the radial grid's circle centre.
                val cx = w * PC_RADIAL_CENTER_X
                val cy = h * PC_RADIAL_CENTER_Y
                // Vertical lines outward from centre
                var dx = 0f
                while (cx - dx >= 0f || cx + dx <= w) {
                    if (cx + dx <= w)
                        drawLine(gridColor, Offset(cx + dx, 0f), Offset(cx + dx, h), strokeWidth = PC_GRID_STROKE_PX)
                    if (dx > 0f && cx - dx >= 0f)
                        drawLine(gridColor, Offset(cx - dx, 0f), Offset(cx - dx, h), strokeWidth = PC_GRID_STROKE_PX)
                    dx += gridStepPx
                }
                // Horizontal lines outward from centre
                var dy = 0f
                while (cy - dy >= 0f || cy + dy <= h) {
                    if (cy + dy <= h)
                        drawLine(gridColor, Offset(0f, cy + dy), Offset(w, cy + dy), strokeWidth = PC_GRID_STROKE_PX)
                    if (dy > 0f && cy - dy >= 0f)
                        drawLine(gridColor, Offset(0f, cy - dy), Offset(w, cy - dy), strokeWidth = PC_GRID_STROKE_PX)
                    dy += gridStepPx
                }
            }

            GridMode.RADIAL -> {
                val cx = w * PC_RADIAL_CENTER_X
                val cy = h * PC_RADIAL_CENTER_Y
                val maxRadius = maxOf(w, h) / 2f
                val dotRadius = dotRadiusPx
                val centerDotRadius = centerDotPx
                val dotColor = gridColor.copy(alpha = PC_RADIAL_DOT_ALPHA)

                // Concentric circles with evenly-distributed snap dots.
                // Odd circles (1, 3, 5 …): phase 45° → diagonals as anchors.
                // Even circles (2, 4, 6 …): phase 0° → cardinal directions as anchors.
                var r = gridStepPx
                var circleIndex = 1
                while (r <= maxRadius) {
                    drawCircle(gridColor, radius = r, center = Offset(cx, cy), style = Stroke(PC_GRID_STROKE_PX))
                    val n = radialPointCount(r, buttonUnitPx)
                    val phaseOffset = if (circleIndex % 2 == 1) PI / 4.0 else 0.0
                    val angleStep = 2.0 * PI / n
                    for (i in 0 until n) {
                        val angle = (phaseOffset + i * angleStep).toFloat()
                        val px = cx + r * cos(angle)
                        val py = cy + r * sin(angle)
                        drawCircle(dotColor, radius = dotRadius, center = Offset(px, py))
                    }
                    r += gridStepPx
                    circleIndex++
                }

                // Center snap point
                drawCircle(dotColor, radius = centerDotRadius, center = Offset(cx, cy))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Snap helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Snap a normalised position to the active grid. Returns the (possibly unchanged)
 * normalised coordinates.
 */
private fun snapPosition(
    rawNormX: Float,
    rawNormY: Float,
    canvasW:  Float,
    canvasH:  Float,
    gridMode: GridMode,
    gridStepPx: Float,
): Pair<Float, Float> = when (gridMode) {
    GridMode.OFF -> rawNormX to rawNormY
    GridMode.RECTANGULAR -> snapRectangular(rawNormX, rawNormY, canvasW, canvasH, gridStepPx)
    GridMode.RADIAL      -> snapRadial(rawNormX, rawNormY, canvasW, canvasH, gridStepPx)
}

/**
 * Round to nearest grid intersection. The grid is centred on the canvas midpoint
 * (same origin as the radial circles) so the centre is always a cross-point.
 */
private fun snapRectangular(
    rawNormX: Float,
    rawNormY: Float,
    canvasW:  Float,
    canvasH:  Float,
    gridStepPx: Float,
): Pair<Float, Float> {
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
private fun snapRadial(
    rawNormX: Float,
    rawNormY: Float,
    canvasW:  Float,
    canvasH:  Float,
    gridStepPx: Float,
): Pair<Float, Float> {
    val rawPxX = rawNormX * canvasW
    val rawPxY = rawNormY * canvasH
    val cx = canvasW * PC_RADIAL_CENTER_X
    val cy = canvasH * PC_RADIAL_CENTER_Y

    val dx = rawPxX - cx
    val dy = rawPxY - cy
    val rawRadius = sqrt(dx * dx + dy * dy)

    // Grid step is always half the button unit
    val buttonUnitPx = gridStepPx * 2f

    // Snap radius to nearest circle (or 0 = center)
    val snappedRadius = (round(rawRadius / gridStepPx) * gridStepPx)

    // Center snap
    if (snappedRadius < gridStepPx * 0.5f) {
        return (cx / canvasW) to (cy / canvasH)
    }

    // Determine phase offset for this circle
    val circleIndex = round(snappedRadius / gridStepPx).toInt()
    val phaseOffset = if (circleIndex % 2 == 1) PI / 4.0 else 0.0

    val n = radialPointCount(snappedRadius, buttonUnitPx)
    val angleStep = 2.0 * PI / n

    // Snap to nearest point: work in phase-relative angle space
    val rawAngle = atan2(dy.toDouble(), dx.toDouble())           // −π..π
    val relAngle = rawAngle - phaseOffset                         // shift to phase origin
    val relAnglePos = if (relAngle < 0) relAngle + 2 * PI else relAngle  // 0..2π
    val nearestIndex = round(relAnglePos / angleStep).toInt() % n
    val snappedAngle = phaseOffset + nearestIndex * angleStep

    val snappedPxX = cx + snappedRadius * cos(snappedAngle).toFloat()
    val snappedPxY = cy + snappedRadius * sin(snappedAngle).toFloat()

    // Also consider the center point — pick whichever is closer
    val distToCircle = dist(rawPxX, rawPxY, snappedPxX, snappedPxY)
    val distToCenter = dist(rawPxX, rawPxY, cx, cy)
    return if (distToCenter < distToCircle) {
        (cx / canvasW) to (cy / canvasH)
    } else {
        (snappedPxX / canvasW) to (snappedPxY / canvasH)
    }
}

/** Euclidean distance between two points. */
private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}

/**
 * How many evenly-distributed snap points to place on a circle of the given radius.
 * Scales with circumference — roughly one point per [buttonUnitPx] of arc length.
 * Always rounded to the nearest multiple of 4 (minimum 4) so the 4 phase-anchor
 * points (cardinal or diagonal) land at exact positions.
 */
private fun radialPointCount(radiusPx: Float, buttonUnitPx: Float): Int {
    val circumference = (2.0 * PI * radiusPx).toFloat()
    val raw = round(circumference / buttonUnitPx).toInt().coerceAtLeast(1)
    // Round to nearest multiple of 4, minimum 4
    val rounded4 = ((raw + 2) / 4) * 4
    return maxOf(PC_RADIAL_MIN_POINTS, rounded4)
}
