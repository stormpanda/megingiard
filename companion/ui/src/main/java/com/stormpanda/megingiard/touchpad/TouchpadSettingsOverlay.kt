package com.stormpanda.megingiard.touchpad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.Locale

private const val TAG = "TouchpadSettingsOverlay"

private val TS_PADDING_SCREEN = 16.dp
private val TS_SPACING_CARDS = 10.dp
private val TS_SECTION_TOP_PADDING = 10.dp

@Composable
fun TouchpadSettingsOverlay(onBack: () -> Unit) {
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .verticalScroll(rememberScrollState())
                .padding(TS_PADDING_SCREEN),
        verticalArrangement = Arrangement.spacedBy(TS_SPACING_CARDS),
    ) {
        Text(
            text = stringResource(R.string.settings_section_relative_mouse).uppercase(),
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_tap_to_click),
            description = stringResource(R.string.settings_touchpad_tap_to_click_desc),
            checked = touchpadTapToClick,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { TouchpadSettings.setTouchpadTapToClick(it) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_tap),
            description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
            checked = touchpadTwoFingerTap,
            icon = Icons.Rounded.PanTool,
            onCheckedChange = { TouchpadSettings.setTouchpadTwoFingerTap(it) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_three_finger_tap),
            description = stringResource(R.string.settings_touchpad_three_finger_tap_desc),
            checked = touchpadThreeFingerTap,
            icon = Icons.Rounded.PanTool,
            onCheckedChange = { TouchpadSettings.setTouchpadThreeFingerTap(it) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_tap_drag),
            description = stringResource(R.string.settings_touchpad_tap_drag_desc),
            checked = touchpadTapDrag,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { TouchpadSettings.setTouchpadTapDrag(it) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_scroll),
            description = stringResource(R.string.settings_touchpad_two_finger_scroll_desc),
            checked = touchpadTwoFingerScroll,
            icon = Icons.Rounded.SwapVert,
            onCheckedChange = { TouchpadSettings.setTouchpadTwoFingerScroll(it) },
        )

        if (touchpadTwoFingerScroll) {
            GamepadToggleCard(
                title = stringResource(R.string.settings_touchpad_natural_scroll),
                description = stringResource(R.string.settings_touchpad_natural_scroll_desc),
                checked = touchpadNaturalScroll,
                icon = Icons.Rounded.SwapVert,
                onCheckedChange = { TouchpadSettings.setTouchpadNaturalScroll(it) },
            )

            GamepadStepperCard(
                title = stringResource(R.string.settings_touchpad_scroll_speed),
                description = stringResource(R.string.help_touchpad_settings_scroll_speed_desc),
                valueText = "%.1fx".format(Locale.US, touchpadScrollSpeed),
                icon = Icons.Rounded.Speed,
                onDecrement = { TouchpadSettings.setTouchpadScrollSpeed((touchpadScrollSpeed - 0.2f).coerceAtLeast(0.5f)) },
                onIncrement = { TouchpadSettings.setTouchpadScrollSpeed((touchpadScrollSpeed + 0.2f).coerceAtMost(3.0f)) },
            )
        }

        GamepadStepperCard(
            title = stringResource(R.string.settings_touchpad_sensitivity),
            description = stringResource(R.string.help_touchpad_settings_sensitivity_desc),
            valueText = "%.1fx".format(Locale.US, touchpadSensitivity),
            icon = Icons.Rounded.Speed,
            onDecrement = { TouchpadSettings.setTouchpadSensitivity((touchpadSensitivity - 0.2f).coerceAtLeast(0.2f)) },
            onIncrement = { TouchpadSettings.setTouchpadSensitivity((touchpadSensitivity + 0.2f).coerceAtMost(3.0f)) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_mouse_4_5),
            description = stringResource(R.string.settings_touchpad_mouse_4_5_desc),
            checked = touchpadMouse45Enabled,
            icon = Icons.Rounded.Mouse,
            onCheckedChange = { TouchpadSettings.setTouchpadMouse45Enabled(it) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_haptics),
            description = stringResource(R.string.settings_touchpad_haptics_desc),
            checked = touchpadHapticsEnabled,
            icon = Icons.Rounded.Vibration,
            onCheckedChange = { TouchpadSettings.setTouchpadHapticsEnabled(it) },
        )

        Text(
            text = stringResource(R.string.settings_section_absolute_touch).uppercase(),
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = TS_SECTION_TOP_PADDING),
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_mirroring),
            description = stringResource(R.string.settings_touchpad_mirroring_desc),
            checked = touchpadMirroringEnabled,
            icon = Icons.Rounded.Videocam,
            onCheckedChange = { TouchpadSettings.setTouchpadMirroringEnabled(it) },
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
