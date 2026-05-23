package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "ButtonListItem"

@Composable
internal fun ButtonListItem(
    btn:                PadButton,
    accentColor:        Color,
    enableKeyboard:     Boolean,
    enableGamepad:      Boolean,
    enableMouse:        Boolean,
    isDragging:         Boolean,
    onEdit:             () -> Unit,
    onDelete:           () -> Unit,
    dragHandleModifier: Modifier,
    modifier:           Modifier = Modifier,
) {
    val colors      = LocalAppColors.current

    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val isDeviceDisabled = when (btn.action) {
        is PadAction.KeyboardKey                 -> !enableKeyboard
        is PadAction.GamepadButton               -> !enableGamepad
        is PadAction.MouseButton,
        is PadAction.ScrollWheel,
        is PadAction.TrackpointMove              -> !enableMouse
        is PadAction.Macro                       -> !enableGamepad
        is PadAction.BackgroundPeek                 -> false
        is PadAction.LayoutNext,
        is PadAction.LayoutPrevious,
        is PadAction.ProfileSwitcher,
        is PadAction.MirrorPlayStop,
        is PadAction.MirrorFreeze,
        is PadAction.MirrorViewportEdit,
        is PadAction.MirrorTouchProjection       -> false
        is PadAction.FullScreenMouse             -> !enableMouse
        is PadAction.FullScreenKeyboard          -> !enableKeyboard
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDeviceDisabled) 0.38f else 1f)
            .background(if (isDragging) colors.surfaceVariant else colors.surface)
            .clickable { onEdit() }
            .padding(start = MPE_PADDING, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shape indicator
        val chipShape = if (isTrackpoint || btn.buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(4.dp)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(chipShape)
                .background(accentColor.copy(alpha = 0.2f))
                .border(1.dp, accentColor, chipShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isTrackpoint) {
                Text("●", color = colors.onSurface, style = MaterialTheme.typography.labelSmall)
            } else {
                val iconName = btn.iconName
                if (iconName != null) {
                    MaterialSymbol(
                        name = iconName,
                        size = 18.dp,
                        tint = colors.onSurface,
                        filled = btn.iconFilled,
                    )
                } else {
                    Text(btn.label.take(2), color = colors.onSurface, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isTrackpoint) {
                val sizeLabel = when ((btn.action as PadAction.TrackpointMove).size) {
                    TrackpointSize.SMALL  -> stringResource(R.string.macropad_trackpoint_size_small)
                    TrackpointSize.MEDIUM -> stringResource(R.string.macropad_trackpoint_size_medium)
                    TrackpointSize.LARGE  -> stringResource(R.string.macropad_trackpoint_size_large)
                }
                Text(stringResource(R.string.macropad_action_trackpoint), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(sizeLabel, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            } else {
                Text(btn.label, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(btn.action.displayLabel(), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!isTrackpoint && btn.action !is PadAction.ScrollWheel) {
            Text(
                text = "${btn.buttonSize.cols}×${btn.buttonSize.rows}",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        IconButton(onClick = { onDelete() }) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_button), tint = colors.onSurfaceSecondary)
        }
        Icon(
            imageVector        = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_reorder),
            tint               = colors.onSurfaceSecondary,
            modifier           = Modifier
                .padding(horizontal = 12.dp)
                .then(dragHandleModifier),
        )
    }

}
