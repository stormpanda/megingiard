package com.stormpanda.megingiard.macropad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import java.util.UUID

private const val TAG = "PadActionSubPickers"

@Composable
internal fun KeyboardKeyPicker(
    current: PadAction.KeyboardKey,
    onOpenPicker: () -> Unit,
    onChange: (PadAction) -> Unit,
) {
    var mod1 by remember(current.modifiers) { mutableStateOf(current.modifiers.getOrNull(0)) }
    var mod2 by remember(current.modifiers) { mutableStateOf(current.modifiers.getOrNull(1)) }
    val noneLabel = stringResource(R.string.macropad_modifier_none)

    fun modifierLabel(code: Int?): String =
        code?.let { selectedCode ->
            MODIFIER_PRESETS.firstOrNull { it.first == selectedCode }?.second
        } ?: noneLabel

    fun emitChange(
        keycode: Int,
        label: String,
        newMod1: Int?,
        newMod2: Int?,
    ) {
        AppLog.d(TAG, "KeyboardKeyPicker: emitChange keycode=$keycode label='$label'")
        onChange(PadAction.KeyboardKey(keycode, label, listOfNotNull(newMod1, newMod2)))
    }

    GamepadActionCard(
        title = stringResource(R.string.macropad_picker_label_key),
        description = stringResource(R.string.macropad_picker_label_key_desc),
        actionText = current.label.ifBlank { stringResource(R.string.gamepad_action_choose_key) },
        icon = Icons.Rounded.Keyboard,
        onClick = onOpenPicker,
    )

    val mod1Options = listOf<Int?>(null) + MODIFIER_PRESETS.map { it.first }.filter { it != mod2 }
    val mod1Idx = mod1Options.indexOf(mod1).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_mod_1),
        description = stringResource(R.string.macropad_picker_label_mod_desc),
        selectedText = modifierLabel(mod1),
        icon = Icons.Rounded.Keyboard,
        onPrevious = {
            val nextIdx = (mod1Idx - 1 + mod1Options.size) % mod1Options.size
            val code = mod1Options[nextIdx]
            mod1 = code
            emitChange(current.keycode, current.label, code, mod2)
        },
        onNext = {
            val nextIdx = (mod1Idx + 1) % mod1Options.size
            val code = mod1Options[nextIdx]
            mod1 = code
            emitChange(current.keycode, current.label, code, mod2)
        },
    )

    val mod2Options = listOf<Int?>(null) + MODIFIER_PRESETS.map { it.first }.filter { it != mod1 }
    val mod2Idx = mod2Options.indexOf(mod2).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_mod_2),
        description = stringResource(R.string.macropad_picker_label_mod_desc),
        selectedText = modifierLabel(mod2),
        icon = Icons.Rounded.Keyboard,
        onPrevious = {
            val nextIdx = (mod2Idx - 1 + mod2Options.size) % mod2Options.size
            val code = mod2Options[nextIdx]
            mod2 = code
            emitChange(current.keycode, current.label, mod1, code)
        },
        onNext = {
            val nextIdx = (mod2Idx + 1) % mod2Options.size
            val code = mod2Options[nextIdx]
            mod2 = code
            emitChange(current.keycode, current.label, mod1, code)
        },
    )
}

@Composable
internal fun MouseButtonPicker(
    current: PadAction.MouseButton,
    onOpenPicker: () -> Unit,
) {
    GamepadActionCard(
        title = stringResource(R.string.macropad_action_mouse_button),
        description = stringResource(R.string.macropad_picker_mouse_button_desc),
        actionText = current.button.displayLabel(),
        icon = Icons.Rounded.Mouse,
        onClick = onOpenPicker,
    )
}

