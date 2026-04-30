package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.macropad.displayShortLabel
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "PadActionPicker"

// ─────────────────────────────────────────────────────────────────────────────
// Action category enum
// ─────────────────────────────────────────────────────────────────────────────

internal enum class ActionGroup {
    KEYBOARD,
    GAMEPAD,
    MOUSE,
    MACRO,
    LAYOUT,
    MIRROR,
    OTHER,
}

internal fun ActionGroup.labelResId(): Int = when (this) {
    ActionGroup.KEYBOARD -> R.string.macropad_action_group_keyboard
    ActionGroup.GAMEPAD  -> R.string.macropad_action_group_gamepad
    ActionGroup.MOUSE    -> R.string.macropad_action_group_mouse
    ActionGroup.MACRO    -> R.string.macropad_action_group_macro
    ActionGroup.LAYOUT   -> R.string.macropad_action_group_layout
    ActionGroup.MIRROR   -> R.string.macropad_action_group_mirror
    ActionGroup.OTHER    -> R.string.macropad_action_group_other
}

internal fun ActionGroup.actions(): List<ActionCategory> = when (this) {
    ActionGroup.KEYBOARD -> listOf(ActionCategory.KEYBOARD_KEY)
    ActionGroup.GAMEPAD  -> listOf(ActionCategory.GAMEPAD_BUTTON)
    ActionGroup.MOUSE    -> listOf(
        ActionCategory.MOUSE_BUTTON,
        ActionCategory.SCROLL_WHEEL,
        ActionCategory.TRACKPOINT,
    )
    ActionGroup.MACRO    -> listOf(ActionCategory.MACRO)
    ActionGroup.LAYOUT   -> listOf(
        ActionCategory.LAYOUT_NEXT,
        ActionCategory.LAYOUT_PREVIOUS,
        ActionCategory.PROFILE_SWITCHER,
    )
    ActionGroup.MIRROR   -> listOf(
        ActionCategory.MIRROR_PLAY_STOP,
        ActionCategory.MIRROR_FREEZE,
        ActionCategory.MIRROR_VIEWPORT_EDIT,
        ActionCategory.MIRROR_TOUCH_PROJECTION,
        ActionCategory.AMBIENT_PEEK,
    )
    ActionGroup.OTHER    -> listOf(
        ActionCategory.FULLSCREEN_MOUSE,
        ActionCategory.FULLSCREEN_KEYBOARD,
    )
}

internal enum class ActionCategory {
    KEYBOARD_KEY, GAMEPAD_BUTTON, MOUSE_BUTTON, SCROLL_WHEEL, TRACKPOINT, MACRO, AMBIENT_PEEK,
    LAYOUT_NEXT, LAYOUT_PREVIOUS, PROFILE_SWITCHER,
    MIRROR_PLAY_STOP, MIRROR_FREEZE, MIRROR_VIEWPORT_EDIT, MIRROR_TOUCH_PROJECTION,
    FULLSCREEN_MOUSE, FULLSCREEN_KEYBOARD,
}

internal fun ActionCategory.labelResId(): Int = when (this) {
    ActionCategory.KEYBOARD_KEY          -> R.string.macropad_action_keyboard_key
    ActionCategory.GAMEPAD_BUTTON        -> R.string.macropad_action_gamepad_button
    ActionCategory.MOUSE_BUTTON          -> R.string.macropad_action_mouse_button
    ActionCategory.SCROLL_WHEEL          -> R.string.macropad_action_scroll_wheel
    ActionCategory.TRACKPOINT            -> R.string.macropad_action_trackpoint
    ActionCategory.MACRO                 -> R.string.macropad_action_macro
    ActionCategory.AMBIENT_PEEK          -> R.string.macropad_action_ambient_peek
    ActionCategory.LAYOUT_NEXT           -> R.string.macropad_action_layout_next
    ActionCategory.LAYOUT_PREVIOUS       -> R.string.macropad_action_layout_previous
    ActionCategory.PROFILE_SWITCHER      -> R.string.macropad_action_profile_switcher
    ActionCategory.MIRROR_PLAY_STOP      -> R.string.macropad_action_mirror_play_stop
    ActionCategory.MIRROR_FREEZE         -> R.string.macropad_action_mirror_freeze
    ActionCategory.MIRROR_VIEWPORT_EDIT  -> R.string.macropad_action_mirror_viewport_edit
    ActionCategory.MIRROR_TOUCH_PROJECTION -> R.string.macropad_action_mirror_touch_projection
    ActionCategory.FULLSCREEN_MOUSE      -> R.string.macropad_action_fullscreen_mouse
    ActionCategory.FULLSCREEN_KEYBOARD   -> R.string.macropad_action_fullscreen_keyboard
}

