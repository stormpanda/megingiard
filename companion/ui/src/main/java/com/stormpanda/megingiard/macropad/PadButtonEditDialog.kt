package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.lerp
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
import java.util.UUID

private const val TAG = "PadButtonEditDialog"
private const val PBD_PULSE_DURATION_MS = 1400
private const val PBD_PULSE_ACCENT_ALPHA = 0.35f
private const val PBD_PULSE_SURFACE_ALPHA = 0.55f
private const val PBD_ARROW_ALPHA = 0.6f
private const val PBD_PREVIEW_BG_ALPHA = 0.25f
private const val PBD_PREVIEW_GRADIENT_SCALE = 2.8f
private val PBD_SAVE_PREVIEW_SPACING = 8.dp
private val PBD_ARROW_SIZE = 16.dp
private val PBD_COLOR_PREVIEW_SIZE = 36.dp
private val PBD_CORNER_RADIUS_DP = 6.dp
private val PBD_ICON_SIZE_DP = 20.dp
private val PBD_GRID_SPACING = 10.dp

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
        is ColorOption.Custom -> String.format("#%06X", 0xFFFFFF and resolvedColor.toArgb())
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
    val profile by MacroPadState.activeProfile.collectAsState()
    val hasMacros = profile?.macros?.isNotEmpty() == true
    val availableGroups =
        remember(hasMacros, enableKeyboard, enableGamepad, enableMouse) {
            ActionGroup.entries.filter { group ->
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
            actionText = stringResource(R.string.gamepad_action_choose),
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
    onOpenGamepadPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenMousePicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenMirrorPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenOverlayPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenLayoutPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onEditMacro: ((Macro) -> Unit)? = null,
    onDuplicate: ((PadButton) -> Unit)? = null,
    onCopyToLayout: ((PadButton) -> Unit)? = null,
    onDelete: ((PadButton) -> Unit)? = null,
    onDiscard: () -> Unit = {},
    onSave: (PadButton) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val activeLayout = MacroPadState.activeLayout.collectAsState().value
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
            return
        }
        if (newAction is PadAction.TrackpointMove) {
            buttonShape = ButtonShape.CIRCLE
            return
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

    val isNew = savedButton == null
    val currentButton = buildCurrentButton()
    val hasChanges =
        isNew || currentButton.copy(posX = savedButton.posX, posY = savedButton.posY) != savedButton

    val pulseTransition = rememberInfiniteTransition(label = "btnEditSavePulse")
    val pulseFraction by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = PBD_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "btnEditSavePulseFraction",
    )
    val saveCardBgColor =
        if (hasChanges) {
            lerp(
                colors.surface.copy(alpha = PBD_PULSE_SURFACE_ALPHA),
                colors.accent.copy(alpha = PBD_PULSE_ACCENT_ALPHA),
                pulseFraction,
            )
        } else {
            null
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

    // ── Save Option at the Very Top ─────────────────────────────────
    GamepadSaveExitActionRow(
        title = stringResource(if (isNew) R.string.macropad_editor_create_button_title else R.string.macropad_editor_save_button_title),
        description = stringResource(if (isNew) R.string.macropad_editor_create_button_desc else R.string.macropad_editor_save_button_desc),
        cardBgColor = saveCardBgColor,
        saveActionText = stringResource(if (isNew) R.string.gamepad_action_create else R.string.gamepad_action_save),
        saveIcon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        showExitPrompt = promptState.showExitPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
        modifier = Modifier.firstDeckItem(),
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
            onClick = { onOpenIconPicker(currentButton) },
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
            onOpenAppPicker(currentButton)
        },
        onOpenKeyboardPicker = {
            onOpenKeyboardPicker?.invoke(currentButton)
        },
        onOpenGamepadPicker = {
            onOpenGamepadPicker?.invoke(currentButton)
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
                        bgAlpha = PBD_PREVIEW_BG_ALPHA,
                        gradientScale = PBD_PREVIEW_GRADIENT_SCALE,
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
                                text = label.ifBlank { "A" },
                                color = tColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
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
            actionText = stringResource(R.string.gamepad_action_edit),
            onClick = { onOpenColorSubMenu(buildCurrentButton(), ButtonColorTarget.TEXT) },
        )

        // ── Border Color Menu Item ──────────────────────────────────
        GamepadActionCard(
            title = stringResource(R.string.layout_settings_color_border),
            description = describeButtonColorOption(buttonBorderColor, currentBorder),
            icon = Icons.Rounded.Palette,
            actionLeadingContent = buttonPreviewLeading(Color.Transparent, currentBorder, Color.Transparent, false),
            actionText = stringResource(R.string.gamepad_action_edit),
            onClick = { onOpenColorSubMenu(buildCurrentButton(), ButtonColorTarget.BORDER) },
        )

        // ── Background / Fading Color Menu Item ─────────────────────
        GamepadActionCard(
            title = stringResource(R.string.layout_settings_color_bg),
            description = describeButtonColorOption(buttonBgColor, currentBg),
            icon = Icons.Rounded.FormatColorFill,
            actionLeadingContent = buttonPreviewLeading(Color.Transparent, Color.Transparent, currentBg, false),
            actionText = stringResource(R.string.gamepad_action_edit),
            onClick = { onOpenColorSubMenu(buildCurrentButton(), ButtonColorTarget.BG) },
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
                    description = stringResource(R.string.macropad_editor_duplicate_layout_desc),
                    actionText = stringResource(R.string.gamepad_action_duplicate),
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { onDuplicate(button) },
                )
            }

            if (onCopyToLayout != null) {
                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_copy_to_layout),
                    description = stringResource(R.string.macropad_editor_copy_layout_desc),
                    actionText = stringResource(R.string.gamepad_action_copy),
                    icon = Icons.Rounded.Share,
                    onClick = { onCopyToLayout(button) },
                )
            }

            if (onDelete != null) {
                GamepadTwoStepConfirmCard(
                    title = stringResource(R.string.macropad_editor_delete_button),
                    confirmTitle = stringResource(R.string.macropad_button_delete_confirm_title),
                    description =
                        stringResource(
                            R.string.macropad_editor_delete_layout_desc,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ButtonColorSubPageContent(
    button: PadButton,
    savedButton: PadButton?,
    activeLayout: PadLayout?,
    target: ButtonColorTarget,
    accentColor: Color,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, inFlightButton: PadButton) -> Unit,
    onDiscard: () -> Unit = {},
    onSave: (inFlightButton: PadButton) -> Unit,
) {
    val colors = LocalAppColors.current
    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    val initialOption =
        when (target) {
            ButtonColorTarget.TEXT -> button.buttonTextColor
            ButtonColorTarget.BORDER -> button.buttonBorderColor
            ButtonColorTarget.BG -> button.buttonBgColor
        }
    var selectedOption by remember(button.id, target, initialOption) { mutableStateOf(initialOption) }

    val effectiveSavedButton = savedButton ?: button

    val savedBaselineOption =
        when (target) {
            ButtonColorTarget.TEXT -> effectiveSavedButton.buttonTextColor
            ButtonColorTarget.BORDER -> effectiveSavedButton.buttonBorderColor
            ButtonColorTarget.BG -> effectiveSavedButton.buttonBgColor
        }

    val hasChanges = selectedOption != savedBaselineOption

    val pulseTransition = rememberInfiniteTransition(label = "btnColorSubPageSavePulse")
    val pulseFraction by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = PBD_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "btnColorSavePulseFraction",
    )
    val saveCardBgColor =
        if (hasChanges) {
            lerp(
                colors.surface.copy(alpha = PBD_PULSE_SURFACE_ALPHA),
                colors.accent.copy(alpha = PBD_PULSE_ACCENT_ALPHA),
                pulseFraction,
            )
        } else {
            null
        }

    fun buildInFlightButton(): PadButton =
        when (target) {
            ButtonColorTarget.TEXT -> button.copy(buttonTextColor = selectedOption)
            ButtonColorTarget.BORDER -> button.copy(buttonBorderColor = selectedOption)
            ButtonColorTarget.BG -> button.copy(buttonBgColor = selectedOption)
        }

    val inFlightButton = buildInFlightButton()

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

    val currentColor = resolveColorOption(selectedOption ?: layoutDefaultOption, globalAccentColor, defaultNeutralColor)
    val layoutResolvedColor = resolveColorOption(layoutDefaultOption, globalAccentColor, defaultNeutralColor)

    val savedResolvedText =
        resolveColorOption(
            effectiveSavedButton.buttonTextColor ?: (activeLayout?.buttonTextColor ?: ColorOption.Neutral),
            globalAccentColor,
            MP_AMBIENT_NEUTRAL_TEXT,
        )
    val savedResolvedBorder =
        resolveColorOption(
            effectiveSavedButton.buttonBorderColor ?: (activeLayout?.buttonBorderColor ?: ColorOption.Neutral),
            globalAccentColor,
            MP_AMBIENT_NEUTRAL_BORDER,
        )
    val savedResolvedBg =
        resolveColorOption(
            effectiveSavedButton.buttonBgColor ?: (activeLayout?.buttonBgColor ?: ColorOption.Neutral),
            globalAccentColor,
            MP_AMBIENT_NEUTRAL_BG,
        )

    val currentResolvedText =
        resolveColorOption(
            inFlightButton.buttonTextColor ?: (activeLayout?.buttonTextColor ?: ColorOption.Neutral),
            globalAccentColor,
            MP_AMBIENT_NEUTRAL_TEXT,
        )
    val currentResolvedBorder =
        resolveColorOption(
            inFlightButton.buttonBorderColor ?: (activeLayout?.buttonBorderColor ?: ColorOption.Neutral),
            globalAccentColor,
            MP_AMBIENT_NEUTRAL_BORDER,
        )
    val currentResolvedBg =
        resolveColorOption(
            inFlightButton.buttonBgColor ?: (activeLayout?.buttonBgColor ?: ColorOption.Neutral),
            globalAccentColor,
            MP_AMBIENT_NEUTRAL_BG,
        )

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

    val isDefaultSelected = selectedOption == null
    val isNeutralSelected = selectedOption is ColorOption.Neutral
    val isAccentSelected = selectedOption is ColorOption.Accent
    val isCustomSelected = selectedOption is ColorOption.Custom

    val renderPreviewFace: @Composable (tCol: Color, bCol: Color, bgCol: Color) -> Unit = { tCol, bCol, bgCol ->
        PadButtonFace(
            width = PBD_COLOR_PREVIEW_SIZE,
            height = PBD_COLOR_PREVIEW_SIZE,
            shape = if (button.buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(PBD_CORNER_RADIUS_DP),
            isIconOnly = button.buttonShape == ButtonShape.ICON_ONLY,
            isDeviceDisabled = false,
            borderColor = bCol,
            bgColor = bgCol,
            bgAlpha = PBD_PREVIEW_BG_ALPHA,
            gradientScale = PBD_PREVIEW_GRADIENT_SCALE,
        ) {
            val icon = button.iconName
            if (icon != null) {
                MaterialSymbol(
                    name = icon,
                    size = PBD_ICON_SIZE_DP,
                    tint = tCol,
                    filled = button.iconFilled,
                )
            } else {
                Text(
                    text = button.label.ifBlank { "A" },
                    color = tCol,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = { onSave(inFlightButton) },
            onDiscard = onDiscard,
        )

    // ── Save Option at Top (with Saved vs In-Flight Previews) ──
    GamepadSaveExitActionRow(
        title = stringResource(R.string.gamepad_action_save),
        description = stringResource(R.string.macropad_btn_color_save_desc),
        cardBgColor = saveCardBgColor,
        saveActionLeadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PBD_SAVE_PREVIEW_SPACING),
            ) {
                renderPreviewFace(savedResolvedText, savedResolvedBorder, savedResolvedBg)
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary.copy(alpha = PBD_ARROW_ALPHA),
                    modifier = Modifier.size(PBD_ARROW_SIZE),
                )
                renderPreviewFace(currentResolvedText, currentResolvedBorder, currentResolvedBg)
            }
        },
        saveActionText = stringResource(R.string.gamepad_action_save),
        saveIcon = Icons.Rounded.Save,
        enabled = true,
        showExitPrompt = promptState.showExitPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
        modifier = Modifier.firstDeckItem(),
    )

    // Option 1: Layout Default
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_layout_default),
        description = stringResource(R.string.macropad_btn_color_layout_default_desc),
        icon = Icons.Rounded.Sync,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = layoutResolvedColor,
                isSelected = isDefaultSelected,
            )
        },
        actionText =
            if (isDefaultSelected) {
                stringResource(R.string.gamepad_color_selected)
            } else {
                stringResource(R.string.gamepad_action_select)
            },
        onClick = { selectedOption = null },
    )

    // Option 2: Theme Neutral
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_neutral),
        description = stringResource(R.string.macropad_editor_color_palette_desc),
        icon = Icons.Rounded.FormatColorText,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = defaultNeutralColor,
                isSelected = isNeutralSelected,
            )
        },
        actionText =
            if (isNeutralSelected) {
                stringResource(R.string.gamepad_color_selected)
            } else {
                stringResource(R.string.gamepad_action_select)
            },
        onClick = { selectedOption = ColorOption.Neutral },
    )

    // Option 3: App Accent
    GamepadActionCard(
        title = stringResource(R.string.layout_settings_color_accent),
        description = stringResource(R.string.settings_accent_color_desc),
        icon = Icons.Rounded.Palette,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = globalAccentColor,
                isSelected = isAccentSelected,
            )
        },
        actionText =
            if (isAccentSelected) {
                stringResource(R.string.gamepad_color_selected)
            } else {
                stringResource(R.string.gamepad_action_select)
            },
        onClick = { selectedOption = ColorOption.Accent },
    )

    // Option 4: Custom Color (Color Wheel)
    GamepadActionCard(
        title = stringResource(R.string.gamepad_action_custom_color),
        description =
            if (isCustomSelected) {
                String.format("#%06X", 0xFFFFFF and currentColor.toArgb())
            } else {
                stringResource(R.string.macropad_editor_color_wheel_desc)
            },
        icon = Icons.Rounded.Colorize,
        actionLeadingContent = {
            GamepadColorSwatch(
                color = if (isCustomSelected) currentColor else Color.Transparent,
                isSelected = isCustomSelected,
            )
        },
        actionText =
            if (isCustomSelected) {
                stringResource(R.string.gamepad_color_selected)
            } else {
                stringResource(R.string.gamepad_action_choose)
            },
        onClick = {
            onOpenColorWheel(
                selectColorWheelTitle,
                colorWheelBreadcrumbs,
                if (isCustomSelected) currentColor else layoutResolvedColor,
                inFlightButton,
            )
        },
    )
}
