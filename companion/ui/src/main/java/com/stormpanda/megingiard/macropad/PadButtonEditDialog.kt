package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.ColorWheelPicker
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadColorPaletteCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
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
private val PBD_PREVIEW_BUTTON_SIZE = 60.dp

private val PBD_PALETTE_PRESETS =
    listOf(
        Color(0xFFFF5252), // Red
        Color(0xFFFF7043), // Deep Orange
        Color(0xFFFFA726), // Orange
        Color(0xFFFFCA28), // Amber
        Color(0xFF66BB6A), // Green
        Color(0xFF26A69A), // Teal
        Color(0xFF29B6F6), // Light Blue
        Color(0xFF42A5F5), // Blue
        Color(0xFF7E57C2), // Deep Purple
        Color(0xFFEC407A), // Pink
        Color(0xFFFFFFFF), // White
        Color(0xFF212121), // Dark Grey
    )

private enum class ButtonColorPickerTarget { TEXT, BORDER, BG }

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
    onOpenIconPicker: () -> Unit,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, onSave: (Color) -> Unit) -> Unit,
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

    val colors = LocalAppColors.current
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

    GamepadSubPageHeader(
        parentTitle = stringResource(R.string.macropad_editor_section_buttons),
        subPageTitle = subPageTitle,
        accentColor = accentColor,
    )

    // Live preview banner
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        PadButtonFace(
            width = PBD_PREVIEW_BUTTON_SIZE,
            height = PBD_PREVIEW_BUTTON_SIZE,
            shape = if (buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp),
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
                    size = 32.dp,
                    tint = currentText,
                    filled = iconFilled,
                )
            } else {
                Text(
                    text = label.ifBlank { "A" },
                    color = currentText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_button_settings),
        color = accentColor,
    )

    // Label & Icon input
    if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove && action !is PadAction.AppLauncher) {
        AppTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.macropad_editor_button_label), color = colors.onSurfaceSecondary) },
            modifier = Modifier.fillMaxWidth(),
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
            onClick = onOpenIconPicker,
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
            text = stringResource(R.string.button_settings_colors_section_title),
            color = accentColor,
        )

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
            onOpenColorWheel = {
                onOpenColorWheel(
                    selectTextColorTitle,
                    textBreadcrumbs,
                    currentText,
                ) { selected ->
                    buttonTextColor = ColorOption.Custom(selected.toArgb())
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
            onOpenColorWheel = {
                onOpenColorWheel(
                    selectBorderColorTitle,
                    borderBreadcrumbs,
                    currentBorder,
                ) { selected ->
                    buttonBorderColor = ColorOption.Custom(selected.toArgb())
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
            onOpenColorWheel = {
                onOpenColorWheel(
                    selectBgColorTitle,
                    bgBreadcrumbs,
                    currentBg,
                ) { selected ->
                    buttonBgColor = ColorOption.Custom(selected.toArgb())
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
                val result =
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
                AppLog.d(TAG, "Confirm button edit: id=${result.id} label=${result.label} action=${result.action}")
                onSave(result)
            }
        },
    )
}

@Composable
internal fun ButtonEditDialog(
    button: PadButton?, // null → create new
    accentColor: Color,
    enableKeyboard: Boolean = true,
    enableGamepad: Boolean = true,
    enableMouse: Boolean = true,
    initialAction: PadAction? = null,
    onEditMacro: ((Macro) -> Unit)? = null,
    onConfirm: (PadButton) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var showIconPicker by remember { mutableStateOf(false) }
    var activeColorPickerTarget by remember { mutableStateOf<ButtonColorPickerTarget?>(null) }

    // TODO: State management for color wheel and icon picker
    // This implementation is a placeholder for the integration pattern
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .blockPointerEvents(),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.appBackground,
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Implementation would call EditButtonSubPageContent
            }
        }
    }
}

@Composable
internal fun ColorOptionPaletteSection(
    title: String,
    description: String,
    icon: ImageVector,
    colorOption: ColorOption,
    defaultNeutralColor: Color,
    globalAccentColor: Color,
    onOptionSelected: (ColorOption) -> Unit,
    onOpenColorWheel: () -> Unit,
) {
    val currentColor = resolveColorOption(colorOption, globalAccentColor, defaultNeutralColor)
    val isCustom = colorOption is ColorOption.Custom && PBD_PALETTE_PRESETS.none { it.toArgb() == colorOption.argb }

    val paletteList =
        remember(globalAccentColor, defaultNeutralColor) {
            listOf(defaultNeutralColor, globalAccentColor) + PBD_PALETTE_PRESETS
        }

    GamepadColorPaletteCard(
        title = title,
        description = description,
        icon = icon,
        paletteColors = paletteList,
        selectedColor = if (isCustom) Color.Transparent else currentColor,
        onColorSelected = { selected ->
            val option =
                when (selected) {
                    defaultNeutralColor -> ColorOption.Neutral
                    globalAccentColor -> ColorOption.Accent
                    else -> ColorOption.Custom(selected.toArgb())
                }
            onOptionSelected(option)
        },
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_accent_custom_title),
        description = stringResource(R.string.macropad_editor_color_wheel_desc),
        actionText = stringResource(R.string.gamepad_action_color_wheel),
        icon = Icons.Rounded.Colorize,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = currentColor,
                isSelected = isCustom,
            )
        },
        onClick = onOpenColorWheel,
    )
}