internal fun ActionCategory.defaultAction(): PadAction = when (this) {
    ActionCategory.KEYBOARD_KEY          -> PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
    ActionCategory.GAMEPAD_BUTTON        -> PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    ActionCategory.MOUSE_BUTTON          -> PadAction.MouseButton(MouseButton.LEFT)
    ActionCategory.SCROLL_WHEEL          -> PadAction.ScrollWheel
    ActionCategory.TRACKPOINT            -> PadAction.TrackpointMove()
    ActionCategory.MACRO                 -> PadAction.Macro(MacroPadState.activeProfile.value?.macros?.firstOrNull()?.id ?: "")
    ActionCategory.AMBIENT_PEEK          -> PadAction.AmbientPeek
    ActionCategory.LAYOUT_NEXT           -> PadAction.LayoutNext
    ActionCategory.LAYOUT_PREVIOUS       -> PadAction.LayoutPrevious
    ActionCategory.PROFILE_SWITCHER      -> PadAction.ProfileSwitcher
    ActionCategory.MIRROR_PLAY_STOP      -> PadAction.MirrorPlayStop
    ActionCategory.MIRROR_FREEZE         -> PadAction.MirrorFreeze
    ActionCategory.MIRROR_VIEWPORT_EDIT  -> PadAction.MirrorViewportEdit
    ActionCategory.MIRROR_TOUCH_PROJECTION -> PadAction.MirrorTouchProjection
    ActionCategory.FULLSCREEN_MOUSE      -> PadAction.FullScreenMouse()
    ActionCategory.FULLSCREEN_KEYBOARD   -> PadAction.FullScreenKeyboard()
}

internal fun ActionCategory.group(): ActionGroup = when (this) {
    ActionCategory.KEYBOARD_KEY           -> ActionGroup.KEYBOARD
    ActionCategory.GAMEPAD_BUTTON         -> ActionGroup.GAMEPAD
    ActionCategory.MOUSE_BUTTON,
    ActionCategory.SCROLL_WHEEL,
    ActionCategory.TRACKPOINT             -> ActionGroup.MOUSE
    ActionCategory.MACRO                  -> ActionGroup.MACRO
    ActionCategory.LAYOUT_NEXT,
    ActionCategory.LAYOUT_PREVIOUS,
    ActionCategory.PROFILE_SWITCHER       -> ActionGroup.LAYOUT
    ActionCategory.MIRROR_PLAY_STOP,
    ActionCategory.MIRROR_FREEZE,
    ActionCategory.MIRROR_VIEWPORT_EDIT,
    ActionCategory.MIRROR_TOUCH_PROJECTION,
    ActionCategory.AMBIENT_PEEK           -> ActionGroup.MIRROR
    ActionCategory.FULLSCREEN_MOUSE,
    ActionCategory.FULLSCREEN_KEYBOARD    -> ActionGroup.OTHER
}

