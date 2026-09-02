package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.cycle
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
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_mod_1),
        description = stringResource(R.string.macropad_picker_label_mod_desc),
        selectedText = modifierLabel(mod1),
        icon = Icons.Rounded.Keyboard,
        onPrevious = {
            val code = mod1Options.cycle(mod1, BumperDirection.PREV)
            mod1 = code
            emitChange(current.keycode, current.label, code, mod2)
        },
        onNext = {
            val code = mod1Options.cycle(mod1, BumperDirection.NEXT)
            mod1 = code
            emitChange(current.keycode, current.label, code, mod2)
        },
    )

    val mod2Options = listOf<Int?>(null) + MODIFIER_PRESETS.map { it.first }.filter { it != mod1 }
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_picker_label_mod_2),
        description = stringResource(R.string.macropad_picker_label_mod_desc),
        selectedText = modifierLabel(mod2),
        icon = Icons.Rounded.Keyboard,
        onPrevious = {
            val code = mod2Options.cycle(mod2, BumperDirection.PREV)
            mod2 = code
            emitChange(current.keycode, current.label, mod1, code)
        },
        onNext = {
            val code = mod2Options.cycle(mod2, BumperDirection.NEXT)
            mod2 = code
            emitChange(current.keycode, current.label, mod1, code)
        },
    )
}

private val EXTRA_SLOT_LABELS =
    listOf(
        R.string.macropad_picker_label_extra_1,
        R.string.macropad_picker_label_extra_2,
        R.string.macropad_picker_label_extra_3,
    )

@Composable
internal fun GamepadButtonPicker(
    current: PadAction.GamepadButton,
    onOpenPicker: (slotIndex: Int) -> Unit,
    isFirstItem: Boolean = false,
) {
    val noneLabel = stringResource(R.string.macropad_modifier_none)
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsStateWithLifecycle()

    val currentPreset =
        GamepadKeycodes.PRESETS.firstOrNull { it.code == current.btnCode }
            ?: GamepadKeycodes.PRESETS.first()

    @Composable
    fun presetLabel(code: Int?): String =
        code?.let { c ->
            GamepadKeycodes.PRESETS.firstOrNull { it.code == c }?.localizedDisplayLabel(swapFaceButtons)
        } ?: noneLabel

    GamepadActionCard(
        title = stringResource(R.string.macropad_picker_label_button),
        description = stringResource(R.string.macropad_picker_label_button_desc),
        actionText = currentPreset.localizedDisplayLabel(swapFaceButtons),
        icon = Icons.Rounded.SportsEsports,
        onClick = { onOpenPicker(0) },
        modifier = Modifier.firstDeckItem(isFirstItem),
    )

    EXTRA_SLOT_LABELS.forEachIndexed { idx, labelRes ->
        val extraCode = current.extraBtnCodes.getOrNull(idx)
        GamepadActionCard(
            title = stringResource(labelRes),
            description = stringResource(R.string.macropad_picker_label_extra_desc),
            actionText = presetLabel(extraCode),
            icon = Icons.Rounded.SportsEsports,
            onClick = { onOpenPicker(idx + 1) },
        )
    }
}

@Composable
internal fun MacroPicker(
    current: PadAction.Macro,
    onOpenMacroPicker: () -> Unit,
    isFirstItem: Boolean = false,
) {
    val profile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
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
