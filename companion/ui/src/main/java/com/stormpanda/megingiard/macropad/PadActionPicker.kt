package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "PadActionPicker"

@Composable
internal fun ActionPicker(
    current: PadAction,
    onOpenMacroPicker: (() -> Unit)? = null,
    onOpenAppPicker: (() -> Unit)? = null,
    onOpenKeyboardPicker: () -> Unit = {},
    onOpenGamepadPicker: (slotIndex: Int) -> Unit = {},
    onOpenMousePicker: () -> Unit = {},
    onOpenMirrorPicker: () -> Unit = {},
    onOpenToolsPicker: () -> Unit = {},
    onOpenLayoutPicker: () -> Unit = {},
    onOpenMediaPicker: () -> Unit = {},
    onRecordTouchTap: () -> Unit = {},
    isFirstItem: Boolean = false,
    onChange: (PadAction) -> Unit,
) {
    val loggedOnChange: (PadAction) -> Unit = { updated ->
        AppLog.d(TAG, "ActionPicker onChange: ${current::class.simpleName} -> ${updated::class.simpleName}")
        onChange(updated)
    }
    when (current) {
        is PadAction.KeyboardKey -> {
            KeyboardKeyPicker(current, onOpenKeyboardPicker, loggedOnChange, isFirstItem = isFirstItem)
        }

        is PadAction.GamepadButton -> {
            GamepadButtonPicker(current, onOpenGamepadPicker, isFirstItem = isFirstItem)
        }

        is PadAction.MouseButton,
        is PadAction.ScrollWheel,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_mouse),
                description = stringResource(R.string.macropad_action_group_mouse_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.Mouse,
                onClick = {
                    AppLog.d(TAG, "Opening mouse action picker from ${current::class.simpleName}")
                    onOpenMousePicker()
                },
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.TrackpointMove -> {
            TrackpointPicker(
                current = current,
                onOpenPicker = onOpenMousePicker,
                onChange = loggedOnChange,
                isFirstItem = isFirstItem,
            )
        }

        is PadAction.BackgroundPeek -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_ambient_peek),
                description = stringResource(R.string.macropad_action_ambient_peek_desc),
                actionText = stringResource(R.string.macropad_action_ambient_peek),
                icon = Icons.Rounded.Layers,
                onClick = {
                    AppLog.d(TAG, "Opening mirror action picker from BackgroundPeek")
                    onOpenMirrorPicker()
                },
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.MirrorPlayStop,
        is PadAction.MirrorFreeze,
        is PadAction.MirrorViewportEdit,
        is PadAction.MirrorTouchProjection,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_mirror),
                description = stringResource(R.string.macropad_action_group_mirror_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.Cast,
                onClick = {
                    AppLog.d(TAG, "Opening mirror action picker from ${current::class.simpleName}")
                    onOpenMirrorPicker()
                },
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.FullScreenMouse -> {
            FullScreenMousePicker(
                current = current,
                onOpenPicker = onOpenToolsPicker,
                onChange = loggedOnChange,
                isFirstItem = isFirstItem,
            )
        }

        is PadAction.TouchTap -> {
            TouchTapPicker(
                current = current,
                onOpenPicker = onOpenToolsPicker,
                onRecordTap = onRecordTouchTap,
                onChange = loggedOnChange,
                isFirstItem = isFirstItem,
            )
        }

        is PadAction.FullScreenKeyboard,
        is PadAction.Screenshot,
        is PadAction.StopAllMacros,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_tools),
                description = stringResource(R.string.macropad_action_group_tools_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.Build,
                onClick = {
                    AppLog.d(TAG, "Opening tools action picker from ${current::class.simpleName}")
                    onOpenToolsPicker()
                },
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.MediaControl -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_media),
                description = stringResource(R.string.macropad_action_group_media_desc),
                actionText = current.displayLabel(),
                icon = Icons.Rounded.MusicNote,
                onClick = {
                    AppLog.d(TAG, "Opening media action picker from MediaControl(${current.mediaAction})")
                    onOpenMediaPicker()
                },
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.AppLauncher -> {
            AppLauncherPicker(
                current = current,
                onOpenPicker = onOpenAppPicker ?: {},
                isFirstItem = isFirstItem,
            )
        }

        is PadAction.LayoutNext,
        is PadAction.LayoutPrevious,
        is PadAction.ProfileSwitcher,
        is PadAction.SwitchToHub,
        -> {
            GamepadActionCard(
                title = stringResource(R.string.macropad_action_group_layout),
                description = stringResource(R.string.macropad_action_group_layout_desc),
                actionText = current.displayLabel(),
                icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                onClick = {
                    AppLog.d(TAG, "Opening layout action picker from ${current::class.simpleName}")
                    onOpenLayoutPicker()
                },
                modifier = Modifier.firstDeckItem(isFirstItem),
            )
        }

        is PadAction.SwitchToLayout -> {
            SwitchToLayoutPicker(
                current = current,
                onOpenPicker = onOpenLayoutPicker,
                onChange = loggedOnChange,
                isFirstItem = isFirstItem,
            )
        }

        is PadAction.Macro -> {
            MacroPicker(
                current = current,
                onOpenMacroPicker = onOpenMacroPicker ?: {},
                isFirstItem = isFirstItem,
            )
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
        LinuxKeycodes.KEY_FN to "Fn",
    )
