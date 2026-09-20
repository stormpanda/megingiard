package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroExecutor
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "KeyboardMacroQuickRow"

private val KMQ_ROW_HEIGHT = 36.dp
private val KMQ_CHIP_HEIGHT = 28.dp
private val KMQ_CHIP_SHAPE = RoundedCornerShape(14.dp)

@Composable
internal fun KeyboardMacroQuickRow(
    visible: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val activeProfile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    val runningMacroIds by MacroExecutor.runningMacroIds.collectAsStateWithLifecycle()
    val macros = activeProfile?.macros ?: emptyList()

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KMQ_ROW_HEIGHT)
                    .background(colors.keyboardBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (macros.isEmpty()) {
                Text(
                    text = stringResource(R.string.kb_macro_quick_row_empty),
                    color = colors.onSurfaceSecondary,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    macros.forEach { macro ->
                        val isRunning = macro.id in runningMacroIds
                        MacroQuickChip(
                            macro = macro,
                            isRunning = isRunning,
                            accentColor = accentColor,
                            onClick = {
                                if (isRunning) {
                                    AppLog.d(TAG, "Stopping macro '${macro.name}' via keyboard quick row")
                                    MacroExecutor.stop(macro.id)
                                } else {
                                    AppLog.d(TAG, "Triggering macro '${macro.name}' via keyboard quick row")
                                    MacroExecutor.execute(macro)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroQuickChip(
    macro: Macro,
    isRunning: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val bg = if (isRunning) accentColor.copy(alpha = 0.85f) else colors.surfaceVariant
    val contentColor = if (isRunning) colors.onAccent else colors.onSurface
    val borderColor = if (isRunning) accentColor else colors.divider

    Row(
        modifier =
            modifier
                .height(KMQ_CHIP_HEIGHT)
                .clip(KMQ_CHIP_SHAPE)
                .background(bg)
                .border(width = 1.dp, color = borderColor, shape = KMQ_CHIP_SHAPE)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.PlaylistPlay,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = macro.name,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