// ─────────────────────────────────────────────────────────────────────────────
// PadAction display helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun PadAction.categoryResId(): Int = when (this) {
    is PadAction.KeyboardKey        -> R.string.macropad_action_keyboard_key
    is PadAction.GamepadButton      -> R.string.macropad_action_gamepad_button
    is PadAction.MouseButton        -> R.string.macropad_action_mouse_button
    is PadAction.ScrollWheel        -> R.string.macropad_action_scroll_wheel
    is PadAction.TrackpointMove     -> R.string.macropad_action_trackpoint
    is PadAction.Macro              -> R.string.macropad_action_macro
    is PadAction.AmbientPeek        -> R.string.macropad_action_ambient_peek
    is PadAction.LayoutNext         -> R.string.macropad_action_layout_next
    is PadAction.LayoutPrevious     -> R.string.macropad_action_layout_previous
    is PadAction.ProfileSwitcher    -> R.string.macropad_action_profile_switcher
    is PadAction.MirrorPlayStop     -> R.string.macropad_action_mirror_play_stop
    is PadAction.MirrorFreeze       -> R.string.macropad_action_mirror_freeze
    is PadAction.MirrorViewportEdit -> R.string.macropad_action_mirror_viewport_edit
    is PadAction.MirrorTouchProjection -> R.string.macropad_action_mirror_touch_projection
    is PadAction.FullScreenMouse    -> R.string.macropad_action_fullscreen_mouse
    is PadAction.FullScreenKeyboard -> R.string.macropad_action_fullscreen_keyboard
}

internal fun PadAction.toCategory(): ActionCategory = when (this) {
    is PadAction.KeyboardKey                                                   -> ActionCategory.KEYBOARD_KEY
    is PadAction.GamepadButton                                                 -> ActionCategory.GAMEPAD_BUTTON
    is PadAction.MouseButton                                                       -> ActionCategory.MOUSE_BUTTON
    is PadAction.ScrollWheel                                                   -> ActionCategory.SCROLL_WHEEL
    is PadAction.TrackpointMove                                                -> ActionCategory.TRACKPOINT
    is PadAction.Macro                                                         -> ActionCategory.MACRO
    is PadAction.AmbientPeek                                                   -> ActionCategory.AMBIENT_PEEK
    is PadAction.LayoutNext                                                    -> ActionCategory.LAYOUT_NEXT
    is PadAction.LayoutPrevious                                                -> ActionCategory.LAYOUT_PREVIOUS
    is PadAction.ProfileSwitcher                                               -> ActionCategory.PROFILE_SWITCHER
    is PadAction.MirrorPlayStop                                                -> ActionCategory.MIRROR_PLAY_STOP
    is PadAction.MirrorFreeze                                                  -> ActionCategory.MIRROR_FREEZE
    is PadAction.MirrorViewportEdit                                            -> ActionCategory.MIRROR_VIEWPORT_EDIT
    is PadAction.MirrorTouchProjection                                         -> ActionCategory.MIRROR_TOUCH_PROJECTION
    is PadAction.FullScreenMouse                                               -> ActionCategory.FULLSCREEN_MOUSE
    is PadAction.FullScreenKeyboard                                            -> ActionCategory.FULLSCREEN_KEYBOARD
}

private fun ActionCategory.isEnabled(
    enableKeyboard: Boolean,
    enableGamepad: Boolean,
    enableMouse: Boolean,
    hasMacros: Boolean,
): Boolean = when (this) {
    ActionCategory.KEYBOARD_KEY           -> enableKeyboard
    ActionCategory.GAMEPAD_BUTTON         -> enableGamepad
    ActionCategory.MOUSE_BUTTON,
    ActionCategory.SCROLL_WHEEL,
    ActionCategory.TRACKPOINT             -> enableMouse
    ActionCategory.MACRO                  -> hasMacros
    ActionCategory.AMBIENT_PEEK,
    ActionCategory.LAYOUT_NEXT,
    ActionCategory.LAYOUT_PREVIOUS,
    ActionCategory.PROFILE_SWITCHER,
    ActionCategory.MIRROR_PLAY_STOP,
    ActionCategory.MIRROR_FREEZE,
    ActionCategory.MIRROR_VIEWPORT_EDIT,
    ActionCategory.MIRROR_TOUCH_PROJECTION -> true
    ActionCategory.FULLSCREEN_MOUSE       -> enableMouse
    ActionCategory.FULLSCREEN_KEYBOARD    -> enableKeyboard
}

