package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.ui.AppSelectableChip
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "MacroPadEditor"

private val ED_TOP_BAR_HEIGHT          = 56.dp
private val ED_PADDING                 = 16.dp
private val ED_ITEM_PADDING            = 12.dp
private val ED_GRID_TOGGLE_SIZE        = 36.dp
private val ED_GRID_TOGGLE_MARGIN      = 8.dp
private val ED_SECTION_HEADER_V_PADDING = 10.dp

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen MacroPad layout editor.
 *
 * Opened from the Pill Menu. Allows the user to:
 * - Create, rename, and delete profiles
 * - Add, configure, reposition, and delete buttons
 * - Toggle the trackpoint area
 *
 * All changes are persisted immediately via [MacroPadState].
 *
 * @param onDone  Called when the user taps "Done" to close the editor.
 */
@Composable
fun MacroPadEditor(onDone: () -> Unit) {
    val context     = LocalContext.current
    val profiles    by MacroPadState.profiles.collectAsState()
    val activeId    by MacroPadState.activeProfileId.collectAsState()
    val colors      = LocalAppColors.current

    // Stop all uinput virtual devices while the editor is open.
    // keyinjector_arm64 registers as a hardware keyboard via uinput, which causes
    // Android to suppress the soft IME — making text fields un-typeable.
    // MacroPadViewModel.watchInjectorLifecycle() detects isEditorActive=false and
    // restarts injectors automatically when this screen is dismissed.
    DisposableEffect(Unit) {
        AppLog.i(TAG, "MacroPadEditor visible \u2192 stopping injectors")
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        onDispose { AppLog.i(TAG, "MacroPadEditor dismissed") }
    }

    val profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    var showMacroListEditor      by remember { mutableStateOf(false) }
    var pendingMacroEditId        by remember { mutableStateOf<String?>(null) }
    var showAddButton            by remember { mutableStateOf(false) }
    var editingButton            by remember { mutableStateOf<PadButton?>(null) }
    var editingButtonActive      by remember { mutableStateOf(false) }
    var buttonPendingDelete      by remember { mutableStateOf<PadButton?>(null) }
    var showNewProfileDialog     by remember { mutableStateOf(false) }
    var showRenameProfileDialog  by remember { mutableStateOf(false) }
    var showDeleteProfileConfirm by remember { mutableStateOf(false) }
    var showNewLayoutDialog      by remember { mutableStateOf(false) }
    var layoutPendingDelete      by remember { mutableStateOf<PadLayout?>(null) }

    // Intercept system Back when an overlay is visible, so Back closes the overlay
    // instead of dismissing the whole editor dialog.
    val anyOverlayVisible = showMacroListEditor || showAddButton ||
        editingButtonActive || buttonPendingDelete != null ||
        showNewLayoutDialog || layoutPendingDelete != null ||
        showNewProfileDialog || showRenameProfileDialog || showDeleteProfileConfirm
    BackHandler(enabled = anyOverlayVisible) {
        when {
            showMacroListEditor      -> showMacroListEditor = false
            showAddButton            -> showAddButton = false
            editingButtonActive      -> { editingButtonActive = false; editingButton = null }
            buttonPendingDelete != null -> buttonPendingDelete = null
            showNewLayoutDialog      -> showNewLayoutDialog = false
            layoutPendingDelete != null -> layoutPendingDelete = null
            showNewProfileDialog     -> showNewProfileDialog = false
            showRenameProfileDialog  -> showRenameProfileDialog = false
            showDeleteProfileConfirm -> showDeleteProfileConfirm = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            EditorTopBar(
                profiles                 = profiles,
                activeId                 = activeId,
                accentColor              = colors.accent,
                onSelectProfile          = { MacroPadState.setActiveProfileId(it) },
                onNewProfileRequested    = { showNewProfileDialog = true },
                onRenameProfileRequested = { showRenameProfileDialog = true },
                onDeleteProfileRequested = { showDeleteProfileConfirm = true },
                onDone                   = onDone,
            )
        }
    ) { innerPadding ->
        if (profile == null) {
            // No profile yet — show prompt
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.macropad_no_profile),
                    color = colors.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(ED_PADDING),
                )
            }
        } else {
            EditorBody(
                profile                 = profile,
                layout                  = activeLayout,
                accentColor             = colors.accent,
                onManageMacros          = { showMacroListEditor = true },
                onAddButton             = { showAddButton = true },
                onEditButton            = { btn -> editingButton = btn; editingButtonActive = true },
                onDeleteRequested       = { btn -> buttonPendingDelete = btn },
                onNewLayout             = { showNewLayoutDialog = true },
                onDeleteLayoutRequested = { lay -> layoutPendingDelete = lay },
                modifier                = Modifier.padding(innerPadding),
            )
        }
    }

    // Render MacroListEditor as a full-screen inline overlay (same window — no nested Dialog)
    AnimatedVisibility(
        visible  = showMacroListEditor,
        enter    = slideInVertically { it } + fadeIn(),
        exit     = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        MacroListEditor(
            onDone             = { showMacroListEditor = false; pendingMacroEditId = null },
            initialEditMacroId = pendingMacroEditId,
        )
    }

    // Add button overlay
    AnimatedVisibility(
        visible  = showAddButton && profile != null,
        enter    = slideInVertically { it } + fadeIn(),
        exit     = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (profile != null) {
            ButtonEditDialog(
                button      = null,
                accentColor = colors.accent,
                onEditMacro = { macro -> pendingMacroEditId = macro.id; showAddButton = false; showMacroListEditor = true },
                onConfirm   = { newBtn ->
                    val layout = MacroPadState.activeLayout.value ?: return@ButtonEditDialog
                    MacroPadState.updateLayout(layout.copy(buttons = layout.buttons + newBtn))
                    showAddButton = false
                },
                onDismiss      = { showAddButton = false },
            )
        }
    }

    // Edit existing button overlay
    AnimatedVisibility(
        visible  = editingButtonActive && editingButton != null && profile != null,
        enter    = slideInVertically { it } + fadeIn(),
        exit     = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (editingButton != null && profile != null) {
            ButtonEditDialog(
                button      = editingButton,
                accentColor = colors.accent,
                onEditMacro = { macro -> pendingMacroEditId = macro.id; editingButtonActive = false; showMacroListEditor = true },
                onConfirm   = { updated ->
                    val layout = MacroPadState.activeLayout.value ?: return@ButtonEditDialog
                    MacroPadState.updateLayout(
                        layout.copy(buttons = layout.buttons.map { if (it.id == updated.id) updated else it })
                    )
                    editingButtonActive = false
                    editingButton = null
                },
                onDismiss      = { editingButtonActive = false; editingButton = null },
            )
        }
    }

    // Delete button confirmation (in-tree — no Dialog window, works in Presentation)
    if (buttonPendingDelete != null && profile != null) {
        val pendingBtn = buttonPendingDelete!!
        InlineConfirmDeleteOverlay(
            title     = stringResource(R.string.macropad_editor_delete_button),
            body      = if (pendingBtn.action is PadAction.TrackpointMove)
                            stringResource(R.string.macropad_action_trackpoint)
                        else
                            pendingBtn.label,
            onConfirm = {
                val layout = MacroPadState.activeLayout.value
                if (layout != null) {
                    MacroPadState.updateLayout(
                        layout.copy(buttons = layout.buttons.filter { it.id != pendingBtn.id })
                    )
                }
                buttonPendingDelete = null
            },
            onDismiss = { buttonPendingDelete = null },
        )
    }

    // New layout (name input + template selection)
    if (showNewLayoutDialog && profile != null) {
        val defaultLayoutName = stringResource(R.string.pill_menu_new_layout)
        NewLayoutOverlay(
            profiles    = profiles,
            existingLayoutNames = profile.layouts.map { it.name },
            accentColor = colors.accent,
            onConfirm   = { name, templateButtons ->
                val newLayout = PadLayout(
                    id      = UUID.randomUUID().toString(),
                    name    = name.ifBlank { defaultLayoutName },
                    buttons = templateButtons,
                )
                MacroPadState.addLayout(newLayout)
                showNewLayoutDialog = false
            },
            onDismiss = { showNewLayoutDialog = false },
        )
    }

    // Delete layout confirmation
    if (layoutPendingDelete != null) {
        val pendingLayout = layoutPendingDelete!!
        InlineConfirmDeleteOverlay(
            title     = stringResource(R.string.macropad_editor_delete_layout),
            body      = pendingLayout.name,
            onConfirm = {
                MacroPadState.deleteLayout(pendingLayout.id)
                layoutPendingDelete = null
            },
            onDismiss = { layoutPendingDelete = null },
        )
    }

    // New profile (in-tree input overlay — no Dialog window)
    if (showNewProfileDialog) {
        InlineNameInputOverlay(
            title        = stringResource(R.string.settings_macropad_new_profile),
            initialValue = "",
            accentColor  = colors.accent,
            existingNames = profiles.map { it.name },
            onConfirm    = { name ->
                val newProfile = PadProfile(id = UUID.randomUUID().toString(), name = name)
                MacroPadState.addProfile(newProfile)
                showNewProfileDialog = false
            },
            onDismiss = { showNewProfileDialog = false },
        )
    }

    // Rename profile (in-tree input overlay — no Dialog window)
    if (showRenameProfileDialog && profile != null) {
        InlineNameInputOverlay(
            title        = stringResource(R.string.macropad_editor_rename),
            initialValue = profile.name,
            accentColor  = colors.accent,
            existingNames = profiles.filter { it.id != profile.id }.map { it.name },
            onConfirm    = { name ->
                MacroPadState.renameProfile(profile.id, name)
                showRenameProfileDialog = false
            },
            onDismiss = { showRenameProfileDialog = false },
        )
    }

    // Delete profile confirmation (in-tree — no Dialog window)
    if (showDeleteProfileConfirm && profile != null) {
        val activeProfile = profile
        InlineConfirmDeleteOverlay(
            title     = stringResource(R.string.macropad_editor_delete_profile),
            body      = stringResource(R.string.macropad_editor_confirm_delete),
            onConfirm = {
                MacroPadState.deleteProfile(activeProfile.id)
                showDeleteProfileConfirm = false
            },
            onDismiss = { showDeleteProfileConfirm = false },
        )
    }
    } // end Box
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    profiles:                 List<PadProfile>,
    activeId:                 String?,
    accentColor:              Color,
    onSelectProfile:          (String) -> Unit,
    onNewProfileRequested:    () -> Unit,
    onRenameProfileRequested: () -> Unit,
    onDeleteProfileRequested: () -> Unit,
    onDone:                   () -> Unit,
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }

    val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val colors        = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ED_TOP_BAR_HEIGHT)
            .background(colors.surface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDone) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.onSurface,
            )
        }

        // Profile selector
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { profileMenuExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = activeProfile?.name ?: stringResource(R.string.macropad_editor_new_profile_name),
                    color    = colors.onSurface,
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
            }

            DropdownMenu(
                expanded          = profileMenuExpanded,
                onDismissRequest  = { profileMenuExpanded = false },
                modifier          = Modifier.background(colors.surface),
            ) {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text  = p.name,
                                color = if (p.id == activeId) accentColor else colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            onSelectProfile(p.id)
                            profileMenuExpanded = false
                        },
                    )
                }
                HorizontalDivider(color = colors.divider)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_macropad_new_profile), color = accentColor, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { profileMenuExpanded = false; onNewProfileRequested() },
                )
            }
        }

        // Rename & delete buttons (only when a profile exists)
        if (activeProfile != null) {
            IconButton(onClick = { onRenameProfileRequested() }) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.macropad_editor_rename), tint = colors.onSurfaceSecondary)
            }
            IconButton(onClick = { onDeleteProfileRequested() }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_profile), tint = colors.onSurfaceSecondary)
            }
        }
    }

}

