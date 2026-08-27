package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadTwoColumnGrid

private const val TAG = "QuickActionsDeck"

private data class QuickActionItem(
    val titleRes: Int,
    val descRes: Int,
    val actionRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun QuickActionsDeckContent(
    onNewButton: () -> Unit,
    onNewMacro: () -> Unit,
    onNewLayout: () -> Unit,
    onNewProfile: () -> Unit,
    onArrangeButtons: () -> Unit,
    onEditMirrorLayout: () -> Unit,
) {
    AppLog.d(TAG, "Rendering QuickActionsDeckContent")

    val items =
        remember(onNewButton, onNewMacro, onNewLayout, onNewProfile, onArrangeButtons, onEditMirrorLayout) {
            listOf(
                QuickActionItem(
                    titleRes = R.string.macropad_editor_add_button,
                    descRes = R.string.macropad_editor_create_button_desc,
                    actionRes = R.string.gamepad_action_create,
                    icon = Icons.Rounded.SmartButton,
                    onClick = onNewButton,
                ),
                QuickActionItem(
                    titleRes = R.string.macropad_editor_open_timeline_title,
                    descRes = R.string.macropad_editor_open_timeline_desc,
                    actionRes = R.string.gamepad_action_create,
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    onClick = onNewMacro,
                ),
                QuickActionItem(
                    titleRes = R.string.settings_macropad_new_layout,
                    descRes = R.string.macropad_editor_new_layout_desc,
                    actionRes = R.string.gamepad_action_create,
                    icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                    onClick = onNewLayout,
                ),
                QuickActionItem(
                    titleRes = R.string.settings_macropad_new_profile,
                    descRes = R.string.macropad_editor_new_profile_desc,
                    actionRes = R.string.gamepad_action_create,
                    icon = Icons.Rounded.Folder,
                    onClick = onNewProfile,
                ),
                QuickActionItem(
                    titleRes = R.string.quick_action_edit_buttons,
                    descRes = R.string.quick_action_edit_buttons_desc,
                    actionRes = R.string.gamepad_action_open,
                    icon = Icons.Rounded.OpenWith,
                    onClick = onArrangeButtons,
                ),
                QuickActionItem(
                    titleRes = R.string.mirror_editor_arrange_cutouts_title,
                    descRes = R.string.mirror_editor_arrange_cutouts_desc,
                    actionRes = R.string.gamepad_action_open,
                    icon = Icons.Rounded.Crop,
                    onClick = onEditMirrorLayout,
                ),
            )
        }

    GamepadTwoColumnGrid(
        items = items,
    ) { item, _, cardModifier ->
        GamepadActionCard(
            title = stringResource(item.titleRes),
            description = stringResource(item.descRes),
            actionText = stringResource(item.actionRes),
            icon = item.icon,
            alwaysShowFullDescription = true,
            onClick = item.onClick,
            modifier = cardModifier,
        )
    }
}
