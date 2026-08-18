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
    onChange: (PadAction) -> Unit,
) {
    val profile by MacroPadState.activeProfile.collectAsState()

    val hasMacros = profile?.macros?.isNotEmpty() == true
    val currentCategory = current.toCategory()
    val currentGroup = currentCategory.group()
    val availableGroups =
        ActionGroup.entries.filter { group ->
            group.actions().any { category ->
                category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
            }
        }
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

    val groupIdx = availableGroups.indexOf(currentGroup).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_group),
        description = stringResource(R.string.macropad_editor_action_group_desc),
        selectedText = stringResource(currentGroup.labelResId()),
        icon = currentGroup.icon(),
        onPrevious = {
            val nextIdx = (groupIdx - 1 + availableGroups.size) % availableGroups.size
            val group = availableGroups[nextIdx]
            val defaultCategory =
                group.actions().firstOrNull { category ->
                    category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
                }
            if (defaultCategory != null) {
                onChange(defaultCategory.defaultAction())
            }
        },
        onNext = {
            val nextIdx = (groupIdx + 1) % availableGroups.size
            val group = availableGroups[nextIdx]
            val defaultCategory =
                group.actions().firstOrNull { category ->
                    category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
                }
            if (defaultCategory != null) {
                onChange(defaultCategory.defaultAction())
            }
        },
    )

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
            KeyboardKeyPicker(current, onChange)
        }

        is PadAction.GamepadButton -> {
            GamepadButtonPicker(current, onChange)
        }

        is PadAction.MouseButton -> {
            MouseButtonPicker(current, onChange)
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

// ─────────────────────────────────────────────────────────────────────────────
// Keyboard key preset list (common keys for MacroPad use)
// ─────────────────────────────────────────────────────────────────────────────

internal val KEYBOARD_KEY_PRESETS: List<Pair<Int, String>> =
    listOf(
        // ── Special / control keys ────────────────────────────────────────────────
        LinuxKeycodes.KEY_SPACE to "Space",
        LinuxKeycodes.KEY_ENTER to "Enter",
        LinuxKeycodes.KEY_ESC to "Esc",
        LinuxKeycodes.KEY_TAB to "Tab",
        LinuxKeycodes.KEY_BACKSPACE to "Backspace",
        LinuxKeycodes.KEY_CAPSLOCK to "CapsLock",
        LinuxKeycodes.KEY_LEFTCTRL to "Ctrl",
        LinuxKeycodes.KEY_RIGHTCTRL to "Ctrl R",
        LinuxKeycodes.KEY_LEFTSHIFT to "Shift",
        LinuxKeycodes.KEY_RIGHTSHIFT to "Shift R",
        LinuxKeycodes.KEY_LEFTALT to "Alt",
        LinuxKeycodes.KEY_RIGHTALT to "AltGr",
        LinuxKeycodes.KEY_LEFTMETA to "Meta / Win",
        // ── Navigation ────────────────────────────────────────────────────────────
        LinuxKeycodes.KEY_UP to "↑",
        LinuxKeycodes.KEY_DOWN to "↓",
        LinuxKeycodes.KEY_LEFT to "←",
        LinuxKeycodes.KEY_RIGHT to "→",
        LinuxKeycodes.KEY_HOME to "Home",
        LinuxKeycodes.KEY_END to "End",
        LinuxKeycodes.KEY_PAGEUP to "PgUp",
        LinuxKeycodes.KEY_PAGEDOWN to "PgDn",
        LinuxKeycodes.KEY_INSERT to "Insert",
        LinuxKeycodes.KEY_DELETE to "Delete",
        LinuxKeycodes.KEY_SYSRQ to "PrintScrn",
        // ── F-keys ────────────────────────────────────────────────────────────────
        LinuxKeycodes.KEY_F1 to "F1",
        LinuxKeycodes.KEY_F2 to "F2",
        LinuxKeycodes.KEY_F3 to "F3",
        LinuxKeycodes.KEY_F4 to "F4",
        LinuxKeycodes.KEY_F5 to "F5",
        LinuxKeycodes.KEY_F6 to "F6",
        LinuxKeycodes.KEY_F7 to "F7",
        LinuxKeycodes.KEY_F8 to "F8",
        LinuxKeycodes.KEY_F9 to "F9",
        LinuxKeycodes.KEY_F10 to "F10",
        LinuxKeycodes.KEY_F11 to "F11",
        LinuxKeycodes.KEY_F12 to "F12",
        // ── Number row ────────────────────────────────────────────────────────────
        LinuxKeycodes.KEY_1 to "1",
        LinuxKeycodes.KEY_2 to "2",
        LinuxKeycodes.KEY_3 to "3",
        LinuxKeycodes.KEY_4 to "4",
        LinuxKeycodes.KEY_5 to "5",
        LinuxKeycodes.KEY_6 to "6",
        LinuxKeycodes.KEY_7 to "7",
        LinuxKeycodes.KEY_8 to "8",
        LinuxKeycodes.KEY_9 to "9",
        LinuxKeycodes.KEY_0 to "0",
        LinuxKeycodes.KEY_MINUS to "-",
        LinuxKeycodes.KEY_EQUAL to "=",
        // ── Letters A–Z ───────────────────────────────────────────────────────────
        LinuxKeycodes.KEY_A to "A",
        LinuxKeycodes.KEY_B to "B",
        LinuxKeycodes.KEY_C to "C",
        LinuxKeycodes.KEY_D to "D",
        LinuxKeycodes.KEY_E to "E",
        LinuxKeycodes.KEY_F to "F",
        LinuxKeycodes.KEY_G to "G",
        LinuxKeycodes.KEY_H to "H",
        LinuxKeycodes.KEY_I to "I",
        LinuxKeycodes.KEY_J to "J",
        LinuxKeycodes.KEY_K to "K",
        LinuxKeycodes.KEY_L to "L",
        LinuxKeycodes.KEY_M to "M",
        LinuxKeycodes.KEY_N to "N",
        LinuxKeycodes.KEY_O to "O",
        LinuxKeycodes.KEY_P to "P",
        LinuxKeycodes.KEY_Q to "Q",
        LinuxKeycodes.KEY_R to "R",
        LinuxKeycodes.KEY_S to "S",
        LinuxKeycodes.KEY_T to "T",
        LinuxKeycodes.KEY_U to "U",
        LinuxKeycodes.KEY_V to "V",
        LinuxKeycodes.KEY_W to "W",
        LinuxKeycodes.KEY_X to "X",
        LinuxKeycodes.KEY_Y to "Y",
        LinuxKeycodes.KEY_Z to "Z",
        // ── Symbols / punctuation ─────────────────────────────────────────────────
        LinuxKeycodes.KEY_GRAVE to "`",
        LinuxKeycodes.KEY_LEFTBRACE to "[",
        LinuxKeycodes.KEY_RIGHTBRACE to "]",
        LinuxKeycodes.KEY_BACKSLASH to "\\",
        LinuxKeycodes.KEY_SEMICOLON to ";",
        LinuxKeycodes.KEY_APOSTROPHE to "'",
        LinuxKeycodes.KEY_COMMA to ",",
        LinuxKeycodes.KEY_DOT to ".",
        LinuxKeycodes.KEY_SLASH to "/",
        LinuxKeycodes.KEY_102ND to "< >",
    )
