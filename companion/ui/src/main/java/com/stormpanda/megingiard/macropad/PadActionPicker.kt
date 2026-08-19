package com.stormpanda.megingiard.macropad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.GamepadChoiceCard

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
    onChange: (PadAction) -> Unit,
) {
    val profile by MacroPadState.activeProfile.collectAsState()

    val hasMacros = profile?.macros?.isNotEmpty() == true
    val currentCategory = current.toCategory()
    val currentGroup = currentCategory.group()
    val groupActions =
        currentGroup.actions().filter { category ->
            category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
        }

    LaunchedEffect(groupActions, currentCategory) {
        if (groupActions.size == 1) {
            val singleCategory = groupActions.first()
            if (currentCategory != singleCategory) {
                AppLog.d(TAG, "Only one enabled category for group $currentGroup -> auto-selecting in background: $singleCategory")
                onChange(singleCategory.defaultAction())
            }
        }
    }

    if (groupActions.size > 1) {
        val catIdx = groupActions.indexOf(currentCategory).coerceAtLeast(0)
        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_action),
            description = stringResource(R.string.macropad_editor_action_category_desc),
            selectedText = stringResource(currentCategory.labelResId()),
            icon = currentCategory.icon(),
            onPrevious = {
                val nextIdx = (catIdx - 1 + groupActions.size) % groupActions.size
                onChange(groupActions[nextIdx].defaultAction())
            },
            onNext = {
                val nextIdx = (catIdx + 1) % groupActions.size
                onChange(groupActions[nextIdx].defaultAction())
            },
        )
    }

    when (current) {
        is PadAction.KeyboardKey -> {
            KeyboardKeyPicker(current, onOpenKeyboardPicker, onChange)
        }

        is PadAction.GamepadButton -> {
            GamepadButtonPicker(current, onOpenGamepadPicker, onChange)
        }

        is PadAction.MouseButton -> {
            MouseButtonPicker(current, onOpenMousePicker)
        }

        is PadAction.Macro -> {
            MacroPicker(current, accentColor, onEditMacro, onChange)
        }

        is PadAction.AppLauncher -> {
            AppLauncherPicker(
                current = current,
                onOpenPicker = onOpenAppPicker ?: {},
            )
        }

        is PadAction.ScrollWheel,
        is PadAction.TrackpointMove,
        is PadAction.BackgroundPeek,
        is PadAction.LayoutNext,
        is PadAction.LayoutPrevious,
        is PadAction.ProfileSwitcher,
        is PadAction.MirrorPlayStop,
        is PadAction.MirrorFreeze,
        is PadAction.MirrorViewportEdit,
        is PadAction.MirrorTouchProjection,
        is PadAction.FullScreenMouse,
        is PadAction.FullScreenKeyboard,
        -> { /* no further config needed */ }
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
