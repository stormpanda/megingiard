package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.AppSettingsRow
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.LocalAppColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TAG = "EditorLayoutComponents"

@Composable
internal fun EditorProfileChipsBar(
    profiles: List<PadProfile>,
    activeProfile: PadProfile?,
    accentColor: Color,
    onSelectProfile: (String) -> Unit,
    onEditProfile: () -> Unit,
    onReorderProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

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

        IconButton(
            onClick = onEditProfile,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.macropad_editor_rename),
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onReorderProfiles,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatListNumbered,
                contentDescription = stringResource(R.string.macropad_reorder_profiles),
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun EditorLayoutChipsBar(
    layouts: List<PadLayout>,
    activeLayout: PadLayout?,
    accentColor: Color,
    onSelectLayout: (String) -> Unit,
    onEditLayout: () -> Unit,
    onReorderLayouts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val latestLayouts by rememberUpdatedState(layouts)

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

        IconButton(
            onClick = onEditLayout,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.macropad_editor_rename),
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onReorderLayouts,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatListNumbered,
                contentDescription = stringResource(R.string.macropad_reorder_layouts),
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(20.dp)
            )
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
