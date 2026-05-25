package com.stormpanda.megingiard.macropad

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.max

private const val TAG = "MacroVerticalTimeline"

private const val MTE_TICK_INTERVAL_MS = 500L
private const val MTE_AXIS_TEXT_SIZE_SP = 11
private const val MTE_BAR_CORNER_RADIUS = 4
private const val MTE_VERTICAL_DP_PER_MS = 0.22f
private const val MTE_VERTICAL_AXIS_WIDTH = 52
private const val MTE_VERTICAL_BAR_PADDING = 3f
private const val MTE_BAR_LABEL_TEXT_SIZE_SP = 10
internal const val MTE_TIMELINE_SIDE_PADDING = 10

private fun assignLanes(steps: List<MacroStep>): List<Int> {
    val sortedIndices = steps.indices.sortedBy { steps[it].startTimeMs }
    val result = IntArray(steps.size)
    val laneEndTimes = mutableListOf<Long>()
    sortedIndices.forEach { originalIdx ->
        val step = steps[originalIdx]
        val laneIdx = laneEndTimes.indexOfFirst { it <= step.startTimeMs }
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

@Composable
internal fun MacroVerticalTimeline(
    modifier: Modifier,
    steps: List<MacroStep>,
    accentColor: Color,
    onEditStep: (Int) -> Unit,
) {
    val laneAssignment = remember(steps) { assignLanes(steps) }
    val numLanes = remember(laneAssignment) { (laneAssignment.maxOrNull() ?: 0) + 1 }
    val totalMs = remember(steps) { steps.totalDurationMs().coerceAtLeast(1000L) }
    val density = LocalDensity.current
    val colors = LocalAppColors.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
    val joystickColor = colors.actionColorGamepad
    val dpadColor = colors.actionColorSystem
    val touchColor = MaterialTheme.colorScheme.tertiary
    val tickFormat = stringResource(R.string.macropad_macro_timeline_tick)
    val tapLabel = stringResource(R.string.macropad_macro_step_label_short_tap)
    val gestureLabel = stringResource(R.string.macropad_macro_step_label_short_gesture)
    val stepLabels = remember(steps, swapFaceButtons, tapLabel, gestureLabel) {
        steps.map { shortStepLabel(it, swapFaceButtons, tapLabel, gestureLabel) }
    }

    val pxPerMs = with(density) { MTE_VERTICAL_DP_PER_MS.dp.toPx() }
    val axisWidthPx = with(density) { MTE_VERTICAL_AXIS_WIDTH.dp.toPx() }
    val contentHeightDp = (totalMs * MTE_VERTICAL_DP_PER_MS).dp

    val textPaint = remember(density, colors.onSurfaceSecondary) {
        NativePaint().apply {
            isAntiAlias = true
            textSize = with(density) { MTE_AXIS_TEXT_SIZE_SP.sp.toPx() }
            color = colors.onSurfaceSecondary.toArgb()
        }
    }
    val labelPaint = remember(density, colors.onSurface) {
        NativePaint().apply {
            isAntiAlias = true
            isFakeBoldText = true
            textSize = with(density) { MTE_BAR_LABEL_TEXT_SIZE_SP.sp.toPx() }
            color = colors.onSurface.toArgb()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
    ) {
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        val laneWidthPx = max((canvasWidthPx - axisWidthPx) / numLanes.toFloat(), 1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeightDp)
                .pointerInput(steps, laneWidthPx, axisWidthPx, pxPerMs) {
                    detectTapGestures { tap ->
                        val y = tap.y
                        val x = tap.x
                        val hitIndex = steps.indices.reversed().firstOrNull { idx ->
                            val step = steps[idx]
                            val lane = laneAssignment.getOrElse(idx) { 0 }
                            val left = axisWidthPx + lane * laneWidthPx + MTE_VERTICAL_BAR_PADDING
                            val right = left + laneWidthPx - MTE_VERTICAL_BAR_PADDING * 2
                            val top = step.startTimeMs * pxPerMs
                            val bottom = top + (step.durationMs * pxPerMs).coerceAtLeast(6f)
                            x in left..right && y in top..bottom
                        }
                        if (hitIndex != null) onEditStep(hitIndex)
                    }
                },
        ) {
            drawVerticalTicks(
                totalMs = totalMs,
                pxPerMs = pxPerMs,
                width = size.width,
                axisWidthPx = axisWidthPx,
                textPaint = textPaint,
                dividerColor = colors.divider,
                tickLabel = { ms -> tickFormat.format(ms) },
            )

            repeat(numLanes + 1) { laneIndex ->
                val x = axisWidthPx + laneIndex * laneWidthPx
                drawLine(
                    color = colors.divider.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
            }

            steps.forEachIndexed { idx, step ->
                val lane = laneAssignment.getOrElse(idx) { 0 }
                val barTop = step.startTimeMs * pxPerMs
                val barHeight = (step.durationMs * pxPerMs).coerceAtLeast(6f)
                val barLeft = axisWidthPx + lane * laneWidthPx + MTE_VERTICAL_BAR_PADDING
                val barWidth = laneWidthPx - MTE_VERTICAL_BAR_PADDING * 2
                val barLabel = stepLabels.getOrElse(idx) { "" }
                drawRoundRect(
                    color = stepColor(step, accentColor, joystickColor, dpadColor, touchColor).copy(alpha = 0.85f),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(MTE_BAR_CORNER_RADIUS.dp.toPx()),
                )
                if (barLabel.isNotEmpty() && barHeight > labelPaint.textSize + 6f) {
                    drawContext.canvas.nativeCanvas.apply {
                        save()
                        clipRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight)
                        drawText(barLabel, barLeft + 4f, barTop + labelPaint.textSize + 2f, labelPaint)
                        restore()
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawVerticalTicks(
    totalMs: Long,
    pxPerMs: Float,
    width: Float,
    axisWidthPx: Float,
    textPaint: NativePaint,
    dividerColor: Color,
    tickLabel: (Long) -> String,
) {
    var tick = 0L
    while (tick <= totalMs + MTE_TICK_INTERVAL_MS) {
        val y = tick * pxPerMs
        drawLine(
            color = dividerColor.copy(alpha = 0.45f),
            start = Offset(axisWidthPx, y),
            end = Offset(width, y),
            strokeWidth = 1f,
        )
        drawContext.canvas.nativeCanvas.drawText(tickLabel(tick), 2f, y + textPaint.textSize, textPaint)
        tick += MTE_TICK_INTERVAL_MS
    }
}
