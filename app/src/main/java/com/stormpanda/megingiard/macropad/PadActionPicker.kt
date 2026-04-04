package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.LocalAppColors

// ─────────────────────────────────────────────────────────────────────────────
// Action category enum
// ─────────────────────────────────────────────────────────────────────────────

internal enum class ActionCategory { KEYBOARD_KEY, GAMEPAD_BUTTON, MOUSE_BUTTON, SCROLL_WHEEL, TRACKPOINT }

internal fun ActionCategory.labelResId(): Int = when (this) {
    ActionCategory.KEYBOARD_KEY   -> R.string.macropad_action_keyboard_key
    ActionCategory.GAMEPAD_BUTTON -> R.string.macropad_action_gamepad_button
    ActionCategory.MOUSE_BUTTON   -> R.string.macropad_action_mouse_button
    ActionCategory.SCROLL_WHEEL   -> R.string.macropad_action_scroll_wheel
    ActionCategory.TRACKPOINT     -> R.string.macropad_action_trackpoint
}

internal fun ActionCategory.defaultAction(): PadAction = when (this) {
    ActionCategory.KEYBOARD_KEY   -> PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
    ActionCategory.GAMEPAD_BUTTON -> PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    ActionCategory.MOUSE_BUTTON   -> PadAction.MouseButton(MouseButton.LEFT)
    ActionCategory.SCROLL_WHEEL   -> PadAction.ScrollWheel
    ActionCategory.TRACKPOINT     -> PadAction.TrackpointMove()
}

// ─────────────────────────────────────────────────────────────────────────────
// PadAction display helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun PadAction.categoryResId(): Int = when (this) {
    is PadAction.KeyboardKey     -> R.string.macropad_action_keyboard_key
    is PadAction.GamepadButton   -> R.string.macropad_action_gamepad_button
    is PadAction.MouseButton     -> R.string.macropad_action_mouse_button
    is PadAction.ScrollWheel     -> R.string.macropad_action_scroll_wheel
    is PadAction.TrackpointMove  -> R.string.macropad_action_trackpoint
    is PadAction.Macro           -> R.string.macropad_action_macro
    is PadAction.MouseLeftClick  -> R.string.macropad_action_mouse_button
    is PadAction.MouseRightClick -> R.string.macropad_action_mouse_button
}

@Composable
internal fun PadAction.displayLabel(): String {
    val context = LocalContext.current
    return when (this) {
        is PadAction.KeyboardKey     -> context.getString(R.string.macropad_display_keyboard_key, label)
        is PadAction.GamepadButton   -> context.getString(R.string.macropad_display_gamepad_button, label)
        is PadAction.MouseButton     -> context.getString(R.string.macropad_display_mouse_button, button.displayLabel())
        is PadAction.ScrollWheel     -> context.getString(R.string.macropad_display_scroll_wheel)
        is PadAction.TrackpointMove  -> context.getString(R.string.macropad_display_trackpoint)
        is PadAction.Macro           -> {
            val m = this
            if (m.events.isEmpty()) context.getString(R.string.macropad_display_macro_empty)
            else context.getString(R.string.macropad_display_macro_recorded, m.events.size, m.events.last().relativeTimeMs / 1000.0f)
        }
        is PadAction.MouseLeftClick  -> context.getString(R.string.macropad_display_mouse_button, "Left")
        is PadAction.MouseRightClick -> context.getString(R.string.macropad_display_mouse_button, "Right")
    }
}

@Composable
internal fun ButtonSize.displayLabel(): String = when (this) {
    ButtonSize.SIZE_1X1 -> stringResource(R.string.macropad_button_size_1x1)
    ButtonSize.SIZE_2X1 -> stringResource(R.string.macropad_button_size_2x1)
    ButtonSize.SIZE_1X2 -> stringResource(R.string.macropad_button_size_1x2)
    ButtonSize.SIZE_2X2 -> stringResource(R.string.macropad_button_size_2x2)
}

internal fun MouseButton.displayLabel(): String = when (this) {
    MouseButton.LEFT   -> "Left"
    MouseButton.RIGHT  -> "Right"
    MouseButton.MIDDLE -> "Middle"
    MouseButton.MOUSE4 -> "Mouse 4"
    MouseButton.MOUSE5 -> "Mouse 5"
}

