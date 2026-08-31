package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MaterialSymbol

private const val TAG = "QuickMenuMirrorCard"

private val PM_CARDS_GAP = 8.dp
private const val PM_MIRROR_CARD_WEIGHT = 2f
private const val PM_SCREENSHOT_CARD_WEIGHT = 3f
private val PM_TOP_SECTION_TITLE_SPACING = 2.dp
private val PM_TOP_CARD_H_PADDING = 8.dp
private val PM_TOP_CARD_V_PADDING = 8.dp
private val PM_MIRROR_ICON_BUTTON_HEIGHT = 34.dp
private val PM_MIRROR_LABEL_TOP_PADDING = 1.dp
private const val PM_DISABLED_ALPHA = 0.3f
private const val PM_DISABLED_LABEL_ALPHA = 0.4f

private const val SYMBOL_SPLITSCREEN_BOTTOM = "splitscreen_bottom"
private const val SYMBOL_SPLITSCREEN_TOP = "splitscreen_top"
private const val SYMBOL_SPLITSCREEN = "splitscreen"

@Composable
internal fun MirrorControlCard(
    colors: AppColors,
    isCapturing: Boolean,
    isFrozen: Boolean,
    isTopScreenshotEnabled: Boolean,
    isBottomScreenshotEnabled: Boolean,
    isBothScreenshotEnabled: Boolean,
    isCompanionHub: Boolean = false,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleFreeze: () -> Unit,
    onTakeTopScreenshot: () -> Unit,
    onTakeBottomScreenshot: () -> Unit,
    onTakeBothScreenshot: () -> Unit,
) {
    val menuBezelBrush = rememberBezelBrush()
    val isStartStopEnabled = !isCompanionHub
    val isPauseEnabled = !isCompanionHub && isCapturing
    val panelShape = RoundedCornerShape(PM_PANEL_CORNER)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PM_PANEL_H_PADDING, vertical = PM_PANEL_V_PADDING),
        horizontalArrangement = Arrangement.spacedBy(PM_CARDS_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Left card: Screen Mirroring ─────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .weight(PM_MIRROR_CARD_WEIGHT)
                    .shadow(PM_ELEVATION, panelShape)
                    .clip(panelShape)
                    .background(colors.controlOverlay)
                    .border(PM_BORDER_WIDTH, brush = menuBezelBrush, shape = panelShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { } // absorb clicks — prevent scrim dismiss
                    .padding(horizontal = PM_TOP_CARD_H_PADDING, vertical = PM_TOP_CARD_V_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel(text = stringResource(R.string.quick_menu_screen_mirroring), colors = colors)
            Spacer(Modifier.height(PM_TOP_SECTION_TITLE_SPACING))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MirrorControlIconButton(
                    icon = if (isCapturing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isCapturing) R.string.cd_stop_mirroring else R.string.cd_start_mirroring),
                    label = stringResource(if (isCapturing) R.string.mirror_control_label_stop else R.string.mirror_control_label_start),
                    tint = colors.onControlOverlay,
                    enabled = isStartStopEnabled,
                    colors = colors,
                    onClick = if (isCapturing) onStop else onStart,
                    modifier = Modifier.weight(1f),
                )
                MirrorControlIconButton(
                    icon = if (isFrozen) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(if (isFrozen) R.string.cd_unfreeze else R.string.cd_freeze),
                    label = stringResource(if (isFrozen) R.string.mirror_control_label_unfreeze else R.string.mirror_control_label_freeze),
                    tint = if (isFrozen) colors.accent else colors.onControlOverlay,
                    enabled = isPauseEnabled,
                    colors = colors,
                    onClick = onToggleFreeze,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Right card: Take Screenshot ─────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .weight(PM_SCREENSHOT_CARD_WEIGHT)
                    .shadow(PM_ELEVATION, panelShape)
                    .clip(panelShape)
                    .background(colors.controlOverlay)
                    .border(PM_BORDER_WIDTH, brush = menuBezelBrush, shape = panelShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { } // absorb clicks — prevent scrim dismiss
                    .padding(horizontal = PM_TOP_CARD_H_PADDING, vertical = PM_TOP_CARD_V_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel(text = stringResource(R.string.quick_menu_take_screenshot), colors = colors)
            Spacer(Modifier.height(PM_TOP_SECTION_TITLE_SPACING))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    Triple(SYMBOL_SPLITSCREEN_BOTTOM, R.string.cd_screenshot_top, R.string.quick_menu_screenshot_top) to
                        (isTopScreenshotEnabled to onTakeTopScreenshot),
                    Triple(SYMBOL_SPLITSCREEN_TOP, R.string.cd_screenshot_bottom, R.string.quick_menu_screenshot_bottom) to
                        (isBottomScreenshotEnabled to onTakeBottomScreenshot),
                    Triple(SYMBOL_SPLITSCREEN, R.string.cd_screenshot_both, R.string.quick_menu_screenshot_both) to
                        (isBothScreenshotEnabled to onTakeBothScreenshot),
                ).forEach { (meta, handler) ->
                    val (symbol, cdRes, labelRes) = meta
                    val (enabled, action) = handler
                    MirrorControlIconButton(
                        symbolName = symbol,
                        contentDescription = stringResource(cdRes),
                        label = stringResource(labelRes),
                        tint = colors.onControlOverlay,
                        enabled = enabled,
                        colors = colors,
                        onClick = action,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MirrorControlIconButton(
    contentDescription: String,
    label: String,
    tint: Color,
    enabled: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    symbolName: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .size(width = PM_MIRROR_BUTTON_SIZE, height = PM_MIRROR_ICON_BUTTON_HEIGHT)
                    .semantics { this.contentDescription = contentDescription },
        ) {
            if (symbolName != null) {
                MaterialSymbol(
                    name = symbolName,
                    size = PM_MIRROR_ICON_SIZE,
                    tint = if (enabled) tint else colors.onControlOverlay.copy(alpha = PM_DISABLED_ALPHA),
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) tint else colors.onControlOverlay.copy(alpha = PM_DISABLED_ALPHA),
                    modifier = Modifier.size(PM_MIRROR_ICON_SIZE),
                )
            }
        }
        Text(
            text = label,
            color = if (enabled) colors.onControlOverlay else colors.onControlOverlay.copy(alpha = PM_DISABLED_LABEL_ALPHA),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = PM_MIRROR_LABEL_TOP_PADDING),
        )
    }
}
