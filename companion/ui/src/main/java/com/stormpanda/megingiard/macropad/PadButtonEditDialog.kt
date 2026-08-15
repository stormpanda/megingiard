package com.stormpanda.megingiard.macropad

import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.ColorWheelPicker
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.appSwitchColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import java.util.Locale
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
private val PBD_RECENT_COLORS_GRID_HEIGHT = 128.dp

/**
 * Maps a [PadAction] type to its localised label string resource.
 * Returns `null` for action types that manage their own label
 * ([PadAction.KeyboardKey], [PadAction.GamepadButton], [PadAction.MouseButton])
 * or have fixed rendering ([PadAction.ScrollWheel], [PadAction.TrackpointMove]).
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

// ─────────────────────────────────────────────────────────────────────────────
// Button Edit Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ButtonEditDialog(
    button: PadButton?, // null → create new
    accentColor: Color,
    enableKeyboard: Boolean = true,
    enableGamepad: Boolean = true,
    enableMouse: Boolean = true,
    initialAction: PadAction? = null, // pre-set action for new buttons; ignored if button != null
    onEditMacro: ((Macro) -> Unit)? = null,
    onConfirm: (PadButton) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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
    var label by remember { mutableStateOf(initLabel) }
    var iconName by remember { mutableStateOf(initIconName) }
    var showIconPicker by remember { mutableStateOf(false) }
    var buttonShape by remember { mutableStateOf(button?.buttonShape ?: ButtonShape.CIRCLE) }
    var buttonSize by remember { mutableStateOf(button?.buttonSize ?: ButtonSize.SIZE_1X1) }
    var action by remember { mutableStateOf(initAction) }
    var iconFilled by remember { mutableStateOf(button?.iconFilled ?: true) }
    var hapticStrength by remember { mutableStateOf(button?.hapticStrength ?: HapticStrength.OFF) }
    // Sliders always reflect the active preset or the stored custom values on open
    var hapticCustomDurationMs by remember {
        mutableIntStateOf(
            when (button?.hapticStrength) {
                HapticStrength.LIGHT, HapticStrength.MEDIUM, HapticStrength.STRONG -> HF_PRESET_DURATION_MS
                else -> button?.hapticCustomDurationMs ?: HF_PRESET_DURATION_MS
            },
        )
    }
    var hapticCustomAmplitude by remember {
        mutableIntStateOf(
            when (button?.hapticStrength) {
                HapticStrength.LIGHT -> HF_LIGHT_AMPLITUDE_USER
                HapticStrength.MEDIUM -> HF_MEDIUM_AMPLITUDE_USER
                HapticStrength.STRONG -> HF_STRONG_AMPLITUDE_USER
                else -> button?.hapticCustomAmplitude ?: HF_LIGHT_AMPLITUDE_USER
            },
        )
    }
    var buttonTextColor by remember { mutableStateOf(button?.buttonTextColor) }
    var buttonBorderColor by remember { mutableStateOf(button?.buttonBorderColor) }
    var buttonBgColor by remember { mutableStateOf(button?.buttonBgColor) }
    var invisible by remember { mutableStateOf(button?.invisible ?: (activeLayout?.invisibleButtons ?: false)) }

    var activeColorPickerTarget by remember { mutableStateOf<ButtonColorPickerTarget?>(null) }
    var activePaletteDialogTarget by remember { mutableStateOf<ButtonColorPickerTarget?>(null) }

    val recentColors by MacroPadSettings.recentColors.collectAsState()
    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

    val colors = LocalAppColors.current
    val profile by MacroPadState.activeProfile.collectAsState()
    val macros = profile?.macros ?: emptyList()

    var actionBeforeEdit by remember { mutableStateOf<PadAction?>(null) }

    LaunchedEffect(button?.id) {
        AppLog.d(TAG, "Opening ButtonEditDialog for button=${button?.id ?: "new"} action=$initAction")
    }

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
        // For Macro: fill label from the macro name if the label field is still blank.
        if (newAction is PadAction.Macro && label.isBlank()) {
            val macroName = macros.firstOrNull { it.id == newAction.macroId }?.name
            if (macroName != null) label = macroName
        }
        // For new buttons (or when the label was never customised), pre-fill with the
        // action-type defaults so the user has a sensible starting point.
        if (button == null || label.isBlank()) {
            val defaultLbl = newAction.defaultLabelRes()?.let { context.getString(it) } ?: ""
            val defaultIcon = newAction.editorDefaultIconName()
            if (defaultLbl.isNotEmpty()) label = defaultLbl
            if (defaultIcon != null) iconName = defaultIcon
        }
    }

    val isConfirmEnabled =
        when {
            action is PadAction.ScrollWheel || action is PadAction.TrackpointMove -> {
                true
            }

            action is PadAction.AppLauncher -> {
                (action as PadAction.AppLauncher).packageName.isNotBlank()
            }

            action is PadAction.Macro -> {
                label.isNotBlank() &&
                    macros.any { it.id == (action as PadAction.Macro).macroId }
            }

            else -> {
                label.isNotBlank()
            }
        }

    // ── Full-screen layout (no AlertDialog — stays in same window for IME) ─────────────
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface)
                .blockPointerEvents(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val topBarTitle =
                when {
                    button == null -> stringResource(R.string.macropad_editor_add_button)
                    button.action is PadAction.TrackpointMove -> stringResource(R.string.macropad_action_trackpoint)
                    else -> button.label
                }
            FullScreenTopBar(title = topBarTitle, onDismiss = onDismiss) {
                TextButton(
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
                            onConfirm(result)
                        }
                    },
                    enabled = isConfirmEnabled,
                ) {
                    Text(
                        stringResource(R.string.macropad_editor_done),
                        color = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Scrollable form body ──────────────────────────────────────────────────────────
            // ── Section header ───────────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.macropad_editor_section_button_settings).uppercase(Locale.ROOT),
                color = colors.sectionHeaderColor,
                style = MaterialTheme.typography.labelSmall,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val iconsFilled = iconFilled

                // Label input and shape — hidden for ScrollWheel, TrackpointMove, and AppLauncher
                if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove && action !is PadAction.AppLauncher) {
                    // ── Label + Icon selector row ──────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text(stringResource(R.string.macropad_editor_button_label), color = colors.onSurfaceSecondary) },
                            modifier = Modifier.weight(1f),
                        )
                        // Icon selector button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (iconName != null) {
                                            accentColor.copy(alpha = 0.2f)
                                        } else {
                                            colors.surface
                                        },
                                    ).border(
                                        width = 1.dp,
                                        color = colors.subduedBorder,
                                        shape = RoundedCornerShape(8.dp),
                                    ).clickable { showIconPicker = true },
                        ) {
                            MaterialSymbol(
                                name = iconName ?: "add_reaction",
                                size = 28.dp,
                                tint = if (iconName != null) accentColor else colors.onSurfaceSecondary,
                                filled = iconsFilled,
                            )
                        }
                    }
                }

                SectionLabel(stringResource(R.string.macropad_editor_action), accentColor)
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
                        description = "Geometry of the interactive button cap",
                        selectedText = shapeLabels[shapeIdx],
                        icon = Icons.Rounded.Palette,
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
                        description = "Grid width and height footprint",
                        selectedText = sizeEntries[sizeIdx].displayLabel(),
                        icon = Icons.Rounded.Palette,
                        onPrevious = {
                            val nextIdx = (sizeIdx - 1 + sizeEntries.size) % sizeEntries.size
                            buttonSize = sizeEntries[nextIdx]
                        },
                        onNext = {
                            val nextIdx = (sizeIdx + 1) % sizeEntries.size
                            buttonSize = sizeEntries[nextIdx]
                        },
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
                        description = "Vibration feedback triggered on tap",
                        selectedText = hapticLabels[hapticIdx],
                        icon = Icons.Rounded.Palette,
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
                }

                if (hapticStrength == HapticStrength.CUSTOM) {
                    GamepadStepperCard(
                        title = stringResource(R.string.macropad_haptic_custom_duration),
                        description = "Duration in milliseconds",
                        valueText = "$hapticCustomDurationMs ms",
                        icon = Icons.Rounded.Palette,
                        onDecrement = {
                            hapticCustomDurationMs = (hapticCustomDurationMs - 10).coerceIn(10, 200)
                        },
                        onIncrement = {
                            hapticCustomDurationMs = (hapticCustomDurationMs + 10).coerceIn(10, 200)
                        },
                    )

                    GamepadStepperCard(
                        title = stringResource(R.string.macropad_haptic_custom_amplitude),
                        description = "Vibration motor power percentage",
                        valueText = "$hapticCustomAmplitude%",
                        icon = Icons.Rounded.Palette,
                        onDecrement = {
                            hapticCustomAmplitude = (hapticCustomAmplitude - 5).coerceIn(5, 100)
                        },
                        onIncrement = {
                            hapticCustomAmplitude = (hapticCustomAmplitude + 5).coerceIn(5, 100)
                        },
                    )
                }

                AppDivider()
                SectionLabel(stringResource(R.string.button_settings_colors_section_title), accentColor)

                ColorPickerRow(
                    label = stringResource(R.string.layout_settings_color_text),
                    option = buttonTextColor,
                    defaultNeutralColor = MP_AMBIENT_NEUTRAL_TEXT,
                    globalAccentColor = globalAccentColor,
                    layoutDefaultOption = activeLayout?.buttonTextColor ?: ColorOption.Neutral,
                    onWheelClick = { activeColorPickerTarget = ButtonColorPickerTarget.TEXT },
                    onPaletteClick = { activePaletteDialogTarget = ButtonColorPickerTarget.TEXT },
                )

                ColorPickerRow(
                    label = stringResource(R.string.layout_settings_color_border),
                    option = buttonBorderColor,
                    defaultNeutralColor = MP_AMBIENT_NEUTRAL_BORDER,
                    globalAccentColor = globalAccentColor,
                    layoutDefaultOption = activeLayout?.buttonBorderColor ?: ColorOption.Neutral,
                    onWheelClick = { activeColorPickerTarget = ButtonColorPickerTarget.BORDER },
                    onPaletteClick = { activePaletteDialogTarget = ButtonColorPickerTarget.BORDER },
                )

                ColorPickerRow(
                    label = stringResource(R.string.layout_settings_color_bg),
                    option = buttonBgColor,
                    defaultNeutralColor = MP_AMBIENT_NEUTRAL_BG,
                    globalAccentColor = globalAccentColor,
                    layoutDefaultOption = activeLayout?.buttonBgColor ?: ColorOption.Neutral,
                    onWheelClick = { activeColorPickerTarget = ButtonColorPickerTarget.BG },
                    onPaletteClick = { activePaletteDialogTarget = ButtonColorPickerTarget.BG },
                )

                GamepadToggleCard(
                    title = stringResource(R.string.layout_settings_invisible_buttons),
                    description = stringResource(R.string.layout_settings_invisible_buttons_desc),
                    checked = invisible,
                    icon = Icons.Rounded.Palette,
                    onCheckedChange = { invisible = it },
                )
            }
        }

        // ── Icon picker overlay ──────────────────────────────────────────────
        if (showIconPicker) {
            IconPickerDialog(
                selectedIcon = iconName,
                accentColor = accentColor,
                filled = iconFilled,
                onFilledChange = { iconFilled = it },
                onSelect = { name ->
                    iconName = name
                    showIconPicker = false
                },
                onDismiss = { showIconPicker = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Color Wheel overlays
        val activeWheelTarget = activeColorPickerTarget
        if (activeWheelTarget != null) {
            val currentText =
                resolveColorOption(
                    buttonTextColor ?: activeLayout?.buttonTextColor ?: ColorOption.Neutral,
                    globalAccentColor,
                    MP_AMBIENT_NEUTRAL_TEXT,
                )
            val currentBorder =
                resolveColorOption(
                    buttonBorderColor ?: activeLayout?.buttonBorderColor ?: ColorOption.Neutral,
                    globalAccentColor,
                    MP_AMBIENT_NEUTRAL_BORDER,
                )
            val currentBg =
                resolveColorOption(
                    buttonBgColor ?: activeLayout?.buttonBgColor ?: ColorOption.Neutral,
                    globalAccentColor,
                    MP_AMBIENT_NEUTRAL_BG,
                )
            val defaultNeutral =
                when (activeWheelTarget) {
                    ButtonColorPickerTarget.TEXT -> MP_AMBIENT_NEUTRAL_TEXT
                    ButtonColorPickerTarget.BORDER -> MP_AMBIENT_NEUTRAL_BORDER
                    ButtonColorPickerTarget.BG -> MP_AMBIENT_NEUTRAL_BG
                }
            val resolvedLayoutOption =
                when (activeWheelTarget) {
                    ButtonColorPickerTarget.TEXT -> activeLayout?.buttonTextColor ?: ColorOption.Neutral
                    ButtonColorPickerTarget.BORDER -> activeLayout?.buttonBorderColor ?: ColorOption.Neutral
                    ButtonColorPickerTarget.BG -> activeLayout?.buttonBgColor ?: ColorOption.Neutral
                }
            val initialColor =
                when (
                    val opt =
                        when (activeWheelTarget) {
                            ButtonColorPickerTarget.TEXT -> buttonTextColor
                            ButtonColorPickerTarget.BORDER -> buttonBorderColor
                            ButtonColorPickerTarget.BG -> buttonBgColor
                        } ?: resolvedLayoutOption
                ) {
                    ColorOption.Neutral -> defaultNeutral
                    ColorOption.Accent -> globalAccentColor
                    is ColorOption.Custom -> Color(opt.argb)
                }
            ColorWheelPicker(
                initialColor = initialColor,
                title =
                    when (activeWheelTarget) {
                        ButtonColorPickerTarget.TEXT -> stringResource(R.string.layout_settings_select_text_color)
                        ButtonColorPickerTarget.BORDER -> stringResource(R.string.layout_settings_select_border_color)
                        ButtonColorPickerTarget.BG -> stringResource(R.string.layout_settings_select_bg_color)
                    },
                showAlphaSlider = true,
                onColorSelected = { selectedColor ->
                    val customOpt = ColorOption.Custom(selectedColor.toArgb())
                    when (activeWheelTarget) {
                        ButtonColorPickerTarget.TEXT -> buttonTextColor = customOpt
                        ButtonColorPickerTarget.BORDER -> buttonBorderColor = customOpt
                        ButtonColorPickerTarget.BG -> buttonBgColor = customOpt
                    }
                    MacroPadSettings.addRecentColor(selectedColor.toArgb())
                    activeColorPickerTarget = null
                },
                onDismiss = { activeColorPickerTarget = null },
                preview = { liveColor ->
                    SwordsButtonPreview(
                        textColor = if (activeWheelTarget == ButtonColorPickerTarget.TEXT) liveColor else currentText,
                        borderColor = if (activeWheelTarget == ButtonColorPickerTarget.BORDER) liveColor else currentBorder,
                        bgColor = if (activeWheelTarget == ButtonColorPickerTarget.BG) liveColor else currentBg,
                        size = PBD_PREVIEW_BUTTON_SIZE,
                    )
                },
            )
        }

        // Palette Overlay Dialogs
        val activePaletteTarget = activePaletteDialogTarget
        if (activePaletteTarget != null) {
            val defaultNeutralColor =
                when (activePaletteTarget) {
                    ButtonColorPickerTarget.TEXT -> MP_AMBIENT_NEUTRAL_TEXT
                    ButtonColorPickerTarget.BORDER -> MP_AMBIENT_NEUTRAL_BORDER
                    ButtonColorPickerTarget.BG -> MP_AMBIENT_NEUTRAL_BG
                }
            val resolvedLayoutOption =
                when (activePaletteTarget) {
                    ButtonColorPickerTarget.TEXT -> activeLayout?.buttonTextColor ?: ColorOption.Neutral
                    ButtonColorPickerTarget.BORDER -> activeLayout?.buttonBorderColor ?: ColorOption.Neutral
                    ButtonColorPickerTarget.BG -> activeLayout?.buttonBgColor ?: ColorOption.Neutral
                }
            val layoutDefaultColor = resolveColorOption(resolvedLayoutOption, globalAccentColor, defaultNeutralColor)
            val currentText =
                resolveColorOption(
                    buttonTextColor ?: activeLayout?.buttonTextColor ?: ColorOption.Neutral,
                    globalAccentColor,
                    MP_AMBIENT_NEUTRAL_TEXT,
                )
            val currentBorder =
                resolveColorOption(
                    buttonBorderColor ?: activeLayout?.buttonBorderColor ?: ColorOption.Neutral,
                    globalAccentColor,
                    MP_AMBIENT_NEUTRAL_BORDER,
                )
            val currentBg =
                resolveColorOption(
                    buttonBgColor ?: activeLayout?.buttonBgColor ?: ColorOption.Neutral,
                    globalAccentColor,
                    MP_AMBIENT_NEUTRAL_BG,
                )

            QuickColorSelectionDialog(
                title =
                    when (activePaletteTarget) {
                        ButtonColorPickerTarget.TEXT -> stringResource(R.string.layout_settings_select_text_color)
                        ButtonColorPickerTarget.BORDER -> stringResource(R.string.layout_settings_select_border_color)
                        ButtonColorPickerTarget.BG -> stringResource(R.string.layout_settings_select_bg_color)
                    },
                recentColors = recentColors,
                onSelected = { opt ->
                    when (activePaletteTarget) {
                        ButtonColorPickerTarget.TEXT -> buttonTextColor = opt
                        ButtonColorPickerTarget.BORDER -> buttonBorderColor = opt
                        ButtonColorPickerTarget.BG -> buttonBgColor = opt
                    }
                    if (opt is ColorOption.Custom) {
                        MacroPadSettings.addRecentColor(opt.argb)
                    }
                    activePaletteDialogTarget = null
                },
                onDismiss = { activePaletteDialogTarget = null },
                preview = { option ->
                    val resolved =
                        when (option) {
                            null -> layoutDefaultColor
                            ColorOption.Neutral -> defaultNeutralColor
                            ColorOption.Accent -> globalAccentColor
                            is ColorOption.Custom -> Color(option.argb)
                        }
                    SwordsButtonPreview(
                        textColor = if (activePaletteTarget == ButtonColorPickerTarget.TEXT) resolved else currentText,
                        borderColor = if (activePaletteTarget == ButtonColorPickerTarget.BORDER) resolved else currentBorder,
                        bgColor = if (activePaletteTarget == ButtonColorPickerTarget.BG) resolved else currentBg,
                        size = PBD_PREVIEW_BUTTON_SIZE,
                    )
                },
            )
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Section label helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SectionLabel(
    text: String,
    accentColor: Color,
) {
    Text(text, color = accentColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
}

private enum class ButtonColorPickerTarget { TEXT, BORDER, BG }

@Composable
private fun ColorPickerRow(
    label: String,
    option: ColorOption?,
    defaultNeutralColor: Color,
    globalAccentColor: Color,
    layoutDefaultOption: ColorOption,
    onWheelClick: () -> Unit,
    onPaletteClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val resolvedOption = option ?: layoutDefaultOption
            val previewColor =
                when (resolvedOption) {
                    ColorOption.Neutral -> defaultNeutralColor
                    ColorOption.Accent -> globalAccentColor
                    is ColorOption.Custom -> Color(resolvedOption.argb)
                }
            // Circular color wheel button (click to open color wheel)
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(1.dp, colors.subduedBorder, CircleShape)
                        .clickable(onClick = onWheelClick),
            )

            Spacer(Modifier.width(12.dp))

            // Palette button (click to open quick select dialog)
            IconButton(
                onClick = onPaletteClick,
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(colors.surfaceVariant, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickColorSelectionDialog(
    title: String,
    recentColors: List<Int>,
    onSelected: (ColorOption?) -> Unit,
    onDismiss: () -> Unit,
    preview: @Composable (ColorOption?) -> Unit,
) {
    val colors = LocalAppColors.current

    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = 0.85f,
        cornerRadius = 12.dp,
        contentPadding = 16.dp,
    ) {
        Text(
            text = title,
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(16.dp))

        // System Styles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Layout Default option
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelected(null) }
                        .padding(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(PBD_PREVIEW_BUTTON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    preview(null)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.layout_settings_color_layout_default),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }

            // Neutral option
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelected(ColorOption.Neutral) }
                        .padding(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(PBD_PREVIEW_BUTTON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    preview(ColorOption.Neutral)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.layout_settings_color_neutral),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }

            // Accent option
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelected(ColorOption.Accent) }
                        .padding(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(PBD_PREVIEW_BUTTON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    preview(ColorOption.Accent)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.layout_settings_color_accent),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.layout_settings_recent_colors),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))

        if (recentColors.isEmpty()) {
            Text(
                text = stringResource(R.string.layout_settings_no_recent_colors),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(PBD_RECENT_COLORS_GRID_HEIGHT),
            ) {
                items(recentColors) { argb ->
                    Box(
                        modifier =
                            Modifier
                                .size(PBD_PREVIEW_BUTTON_SIZE)
                                .clickable { onSelected(ColorOption.Custom(argb)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        preview(ColorOption.Custom(argb))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
        }
    }
}