// ─────────────────────────────────────────────────────────────────────────────
// Action picker composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ActionPicker(
    current:        PadAction,
    accentColor:    Color,
    enableKeyboard: Boolean = true,
    enableGamepad:  Boolean = true,
    enableMouse:    Boolean = true,
    onChange:       (PadAction) -> Unit,
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    val colors           = LocalAppColors.current

    val categoryLabel = stringResource(current.categoryResId())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                .clickable { categoryExpanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(categoryLabel, color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
        }

        DropdownMenu(
            expanded         = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            ActionCategory.entries.forEach { cat ->
                val catEnabled = when (cat) {
                    ActionCategory.KEYBOARD_KEY   -> enableKeyboard
                    ActionCategory.GAMEPAD_BUTTON -> enableGamepad
                    ActionCategory.MOUSE_BUTTON,
                    ActionCategory.SCROLL_WHEEL,
                    ActionCategory.TRACKPOINT     -> enableMouse
                }
                if (catEnabled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(cat.labelResId()), color = colors.onSurface, fontSize = 14.sp) },
                        onClick = {
                            categoryExpanded = false
                            onChange(cat.defaultAction())
                        },
                    )
                }
            }
        }

        when (current) {
            is PadAction.KeyboardKey    -> KeyboardKeyPicker(current, accentColor, onChange)
            is PadAction.GamepadButton  -> GamepadButtonPicker(current, accentColor, onChange)
            is PadAction.MouseButton    -> MouseButtonPicker(current, accentColor, onChange)
            is PadAction.ScrollWheel,
            is PadAction.TrackpointMove,
            is PadAction.MouseLeftClick,
            is PadAction.MouseRightClick -> { /* no further config needed */ }
        }
    }
}

