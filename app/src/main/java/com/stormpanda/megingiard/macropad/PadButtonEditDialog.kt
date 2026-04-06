package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID

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
    onConfirm:      (PadButton) -> Unit,
    onDismiss:      () -> Unit,
) {
    val initAction = button?.action
        ?: initialAction
        ?: PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
    val initLabel = button?.label ?: when (val ia = initialAction) {
        is PadAction.Macro -> MacroState.macros.value.firstOrNull { it.id == ia.macroId }?.name ?: ""
        else               -> ""
    }
    var label         by remember { mutableStateOf(initLabel) }
    var buttonShape   by remember { mutableStateOf(button?.buttonShape ?: ButtonShape.CIRCLE) }
    var buttonSize    by remember { mutableStateOf(button?.buttonSize ?: ButtonSize.SIZE_1X1) }
    var showSizeMenu  by remember { mutableStateOf(false) }
    var action        by remember { mutableStateOf(initAction) }
    val colors        = LocalAppColors.current

    fun onActionChanged(newAction: PadAction) {
        action = newAction
        if (newAction is PadAction.ScrollWheel) {
            buttonSize = ButtonSize.SIZE_1X2
            label = ""
        }
        if (newAction is PadAction.TrackpointMove) {
            label = ""
            buttonShape = ButtonShape.CIRCLE
        }
        if (newAction is PadAction.Macro && label.isBlank()) {
            val macroName = MacroState.macros.value.firstOrNull { it.id == newAction.macroId }?.name
            if (macroName != null) label = macroName
        }
    }

    val isConfirmEnabled = when {
        action is PadAction.ScrollWheel || action is PadAction.TrackpointMove -> true
        action is PadAction.Macro -> label.isNotBlank() &&
            MacroState.macros.value.any { it.id == (action as PadAction.Macro).macroId }
        else -> label.isNotBlank()
    }

    AlertDialog(
        containerColor   = colors.surface,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text  = if (button == null) stringResource(R.string.macropad_editor_add_button)
                        else if (button.action is PadAction.TrackpointMove) stringResource(R.string.macropad_action_trackpoint)
                        else button.label,
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Label input and shape — hidden for ScrollWheel and TrackpointMove
                if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove) {
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
                    )

                    SectionLabel(stringResource(R.string.macropad_editor_button_shape), accentColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonShape.entries.forEach { shape ->
                            val selected = shape == buttonShape
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(if (shape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp))
                                    .background(if (selected) accentColor.copy(alpha = 0.3f) else colors.surface)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) accentColor else colors.accentBorder,
                                        shape = if (shape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp),
                                    )
                                    .clickable { buttonShape = shape },
                            ) {
                                Text(
                                    text  = if (shape == ButtonShape.CIRCLE)
                                        stringResource(R.string.macropad_editor_shape_circle)
                                    else
                                        stringResource(R.string.macropad_editor_shape_square),
                                    color = if (selected) accentColor else colors.onSurfaceSecondary,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // Size section — TrackpointSize picker for trackpoint, locked label for ScrollWheel, dropdown otherwise
                SectionLabel(
                    text = if (action is PadAction.TrackpointMove)
                        stringResource(R.string.macropad_editor_trackpoint_size)
                    else
                        stringResource(R.string.macropad_editor_button_size),
                    accentColor = accentColor,
                )
                if (action is PadAction.ScrollWheel) {
                    Text(
                        text     = ButtonSize.SIZE_1X2.displayLabel(),
                        color    = colors.onSurfaceSecondary,
                        fontSize = 14.sp,
                    )
                } else if (action is PadAction.TrackpointMove) {
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
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) accentColor.copy(alpha = 0.3f) else colors.surface)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) accentColor else colors.accentBorder,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable { action = PadAction.TrackpointMove(sz) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(szLabel, color = if (selected) accentColor else colors.onSurfaceSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                                .clickable { showSizeMenu = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(buttonSize.displayLabel(), color = colors.onSurface, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = colors.onSurfaceSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded         = showSizeMenu,
                            onDismissRequest = { showSizeMenu = false },
                            modifier         = Modifier.background(colors.surface),
                        ) {
                            ButtonSize.entries.forEach { size ->
                                DropdownMenuItem(
                                    text    = { Text(size.displayLabel(), color = colors.onSurface) },
                                    onClick = { buttonSize = size; showSizeMenu = false },
                                )
                            }
                        }
                    }
                }

                // Action picker
                SectionLabel(stringResource(R.string.macropad_editor_action), accentColor)
                ActionPicker(
                    current        = action,
                    accentColor    = accentColor,
                    enableKeyboard = enableKeyboard,
                    enableGamepad  = enableGamepad,
                    enableMouse    = enableMouse,
                    onChange       = ::onActionChanged,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isConfirmEnabled) {
                        val result = button?.copy(
                            label       = label,
                            buttonShape = buttonShape,
                            buttonSize  = buttonSize,
                            action      = action,
                        ) ?: PadButton(
                            id          = UUID.randomUUID().toString(),
                            label       = label,
                            posX        = 0.5f,
                            posY        = 0.5f,
                            buttonShape = buttonShape,
                            buttonSize  = buttonSize,
                            action      = action,
                        )
                        onConfirm(result)
                    }
                },
                enabled = isConfirmEnabled,
            ) {
                Text(stringResource(R.string.macropad_editor_done), color = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Section label helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SectionLabel(text: String, accentColor: Color) {
    Text(text, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}
