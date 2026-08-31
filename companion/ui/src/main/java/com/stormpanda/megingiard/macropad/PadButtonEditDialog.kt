package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.math.nextItem
import com.stormpanda.megingiard.math.prevItem
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoColumnGrid
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import com.stormpanda.megingiard.ui.toHexLabel
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "PadButtonEditDialog"

private val PBD_COLOR_PREVIEW_SIZE = 36.dp
private val PBD_CORNER_RADIUS_DP = 6.dp
private val PBD_ICON_SIZE_DP = 20.dp

internal fun MouseButton.labelRes(): Int =
    when (this) {
        MouseButton.LEFT -> R.string.macropad_mouse_btn_left
        MouseButton.RIGHT -> R.string.macropad_mouse_btn_right
        MouseButton.MIDDLE -> R.string.macropad_mouse_btn_middle
        MouseButton.MOUSE4 -> R.string.macropad_mouse_btn_back
        MouseButton.MOUSE5 -> R.string.macropad_mouse_btn_forward
    }

@Composable
internal fun describeButtonColorOption(
    option: ColorOption?,
    resolvedColor: Color,
): String =
    when (option) {
        null -> stringResource(R.string.layout_settings_color_layout_default)
        is ColorOption.Neutral -> stringResource(R.string.layout_settings_color_neutral)
        is ColorOption.Accent -> stringResource(R.string.layout_settings_color_accent)
        is ColorOption.Custom -> resolvedColor.toHexLabel()
    }

/**
 * Deck sub-page allowing the user to select the Button Type (ActionGroup) before editing button details.
 */
