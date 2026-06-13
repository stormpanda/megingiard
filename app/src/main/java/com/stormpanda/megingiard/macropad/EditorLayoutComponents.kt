package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.LocalAppColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TAG = "EditorLayoutComponents"

@Composable
internal fun EditorProfileChipsBar(
    profiles: List<PadProfile>,
    activeProfile: PadProfile?,
    onSelectProfile: (String) -> Unit,
    onEditProfile: () -> Unit,
    onDuplicateProfile: () -> Unit,
    onReorderProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isActive = profile.id == activeProfile?.id
                AppSelectableChip(
                    text = profile.name,
                    selected = isActive,
                    onClick = { onSelectProfile(profile.id) },
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.macropad_editor_title_edit_profile), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onEditProfile() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.macropad_duplicate_profile), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onDuplicateProfile() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.macropad_reorder_profiles), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onReorderProfiles() }
                )
            }
        }
    }
}

@Composable
internal fun EditorLayoutChipsBar(
    layouts: List<PadLayout>,
    activeLayout: PadLayout?,
    onSelectLayout: (String) -> Unit,
    onEditLayout: () -> Unit,
    onDuplicateLayout: () -> Unit,
    onCopyToProfile: () -> Unit,
    onReorderLayouts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val latestLayouts by rememberUpdatedState(layouts)
    var menuExpanded by remember { mutableStateOf(false) }

    val lazyRowState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyRowState) { from, to ->
        val fromIdx = latestLayouts.indexOfFirst { it.id == from.key as? String }
        val toIdx   = latestLayouts.indexOfFirst { it.id == to.key as? String }
        if (fromIdx >= 0 && toIdx >= 0) {
            val mutable = latestLayouts.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            MacroPadState.reorderLayouts(mutable)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            state = lazyRowState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(layouts, key = { it.id }) { layout ->
                ReorderableItem(reorderState, key = layout.id) {
                    val isActive = layout.id == activeLayout?.id
                    val text = if (layout.enabled) layout.name else stringResource(R.string.macropad_layout_name_hidden, layout.name)
                    val chipAlpha = if (layout.enabled) 1f else 0.45f
                    AppSelectableChip(
                        text = text,
                        selected = isActive,
                        onClick = { onSelectLayout(layout.id) },
                        modifier = Modifier
                            .alpha(chipAlpha)
                            .then(Modifier.longPressDraggableHandle())
                    )
                }
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.macropad_editor_title), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onEditLayout() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.macropad_duplicate_layout), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onDuplicateLayout() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.macropad_editor_copy_to_profile), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onCopyToProfile() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.macropad_reorder_layouts), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onReorderLayouts() }
                )
            }
        }
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
