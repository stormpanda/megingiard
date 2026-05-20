package com.stormpanda.megingiard.macropad

import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.stormpanda.megingiard.ui.blockPointerEvents
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.LocalAppColors
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

/**
 * Maps a [PadAction] type to its localised label string resource.
 * Returns `null` for action types that manage their own label
 * ([PadAction.KeyboardKey], [PadAction.GamepadButton], [PadAction.MouseButton])
 * or have fixed rendering ([PadAction.ScrollWheel], [PadAction.TrackpointMove]).
 */
private fun PadAction.defaultLabelRes(): Int? = when (this) {
    is PadAction.LayoutNext            -> R.string.macropad_action_layout_next
    is PadAction.LayoutPrevious        -> R.string.macropad_action_layout_previous
    is PadAction.ProfileSwitcher       -> R.string.macropad_action_profile_switcher
    is PadAction.MirrorPlayStop        -> R.string.macropad_action_mirror_play_stop
    is PadAction.MirrorFreeze          -> R.string.macropad_action_mirror_freeze
    is PadAction.MirrorViewportEdit    -> R.string.macropad_action_mirror_viewport_edit
    is PadAction.MirrorTouchProjection -> R.string.macropad_action_mirror_touch_projection
    is PadAction.FullScreenMouse       -> R.string.macropad_action_fullscreen_mouse
    is PadAction.FullScreenKeyboard    -> R.string.macropad_action_fullscreen_keyboard
    is PadAction.Macro                 -> R.string.macropad_action_macro
    is PadAction.BackgroundPeek           -> R.string.macropad_action_ambient_peek
    else                               -> null
}