@Composable
internal fun ChooseButtonTypeSubPageContent(
    enableKeyboard: Boolean = true,
    enableGamepad: Boolean = true,
    enableMouse: Boolean = true,
    onSelectType: (ActionGroup) -> Unit,
) {
    val privdState by PrivdManager.state.collectAsState()
    val isPrivdRunning = privdState == PrivdState.RUNNING
    val profile by MacroPadState.activeProfile.collectAsState()
    val hasMacros = isPrivdRunning && (profile?.macros?.isNotEmpty() == true)
    val availableGroups =
        remember(hasMacros, enableKeyboard, enableGamepad, enableMouse, isPrivdRunning) {
            ActionGroup.entries.filter { group ->
                if (group == ActionGroup.MACRO && !isPrivdRunning) return@filter false
                group.actions().any { category ->
                    category.isEnabled(enableKeyboard, enableGamepad, enableMouse, hasMacros)
                }
            }
        }

    GamepadTwoColumnGrid(
        items = availableGroups,
    ) { group, _, cardModifier ->
        GamepadActionCard(
            title = stringResource(group.labelResId()),
            description = stringResource(group.descriptionResId()),
            icon = group.icon(),
            alwaysShowFullDescription = true,
            onClick = { onSelectType(group) },
            modifier = cardModifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditButtonSubPageContent(
    button: PadButton?, // null → create new
    savedButton: PadButton? = null,
    accentColor: Color,
    enableKeyboard: Boolean = true,
    enableGamepad: Boolean = true,
    enableMouse: Boolean = true,
    initialAction: PadAction? = null,
    selectedIcon: String? = null,
    onOpenIconPicker: (currentDraft: PadButton) -> Unit,
    onOpenAppPicker: (currentDraft: PadButton) -> Unit,
    onOpenColorSubMenu: (currentDraft: PadButton, target: ButtonColorTarget) -> Unit,
    onOpenKeyboardPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenGamepadPicker: ((currentDraft: PadButton, slotIndex: Int) -> Unit)? = null,
    onOpenMousePicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenMirrorPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenOverlayPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenLayoutPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenMacroPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onDuplicate: ((PadButton) -> Unit)? = null,
    onCopyToLayout: ((PadButton) -> Unit)? = null,
    onDelete: ((PadButton) -> Unit)? = null,
    onDiscard: () -> Unit = {},
    onSave: (PadButton) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val activeLayout = MacroPadState.activeLayout.collectAsState().value
    val stableButtonId = remember(button?.id) { button?.id ?: UUID.randomUUID().toString() }

    DisposableEffect(stableButtonId) {
        MacroPadState.setSelectedButtonId(stableButtonId)
        onDispose {
            MacroPadState.setSelectedButtonId(null)
            MacroPadState.setPreviewButton(null)
        }
    }

    val defaultButtonLabel = stringResource(R.string.macropad_editor_new_button_default_label)
    val initAction =
        button?.action
            ?: initialAction
            ?: PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    val initLabel =
        button?.label?.ifBlank { defaultButtonLabel }
            ?: defaultButtonLabel
    val initIconName = button?.iconName ?: selectedIcon
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

    LaunchedEffect(macros) {
        val currentAction = action
        if (currentAction is PadAction.Macro) {
            val macroExists = macros.any { it.id == currentAction.macroId }
            if (!macroExists) {
                action = initAction
            }
        }
    }

    fun onActionChanged(newAction: PadAction) {
        AppLog.d(TAG, "onActionChanged: $newAction")
        action = newAction
        if (newAction is PadAction.ScrollWheel) {
            buttonSize = ButtonSize.SIZE_1X2
            return
        }
        if (newAction is PadAction.TrackpointMove) {
            buttonShape = ButtonShape.CIRCLE
            return
        }
    }

    val currentButton =
        remember(
            stableButtonId,
            button?.posX,
            button?.posY,
            label,
            iconName,
            iconFilled,
            buttonShape,
            buttonSize,
            action,
            hapticStrength,
            hapticCustomDurationMs,
            hapticCustomAmplitude,
            buttonTextColor,
            buttonBorderColor,
            buttonBgColor,
            invisible,
        ) {
            PadButton(
                id = stableButtonId,
                label = label,
                iconName = iconName,
                iconFilled = iconFilled,
                posX = button?.posX ?: 0.5f,
                posY = button?.posY ?: 0.5f,
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
        }

    val isConfirmEnabled =
        when {
            action is PadAction.ScrollWheel || action is PadAction.TrackpointMove -> true
            action is PadAction.AppLauncher -> (action as PadAction.AppLauncher).packageName.isNotBlank()
            action is PadAction.Macro -> label.isNotBlank() && macros.any { it.id == (action as PadAction.Macro).macroId }
            else -> label.isNotBlank()
        }

    val isNew = savedButton == null

    LaunchedEffect(currentButton) {
        MacroPadState.setPreviewButton(currentButton)
    }

    val hasChanges =
        isNew || currentButton.copy(posX = savedButton.posX, posY = savedButton.posY) != savedButton

    val layoutTextOpt = activeLayout?.buttonTextColor ?: ColorOption.Neutral
    val layoutBorderOpt = activeLayout?.buttonBorderColor ?: ColorOption.Neutral
    val layoutBgOpt = activeLayout?.buttonBgColor ?: ColorOption.Neutral

    val effectiveTextOpt = buttonTextColor ?: layoutTextOpt
    val effectiveBorderOpt = buttonBorderColor ?: layoutBorderOpt
    val effectiveBgOpt = buttonBgColor ?: layoutBgOpt

    val currentText = resolveColorOption(effectiveTextOpt, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val currentBorder = resolveColorOption(effectiveBorderOpt, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val currentBg = resolveBgColorOption(effectiveBgOpt, globalAccentColor)

    val showLabelAndIcon = action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove && action !is PadAction.AppLauncher

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (isConfirmEnabled) {
                    AppLog.d(TAG, "Confirm button edit: id=${currentButton.id} label=${currentButton.label} action=${currentButton.action}")
                    onSave(currentButton)
                }
            },
            onDiscard = onDiscard,
        )

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
            onClick = { onOpenIconPicker(currentButton) },
        )
    }

    ActionPicker(
        current = action,
        accentColor = accentColor,
        enableKeyboard = enableKeyboard,
        enableGamepad = enableGamepad,
        enableMouse = enableMouse,
        isFirstItem = !showLabelAndIcon,
        onOpenMacroPicker = {
            onOpenMacroPicker?.invoke(currentButton)
        },
        onOpenAppPicker = {
            onOpenAppPicker(currentButton)
        },
        onOpenKeyboardPicker = {
            onOpenKeyboardPicker?.invoke(currentButton)
        },
        onOpenGamepadPicker = { slotIndex ->
            onOpenGamepadPicker?.invoke(currentButton, slotIndex)
        },
        onOpenMousePicker = {
            onOpenMousePicker?.invoke(currentButton)
        },
        onOpenMirrorPicker = {
            onOpenMirrorPicker?.invoke(currentButton)
        },
        onOpenOverlayPicker = {
            onOpenOverlayPicker?.invoke(currentButton)
        },
        onOpenLayoutPicker = {
            onOpenLayoutPicker?.invoke(currentButton)
        },
        onChange = ::onActionChanged,
    )

    if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove) {
        GamepadSectionHeader(
            text = stringResource(R.string.macropad_editor_section_shape_size),
            color = accentColor,
        )

        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_button_shape),
            description = stringResource(R.string.macropad_btn_shape_desc),
            selectedText = buttonShape.displayLabel(),
            icon = Icons.Rounded.CropFree,
            onPrevious = { buttonShape = ButtonShape.entries.prevItem(buttonShape) },
            onNext = { buttonShape = ButtonShape.entries.nextItem(buttonShape) },
        )

        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_button_size),
            description = stringResource(R.string.macropad_btn_size_desc),
            selectedText = buttonSize.displayLabel(),
            icon = Icons.Rounded.CropFree,
            onPrevious = { buttonSize = ButtonSize.entries.prevItem(buttonSize) },
            onNext = { buttonSize = ButtonSize.entries.nextItem(buttonSize) },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.macropad_editor_section_haptic),
            color = accentColor,
        )

        val applyHapticStrength: (HapticStrength) -> Unit = { selectedStrength ->
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
        }

        val hapticSelectedText = stringResource(hapticStrength.labelResId())

        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_section_haptic),
            description = stringResource(R.string.macropad_btn_haptic_desc),
            selectedText = hapticSelectedText,
            icon = Icons.Rounded.Vibration,
            onPrevious = { applyHapticStrength(HapticStrength.entries.prevItem(hapticStrength)) },
            onNext = { applyHapticStrength(HapticStrength.entries.nextItem(hapticStrength)) },
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
            text = stringResource(R.string.macropad_editor_section_button_colors),
            color = accentColor,
        )

        val previewLabel = stringResource(R.string.macropad_editor_button_preview_text)
        val buttonPreviewLeading: (textColor: Color, borderColor: Color, bgColor: Color, isIconOnly: Boolean) -> @Composable () -> Unit =
            { tColor, bColor, bgCol, iconOnly ->
                {
                    PadButtonFace(
                        width = PBD_COLOR_PREVIEW_SIZE,
                        height = PBD_COLOR_PREVIEW_SIZE,
                        shape = if (buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(PBD_CORNER_RADIUS_DP),
                        isIconOnly = iconOnly || buttonShape == ButtonShape.ICON_ONLY,
                        isDeviceDisabled = false,
                        borderColor = bColor,
                        bgColor = bgCol,
                    ) {
                        if (iconName != null) {
                            MaterialSymbol(
                                name = iconName!!,
                                size = PBD_ICON_SIZE_DP,
                                tint = tColor,
                                filled = iconFilled,
                            )
                        } else {
                            Text(
                                text = previewLabel,
                                color = tColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

        // ── Text Color Menu Item ────────────────────────────────────
        GamepadActionCard(
            title = stringResource(R.string.layout_settings_color_text),
            description = describeButtonColorOption(buttonTextColor, currentText),
            icon = Icons.Rounded.FormatColorText,
            actionLeadingContent = buttonPreviewLeading(currentText, Color.Transparent, Color.Transparent, true),
            onClick = { onOpenColorSubMenu(currentButton, ButtonColorTarget.TEXT) },
        )

        // ── Border Color Menu Item ──────────────────────────────────
        GamepadActionCard(
            title = stringResource(R.string.layout_settings_color_border),
            description = describeButtonColorOption(buttonBorderColor, currentBorder),
            icon = Icons.Rounded.Palette,
            actionLeadingContent = buttonPreviewLeading(Color.Transparent, currentBorder, Color.Transparent, false),
            onClick = { onOpenColorSubMenu(currentButton, ButtonColorTarget.BORDER) },
        )

        // ── Background / Fading Color Menu Item ─────────────────────
        GamepadActionCard(
            title = stringResource(R.string.layout_settings_color_bg),
            description = describeButtonColorOption(buttonBgColor, currentBg),
            icon = Icons.Rounded.FormatColorFill,
            actionLeadingContent = buttonPreviewLeading(Color.Transparent, Color.Transparent, currentBg, false),
            onClick = { onOpenColorSubMenu(currentButton, ButtonColorTarget.BG) },
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

        if (button != null) {
            GamepadSectionHeader(
                text = stringResource(R.string.macropad_editor_manage_buttons),
                color = accentColor,
            )

            if (onDuplicate != null) {
                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_copy_button_duplicate),
                    description = stringResource(R.string.macropad_editor_duplicate_button_desc),
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { onDuplicate(button) },
                )
            }

            if (onCopyToLayout != null) {
                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_copy_to_layout),
                    description = stringResource(R.string.macropad_editor_copy_button_to_layout_desc),
                    icon = Icons.Rounded.Share,
                    onClick = { onCopyToLayout(button) },
                )
            }
        }

        // ── Save / Save & Delete Section ─────────────────────────────────
        val hasDelete = button != null && onDelete != null
        GamepadSectionHeader(
            text =
                stringResource(
                    if (hasDelete) {
                        R.string.macropad_editor_section_save_and_delete
                    } else {
                        R.string.macropad_editor_section_save
                    },
                ),
            color = accentColor,
        )

        // ── Save & Exit Action Row ───────────────────────────────────────
        GamepadSaveExitActionRow(
            title = stringResource(if (isNew) R.string.macropad_editor_create_button_title else R.string.macropad_editor_save_button_title),
            description =
                stringResource(
                    if (isNew) R.string.macropad_editor_create_button_desc else R.string.macropad_editor_save_button_desc,
                ),
            pulseOnChanges = hasChanges,
            saveActionText = stringResource(if (isNew) R.string.gamepad_action_create else R.string.gamepad_action_save),
            saveIcon = Icons.Rounded.Save,
            enabled = isConfirmEnabled,
            showExitPrompt = promptState.showExitPrompt,
            onDismissPrompt = promptState.dismissPrompt,
            saveFocusRequester = promptState.focusRequester,
            bringIntoViewRequester = promptState.bringIntoViewRequester,
            onSave = promptState.onSave,
            onDiscard = promptState.onDiscard,
        )

        // ── Button Deletion (Last Item) ──────────────────────────────────
        if (button != null && onDelete != null) {
            GamepadTwoStepConfirmCard(
                title = stringResource(R.string.macropad_editor_delete_button),
                confirmTitle = stringResource(R.string.macropad_button_delete_confirm_title),
                description =
                    stringResource(
                        R.string.macropad_editor_delete_button_desc,
                        button.label.ifBlank { button.action.displayLabel() },
                    ),
                actionText = stringResource(R.string.gamepad_action_delete),
                confirmActionText = stringResource(R.string.gamepad_action_confirm),
                icon = Icons.Rounded.Delete,
                isDestructive = true,
                onConfirm = { onDelete(button) },
            )
        }
    }
}

@Composable
internal fun ButtonColorSubPageContent(
    button: PadButton,
    savedButton: PadButton?,
    activeLayout: PadLayout?,
    target: ButtonColorTarget,
    accentColor: Color,
    onColorOptionChanged: (ColorOption?) -> Unit,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, inFlightButton: PadButton) -> Unit,
) {
    val currentOption =
        when (target) {
            ButtonColorTarget.TEXT -> button.buttonTextColor
            ButtonColorTarget.BORDER -> button.buttonBorderColor
            ButtonColorTarget.BG -> button.buttonBgColor
        }

    val layoutDefaultOption =
        when (target) {
            ButtonColorTarget.TEXT -> activeLayout?.buttonTextColor ?: ColorOption.Neutral
            ButtonColorTarget.BORDER -> activeLayout?.buttonBorderColor ?: ColorOption.Neutral
            ButtonColorTarget.BG -> activeLayout?.buttonBgColor ?: ColorOption.Neutral
        }

    val defaultNeutralColor =
        when (target) {
            ButtonColorTarget.TEXT -> MP_AMBIENT_NEUTRAL_TEXT
            ButtonColorTarget.BORDER -> MP_AMBIENT_NEUTRAL_BORDER
            ButtonColorTarget.BG -> MP_AMBIENT_NEUTRAL_BG
        }

    val targetTitle =
        when (target) {
            ButtonColorTarget.TEXT -> stringResource(R.string.layout_settings_color_text)
            ButtonColorTarget.BORDER -> stringResource(R.string.layout_settings_color_border)
            ButtonColorTarget.BG -> stringResource(R.string.layout_settings_color_bg)
        }

    val selectColorWheelTitle =
        when (target) {
            ButtonColorTarget.TEXT -> stringResource(R.string.layout_settings_select_text_color)
            ButtonColorTarget.BORDER -> stringResource(R.string.layout_settings_select_border_color)
            ButtonColorTarget.BG -> stringResource(R.string.layout_settings_select_bg_color)
        }

    val colorWheelBreadcrumbs =
        listOf(
            stringResource(R.string.macropad_editor_section_buttons),
            button.label.ifBlank { stringResource(R.string.macropad_editor_section_button_settings) },
            targetTitle,
            stringResource(R.string.gamepad_action_custom_color),
        )

    ColorOptionSubPageContent(
        currentOption = currentOption,
        layoutDefaultOption = layoutDefaultOption,
        defaultNeutralColor = defaultNeutralColor,
        isBgTarget = target == ButtonColorTarget.BG,
        selectColorWheelTitle = selectColorWheelTitle,
        colorWheelBreadcrumbs = colorWheelBreadcrumbs,
        onColorOptionChanged = onColorOptionChanged,
        onOpenColorWheel = { title, breadcrumbs, initialColor ->
            onOpenColorWheel(title, breadcrumbs, initialColor, button)
        },
    )
}
