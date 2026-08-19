package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadTwoColumnGrid

private const val TAG = "ActionGridSubPages"

internal data class ActionGridItem(
    val action: PadAction,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

@Composable
internal fun MirrorActionPickerSubPageContent(
    currentAction: PadAction,
    accentColor: Color,
    onSelectAction: (action: PadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "MirrorActionPickerSubPageContent: currentAction=$currentAction")

    val items =
        remember {
            listOf(
                ActionGridItem(
                    PadAction.MirrorPlayStop,
                    R.string.macropad_action_mirror_play_stop,
                    R.string.macropad_action_mirror_play_stop_desc,
                    Icons.Rounded.Cast,
                ),
                ActionGridItem(
                    PadAction.MirrorFreeze,
                    R.string.macropad_action_mirror_freeze,
                    R.string.macropad_action_mirror_freeze_desc,
                    Icons.Rounded.PauseCircle,
                ),
                ActionGridItem(
                    PadAction.MirrorViewportEdit,
                    R.string.macropad_action_mirror_viewport_edit,
                    R.string.macropad_action_mirror_viewport_edit_desc,
                    Icons.Rounded.CropFree,
                ),
                ActionGridItem(
                    PadAction.MirrorTouchProjection,
                    R.string.macropad_action_mirror_touch_projection,
                    R.string.macropad_action_mirror_touch_projection_desc,
                    Icons.Rounded.TouchApp,
                ),
                ActionGridItem(
                    PadAction.BackgroundPeek,
                    R.string.macropad_action_ambient_peek,
                    R.string.macropad_action_ambient_peek_desc,
                    Icons.Rounded.Visibility,
                ),
            )
        }

    GamepadTwoColumnGrid(
        items = items,
        modifier = modifier,
    ) { item, _, cardModifier ->
        val isSelected = currentAction::class == item.action::class

        GamepadActionCard(
            title = stringResource(item.titleRes),
            description = stringResource(item.descRes),
            icon = item.icon,
            actionText =
                if (isSelected) {
                    stringResource(R.string.gamepad_color_selected)
                } else {
                    stringResource(R.string.gamepad_action_select)
                },
            alwaysShowFullDescription = true,
            onClick = { onSelectAction(item.action) },
            modifier = cardModifier,
        )
    }
}

@Composable
internal fun OverlayActionPickerSubPageContent(
    currentAction: PadAction,
    accentColor: Color,
    onSelectAction: (action: PadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "OverlayActionPickerSubPageContent: currentAction=$currentAction")

    val items =
        remember {
            listOf(
                ActionGridItem(
                    PadAction.FullScreenMouse(),
                    R.string.macropad_action_fullscreen_mouse,
                    R.string.macropad_action_fullscreen_mouse_desc,
                    Icons.Rounded.Mouse,
                ),
                ActionGridItem(
                    PadAction.FullScreenKeyboard(),
                    R.string.macropad_action_fullscreen_keyboard,
                    R.string.macropad_action_fullscreen_keyboard_desc,
                    Icons.Rounded.Keyboard,
                ),
            )
        }

    GamepadTwoColumnGrid(
        items = items,
        modifier = modifier,
    ) { item, _, cardModifier ->
        val isSelected = currentAction::class == item.action::class

        GamepadActionCard(
            title = stringResource(item.titleRes),
            description = stringResource(item.descRes),
            icon = item.icon,
            actionText =
                if (isSelected) {
                    stringResource(R.string.gamepad_color_selected)
                } else {
                    stringResource(R.string.gamepad_action_select)
                },
            alwaysShowFullDescription = true,
            onClick = { onSelectAction(item.action) },
            modifier = cardModifier,
        )
    }
}

@Composable
internal fun LayoutActionPickerSubPageContent(
    currentAction: PadAction,
    accentColor: Color,
    onSelectAction: (action: PadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "LayoutActionPickerSubPageContent: currentAction=$currentAction")

    val items =
        remember {
            listOf(
                ActionGridItem(
                    PadAction.LayoutNext,
                    R.string.macropad_action_layout_next,
                    R.string.macropad_action_layout_next_desc,
                    Icons.AutoMirrored.Rounded.ArrowForward,
                ),
                ActionGridItem(
                    PadAction.LayoutPrevious,
                    R.string.macropad_action_layout_previous,
                    R.string.macropad_action_layout_previous_desc,
                    Icons.AutoMirrored.Rounded.ArrowBack,
                ),
                ActionGridItem(
                    PadAction.ProfileSwitcher,
                    R.string.macropad_action_profile_switcher,
                    R.string.macropad_action_profile_switcher_desc,
                    Icons.Rounded.SwapHoriz,
                ),
            )
        }

    GamepadTwoColumnGrid(
        items = items,
        modifier = modifier,
    ) { item, _, cardModifier ->
        val isSelected = currentAction::class == item.action::class

        GamepadActionCard(
            title = stringResource(item.titleRes),
            description = stringResource(item.descRes),
            icon = item.icon,
            actionText =
                if (isSelected) {
                    stringResource(R.string.gamepad_color_selected)
                } else {
                    stringResource(R.string.gamepad_action_select)
                },
            alwaysShowFullDescription = true,
            onClick = { onSelectAction(item.action) },
            modifier = cardModifier,
        )
    }
}
