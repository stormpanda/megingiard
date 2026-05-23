package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "PadActionSubPickers"

@Composable
internal fun KeyboardKeyPicker(
    current: PadAction.KeyboardKey,
    onChange: (PadAction) -> Unit,
) {
    var mod1         by remember(current.modifiers) { mutableStateOf(current.modifiers.getOrNull(0)) }
    var mod2         by remember(current.modifiers) { mutableStateOf(current.modifiers.getOrNull(1)) }
    val colors       = LocalAppColors.current
    val noneLabel    = stringResource(R.string.macropad_modifier_none)

    fun modifierLabel(code: Int?): String = code?.let { selectedCode ->
        MODIFIER_PRESETS.firstOrNull { it.first == selectedCode }?.second
    } ?: noneLabel

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
            AppDropdown(
                selected          = current.keycode,
                options           = KEYBOARD_KEY_PRESETS.map { it.first },
                optionText        = { code -> KEYBOARD_KEY_PRESETS.firstOrNull { it.first == code }?.second ?: current.label },
                onSelected        = { code ->
                    val label = KEYBOARD_KEY_PRESETS.firstOrNull { it.first == code }?.second ?: current.label
                    emitChange(code, label, mod1, mod2)
                },
                modifier          = Modifier.fillMaxWidth(),
                textStyle         = MaterialTheme.typography.bodySmall,
                horizontalPadding = 8.dp,
                verticalPadding   = 10.dp,
                fillMaxWidth      = true,
            )
        }

        // ── Modifier 1 ──────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_mod_1), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            AppDropdown(
                selected          = mod1,
                options           = listOf<Int?>(null) + MODIFIER_PRESETS.map { it.first }.filter { it != mod2 },
                optionText        = { code -> modifierLabel(code) },
                onSelected        = { code -> mod1 = code; emitChange(current.keycode, current.label, code, mod2) },
                modifier          = Modifier.fillMaxWidth(),
                textStyle         = MaterialTheme.typography.bodySmall,
                horizontalPadding = 8.dp,
                verticalPadding   = 10.dp,
                fillMaxWidth      = true,
            )
        }

        // ── Modifier 2 ──────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_mod_2), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            AppDropdown(
                selected          = mod2,
                options           = listOf<Int?>(null) + MODIFIER_PRESETS.map { it.first }.filter { it != mod1 },
                optionText        = { code -> modifierLabel(code) },
                onSelected        = { code -> mod2 = code; emitChange(current.keycode, current.label, mod1, code) },
                modifier          = Modifier.fillMaxWidth(),
                textStyle         = MaterialTheme.typography.bodySmall,
                horizontalPadding = 8.dp,
                verticalPadding   = 10.dp,
                fillMaxWidth      = true,
            )
        }
    }
}

@Composable
internal fun MouseButtonPicker(
    current: PadAction.MouseButton,
    onChange: (PadAction) -> Unit,
) {
    AppDropdown(
        selected     = current.button,
        options      = MouseButton.entries,
        optionText   = { btn -> btn.displayLabel() },
        onSelected   = { btn -> onChange(PadAction.MouseButton(btn)) },
        modifier     = Modifier.fillMaxWidth(),
        fillMaxWidth = true,
    )
}

@Composable
internal fun GamepadButtonPicker(
    current: PadAction.GamepadButton,
    onChange: (PadAction) -> Unit,
) {
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

    @Composable
    fun presetMenuLabel(code: Int?): String = code?.let { c ->
        GamepadKeycodes.PRESETS.firstOrNull { it.code == c }?.localizedDisplayLabel(swapFaceButtons)
    } ?: noneLabel

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
            AppDropdown(
                selected          = currentPreset,
                options           = GamepadKeycodes.PRESETS,
                optionText        = { preset -> preset.localizedDisplayLabel(swapFaceButtons) },
                onSelected        = { preset -> emitChange(preset, extra1, extra2, extra3) },
                modifier          = Modifier.fillMaxWidth(),
                textStyle         = MaterialTheme.typography.bodySmall,
                horizontalPadding = 8.dp,
                verticalPadding   = 10.dp,
                fillMaxWidth      = true,
            )
        }

        // ── Extra button 1 ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_extra_1), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            AppDropdown(
                selected          = extra1,
                options           = listOf<Int?>(null) + GamepadKeycodes.PRESETS.map { it.code }.filter { it != current.btnCode && it !in setOfNotNull(extra2, extra3) },
                optionText        = { code -> presetShortLabel(code) ?: presetMenuLabel(code) },
                onSelected        = { code -> extra1 = code; emitChange(currentPreset, code, extra2, extra3) },
                modifier          = Modifier.fillMaxWidth(),
                textStyle         = MaterialTheme.typography.bodySmall,
                horizontalPadding = 8.dp,
                verticalPadding   = 10.dp,
                fillMaxWidth      = true,
            )
        }

        // ── Extra button 2 ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_extra_2), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            AppDropdown(
                selected          = extra2,
                options           = listOf<Int?>(null) + GamepadKeycodes.PRESETS.map { it.code }.filter { it != current.btnCode && it !in setOfNotNull(extra1, extra3) },
                optionText        = { code -> presetShortLabel(code) ?: presetMenuLabel(code) },
                onSelected        = { code -> extra2 = code; emitChange(currentPreset, extra1, code, extra3) },
                modifier          = Modifier.fillMaxWidth(),
                textStyle         = MaterialTheme.typography.bodySmall,
                horizontalPadding = 8.dp,
                verticalPadding   = 10.dp,
                fillMaxWidth      = true,
            )
        }

        // ── Extra button 3 ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.macropad_picker_label_extra_3), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelSmall)
            AppDropdown(
                selected          = extra3,
                options           = listOf<Int?>(null) + GamepadKeycodes.PRESETS.map { it.code }.filter { it != current.btnCode && it !in setOfNotNull(extra1, extra2) },
                optionText        = { code -> presetShortLabel(code) ?: presetMenuLabel(code) },
                onSelected        = { code -> extra3 = code; emitChange(currentPreset, extra1, extra2, code) },
                modifier          = Modifier.fillMaxWidth(),
                textStyle         = MaterialTheme.typography.bodySmall,
                horizontalPadding = 8.dp,
                verticalPadding   = 10.dp,
                fillMaxWidth      = true,
            )
        }
    }
}

@Composable
internal fun MacroPicker(
    current:     PadAction.Macro,
    accentColor: Color,
    onEditMacro: ((Macro) -> Unit)? = null,
    onChange:    (PadAction) -> Unit,
) {
    val profile by MacroPadState.activeProfile.collectAsState()
    val macros  = profile?.macros ?: emptyList()
    val folderEmptyLabel = stringResource(R.string.macropad_picker_folder_empty)

    val selectedMacro = macros.firstOrNull { it.id == current.macroId }
        ?: macros.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier             = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppDropdown(
                selected     = selectedMacro,
                options      = macros,
                optionText   = { macro -> macro?.name ?: folderEmptyLabel },
                onSelected   = { macro -> if (macro != null) onChange(PadAction.Macro(macro.id)) },
                modifier     = Modifier.weight(1f),
                enabled      = macros.isNotEmpty(),
                fillMaxWidth = true,
            )
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
