package com.stormpanda.megingiard.macropad

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.Locale

private const val TAG = "EditorBaseComponents"

@Composable
internal fun EditorSectionHeader(@StringRes textRes: Int) {
    val colors = LocalAppColors.current
    Text(
        text     = stringResource(textRes).uppercase(Locale.ROOT),
        color    = colors.sectionHeaderColor,
        style    = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = MPE_PADDING, vertical = MPE_SECTION_HEADER_V_PADDING),
    )
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
    onManageMacros:   () -> Unit,
    onAddButton:      () -> Unit,
    onGridModeChange: () -> Unit,
    modifier:         Modifier = Modifier,
) {
    val colors   = LocalAppColors.current
    val gridIcon = when (gridMode) {
        GridMode.OFF         -> Icons.Rounded.GridOff
        GridMode.RECTANGULAR -> Icons.Rounded.Grid4x4
        GridMode.RADIAL      -> Icons.Rounded.TripOrigin
    }
    val gridTint = if (gridMode == GridMode.OFF) colors.onSurfaceSecondary else accentColor
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MPE_ITEM_PADDING),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Add Button
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_add_button),
            icon        = Icons.Rounded.Add,
            accentColor = accentColor,
            onClick     = onAddButton,
            modifier    = Modifier.weight(1f),
        )
        // Manage Macros
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_manage_macros),
            icon        = Icons.Rounded.Edit,
            accentColor = accentColor,
            onClick     = onManageMacros,
            modifier    = Modifier.weight(1f),
        )
        // Grid toggle
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_grid_toggle),
            icon        = gridIcon,
            accentColor = gridTint,
            onClick     = onGridModeChange,
            modifier    = Modifier.weight(1f),
        )
    }
}
