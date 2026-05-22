package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt

private const val TAG = "TimelineLoopSettings"

private const val MTE_PADDING = 16
internal const val MTE_LOOP_PAUSE_INIT_MAX_MS = 2_000
private const val MTE_LOOP_PAUSE_SCALE_STEP_MS = 1_000
private const val MTE_LOOP_PAUSE_SLIDER_STEP_MS = 100

/**
 * Extends the loop-pause slider scale in [MTE_LOOP_PAUSE_SCALE_STEP_MS] increments until
 * [requiredValueMs] fits, mirroring the scale-extension pattern in MacroStepEditDialog.
 */
internal fun mtExpandLoopScale(currentMaxMs: Int, requiredValueMs: Int): Int {
    var maxMs = currentMaxMs.coerceAtLeast(MTE_LOOP_PAUSE_SCALE_STEP_MS)
    while (requiredValueMs > maxMs) maxMs += MTE_LOOP_PAUSE_SCALE_STEP_MS
    return maxMs
}

@Composable
private fun MtLoopPauseDeltaButton(
    accentColor: Color,
    deltaMs: Int,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        modifier = Modifier.defaultMinSize(minWidth = 30.dp, minHeight = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.macropad_macro_step_timing_delta, deltaMs),
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun MtLoopSection(
    loopEnabled: Boolean,
    loopPauseMs: Int,
    loopPauseMaxMs: Int,
    accentColor: Color,
    onLoopEnabledChange: (Boolean) -> Unit,
    onLoopPauseMsChange: (Int) -> Unit,
    onLoopPauseMaxMsChange: (Int) -> Unit,
) {
    val colors = LocalAppColors.current

    fun applyPauseDelta(deltaMs: Int) {
        val next = (loopPauseMs + deltaMs).coerceAtLeast(0)
        val nextMax = mtExpandLoopScale(loopPauseMaxMs, next)
        if (nextMax != loopPauseMaxMs) onLoopPauseMaxMsChange(nextMax)
        onLoopPauseMsChange(next)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MTE_PADDING.dp, end = MTE_PADDING.dp, bottom = MTE_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.macropad_macro_loop_toggle),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = loopEnabled,
                onCheckedChange = onLoopEnabledChange,
            )
        }

        if (loopEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.macropad_macro_loop_pause_label),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "$loopPauseMs ms",
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MtLoopPauseDeltaButton(accentColor = accentColor, deltaMs = -100) { applyPauseDelta(-100) }
                MtLoopPauseDeltaButton(accentColor = accentColor, deltaMs = -10)  { applyPauseDelta(-10) }
                MtLoopPauseDeltaButton(accentColor = accentColor, deltaMs = -1)   { applyPauseDelta(-1) }
                Slider(
                    value = loopPauseMs.toFloat(),
                    onValueChange = { onLoopPauseMsChange(it.roundToInt().coerceIn(0, loopPauseMaxMs)) },
                    valueRange = 0f..loopPauseMaxMs.toFloat(),
                    steps = ((loopPauseMaxMs / MTE_LOOP_PAUSE_SLIDER_STEP_MS) - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                    ),
                    modifier = Modifier.weight(1f),
                )
                MtLoopPauseDeltaButton(accentColor = accentColor, deltaMs = 1)    { applyPauseDelta(1) }
                MtLoopPauseDeltaButton(accentColor = accentColor, deltaMs = 10)   { applyPauseDelta(10) }
                MtLoopPauseDeltaButton(accentColor = accentColor, deltaMs = 100)  { applyPauseDelta(100) }
                MtLoopPauseDeltaButton(accentColor = accentColor, deltaMs = 1_000) { applyPauseDelta(1_000) }
            }
        }
    }
}
