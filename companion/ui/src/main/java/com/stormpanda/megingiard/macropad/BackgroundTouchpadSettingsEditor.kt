package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt

private const val TAG = "BackgroundTouchpadSettingsEditor"

@Composable
internal fun LayoutTouchpadSubPageContent(
    layout: PadLayout,
    accentColor: Color,
    onUpdate: (BackgroundTouchpadConfig, disableProjection: Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val cfg = layout.backgroundTouchpad
    val hasTouchProjection = layout.mirrorCutouts.any { it.touchProjectionEnabled }

    val touchpadFocusRequester = remember { FocusRequester() }
    var restoreFocusTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(restoreFocusTrigger) {
        if (restoreFocusTrigger > 0) {
            try {
                touchpadFocusRequester.requestFocus()
            } catch (_: Exception) {
                // Focus requester not attached
            }
        }
    }

    if (hasTouchProjection) {
        GamepadInfoBox(
            text = stringResource(R.string.layout_settings_touchpad_incompatible_warning),
            description = stringResource(R.string.macropad_touchpad_conflict_projection_body),
            icon = Icons.Rounded.Warning,
            iconTint = colors.error,
        )
    }

    if (!cfg.enabled && hasTouchProjection) {
        GamepadTwoStepConfirmCard(
            title = stringResource(R.string.layout_settings_touchpad_enable),
            confirmTitle = stringResource(R.string.layout_settings_touchpad_enable),
            description = stringResource(R.string.layout_settings_touchpad_enable_desc),
            confirmDescription = stringResource(R.string.macropad_touchpad_conflict_projection_body),
            actionText = stringResource(R.string.gamepad_action_enable),
            confirmActionText = stringResource(R.string.gamepad_action_confirm),
            icon = Icons.Rounded.Mouse,
            isDestructive = true,
            cardFocusRequester = touchpadFocusRequester,
            onConfirm = {
                onUpdate(cfg.copy(enabled = true), true)
                restoreFocusTrigger++
            },
            modifier = Modifier.firstDeckItem(),
        )
    } else {
        GamepadToggleCard(
            title = stringResource(R.string.layout_settings_touchpad_enable),
            description = stringResource(R.string.layout_settings_touchpad_enable_desc),
            checked = cfg.enabled,
            icon = Icons.Rounded.Mouse,
            cardFocusRequester = touchpadFocusRequester,
            onCheckedChange = { targetState ->
                onUpdate(cfg.copy(enabled = targetState), false)
            },
            modifier = Modifier.firstDeckItem(),
        )
    }

    if (cfg.enabled) {
        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_pointer_speed),
            color = accentColor,
        )

        GamepadSliderCard(
            title = stringResource(R.string.settings_touchpad_sensitivity),
            description = stringResource(R.string.settings_touchpad_sensitivity_desc),
            value = cfg.sensitivity,
            valueRange = 0.5f..3.0f,
            onValueChange = { onUpdate(cfg.copy(sensitivity = it), false) },
            valueLabel = "${(cfg.sensitivity * 10f).roundToInt() / 10f}x",
            step = 0.1f,
            icon = Icons.Rounded.Speed,
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_gestures),
            color = accentColor,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_tap_to_click),
            description = stringResource(R.string.settings_touchpad_tap_to_click_desc),
            checked = cfg.tapToClick,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { onUpdate(cfg.copy(tapToClick = it), false) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_tap),
            description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
            checked = cfg.twoFingerTap,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { onUpdate(cfg.copy(twoFingerTap = it), false) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_three_finger_tap),
            description = stringResource(R.string.settings_touchpad_three_finger_tap_desc),
            checked = cfg.threeFingerTap,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { onUpdate(cfg.copy(threeFingerTap = it), false) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_tap_drag),
            description = stringResource(R.string.settings_touchpad_tap_drag_desc),
            checked = cfg.tapDrag,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { onUpdate(cfg.copy(tapDrag = it), false) },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_scrolling),
            color = accentColor,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_scroll),
            description = stringResource(R.string.settings_touchpad_two_finger_scroll_desc),
            checked = cfg.twoFingerScroll,
            icon = Icons.Rounded.SwapVert,
            onCheckedChange = { onUpdate(cfg.copy(twoFingerScroll = it), false) },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_natural_scroll),
            description = stringResource(R.string.settings_touchpad_natural_scroll_desc),
            checked = cfg.naturalScroll,
            icon = Icons.Rounded.SwapVert,
            onCheckedChange = { onUpdate(cfg.copy(naturalScroll = it), false) },
        )

        GamepadStepperCard(
            title = stringResource(R.string.settings_touchpad_scroll_speed),
            description = stringResource(R.string.settings_touchpad_scroll_speed_desc),
            valueText = "${(cfg.scrollSpeed * 10f).roundToInt() / 10f}x",
            icon = Icons.Rounded.Speed,
            onDecrement = {
                val newVal = ((cfg.scrollSpeed - 0.1f) * 10f).roundToInt() / 10f
                onUpdate(cfg.copy(scrollSpeed = newVal.coerceIn(0.1f, 3.0f)), false)
            },
            onIncrement = {
                val newVal = ((cfg.scrollSpeed + 0.1f) * 10f).roundToInt() / 10f
                onUpdate(cfg.copy(scrollSpeed = newVal.coerceIn(0.1f, 3.0f)), false)
            },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_feedback),
            color = accentColor,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_haptics),
            description = stringResource(R.string.settings_touchpad_haptics_desc),
            checked = cfg.hapticsEnabled,
            icon = Icons.Rounded.Vibration,
            onCheckedChange = { onUpdate(cfg.copy(hapticsEnabled = it), false) },
        )
    }
}
