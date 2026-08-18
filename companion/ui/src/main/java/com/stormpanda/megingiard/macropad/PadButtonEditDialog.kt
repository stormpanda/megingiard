package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem
import java.util.UUID

private const val TAG = "PadButtonEditDialog"
private const val PBD_ICON_LAYOUT_NEXT = "arrow_forward"
private const val PBD_ICON_LAYOUT_PREVIOUS = "arrow_back"
private const val PBD_ICON_MIRROR_PLAY_STOP = "cast"
private const val PBD_ICON_MIRROR_FREEZE = "pause_circle"
private const val PBD_ICON_MIRROR_VIEWPORT_EDIT = "crop_free"
private const val PBD_ICON_MIRROR_TOUCH_PROJECTION = "touch_app"
private const val PBD_ICON_FULLSCREEN_MOUSE = "mouse"
private const val PBD_ICON_FULLSCREEN_KEYBOARD = "keyboard"
private const val PBD_ICON_MACRO = "smart_button"
private const val PBD_ICON_PROFILE_SWITCHER = "swap_horiz"
private const val PBD_ICON_BACKGROUND_PEEK = "visibility"

/**
 * Maps a [PadAction] type to its localised label string resource.
 */
private fun PadAction.defaultLabelRes(): Int? =
    when (this) {
        is PadAction.LayoutNext -> R.string.macropad_action_layout_next
        is PadAction.LayoutPrevious -> R.string.macropad_action_layout_previous
        is PadAction.ProfileSwitcher -> R.string.macropad_action_profile_switcher
        is PadAction.MirrorPlayStop -> R.string.macropad_action_mirror_play_stop
        is PadAction.MirrorFreeze -> R.string.macropad_action_mirror_freeze
        is PadAction.MirrorViewportEdit -> R.string.macropad_action_mirror_viewport_edit
        is PadAction.MirrorTouchProjection -> R.string.macropad_action_mirror_touch_projection
        is PadAction.FullScreenMouse -> R.string.macropad_action_fullscreen_mouse
        is PadAction.FullScreenKeyboard -> R.string.macropad_action_fullscreen_keyboard
        is PadAction.Macro -> R.string.macropad_action_macro
        is PadAction.BackgroundPeek -> R.string.macropad_action_ambient_peek
        else -> null
    }

/** Default Material Symbols icon name for actions that behave like regular buttons in the editor. */
private fun PadAction.editorDefaultIconName(): String? =
    when (this) {
        is PadAction.LayoutNext -> PBD_ICON_LAYOUT_NEXT
        is PadAction.LayoutPrevious -> PBD_ICON_LAYOUT_PREVIOUS
        is PadAction.ProfileSwitcher -> PBD_ICON_PROFILE_SWITCHER
        is PadAction.MirrorPlayStop -> PBD_ICON_MIRROR_PLAY_STOP
        is PadAction.MirrorFreeze -> PBD_ICON_MIRROR_FREEZE
        is PadAction.MirrorViewportEdit -> PBD_ICON_MIRROR_VIEWPORT_EDIT
        is PadAction.MirrorTouchProjection -> PBD_ICON_MIRROR_TOUCH_PROJECTION
        is PadAction.FullScreenMouse -> PBD_ICON_FULLSCREEN_MOUSE
        is PadAction.FullScreenKeyboard -> PBD_ICON_FULLSCREEN_KEYBOARD
        is PadAction.Macro -> PBD_ICON_MACRO
        is PadAction.BackgroundPeek -> PBD_ICON_BACKGROUND_PEEK
        else -> null
    }

