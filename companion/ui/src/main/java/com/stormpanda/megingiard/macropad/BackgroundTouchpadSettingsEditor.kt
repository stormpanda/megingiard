package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadConfirmDialog
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlin.math.roundToInt

private const val TAG = "BackgroundTouchpadSettingsEditor"

@Composable
internal fun LayoutTouchpadSubPageContent(
    layout: PadLayout,
    accentColor: Color,
    onConfirm: (BackgroundTouchpadConfig, disableProjection: Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val initialCfg = remember(layout) { layout.backgroundTouchpad }
    val initialHasTouchProjection =
        remember(layout) {
            layout.mirrorCutouts.any { it.touchProjectionEnabled }
        }

    var enabled by remember(layout) { mutableStateOf(initialCfg.enabled) }
    var sensitivity by remember(layout) { mutableFloatStateOf(initialCfg.sensitivity) }
    var tapToClick by remember(layout) { mutableStateOf(initialCfg.tapToClick) }
    var twoFingerTap by remember(layout) { mutableStateOf(initialCfg.twoFingerTap) }
    var threeFingerTap by remember(layout) { mutableStateOf(initialCfg.threeFingerTap) }
    var tapDrag by remember(layout) { mutableStateOf(initialCfg.tapDrag) }
    var twoFingerScroll by remember(layout) { mutableStateOf(initialCfg.twoFingerScroll) }
    var naturalScroll by remember(layout) { mutableStateOf(initialCfg.naturalScroll) }
    var scrollSpeed by remember(layout) { mutableFloatStateOf(initialCfg.scrollSpeed) }
    var hapticsEnabled by remember(layout) { mutableStateOf(initialCfg.hapticsEnabled) }

    var showConflictDialog by remember { mutableStateOf(false) }
    var touchProjectionCleared by remember { mutableStateOf(false) }

    val hasTouchProjection = initialHasTouchProjection && !touchProjectionCleared

    GamepadSectionHeader(
        text = stringResource(R.string.settings_section_master_touchpad),
        color = accentColor,
    )

    GamepadToggleCard(
        title = stringResource(R.string.layout_settings_touchpad_enable),
        description = stringResource(R.string.layout_settings_touchpad_enable_desc),
        checked = enabled,
        icon = Icons.Rounded.Mouse,
        onCheckedChange = { targetState ->
            if (targetState && hasTouchProjection) {
                showConflictDialog = true
            } else {
                enabled = targetState
            }
        },
        modifier = Modifier.firstDeckItem(),
    )

    if (hasTouchProjection) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.error.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.layout_settings_touchpad_incompatible_warning),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (enabled) {
        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_pointer_speed),
            color = accentColor,
        )

        GamepadSliderCard(
            title = stringResource(R.string.settings_touchpad_sensitivity),
            description = stringResource(R.string.settings_touchpad_sensitivity_desc),
            value = sensitivity,
            valueRange = 0.5f..3.0f,
            onValueChange = { sensitivity = it },
            valueLabel = "${(sensitivity * 10f).roundToInt() / 10f}x",
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
            checked = tapToClick,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { tapToClick = it },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_tap),
            description = stringResource(R.string.settings_touchpad_two_finger_tap_desc),
            checked = twoFingerTap,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { twoFingerTap = it },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_three_finger_tap),
            description = stringResource(R.string.settings_touchpad_three_finger_tap_desc),
            checked = threeFingerTap,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { threeFingerTap = it },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_tap_drag),
            description = stringResource(R.string.settings_touchpad_tap_drag_desc),
            checked = tapDrag,
            icon = Icons.Rounded.TouchApp,
            onCheckedChange = { tapDrag = it },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_touchpad_scroll_speed),
            color = accentColor,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_two_finger_scroll),
            description = stringResource(R.string.settings_touchpad_two_finger_scroll_desc),
            checked = twoFingerScroll,
            icon = Icons.Rounded.SwapVert,
            onCheckedChange = { twoFingerScroll = it },
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_natural_scroll),
            description = stringResource(R.string.settings_touchpad_natural_scroll_desc),
            checked = naturalScroll,
            icon = Icons.Rounded.SwapVert,
            onCheckedChange = { naturalScroll = it },
        )

        GamepadStepperCard(
            title = stringResource(R.string.settings_touchpad_scroll_speed),
            description = stringResource(R.string.settings_touchpad_scroll_speed_desc),
            valueText = "${(scrollSpeed * 10f).roundToInt() / 10f}x",
            icon = Icons.Rounded.Speed,
            onDecrement = {
                val newVal = ((scrollSpeed - 0.1f) * 10f).roundToInt() / 10f
                scrollSpeed = newVal.coerceIn(0.1f, 3.0f)
            },
            onIncrement = {
                val newVal = ((scrollSpeed + 0.1f) * 10f).roundToInt() / 10f
                scrollSpeed = newVal.coerceIn(0.1f, 3.0f)
            },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.settings_section_feedback),
            color = accentColor,
        )

        GamepadToggleCard(
            title = stringResource(R.string.settings_touchpad_haptics),
            description = stringResource(R.string.settings_touchpad_haptics_desc),
            checked = hapticsEnabled,
            icon = Icons.Rounded.Vibration,
            onCheckedChange = { hapticsEnabled = it },
        )
    }

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_done),
        description = stringResource(R.string.macropad_editor_appearance_desc),
        actionText = stringResource(R.string.macropad_editor_done),
        onClick = {
            val updated =
                initialCfg.copy(
                    enabled = enabled,
                    sensitivity = sensitivity,
                    tapToClick = tapToClick,
                    twoFingerTap = twoFingerTap,
                    threeFingerTap = threeFingerTap,
                    tapDrag = tapDrag,
                    twoFingerScroll = twoFingerScroll,
                    naturalScroll = naturalScroll,
                    scrollSpeed = scrollSpeed,
                    hapticsEnabled = hapticsEnabled,
                )
            val disableProjection = touchProjectionCleared || (enabled && initialHasTouchProjection)
            onConfirm(updated, disableProjection)
        },
    )

    if (showConflictDialog) {
        GamepadConfirmDialog(
            title = stringResource(R.string.layout_settings_touchpad_enable),
            message = stringResource(R.string.macropad_touchpad_conflict_projection_body),
            confirmText = stringResource(R.string.macropad_editor_confirm),
            cancelText = stringResource(R.string.settings_cancel),
            onConfirm = {
                touchProjectionCleared = true
                enabled = true
                showConflictDialog = false
            },
            onDismiss = { showConflictDialog = false },
        )
    }
}
