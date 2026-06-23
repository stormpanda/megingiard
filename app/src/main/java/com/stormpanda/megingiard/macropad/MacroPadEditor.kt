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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppDropdown
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
    var showEditLayoutDialog     by remember { mutableStateOf(false) }
    var showReorderProfilesOverlay by remember { mutableStateOf(false) }
    var showReorderLayoutsOverlay by remember { mutableStateOf(false) }
    var isCanvasLocked            by remember { mutableStateOf(true) }
    var showCopyLayoutProfileDialog by remember { mutableStateOf(false) }
    var showCopyButtonLayoutDialog by remember { mutableStateOf(false) }

    // Intercept system Back when an overlay is visible, so Back closes the overlay
    // instead of dismissing the whole editor dialog.
    val anyOverlayVisible = showMacroListEditor || showAddButton ||
        editingButtonActive || buttonPendingDelete != null ||
        showNewLayoutDialog || layoutPendingDelete != null ||
        showNewProfileDialog || showRenameProfileDialog || showDeleteProfileConfirm ||
        showEditLayoutDialog || showReorderProfilesOverlay || showReorderLayoutsOverlay ||
        showCopyLayoutProfileDialog || showCopyButtonLayoutDialog
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
            showEditLayoutDialog     -> showEditLayoutDialog = false
            showReorderProfilesOverlay -> showReorderProfilesOverlay = false
            showReorderLayoutsOverlay -> showReorderLayoutsOverlay = false
            showCopyLayoutProfileDialog -> showCopyLayoutProfileDialog = false
            showCopyButtonLayoutDialog -> showCopyButtonLayoutDialog = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.appBackground,
            topBar = {
                EditorTopBar(
                    onDone = onDone,
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
                    profiles                = profiles,
                    profile                 = profile,
                    layout                  = activeLayout,
                    accentColor             = colors.accent,
                    onSelectProfile         = { MacroPadState.setActiveProfileId(it) },
                    onNewProfile            = { showNewProfileDialog = true },
                    onEditProfile           = { showRenameProfileDialog = true },
                    onDeleteProfile         = { showDeleteProfileConfirm = true },
                    onSelectLayout          = { MacroPadState.setActiveLayoutId(it) },
                    onNewLayout             = { showNewLayoutDialog = true },
                    onEditLayout            = { showEditLayoutDialog = true },
                    onDeleteLayoutRequested = { lay -> layoutPendingDelete = lay },
                    onManageMacros          = { showMacroListEditor = true },
                    onAddButton             = { showAddButton = true },
                    onEditButton            = { btn -> editingButton = btn; editingButtonActive = true },
                    onCopyToProfile         = { showCopyLayoutProfileDialog = true },
                    onCopyToLayout          = { btn -> editingButton = btn; showCopyButtonLayoutDialog = true },
                    onDeleteRequested       = { btn -> buttonPendingDelete = btn },
                    onReorderProfiles       = { showReorderProfilesOverlay = true },
                    onReorderLayouts        = { showReorderLayoutsOverlay = true },
                    isCanvasLocked          = isCanvasLocked,
                    onToggleCanvasLock      = { isCanvasLocked = !isCanvasLocked },
                    modifier                = Modifier.padding(innerPadding),
                )
            }
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
                    onEditMacro = { macro -> pendingMacroEditId = macro.id; showMacroListEditor = true },
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
                    onEditMacro = { macro -> pendingMacroEditId = macro.id; showMacroListEditor = true },
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
                onConfirm   = { name, templateButtons, templateCutouts ->
                    val sourceW = ScreenCaptureManager.captureSourceWidth.value.toFloat().let { if (it > 0f) it else 1920f }
                    val sourceH = ScreenCaptureManager.captureSourceHeight.value.toFloat().let { if (it > 0f) it else 1080f }
                    val bottomW = ScreenCaptureManager.surfaceWidth.value
                    val bottomH = ScreenCaptureManager.surfaceHeight.value
                    val defaultCutout = if (bottomW > 0f && bottomH > 0f) {
                        ScreenCutout.createDefault(sourceW, sourceH, bottomW, bottomH)
                    } else {
                        ScreenCutout.createDefault(sourceW, sourceH)
                    }
                    val newLayout = PadLayout(
                        id      = UUID.randomUUID().toString(),
                        name    = name.ifBlank { defaultLayoutName },
                        buttons = templateButtons,
                        mirrorCutouts = templateCutouts ?: listOf(defaultCutout),
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
            InlineProfileSettingsOverlay(
                title        = stringResource(R.string.settings_macropad_new_profile),
                initialName  = "",
                initialPackage = null,
                accentColor  = colors.accent,
                existingNames = profiles.map { it.name },
                onConfirm    = { name, pkg ->
                    val newProfile = PadProfile(id = UUID.randomUUID().toString(), name = name, associatedPackage = pkg)
                    MacroPadState.addProfile(newProfile)
                    showNewProfileDialog = false
                },
                onDismiss = { showNewProfileDialog = false },
            )
        }

        // Rename profile (in-tree input overlay — no Dialog window)
        if (showRenameProfileDialog && profile != null) {
            InlineProfileSettingsOverlay(
                title        = stringResource(R.string.profile_settings_title),
                initialName  = profile.name,
                initialPackage = profile.associatedPackage,
                accentColor  = colors.accent,
                existingNames = profiles.filter { it.id != profile.id }.map { it.name },
                onConfirm    = { name, pkg ->
                    MacroPadState.renameProfile(profile.id, name, pkg)
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

        // Edit layout overlay (in-tree input overlay — no Dialog window)
        if (showEditLayoutDialog && activeLayout != null) {
            val curLayout = activeLayout!!
            InlineLayoutSettingsOverlay(
                title = stringResource(R.string.macropad_editor_title),
                initialName = curLayout.name,
                initialEnabled = curLayout.enabled,
                initialButtonColorNoMirror = curLayout.buttonColorNoMirror,
                initialButtonColorMirror = curLayout.buttonColorMirror,
                accentColor = colors.accent,
                existingNames = profile?.layouts?.filter { it.id != curLayout.id }?.map { it.name } ?: emptyList(),
                onConfirm = { name, enabled, noMirrorStyle, mirrorStyle ->
                    MacroPadState.updateLayout(
                        curLayout.copy(
                            name = name,
                            enabled = enabled,
                            buttonColorNoMirror = noMirrorStyle,
                            buttonColorMirror = mirrorStyle
                        )
                    )
                    showEditLayoutDialog = false
                },
                onDismiss = { showEditLayoutDialog = false }
            )
        }

        // Render ReorderProfilesOverlay
        AnimatedVisibility(
            visible  = showReorderProfilesOverlay,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ReorderProfilesOverlay(
                profiles = profiles,
                onDone   = { showReorderProfilesOverlay = false },
            )
        }

        // Render ReorderLayoutsOverlay
        AnimatedVisibility(
            visible  = showReorderLayoutsOverlay && profile != null,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (profile != null) {
                ReorderLayoutsOverlay(
                    layouts = profile.layouts,
                    onDone  = { showReorderLayoutsOverlay = false },
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
                onDirectEditSave   = { savedMacro ->
                    showMacroListEditor = false
                    pendingMacroEditId = null
                    AppLog.d(TAG, "Direct edit macro saved: ${savedMacro.name}")
                },
                onDirectEditCancel = {
                    showMacroListEditor = false
                    val draftId = pendingMacroEditId
                    pendingMacroEditId = null
                    if (draftId != null) {
                        val macro = MacroPadState.activeProfile.value?.macros?.firstOrNull { it.id == draftId }
                        if (macro != null && macro.steps.isEmpty()) {
                            AppLog.d(TAG, "Cleaning up empty macro draft on cancel: $draftId")
                            MacroPadState.deleteMacro(draftId)
                        }
                    }
                }
            )
        }

        // Copy layout selection overlay
        if (showCopyLayoutProfileDialog && activeLayout != null && profile != null) {
            val curLayout = activeLayout!!
            InlineProfileSelectionOverlay(
                title = stringResource(R.string.macropad_editor_copy_profile_select),
                profiles = profiles,
                excludeProfileId = profile.id,
                onSelect = { targetProfileId ->
                    MacroPadState.copyLayoutToProfile(curLayout, profile.id, targetProfileId)
                    showCopyLayoutProfileDialog = false
                },
                onDismiss = { showCopyLayoutProfileDialog = false }
            )
        }

        // Copy button selection overlay
        if (showCopyButtonLayoutDialog && editingButton != null && profile != null) {
            val curButton = editingButton!!
            InlineLayoutSelectionOverlay(
                title = stringResource(R.string.macropad_editor_copy_layout_select),
                profiles = profiles,
                excludeLayoutId = activeLayout?.id,
                onSelect = { targetProfileId, targetLayoutId ->
                    MacroPadState.copyButtonToLayout(curButton, profile.id, targetProfileId, targetLayoutId)
                    showCopyButtonLayoutDialog = false
                    editingButtonActive = false
                    editingButton = null
                },
                onDismiss = { showCopyButtonLayoutDialog = false }
            )
        }
    } // end Box
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    onDone:                   () -> Unit,
) {
    val colors        = LocalAppColors.current

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.macropad_editor_title_edit_profile),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = colors.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Body
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorBody(
    profiles:                List<PadProfile>,
    profile:                 PadProfile,
    layout:                  PadLayout?,
    accentColor:             Color,
    onSelectProfile:         (String) -> Unit,
    onNewProfile:            () -> Unit,
    onEditProfile:           () -> Unit,
    onDeleteProfile:         () -> Unit,
    onSelectLayout:          (String) -> Unit,
    onNewLayout:             () -> Unit,
    onEditLayout:            () -> Unit,
    onDeleteLayoutRequested: (PadLayout) -> Unit,
    onManageMacros:          () -> Unit,
    onAddButton:             () -> Unit,
    onEditButton:            (PadButton) -> Unit,
    onCopyToProfile:         () -> Unit,
    onCopyToLayout:          (PadButton) -> Unit,
    onDeleteRequested:       (PadButton) -> Unit,
    onReorderProfiles:       () -> Unit,
    onReorderLayouts:        () -> Unit,
    isCanvasLocked:          Boolean,
    onToggleCanvasLock:      () -> Unit,
    modifier:                Modifier = Modifier,
) {
    val colors     = LocalAppColors.current
    var gridMode   by remember { mutableStateOf(GridMode.OFF) }
    val profileRef by rememberUpdatedState(profile)
    val layoutRef  by rememberUpdatedState(layout)

    val lazyListState = rememberLazyListState()
    // Items before buttons: section_profile(0), profiles(1), section_layout(2), layouts(3),
    // toolbar(4), canvas(5), section_buttons(6) → offset = 7
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
        // 1. Profile section header
        item(key = "section_profile") {
            EditorSectionHeader(
                textRes = R.string.pill_menu_profile_label,
                actionIcon = Icons.Rounded.Add,
                actionContentDescription = stringResource(R.string.settings_macropad_new_profile),
                onActionClick = onNewProfile
            )
        }

        // 2. Profile management bar
        item(key = "profiles") {
            EditorProfileChipsBar(
                profiles        = profiles,
                activeProfile   = profile,
                onSelectProfile = onSelectProfile,
                onEditProfile   = onEditProfile,
                onDuplicateProfile = { profile?.id?.let { MacroPadState.duplicateProfile(it) } },
                onReorderProfiles = onReorderProfiles,
                onDeleteProfile = onDeleteProfile,
                modifier        = Modifier
                    .background(colors.surface)
                    .padding(horizontal = MPE_PADDING)
                    .padding(vertical = MPE_PADDING),
            )
        }

        // 3. Layout section header
        item(key = "section_layout") {
            EditorSectionHeader(
                textRes = R.string.macropad_editor_section_layout,
                actionIcon = Icons.Rounded.Add,
                actionContentDescription = stringResource(R.string.settings_macropad_new_layout),
                onActionClick = onNewLayout
            )
        }

        // 4. Layout management bar
        item(key = "layouts") {
            EditorLayoutChipsBar(
                layouts        = profile.layouts,
                activeLayout   = layout,
                onSelectLayout = onSelectLayout,
                onEditLayout   = onEditLayout,
                onDuplicateLayout = { layout?.id?.let { MacroPadState.duplicateLayout(it) } },
                onCopyToProfile = onCopyToProfile,
                onReorderLayouts = onReorderLayouts,
                onDeleteLayout = { layout?.let { onDeleteLayoutRequested(it) } },
                modifier       = Modifier
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
                isCanvasLocked   = isCanvasLocked,
                onToggleCanvasLock = onToggleCanvasLock,
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
            PadCanvas(profile = profile, layout = layout, accentColor = accentColor, gridMode = gridMode, isLocked = isCanvasLocked)
        }

        // 5. Buttons section header
        item(key = "section_buttons") {
            EditorSectionHeader(
                textRes = R.string.macropad_editor_section_buttons,
                actionIcon = Icons.Rounded.Add,
                actionContentDescription = stringResource(R.string.macropad_editor_add_button),
                onActionClick = onAddButton
            )
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
                        enableTouch        = profile.enableTouch,
                        isDragging         = isDragging,
                        onEdit             = { onEditButton(btn) },
                        onDuplicate        = { MacroPadState.duplicateButtonInLayout(btn, layout.id) },
                        onCopyToLayout     = { onCopyToLayout(btn) },
                        onDelete           = { onDeleteRequested(btn) },
                        dragHandleModifier = Modifier.draggableHandle(),
                    )
                    AppDivider(modifier = Modifier.padding(horizontal = MPE_PADDING))
                }
            }
        }
    }
}
