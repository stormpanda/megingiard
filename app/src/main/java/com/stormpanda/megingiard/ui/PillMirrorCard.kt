package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R

private const val TAG = "PillMirrorCard"

@Composable
internal fun MirrorControlCard(
    colors: AppColors,
    isCapturing: Boolean,
    isFrozen: Boolean,
    isViewportEditActive: Boolean,
    isTouchProjectionActive: Boolean,
    isFollowActive: Boolean,
    modifier: Modifier = Modifier,
    onBackgroundSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleFreeze: () -> Unit,
    onToggleViewportEdit: () -> Unit,
    onToggleTouchProjection: () -> Unit,
    onToggleFollow: () -> Unit,
    showLabels: Boolean,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PM_PANEL_H_PADDING, vertical = PM_PANEL_V_PADDING)
            .shadow(PM_ELEVATION, RoundedCornerShape(PM_PANEL_CORNER))
            .clip(RoundedCornerShape(PM_PANEL_CORNER))
            .background(colors.controlOverlay)
            .border(PM_BORDER_WIDTH, colors.controlOverlayBorder, RoundedCornerShape(PM_PANEL_CORNER))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { } // absorb clicks — prevent scrim dismiss
            .padding(horizontal = PM_CONTENT_PADDING, vertical = PM_MIRROR_CARD_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Background Settings button (left)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .border(PM_BORDER_WIDTH, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(PM_ACTION_BUTTON_CORNER))
                .clickable(onClick = onBackgroundSettings)
                .padding(horizontal = PM_ACTION_BUTTON_H_PADDING, vertical = PM_ACTION_BUTTON_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = stringResource(R.string.pill_menu_ambient_settings),
                color = colors.accent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.weight(1f))

        // Mirror control icon buttons (right)
        if (isCapturing) {
            MirrorControlIconButton(
                icon = Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.cd_stop_mirroring),
                label = stringResource(R.string.mirror_control_label_stop),
                tint = colors.onControlOverlay,
                enabled = true,
                showLabel = showLabels,
                colors = colors,
                onClick = onStop,
            )
        } else {
            MirrorControlIconButton(
                icon = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.cd_start_mirroring),
                label = stringResource(R.string.mirror_control_label_start),
                tint = colors.onControlOverlay,
                enabled = true,
                showLabel = showLabels,
                colors = colors,
                onClick = onStart,
            )
        }
        MirrorControlIconButton(
            icon = if (isFrozen) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = stringResource(
                if (isFrozen) R.string.cd_unfreeze else R.string.cd_freeze,
            ),
            label = stringResource(
                if (isFrozen) R.string.mirror_control_label_unfreeze else R.string.mirror_control_label_freeze,
            ),
            tint = if (isFrozen) colors.accent else colors.onControlOverlay,
            enabled = isCapturing,
            showLabel = showLabels,
            colors = colors,
            onClick = onToggleFreeze,
        )
        MirrorControlIconButton(
            icon = Icons.Rounded.CropFree,
            contentDescription = stringResource(R.string.cd_viewport_edit),
            label = stringResource(R.string.mirror_control_label_viewport),
            tint = if (isViewportEditActive) colors.accent else colors.onControlOverlay,
            enabled = isCapturing,
            showLabel = showLabels,
            colors = colors,
            onClick = onToggleViewportEdit,
        )
        MirrorControlIconButton(
            icon = Icons.Rounded.MyLocation,
            contentDescription = stringResource(
                if (isFollowActive) R.string.cd_mirror_follow_on else R.string.cd_mirror_follow_off
            ),
            label = stringResource(R.string.mirror_control_label_follow),
            tint = if (isFollowActive) colors.accent else colors.onControlOverlay,
            enabled = isCapturing && !isFrozen,
            showLabel = showLabels,
            colors = colors,
            onClick = onToggleFollow,
        )
        MirrorControlIconButton(
            icon = Icons.Rounded.TouchApp,
            contentDescription = stringResource(
                if (isTouchProjectionActive) R.string.cd_touch_projection_on
                else R.string.cd_touch_projection_off,
            ),
            label = stringResource(R.string.mirror_control_label_projection),
            tint = if (isTouchProjectionActive) colors.accent else colors.onControlOverlay,
            enabled = isCapturing,
            showLabel = showLabels,
            colors = colors,
            onClick = onToggleTouchProjection,
        )
    }
}

@Composable
private fun MirrorControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    tint: Color,
    enabled: Boolean,
    showLabel: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(if (showLabel) PM_MIRROR_LABELED_BUTTON_WIDTH else PM_MIRROR_BUTTON_SIZE),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(PM_MIRROR_BUTTON_SIZE),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else colors.onControlOverlay.copy(alpha = 0.3f),
                modifier = Modifier.size(PM_MIRROR_ICON_SIZE),
            )
        }
        if (showLabel) {
            Text(
                text = label,
                color = if (enabled) colors.onControlOverlay else colors.onControlOverlay.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
