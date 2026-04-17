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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.LocalAppColors

// ─────────────────────────────────────────────────────────────────────────────
// Action category enum
// ─────────────────────────────────────────────────────────────────────────────

internal enum class ActionCategory { KEYBOARD_KEY, GAMEPAD_BUTTON, MOUSE_BUTTON, SCROLL_WHEEL, TRACKPOINT, MACRO, AMBIENT_PEEK }

internal fun ActionCategory.labelResId(): Int = when (this) {
    ActionCategory.KEYBOARD_KEY   -> R.string.macropad_action_keyboard_key
    ActionCategory.GAMEPAD_BUTTON -> R.string.macropad_action_gamepad_button
    ActionCategory.MOUSE_BUTTON   -> R.string.macropad_action_mouse_button
    ActionCategory.SCROLL_WHEEL   -> R.string.macropad_action_scroll_wheel
    ActionCategory.TRACKPOINT     -> R.string.macropad_action_trackpoint
    ActionCategory.MACRO          -> R.string.macropad_action_macro
    ActionCategory.AMBIENT_PEEK   -> R.string.macropad_action_ambient_peek
}

internal fun ActionCategory.defaultAction(): PadAction = when (this) {
    ActionCategory.KEYBOARD_KEY   -> PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
    ActionCategory.GAMEPAD_BUTTON -> PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    ActionCategory.MOUSE_BUTTON   -> PadAction.MouseButton(MouseButton.LEFT)
    ActionCategory.SCROLL_WHEEL   -> PadAction.ScrollWheel
    ActionCategory.TRACKPOINT     -> PadAction.TrackpointMove()
    ActionCategory.MACRO          -> PadAction.Macro(MacroState.macros.value.firstOrNull()?.id ?: "")
    ActionCategory.AMBIENT_PEEK   -> PadAction.AmbientPeek
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
    is PadAction.AmbientPeek     -> R.string.macropad_action_ambient_peek
    is PadAction.MouseLeftClick  -> R.string.macropad_action_mouse_button
    is PadAction.MouseRightClick -> R.string.macropad_action_mouse_button
}

