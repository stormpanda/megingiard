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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ED_BUTTON_UNIT_DP      = 60.dp
private val ED_BTN_SQUARE_RADIUS   = 4.dp
// Must match MP_SCREEN_PADDING in MacroPadScreen.kt so the editor canvas is pixel-identical to use mode
private val PC_SCREEN_PADDING      = 4.dp
private const val ED_BTN_DISABLED_ALPHA = 0.38f
private const val ED_EDGE_MARGIN        = 0.05f

// Grid: half a button unit — two steps apart = buttons touch exactly
private val PC_GRID_STEP_DP        = 30.dp
private const val PC_GRID_LINE_ALPHA    = 0.12f
private const val PC_GRID_STROKE_PX     = 1f
private const val PC_RADIAL_CENTER_X    = 0.5f
private const val PC_RADIAL_CENTER_Y    = 0.5f

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
        is PadAction.Macro                       -> false
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
                Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(14.dp))
                Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = colors.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(14.dp))
            }
        } else {
            Text(btn.label, color = colors.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GridOverlay(gridMode: GridMode, gridStepPx: Float, gridColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (gridMode) {
            GridMode.OFF -> { /* no-op */ }

            GridMode.RECTANGULAR -> {
                // Vertical lines
                var x = gridStepPx
                while (x < w) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = PC_GRID_STROKE_PX)
                    x += gridStepPx
                }
                // Horizontal lines
                var y = gridStepPx
                while (y < h) {
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = PC_GRID_STROKE_PX)
                    y += gridStepPx
                }
            }

            GridMode.RADIAL -> {
                val cx = w * PC_RADIAL_CENTER_X
                val cy = h * PC_RADIAL_CENTER_Y
                val maxRadius = maxOf(w, h) / 2f

                // Concentric circles
                var r = gridStepPx
                while (r <= maxRadius) {
                    drawCircle(gridColor, radius = r, center = Offset(cx, cy), style = Stroke(PC_GRID_STROKE_PX))
                    r += gridStepPx
                }
                // Horizontal lines (same as rectangular)
                var y = gridStepPx
                while (y < h) {
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = PC_GRID_STROKE_PX)
                    y += gridStepPx
                }
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

/** Round to nearest grid intersection. */
private fun snapRectangular(
    rawNormX: Float,
    rawNormY: Float,
    canvasW:  Float,
    canvasH:  Float,
    gridStepPx: Float,
): Pair<Float, Float> {
    val rawPxX = rawNormX * canvasW
    val rawPxY = rawNormY * canvasH
    val snappedPxX = (rawPxX / gridStepPx).roundToInt() * gridStepPx
    val snappedPxY = (rawPxY / gridStepPx).roundToInt() * gridStepPx
    return (snappedPxX / canvasW) to (snappedPxY / canvasH)
}

/**
 * Snap to the nearest intersection of a concentric circle with a horizontal grid line.
 *
 * 1. Find the nearest horizontal grid line (same as rectangular Y-snap).
 * 2. Find the nearest circle (snap distance from center to nearest radius step).
 * 3. Compute x-intersection of that circle with that horizontal line.
 * 4. Pick the side (left/right of center) closest to the raw position.
 *
 * Falls back to the rectangular snap if no valid intersection exists (horizontal
 * line is outside the circle).
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

    // Snap Y to nearest horizontal grid line
    val snappedPxY = (rawPxY / gridStepPx).roundToInt() * gridStepPx

    // Snap radius to nearest circle
    val dx = rawPxX - cx
    val dy = rawPxY - cy
    val rawRadius = sqrt(dx * dx + dy * dy)
    val snappedRadius = ((rawRadius / gridStepPx).roundToInt() * gridStepPx).coerceAtLeast(gridStepPx)

    // Compute x-intersection: x² = r² - (snappedY - cy)²
    val dySnapped = snappedPxY - cy
    val xSqr = snappedRadius * snappedRadius - dySnapped * dySnapped

    return if (xSqr >= 0f) {
        val xOffset = sqrt(xSqr)
        // Choose intersection side closest to raw position
        val candidateLeft  = cx - xOffset
        val candidateRight = cx + xOffset
        val snappedPxX = if (abs(rawPxX - candidateLeft) < abs(rawPxX - candidateRight)) {
            candidateLeft
        } else {
            candidateRight
        }
        (snappedPxX / canvasW) to (snappedPxY / canvasH)
    } else {
        // Horizontal line doesn't intersect the circle — fall back to rectangular
        snapRectangular(rawNormX, rawNormY, canvasW, canvasH, gridStepPx)
    }
}