/** Default Material Symbols icon name for actions that behave like regular buttons in the editor. */
private fun PadAction.editorDefaultIconName(): String? = when (this) {
    is PadAction.LayoutNext            -> PBD_ICON_LAYOUT_NEXT
    is PadAction.LayoutPrevious        -> PBD_ICON_LAYOUT_PREVIOUS
    is PadAction.ProfileSwitcher       -> PBD_ICON_PROFILE_SWITCHER
    is PadAction.MirrorPlayStop        -> PBD_ICON_MIRROR_PLAY_STOP
    is PadAction.MirrorFreeze          -> PBD_ICON_MIRROR_FREEZE
    is PadAction.MirrorViewportEdit    -> PBD_ICON_MIRROR_VIEWPORT_EDIT
    is PadAction.MirrorTouchProjection -> PBD_ICON_MIRROR_TOUCH_PROJECTION
    is PadAction.FullScreenMouse       -> PBD_ICON_FULLSCREEN_MOUSE
    is PadAction.FullScreenKeyboard    -> PBD_ICON_FULLSCREEN_KEYBOARD
    is PadAction.Macro                 -> PBD_ICON_MACRO
    is PadAction.BackgroundPeek           -> PBD_ICON_BACKGROUND_PEEK
    else                               -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// Button Edit Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ButtonEditDialog(
    button:         PadButton?,    // null → create new
    accentColor:    Color,
    enableKeyboard: Boolean = true,
    enableGamepad:  Boolean = true,
    enableMouse:    Boolean = true,
    initialAction:  PadAction? = null,  // pre-set action for new buttons; ignored if button != null
    onEditMacro:    ((Macro) -> Unit)? = null,
    onConfirm:      (PadButton) -> Unit,
    onDismiss:      () -> Unit,
    modifier:       Modifier = Modifier,
) {
    val context = LocalContext.current
    val initAction = button?.action
        ?: initialAction
        ?: PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    val initLabel = button?.label ?: when (val ia = initialAction) {
        is PadAction.Macro -> MacroPadState.activeProfile.value?.macros?.firstOrNull { it.id == ia.macroId }?.name ?: ""
        null               -> ""
        else               -> ia.defaultLabelRes()?.let { context.getString(it) } ?: ""
    }
    val initIconName = button?.iconName ?: initialAction?.editorDefaultIconName()
    var label            by remember { mutableStateOf(initLabel) }
    var iconName          by remember { mutableStateOf(initIconName) }
    var showIconPicker    by remember { mutableStateOf(false) }
    var buttonShape       by remember { mutableStateOf(button?.buttonShape ?: ButtonShape.CIRCLE) }
    var buttonSize        by remember { mutableStateOf(button?.buttonSize ?: ButtonSize.SIZE_1X1) }
    var action            by remember { mutableStateOf(initAction) }
    var iconFilled        by remember { mutableStateOf(button?.iconFilled ?: true) }
    var hapticStrength         by remember { mutableStateOf(button?.hapticStrength ?: HapticStrength.OFF) }
    // Sliders always reflect the active preset or the stored custom values on open
    var hapticCustomDurationMs  by remember {
        mutableIntStateOf(
            when (button?.hapticStrength) {
                HapticStrength.LIGHT, HapticStrength.MEDIUM, HapticStrength.STRONG -> HF_PRESET_DURATION_MS
                else -> button?.hapticCustomDurationMs ?: HF_PRESET_DURATION_MS
            }
        )
    }
    var hapticCustomAmplitude   by remember {
        mutableIntStateOf(
            when (button?.hapticStrength) {
                HapticStrength.LIGHT  -> HF_LIGHT_AMPLITUDE_USER
                HapticStrength.MEDIUM -> HF_MEDIUM_AMPLITUDE_USER
                HapticStrength.STRONG -> HF_STRONG_AMPLITUDE_USER
                else -> button?.hapticCustomAmplitude ?: HF_LIGHT_AMPLITUDE_USER
            }
        )
    }
    val colors            = LocalAppColors.current

    fun onActionChanged(newAction: PadAction) {
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
        // For Macro: fill label from the macro name if the label field is still blank.
        if (newAction is PadAction.Macro && label.isBlank()) {
            val macroName = MacroPadState.activeProfile.value?.macros?.firstOrNull { it.id == newAction.macroId }?.name
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

    val isConfirmEnabled = when {
        action is PadAction.ScrollWheel || action is PadAction.TrackpointMove -> true
        action is PadAction.Macro -> label.isNotBlank() &&
            MacroPadState.activeProfile.value?.macros?.any { it.id == (action as PadAction.Macro).macroId } == true
        else -> label.isNotBlank()
    }

    // ── Full-screen layout (no AlertDialog — stays in same window for IME) ─────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .blockPointerEvents(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val topBarTitle = when {
                button == null -> stringResource(R.string.macropad_editor_add_button)
                button.action is PadAction.TrackpointMove -> stringResource(R.string.macropad_action_trackpoint)
                else -> button.label
            }
            FullScreenTopBar(title = topBarTitle, onDismiss = onDismiss) {
                TextButton(
                    onClick = {
                        if (isConfirmEnabled) {
                            val result = button?.copy(
                                label                 = label,
                                iconName              = iconName,
                                iconFilled            = iconFilled,
                                buttonShape           = buttonShape,
                                buttonSize            = buttonSize,
                                action                = action,
                                hapticStrength        = hapticStrength,
                                hapticCustomDurationMs = hapticCustomDurationMs,
                                hapticCustomAmplitude  = hapticCustomAmplitude,
                            ) ?: PadButton(
                                id                    = UUID.randomUUID().toString(),
                                label                 = label,
                                iconName              = iconName,
                                iconFilled            = iconFilled,
                                posX                  = 0.5f,
                                posY                  = 0.5f,
                                buttonShape           = buttonShape,
                                buttonSize            = buttonSize,
                                action                = action,
                                hapticStrength        = hapticStrength,
                                hapticCustomDurationMs = hapticCustomDurationMs,
                                hapticCustomAmplitude  = hapticCustomAmplitude,
                            )
                            onConfirm(result)
                        }
                    },
                    enabled = isConfirmEnabled,
                ) {
                    Text(
                        stringResource(R.string.macropad_editor_done),
                        color      = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Scrollable form body ──────────────────────────────────────────────────────────
            // ── Section header ───────────────────────────────────────────────────────────────
            Text(
                text     = stringResource(R.string.macropad_editor_section_button_settings).uppercase(Locale.ROOT),
                color    = colors.sectionHeaderColor,
                style    = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val iconsFilled = iconFilled

                // Label input and shape — hidden for ScrollWheel and TrackpointMove
                if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove) {
                    // ── Label + Icon selector row ──────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value         = label,
                            onValueChange = { label = it },
                            label         = { Text(stringResource(R.string.macropad_editor_button_label), color = colors.onSurfaceSecondary) },
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = accentColor,
                                unfocusedBorderColor = colors.accentBorder,
                                focusedTextColor     = colors.onSurface,
                                unfocusedTextColor   = colors.onSurface,
                                cursorColor          = accentColor,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        // Icon selector button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (iconName != null) accentColor.copy(alpha = 0.2f)
                                    else colors.surface
                                )
                                .border(
                                    width = if (iconName != null) 2.dp else 1.dp,
                                    color = if (iconName != null) accentColor else colors.accentBorder,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { showIconPicker = true },
                        ) {
                            MaterialSymbol(
                                name = iconName ?: "add_reaction",
                                size = 28.dp,
                                tint = if (iconName != null) accentColor else colors.onSurfaceSecondary,
                                filled = iconsFilled,
                            )
                        }
                    }

                    // ── Shape + Size side by side ──────────────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel(stringResource(R.string.macropad_editor_button_shape), accentColor)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ButtonShape.entries.forEach { shape ->
                                    val selected = shape == buttonShape
                                    val shapeLabel = if (shape == ButtonShape.CIRCLE)
                                        stringResource(R.string.macropad_editor_shape_circle)
                                    else
                                        stringResource(R.string.macropad_editor_shape_square)
                                    AppSelectableChip(
                                        text     = shapeLabel,
                                        selected = selected,
                                        onClick  = { buttonShape = shape },
                                    )
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel(stringResource(R.string.macropad_editor_button_size), accentColor)
                            AppDropdown(
                                selected          = buttonSize,
                                options           = ButtonSize.entries,
                                optionText        = { size -> size.displayLabel() },
                                onSelected        = { size -> buttonSize = size },
                                horizontalPadding = 16.dp,
                                verticalPadding   = 10.dp,
                            )
                        }
                    }
                } else if (action is PadAction.TrackpointMove) {
                    // ── Trackpoint size picker ──────────────────────────────────────────────
                    SectionLabel(stringResource(R.string.macropad_editor_trackpoint_size), accentColor)
                    val tpAction = action as PadAction.TrackpointMove
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrackpointSize.entries.forEach { sz ->
                            val selected = sz == tpAction.size
                            val szLabel = when (sz) {
                                TrackpointSize.SMALL  -> stringResource(R.string.macropad_trackpoint_size_small)
                                TrackpointSize.MEDIUM -> stringResource(R.string.macropad_trackpoint_size_medium)
                                TrackpointSize.LARGE  -> stringResource(R.string.macropad_trackpoint_size_large)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                AppSelectableChip(
                                    text     = szLabel,
                                    selected = selected,
                                    onClick  = { action = PadAction.TrackpointMove(sz) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                } else if (action is PadAction.ScrollWheel) {
                    // ── ScrollWheel: size locked to 1×2 ────────────────────────────────────
                    SectionLabel(stringResource(R.string.macropad_editor_button_size), accentColor)
                    Text(
                        text     = ButtonSize.SIZE_1X2.displayLabel(),
                        color    = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Action picker
                SectionLabel(stringResource(R.string.macropad_editor_section_haptic), accentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HapticStrength.entries.forEach { strength ->
                        val selected = strength == hapticStrength
                        val strengthLabel = when (strength) {
                            HapticStrength.OFF    -> stringResource(R.string.macropad_haptic_off)
                            HapticStrength.LIGHT  -> stringResource(R.string.macropad_haptic_light)
                            HapticStrength.MEDIUM -> stringResource(R.string.macropad_haptic_medium)
                            HapticStrength.STRONG -> stringResource(R.string.macropad_haptic_strong)
                            HapticStrength.CUSTOM -> stringResource(R.string.macropad_haptic_custom)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f),
                        ) {
                            AppSelectableChip(
                                text     = strengthLabel,
                                selected = selected,
                                onClick  = {
                                    // Snap sliders to preset values; CUSTOM/OFF leave sliders unchanged
                                    when (strength) {
                                        HapticStrength.LIGHT  -> { hapticCustomDurationMs = HF_PRESET_DURATION_MS; hapticCustomAmplitude = HF_LIGHT_AMPLITUDE_USER }
                                        HapticStrength.MEDIUM -> { hapticCustomDurationMs = HF_PRESET_DURATION_MS; hapticCustomAmplitude = HF_MEDIUM_AMPLITUDE_USER }
                                        HapticStrength.STRONG -> { hapticCustomDurationMs = HF_PRESET_DURATION_MS; hapticCustomAmplitude = HF_STRONG_AMPLITUDE_USER }
                                        else                  -> { /* OFF / CUSTOM → keep current slider values */ }
                                    }
                                    hapticStrength = strength
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                // Sliders always visible; dragging either slider auto-selects CUSTOM
                if (hapticStrength != HapticStrength.OFF) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.width(96.dp)) {
                                    Text(
                                        text  = stringResource(R.string.macropad_haptic_custom_duration),
                                        color = colors.onSurfaceSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text  = "$hapticCustomDurationMs ms",
                                        color = accentColor,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Slider(
                                    modifier = Modifier.weight(1f),
                                    value    = hapticCustomDurationMs.toFloat(),
                                    onValueChange = {
                                        hapticCustomDurationMs = it.toInt()
                                        hapticStrength = HapticStrength.CUSTOM
                                    },
                                    valueRange = 1f..200f,
                                    steps = 198, // 1..200 → 200 values = 198 interior steps
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.width(96.dp)) {
                                    Text(
                                        text  = stringResource(R.string.macropad_haptic_custom_amplitude),
                                        color = colors.onSurfaceSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text  = "$hapticCustomAmplitude",
                                        color = accentColor,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Slider(
                                    modifier = Modifier.weight(1f),
                                    value    = hapticCustomAmplitude.toFloat(),
                                    onValueChange = {
                                        hapticCustomAmplitude = (it / 5).toInt() * 5
                                        hapticStrength = HapticStrength.CUSTOM
                                    },
                                    valueRange = 5f..100f,
                                    steps = 18, // 5,10,15,…,100 → 20 values = 18 interior steps
                                )
                            }
                            val vibrator = LocalContext.current.getSystemService(Vibrator::class.java)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        triggerHaptic(
                                            vibrator         = vibrator,
                                            strength         = HapticStrength.CUSTOM,
                                            customDurationMs = hapticCustomDurationMs,
                                            customAmplitude  = hapticCustomAmplitude,
                                        )
                                    },
                                ) {
                                    Text(
                                        text  = stringResource(R.string.macropad_haptic_custom_test),
                                        color = accentColor,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }

                SectionLabel(stringResource(R.string.macropad_editor_action), accentColor)
                ActionPicker(
                    current        = action,
                    accentColor    = accentColor,
                    enableKeyboard = enableKeyboard,
                    enableGamepad  = enableGamepad,
                    enableMouse    = enableMouse,
                    onEditMacro    = onEditMacro,
                    onChange       = ::onActionChanged,
                )
            }
        }

        // ── Icon picker overlay ──────────────────────────────────────────────
        if (showIconPicker) {
            IconPickerDialog(
                selectedIcon   = iconName,
                accentColor    = accentColor,
                filled         = iconFilled,
                onFilledChange = { iconFilled = it },
                onSelect       = { name -> iconName = name; showIconPicker = false },
                onDismiss      = { showIconPicker = false },
                modifier       = Modifier.fillMaxSize(),
            )
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Section label helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SectionLabel(text: String, accentColor: Color) {
    Text(text, color = accentColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
}