@Composable
internal fun PadAction.displayLabel(): String {
    val context = LocalContext.current
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
    return when (this) {
        is PadAction.KeyboardKey -> {
            val modLabel = if (modifiers.isEmpty()) label else {
                val modNames = modifiers.mapNotNull { code ->
                    MODIFIER_PRESETS.firstOrNull { it.first == code }?.second
                }.joinToString("+")
                "$modNames+$label"
            }
            context.getString(R.string.macropad_display_keyboard_key, modLabel)
        }
        is PadAction.GamepadButton -> {
            val primaryLabel = GamepadKeycodes.PRESETS
                .firstOrNull { it.code == btnCode }
                ?.displayShortLabel(swapFaceButtons)
                ?: label
            val comboLabel = if (extraBtnCodes.isEmpty()) primaryLabel else {
                val extraNames = extraBtnCodes.mapNotNull { code ->
                    GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.displayShortLabel(swapFaceButtons)
                }.joinToString("+")
                "$primaryLabel+$extraNames"
            }
            context.getString(R.string.macropad_display_gamepad_button, comboLabel)
        }
        is PadAction.MouseButton     -> context.getString(R.string.macropad_display_mouse_button, button.displayLabel())
        is PadAction.ScrollWheel     -> context.getString(R.string.macropad_display_scroll_wheel)
        is PadAction.TrackpointMove  -> context.getString(R.string.macropad_display_trackpoint)
        is PadAction.Macro           -> {
            val macroName = MacroPadState.activeProfile.value?.macros?.firstOrNull { it.id == macroId }?.name ?: macroId
            context.getString(R.string.macropad_display_macro, macroName)
        }
        is PadAction.AmbientPeek     -> context.getString(R.string.macropad_action_ambient_peek)
        is PadAction.LayoutNext         -> context.getString(R.string.macropad_action_layout_next)
        is PadAction.LayoutPrevious     -> context.getString(R.string.macropad_action_layout_previous)
        is PadAction.ProfileSwitcher    -> context.getString(R.string.macropad_action_profile_switcher)
        is PadAction.MirrorPlayStop     -> context.getString(R.string.macropad_action_mirror_play_stop)
        is PadAction.MirrorFreeze       -> context.getString(R.string.macropad_action_mirror_freeze)
        is PadAction.MirrorViewportEdit -> context.getString(R.string.macropad_action_mirror_viewport_edit)
        is PadAction.MirrorTouchProjection -> context.getString(R.string.macropad_action_mirror_touch_projection)
        is PadAction.FullScreenMouse    -> context.getString(R.string.macropad_action_fullscreen_mouse)
        is PadAction.FullScreenKeyboard -> context.getString(R.string.macropad_action_fullscreen_keyboard)
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

internal fun gamepadCodeDisplayShortLabel(code: Int, swapFaceButtons: Boolean): String {
    return when {
        swapFaceButtons && code == GamepadKeycodes.BTN_SOUTH -> "B"
        swapFaceButtons && code == GamepadKeycodes.BTN_EAST  -> "A"
        swapFaceButtons && code == GamepadKeycodes.BTN_NORTH -> "X"
        swapFaceButtons && code == GamepadKeycodes.BTN_WEST  -> "Y"
        else -> GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.shortLabel ?: code.toString()
    }
}

@Composable
internal fun gamepadCodeDisplayLabel(code: Int, swapFaceButtons: Boolean): String {
    val primary = gamepadCodeDisplayShortLabel(code, swapFaceButtons)
    return when (code) {
        GamepadKeycodes.BTN_SOUTH -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_cross),
            stringResource(R.string.macropad_gamepad_position_south),
        )

        GamepadKeycodes.BTN_EAST -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_circle),
            stringResource(R.string.macropad_gamepad_position_east),
        )

        GamepadKeycodes.BTN_NORTH -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_triangle),
            stringResource(R.string.macropad_gamepad_position_north),
        )

        GamepadKeycodes.BTN_WEST -> stringResource(
            R.string.macropad_gamepad_face_label_template,
            primary,
            stringResource(R.string.macropad_gamepad_symbol_square),
            stringResource(R.string.macropad_gamepad_position_west),
        )

        else -> GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.label ?: code.toString()
    }
}