@Composable
internal fun KeyboardKeyPicker(
    current: PadAction.KeyboardKey,
    accentColor: Color,
    onChange: (PadAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors   = LocalAppColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.label, color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accentColor)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            KEYBOARD_KEY_PRESETS.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = if (code == current.keycode) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { onChange(PadAction.KeyboardKey(code, label)); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun MouseButtonPicker(
    current: PadAction.MouseButton,
    accentColor: Color,
    onChange: (PadAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors   = LocalAppColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.button.displayLabel(), color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accentColor)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            MouseButton.entries.forEach { btn ->
                DropdownMenuItem(
                    text    = { Text(btn.displayLabel(), color = if (btn == current.button) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { onChange(PadAction.MouseButton(btn)); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun GamepadButtonPicker(
    current: PadAction.GamepadButton,
    accentColor: Color,
    onChange: (PadAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors   = LocalAppColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.label, color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accentColor)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            GamepadKeycodes.PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label, color = if (preset.code == current.btnCode) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { onChange(PadAction.GamepadButton(preset.code, preset.shortLabel)); expanded = false },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyboard key preset list (common keys for MacroPad use)
// ─────────────────────────────────────────────────────────────────────────────

internal val KEYBOARD_KEY_PRESETS: List<Pair<Int, String>> = listOf(
    // ── Special / control keys ────────────────────────────────────────────────
    LinuxKeycodes.KEY_SPACE      to "Space",
    LinuxKeycodes.KEY_ENTER      to "Enter",
    LinuxKeycodes.KEY_ESC        to "Esc",
    LinuxKeycodes.KEY_TAB        to "Tab",
    LinuxKeycodes.KEY_BACKSPACE  to "Backspace",
    LinuxKeycodes.KEY_CAPSLOCK   to "CapsLock",
    LinuxKeycodes.KEY_LEFTCTRL   to "Ctrl",
    LinuxKeycodes.KEY_RIGHTCTRL  to "Ctrl R",
    LinuxKeycodes.KEY_LEFTSHIFT  to "Shift",
    LinuxKeycodes.KEY_RIGHTSHIFT to "Shift R",
    LinuxKeycodes.KEY_LEFTALT    to "Alt",
    LinuxKeycodes.KEY_RIGHTALT   to "AltGr",
    LinuxKeycodes.KEY_LEFTMETA   to "Meta / Win",
    // ── Navigation ────────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_UP         to "↑",
    LinuxKeycodes.KEY_DOWN       to "↓",
    LinuxKeycodes.KEY_LEFT       to "←",
    LinuxKeycodes.KEY_RIGHT      to "→",
    LinuxKeycodes.KEY_HOME       to "Home",
    LinuxKeycodes.KEY_END        to "End",
    LinuxKeycodes.KEY_PAGEUP     to "PgUp",
    LinuxKeycodes.KEY_PAGEDOWN   to "PgDn",
    LinuxKeycodes.KEY_INSERT     to "Insert",
    LinuxKeycodes.KEY_DELETE     to "Delete",
    LinuxKeycodes.KEY_SYSRQ      to "PrintScrn",
    // ── F-keys ────────────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_F1         to "F1",
    LinuxKeycodes.KEY_F2         to "F2",
    LinuxKeycodes.KEY_F3         to "F3",
    LinuxKeycodes.KEY_F4         to "F4",
    LinuxKeycodes.KEY_F5         to "F5",
    LinuxKeycodes.KEY_F6         to "F6",
    LinuxKeycodes.KEY_F7         to "F7",
    LinuxKeycodes.KEY_F8         to "F8",
    LinuxKeycodes.KEY_F9         to "F9",
    LinuxKeycodes.KEY_F10        to "F10",
    LinuxKeycodes.KEY_F11        to "F11",
    LinuxKeycodes.KEY_F12        to "F12",
    // ── Number row ────────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_1          to "1",
    LinuxKeycodes.KEY_2          to "2",
    LinuxKeycodes.KEY_3          to "3",
    LinuxKeycodes.KEY_4          to "4",
    LinuxKeycodes.KEY_5          to "5",
    LinuxKeycodes.KEY_6          to "6",
    LinuxKeycodes.KEY_7          to "7",
    LinuxKeycodes.KEY_8          to "8",
    LinuxKeycodes.KEY_9          to "9",
    LinuxKeycodes.KEY_0          to "0",
    LinuxKeycodes.KEY_MINUS      to "-",
    LinuxKeycodes.KEY_EQUAL      to "=",
    // ── Letters A–Z ───────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_A          to "A",
    LinuxKeycodes.KEY_B          to "B",
    LinuxKeycodes.KEY_C          to "C",
    LinuxKeycodes.KEY_D          to "D",
    LinuxKeycodes.KEY_E          to "E",
    LinuxKeycodes.KEY_F          to "F",
    LinuxKeycodes.KEY_G          to "G",
    LinuxKeycodes.KEY_H          to "H",
    LinuxKeycodes.KEY_I          to "I",
    LinuxKeycodes.KEY_J          to "J",
    LinuxKeycodes.KEY_K          to "K",
    LinuxKeycodes.KEY_L          to "L",
    LinuxKeycodes.KEY_M          to "M",
    LinuxKeycodes.KEY_N          to "N",
    LinuxKeycodes.KEY_O          to "O",
    LinuxKeycodes.KEY_P          to "P",
    LinuxKeycodes.KEY_Q          to "Q",
    LinuxKeycodes.KEY_R          to "R",
    LinuxKeycodes.KEY_S          to "S",
    LinuxKeycodes.KEY_T          to "T",
    LinuxKeycodes.KEY_U          to "U",
    LinuxKeycodes.KEY_V          to "V",
    LinuxKeycodes.KEY_W          to "W",
    LinuxKeycodes.KEY_X          to "X",
    LinuxKeycodes.KEY_Y          to "Y",
    LinuxKeycodes.KEY_Z          to "Z",
    // ── Symbols / punctuation ─────────────────────────────────────────────────
    LinuxKeycodes.KEY_GRAVE      to "`",
    LinuxKeycodes.KEY_LEFTBRACE  to "[",
    LinuxKeycodes.KEY_RIGHTBRACE to "]",
    LinuxKeycodes.KEY_BACKSLASH  to "\\",
    LinuxKeycodes.KEY_SEMICOLON  to ";",
    LinuxKeycodes.KEY_APOSTROPHE to "'",
    LinuxKeycodes.KEY_COMMA      to ",",
    LinuxKeycodes.KEY_DOT        to ".",
    LinuxKeycodes.KEY_SLASH      to "/",
    LinuxKeycodes.KEY_102ND      to "< >",
)
