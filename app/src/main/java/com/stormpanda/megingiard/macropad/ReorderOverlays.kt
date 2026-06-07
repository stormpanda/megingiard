package com.stormpanda.megingiard.macropad

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppReorderOverlay

private const val TAG = "ReorderOverlays"

@Composable
internal fun ReorderProfilesOverlay(
    profiles: List<PadProfile>,
    onDone: () -> Unit,
) {
    AppReorderOverlay(
        title = stringResource(R.string.macropad_reorder_profiles),
        items = profiles,
        itemKey = { it.id },
        itemText = { it.name },
        onReorder = { MacroPadState.reorderProfiles(it) },
        onDone = onDone,
    )
}

@Composable
internal fun ReorderLayoutsOverlay(
    layouts: List<PadLayout>,
    onDone: () -> Unit,
) {
    AppReorderOverlay(
        title = stringResource(R.string.macropad_reorder_layouts),
        items = layouts,
        itemKey = { it.id },
        itemText = { if (it.enabled) it.name else stringResource(R.string.macropad_layout_name_hidden, it.name) },
        onReorder = { MacroPadState.reorderLayouts(it) },
        onDone = onDone,
    )
}