@Composable
internal fun GamepadKeycodes.GamepadButtonPreset.localizedDisplayLabel(swapFaceButtons: Boolean): String {
    return gamepadCodeDisplayLabel(code, swapFaceButtons)
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
    onEditMacro:    ((Macro) -> Unit)? = null,
    onChange:       (PadAction) -> Unit,
) {
    var groupExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val profile by MacroPadState.activeProfile.collectAsState()

    val hasMacros = profile?.macros?.isNotEmpty() == true
    val currentCategory = current.toCategory()
    val currentGroup = currentCategory.group()
    val currentActionLabel = stringResource(current.categoryResId())
    val currentGroupLabel = stringResource(currentGroup.labelResId())
    val availableGroups = ActionGroup.entries.filter { group ->
        group.actions().any { category ->
            category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
        }
    }
    val groupActions = currentGroup.actions().filter { category ->
        category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.macropad_picker_label_group),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelSmall,
        )

        // Group dropdown
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = availableGroups.isNotEmpty()) { groupExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    currentGroupLabel,
                    color = if (availableGroups.isEmpty()) colors.onSurfaceSecondary else colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (availableGroups.isNotEmpty()) {
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
                }
            }

            DropdownMenu(
                expanded         = groupExpanded,
                onDismissRequest = { groupExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                availableGroups.forEach { group ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(group.labelResId()),
                                color = if (group == currentGroup) accentColor else colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            groupExpanded = false
                            val defaultCategory = group.actions().firstOrNull { category ->
                                category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
                            }
                            if (defaultCategory != null) {
                                onChange(defaultCategory.defaultAction())
                            }
                        },
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.macropad_editor_action),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelSmall,
        )

        // Action dropdown (within selected group)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = groupActions.isNotEmpty()) { actionExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    currentActionLabel,
                    color = if (groupActions.isEmpty()) colors.onSurfaceSecondary else colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (groupActions.isNotEmpty()) {
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
                }
            }

            DropdownMenu(
                expanded         = actionExpanded,
                onDismissRequest = { actionExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                groupActions.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(category.labelResId()),
                                color = if (category == currentCategory) accentColor else colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            actionExpanded = false
                            onChange(category.defaultAction())
                        },
                    )
                }
            }
        }

        when (current) {
            is PadAction.KeyboardKey    -> KeyboardKeyPicker(current, accentColor, onChange)
            is PadAction.GamepadButton  -> GamepadButtonPicker(current, accentColor, onChange)
            is PadAction.MouseButton    -> MouseButtonPicker(current, accentColor, onChange)
            is PadAction.Macro          -> MacroPicker(current, accentColor, onEditMacro, onChange)
            is PadAction.ScrollWheel,
            is PadAction.TrackpointMove,
            is PadAction.AmbientPeek,
            is PadAction.LayoutNext,
            is PadAction.LayoutPrevious,
            is PadAction.ProfileSwitcher,
            is PadAction.MirrorPlayStop,
            is PadAction.MirrorFreeze,
            is PadAction.MirrorViewportEdit,
            is PadAction.MirrorTouchProjection,
            is PadAction.FullScreenMouse,
            is PadAction.FullScreenKeyboard -> { /* no further config needed */ }
        }
    }
}

