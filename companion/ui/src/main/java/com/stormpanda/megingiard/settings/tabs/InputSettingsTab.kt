package com.stormpanda.megingiard.settings.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt

@Composable
fun InputSettingsTab(
    gamepadSwapFaceButtons: Boolean,
    deadzoneLeft: Float,
    deadzoneRight: Float,
    onGamepadSwapFaceButtonsChange: (Boolean) -> Unit,
    onOpenDeadzones: () -> Unit,
) {
    GamepadToggleCard(
        title = stringResource(R.string.settings_gamepad_swap_face_buttons),
        description = stringResource(R.string.settings_gamepad_swap_face_buttons_desc),
        checked = gamepadSwapFaceButtons,
        icon = Icons.Rounded.SwapHoriz,
        onCheckedChange = onGamepadSwapFaceButtonsChange,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadActionCard(
        title = stringResource(R.string.privd_deadzone_title),
        description = stringResource(R.string.help_settings_deadzone_desc),
        actionText =
            stringResource(
                R.string.privd_deadzone_summary,
                (deadzoneLeft * 100f).roundToInt(),
                (deadzoneRight * 100f).roundToInt(),
            ),
        icon = Icons.Rounded.Games,
        onClick = onOpenDeadzones,
    )
}

@Composable
fun DeadzonesSubPage(
    deadzoneLeft: Float,
    deadzoneRight: Float,
    onLeftChange: (Float) -> Unit,
    onRightChange: (Float) -> Unit,
) {
    GamepadSliderCard(
        title = stringResource(R.string.privd_deadzone_left),
        description = stringResource(R.string.help_settings_deadzone_desc),
        value = deadzoneLeft,
        valueRange = 0f..0.50f,
        step = 0.01f,
        icon = Icons.Rounded.NearMe,
        valueLabel = "${(deadzoneLeft * 100f).roundToInt()}%",
        onValueChange = onLeftChange,
        modifier = Modifier.firstDeckItem(),
    )

    GamepadSliderCard(
        title = stringResource(R.string.privd_deadzone_right),
        description = stringResource(R.string.help_settings_deadzone_desc),
        value = deadzoneRight,
        valueRange = 0f..0.50f,
        step = 0.01f,
        icon = Icons.Rounded.NearMe,
        valueLabel = "${(deadzoneRight * 100f).roundToInt()}%",
        onValueChange = onRightChange,
    )
}
