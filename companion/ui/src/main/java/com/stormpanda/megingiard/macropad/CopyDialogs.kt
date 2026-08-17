package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "CopyDialogs"

@Composable
internal fun CopyLayoutSubPageContent(
    title: String,
    profiles: List<PadProfile>,
    excludeProfileId: String?,
    accentColor: Color,
    onSelect: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val filteredProfiles = profiles.filter { it.id != excludeProfileId }

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.macropad_editor_section_layout),
        subPageTitle = title,
        accentColor = accentColor,
    )

    if (filteredProfiles.isEmpty()) {
        Text(
            text = stringResource(R.string.macropad_copy_no_profiles_available),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filteredProfiles.forEach { profile ->
                GamepadActionCard(
                    title = profile.name,
                    description = stringResource(R.string.quick_menu_layouts_count, profile.layouts.size),
                    actionText = stringResource(R.string.gamepad_prompt_select),
                    icon = Icons.Rounded.Folder,
                    onClick = { onSelect(profile.id) },
                )
            }
        }
    }
}

@Composable
internal fun CopyButtonSubPageContent(
    title: String,
    profiles: List<PadProfile>,
    excludeLayoutId: String?,
    accentColor: Color,
    onSelect: (targetProfileId: String, targetLayoutId: String) -> Unit,
) {
    val colors = LocalAppColors.current
    val hasSelectableLayouts =
        profiles.any { profile ->
            profile.layouts.any { it.id != excludeLayoutId }
        }

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.macropad_editor_section_buttons),
        subPageTitle = title,
        accentColor = accentColor,
    )

    if (!hasSelectableLayouts) {
        Text(
            text = stringResource(R.string.macropad_copy_no_layouts_available),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profiles.forEach { profile ->
                val layouts = profile.layouts.filter { it.id != excludeLayoutId }
                if (layouts.isNotEmpty()) {
                    GamepadSectionHeader(
                        text = profile.name,
                        color = accentColor,
                    )
                    layouts.forEach { layout ->
                        GamepadActionCard(
                            title = layout.name,
                            description = stringResource(R.string.quick_menu_buttons_count, layout.buttons.size),
                            actionText = stringResource(R.string.gamepad_action_copy),
                            icon = Icons.Rounded.ContentCopy,
                            onClick = { onSelect(profile.id, layout.id) },
                        )
                    }
                }
            }
        }
    }
}