@Composable
internal fun EditButtonSubPageContent(
    button: PadButton?, // null → create new
    accentColor: Color,
    enableKeyboard: Boolean = true,
    enableGamepad: Boolean = true,
    enableMouse: Boolean = true,
    initialAction: PadAction? = null,
    selectedIcon: String? = null,
    onOpenIconPicker: (currentDraft: PadButton) -> Unit,
    onOpenAppPicker: (currentDraft: PadButton) -> Unit,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, onApplyColor: (Color) -> PadButton) -> Unit,
    onEditMacro: ((Macro) -> Unit)? = null,
    onSave: (PadButton) -> Unit,
) {
    val context = LocalContext.current
    val activeLayout = MacroPadState.activeLayout.collectAsState().value
    val initAction =
        button?.action
            ?: initialAction
            ?: PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    val initLabel =
        button?.label ?: when (val ia = initialAction) {
            is PadAction.Macro -> {
                MacroPadState.activeProfile.value
                    ?.macros
                    ?.firstOrNull { it.id == ia.macroId }
                    ?.name ?: ""
            }

            null -> {
                ""
            }

            else -> {
                ia.defaultLabelRes()?.let { context.getString(it) } ?: ""
            }
        }
    val initIconName = button?.iconName ?: initialAction?.editorDefaultIconName()
    var label by remember(button) { mutableStateOf(initLabel) }
    var iconName by remember(button, selectedIcon) { mutableStateOf(selectedIcon ?: initIconName) }
    var buttonShape by remember(button) { mutableStateOf(button?.buttonShape ?: ButtonShape.CIRCLE) }
    var buttonSize by remember(button) { mutableStateOf(button?.buttonSize ?: ButtonSize.SIZE_1X1) }
    var action by remember(button) { mutableStateOf(initAction) }
    var iconFilled by remember(button) { mutableStateOf(button?.iconFilled ?: true) }
    var hapticStrength by remember(button) { mutableStateOf(button?.hapticStrength ?: HapticStrength.OFF) }

    var hapticCustomDurationMs by remember(button) {
        mutableIntStateOf(
            when (button?.hapticStrength) {
                HapticStrength.LIGHT, HapticStrength.MEDIUM, HapticStrength.STRONG -> HF_PRESET_DURATION_MS
                else -> button?.hapticCustomDurationMs ?: HF_PRESET_DURATION_MS
            },
        )
    }
    var hapticCustomAmplitude by remember(button) {
        mutableIntStateOf(
            when (button?.hapticStrength) {
                HapticStrength.LIGHT -> HF_LIGHT_AMPLITUDE_USER
                HapticStrength.MEDIUM -> HF_MEDIUM_AMPLITUDE_USER
                HapticStrength.STRONG -> HF_STRONG_AMPLITUDE_USER
                else -> button?.hapticCustomAmplitude ?: HF_LIGHT_AMPLITUDE_USER
            },
        )
    }
    var buttonTextColor by remember(button) { mutableStateOf(button?.buttonTextColor) }
    var buttonBorderColor by remember(button) { mutableStateOf(button?.buttonBorderColor) }
    var buttonBgColor by remember(button) { mutableStateOf(button?.buttonBgColor) }
    var invisible by remember(button) { mutableStateOf(button?.invisible ?: (activeLayout?.invisibleButtons ?: false)) }

    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    val profile by MacroPadState.activeProfile.collectAsState()
    val macros = profile?.macros ?: emptyList()

    var actionBeforeEdit by remember { mutableStateOf<PadAction?>(null) }

    LaunchedEffect(macros) {
        val currentAction = action
        if (currentAction is PadAction.Macro) {
            val macroExists = macros.any { it.id == currentAction.macroId }
            if (!macroExists) {
                val revertTo = actionBeforeEdit
                if (revertTo != null) {
                    if (revertTo is PadAction.Macro) {
                        val revertMacroExists = macros.any { it.id == revertTo.macroId }
                        action = if (revertMacroExists) revertTo else initAction
                    } else {
                        action = revertTo
                    }
                } else {
                    action = initAction
                }
            }
        }
    }

    fun onActionChanged(newAction: PadAction) {
        AppLog.d(TAG, "onActionChanged: $newAction")
        action = newAction
        if (newAction is PadAction.ScrollWheel) {
            buttonSize = ButtonSize.SIZE_1X2
            label = ""
            iconName = null
            return
        }
        if (newAction is PadAction.TrackpointMove) {
            label = ""
            iconName = null
            buttonShape = ButtonShape.CIRCLE
            return
        }
        if (newAction is PadAction.AppLauncher) {
            label = ""
            iconName = null
            return
        }
        if (newAction is PadAction.Macro && label.isBlank()) {
            val macroName = macros.firstOrNull { it.id == newAction.macroId }?.name
            if (macroName != null) label = macroName
        }
        if (button == null || label.isBlank()) {
            val defaultLbl = newAction.defaultLabelRes()?.let { context.getString(it) } ?: ""
            val defaultIcon = newAction.editorDefaultIconName()
            if (defaultLbl.isNotEmpty()) label = defaultLbl
            if (defaultIcon != null) iconName = defaultIcon
        }
    }

    fun buildCurrentButton(): PadButton =
        button?.copy(
            label = label,
            iconName = iconName,
            iconFilled = iconFilled,
            buttonShape = buttonShape,
            buttonSize = buttonSize,
            action = action,
            hapticStrength = hapticStrength,
            hapticCustomDurationMs = hapticCustomDurationMs,
            hapticCustomAmplitude = hapticCustomAmplitude,
            buttonTextColor = buttonTextColor,
            buttonBorderColor = buttonBorderColor,
            buttonBgColor = buttonBgColor,
            invisible = invisible,
        ) ?: PadButton(
            id = UUID.randomUUID().toString(),
            label = label,
            iconName = iconName,
            iconFilled = iconFilled,
            posX = 0.5f,
            posY = 0.5f,
            buttonShape = buttonShape,
            buttonSize = buttonSize,
            action = action,
            hapticStrength = hapticStrength,
            hapticCustomDurationMs = hapticCustomDurationMs,
            hapticCustomAmplitude = hapticCustomAmplitude,
            buttonTextColor = buttonTextColor,
            buttonBorderColor = buttonBorderColor,
            buttonBgColor = buttonBgColor,
            invisible = invisible,
        )

    val isConfirmEnabled =
        when {
            action is PadAction.ScrollWheel || action is PadAction.TrackpointMove -> true
            action is PadAction.AppLauncher -> (action as PadAction.AppLauncher).packageName.isNotBlank()
            action is PadAction.Macro -> label.isNotBlank() && macros.any { it.id == (action as PadAction.Macro).macroId }
            else -> label.isNotBlank()
        }

    val layoutTextOpt = activeLayout?.buttonTextColor ?: ColorOption.Neutral
    val layoutBorderOpt = activeLayout?.buttonBorderColor ?: ColorOption.Neutral
    val layoutBgOpt = activeLayout?.buttonBgColor ?: ColorOption.Neutral

    val effectiveTextOpt = buttonTextColor ?: layoutTextOpt
    val effectiveBorderOpt = buttonBorderColor ?: layoutBorderOpt
    val effectiveBgOpt = buttonBgColor ?: layoutBgOpt

    val currentText = resolveColorOption(effectiveTextOpt, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val currentBorder = resolveColorOption(effectiveBorderOpt, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val currentBg = resolveColorOption(effectiveBgOpt, globalAccentColor, MP_AMBIENT_NEUTRAL_BG)

    val subPageTitle =
        when {
            button == null -> stringResource(R.string.macropad_editor_add_button)
            button.action is PadAction.TrackpointMove -> stringResource(R.string.macropad_action_trackpoint)
            else -> button.label.ifBlank { stringResource(R.string.settings_macropad_edit) }
        }

    val showLabelAndIcon = action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove && action !is PadAction.AppLauncher

    // Label & Icon input
    if (showLabelAndIcon) {
        GamepadTextFieldCard(
            title = stringResource(R.string.macropad_editor_button_label),
            description = stringResource(R.string.macropad_editor_button_label_desc),
            placeholder = stringResource(R.string.macropad_editor_button_label_placeholder),
            value = label,
            onValueChange = { label = it },
            icon = Icons.Rounded.Edit,
            modifier = Modifier.firstDeckItem(),
        )

        GamepadActionCard(
            title = stringResource(R.string.macropad_icon_picker_title),
            description = if (iconName != null) iconName!! else stringResource(R.string.macropad_icon_picker_search),
            actionText = stringResource(R.string.gamepad_action_choose_icon),
            icon = Icons.Rounded.Image,
            actionLeadingContent = {
                if (iconName != null) {
                    MaterialSymbol(
                        name = iconName!!,
                        size = 24.dp,
                        tint = accentColor,
                        filled = iconFilled,
                    )
                }
            },
            onClick = { onOpenIconPicker(buildCurrentButton()) },
        )
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_action),
        color = accentColor,
    )

    ActionPicker(
        current = action,
        accentColor = accentColor,
        enableKeyboard = enableKeyboard,
        enableGamepad = enableGamepad,
        enableMouse = enableMouse,
        onEditMacro = { macro ->
            actionBeforeEdit = action
            onEditMacro?.invoke(macro)
        },
        onOpenAppPicker = {
            onOpenAppPicker(buildCurrentButton())
        },
        onChange = ::onActionChanged,
    )

    if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove) {
        GamepadSectionHeader(
            text = stringResource(R.string.macropad_editor_button_shape),
            color = accentColor,
        )

        val shapeEntries = ButtonShape.entries
        val shapeLabels =
            listOf(
                stringResource(R.string.macropad_editor_shape_circle),
                stringResource(R.string.macropad_editor_shape_square),
                stringResource(R.string.macropad_editor_shape_icon_only),
            )
        val shapeIdx = shapeEntries.indexOf(buttonShape).coerceAtLeast(0)
        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_button_shape),
            description = stringResource(R.string.macropad_btn_shape_desc),
            selectedText = shapeLabels[shapeIdx],
            icon = Icons.Rounded.CropFree,
            onPrevious = {
                val nextIdx = (shapeIdx - 1 + shapeEntries.size) % shapeEntries.size
                buttonShape = shapeEntries[nextIdx]
            },
            onNext = {
                val nextIdx = (shapeIdx + 1) % shapeEntries.size
                buttonShape = shapeEntries[nextIdx]
            },
        )

        val sizeEntries = ButtonSize.entries
        val sizeIdx = sizeEntries.indexOf(buttonSize).coerceAtLeast(0)
        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_button_size),
            description = stringResource(R.string.macropad_btn_size_desc),
            selectedText = sizeEntries[sizeIdx].displayLabel(),
            icon = Icons.Rounded.CropFree,
            onPrevious = {
                val nextIdx = (sizeIdx - 1 + sizeEntries.size) % sizeEntries.size
                buttonSize = sizeEntries[nextIdx]
            },
            onNext = {
                val nextIdx = (sizeIdx + 1) % sizeEntries.size
                buttonSize = sizeEntries[nextIdx]
            },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.macropad_editor_section_haptic),
            color = accentColor,
        )

        val hapticEntries = HapticStrength.entries
        val hapticLabels =
            listOf(
                stringResource(R.string.macropad_haptic_off),
                stringResource(R.string.macropad_haptic_light),
                stringResource(R.string.macropad_haptic_medium),
                stringResource(R.string.macropad_haptic_strong),
                stringResource(R.string.macropad_haptic_custom),
            )
        val hapticIdx = hapticEntries.indexOf(hapticStrength).coerceAtLeast(0)
        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_section_haptic),
            description = stringResource(R.string.macropad_btn_haptic_desc),
            selectedText = hapticLabels[hapticIdx],
            icon = Icons.Rounded.Vibration,
            onPrevious = {
                val nextIdx = (hapticIdx - 1 + hapticEntries.size) % hapticEntries.size
                val selectedStrength = hapticEntries[nextIdx]
                when (selectedStrength) {
                    HapticStrength.LIGHT -> {
                        hapticCustomDurationMs = HF_PRESET_DURATION_MS
                        hapticCustomAmplitude = HF_LIGHT_AMPLITUDE_USER
                    }

                    HapticStrength.MEDIUM -> {
                        hapticCustomDurationMs = HF_PRESET_DURATION_MS
                        hapticCustomAmplitude = HF_MEDIUM_AMPLITUDE_USER
                    }

                    HapticStrength.STRONG -> {
                        hapticCustomDurationMs = HF_PRESET_DURATION_MS
                        hapticCustomAmplitude = HF_STRONG_AMPLITUDE_USER
                    }

                    else -> {}
                }
                hapticStrength = selectedStrength
            },
            onNext = {
                val nextIdx = (hapticIdx + 1) % hapticEntries.size
                val selectedStrength = hapticEntries[nextIdx]
                when (selectedStrength) {
                    HapticStrength.LIGHT -> {
                        hapticCustomDurationMs = HF_PRESET_DURATION_MS
                        hapticCustomAmplitude = HF_LIGHT_AMPLITUDE_USER
                    }

                    HapticStrength.MEDIUM -> {
                        hapticCustomDurationMs = HF_PRESET_DURATION_MS
                        hapticCustomAmplitude = HF_MEDIUM_AMPLITUDE_USER
                    }

                    HapticStrength.STRONG -> {
                        hapticCustomDurationMs = HF_PRESET_DURATION_MS
                        hapticCustomAmplitude = HF_STRONG_AMPLITUDE_USER
                    }

                    else -> {}
                }
                hapticStrength = selectedStrength
            },
        )

        if (hapticStrength == HapticStrength.CUSTOM) {
            GamepadStepperCard(
                title = stringResource(R.string.macropad_haptic_custom_duration),
                description = stringResource(R.string.macropad_btn_haptic_duration_desc),
                valueText = "$hapticCustomDurationMs ms",
                icon = Icons.Rounded.Vibration,
                onDecrement = {
                    hapticCustomDurationMs = (hapticCustomDurationMs - 10).coerceIn(10, 200)
                },
                onIncrement = {
                    hapticCustomDurationMs = (hapticCustomDurationMs + 10).coerceIn(10, 200)
                },
            )

            GamepadStepperCard(
                title = stringResource(R.string.macropad_haptic_custom_amplitude),
                description = stringResource(R.string.macropad_btn_haptic_amplitude_desc),
                valueText = "$hapticCustomAmplitude%",
                icon = Icons.Rounded.Vibration,
                onDecrement = {
                    hapticCustomAmplitude = (hapticCustomAmplitude - 5).coerceIn(5, 100)
                },
                onIncrement = {
                    hapticCustomAmplitude = (hapticCustomAmplitude + 5).coerceIn(5, 100)
                },
            )
        }

        GamepadSectionHeader(
            text = stringResource(R.string.layout_settings_colors_section_title),
            color = accentColor,
        )

        val buttonPreviewContent: @Composable () -> Unit = {
            PadButtonFace(
                width = 36.dp,
                height = 36.dp,
                shape = if (buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(6.dp),
                isIconOnly = buttonShape == ButtonShape.ICON_ONLY,
                isDeviceDisabled = false,
                borderColor = currentBorder,
                bgColor = currentBg,
                bgAlpha = 0.25f,
                gradientScale = 2.8f,
            ) {
                if (iconName != null) {
                    MaterialSymbol(
                        name = iconName!!,
                        size = 20.dp,
                        tint = currentText,
                        filled = iconFilled,
                    )
                } else {
                    Text(
                        text = label.ifBlank { "A" },
                        color = currentText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        val selectTextColorTitle = stringResource(R.string.layout_settings_select_text_color)
        val textBreadcrumbs =
            listOf(
                stringResource(R.string.macropad_editor_section_buttons),
                subPageTitle,
                stringResource(R.string.layout_settings_color_text),
            )
        // Text Color
        ColorOptionPaletteSection(
            title = stringResource(R.string.layout_settings_color_text),
            description = stringResource(R.string.macropad_editor_color_palette_desc),
            icon = Icons.Rounded.FormatColorText,
            colorOption = effectiveTextOpt,
            defaultNeutralColor = MP_AMBIENT_NEUTRAL_TEXT,
            globalAccentColor = globalAccentColor,
            onOptionSelected = { buttonTextColor = it },
            trailingContent = buttonPreviewContent,
            onOpenColorWheel = {
                val draft = buildCurrentButton()
                onOpenColorWheel(
                    selectTextColorTitle,
                    textBreadcrumbs,
                    currentText,
                ) { selected ->
                    draft.copy(buttonTextColor = ColorOption.Custom(selected.toArgb()))
                }
            },
        )

        val selectBorderColorTitle = stringResource(R.string.layout_settings_select_border_color)
        val borderBreadcrumbs =
            listOf(
                stringResource(R.string.macropad_editor_section_buttons),
                subPageTitle,
                stringResource(R.string.layout_settings_color_border),
            )
        // Border Color
        ColorOptionPaletteSection(
            title = stringResource(R.string.layout_settings_color_border),
            description = stringResource(R.string.macropad_editor_border_style_desc),
            icon = Icons.Rounded.Palette,
            colorOption = effectiveBorderOpt,
            defaultNeutralColor = MP_AMBIENT_NEUTRAL_BORDER,
            globalAccentColor = globalAccentColor,
            onOptionSelected = { buttonBorderColor = it },
            trailingContent = buttonPreviewContent,
            onOpenColorWheel = {
                val draft = buildCurrentButton()
                onOpenColorWheel(
                    selectBorderColorTitle,
                    borderBreadcrumbs,
                    currentBorder,
                ) { selected ->
                    draft.copy(buttonBorderColor = ColorOption.Custom(selected.toArgb()))
                }
            },
        )

        val selectBgColorTitle = stringResource(R.string.layout_settings_select_bg_color)
        val bgBreadcrumbs =
            listOf(
                stringResource(R.string.macropad_editor_section_buttons),
                subPageTitle,
                stringResource(R.string.layout_settings_color_bg),
            )
        // Background Color
        ColorOptionPaletteSection(
            title = stringResource(R.string.layout_settings_color_bg),
            description = stringResource(R.string.macropad_editor_fill_style_desc),
            icon = Icons.Rounded.FormatColorFill,
            colorOption = effectiveBgOpt,
            defaultNeutralColor = MP_AMBIENT_NEUTRAL_BG,
            globalAccentColor = globalAccentColor,
            onOptionSelected = { buttonBgColor = it },
            trailingContent = buttonPreviewContent,
            onOpenColorWheel = {
                val draft = buildCurrentButton()
                onOpenColorWheel(
                    selectBgColorTitle,
                    bgBreadcrumbs,
                    currentBg,
                ) { selected ->
                    draft.copy(buttonBgColor = ColorOption.Custom(selected.toArgb()))
                }
            },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.macropad_editor_section_visibility_behavior),
            color = accentColor,
        )

        GamepadToggleCard(
            title = stringResource(R.string.layout_settings_invisible_buttons),
            description = stringResource(R.string.layout_settings_invisible_buttons_desc),
            checked = invisible,
            icon = Icons.Rounded.VisibilityOff,
            onCheckedChange = { invisible = it },
        )
    }

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_done),
        description = stringResource(R.string.macropad_editor_appearance_desc),
        actionText = stringResource(R.string.macropad_editor_done),
        enabled = isConfirmEnabled,
        onClick = {
            if (isConfirmEnabled) {
                val result = buildCurrentButton()
                AppLog.d(TAG, "Confirm button edit: id=${result.id} label=${result.label} action=${result.action}")
                onSave(result)
            }
        },
    )
}
