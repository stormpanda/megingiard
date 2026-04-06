package com.stormpanda.megingiard.macropad

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Canvas
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val MT_TOP_BAR_HEIGHT            = 56
private const val MT_PADDING                   = 16
private const val MT_DP_PER_MS                 = 0.3f   // dp per millisecond on timeline
private const val MT_LANE_HEIGHT_DP            = 32     // timeline lane height in dp
private const val MT_AXIS_HEIGHT_DP            = 24     // time-label row height in dp
private const val MT_MIN_TIMELINE_MS           = 1000L  // minimum canvas width in ms
private const val MT_TICK_INTERVAL_MS          = 500L   // vertical grid line interval
private const val MT_STEP_BAR_PADDING          = 2f     // px padding inside each bar
private const val MT_AXIS_TEXT_SIZE            = 28f    // native paint text size
private const val MT_BAR_CORNER_RADIUS         = 4      // dp
private val MT_COLOR_JOYSTICK                  = Color(0xFFFF9800) // orange
private val MT_COLOR_DPAD                      = Color(0xFF2196F3) // blue

// ─────────────────────────────────────────────────────────────────────────────
// Lane assignment — greedy; assigns each step (sorted by startTimeMs) to the
// first lane whose last step ended at or before this step's startTimeMs.
// Returns a list of lane indices parallel to the original `steps` list.
// ─────────────────────────────────────────────────────────────────────────────

private fun assignLanes(steps: List<MacroStep>): List<Int> {
    val sortedIndices = steps.indices.sortedBy { steps[it].startTimeMs }
    val result        = IntArray(steps.size)
    val laneEndTimes  = mutableListOf<Long>()
    sortedIndices.forEach { originalIdx ->
        val step     = steps[originalIdx]
        val laneIdx  = laneEndTimes.indexOfFirst { it <= step.startTimeMs }
        if (laneIdx == -1) {
            result[originalIdx] = laneEndTimes.size
            laneEndTimes.add(step.endTimeMs())
        } else {
            result[originalIdx] = laneIdx
            laneEndTimes[laneIdx] = step.endTimeMs()
        }
    }
    return result.toList()
}

// ─────────────────────────────────────────────────────────────────────────────
// Direction helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun dirArrow(dirX: Int, dirY: Int): String = when {
    dirX > 0  && dirY < 0  -> "↗"
    dirX > 0  && dirY == 0 -> "→"
    dirX > 0  && dirY > 0  -> "↘"
    dirX == 0 && dirY < 0  -> "↑"
    dirX == 0 && dirY > 0  -> "↓"
    dirX < 0  && dirY < 0  -> "↖"
    dirX < 0  && dirY == 0 -> "←"
    dirX < 0  && dirY > 0  -> "↙"
    else                    -> "·"
}

