package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.navigation.NavDestination
import com.stormpanda.megingiard.settings.SettingsCategory
import com.stormpanda.megingiard.settings.SettingsSubPage
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "QuickActionsSubPage"

@Composable
internal fun QuickActionsSubPageContent() {
    AppLog.d(TAG, "Rendering QuickActionsSubPageContent")
    val firstItemFocusRequester = remember { FocusRequester() }

    GamepadSectionHeader(
        text = stringResource(R.string.quick_actions_section_editor),
    )

    GamepadActionCard(
        title = stringResource(R.string.quick_action_edit_buttons),
        description = stringResource(R.string.quick_action_edit_buttons_desc),
        actionText = stringResource(R.string.gamepad_action_open),
        icon = Icons.Rounded.Edit,
        onClick = {
            AppStateManager.navigateTo(
                NavDestination.MacroPad(
                    section = EditorSection.BUTTONS,
                    editPositions = true,
                ),
            )
        },
        modifier = Modifier.firstDeckItem().focusRequester(firstItemFocusRequester),
    )

    GamepadActionCard(
        title = stringResource(R.string.quick_action_cutouts),
        description = stringResource(R.string.quick_action_cutouts_desc),
        actionText = stringResource(R.string.gamepad_action_open),
        icon = Icons.Rounded.Crop,
        onClick = {
            AppStateManager.navigateTo(NavDestination.CutoutLayoutEditor())
        },
    )

    GamepadSectionHeader(
        text = stringResource(R.string.quick_actions_section_preferences),
    )

    GamepadActionCard(
        title = stringResource(R.string.quick_action_custom_accent),
        description = stringResource(R.string.quick_action_custom_accent_desc),
        actionText = stringResource(R.string.gamepad_action_open),
        icon = Icons.Rounded.Palette,
        onClick = {
            AppStateManager.navigateTo(
                NavDestination.GlobalSettings(
                    category = SettingsCategory.APPEARANCE,
                    subPage = SettingsSubPage.CUSTOM_ACCENT,
                ),
            )
        },
    )

    GamepadActionCard(
        title = stringResource(R.string.quick_action_deadzones),
        description = stringResource(R.string.quick_action_deadzones_desc),
        actionText = stringResource(R.string.gamepad_action_open),
        icon = Icons.Rounded.Gamepad,
        onClick = {
            AppStateManager.navigateTo(
                NavDestination.GlobalSettings(
                    category = SettingsCategory.INPUT,
                    subPage = SettingsSubPage.DEADZONES,
                ),
            )
        },
    )

    GamepadActionCard(
        title = stringResource(R.string.quick_action_steamgriddb),
        description = stringResource(R.string.quick_action_steamgriddb_desc),
        actionText = stringResource(R.string.gamepad_action_open),
        icon = Icons.Rounded.Image,
        onClick = {
            AppStateManager.navigateTo(
                NavDestination.GlobalSettings(
                    category = SettingsCategory.GENERAL,
                    subPage = SettingsSubPage.STEAMGRIDDB_TOKEN,
                ),
            )
        },
    )

    GamepadSectionHeader(
        text = stringResource(R.string.quick_actions_section_data),
    )

    GamepadActionCard(
        title = stringResource(R.string.quick_action_backup),
        description = stringResource(R.string.quick_action_backup_desc),
        actionText = stringResource(R.string.gamepad_action_open),
        icon = Icons.Rounded.Build,
        onClick = {
            AppStateManager.navigateTo(
                NavDestination.GlobalSettings(
                    category = SettingsCategory.CONFIGURATION,
                    subPage = SettingsSubPage.CREATE_BACKUP,
                ),
            )
        },
    )

    GamepadActionCard(
        title = stringResource(R.string.quick_action_share_profile),
        description = stringResource(R.string.quick_action_share_profile_desc),
        actionText = stringResource(R.string.gamepad_action_open),
        icon = Icons.Rounded.Share,
        onClick = {
            AppStateManager.navigateTo(
                NavDestination.GlobalSettings(
                    category = SettingsCategory.CONFIGURATION,
                    subPage = SettingsSubPage.SHARE_PROFILE,
                ),
            )
        },
    )
}
