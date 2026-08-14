package com.stormpanda.megingiard.touchpad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.RememberSettingRow
import com.stormpanda.megingiard.settings.SettingsSection
import com.stormpanda.megingiard.settings.SliderSettingRow
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.SettingsOverlayScaffold

private const val TAG = "TouchpadSettingsOverlay"

@OptIn(ExperimentalMaterial3Api::class)
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
    var showHelp by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        AppLog.d(TAG, "TouchpadSettingsOverlay composed")
        onDispose {
            AppLog.d(TAG, "TouchpadSettingsOverlay disposed")
        }
    }

    SettingsOverlayScaffold(
        title = stringResource(R.string.settings_touchpad_title),
        onBack = onBack,
        onHelpClick = { showHelp = true },
    ) {
        SettingsSection(
            title = stringResource(R.string.settings_section_relative_mouse),
            colors = colors,
        ) {
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_tap_to_click),
                description = stringResource(R.string.settings_touchpad_tap_to_click_desc),
                checked = touchpadTapToClick,
                onCheckedChange = { TouchpadSettings.setTouchpadTapToClick(it) },
            )
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_two_finger_tap),
                description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
                checked = touchpadTwoFingerTap,
                onCheckedChange = { TouchpadSettings.setTouchpadTwoFingerTap(it) },
            )
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_three_finger_tap),
                description = stringResource(R.string.settings_touchpad_three_finger_tap_desc),
                checked = touchpadThreeFingerTap,
                onCheckedChange = { TouchpadSettings.setTouchpadThreeFingerTap(it) },
            )
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_tap_drag),
                description = stringResource(R.string.settings_touchpad_tap_drag_desc),
                checked = touchpadTapDrag,
                onCheckedChange = { TouchpadSettings.setTouchpadTapDrag(it) },
            )
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_two_finger_scroll),
                description = stringResource(R.string.settings_touchpad_two_finger_scroll_desc),
                checked = touchpadTwoFingerScroll,
                onCheckedChange = { TouchpadSettings.setTouchpadTwoFingerScroll(it) },
            )
            if (touchpadTwoFingerScroll) {
                RememberSettingRow(
                    label = stringResource(R.string.settings_touchpad_natural_scroll),
                    description = stringResource(R.string.settings_touchpad_natural_scroll_desc),
                    checked = touchpadNaturalScroll,
                    onCheckedChange = { TouchpadSettings.setTouchpadNaturalScroll(it) },
                )
                SliderSettingRow(
                    label = stringResource(R.string.settings_touchpad_scroll_speed),
                    value = touchpadScrollSpeed,
                    valueRange = 0.5f..3.0f,
                    formatLabel = { String.format(java.util.Locale.US, "%.1fx", it) },
                    onValueChange = { TouchpadSettings.setTouchpadScrollSpeed(it) },
                )
            }
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_mouse_4_5),
                description = stringResource(R.string.settings_touchpad_mouse_4_5_desc),
                checked = touchpadMouse45Enabled,
                onCheckedChange = { TouchpadSettings.setTouchpadMouse45Enabled(it) },
            )
            SliderSettingRow(
                label = stringResource(R.string.settings_touchpad_sensitivity),
                value = touchpadSensitivity,
                valueRange = 0.1f..3.0f,
                formatLabel = { String.format(java.util.Locale.US, "%.1fx", it) },
                onValueChange = { TouchpadSettings.setTouchpadSensitivity(it) },
            )
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_haptics),
                description = stringResource(R.string.settings_touchpad_haptics_desc),
                checked = touchpadHapticsEnabled,
                onCheckedChange = { TouchpadSettings.setTouchpadHapticsEnabled(it) },
            )
        }

        SettingsSection(
            title = stringResource(R.string.settings_section_absolute_touch),
            colors = colors,
        ) {
            RememberSettingRow(
                label = stringResource(R.string.settings_touchpad_mirroring),
                description = stringResource(R.string.settings_touchpad_mirroring_desc),
                checked = touchpadMirroringEnabled,
                onCheckedChange = { TouchpadSettings.setTouchpadMirroringEnabled(it) },
            )
            if (touchpadMirroringEnabled) {
                SliderSettingRow(
                    label = stringResource(R.string.settings_touchpad_mirror_dim),
                    value = touchpadMirrorDim.toFloat(),
                    valueRange = 0f..90f,
                    formatLabel = { "${it.toInt()}%" },
                    onValueChange = { TouchpadSettings.setTouchpadMirrorDim(it.toInt()) },
                )
            }
        }
    }

    TouchpadSettingsHelpModal(
        visible = showHelp,
        onDismiss = { showHelp = false },
    )
}

@Composable
private fun TouchpadSettingsHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_touchpad_settings_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_touchpad_settings_intro))

        HelpSection(stringResource(R.string.settings_touchpad_use_mouse))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_use_mouse),
            description = stringResource(R.string.help_touchpad_settings_mode_desc),
        )

        HelpSection(stringResource(R.string.settings_touchpad_tap_to_click))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_tap_to_click),
            description = stringResource(R.string.help_touchpad_settings_tap_to_click_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_two_finger_tap),
            description = stringResource(R.string.help_touchpad_settings_two_finger_tap_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_three_finger_tap),
            description = stringResource(R.string.help_touchpad_settings_three_finger_tap_desc),
        )

        HelpSection(stringResource(R.string.settings_touchpad_tap_drag))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_tap_drag),
            description = stringResource(R.string.help_touchpad_settings_tap_drag_desc),
        )

        HelpSection(stringResource(R.string.settings_touchpad_two_finger_scroll))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_two_finger_scroll),
            description = stringResource(R.string.help_touchpad_settings_scroll_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_natural_scroll),
            description = stringResource(R.string.help_touchpad_settings_natural_scroll_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_scroll_speed),
            description = stringResource(R.string.help_touchpad_settings_scroll_speed_desc),
        )

        HelpSection(stringResource(R.string.settings_touchpad_mouse_4_5))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_mouse_4_5),
            description = stringResource(R.string.help_touchpad_settings_mouse_4_5_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_sensitivity),
            description = stringResource(R.string.help_touchpad_settings_sensitivity_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_haptics),
            description = stringResource(R.string.help_touchpad_settings_haptics_desc),
        )

        HelpSection(stringResource(R.string.settings_touchpad_mirroring))
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_mirroring),
            description = stringResource(R.string.help_touchpad_settings_mirroring_desc),
        )
        HelpEntry(
            label = stringResource(R.string.settings_touchpad_mirror_dim),
            description = stringResource(R.string.help_touchpad_settings_mirror_dim_desc),
        )
    }
}