@Composable
internal fun GamepadButtonPicker(
    current: PadAction.GamepadButton,
    onOpenPicker: () -> Unit,
    onChange: (PadAction) -> Unit,
) {
    var extra1 by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(0)) }
    var extra2 by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(1)) }
    var extra3 by remember(current.extraBtnCodes) { mutableStateOf(current.extraBtnCodes.getOrNull(2)) }
    val noneLabel = stringResource(R.string.macropad_modifier_none)
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    val currentPreset =
        GamepadKeycodes.PRESETS.firstOrNull { it.code == current.btnCode }
            ?: GamepadKeycodes.PRESETS.first()

    fun presetShortLabel(code: Int?) =
        code?.let { c ->
            GamepadKeycodes.PRESETS.firstOrNull { it.code == c }?.displayShortLabel(swapFaceButtons)
        }

    @Composable
    fun presetMenuLabel(code: Int?): String =
        code?.let { c ->
            GamepadKeycodes.PRESETS.firstOrNull { it.code == c }?.localizedDisplayLabel(swapFaceButtons)
        } ?: noneLabel

    fun emitChange(
        primary: GamepadKeycodes.GamepadButtonPreset,
        e1: Int?,
        e2: Int?,
        e3: Int?,
    ) {
        onChange(PadAction.GamepadButton(primary.code, primary.displayShortLabel(swapFaceButtons), listOfNotNull(e1, e2, e3)))
    }

    GamepadActionCard(
        title = stringResource(R.string.macropad_picker_label_button),
        description = stringResource(R.string.macropad_picker_label_button_desc),
        actionText = currentPreset.localizedDisplayLabel(swapFaceButtons),
        icon = Icons.Rounded.SportsEsports,
        onClick = onOpenPicker,
    )

    val extra1Options =
        listOf<Int?>(null) +
            GamepadKeycodes.PRESETS.map { it.code }.filter { it != current.btnCode && it !in setOfNotNull(extra2, extra3) }
    val extra1Idx = extra1Options.indexOf(extra1).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_extra_1),
        description = stringResource(R.string.macropad_picker_label_extra_desc),
        selectedText = presetShortLabel(extra1) ?: presetMenuLabel(extra1),
        icon = Icons.Rounded.SportsEsports,
        onPrevious = {
            val nextIdx = (extra1Idx - 1 + extra1Options.size) % extra1Options.size
            val code = extra1Options[nextIdx]
            extra1 = code
            emitChange(currentPreset, code, extra2, extra3)
        },
        onNext = {
            val nextIdx = (extra1Idx + 1) % extra1Options.size
            val code = extra1Options[nextIdx]
            extra1 = code
            emitChange(currentPreset, code, extra2, extra3)
        },
    )

    val extra2Options =
        listOf<Int?>(null) +
            GamepadKeycodes.PRESETS.map { it.code }.filter { it != current.btnCode && it !in setOfNotNull(extra1, extra3) }
    val extra2Idx = extra2Options.indexOf(extra2).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_extra_2),
        description = stringResource(R.string.macropad_picker_label_extra_desc),
        selectedText = presetShortLabel(extra2) ?: presetMenuLabel(extra2),
        icon = Icons.Rounded.SportsEsports,
        onPrevious = {
            val nextIdx = (extra2Idx - 1 + extra2Options.size) % extra2Options.size
            val code = extra2Options[nextIdx]
            extra2 = code
            emitChange(currentPreset, extra1, code, extra3)
        },
        onNext = {
            val nextIdx = (extra2Idx + 1) % extra2Options.size
            val code = extra2Options[nextIdx]
            extra2 = code
            emitChange(currentPreset, extra1, code, extra3)
        },
    )

    val extra3Options =
        listOf<Int?>(null) +
            GamepadKeycodes.PRESETS.map { it.code }.filter { it != current.btnCode && it !in setOfNotNull(extra1, extra2) }
    val extra3Idx = extra3Options.indexOf(extra3).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_extra_3),
        description = stringResource(R.string.macropad_picker_label_extra_desc),
        selectedText = presetShortLabel(extra3) ?: presetMenuLabel(extra3),
        icon = Icons.Rounded.SportsEsports,
        onPrevious = {
            val nextIdx = (extra3Idx - 1 + extra3Options.size) % extra3Options.size
            val code = extra3Options[nextIdx]
            extra3 = code
            emitChange(currentPreset, extra1, extra2, code)
        },
        onNext = {
            val nextIdx = (extra3Idx + 1) % extra3Options.size
            val code = extra3Options[nextIdx]
            extra3 = code
            emitChange(currentPreset, extra1, extra2, code)
        },
    )
}

@Composable
internal fun MacroPicker(
    current: PadAction.Macro,
    accentColor: Color,
    onEditMacro: ((Macro) -> Unit)? = null,
    onChange: (PadAction) -> Unit,
) {
    val profile by MacroPadState.activeProfile.collectAsState()
    val macros = profile?.macros ?: emptyList()
    val defaultName = stringResource(R.string.macropad_macro_default_name)

    val selectedMacro =
        macros.firstOrNull { it.id == current.macroId }
            ?: macros.firstOrNull()

    val macroIdx = macros.indexOf(selectedMacro).coerceAtLeast(0)

    GamepadChoiceCard(
        title = stringResource(R.string.macropad_action_macro),
        description = stringResource(R.string.macropad_picker_macro_desc),
        selectedText = selectedMacro?.name ?: stringResource(R.string.macropad_picker_folder_empty),
        icon = Icons.Rounded.SmartButton,
        enabled = macros.isNotEmpty(),
        onPrevious = {
            if (macros.isNotEmpty()) {
                val nextIdx = (macroIdx - 1 + macros.size) % macros.size
                onChange(PadAction.Macro(macros[nextIdx].id))
            }
        },
        onNext = {
            if (macros.isNotEmpty()) {
                val nextIdx = (macroIdx + 1) % macros.size
                onChange(PadAction.Macro(macros[nextIdx].id))
            }
        },
    )

    if (onEditMacro != null && selectedMacro != null) {
        GamepadActionCard(
            title = stringResource(R.string.settings_macropad_edit),
            description = stringResource(R.string.macropad_picker_macro_edit_desc, selectedMacro.name),
            actionText = stringResource(R.string.gamepad_action_edit),
            icon = Icons.Rounded.Edit,
            onClick = { onEditMacro(selectedMacro) },
        )
    }

    if (onEditMacro != null) {
        GamepadActionCard(
            title = stringResource(R.string.settings_macropad_new),
            description = stringResource(R.string.macropad_picker_macro_new_desc),
            actionText = stringResource(R.string.gamepad_action_create),
            icon = Icons.Rounded.Add,
            onClick = {
                val newMacroId = UUID.randomUUID().toString()
                val newMacro =
                    Macro(
                        id = newMacroId,
                        name = defaultName,
                        steps = emptyList(),
                    )
                MacroPadState.addMacro(newMacro)
                onChange(PadAction.Macro(newMacroId))
                onEditMacro(newMacro)
            },
        )
    }
}
