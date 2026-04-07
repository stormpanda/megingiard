package com.stormpanda.megingiard.macropad

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val ML_TOP_BAR_HEIGHT = 56
private const val ML_PADDING        = 16

// ─────────────────────────────────────────────────────────────────────────────
// MacroListEditor — navigation host (no self-wrapping Dialog)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen macro library editor.
 *
 * Hosts two views via internal navigation:
 * - **[MacroListView]** — lists all global macros with create and delete actions.
 * - **[MacroTimelineEditor]** — step editor for a single macro.
 *
 * The caller is responsible for wrapping this in a `Dialog(usePlatformDefaultWidth = false)`.
 *
 * @param onDone Called when the user taps Done on the list view to close the editor.
 */
@Composable
internal fun MacroListEditor(onDone: () -> Unit) {
    val colors      = LocalAppColors.current
    val accentColor = colors.accent
    var editingMacro by remember { mutableStateOf<Macro?>(null) }
    val defaultName  = stringResource(R.string.macropad_macro_default_name)
    val copyNameFormat = stringResource(R.string.macropad_macro_copy_name)

    if (editingMacro == null) {
        MacroListView(
            accentColor  = accentColor,
            onEditMacro  = { editingMacro = it },
            onDuplicateMacro = { original ->
                val existingNames = MacroState.macros.value.map { it.name }.toSet()
                val baseName = copyNameFormat.format(original.name)
                val copyName = if (baseName !in existingNames) {
                    baseName
                } else {
                    var n = 2
                    while ("$baseName ($n)" in existingNames) n++
                    "$baseName ($n)"
                }
                MacroState.addMacro(
                    original.copy(id = UUID.randomUUID().toString(), name = copyName)
                )
            },
            onNewMacro  = {
                editingMacro = Macro(
                    id    = UUID.randomUUID().toString(),
                    name  = defaultName,
                    steps = emptyList(),
                )
            },
            onDone = onDone,
        )
    } else {
        MacroTimelineEditor(
            macro       = editingMacro!!,
            accentColor = accentColor,
            onSave      = { saved ->
                val isNew = MacroState.macros.value.none { it.id == saved.id }
                if (isNew) MacroState.addMacro(saved) else MacroState.updateMacro(saved)
                editingMacro = null
            },
            onBack      = { editingMacro = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro list view
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroListView(
    accentColor:      Color,
    onEditMacro:      (Macro) -> Unit,
    onDuplicateMacro: (Macro) -> Unit,
    onNewMacro:       () -> Unit,
    onDone:           () -> Unit,
) {
    val macros   by MacroState.macros.collectAsState()
    val colors   = LocalAppColors.current
    var deletingId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ML_TOP_BAR_HEIGHT.dp)
                .background(colors.surface)
                .padding(horizontal = ML_PADDING.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.macropad_macro_list_title),
                color      = colors.onSurface,
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) {
                Text(
                    stringResource(R.string.macropad_editor_done),
                    color      = accentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        HorizontalDivider(color = colors.divider)

        // Scrollable list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (macros.isEmpty()) {
                Text(
                    stringResource(R.string.macropad_macro_list_empty),
                    color     = colors.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                    fontSize  = 13.sp,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }

            macros.forEach { macro ->
                MacroRow(
                    macro       = macro,
                    accentColor = accentColor,
                    onEdit      = { onEditMacro(macro) },
                    onDuplicate = { onDuplicateMacro(macro) },
                    onDelete    = { deletingId = macro.id },
                )
                HorizontalDivider(color = colors.divider)
            }

            // New Macro chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ML_PADDING.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onNewMacro)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint     = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.macropad_macro_list_new),
                        color    = accentColor,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (deletingId != null) {
        val macroId  = deletingId!!
        val refCount = MacroPadState.profiles.value
            .sumOf { p -> p.buttons.count { b -> (b.action as? PadAction.Macro)?.macroId == macroId } }
        AlertDialog(
            containerColor   = colors.surface,
            onDismissRequest = { deletingId = null },
            title = {
                Text(stringResource(R.string.macropad_macro_delete_title), color = colors.onSurface)
            },
            text = {
                Text(
                    if (refCount > 0)
                        stringResource(R.string.macropad_macro_delete_confirm_referenced, refCount)
                    else
                        stringResource(R.string.macropad_macro_delete_confirm),
                    color = colors.onSurfaceSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    MacroState.deleteMacro(macroId)
                    deletingId = null
                }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroRow(
    macro:       Macro,
    accentColor: Color,
    onEdit:      () -> Unit,
    onDuplicate: () -> Unit,
    onDelete:    () -> Unit,
) {
    val colors        = LocalAppColors.current
    val stepCount     = macro.steps.size
    val totalMs       = macro.steps.totalDurationMs()
    val summaryLabel  = stringResource(R.string.macropad_macro_list_steps, stepCount, totalMs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = ML_PADDING.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                macro.name,
                color    = colors.onSurface,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summaryLabel,
                color    = colors.onSurfaceSecondary,
                fontSize = 12.sp,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.macropad_editor_rename),
                tint               = colors.onSurfaceSecondary,
            )
        }
        IconButton(onClick = onDuplicate) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.macropad_macro_duplicate),
                tint               = colors.onSurfaceSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.macropad_macro_delete_title),
                tint               = colors.onSurfaceSecondary,
            )
        }
    }
}
