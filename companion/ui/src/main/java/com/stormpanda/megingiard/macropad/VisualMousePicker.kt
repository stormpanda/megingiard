package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "VisualMousePicker"
private val VMP_GRID_SPACING = 10.dp
private val VMP_SECTION_SPACING = 12.dp

internal data class MouseButtonItem(
    val button: MouseButton,
    val titleRes: Int,
    val descRes: Int,
)

@Composable
internal fun VisualMousePicker(
    selectedButton: MouseButton,
    accentColor: Color,
    onSelectButton: (button: MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "VisualMousePicker: selectedButton=$selectedButton")

    val items =
        remember {
            listOf(
                MouseButtonItem(MouseButton.LEFT, R.string.macropad_mouse_btn_left, R.string.macropad_mouse_btn_left_desc),
                MouseButtonItem(MouseButton.RIGHT, R.string.macropad_mouse_btn_right, R.string.macropad_mouse_btn_right_desc),
                MouseButtonItem(MouseButton.MIDDLE, R.string.macropad_mouse_btn_middle, R.string.macropad_mouse_btn_middle_desc),
                MouseButtonItem(MouseButton.MOUSE4, R.string.macropad_mouse_btn_back, R.string.macropad_mouse_btn_back_desc),
                MouseButtonItem(MouseButton.MOUSE5, R.string.macropad_mouse_btn_forward, R.string.macropad_mouse_btn_forward_desc),
            )
        }

    val chunked = remember(items) { items.chunked(2) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VMP_SECTION_SPACING),
    ) {
        GamepadSectionHeader(
            text = stringResource(R.string.macropad_picker_visual_mouse_title),
            color = accentColor,
        )

        chunked.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(VMP_GRID_SPACING),
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val isSelected = item.button == selectedButton
                    val isFirstItem = rowIndex == 0 && colIndex == 0

                    GamepadActionCard(
                        title = stringResource(item.titleRes),
                        description = stringResource(item.descRes),
                        icon = Icons.Rounded.Mouse,
                        actionText =
                            if (isSelected) {
                                stringResource(R.string.gamepad_color_selected)
                            } else {
                                stringResource(R.string.gamepad_action_select)
                            },
                        alwaysShowFullDescription = true,
                        onClick = { onSelectButton(item.button) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(if (isFirstItem) Modifier.firstDeckItem() else Modifier),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
