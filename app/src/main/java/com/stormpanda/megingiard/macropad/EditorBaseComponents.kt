package com.stormpanda.megingiard.macropad

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.Locale

private const val TAG = "EditorBaseComponents"

internal val EBC_PREVIEW_DEFAULT_SIZE = 60.dp
private const val EBC_PREVIEW_BG_ALPHA = 0.25f
private const val EBC_PREVIEW_GRADIENT_SCALE = 2.8f
private val EBC_PREVIEW_ICON_SIZE = 44.dp

@Composable
internal fun EditorSectionHeader(
    @StringRes textRes: Int,
    actionIcon: ImageVector? = null,
    actionContentDescription: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = MPE_PADDING, vertical = MPE_SECTION_HEADER_V_PADDING - 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = stringResource(textRes).uppercase(Locale.ROOT),
            color    = colors.sectionHeaderColor,
            style    = MaterialTheme.typography.labelSmall,
        )
        if (actionIcon != null && onActionClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionContentDescription,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.macropad_editor_add),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun EditorActionChip(
    label:       String,
    icon:        ImageVector,
    accentColor: Color,
    onClick:     () -> Unit,
    modifier:    Modifier = Modifier,
    enabled:     Boolean = true,
) {
    val effectiveColor = if (enabled) accentColor else accentColor.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, effectiveColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = effectiveColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = effectiveColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun EditorToolbar(
    profile:          PadProfile,
    accentColor:      Color,
    gridMode:         GridMode,
    isCanvasLocked:   Boolean,
    onToggleCanvasLock: () -> Unit,
    onAddButton:      () -> Unit,
    onGridModeChange: () -> Unit,
    onManageBackground: () -> Unit,
    modifier:         Modifier = Modifier,
) {
    val gridIcon = when (gridMode) {
        GridMode.OFF         -> Icons.Rounded.GridOff
        GridMode.RECTANGULAR -> Icons.Rounded.Grid4x4
        GridMode.RADIAL      -> Icons.Rounded.TripOrigin
    }
    val gridLabel = stringResource(R.string.macropad_editor_grid_toggle)
    val buttonLabel = stringResource(R.string.macropad_editor_toolbar_button)
    val bgLabel = stringResource(R.string.layout_settings_bg_section_title)

    val lockIcon = if (isCanvasLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen
    val lockLabel = if (isCanvasLocked) stringResource(R.string.macropad_editor_unlock) else stringResource(R.string.macropad_editor_lock)

    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MPE_ITEM_PADDING),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Add Button ("Button")
        EditorActionChip(
            label       = buttonLabel,
            icon        = Icons.Rounded.Add,
            accentColor = accentColor,
            onClick     = onAddButton,
            modifier    = Modifier.weight(1f),
        )
        // Grid toggle ("Grid", accent color all the time)
        EditorActionChip(
            label       = gridLabel,
            icon        = gridIcon,
            accentColor = accentColor,
            onClick     = onGridModeChange,
            modifier    = Modifier.weight(1f),
        )
        // Unlock / Lock button
        EditorActionChip(
            label       = lockLabel,
            icon        = lockIcon,
            accentColor = accentColor,
            onClick     = onToggleCanvasLock,
            modifier    = Modifier.weight(1f),
        )
        // Background Button ("Background")
        EditorActionChip(
            label       = bgLabel,
            icon        = Icons.Rounded.Wallpaper,
            accentColor = accentColor,
            onClick     = onManageBackground,
            modifier    = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun SwordsButtonPreview(
    textColor: Color,
    borderColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = EBC_PREVIEW_DEFAULT_SIZE,
) {
    PadButtonFace(
        width = size,
        height = size,
        shape = CircleShape,
        isIconOnly = false,
        isDeviceDisabled = false,
        borderColor = borderColor,
        bgColor = bgColor,
        bgAlpha = EBC_PREVIEW_BG_ALPHA,
        gradientScale = EBC_PREVIEW_GRADIENT_SCALE,
        modifier = modifier
    ) {
        MaterialSymbol(
            name = "swords",
            size = EBC_PREVIEW_ICON_SIZE,
            tint = textColor,
            filled = true
        )
    }
}

