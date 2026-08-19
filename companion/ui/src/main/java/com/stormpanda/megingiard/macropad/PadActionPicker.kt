package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "PadActionPicker"

@Composable
internal fun ActionPicker(
    current: PadAction,
    accentColor: Color,
    enableKeyboard: Boolean = true,
    enableGamepad: Boolean = true,
    enableMouse: Boolean = true,
    onEditMacro: ((Macro) -> Unit)? = null,
    onOpenAppPicker: (() -> Unit)? = null,
    onOpenKeyboardPicker: () -> Unit = {},
    onOpenGamepadPicker: () -> Unit = {},
    onOpenMousePicker: () -> Unit = {},
    onOpenMirrorPicker: () -> Unit = {},
    onOpenOverlayPicker: () -> Unit = {},
    onOpenLayoutPicker: () -> Unit = {},
    isFirstItem: Boolean = false,
    onChange: (PadAction) -> Unit,
) {
    when (current) {
        is PadAction.KeyboardKey -> {
            KeyboardKeyPicker(current, onOpenKeyboardPicker, onChange, isFirstItem = isFirstItem)
        }

        is PadAction.GamepadButton -> {
            GamepadButtonPicker(current, onOpenGamepadPicker, onChange, isFirstItem = isFirstItem)
        }

        is PadAction.MouseButton,
        is PadAction.ScrollWheel,
        is PadAction.TrackpointMove,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_mouse),
                description = stringResource(R.string.macropad_action_group_mouse_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.Mouse,
                onClick = onOpenMousePicker,
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.MirrorPlayStop,
        is PadAction.MirrorFreeze,
        is PadAction.MirrorViewportEdit,
        is PadAction.MirrorTouchProjection,
        is PadAction.BackgroundPeek,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_mirror),
                description = stringResource(R.string.macropad_action_group_mirror_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.Cast,
                onClick = onOpenMirrorPicker,
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.FullScreenMouse,
        is PadAction.FullScreenKeyboard,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_other),
                description = stringResource(R.string.macropad_action_group_other_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.Apps,
                onClick = onOpenOverlayPicker,
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.AppLauncher -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_other),
                description = stringResource(R.string.macropad_action_group_other_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.Apps,
                onClick = onOpenOverlayPicker,
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
            AppLauncherPicker(
                current = current,
                onOpenPicker = onOpenAppPicker ?: {},
            )
        }

        is PadAction.LayoutNext,
        is PadAction.LayoutPrevious,
        is PadAction.ProfileSwitcher,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_layout),
                description = stringResource(R.string.macropad_action_group_layout_desc),
                actionText = current.displayLabel(),
                icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                onClick = onOpenLayoutPicker,
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.Macro -> {
            MacroPicker(current, accentColor, onEditMacro, onChange, isFirstItem = isFirstItem)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modifier preset list (Ctrl/Shift/Alt/Meta — used in KeyboardKeyPicker dropdowns)
// ─────────────────────────────────────────────────────────────────────────────

internal val MODIFIER_PRESETS: List<Pair<Int, String>> =
    listOf(
        LinuxKeycodes.KEY_LEFTCTRL to "Ctrl L",
        LinuxKeycodes.KEY_RIGHTCTRL to "Ctrl R",
        LinuxKeycodes.KEY_LEFTSHIFT to "Shift L",
        LinuxKeycodes.KEY_RIGHTSHIFT to "Shift R",
        LinuxKeycodes.KEY_LEFTALT to "Alt",
        LinuxKeycodes.KEY_RIGHTALT to "AltGr",
        LinuxKeycodes.KEY_LEFTMETA to "Meta/Win",
    )
