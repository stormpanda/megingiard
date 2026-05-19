package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import com.stormpanda.megingiard.ui.blockPointerEvents
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.stormpanda.megingiard.ui.AppContentDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TAG = "MacroListEditor"

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val ML_TOP_BAR_HEIGHT = 56
private const val ML_PADDING        = 16

// ─────────────────────────────────────────────────────────────────────────────
// MacroListEditor — navigation host (no self-wrapping Dialog)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen per-profile macro editor (flat list, no folders).
 *
 * Hosts two views via internal navigation:
 * - **[MacroListView]** — flat list of macros belonging to the active profile.
 * - **[MacroTimelineEditor]** — step editor for a single macro.
 *
 * @param onDone             Called when the user taps Done on the list view to close the editor.
 * @param initialEditMacroId When non-null, opens [MacroTimelineEditor] immediately for the macro
 *                           with this id (used when navigating directly from a button config).
 */
@Composable
internal fun MacroListEditor(
    onDone: () -> Unit,
    initialEditMacroId: String? = null,
) {
    val colors       = LocalAppColors.current
    val accentColor  = colors.accent
    var editingMacro by remember { mutableStateOf<Macro?>(null) }

    // If the caller wants to open a specific macro immediately, look it up once.
    LaunchedEffect(initialEditMacroId) {
        if (initialEditMacroId != null && editingMacro == null) {
            val macro = MacroPadState.activeProfile.value?.macros
                ?.firstOrNull { it.id == initialEditMacroId }
            if (macro != null) editingMacro = macro
        }
    }
    val defaultName  = stringResource(R.string.macropad_macro_default_name)
    val copyNameFormat = stringResource(R.string.macropad_macro_copy_name)

    val profile by MacroPadState.activeProfile.collectAsState()
    val macros = profile?.macros ?: emptyList()

    if (editingMacro == null) {
        MacroListView(
            accentColor  = accentColor,
            macros       = macros,
            onEditMacro  = { editingMacro = it },
            onDuplicateMacro = { original ->
                val existingNames = macros.map { it.name }.toSet()
                val baseName = copyNameFormat.format(original.name)
                val copyName = if (baseName !in existingNames) baseName else {
                    var n = 2
                    while ("$baseName ($n)" in existingNames) n++
                    "$baseName ($n)"
                }
                MacroPadState.addMacro(original.copy(id = UUID.randomUUID().toString(), name = copyName))
            },
            onNewMacro = {
                editingMacro = Macro(id = UUID.randomUUID().toString(), name = defaultName, steps = emptyList())
            },
            onDone = onDone,
        )
    } else {
        MacroTimelineEditor(
            macro       = editingMacro!!,
            accentColor = accentColor,
            onSave      = { saved ->
                val isNew = macros.none { it.id == saved.id }
                if (isNew) {
                    MacroPadState.addMacro(saved)
                } else {
                    MacroPadState.updateMacro(saved)
                }
                editingMacro = null
            },
            onBack = { editingMacro = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro list view — flat list (no folders)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroListView(
    accentColor:      Color,
    macros:           List<Macro>,
    onEditMacro:      (Macro) -> Unit,
    onDuplicateMacro: (Macro) -> Unit,
    onNewMacro:       () -> Unit,
    onDone:           () -> Unit,
) {
    val colors = LocalAppColors.current

    var deletingMacroId by remember { mutableStateOf<String?>(null) }

    val lazyListState   = rememberLazyListState()
    val latestMacros    by rememberUpdatedState(macros)

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = from.index
        val toIdx   = to.index
        if (fromIdx in latestMacros.indices && toIdx in latestMacros.indices) {
            val mutable = latestMacros.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            MacroPadState.reorderMacros(mutable)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .blockPointerEvents(),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        val profile by MacroPadState.activeProfile.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ML_TOP_BAR_HEIGHT.dp)
                .background(colors.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = colors.onSurface,
                )
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.onSurface)) {
                        append(stringResource(R.string.macropad_macro_list_title))
                    }
                    val name = profile?.name
                    if (name != null) {
                        withStyle(SpanStyle(color = colors.onSurfaceSecondary)) {
                            append(" ($name)")
                        }
                    }
                },
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewMacro) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.macropad_macro_list_new),
                    tint = accentColor,
                )
            }
        }

        AppContentDivider()

        // ── Flat macro list ──────────────────────────────────────────────────
        LazyColumn(
            state    = lazyListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "section_list") {
                Text(
                    text     = stringResource(R.string.macropad_macro_section_list).uppercase(Locale.ROOT),
                    color    = colors.sectionHeaderColor,
                    style    = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .padding(horizontal = ML_PADDING.dp, vertical = 10.dp),
                )
            }
            itemsIndexed(macros, key = { _, m -> m.id }) { _, macro ->
                ReorderableItem(reorderState, key = macro.id) { isDragging ->
                    MacroRow(
                        macro              = macro,
                        accentColor        = accentColor,
                        isDragging         = isDragging,
                        onEdit             = { onEditMacro(macro) },
                        onDuplicate        = { onDuplicateMacro(macro) },
                        onDelete           = { deletingMacroId = macro.id },
                        dragHandleModifier = Modifier.draggableHandle(),
                    )
                    AppContentDivider()
                }
            }
        }
    }

    // ── Delete macro confirmation ────────────────────────────────────────────
    if (deletingMacroId != null) {
        val macroId  = deletingMacroId!!
        val profile  = MacroPadState.activeProfile.value
        val refCount = profile?.layouts?.flatMap { it.buttons }
            ?.count { (it.action as? PadAction.Macro)?.macroId == macroId } ?: 0
        AlertDialog(
            containerColor   = colors.surface,
            onDismissRequest = { deletingMacroId = null },
            title   = { Text(stringResource(R.string.macropad_macro_delete_title), color = colors.onSurface) },
            text    = {
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
                    AppLog.d(TAG, "deleteMacro id=$macroId")
                    MacroPadState.deleteMacro(macroId)
                    deletingMacroId = null
                }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = LocalAppColors.current.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMacroId = null }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro row (3-dot context menu)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroRow(
    macro:              Macro,
    accentColor:        Color,
    isDragging:         Boolean,
    onEdit:             () -> Unit,
    onDuplicate:        () -> Unit,
    onDelete:           () -> Unit,
    dragHandleModifier: Modifier,
) {
    val colors       = LocalAppColors.current
    val stepCount    = macro.steps.size
    val totalMs      = macro.steps.totalDurationMs()
    val summaryLabel = stringResource(R.string.macropad_macro_list_steps, stepCount, totalMs)
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDragging) colors.surfaceVariant else Color.Transparent)
            .clickable(onClick = onEdit)
            .padding(start = ML_PADDING.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                macro.name,
                color    = colors.onSurface,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(summaryLabel, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall)
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options), tint = colors.onSurfaceSecondary)
            }
            DropdownMenu(
                expanded         = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.macropad_editor_rename), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onEdit() },
                )
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.macropad_macro_duplicate), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.macropad_macro_delete_title), color = LocalAppColors.current.error, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }

        Icon(
            imageVector        = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_reorder),
            tint               = colors.onSurfaceSecondary,
            modifier           = Modifier
                .padding(horizontal = 12.dp)
                .then(dragHandleModifier),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// New-macro chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NewMacroChip(accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ML_PADDING.dp, end = ML_PADDING.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.macropad_macro_list_new), color = accentColor, style = MaterialTheme.typography.labelMedium)
        }
    }
}
