package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "MacroPadEditor"

internal val MPE_TOP_BAR_HEIGHT          = 56.dp
internal val MPE_PADDING                 = 16.dp
internal val MPE_ITEM_PADDING            = 12.dp
internal val MPE_GRID_TOGGLE_SIZE        = 36.dp
internal val MPE_SECTION_HEADER_V_PADDING = 10.dp

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
                        modifier  = Modifier.padding(MPE_PADDING),
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
    val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val colors        = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MPE_TOP_BAR_HEIGHT)
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

        AppDropdown(
            selected     = activeProfile,
            options      = profiles,
            optionText   = { profile -> profile?.name ?: stringResource(R.string.macropad_editor_new_profile_name) },
            onSelected   = { profile -> if (profile != null) onSelectProfile(profile.id) },
            modifier     = Modifier.weight(1f),
            textStyle    = MaterialTheme.typography.titleMedium,
            fillMaxWidth = true,
            footerContent = { dismiss ->
                AppDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_macropad_new_profile), color = accentColor, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { dismiss(); onNewProfileRequested() },
                )
            },
        )

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
    // Items before buttons: section_layout(0), layouts(1), toolbar(2), canvas(3),
    // section_layout_settings(4), layout_settings(5), section_buttons(6) → offset = 7
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset     = 7
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
        state    = lazyListState,
        modifier = modifier.fillMaxSize(),
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
                    .padding(horizontal = MPE_PADDING)
                    .padding(vertical = MPE_PADDING),
            )
        }

        // 3. Action toolbar (Add Button / Macros… / Grid toggle)
        item(key = "toolbar") {
            EditorToolbar(
                profile          = profile,
                accentColor      = accentColor,
                gridMode         = gridMode,
                onManageMacros   = onManageMacros,
                onAddButton      = onAddButton,
                onGridModeChange = {
                    gridMode = when (gridMode) {
                        GridMode.OFF         -> GridMode.RECTANGULAR
                        GridMode.RECTANGULAR -> GridMode.RADIAL
                        GridMode.RADIAL      -> GridMode.OFF
                    }
                },
                modifier         = Modifier
                    .background(colors.surface)
                    .padding(horizontal = MPE_PADDING)
                    .padding(top = MPE_PADDING / 2, bottom = MPE_PADDING),
            )
        }

        // 4. Pad canvas
        item(key = "canvas") {
            PadCanvas(profile = profile, layout = layout, accentColor = accentColor, gridMode = gridMode)
        }

        // 5. Layout settings section header
        item(key = "section_layout_settings") {
            EditorSectionHeader(R.string.macropad_editor_section_layout_settings)
        }

        // 6. Layout settings content
        item(key = "layout_settings") {
            if (layout != null) {
                LayoutSettingsContent(
                    layout = layout,
                )
            }
        }

        // 7. Buttons section header
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
                        .padding(horizontal = MPE_PADDING)
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
                    )
                    AppDivider(modifier = Modifier.padding(horizontal = MPE_PADDING))
                }
            }
        }
    }
}
