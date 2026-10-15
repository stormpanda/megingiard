package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.TouchpadSettings
import com.stormpanda.megingiard.touchpad.TouchpadGestureAndScrollCards
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt

private const val TAG = "BgTouchpadEditor"

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

    LaunchedEffect(layout.id) {
        AppLog.d(TAG, "LayoutTouchpadSubPageContent opened for layout: ${layout.name} (${layout.id})")
    }

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
            valueRange = TouchpadSettings.SENSITIVITY_MIN..TouchpadSettings.SENSITIVITY_MAX,
            onValueChange = { onUpdate(cfg.copy(sensitivity = it), false) },
            valueLabel = "${(cfg.sensitivity * 10f).roundToInt() / 10f}x",
            step = 0.1f,
            icon = Icons.Rounded.Speed,
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_gestures),
            color = accentColor,
        )

        TouchpadGestureAndScrollCards(
            tapToClick = cfg.tapToClick,
            onTapToClickChange = { onUpdate(cfg.copy(tapToClick = it), false) },
            twoFingerTap = cfg.twoFingerTap,
            onTwoFingerTapChange = { onUpdate(cfg.copy(twoFingerTap = it), false) },
            threeFingerTap = cfg.threeFingerTap,
            onThreeFingerTapChange = { onUpdate(cfg.copy(threeFingerTap = it), false) },
            tapDrag = cfg.tapDrag,
            onTapDragChange = { onUpdate(cfg.copy(tapDrag = it), false) },
            twoFingerScroll = cfg.twoFingerScroll,
            onTwoFingerScrollChange = { onUpdate(cfg.copy(twoFingerScroll = it), false) },
            naturalScroll = cfg.naturalScroll,
            onNaturalScrollChange = { onUpdate(cfg.copy(naturalScroll = it), false) },
            scrollSpeed = cfg.scrollSpeed,
            onScrollSpeedChange = { onUpdate(cfg.copy(scrollSpeed = it), false) },
            scrollingHeader = {
                GamepadSectionHeader(
                    text = stringResource(R.string.settings_section_scrolling),
                    color = accentColor,
                )
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