// ─────────────────────────────────────────────────────────────────────────────
// Body
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorBody(
    profile:                 PadProfile,
    layout:                  PadLayout?,
    accentColor:             Color,
    onManageMacros:          () -> Unit,
    onAddButton:             () -> Unit,
    onEditButton:            (PadButton) -> Unit,
    onDeleteRequested:       (PadButton) -> Unit,
    onNewLayout:             () -> Unit,
    onDeleteLayoutRequested: (PadLayout) -> Unit,
    modifier:                Modifier = Modifier,
) {
    val colors     = LocalAppColors.current
    var gridMode   by remember { mutableStateOf(GridMode.OFF) }
    val profileRef by rememberUpdatedState(profile)
    val layoutRef  by rememberUpdatedState(layout)

    val lazyListState = rememberLazyListState()
    // Items before buttons: section_layout(0), layouts(1), canvas(2), toolbar(3), section_buttons(4) → offset = 5
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset     = 5
        val curLayout  = layoutRef
        if (curLayout != null) {
            val newButtons = curLayout.buttons.toMutableList()
            val fromIdx    = (from.index - offset).coerceIn(0, newButtons.lastIndex)
            val toIdx      = (to.index - offset).coerceIn(0, newButtons.lastIndex)
            newButtons.add(toIdx, newButtons.removeAt(fromIdx))
            MacroPadState.updateLayout(curLayout.copy(buttons = newButtons))
        }
    }

    LazyColumn(
        state          = lazyListState,
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ED_PADDING),
    ) {
        // 1. Layout section header
        item(key = "section_layout") {
            EditorSectionHeader(R.string.macropad_editor_section_layout)
        }

        // 2. Layout management bar
        item(key = "layouts") {
            EditorLayoutBar(
                profile                 = profile,
                activeLayoutId          = profile.activeLayoutId ?: profile.layouts.firstOrNull()?.id,
                accentColor             = accentColor,
                onSelectLayout          = { id -> MacroPadState.setActiveLayoutId(id) },
                onToggleEnabled         = { id, enabled -> MacroPadState.setLayoutEnabled(id, enabled) },
                onDeleteLayoutRequested = onDeleteLayoutRequested,
                onNewLayout             = onNewLayout,
                modifier                = Modifier
                    .background(colors.surface)
                    .padding(horizontal = ED_PADDING)
                    .padding(vertical = ED_PADDING),
            )
        }

        // 3. Pad canvas with grid toggle overlay
        item(key = "canvas") {
            Box {
                PadCanvas(profile = profile, layout = layout, accentColor = accentColor, gridMode = gridMode)

                val gridIcon = when (gridMode) {
                    GridMode.OFF         -> Icons.Rounded.GridOff
                    GridMode.RECTANGULAR -> Icons.Rounded.Grid4x4
                    GridMode.RADIAL      -> Icons.Rounded.TripOrigin
                }
                val gridCd = when (gridMode) {
                    GridMode.OFF         -> stringResource(R.string.macropad_editor_grid_off)
                    GridMode.RECTANGULAR -> stringResource(R.string.macropad_editor_grid_rectangular)
                    GridMode.RADIAL      -> stringResource(R.string.macropad_editor_grid_radial)
                }
                IconButton(
                    onClick = {
                        gridMode = when (gridMode) {
                            GridMode.OFF         -> GridMode.RECTANGULAR
                            GridMode.RECTANGULAR -> GridMode.RADIAL
                            GridMode.RADIAL      -> GridMode.OFF
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ED_GRID_TOGGLE_MARGIN)
                        .size(ED_GRID_TOGGLE_SIZE)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.8f)),
                ) {
                    Icon(
                        gridIcon,
                        contentDescription = gridCd,
                        tint     = if (gridMode == GridMode.OFF) colors.onSurfaceSecondary else accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 4. Action toolbar (Add Button / Macros…)
        item(key = "toolbar") {
            EditorToolbar(
                profile        = profile,
                accentColor    = accentColor,
                onManageMacros = onManageMacros,
                onAddButton    = onAddButton,
                modifier       = Modifier
                    .background(colors.surface)
                    .padding(horizontal = ED_PADDING)
                    .padding(vertical = ED_PADDING),
            )
        }

        // 5. Buttons section header
        item(key = "section_buttons") {
            EditorSectionHeader(R.string.macropad_editor_section_buttons)
        }

        // 6. Button list — tap to edit, drag handle to reorder
        if (layout?.buttons.isNullOrEmpty()) {
            item(key = "empty") {
                Text(
                    text     = stringResource(R.string.macropad_editor_add_button),
                    color    = colors.onSurfaceSecondary,
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(horizontal = ED_PADDING)
                        .padding(vertical = 8.dp),
                )
            }
        } else {
            itemsIndexed(layout?.buttons ?: emptyList(), key = { _, btn -> btn.id }) { _, btn ->
                ReorderableItem(reorderState, key = btn.id) { isDragging ->
                    ButtonListItem(
                        btn                = btn,
                        accentColor        = accentColor,
                        enableKeyboard     = profile.enableKeyboard,
                        enableGamepad      = profile.enableGamepad,
                        enableMouse        = profile.enableMouse,
                        isDragging         = isDragging,
                        onEdit             = { onEditButton(btn) },
                        onUpdate           = { updated ->
                            val curLayout = MacroPadState.activeLayout.value
                            if (curLayout != null) {
                                MacroPadState.updateLayout(
                                    curLayout.copy(buttons = curLayout.buttons.map {
                                        if (it.id == btn.id) updated else it
                                    })
                                )
                            }
                        },
                        onDelete           = { onDeleteRequested(btn) },
                        dragHandleModifier = Modifier.draggableHandle(),
                        modifier           = Modifier.padding(horizontal = ED_PADDING),
                    )
                    HorizontalDivider(
                        color    = colors.divider,
                        modifier = Modifier.padding(horizontal = ED_PADDING),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout management bar — horizontal chip row with enable toggle and delete
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorLayoutBar(
    profile:                 PadProfile,
    activeLayoutId:          String?,
    accentColor:             Color,
    onSelectLayout:          (String) -> Unit,
    onToggleEnabled:         (String, Boolean) -> Unit,
    onDeleteLayoutRequested: (PadLayout) -> Unit,
    onNewLayout:             () -> Unit,
    modifier:                Modifier = Modifier,
) {
    val canDelete = profile.layouts.size > 1
    val latestLayouts by rememberUpdatedState(profile.layouts)

    val lazyRowState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyRowState) { from, to ->
        val fromIdx = latestLayouts.indexOfFirst { it.id == from.key as? String }
        val toIdx   = latestLayouts.indexOfFirst { it.id == to.key as? String }
        if (fromIdx >= 0 && toIdx >= 0) {
            val mutable = latestLayouts.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            MacroPadState.reorderLayouts(mutable)
        }
    }

    Row(
        modifier              = modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ED_ITEM_PADDING),
    ) {
        LazyRow(
            state                 = lazyRowState,
            modifier              = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding        = PaddingValues(vertical = 4.dp),
        ) {
            items(profile.layouts, key = { it.id }) { layout ->
                ReorderableItem(reorderState, key = layout.id) {
                    val isActive = layout.id == activeLayoutId ||
                        (activeLayoutId == null && profile.layouts.firstOrNull()?.id == layout.id)
                    LayoutChip(
                        layout       = layout,
                        isActive     = isActive,
                        canDelete    = canDelete,
                        onSelect     = { onSelectLayout(layout.id) },
                        onToggle     = { onToggleEnabled(layout.id, !layout.enabled) },
                        onDelete     = { onDeleteLayoutRequested(layout) },
                        dragModifier = Modifier.longPressDraggableHandle(),
                    )
                }
            }
        }

        IconButton(
            onClick  = onNewLayout,
            modifier = Modifier.size(ED_GRID_TOGGLE_SIZE),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_macropad_new_layout),
                tint     = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LayoutChip(
    layout:       PadLayout,
    isActive:     Boolean,
    canDelete:    Boolean,
    onSelect:     () -> Unit,
    onToggle:     () -> Unit,
    onDelete:     () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val chipAlpha = if (layout.enabled) 1f else 0.45f

    AppSelectableChip(
        text     = layout.name,
        selected = isActive,
        onClick  = onSelect,
        modifier = Modifier
            .alpha(chipAlpha)
            .then(dragModifier),
        trailingContent = { contentColor ->
            IconButton(
                onClick  = onToggle,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector        = if (layout.enabled) Icons.Rounded.CheckCircle
                                         else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(R.string.cd_layout_enable_toggle),
                    tint               = contentColor.copy(alpha = 0.75f),
                    modifier           = Modifier.size(14.dp),
                )
            }
            if (canDelete) {
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.macropad_editor_delete_layout),
                        tint               = contentColor.copy(alpha = 0.75f),
                        modifier           = Modifier.size(14.dp),
                    )
                }
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Toolbar: add button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorToolbar(
    profile:         PadProfile,
    accentColor:     Color,
    onManageMacros:  () -> Unit,
    onAddButton:     () -> Unit,
    modifier:        Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ED_ITEM_PADDING),
    ) {
        // Add Button
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_add_button),
            icon        = Icons.Rounded.Add,
            accentColor = accentColor,
            onClick     = onAddButton,
            modifier    = Modifier.weight(1f),
        )
        // Manage Macros
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_manage_macros),
            icon        = Icons.Rounded.Edit,
            accentColor = accentColor,
            onClick     = onManageMacros,
            modifier    = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditorSectionHeader(@StringRes textRes: Int) {
    val colors = LocalAppColors.current
    Text(
        text     = stringResource(textRes).uppercase(Locale.ROOT),
        color    = colors.accent,
        style    = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = ED_PADDING, vertical = ED_SECTION_HEADER_V_PADDING),
    )
}

@Composable
private fun EditorActionChip(
    label:       String,
    icon:        ImageVector,
    accentColor: Color,
    onClick:     () -> Unit,
    modifier:    Modifier = Modifier,
    enabled:     Boolean = true,
) {
    val effectiveColor = if (enabled) accentColor else accentColor.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, effectiveColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = effectiveColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = effectiveColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Button list item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ButtonListItem(
    btn:                PadButton,
    accentColor:        Color,
    enableKeyboard:     Boolean,
    enableGamepad:      Boolean,
    enableMouse:        Boolean,
    isDragging:         Boolean,
    onEdit:             () -> Unit,
    onUpdate:           (PadButton) -> Unit,
    onDelete:           () -> Unit,
    dragHandleModifier: Modifier,
    modifier:           Modifier = Modifier,
) {
    val colors      = LocalAppColors.current

    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val isDeviceDisabled = when (btn.action) {
        is PadAction.KeyboardKey                 -> !enableKeyboard
        is PadAction.GamepadButton               -> !enableGamepad
        is PadAction.MouseButton,
        is PadAction.ScrollWheel,
        is PadAction.TrackpointMove              -> !enableMouse
        is PadAction.Macro                       -> !enableGamepad
        is PadAction.AmbientPeek                 -> false
        is PadAction.LayoutNext,
        is PadAction.LayoutPrevious,
        is PadAction.ProfileSwitcher,
        is PadAction.MirrorPlayStop,
        is PadAction.MirrorFreeze,
        is PadAction.MirrorViewportEdit,
        is PadAction.MirrorTouchProjection       -> false
        is PadAction.FullScreenMouse             -> !enableMouse
        is PadAction.FullScreenKeyboard          -> !enableKeyboard
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDeviceDisabled) 0.38f else 1f)
            .background(if (isDragging) colors.surfaceVariant else colors.surface)
            .clickable { onEdit() }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shape indicator
        val chipShape = if (isTrackpoint || btn.buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(4.dp)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(chipShape)
                .background(accentColor.copy(alpha = 0.2f))
                .border(1.dp, accentColor, chipShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isTrackpoint) {
                Text("●", color = colors.onSurface, style = MaterialTheme.typography.labelSmall)
            } else {
                val iconName = btn.iconName
                if (iconName != null) {
                    MaterialSymbol(
                        name = iconName,
                        size = 18.dp,
                        tint = colors.onSurface,
                        filled = btn.iconFilled,
                    )
                } else {
                    Text(btn.label.take(2), color = colors.onSurface, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isTrackpoint) {
                val sizeLabel = when ((btn.action as PadAction.TrackpointMove).size) {
                    TrackpointSize.SMALL  -> stringResource(R.string.macropad_trackpoint_size_small)
                    TrackpointSize.MEDIUM -> stringResource(R.string.macropad_trackpoint_size_medium)
                    TrackpointSize.LARGE  -> stringResource(R.string.macropad_trackpoint_size_large)
                }
                Text(stringResource(R.string.macropad_action_trackpoint), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(sizeLabel, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            } else {
                Text(btn.label, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(btn.action.displayLabel(), color = colors.onSurfaceSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!isTrackpoint && btn.action !is PadAction.ScrollWheel) {
            Text(
                text = "${btn.buttonSize.cols}×${btn.buttonSize.rows}",
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        IconButton(onClick = { onDelete() }) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_button), tint = colors.onSurfaceSecondary)
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
// Helpers & small UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InlineConfirmDeleteOverlay(
    title:    String,
    body:     String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(ED_PADDING),
        ) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = colors.onSurfaceSecondary)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = LocalAppColors.current.error)
                }
            }
        }
    }
}

@Composable
private fun InlineNameInputOverlay(
    title:        String,
    initialValue: String,
    accentColor:  Color,
    existingNames: List<String>,
    onConfirm:    (String) -> Unit,
    onDismiss:    () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val normalizedName = text.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(ED_PADDING),
        ) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                isError       = hasError,
                supportingText = {
                    when {
                        normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                        isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                    }
                },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = colors.accentBorder,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = accentColor,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(
                    onClick = { if (!hasError) onConfirm(normalizedName) },
                    enabled = !hasError,
                ) {
                    Text(
                        stringResource(R.string.macropad_editor_done),
                        color = if (!hasError) accentColor else colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// New layout overlay — name input + template selection
// ─────────────────────────────────────────────────────────────────────────────

private data class TemplateOption(
    val profileName: String,
    val layoutName: String,
    val buttons: List<PadButton>,
)

@Composable
private fun NewLayoutOverlay(
    profiles:    List<PadProfile>,
    existingLayoutNames: List<String>,
    accentColor: Color,
    onConfirm:   (name: String, templateButtons: List<PadButton>) -> Unit,
    onDismiss:   () -> Unit,
) {
    var text             by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf<TemplateOption?>(null) }
    val normalizedName = text.trim()
    val isDuplicate = existingLayoutNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val colors           = LocalAppColors.current
    val blankLabel       = stringResource(R.string.macropad_layout_template_blank)

    val templates = remember(profiles) {
        profiles.flatMap { profile ->
            profile.layouts.map { layout ->
                TemplateOption(
                    profileName = profile.name,
                    layoutName  = layout.name,
                    buttons     = layout.buttons,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(ED_PADDING),
        ) {
            Text(
                stringResource(R.string.settings_macropad_new_layout),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                isError       = hasError,
                supportingText = {
                    when {
                        normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                        isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                    }
                },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = colors.accentBorder,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = accentColor,
                ),
            )

            if (templates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.macropad_layout_template_title),
                    color      = colors.onSurfaceSecondary,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))

                // Blank option
                TemplateRow(
                    label       = blankLabel,
                    subtitle    = null,
                    isSelected  = selectedTemplate == null,
                    accentColor = accentColor,
                    onClick     = { selectedTemplate = null },
                )

                // Template options from all profiles
                templates.forEach { tmpl ->
                    TemplateRow(
                        label       = tmpl.layoutName,
                        subtitle    = tmpl.profileName,
                        isSelected  = selectedTemplate == tmpl,
                        accentColor = accentColor,
                        onClick     = { selectedTemplate = tmpl },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
                TextButton(
                    onClick = {
                        val buttons = selectedTemplate?.buttons?.map {
                            it.copy(id = UUID.randomUUID().toString())
                        } ?: emptyList()
                        onConfirm(normalizedName, buttons)
                    },
                    enabled = !hasError,
                ) {
                    Text(
                        stringResource(R.string.macropad_editor_done),
                        color = if (!hasError) accentColor else colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    label:       String,
    subtitle:    String?,
    isSelected:  Boolean,
    accentColor: Color,
    onClick:     () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = label,
                color    = if (isSelected) accentColor else colors.onSurface,
                style    = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle != null) {
                Text(
                    text     = subtitle,
                    color    = colors.onSurfaceSecondary,
                    style    = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Rounded.TripOrigin,
                contentDescription = null,
                tint     = accentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