@Composable
internal fun PadAction.displayLabel(): String {
    val context = LocalContext.current
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
            val comboLabel = if (extraBtnCodes.isEmpty()) label else {
                val extraNames = extraBtnCodes.mapNotNull { code ->
                    GamepadKeycodes.PRESETS.firstOrNull { it.code == code }?.shortLabel
                }.joinToString("+")
                "$label+$extraNames"
            }
            context.getString(R.string.macropad_display_gamepad_button, comboLabel)
        }
        is PadAction.MouseButton     -> context.getString(R.string.macropad_display_mouse_button, button.displayLabel())
        is PadAction.ScrollWheel     -> context.getString(R.string.macropad_display_scroll_wheel)
        is PadAction.TrackpointMove  -> context.getString(R.string.macropad_display_trackpoint)
        is PadAction.Macro           -> {
            val macroName = MacroState.macros.value.firstOrNull { it.id == macroId }?.name ?: macroId
            context.getString(R.string.macropad_display_macro, macroName)
        }
        is PadAction.AmbientPeek     -> context.getString(R.string.macropad_action_ambient_peek)
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
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
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
                    ActionCategory.MACRO          -> MacroState.macros.value.isNotEmpty()
                    ActionCategory.AMBIENT_PEEK   -> true
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
            is PadAction.Macro          -> MacroPicker(current, accentColor, onChange)
            is PadAction.ScrollWheel,
            is PadAction.TrackpointMove,
            is PadAction.AmbientPeek,
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
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { baseExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current.label, color = accentColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
            }
            DropdownMenu(
                expanded         = baseExpanded,
                onDismissRequest = { baseExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                KEYBOARD_KEY_PRESETS.forEach { (code, label) ->
                    DropdownMenuItem(
                        text    = { Text(label, color = if (code == current.keycode) accentColor else colors.onSurface, fontSize = 14.sp) },
                        onClick = { emitChange(code, label, mod1, mod2); baseExpanded = false },
                    )
                }
            }
        }

        // ── Modifier 1 ──────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
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
                Text(mod1Label, color = if (mod1 == null) colors.onSurfaceSecondary else accentColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (mod1 == null) colors.onSurfaceSecondary else accentColor)
            }
            DropdownMenu(
                expanded         = mod1Expanded,
                onDismissRequest = { mod1Expanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text    = { Text(noneLabel, color = if (mod1 == null) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { mod1 = null; emitChange(current.keycode, current.label, null, mod2); mod1Expanded = false },
                )
                MODIFIER_PRESETS.forEach { (code, label) ->
                    if (code != mod2) {
                        DropdownMenuItem(
                            text    = { Text(label, color = if (mod1 == code) accentColor else colors.onSurface, fontSize = 14.sp) },
                            onClick = { mod1 = code; emitChange(current.keycode, current.label, code, mod2); mod1Expanded = false },
                        )
                    }
                }
            }
        }

        // ── Modifier 2 ──────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
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
                Text(mod2Label, color = if (mod2 == null) colors.onSurfaceSecondary else accentColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (mod2 == null) colors.onSurfaceSecondary else accentColor)
            }
            DropdownMenu(
                expanded         = mod2Expanded,
                onDismissRequest = { mod2Expanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text    = { Text(noneLabel, color = if (mod2 == null) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { mod2 = null; emitChange(current.keycode, current.label, mod1, null); mod2Expanded = false },
                )
                MODIFIER_PRESETS.forEach { (code, label) ->
                    if (code != mod1) {
                        DropdownMenuItem(
                            text    = { Text(label, color = if (mod2 == code) accentColor else colors.onSurface, fontSize = 14.sp) },
                            onClick = { mod2 = code; emitChange(current.keycode, current.label, mod1, code); mod2Expanded = false },
                        )
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
            Text(current.button.displayLabel(), color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
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
    var primaryExpanded by remember { mutableStateOf(false) }
    var extra1Expanded  by remember { mutableStateOf(false) }
    var extra2Expanded  by remember { mutableStateOf(false) }
    var extra3Expanded  by remember { mutableStateOf(false) }
    var extra1          by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(0)) }
    var extra2          by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(1)) }
    var extra3          by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(2)) }
    val colors          = LocalAppColors.current
    val noneLabel       = stringResource(R.string.macropad_modifier_none)

    val currentPreset = GamepadKeycodes.PRESETS.firstOrNull { it.code == current.btnCode }
        ?: GamepadKeycodes.PRESETS.first()

    fun presetShortLabel(code: Int?) = code?.let { c ->
        GamepadKeycodes.PRESETS.firstOrNull { it.code == c }?.shortLabel
    }

    fun emitChange(primary: GamepadKeycodes.GamepadButtonPreset, e1: Int?, e2: Int?, e3: Int?) {
        onChange(PadAction.GamepadButton(primary.code, primary.shortLabel, listOfNotNull(e1, e2, e3)))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Primary button ────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { primaryExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currentPreset.shortLabel, color = accentColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
            }
            DropdownMenu(
                expanded         = primaryExpanded,
                onDismissRequest = { primaryExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                GamepadKeycodes.PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text    = { Text(preset.label, color = if (preset.code == current.btnCode) accentColor else colors.onSurface, fontSize = 14.sp) },
                        onClick = { emitChange(preset, extra1, extra2, extra3); primaryExpanded = false },
                    )
                }
            }
        }

        // ── Extra button 1 ────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
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
                Text(selectedLabel, color = if (extra1 == null) colors.onSurfaceSecondary else accentColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (extra1 == null) colors.onSurfaceSecondary else accentColor)
            }
            DropdownMenu(
                expanded         = extra1Expanded,
                onDismissRequest = { extra1Expanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text    = { Text(noneLabel, color = if (extra1 == null) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { extra1 = null; emitChange(currentPreset, null, extra2, extra3); extra1Expanded = false },
                )
                GamepadKeycodes.PRESETS.forEach { preset ->
                    if (preset.code != current.btnCode && preset.code !in setOfNotNull(extra2, extra3)) {
                        DropdownMenuItem(
                            text    = { Text(preset.label, color = if (preset.code == extra1) accentColor else colors.onSurface, fontSize = 14.sp) },
                            onClick = { extra1 = preset.code; emitChange(currentPreset, preset.code, extra2, extra3); extra1Expanded = false },
                        )
                    }
                }
            }
        }

        // ── Extra button 2 ────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
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
                Text(selectedLabel, color = if (extra2 == null) colors.onSurfaceSecondary else accentColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (extra2 == null) colors.onSurfaceSecondary else accentColor)
            }
            DropdownMenu(
                expanded         = extra2Expanded,
                onDismissRequest = { extra2Expanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text    = { Text(noneLabel, color = if (extra2 == null) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { extra2 = null; emitChange(currentPreset, extra1, null, extra3); extra2Expanded = false },
                )
                GamepadKeycodes.PRESETS.forEach { preset ->
                    if (preset.code != current.btnCode && preset.code !in setOfNotNull(extra1, extra3)) {
                        DropdownMenuItem(
                            text    = { Text(preset.label, color = if (preset.code == extra2) accentColor else colors.onSurface, fontSize = 14.sp) },
                            onClick = { extra2 = preset.code; emitChange(currentPreset, extra1, preset.code, extra3); extra2Expanded = false },
                        )
                    }
                }
            }
        }

        // ── Extra button 3 ────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
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
                Text(selectedLabel, color = if (extra3 == null) colors.onSurfaceSecondary else accentColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (extra3 == null) colors.onSurfaceSecondary else accentColor)
            }
            DropdownMenu(
                expanded         = extra3Expanded,
                onDismissRequest = { extra3Expanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text    = { Text(noneLabel, color = if (extra3 == null) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { extra3 = null; emitChange(currentPreset, extra1, extra2, null); extra3Expanded = false },
                )
                GamepadKeycodes.PRESETS.forEach { preset ->
                    if (preset.code != current.btnCode && preset.code !in setOfNotNull(extra1, extra2)) {
                        DropdownMenuItem(
                            text    = { Text(preset.label, color = if (preset.code == extra3) accentColor else colors.onSurface, fontSize = 14.sp) },
                            onClick = { extra3 = preset.code; emitChange(currentPreset, extra1, extra2, preset.code); extra3Expanded = false },
                        )
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
 * Two-step macro selector:
 * 1. **Folder dropdown** — "Nicht zugeordnet" first, then named folders in stored order.
 * 2. **Macro dropdown** — macros belonging to the selected folder only.
 *
 * Pre-selects the folder and macro that match [current.macroId] on first composition.
 * When the selected folder changes, the first macro in that folder is auto-selected.
 */
@Composable
internal fun MacroPicker(
    current:     PadAction.Macro,
    accentColor: Color,
    onChange:    (PadAction) -> Unit,
) {
    val macros          by MacroState.macros.collectAsState()
    val folders         by MacroState.folders.collectAsState()
    val colors          = LocalAppColors.current
    val unassignedLabel = stringResource(R.string.macropad_folder_unassigned)
    val folderEmptyLabel = stringResource(R.string.macropad_picker_folder_empty)

    // selectedFolderId is derived from the macro's persisted folderId once macros load.
    // A LaunchedEffect keeps it in sync when macros arrive from DataStore.
    // Once the user explicitly picks a folder, userHasChosenFolder prevents the effect
    // from overwriting that deliberate choice.
    var userHasChosenFolder by remember(current.macroId) { mutableStateOf(false) }
    var selectedFolderId    by remember(current.macroId) { mutableStateOf<String?>(null) }

    LaunchedEffect(current.macroId, macros) {
        if (!userHasChosenFolder) {
            selectedFolderId = macros.firstOrNull { it.id == current.macroId }?.folderId
        }
    }

    val macrosInFolder = remember(macros, selectedFolderId) {
        macros.filter { it.folderId == selectedFolderId }
    }

    var folderExpanded by remember { mutableStateOf(false) }
    var macroExpanded  by remember { mutableStateOf(false) }

    // Folder display list: (label, folderId)
    val folderItems = remember(folders, unassignedLabel) {
        buildList {
            add(unassignedLabel to null as String?)
            folders.forEach { f -> add(f.name to f.id) }
        }
    }
    val selectedFolderLabel = folderItems.firstOrNull { it.second == selectedFolderId }?.first ?: unassignedLabel
    val selectedMacroName   = macros.firstOrNull { it.id == current.macroId }?.name
        ?: macrosInFolder.firstOrNull()?.name
        ?: ""

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── Folder dropdown ──────────────────────────────────────────────────
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { folderExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedFolderLabel, color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
            }
            DropdownMenu(
                expanded         = folderExpanded,
                onDismissRequest = { folderExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                folderItems.forEach { (label, fId) ->
                    DropdownMenuItem(
                        text    = { Text(label, color = if (fId == selectedFolderId) accentColor else colors.onSurface, fontSize = 14.sp) },
                        onClick = {
                            userHasChosenFolder = true
                            selectedFolderId    = fId
                            folderExpanded      = false
                            val first = macros.filter { it.folderId == fId }.firstOrNull()
                            if (first != null) onChange(PadAction.Macro(first.id))
                        },
                    )
                }
            }
        }

        // ── Macro dropdown (filtered by selected folder) ─────────────────────
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable(enabled = macrosInFolder.isNotEmpty()) { macroExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = if (macrosInFolder.isEmpty()) folderEmptyLabel else selectedMacroName
                Text(
                    label,
                    color    = if (macrosInFolder.isEmpty()) colors.onSurfaceSecondary else accentColor,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (macrosInFolder.isNotEmpty()) {
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
                }
            }
            DropdownMenu(
                expanded         = macroExpanded,
                onDismissRequest = { macroExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                macrosInFolder.forEach { macro ->
                    DropdownMenuItem(
                        text    = { Text(macro.name, color = if (macro.id == current.macroId) accentColor else colors.onSurface, fontSize = 14.sp) },
                        onClick = { onChange(PadAction.Macro(macro.id)); macroExpanded = false },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modifier preset list (Ctrl/Shift/Alt/Meta/Fn — used in KeyboardKeyPicker dropdowns)
// ─────────────────────────────────────────────────────────────────────────────

internal val MODIFIER_PRESETS: List<Pair<Int, String>> = listOf(
    LinuxKeycodes.KEY_LEFTCTRL   to "Ctrl L",
    LinuxKeycodes.KEY_RIGHTCTRL  to "Ctrl R",
    LinuxKeycodes.KEY_LEFTSHIFT  to "Shift L",
    LinuxKeycodes.KEY_RIGHTSHIFT to "Shift R",
    LinuxKeycodes.KEY_LEFTALT    to "Alt",
    LinuxKeycodes.KEY_RIGHTALT   to "AltGr",
    LinuxKeycodes.KEY_LEFTMETA   to "Meta/Win",
    LinuxKeycodes.KEY_FN         to "Fn",
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
