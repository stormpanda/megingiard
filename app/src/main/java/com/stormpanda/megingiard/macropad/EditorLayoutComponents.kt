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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
internal fun EditorLayoutBar(
    profile:                 PadProfile,
    activeLayoutId:          String?,
    accentColor:             Color,
    onSelectLayout:          (String) -> Unit,
    onToggleEnabled:         (String, Boolean) -> Unit,
    onDeleteLayoutRequested: (PadLayout) -> Unit,
    onNewLayout:             () -> Unit,
    modifier:                Modifier = Modifier,
) {
    val canDelete = profile.layouts.size > 1
    val latestLayouts by rememberUpdatedState(profile.layouts)

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
        modifier              = modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MPE_ITEM_PADDING),
    ) {
        LazyRow(
            state                 = lazyRowState,
            modifier              = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding        = PaddingValues(vertical = 4.dp),
        ) {
            items(profile.layouts, key = { it.id }) { layout ->
                ReorderableItem(reorderState, key = layout.id) {
                    val isActive = layout.id == activeLayoutId ||
                        (activeLayoutId == null && profile.layouts.firstOrNull()?.id == layout.id)
                    LayoutChip(
                        layout       = layout,
                        isActive     = isActive,
                        canDelete    = canDelete,
                        onSelect     = { onSelectLayout(layout.id) },
                        onToggle     = { onToggleEnabled(layout.id, !layout.enabled) },
                        onDelete     = { onDeleteLayoutRequested(layout) },
                        dragModifier = Modifier.longPressDraggableHandle(),
                    )
                }
            }
        }

        IconButton(
            onClick  = onNewLayout,
            modifier = Modifier.size(MPE_GRID_TOGGLE_SIZE),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_macropad_new_layout),
                tint     = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun LayoutChip(
    layout:       PadLayout,
    isActive:     Boolean,
    canDelete:    Boolean,
    onSelect:     () -> Unit,
    onToggle:     () -> Unit,
    onDelete:     () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val chipAlpha = if (layout.enabled) 1f else 0.45f

    AppSelectableChip(
        text     = layout.name,
        selected = isActive,
        onClick  = onSelect,
        modifier = Modifier
            .alpha(chipAlpha)
            .then(dragModifier),
        trailingContent = { contentColor ->
            IconButton(
                onClick  = onToggle,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector        = if (layout.enabled) Icons.Rounded.CheckCircle
                                         else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(R.string.cd_layout_enable_toggle),
                    tint               = contentColor.copy(alpha = 0.75f),
                    modifier           = Modifier.size(14.dp),
                )
            }
            if (canDelete) {
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.macropad_editor_delete_layout),
                        tint               = contentColor.copy(alpha = 0.75f),
                        modifier           = Modifier.size(14.dp),
                    )
                }
            }
        },
    )
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