private fun joyDirArrow(x: Float, y: Float): String {
    val mag = sqrt(x * x + y * y)
    if (mag < 0.1f) return "·"
    val nx = when { x / mag > 0.5f ->  1; x / mag < -0.5f -> -1; else -> 0 }
    val ny = when { y / mag > 0.5f ->  1; y / mag < -0.5f -> -1; else -> 0 }
    return dirArrow(nx, ny)
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroTimelineEditor — inline (no Dialog wrapper; caller wraps in Dialog)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen editor for a single [Macro]'s step timeline.
 *
 * Designed to be hosted inside a `Dialog(usePlatformDefaultWidth = false)` by the
 * caller. Top bar shows the macro name (editable) and Save / Back buttons.
 * Below the top bar is a horizontal-scrollable visual timeline and a scrollable
 * step list. Steps can be added, edited and deleted.
 *
 * @param macro       The macro to edit (its steps and name are copied to local state).
 * @param accentColor Accent color propagated from the parent editor.
 * @param onSave      Called with the updated [Macro] when the user taps Save.
 * @param onBack      Called when the user taps Back / Cancel without saving.
 */
@Composable
internal fun MacroTimelineEditor(
    macro:       Macro,
    accentColor: Color,
    onSave:      (Macro) -> Unit,
    onBack:      () -> Unit,
) {
    val colors = LocalAppColors.current

    var localName        by remember { mutableStateOf(macro.name) }
    var steps            by remember { mutableStateOf(macro.steps) }
    var showAddStep      by remember { mutableStateOf(false) }
    var editingStepIndex by remember { mutableStateOf<Int?>(null) }
    var deleteStepIndex  by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MT_TOP_BAR_HEIGHT.dp)
                .background(colors.surface)
                .padding(horizontal = MT_PADDING.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value         = localName,
                onValueChange = { localName = it },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = colors.accentBorder,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = accentColor,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.macropad_macro_editor_cancel), color = colors.onSurfaceSecondary)
            }
            TextButton(
                onClick = {
                    onSave(macro.copy(name = localName.trim().ifBlank { macro.name }, steps = steps))
                },
                enabled = localName.isNotBlank(),
            ) {
                Text(
                    stringResource(R.string.macropad_macro_editor_save),
                    color      = if (localName.isNotBlank()) accentColor else colors.onSurfaceSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        HorizontalDivider(color = colors.divider)

        // ── Scrollable body ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Timeline section
            if (steps.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height((MT_LANE_HEIGHT_DP + MT_AXIS_HEIGHT_DP).dp)
                        .padding(MT_PADDING.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.macropad_macro_no_steps),
                        color    = colors.onSurfaceSecondary,
                        fontSize = 13.sp,
                    )
                }
            } else {
                MacroTimeline(steps = steps, accentColor = accentColor)
            }

            HorizontalDivider(color = colors.divider)

            // Step list
            steps.forEachIndexed { idx, step ->
                StepListItem(
                    index       = idx,
                    step        = step,
                    accentColor = accentColor,
                    onEdit      = { editingStepIndex = idx },
                    onDelete    = { deleteStepIndex = idx },
                )
                HorizontalDivider(color = colors.divider)
            }

            // Add Step chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MT_PADDING.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { showAddStep = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint     = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.macropad_macro_add_step),
                        color    = accentColor,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }

    // ── Add step dialog ───────────────────────────────────────────────────────
    if (showAddStep) {
        MacroStepEditDialog(
            step        = null,
            accentColor = accentColor,
            onConfirm   = { newStep -> steps = steps + newStep; showAddStep = false },
            onDismiss   = { showAddStep = false },
        )
    }

    // ── Edit step dialog ──────────────────────────────────────────────────────
    editingStepIndex?.let { idx ->
        MacroStepEditDialog(
            step        = steps[idx],
            accentColor = accentColor,
            onConfirm   = { updated ->
                steps = steps.toMutableList().also { it[idx] = updated }
                editingStepIndex = null
            },
            onDismiss   = { editingStepIndex = null },
        )
    }

    // ── Delete step confirmation ───────────────────────────────────────────────
    if (deleteStepIndex != null) {
        AlertDialog(
            containerColor   = colors.surface,
            onDismissRequest = { deleteStepIndex = null },
            title   = { Text(stringResource(R.string.macropad_macro_step_delete_title), color = colors.onSurface) },
            text    = { Text(stringResource(R.string.macropad_macro_step_delete_confirm), color = colors.onSurfaceSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    deleteStepIndex?.let { idx ->
                        steps = steps.filterIndexed { i, _ -> i != idx }
                    }
                    deleteStepIndex = null
                }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteStepIndex = null }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Visual timeline canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroTimeline(
    steps:       List<MacroStep>,
    accentColor: Color,
) {
    val laneAssignment = remember(steps) { assignLanes(steps) }
    val numLanes       = remember(laneAssignment) { (laneAssignment.maxOrNull() ?: 0) + 1 }
    val totalMs        = remember(steps) { steps.totalDurationMs().coerceAtLeast(MT_MIN_TIMELINE_MS) }
    val canvasWidthDp  = (totalMs * MT_DP_PER_MS).dp
    val canvasHeightDp = (numLanes * MT_LANE_HEIGHT_DP + MT_AXIS_HEIGHT_DP).dp
    val density        = LocalDensity.current
    val pxPerMs        = with(density) { MT_DP_PER_MS.dp.toPx() }
    val laneHeightPx   = with(density) { MT_LANE_HEIGHT_DP.dp.toPx() }
    val colors         = LocalAppColors.current

    val textPaint = remember {
        NativePaint().apply {
            isAntiAlias = true
            textSize    = MT_AXIS_TEXT_SIZE
            color       = 0xFFA0A0A0.toInt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(canvasHeightDp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Canvas(
            modifier = Modifier
                .width(canvasWidthDp)
                .height(canvasHeightDp),
        ) {
            // Draw step bars
            steps.forEachIndexed { idx, step ->
                val lane     = laneAssignment.getOrElse(idx) { 0 }
                val startX   = step.startTimeMs * pxPerMs
                val barWidth = (step.durationMs * pxPerMs).coerceAtLeast(2f)
                val laneY    = lane * laneHeightPx
                val barColor = stepColor(step, accentColor)
                drawRoundRect(
                    color       = barColor.copy(alpha = 0.85f),
                    topLeft     = Offset(startX, laneY + MT_STEP_BAR_PADDING),
                    size        = Size(barWidth, laneHeightPx - MT_STEP_BAR_PADDING * 2),
                    cornerRadius = CornerRadius(MT_BAR_CORNER_RADIUS.dp.toPx()),
                )
            }

            // Draw tick lines and time labels
            drawTimeTicks(
                totalMs      = totalMs,
                numLanes     = numLanes,
                pxPerMs      = pxPerMs,
                laneHeightPx = laneHeightPx,
                textPaint    = textPaint,
                dividerColor = colors.divider,
            )
        }
    }
}

private fun stepColor(step: MacroStep, accentColor: Color): Color = when (step) {
    is MacroStep.GamepadButtonTap -> accentColor
    is MacroStep.JoystickMove     -> MT_COLOR_JOYSTICK
    is MacroStep.DPadTap          -> MT_COLOR_DPAD
}

private fun DrawScope.drawTimeTicks(
    totalMs:      Long,
    numLanes:     Int,
    pxPerMs:      Float,
    laneHeightPx: Float,
    textPaint:    NativePaint,
    dividerColor: Color,
) {
    val laneAreaHeight  = numLanes * laneHeightPx
    val labelY          = laneAreaHeight + MT_AXIS_HEIGHT_DP.dp.toPx() - 6.dp.toPx()
    var tick            = 0L
    while (tick <= totalMs + MT_TICK_INTERVAL_MS) {
        val tickX = tick * pxPerMs
        drawLine(
            color       = dividerColor.copy(alpha = 0.4f),
            start       = Offset(tickX, 0f),
            end         = Offset(tickX, laneAreaHeight),
            strokeWidth = 1f,
        )
        drawContext.canvas.nativeCanvas.drawText("${tick}ms", tickX + 2f, labelY, textPaint)
        tick += MT_TICK_INTERVAL_MS
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step list item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepListItem(
    index:       Int,
    step:        MacroStep,
    accentColor: Color,
    onEdit:      () -> Unit,
    onDelete:    () -> Unit,
) {
    val colors = LocalAppColors.current
    val typeLabel = stringResource(when (step) {
        is MacroStep.GamepadButtonTap -> R.string.macropad_macro_step_type_gamepad
        is MacroStep.JoystickMove     -> R.string.macropad_macro_step_type_joystick
        is MacroStep.DPadTap          -> R.string.macropad_macro_step_type_dpad
    })
    val description = when (step) {
        is MacroStep.GamepadButtonTap -> step.label
        is MacroStep.JoystickMove     -> {
            val stickLabel = if (step.stick == JoystickStick.LEFT) "L" else "R"
            "$stickLabel ${joyDirArrow(step.x, step.y)}"
        }
        is MacroStep.DPadTap -> dirArrow(step.dirX, step.dirY)
    }
    val indicatorColor = stepColor(step, accentColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = MT_PADDING.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor)
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${index + 1}. $typeLabel: $description",
                color    = colors.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@${step.startTimeMs}ms  ⟶  ${step.durationMs}ms",
                color    = colors.onSurfaceSecondary,
                fontSize = 12.sp,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.macropad_editor_rename),
                tint               = colors.onSurfaceSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.macropad_editor_delete_button),
                tint               = colors.onSurfaceSecondary,
            )
        }
    }
}
