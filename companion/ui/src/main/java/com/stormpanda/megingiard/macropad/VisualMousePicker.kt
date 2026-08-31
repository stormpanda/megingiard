package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.SwapVert
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

private const val TAG = "VisualMousePicker"

internal data class MouseActionItem(
    val action: PadAction,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

private val MOUSE_ACTION_ITEMS =
    listOf(
        MouseActionItem(
            PadAction.MouseButton(MouseButton.LEFT),
            R.string.macropad_mouse_btn_left,
            R.string.macropad_mouse_btn_left_desc,
            Icons.Rounded.Mouse,
        ),
        MouseActionItem(
            PadAction.MouseButton(MouseButton.RIGHT),
            R.string.macropad_mouse_btn_right,
            R.string.macropad_mouse_btn_right_desc,
            Icons.Rounded.Mouse,
        ),
        MouseActionItem(
            PadAction.MouseButton(MouseButton.MIDDLE),
            R.string.macropad_mouse_btn_middle,
            R.string.macropad_mouse_btn_middle_desc,
            Icons.Rounded.Mouse,
        ),
        MouseActionItem(
            PadAction.MouseButton(MouseButton.MOUSE4),
            R.string.macropad_mouse_btn_back,
            R.string.macropad_mouse_btn_back_desc,
            Icons.Rounded.Mouse,
        ),
        MouseActionItem(
            PadAction.MouseButton(MouseButton.MOUSE5),
            R.string.macropad_mouse_btn_forward,
            R.string.macropad_mouse_btn_forward_desc,
            Icons.Rounded.Mouse,
        ),
        MouseActionItem(
            PadAction.ScrollWheel,
            R.string.macropad_action_scroll_wheel,
            R.string.macropad_mouse_btn_scroll_desc,
            Icons.Rounded.SwapVert,
        ),
        MouseActionItem(
            PadAction.TrackpointMove(),
            R.string.macropad_action_trackpoint,
            R.string.macropad_mouse_btn_trackpoint_desc,
            Icons.Rounded.ControlCamera,
        ),
    )

@Composable
internal fun VisualMousePicker(
    currentAction: PadAction,
    accentColor: Color,
    onSelectAction: (action: PadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "VisualMousePicker: currentAction=$currentAction")

    GamepadTwoColumnGrid(
        items = MOUSE_ACTION_ITEMS,
        modifier = modifier,
    ) { item, _, cardModifier ->
        val isSelected =
            when (item.action) {
                is PadAction.MouseButton -> currentAction is PadAction.MouseButton && currentAction.button == item.action.button
                is PadAction.ScrollWheel -> currentAction is PadAction.ScrollWheel
                is PadAction.TrackpointMove -> currentAction is PadAction.TrackpointMove
                else -> false
            }

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