@Composable
internal fun KeyboardKeyPicker(
    current: PadAction.KeyboardKey,
    accentColor: Color,
    onChange: (PadAction) -> Unit,
) {
    var baseExpanded by remember { mutableStateOf(false) }
    var mod1Expanded by remember { mutableStateOf(false) }
    var mod2Expanded by remember { mutableStateOf(false) }
    var mod1         by remember(current.modifiers) { mutableStateOf(current.modifiers.getOrNull(0)) }
    var mod2         by remember(current.modifiers) { mutableStateOf(current.modifiers.getOrNull(1)) }
    val colors       = LocalAppColors.current
    val noneLabel    = stringResource(R.string.macropad_modifier_none)

    fun emitChange(keycode: Int, label: String, newMod1: Int?, newMod2: Int?) {
        onChange(PadAction.KeyboardKey(keycode, label, listOfNotNull(newMod1, newMod2)))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── Base key ──────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_key), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { baseExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(current.label, color = accentColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
                }
                DropdownMenu(
                    expanded         = baseExpanded,
                    onDismissRequest = { baseExpanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    KEYBOARD_KEY_PRESETS.forEach { (code, label) ->
                        DropdownMenuItem(
                            text    = { Text(label, color = if (code == current.keycode) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { emitChange(code, label, mod1, mod2); baseExpanded = false },
                        )
                    }
                }
            }
        }

        // ── Modifier 1 ──────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_mod_1), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            Box {
                val mod1Label = mod1?.let { code -> MODIFIER_PRESETS.firstOrNull { it.first == code }?.second } ?: noneLabel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (mod1 == null) colors.accentBorder else accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { mod1Expanded = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mod1Label, color = if (mod1 == null) colors.onSurfaceSecondary else accentColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (mod1 == null) colors.onSurfaceSecondary else accentColor)
                }
                DropdownMenu(
                    expanded         = mod1Expanded,
                    onDismissRequest = { mod1Expanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    DropdownMenuItem(
                        text    = { Text(noneLabel, color = if (mod1 == null) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { mod1 = null; emitChange(current.keycode, current.label, null, mod2); mod1Expanded = false },
                    )
                    MODIFIER_PRESETS.forEach { (code, label) ->
                        if (code != mod2) {
                            DropdownMenuItem(
                                text    = { Text(label, color = if (mod1 == code) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { mod1 = code; emitChange(current.keycode, current.label, code, mod2); mod1Expanded = false },
                            )
                        }
                    }
                }
            }
        }

        // ── Modifier 2 ──────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_mod_2), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            Box {
                val mod2Label = mod2?.let { code -> MODIFIER_PRESETS.firstOrNull { it.first == code }?.second } ?: noneLabel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (mod2 == null) colors.accentBorder else accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { mod2Expanded = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mod2Label, color = if (mod2 == null) colors.onSurfaceSecondary else accentColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (mod2 == null) colors.onSurfaceSecondary else accentColor)
                }
                DropdownMenu(
                    expanded         = mod2Expanded,
                    onDismissRequest = { mod2Expanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    DropdownMenuItem(
                        text    = { Text(noneLabel, color = if (mod2 == null) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { mod2 = null; emitChange(current.keycode, current.label, mod1, null); mod2Expanded = false },
                    )
                    MODIFIER_PRESETS.forEach { (code, label) ->
                        if (code != mod1) {
                            DropdownMenuItem(
                                text    = { Text(label, color = if (mod2 == code) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { mod2 = code; emitChange(current.keycode, current.label, mod1, code); mod2Expanded = false },
                            )
                        }
                    }
                }
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
            Text(current.button.displayLabel(), color = accentColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            MouseButton.entries.forEach { btn ->
                DropdownMenuItem(
                    text    = { Text(btn.displayLabel(), color = if (btn == current.button) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
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
    var primaryExpanded by remember { mutableStateOf(false) }
    var extra1Expanded  by remember { mutableStateOf(false) }
    var extra2Expanded  by remember { mutableStateOf(false) }
    var extra3Expanded  by remember { mutableStateOf(false) }
    var extra1          by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(0)) }
    var extra2          by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(1)) }
    var extra3          by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(2)) }
    val colors          = LocalAppColors.current
    val noneLabel       = stringResource(R.string.macropad_modifier_none)
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    val currentPreset = GamepadKeycodes.PRESETS.firstOrNull { it.code == current.btnCode }
        ?: GamepadKeycodes.PRESETS.first()

    fun presetShortLabel(code: Int?) = code?.let { c ->
        GamepadKeycodes.PRESETS.firstOrNull { it.code == c }?.displayShortLabel(swapFaceButtons)
    }

    fun emitChange(primary: GamepadKeycodes.GamepadButtonPreset, e1: Int?, e2: Int?, e3: Int?) {
        onChange(PadAction.GamepadButton(primary.code, primary.displayShortLabel(swapFaceButtons), listOfNotNull(e1, e2, e3)))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Primary button ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_button), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { primaryExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(currentPreset.displayShortLabel(swapFaceButtons), color = accentColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
                }
                DropdownMenu(
                    expanded         = primaryExpanded,
                    onDismissRequest = { primaryExpanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    GamepadKeycodes.PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            text    = { Text(preset.localizedDisplayLabel(swapFaceButtons), color = if (preset.code == current.btnCode) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { emitChange(preset, extra1, extra2, extra3); primaryExpanded = false },
                        )
                    }
                }
            }
        }

        // ── Extra button 1 ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_extra_1), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            Box {
                val selectedLabel = presetShortLabel(extra1) ?: noneLabel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (extra1 == null) colors.accentBorder else accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { extra1Expanded = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedLabel, color = if (extra1 == null) colors.onSurfaceSecondary else accentColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (extra1 == null) colors.onSurfaceSecondary else accentColor)
                }
                DropdownMenu(
                    expanded         = extra1Expanded,
                    onDismissRequest = { extra1Expanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    DropdownMenuItem(
                        text    = { Text(noneLabel, color = if (extra1 == null) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { extra1 = null; emitChange(currentPreset, null, extra2, extra3); extra1Expanded = false },
                    )
                    GamepadKeycodes.PRESETS.forEach { preset ->
                        if (preset.code != current.btnCode && preset.code !in setOfNotNull(extra2, extra3)) {
                            DropdownMenuItem(
                                text    = { Text(preset.localizedDisplayLabel(swapFaceButtons), color = if (preset.code == extra1) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { extra1 = preset.code; emitChange(currentPreset, preset.code, extra2, extra3); extra1Expanded = false },
                            )
                        }
                    }
                }
            }
        }

        // ── Extra button 2 ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_extra_2), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            Box {
                val selectedLabel = presetShortLabel(extra2) ?: noneLabel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (extra2 == null) colors.accentBorder else accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { extra2Expanded = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedLabel, color = if (extra2 == null) colors.onSurfaceSecondary else accentColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (extra2 == null) colors.onSurfaceSecondary else accentColor)
                }
                DropdownMenu(
                    expanded         = extra2Expanded,
                    onDismissRequest = { extra2Expanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    DropdownMenuItem(
                        text    = { Text(noneLabel, color = if (extra2 == null) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { extra2 = null; emitChange(currentPreset, extra1, null, extra3); extra2Expanded = false },
                    )
                    GamepadKeycodes.PRESETS.forEach { preset ->
                        if (preset.code != current.btnCode && preset.code !in setOfNotNull(extra1, extra3)) {
                            DropdownMenuItem(
                                text    = { Text(preset.localizedDisplayLabel(swapFaceButtons), color = if (preset.code == extra2) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { extra2 = preset.code; emitChange(currentPreset, extra1, preset.code, extra3); extra2Expanded = false },
                            )
                        }
                    }
                }
            }
        }

        // ── Extra button 3 ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_extra_3), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            Box {
                val selectedLabel = presetShortLabel(extra3) ?: noneLabel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (extra3 == null) colors.accentBorder else accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { extra3Expanded = true }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedLabel, color = if (extra3 == null) colors.onSurfaceSecondary else accentColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (extra3 == null) colors.onSurfaceSecondary else accentColor)
                }
                DropdownMenu(
                    expanded         = extra3Expanded,
                    onDismissRequest = { extra3Expanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    DropdownMenuItem(
                        text    = { Text(noneLabel, color = if (extra3 == null) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { extra3 = null; emitChange(currentPreset, extra1, extra2, null); extra3Expanded = false },
                    )
                    GamepadKeycodes.PRESETS.forEach { preset ->
                        if (preset.code != current.btnCode && preset.code !in setOfNotNull(extra1, extra2)) {
                            DropdownMenuItem(
                                text    = { Text(preset.localizedDisplayLabel(swapFaceButtons), color = if (preset.code == extra3) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { extra3 = preset.code; emitChange(currentPreset, extra1, extra2, preset.code); extra3Expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro picker — folder dropdown + macro dropdown
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Macro picker: a single dropdown of all macros in the active profile (flat list).
 *
 * Pre-selects the macro that matches [current.macroId] on first composition.
 */
@Composable
internal fun MacroPicker(
    current:     PadAction.Macro,
    accentColor: Color,
    onEditMacro: ((Macro) -> Unit)? = null,
    onChange:    (PadAction) -> Unit,
) {
    val profile by MacroPadState.activeProfile.collectAsState()
    val macros  = profile?.macros ?: emptyList()
    val colors  = LocalAppColors.current
    val folderEmptyLabel = stringResource(R.string.macropad_picker_folder_empty)

    var macroExpanded by remember { mutableStateOf(false) }
    val selectedMacro = macros.firstOrNull { it.id == current.macroId }
        ?: macros.firstOrNull()
    val selectedMacroName = selectedMacro?.name ?: ""

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier             = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(enabled = macros.isNotEmpty()) { macroExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selectedMacroName.ifEmpty { folderEmptyLabel },
                        color    = if (macros.isEmpty()) colors.onSurfaceSecondary else accentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (macros.isNotEmpty()) {
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
                    }
                }
                DropdownMenu(
                    expanded         = macroExpanded,
                    onDismissRequest = { macroExpanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    macros.forEach { macro ->
                        DropdownMenuItem(
                            text    = { Text(macro.name, color = if (macro.id == current.macroId) accentColor else colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { onChange(PadAction.Macro(macro.id)); macroExpanded = false },
                        )
                    }
                }
            }
            if (onEditMacro != null && selectedMacro != null) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onEditMacro(selectedMacro) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint     = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.cd_edit_macro),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modifier preset list (Ctrl/Shift/Alt/Meta — used in KeyboardKeyPicker dropdowns)
// ─────────────────────────────────────────────────────────────────────────────

internal val MODIFIER_PRESETS: List<Pair<Int, String>> = listOf(
    LinuxKeycodes.KEY_LEFTCTRL   to "Ctrl L",
    LinuxKeycodes.KEY_RIGHTCTRL  to "Ctrl R",
    LinuxKeycodes.KEY_LEFTSHIFT  to "Shift L",
    LinuxKeycodes.KEY_RIGHTSHIFT to "Shift R",
    LinuxKeycodes.KEY_LEFTALT    to "Alt",
    LinuxKeycodes.KEY_RIGHTALT   to "AltGr",
    LinuxKeycodes.KEY_LEFTMETA   to "Meta/Win",
)

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
