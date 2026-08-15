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
import com.stormpanda.megingiard.AppLog
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
            description = stringResource(R.string.macro_loop_continuous_desc),
            checked = loopEnabled,
            icon = Icons.Rounded.Repeat,
            onCheckedChange = {
                AppLog.d(TAG, "MtLoopSection: loopEnabled changed to $it")
                onLoopEnabledChange(it)
            },
        )

        if (loopEnabled) {
            GamepadStepperCard(
                title = stringResource(R.string.macropad_macro_loop_pause_label),
                description = stringResource(R.string.macro_loop_pause_desc),
                valueText = "$loopPauseMs ms",
                icon = Icons.Rounded.Timer,
                onDecrement = {
                    val next = (loopPauseMs - 100).coerceAtLeast(0)
                    AppLog.d(TAG, "MtLoopSection: loopPauseMs decremented to $next")
                    onLoopPauseMsChange(next)
                },
                onIncrement = {
                    val next = loopPauseMs + 100
                    val nextMax = mtExpandLoopScale(loopPauseMaxMs, next)
                    if (nextMax != loopPauseMaxMs) onLoopPauseMaxMsChange(nextMax)
                    AppLog.d(TAG, "MtLoopSection: loopPauseMs incremented to $next")
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
            onCheckedChange = {
                AppLog.d(TAG, "MtRandomizationSection: randomizeEnabled changed to $it")
                onRandomizeEnabledChange(it)
            },
        )

        if (randomizeEnabled) {
            GamepadStepperCard(
                title = stringResource(R.string.macropad_macro_randomize_range_label),
                description = stringResource(R.string.macro_loop_randomize_desc),
                valueText = "±$randomizeRangeMs ms",
                icon = Icons.Rounded.Shuffle,
                onDecrement = {
                    val next = (randomizeRangeMs - 10).coerceIn(10, 100)
                    AppLog.d(TAG, "MtRandomizationSection: randomizeRangeMs decremented to $next")
                    onRandomizeRangeMsChange(next)
                },
                onIncrement = {
                    val next = (randomizeRangeMs + 10).coerceIn(10, 100)
                    AppLog.d(TAG, "MtRandomizationSection: randomizeRangeMs incremented to $next")
                    onRandomizeRangeMsChange(next)
                },
            )
        }
    }
}
