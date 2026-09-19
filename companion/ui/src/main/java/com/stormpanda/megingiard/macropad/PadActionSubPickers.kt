package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Speed
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
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "PadActionSubPickers"

private const val TP_SENSITIVITY_MIN = 0.1f
private const val TP_SENSITIVITY_MAX = 5.0f
private const val TP_SENSITIVITY_STEP = 0.1f

private const val FS_MOUSE_SENSITIVITY_MIN = 0.1f
private const val FS_MOUSE_SENSITIVITY_MAX = 5.0f
private const val FS_MOUSE_SENSITIVITY_STEP = 0.1f

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

@Composable
internal fun TrackpointPicker(
    current: PadAction.TrackpointMove,
    onOpenPicker: () -> Unit,
    onChange: (PadAction) -> Unit,
    isFirstItem: Boolean = false,
) {
    AppLog.d(TAG, "TrackpointPicker: mode=${current.mode} size=${current.size} sens=${current.sensitivity}")

    GamepadActionCard(
        title = stringResource(R.string.macropad_action_group_mouse),
        description = stringResource(R.string.macropad_action_group_mouse_desc),
        actionText = current.displayLabel(),
        icon = Icons.Rounded.ControlCamera,
        onClick = onOpenPicker,
        modifier = Modifier.firstDeckItem(isFirstItem),
    )

    val isMouseMode = current.mode == TrackpointMode.PHYSICAL_MOUSE
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_trackpoint_mode_title),
        description =
            stringResource(
                if (isMouseMode) {
                    R.string.macropad_trackpoint_mode_mouse_desc
                } else {
                    R.string.macropad_trackpoint_mode_touch_desc
                },
            ),
        selectedText =
            stringResource(
                if (isMouseMode) {
                    R.string.macropad_trackpoint_mode_mouse
                } else {
                    R.string.macropad_trackpoint_mode_touch
                },
            ),
        icon = Icons.Rounded.ControlCamera,
        onPrevious = {
            val nextMode = if (isMouseMode) TrackpointMode.VIRTUAL_TOUCH else TrackpointMode.PHYSICAL_MOUSE
            onChange(current.copy(mode = nextMode))
        },
        onNext = {
            val nextMode = if (isMouseMode) TrackpointMode.VIRTUAL_TOUCH else TrackpointMode.PHYSICAL_MOUSE
            onChange(current.copy(mode = nextMode))
        },
    )

    GamepadChoiceCard(
        title = stringResource(R.string.macropad_trackpoint_size_title),
        description = stringResource(R.string.macropad_trackpoint_size_desc),
        selectedText = stringResource(current.size.labelResId()),
        icon = Icons.Rounded.CropFree,
        onPrevious = {
            val nextSize = TrackpointSize.entries.cycle(current.size, BumperDirection.PREV)
            onChange(current.copy(size = nextSize))
        },
        onNext = {
            val nextSize = TrackpointSize.entries.cycle(current.size, BumperDirection.NEXT)
            onChange(current.copy(size = nextSize))
        },
    )

    GamepadStepperCard(
        title = stringResource(R.string.macropad_trackpoint_sensitivity_title),
        description = stringResource(R.string.macropad_trackpoint_sensitivity_desc),
        valueText = String.format(Locale.US, "%.1f×", current.sensitivity),
        icon = Icons.Rounded.Speed,
        onDecrement = {
            val stepped = ((current.sensitivity - TP_SENSITIVITY_STEP) * 10f).roundToInt() / 10f
            onChange(current.copy(sensitivity = stepped.coerceIn(TP_SENSITIVITY_MIN, TP_SENSITIVITY_MAX)))
        },
        onIncrement = {
            val stepped = ((current.sensitivity + TP_SENSITIVITY_STEP) * 10f).roundToInt() / 10f
            onChange(current.copy(sensitivity = stepped.coerceIn(TP_SENSITIVITY_MIN, TP_SENSITIVITY_MAX)))
        },
    )
}

@Composable
internal fun FullScreenMousePicker(
    current: PadAction.FullScreenMouse,
    onOpenPicker: () -> Unit,
    onChange: (PadAction) -> Unit,
    isFirstItem: Boolean = false,
) {
    AppLog.d(TAG, "FullScreenMousePicker: sens=${current.sensitivity}")

    GamepadActionCard(
        title = stringResource(R.string.macropad_action_group_other),
        description = stringResource(R.string.macropad_action_group_other_desc),
        actionText = current.displayLabel(),
        icon = Icons.Rounded.Layers,
        onClick = onOpenPicker,
        modifier = Modifier.firstDeckItem(isFirstItem),
    )

    GamepadStepperCard(
        title = stringResource(R.string.macropad_fullscreen_mouse_sensitivity_title),
        description = stringResource(R.string.macropad_fullscreen_mouse_sensitivity_desc),
        valueText = String.format(Locale.US, "%.1f×", current.sensitivity),
        icon = Icons.Rounded.Speed,
        onDecrement = {
            val stepped = ((current.sensitivity - FS_MOUSE_SENSITIVITY_STEP) * 10f).roundToInt() / 10f
            onChange(current.copy(sensitivity = stepped.coerceIn(FS_MOUSE_SENSITIVITY_MIN, FS_MOUSE_SENSITIVITY_MAX)))
        },
        onIncrement = {
            val stepped = ((current.sensitivity + FS_MOUSE_SENSITIVITY_STEP) * 10f).roundToInt() / 10f
            onChange(current.copy(sensitivity = stepped.coerceIn(FS_MOUSE_SENSITIVITY_MIN, FS_MOUSE_SENSITIVITY_MAX)))
        },
    )
}
