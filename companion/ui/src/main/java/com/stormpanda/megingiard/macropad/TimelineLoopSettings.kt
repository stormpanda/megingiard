package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard

private const val TAG = "TimelineLoopSettings"
private const val MTE_PADDING = 16
internal const val MTE_LOOP_PAUSE_INIT_MAX_MS = 2_000
private const val MTE_LOOP_PAUSE_SCALE_STEP_MS = 1_000

internal fun mtExpandLoopScale(
    currentMaxMs: Int,
    requiredValueMs: Int,
): Int {
    var maxMs = currentMaxMs.coerceAtLeast(MTE_LOOP_PAUSE_SCALE_STEP_MS)
    while (requiredValueMs > maxMs) maxMs += MTE_LOOP_PAUSE_SCALE_STEP_MS
    return maxMs
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
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = MTE_PADDING.dp, end = MTE_PADDING.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GamepadToggleCard(
            title = stringResource(R.string.macropad_macro_loop_toggle),
            description = "Continuously restart execution from step 1 after sequence finishes",
            checked = loopEnabled,
            icon = Icons.Rounded.Repeat,
            onCheckedChange = onLoopEnabledChange,
        )

        if (loopEnabled) {
            GamepadStepperCard(
                title = stringResource(R.string.macropad_macro_loop_pause_label),
                description = "Delay before starting next cycle",
                valueText = "$loopPauseMs ms",
                icon = Icons.Rounded.Timer,
                onDecrement = {
                    val next = (loopPauseMs - 100).coerceAtLeast(0)
                    onLoopPauseMsChange(next)
                },
                onIncrement = {
                    val next = loopPauseMs + 100
                    val nextMax = mtExpandLoopScale(loopPauseMaxMs, next)
                    if (nextMax != loopPauseMaxMs) onLoopPauseMaxMsChange(nextMax)
                    onLoopPauseMsChange(next)
                },
            )
        }
    }
}

@Composable
internal fun MtRandomizationSection(
    randomizeEnabled: Boolean,
    randomizeRangeMs: Int,
    accentColor: Color,
    onRandomizeEnabledChange: (Boolean) -> Unit,
    onRandomizeRangeMsChange: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = MTE_PADDING.dp, end = MTE_PADDING.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GamepadToggleCard(
            title = stringResource(R.string.macropad_macro_randomize_toggle),
            description = stringResource(R.string.macropad_macro_randomize_desc),
            checked = randomizeEnabled,
            icon = Icons.Rounded.Shuffle,
            onCheckedChange = onRandomizeEnabledChange,
        )

        if (randomizeEnabled) {
            GamepadStepperCard(
                title = stringResource(R.string.macropad_macro_randomize_range_label),
                description = "Random jitter applied to start times and tap lengths",
                valueText = "±$randomizeRangeMs ms",
                icon = Icons.Rounded.Shuffle,
                onDecrement = {
                    val next = (randomizeRangeMs - 10).coerceIn(10, 100)
                    onRandomizeRangeMsChange(next)
                },
                onIncrement = {
                    val next = (randomizeRangeMs + 10).coerceIn(10, 100)
                    onRandomizeRangeMsChange(next)
                },
            )
        }
    }
}
