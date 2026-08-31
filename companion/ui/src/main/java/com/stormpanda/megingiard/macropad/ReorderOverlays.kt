package com.stormpanda.megingiard.macropad

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadReorderDeck

@Composable
internal fun ReorderProfilesSubPage(
    profiles: List<PadProfile>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    GamepadReorderDeck(
        breadcrumbs =
            listOf(
                stringResource(R.string.quick_menu_profile_label),
                stringResource(R.string.macropad_reorder_profiles),
            ),
        items = profiles,
        itemKey = { it.id },
        itemTitle = { it.name },
        itemDescription = { profile ->
            profile.association?.packageName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.quick_menu_layouts_count, profile.layouts.size)
        },
        onReorder = { MacroPadState.reorderProfiles(it) },
        modifier = modifier,
    )
}

@Composable
internal fun ReorderLayoutsSubPage(
    layouts: List<PadLayout>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    GamepadReorderDeck(
        breadcrumbs =
            listOf(
                stringResource(R.string.macropad_editor_section_layout),
                stringResource(R.string.macropad_reorder_layouts),
            ),
        items = layouts,
        itemKey = { it.id },
        itemTitle = { it.name },
        itemDescription = { layout ->
            context.getString(R.string.quick_menu_buttons_count, layout.buttons.size)
        },
        onReorder = { MacroPadState.reorderLayouts(it) },
        modifier = modifier,
    )
}
