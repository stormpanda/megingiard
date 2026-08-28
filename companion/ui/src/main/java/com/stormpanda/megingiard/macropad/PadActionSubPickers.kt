package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "PadActionSubPickers"

@Composable
internal fun KeyboardKeyPicker(
    current: PadAction.KeyboardKey,
    onOpenPicker: () -> Unit,
    onChange: (PadAction) -> Unit,
    isFirstItem: Boolean = false,
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
        actionText = current.label.ifBlank { null },
        icon = Icons.Rounded.Keyboard,
        onClick = onOpenPicker,
        modifier = Modifier.firstDeckItem(isFirstItem),
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
    onOpenPicker: (slotIndex: Int) -> Unit,
    onChange: (PadAction) -> Unit,
    isFirstItem: Boolean = false,
) {
    val noneLabel = stringResource(R.string.macropad_modifier_none)
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()

    val currentPreset =
        GamepadKeycodes.PRESETS.firstOrNull { it.code == current.btnCode }
            ?: GamepadKeycodes.PRESETS.first()

    @Composable
    fun presetLabel(code: Int?): String =
        code?.let { c ->
            GamepadKeycodes.PRESETS.firstOrNull { it.code == c }?.localizedDisplayLabel(swapFaceButtons)
        } ?: noneLabel

    val extra1 = current.extraBtnCodes.getOrNull(0)
    val extra2 = current.extraBtnCodes.getOrNull(1)
    val extra3 = current.extraBtnCodes.getOrNull(2)

    GamepadActionCard(
        title = stringResource(R.string.macropad_picker_label_button),
        description = stringResource(R.string.macropad_picker_label_button_desc),
        actionText = currentPreset.localizedDisplayLabel(swapFaceButtons),
        icon = Icons.Rounded.SportsEsports,
        onClick = { onOpenPicker(0) },
        modifier = Modifier.firstDeckItem(isFirstItem),
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_picker_label_extra_1),
        description = stringResource(R.string.macropad_picker_label_extra_desc),
        actionText = presetLabel(extra1),
        icon = Icons.Rounded.SportsEsports,
        onClick = { onOpenPicker(1) },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_picker_label_extra_2),
        description = stringResource(R.string.macropad_picker_label_extra_desc),
        actionText = presetLabel(extra2),
        icon = Icons.Rounded.SportsEsports,
        onClick = { onOpenPicker(2) },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_picker_label_extra_3),
        description = stringResource(R.string.macropad_picker_label_extra_desc),
        actionText = presetLabel(extra3),
        icon = Icons.Rounded.SportsEsports,
        onClick = { onOpenPicker(3) },
    )
}

@Composable
internal fun MacroPicker(
    current: PadAction.Macro,
    accentColor: Color,
    onOpenMacroPicker: () -> Unit,
    isFirstItem: Boolean = false,
) {
    val profile by MacroPadState.activeProfile.collectAsState()
    val macros = profile?.macros ?: emptyList()

    val selectedMacro =
        macros.firstOrNull { it.id == current.macroId }
            ?: macros.firstOrNull()

    GamepadInfoBox(
        text = stringResource(R.string.macropad_picker_macro_create_info_title),
        description = stringResource(R.string.macropad_picker_macro_create_info_desc),
        icon = Icons.Rounded.Info,
        modifier = Modifier.fillMaxWidth(),
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_action_macro),
        description = stringResource(R.string.macropad_picker_macro_desc),
        actionText = selectedMacro?.name ?: stringResource(R.string.macropad_picker_folder_empty),
        icon = Icons.Rounded.SmartButton,
        onClick = onOpenMacroPicker,
        modifier = Modifier.firstDeckItem(isFirstItem),
    )
}
