package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val ML_TOP_BAR_HEIGHT  = 56
private const val ML_PADDING         = 16
private const val ML_HEADER_HEIGHT   = 48
private const val ML_SECTION_INDENT  = ML_PADDING + 44   // indent for "New Macro" chips

// ─────────────────────────────────────────────────────────────────────────────
// Flat list item types (used to build a single LazyColumn across all sections)
// ─────────────────────────────────────────────────────────────────────────────

private sealed class MacroListItem {
    abstract val key: String

    data class SectionHeader(
        val folderId: String?,
        val name: String,
        val folderIndex: Int,       // −1 for "Unassigned"
    ) : MacroListItem() {
        override val key get() = "hdr_${folderId ?: "none"}"
    }

    data class MacroEntry(
        val macro: Macro,
        val sectionFolderId: String?,
    ) : MacroListItem() {
        override val key get() = "macro_${macro.id}"
    }

    data class NewMacroButton(val folderId: String?) : MacroListItem() {
        override val key get() = "newbtn_${folderId ?: "none"}"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroListEditor — navigation host (no self-wrapping Dialog)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen macro library editor.
 *
 * Hosts two views via internal navigation:
 * - **[MacroListView]** — lists all global macros organised in folder sections.
 * - **[MacroTimelineEditor]** — step editor for a single macro.
 *
 * The caller is responsible for wrapping this in a `Dialog(usePlatformDefaultWidth = false)`.
 *
 * @param onDone Called when the user taps Done on the list view to close the editor.
 */
@Composable
internal fun MacroListEditor(onDone: () -> Unit) {
    val colors         = LocalAppColors.current
    val accentColor    = colors.accent
    var editingMacro   by remember { mutableStateOf<Macro?>(null) }
    var newMacroFolder by remember { mutableStateOf<String?>(null) }
    val defaultName    = stringResource(R.string.macropad_macro_default_name)
    val copyNameFormat = stringResource(R.string.macropad_macro_copy_name)

    if (editingMacro == null) {
        MacroListView(
            accentColor      = accentColor,
            onEditMacro      = { editingMacro = it },
            onDuplicateMacro = { original ->
                val existingNames = MacroState.macros.value.map { it.name }.toSet()
                val baseName = copyNameFormat.format(original.name)
                val copyName = if (baseName !in existingNames) baseName else {
                    var n = 2
                    while ("$baseName ($n)" in existingNames) n++
                    "$baseName ($n)"
                }
                MacroState.addMacro(original.copy(id = UUID.randomUUID().toString(), name = copyName))
            },
            onNewMacro = { folderId ->
                newMacroFolder = folderId
                editingMacro   = Macro(id = UUID.randomUUID().toString(), name = defaultName, steps = emptyList())
            },
            onDone = onDone,
        )
    } else {
        MacroTimelineEditor(
            macro       = editingMacro!!,
            accentColor = accentColor,
            onSave      = { saved ->
                val isNew = MacroState.macros.value.none { it.id == saved.id }
                if (isNew) {
                    MacroState.addMacro(saved.copy(folderId = newMacroFolder))
                } else {
                    MacroState.updateMacro(saved)
                }
                editingMacro   = null
                newMacroFolder = null
            },
            onBack = {
                editingMacro   = null
                newMacroFolder = null
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro list view — folder-sectioned list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroListView(
    accentColor:      Color,
    onEditMacro:      (Macro) -> Unit,
    onDuplicateMacro: (Macro) -> Unit,
    onNewMacro:       (folderId: String?) -> Unit,
    onDone:           () -> Unit,
) {
    val macros         by MacroState.macros.collectAsState()
    val folders        by MacroState.folders.collectAsState()
    val colors         = LocalAppColors.current
    val unassignedLabel = stringResource(R.string.macro_folder_unassigned)

    // Section expand/collapse: null key = "Unassigned"; absent = expanded
    val expandedSections = remember { mutableStateMapOf<String?, Boolean>() }

    // Dialog state
    var deletingMacroId   by remember { mutableStateOf<String?>(null) }
    var movingMacroId     by remember { mutableStateOf<String?>(null) }
    var renamingFolderId  by remember { mutableStateOf<String?>(null) }
    var deletingFolderId  by remember { mutableStateOf<String?>(null) }
    var showNewFolder     by remember { mutableStateOf(false) }

    // Flat item list derived from current state
    val flatList by remember {
        derivedStateOf {
            buildList {
                val unassignedExpanded = expandedSections[null] != false
                add(MacroListItem.SectionHeader(folderId = null, name = unassignedLabel, folderIndex = -1))
                if (unassignedExpanded) {
                    macros.filter { it.folderId == null }.forEach { add(MacroListItem.MacroEntry(it, null)) }
                    add(MacroListItem.NewMacroButton(null))
                }
                folders.forEachIndexed { idx, folder ->
                    val isExpanded = expandedSections[folder.id] != false
                    add(MacroListItem.SectionHeader(folderId = folder.id, name = folder.name, folderIndex = idx))
                    if (isExpanded) {
                        macros.filter { it.folderId == folder.id }.forEach { add(MacroListItem.MacroEntry(it, folder.id)) }
                        add(MacroListItem.NewMacroButton(folder.id))
                    }
                }
            }
        }
    }

    val lazyListState    = rememberLazyListState()
    val latestFlatList   by rememberUpdatedState(flatList)

    // Only reorder macros within the same section; cross-section drags are no-ops
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromItem = latestFlatList.firstOrNull { it.key == from.key as? String }
        val toItem   = latestFlatList.firstOrNull { it.key == to.key as? String }
        if (fromItem is MacroListItem.MacroEntry && toItem is MacroListItem.MacroEntry
            && fromItem.sectionFolderId == toItem.sectionFolderId
        ) {
            val section = macros.filter { it.folderId == fromItem.sectionFolderId }.toMutableList()
            val fromIdx = section.indexOfFirst { it.id == fromItem.macro.id }
            val toIdx   = section.indexOfFirst { it.id == toItem.macro.id }
            if (fromIdx >= 0 && toIdx >= 0) {
                section.add(toIdx, section.removeAt(fromIdx))
                MacroState.reorderMacrosInFolder(fromItem.sectionFolderId, section)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
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
            IconButton(onClick = { showNewFolder = true }) {
                Icon(
                    Icons.Filled.CreateNewFolder,
                    contentDescription = stringResource(R.string.macro_folder_new),
                    tint               = accentColor,
                )
            }
            TextButton(onClick = onDone) {
                Text(
                    stringResource(R.string.macropad_editor_done),
                    color      = accentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        HorizontalDivider(color = colors.divider)

        // ── Sectioned list ───────────────────────────────────────────────────
        LazyColumn(
            state    = lazyListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(flatList, key = { it.key }) { item ->
                when (item) {
                    is MacroListItem.SectionHeader -> {
                        FolderSectionHeader(
                            folderId      = item.folderId,
                            name          = item.name,
                            folderIndex   = item.folderIndex,
                            foldersCount  = folders.size,
                            isExpanded    = expandedSections[item.folderId] != false,
                            accentColor   = accentColor,
                            onToggleExpand = {
                                expandedSections[item.folderId] = expandedSections[item.folderId] == false
                            },
                            onRename  = { renamingFolderId = item.folderId },
                            onDelete  = { deletingFolderId = item.folderId },
                            onMoveUp  = {
                                val idx = item.folderIndex
                                if (idx > 0) {
                                    val mutable = folders.toMutableList()
                                    mutable.add(idx - 1, mutable.removeAt(idx))
                                    MacroState.reorderFolders(mutable)
                                }
                            },
                            onMoveDown = {
                                val idx = item.folderIndex
                                if (idx in 0 until folders.lastIndex) {
                                    val mutable = folders.toMutableList()
                                    mutable.add(idx + 1, mutable.removeAt(idx))
                                    MacroState.reorderFolders(mutable)
                                }
                            },
                        )
                        HorizontalDivider(color = colors.divider)
                    }

                    is MacroListItem.MacroEntry -> {
                        ReorderableItem(reorderState, key = item.key) { isDragging ->
                            MacroRow(
                                macro              = item.macro,
                                accentColor        = accentColor,
                                isDragging         = isDragging,
                                onEdit             = { onEditMacro(item.macro) },
                                onDuplicate        = { onDuplicateMacro(item.macro) },
                                onMoveToFolder     = { movingMacroId = item.macro.id },
                                onDelete           = { deletingMacroId = item.macro.id },
                                dragHandleModifier = Modifier.draggableHandle(),
                            )
                            HorizontalDivider(color = colors.divider)
                        }
                    }

                    is MacroListItem.NewMacroButton -> {
                        NewMacroChip(accentColor = accentColor, onClick = { onNewMacro(item.folderId) })
                    }
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (deletingMacroId != null) {
        val macroId  = deletingMacroId!!
        val refCount = MacroPadState.profiles.value
            .sumOf { p -> p.buttons.count { b -> (b.action as? PadAction.Macro)?.macroId == macroId } }
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
                TextButton(onClick = { MacroState.deleteMacro(macroId); deletingMacroId = null }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMacroId = null }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }

    if (movingMacroId != null) {
        val macroId        = movingMacroId!!
        val currentFolderId = macros.firstOrNull { it.id == macroId }?.folderId
        FolderPickerDialog(
            title            = stringResource(R.string.macro_folder_move_title),
            folders          = folders,
            selectedFolderId = currentFolderId,
            unassignedLabel  = unassignedLabel,
            accentColor      = accentColor,
            onSelect         = { fId -> MacroState.moveMacroToFolder(macroId, fId); movingMacroId = null },
            onDismiss        = { movingMacroId = null },
        )
    }

    if (renamingFolderId != null) {
        val fId         = renamingFolderId!!
        val currentName = folders.firstOrNull { it.id == fId }?.name ?: ""
        TextInputDialog(
            title       = stringResource(R.string.macro_folder_rename),
            hint        = stringResource(R.string.macro_folder_name_hint),
            initialValue = currentName,
            accentColor = accentColor,
            onConfirm   = { name ->
                if (name.isNotBlank()) MacroState.renameFolder(fId, name.trim())
                renamingFolderId = null
            },
            onDismiss   = { renamingFolderId = null },
        )
    }

    if (deletingFolderId != null) {
        val fId = deletingFolderId!!
        AlertDialog(
            containerColor   = colors.surface,
            onDismissRequest = { deletingFolderId = null },
            title   = { Text(stringResource(R.string.macro_folder_delete), color = colors.onSurface) },
            text    = { Text(stringResource(R.string.macro_folder_delete_confirm), color = colors.onSurfaceSecondary) },
            confirmButton = {
                TextButton(onClick = { MacroState.deleteFolder(fId); deletingFolderId = null }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolderId = null }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }

    if (showNewFolder) {
        TextInputDialog(
            title        = stringResource(R.string.macro_folder_new),
            hint         = stringResource(R.string.macro_folder_name_hint),
            initialValue = "",
            accentColor  = accentColor,
            onConfirm    = { name ->
                if (name.isNotBlank()) {
                    MacroState.addFolder(MacroFolder(id = UUID.randomUUID().toString(), name = name.trim()))
                }
                showNewFolder = false
            },
            onDismiss    = { showNewFolder = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Folder section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderSectionHeader(
    folderId:       String?,
    name:           String,
    folderIndex:    Int,
    foldersCount:   Int,
    isExpanded:     Boolean,
    accentColor:    Color,
    onToggleExpand: () -> Unit,
    onRename:       () -> Unit,
    onDelete:       () -> Unit,
    onMoveUp:       () -> Unit,
    onMoveDown:     () -> Unit,
) {
    val colors        = LocalAppColors.current
    val isNamedFolder = folderId != null
    var menuExpanded  by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ML_HEADER_HEIGHT.dp)
            .background(colors.surface)
            .clickable(onClick = onToggleExpand)
            .padding(start = ML_PADDING.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint               = colors.onSurfaceSecondary,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector        = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = null,
            tint               = if (isNamedFolder) accentColor.copy(alpha = 0.7f) else colors.onSurfaceSecondary,
            modifier           = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            color      = colors.onSurface,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.weight(1f),
        )
        if (isNamedFolder) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = colors.onSurfaceSecondary)
                }
                DropdownMenu(
                    expanded         = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier         = Modifier.background(colors.surface),
                ) {
                    if (folderIndex > 0) {
                        DropdownMenuItem(
                            text    = { Text(stringResource(R.string.macro_folder_move_up), color = colors.onSurface, fontSize = 14.sp) },
                            onClick = { menuExpanded = false; onMoveUp() },
                        )
                    }
                    if (folderIndex < foldersCount - 1) {
                        DropdownMenuItem(
                            text    = { Text(stringResource(R.string.macro_folder_move_down), color = colors.onSurface, fontSize = 14.sp) },
                            onClick = { menuExpanded = false; onMoveDown() },
                        )
                    }
                    DropdownMenuItem(
                        text    = { Text(stringResource(R.string.macro_folder_rename), color = colors.onSurface, fontSize = 14.sp) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text    = { Text(stringResource(R.string.macro_folder_delete), color = Color(0xFFCF6679), fontSize = 14.sp) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
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
    onMoveToFolder:     () -> Unit,
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
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(summaryLabel, color = colors.onSurfaceSecondary, fontSize = 12.sp)
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = colors.onSurfaceSecondary)
            }
            DropdownMenu(
                expanded         = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier         = Modifier.background(colors.surface),
            ) {
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.macropad_editor_rename), color = colors.onSurface, fontSize = 14.sp) },
                    onClick = { menuExpanded = false; onEdit() },
                )
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.macropad_macro_duplicate), color = colors.onSurface, fontSize = 14.sp) },
                    onClick = { menuExpanded = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.macro_move_to_folder), color = colors.onSurface, fontSize = 14.sp) },
                    onClick = { menuExpanded = false; onMoveToFolder() },
                )
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.macropad_macro_delete_title), color = Color(0xFFCF6679), fontSize = 14.sp) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }

        Icon(
            imageVector        = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_reorder),
            tint               = colors.onSurfaceSecondary,
            modifier           = Modifier
                .padding(horizontal = 12.dp)
                .then(dragHandleModifier),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// New-macro chip (shown at the bottom of each section)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NewMacroChip(accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ML_SECTION_INDENT.dp, end = ML_PADDING.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.macropad_macro_list_new), color = accentColor, fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Folder picker dialog (Move to Folder)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderPickerDialog(
    title:            String,
    folders:          List<MacroFolder>,
    selectedFolderId: String?,
    unassignedLabel:  String,
    accentColor:      Color,
    onSelect:         (String?) -> Unit,
    onDismiss:        () -> Unit,
) {
    val colors      = LocalAppColors.current
    val folderItems = buildList {
        add(unassignedLabel to null as String?)
        folders.forEach { add(it.name to it.id) }
    }
    AlertDialog(
        containerColor   = colors.surface,
        onDismissRequest = onDismiss,
        title   = { Text(title, color = colors.onSurface) },
        text    = {
            Column {
                folderItems.forEach { (folderName, fId) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(fId) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            folderName,
                            color    = if (fId == selectedFolderId) accentColor else colors.onSurface,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (fId == selectedFolderId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint     = accentColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Text input dialog (New Folder / Rename Folder)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TextInputDialog(
    title:        String,
    hint:         String,
    initialValue: String,
    accentColor:  Color,
    onConfirm:    (String) -> Unit,
    onDismiss:    () -> Unit,
) {
    val colors = LocalAppColors.current
    var text   by remember { mutableStateOf(initialValue) }

    AlertDialog(
        containerColor   = colors.surface,
        onDismissRequest = onDismiss,
        title   = { Text(title, color = colors.onSurface) },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                placeholder   = { Text(hint, color = colors.onSurfaceSecondary) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = colors.accentBorder,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = accentColor,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(
                    stringResource(R.string.macropad_editor_done),
                    color = if (text.isNotBlank()) accentColor else colors.onSurfaceSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
        },
    )
}
