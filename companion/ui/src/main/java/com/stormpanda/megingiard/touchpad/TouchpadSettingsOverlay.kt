package com.stormpanda.megingiard.touchpad

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "TouchpadSettingsOverlay"

@Composable
fun TouchpadSettingsOverlay() {
    val colors = LocalAppColors.current

    val touchpadTapToClick by TouchpadSettings.touchpadTapToClick.collectAsState()
    val touchpadTwoFingerTap by TouchpadSettings.touchpadTwoFingerTap.collectAsState()
    val touchpadThreeFingerTap by TouchpadSettings.touchpadThreeFingerTap.collectAsState()
    val touchpadTapDrag by TouchpadSettings.touchpadTapDrag.collectAsState()
    val touchpadTwoFingerScroll by TouchpadSettings.touchpadTwoFingerScroll.collectAsState()
    val touchpadMouse45Enabled by TouchpadSettings.touchpadMouse45Enabled.collectAsState()
    val touchpadMirroringEnabled by TouchpadSettings.touchpadMirroringEnabled.collectAsState()
    val touchpadMirrorDim by TouchpadSettings.touchpadMirrorDim.collectAsState()
    val touchpadSensitivity by TouchpadSettings.touchpadSensitivity.collectAsState()
    val touchpadNaturalScroll by TouchpadSettings.touchpadNaturalScroll.collectAsState()
    val touchpadScrollSpeed by TouchpadSettings.touchpadScrollSpeed.collectAsState()
    val touchpadHapticsEnabled by TouchpadSettings.touchpadHapticsEnabled.collectAsState()

    DisposableEffect(Unit) {
        AppLog.d(TAG, "TouchpadSettingsOverlay composed")
        onDispose {
            AppLog.d(TAG, "TouchpadSettingsOverlay disposed")
        }
    }

    GamepadDeck(
        title = "",
        modifier = Modifier.fillMaxSize(),
    ) {
        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_relative_mouse),
            color = colors.accent,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_tap_to_click),
            description = stringResource(R.string.settings_touchpad_tap_to_click_desc),
            checked = touchpadTapToClick,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = TouchpadSettings::setTouchpadTapToClick,
            modifier = Modifier.firstDeckItem(),
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_tap),
            description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
            checked = touchpadTwoFingerTap,
            icon = Icons.Rounded.PanTool,
            onCheckedChange = TouchpadSettings::setTouchpadTwoFingerTap,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_three_finger_tap),
            description = stringResource(R.string.settings_touchpad_three_finger_tap_desc),
            checked = touchpadThreeFingerTap,
            icon = Icons.Rounded.PanTool,
            onCheckedChange = TouchpadSettings::setTouchpadThreeFingerTap,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_tap_drag),
            description = stringResource(R.string.settings_touchpad_tap_drag_desc),
            checked = touchpadTapDrag,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = TouchpadSettings::setTouchpadTapDrag,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_scroll),
            description = stringResource(R.string.settings_touchpad_two_finger_scroll_desc),
            checked = touchpadTwoFingerScroll,
            icon = Icons.Rounded.SwapVert,
            onCheckedChange = TouchpadSettings::setTouchpadTwoFingerScroll,
        )

        if (touchpadTwoFingerScroll) {
            GamepadToggleCard(
                title = stringResource(R.string.settings_touchpad_natural_scroll),
                description = stringResource(R.string.settings_touchpad_natural_scroll_desc),
                checked = touchpadNaturalScroll,
                icon = Icons.Rounded.SwapVert,
                onCheckedChange = TouchpadSettings::setTouchpadNaturalScroll,
            )

            GamepadStepperCard(
                title = stringResource(R.string.settings_touchpad_scroll_speed),
                description = stringResource(R.string.help_touchpad_settings_scroll_speed_desc),
                valueText = "%.1fx".format(Locale.US, touchpadScrollSpeed),
                icon = Icons.Rounded.Speed,
                onDecrement = {
                    TouchpadSettings.setTouchpadScrollSpeed((((touchpadScrollSpeed - 0.1f) * 10f).roundToInt() / 10f).coerceAtLeast(0.5f))
                },
                onIncrement = {
                    TouchpadSettings.setTouchpadScrollSpeed((((touchpadScrollSpeed + 0.1f) * 10f).roundToInt() / 10f).coerceAtMost(3.0f))
                },
            )
        }

        GamepadStepperCard(
            title = stringResource(R.string.settings_touchpad_sensitivity),
            description = stringResource(R.string.help_touchpad_settings_sensitivity_desc),
            valueText = "%.1fx".format(Locale.US, touchpadSensitivity),
            icon = Icons.Rounded.Speed,
            onDecrement = {
                TouchpadSettings.setTouchpadSensitivity((((touchpadSensitivity - 0.1f) * 10f).roundToInt() / 10f).coerceAtLeast(0.2f))
            },
            onIncrement = {
                TouchpadSettings.setTouchpadSensitivity((((touchpadSensitivity + 0.1f) * 10f).roundToInt() / 10f).coerceAtMost(3.0f))
            },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_mouse_4_5),
            description = stringResource(R.string.settings_touchpad_mouse_4_5_desc),
            checked = touchpadMouse45Enabled,
            icon = Icons.Rounded.Mouse,
            onCheckedChange = TouchpadSettings::setTouchpadMouse45Enabled,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_haptics),
            description = stringResource(R.string.settings_touchpad_haptics_desc),
            checked = touchpadHapticsEnabled,
            icon = Icons.Rounded.Vibration,
            onCheckedChange = TouchpadSettings::setTouchpadHapticsEnabled,
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_absolute_touch),
            color = colors.accent,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_mirroring),
            description = stringResource(R.string.settings_touchpad_mirroring_desc),
            checked = touchpadMirroringEnabled,
            icon = Icons.Rounded.Cast,
            onCheckedChange = TouchpadSettings::setTouchpadMirroringEnabled,
        )

        if (touchpadMirroringEnabled) {
            GamepadStepperCard(
                title = stringResource(R.string.settings_touchpad_mirror_dim),
                description = stringResource(R.string.help_touchpad_settings_mirror_dim_desc),
                valueText = "$touchpadMirrorDim%",
                icon = Icons.Rounded.BrightnessMedium,
                onDecrement = { TouchpadSettings.setTouchpadMirrorDim((touchpadMirrorDim - 10).coerceAtLeast(0)) },
                onIncrement = { TouchpadSettings.setTouchpadMirrorDim((touchpadMirrorDim + 10).coerceAtMost(90)) },
            )
        }
    }
}
