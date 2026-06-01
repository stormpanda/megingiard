package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "EditorLayoutComponents"

@Composable
internal fun EditorProfileBar(
    profiles: List<PadProfile>,
    activeProfile: PadProfile?,
    accentColor: Color,
    onSelectProfile: (String) -> Unit,
    onNewProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val canDelete = profiles.size > 1

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDropdown(
            selected = activeProfile,
            options = profiles,
            optionText = { profile -> profile?.name ?: "" },
            onSelected = { profile -> if (profile != null) onSelectProfile(profile.id) },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            fillMaxWidth = true,
        )

        // Add (red plus)
        IconButton(onClick = onNewProfile) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_macropad_new_profile),
                tint = colors.error,
                modifier = Modifier.size(24.dp)
            )
        }

        // Edit
        IconButton(onClick = onEditProfile) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.macropad_editor_rename),
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Delete
        IconButton(
            onClick = onDeleteProfile,
            enabled = canDelete
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.macropad_editor_delete_profile),
                tint = if (canDelete) colors.onSurfaceSecondary else colors.onSurfaceSecondary.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun EditorLayoutDropdownBar(
    layouts: List<PadLayout>,
    activeLayout: PadLayout?,
    accentColor: Color,
    onSelectLayout: (String) -> Unit,
    onNewLayout: () -> Unit,
    onEditLayout: () -> Unit,
    onDeleteLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val canDelete = layouts.size > 1

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDropdown(
            selected = activeLayout,
            options = layouts,
            optionText = { layout ->
                if (layout != null) {
                    if (layout.enabled) layout.name else "${layout.name} (hidden)"
                } else ""
            },
            onSelected = { layout -> if (layout != null) onSelectLayout(layout.id) },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            fillMaxWidth = true,
        )

        // Add (red plus)
        IconButton(onClick = onNewLayout) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_macropad_new_layout),
                tint = colors.error,
                modifier = Modifier.size(24.dp)
            )
        }

        // Edit
        IconButton(onClick = onEditLayout) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.macropad_editor_rename),
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Delete
        IconButton(
            onClick = onDeleteLayout,
            enabled = canDelete
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.macropad_editor_delete_layout),
                tint = if (canDelete) colors.onSurfaceSecondary else colors.onSurfaceSecondary.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


@Composable
internal fun LayoutSettingsContent(
    layout:   PadLayout,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        ButtonColorStyleRow(
            label    = stringResource(R.string.macropad_editor_button_color_no_mirror),
            selected = layout.buttonColorNoMirror,
            onSelect = { style ->
                MacroPadState.updateLayout(layout.copy(buttonColorNoMirror = style))
            },
        )
        AppDivider()
        ButtonColorStyleRow(
            label    = stringResource(R.string.macropad_editor_button_color_mirror),
            selected = layout.buttonColorMirror,
            onSelect = { style ->
                MacroPadState.updateLayout(layout.copy(buttonColorMirror = style))
            },
        )
    }
}

@Composable
internal fun ButtonColorStyleRow(
    label:    String,
    selected: ButtonColorStyle,
    onSelect: (ButtonColorStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    AppSettingsRow(modifier = modifier) {
        Text(
            text     = label,
            color    = colors.onSurface,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        AppDropdown(
            selected   = selected,
            options    = ButtonColorStyle.entries,
            optionText = { style ->
                when (style) {
                    ButtonColorStyle.ACCENTED -> stringResource(R.string.macropad_editor_button_color_accented)
                    ButtonColorStyle.NEUTRAL  -> stringResource(R.string.macropad_editor_button_color_neutral)
                }
            },
            onSelected = onSelect,
        )
    }
}
