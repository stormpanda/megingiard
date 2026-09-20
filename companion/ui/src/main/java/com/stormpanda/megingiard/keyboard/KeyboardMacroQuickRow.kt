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
import com.stormpanda.megingiard.macropad.hasKeyboardSteps
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "KeyboardMacroQuickRow"

private val KMQ_ROW_HEIGHT = 36.dp
private val KMQ_CHIP_HEIGHT = 28.dp
private val KMQ_CHIP_SHAPE = RoundedCornerShape(14.dp)
private val KMQ_FONT_SIZE = 11.sp
private val KMQ_CHIP_ICON_SIZE = 16.dp
private val KMQ_CHIP_BORDER_WIDTH = 1.dp
private const val KMQ_RUNNING_BG_ALPHA = 0.85f
private val KMQ_PADDING_H = 8.dp
private val KMQ_PADDING_V = 4.dp
private val KMQ_EMPTY_PADDING_START = 4.dp
private val KMQ_CHIP_PADDING_H = 10.dp
private val KMQ_CHIP_SPACING = 8.dp
private val KMQ_CHIP_CONTENT_SPACING = 6.dp

@Composable
internal fun KeyboardMacroQuickRow(
    visible: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val activeProfile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    val runningMacroIds by MacroExecutor.runningMacroIds.collectAsStateWithLifecycle()
    val allMacros = activeProfile?.macros ?: emptyList()
    val keyboardMacros = remember(allMacros) { allMacros.filter { it.hasKeyboardSteps } }

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
                    .padding(horizontal = KMQ_PADDING_H, vertical = KMQ_PADDING_V),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (keyboardMacros.isEmpty()) {
                Text(
                    text = stringResource(R.string.kb_macro_quick_row_empty),
                    color = colors.onSurfaceSecondary,
                    fontSize = KMQ_FONT_SIZE,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = KMQ_EMPTY_PADDING_START),
                )
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(KMQ_CHIP_SPACING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    keyboardMacros.forEach { macro ->
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
    val bg = if (isRunning) accentColor.copy(alpha = KMQ_RUNNING_BG_ALPHA) else colors.surfaceVariant
    val contentColor = if (isRunning) colors.onAccent else colors.onSurface
    val borderColor = if (isRunning) accentColor else colors.divider

    Row(
        modifier =
            modifier
                .height(KMQ_CHIP_HEIGHT)
                .clip(KMQ_CHIP_SHAPE)
                .background(bg)
                .border(width = KMQ_CHIP_BORDER_WIDTH, color = borderColor, shape = KMQ_CHIP_SHAPE)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = KMQ_CHIP_PADDING_H),
        horizontalArrangement = Arrangement.spacedBy(KMQ_CHIP_CONTENT_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.PlaylistPlay,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(KMQ_CHIP_ICON_SIZE),
        )
        Text(
            text = macro.name,
            color = contentColor,
            fontSize = KMQ_FONT_SIZE,
            fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
