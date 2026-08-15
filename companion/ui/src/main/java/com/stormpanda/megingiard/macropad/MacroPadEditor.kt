package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppDropdown
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalType
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "MacroPadEditor"

internal val MPE_TOP_BAR_HEIGHT = 56.dp
internal val MPE_PADDING = 16.dp
internal val MPE_ITEM_PADDING = 12.dp
internal val MPE_GRID_TOGGLE_SIZE = 36.dp
internal val MPE_SECTION_HEADER_V_PADDING = 10.dp

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen MacroPad layout editor.
 *
 * Opened from the Quick Menu. Allows the user to:
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by MacroPadState.profiles.collectAsState()
    val activeId by MacroPadState.activeProfileId.collectAsState()
    val colors = LocalAppColors.current

    DisposableEffect(Unit) {
        AppLog.i(TAG, "MacroPadEditor visible")
        onDispose { AppLog.i(TAG, "MacroPadEditor dismissed") }
    }

    val profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val activeLayout =
        remember(profile) {
            val layoutId = profile?.activeLayoutId
            profile?.layouts?.firstOrNull { it.id == layoutId } ?: profile?.layouts?.firstOrNull()
        }
    var showMacroListEditor by remember { mutableStateOf(false) }
    var pendingMacroEditId by remember { mutableStateOf<String?>(null) }
    var showAddButton by remember { mutableStateOf(false) }
    var editingButton by remember { mutableStateOf<PadButton?>(null) }
    var editingButtonActive by remember { mutableStateOf(false) }
    var buttonPendingDelete by remember { mutableStateOf<PadButton?>(null) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showRenameProfileDialog by remember { mutableStateOf(false) }
    var showDeleteProfileConfirm by remember { mutableStateOf(false) }
    var showNewLayoutDialog by remember { mutableStateOf(false) }
    var layoutPendingDelete by remember { mutableStateOf<PadLayout?>(null) }
    var showEditLayoutDialog by remember { mutableStateOf(false) }
    var showBackgroundSettingsDialog by remember { mutableStateOf(false) }
    var showTouchpadSettingsDialog by remember { mutableStateOf(false) }
    var showReorderProfilesOverlay by remember { mutableStateOf(false) }
    var showReorderLayoutsOverlay by remember { mutableStateOf(false) }
    var isCanvasLocked by remember { mutableStateOf(true) }
    var showCopyLayoutProfileDialog by remember { mutableStateOf(false) }
    var showCopyButtonLayoutDialog by remember { mutableStateOf(false) }
    var showEditorHelp by remember { mutableStateOf(false) }

    // Intercept system Back when an overlay is visible, so Back closes the overlay
    // instead of dismissing the whole editor dialog.
    val anyOverlayVisible =
        showMacroListEditor || showAddButton ||
            editingButtonActive || buttonPendingDelete != null ||
            showNewLayoutDialog || layoutPendingDelete != null ||
            showNewProfileDialog || showRenameProfileDialog || showDeleteProfileConfirm ||
            showEditLayoutDialog || showBackgroundSettingsDialog || showTouchpadSettingsDialog || showReorderProfilesOverlay ||
            showReorderLayoutsOverlay ||
            showCopyLayoutProfileDialog || showCopyButtonLayoutDialog
    BackHandler(enabled = anyOverlayVisible) {
        when {
            showMacroListEditor -> {
                showMacroListEditor = false
            }

            showAddButton -> {
                showAddButton = false
            }

            editingButtonActive -> {
                editingButtonActive = false
                editingButton = null
            }

            buttonPendingDelete != null -> {
                buttonPendingDelete = null
            }

            showNewLayoutDialog -> {
                showNewLayoutDialog = false
            }

            layoutPendingDelete != null -> {
                layoutPendingDelete = null
            }

            showNewProfileDialog -> {
                showNewProfileDialog = false
            }

            showRenameProfileDialog -> {
                showRenameProfileDialog = false
            }

            showDeleteProfileConfirm -> {
                showDeleteProfileConfirm = false
            }

            showEditLayoutDialog -> {
                showEditLayoutDialog = false
            }

            showBackgroundSettingsDialog -> {
                showBackgroundSettingsDialog = false
            }

            showTouchpadSettingsDialog -> {
                showTouchpadSettingsDialog = false
            }

            showReorderProfilesOverlay -> {
                showReorderProfilesOverlay = false
            }

            showReorderLayoutsOverlay -> {
                showReorderLayoutsOverlay = false
            }

            showCopyLayoutProfileDialog -> {
                showCopyLayoutProfileDialog = false
            }

            showCopyButtonLayoutDialog -> {
                showCopyButtonLayoutDialog = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.appBackground,
            topBar = {
                EditorTopBar(
                    onDone = onDone,
                    onHelpClick = { showEditorHelp = true },
                )
            },
        ) { innerPadding ->
            if (profile == null) {
                // No profile yet — show prompt
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.macropad_no_profile),
                        color = colors.onSurfaceSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(MPE_PADDING),
                    )
                }
            } else {
                EditorBody(
                    profiles = profiles,
                    profile = profile,
                    layout = activeLayout,
                    accentColor = colors.accent,
                    onSelectProfile = {
                        MacroPadState.setActiveProfileId(it)
                        AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                    },
                    onNewProfile = { showNewProfileDialog = true },
                    onEditProfile = {
                        val isDual = DisplayDetector.findSecondaryDisplay(context) != null
                        if (isDual) {
                            AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.PROFILE_SETTINGS))
                        } else {
                            showRenameProfileDialog = true
                        }
                    },
                    onDeleteProfile = { showDeleteProfileConfirm = true },
                    onSelectLayout = {
                        MacroPadState.setActiveLayoutId(it)
                        AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                    },
                    onNewLayout = { showNewLayoutDialog = true },
                    onEditLayout = {
                        val isDual = DisplayDetector.findSecondaryDisplay(context) != null
                        if (isDual) {
                            MacroPadState.setSelectedButtonId(null)
                            AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.LAYOUT_SETTINGS))
                        } else {
                            showEditLayoutDialog = true
                        }
                    },
                    onDeleteLayoutRequested = { lay -> layoutPendingDelete = lay },
                    onManageMacros = { showMacroListEditor = true },
                    onAddButton = { showAddButton = true },
                    onEditButton = { btn ->
                        val isDual = DisplayDetector.findSecondaryDisplay(context) != null
                        if (isDual) {
                            MacroPadState.setSelectedButtonId(btn.id)
                            AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.MACROPAD_INSPECTOR))
                        } else {
                            editingButton = btn
                            editingButtonActive = true
                        }
                    },
                    onCopyToProfile = { showCopyLayoutProfileDialog = true },
                    onCopyToLayout = { btn ->
                        editingButton = btn
                        showCopyButtonLayoutDialog = true
                    },
                    onDeleteRequested = { btn -> buttonPendingDelete = btn },
                    onReorderProfiles = { showReorderProfilesOverlay = true },
                    onReorderLayouts = { showReorderLayoutsOverlay = true },
                    isCanvasLocked = isCanvasLocked,
                    onToggleCanvasLock = { isCanvasLocked = !isCanvasLocked },
                    onManageBackground = {
                        val isDual = DisplayDetector.findSecondaryDisplay(context) != null
                        if (isDual) {
                            AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.BACKGROUND_SETTINGS))
                        } else {
                            showBackgroundSettingsDialog = true
                        }
                    },
                    onManageTouchpadSettings = {
                        val isDual = DisplayDetector.findSecondaryDisplay(context) != null
                        if (isDual) {
                            AppStateManager.openPrimaryModal(PrimaryModalConfig(PrimaryModalType.TOUCHPAD_SETTINGS))
                        } else {
                            showTouchpadSettingsDialog = true
                        }
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }

        MacroPadEditorHelpModal(
            visible = showEditorHelp,
            onDismiss = { showEditorHelp = false },
        )

        // Add button overlay
        AnimatedVisibility(
            visible = showAddButton && profile != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (profile != null) {
                ButtonEditDialog(
                    button = null,
                    accentColor = colors.accent,
                    onEditMacro = { macro ->
                        pendingMacroEditId = macro.id
                        showMacroListEditor = true
                    },
                    onConfirm = { newBtn ->
                        val layout = MacroPadState.activeLayout.value ?: return@ButtonEditDialog
                        MacroPadState.updateLayout(layout.copy(buttons = layout.buttons + newBtn))
                        showAddButton = false
                    },
                    onDismiss = { showAddButton = false },
                )
            }
        }

        // Edit existing button overlay
        AnimatedVisibility(
            visible = editingButtonActive && editingButton != null && profile != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (editingButton != null && profile != null) {
                ButtonEditDialog(
                    button = editingButton,
                    accentColor = colors.accent,
                    onEditMacro = { macro ->
                        pendingMacroEditId = macro.id
                        showMacroListEditor = true
                    },
                    onConfirm = { updated ->
                        val layout = MacroPadState.activeLayout.value ?: return@ButtonEditDialog
                        MacroPadState.updateLayout(
                            layout.copy(buttons = layout.buttons.map { if (it.id == updated.id) updated else it }),
                        )
                        editingButtonActive = false
                        editingButton = null
                    },
                    onDismiss = {
                        editingButtonActive = false
                        editingButton = null
                    },
                )
            }
        }

        // Delete button confirmation (in-tree — no Dialog window, works in Presentation)
        if (buttonPendingDelete != null && profile != null) {
            val pendingBtn = buttonPendingDelete!!
            InlineConfirmDeleteOverlay(
                title = stringResource(R.string.macropad_editor_delete_button),
                body =
                    if (pendingBtn.action is PadAction.TrackpointMove) {
                        stringResource(R.string.macropad_action_trackpoint)
                    } else {
                        pendingBtn.label
                    },
                onConfirm = {
                    val layout = MacroPadState.activeLayout.value
                    if (layout != null) {
                        MacroPadState.updateLayout(
                            layout.copy(buttons = layout.buttons.filter { it.id != pendingBtn.id }),
                        )
                    }
                    buttonPendingDelete = null
                },
                onDismiss = { buttonPendingDelete = null },
            )
        }

        // New layout (name input + visibility + background + button colors)
        if (showNewLayoutDialog && profile != null) {
            val newLayoutId = remember(showNewLayoutDialog) { UUID.randomUUID().toString() }
            LayoutSettingsEditor(
                title = stringResource(R.string.settings_macropad_new_layout),
                layoutId = newLayoutId,
                initialName = "",
                initialButtonTextColor = ColorOption.Neutral,
                initialButtonBorderColor = ColorOption.Neutral,
                initialButtonBgColor = ColorOption.Neutral,
                initialInvisibleButtons = false,
                accentColor = colors.accent,
                existingNames = profile.layouts.map { it.name },
                onConfirm = { name, textCol, borderCol, bgCol, invisibleBtns ->
                    val sourceW =
                        ScreenCaptureManager.captureSourceWidth.value
                            .toFloat()
                            .let { if (it > 0f) it else 1920f }
                    val sourceH =
                        ScreenCaptureManager.captureSourceHeight.value
                            .toFloat()
                            .let { if (it > 0f) it else 1080f }
                    val bottomW = ScreenCaptureManager.surfaceWidth.value
                    val bottomH = ScreenCaptureManager.surfaceHeight.value
                    val defaultCutout =
                        if (bottomW > 0f && bottomH > 0f) {
                            ScreenCutout.createDefault(sourceW, sourceH, bottomW, bottomH)
                        } else {
                            ScreenCutout.createDefault(sourceW, sourceH)
                        }
                    val newLayout =
                        PadLayout(
                            id = newLayoutId,
                            name = name,
                            enabled = true,
                            buttonTextColor = textCol,
                            buttonBorderColor = borderCol,
                            buttonBgColor = bgCol,
                            backgroundImagePath = null,
                            useBackgroundImageAsMask = false,
                            invisibleButtons = invisibleBtns,
                            backgroundImageVersion = 0,
                            bgImageScale = 1f,
                            bgImageOffsetX = 0f,
                            bgImageOffsetY = 0f,
                            mirrorCutouts = listOf(defaultCutout),
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
                title = stringResource(R.string.macropad_editor_delete_layout),
                body = pendingLayout.name,
                onConfirm = {
                    // Delete background file if it exists
                    pendingLayout.backgroundImagePath?.let { path ->
                        val file = File(context.filesDir, path)
                        if (file.exists()) {
                            try {
                                file.delete()
                            } catch (e: Exception) {
                                AppLog.e(TAG, "Failed to delete background file $path", e)
                            }
                        }
                    }
                    MacroPadState.deleteLayout(pendingLayout.id)
                    layoutPendingDelete = null
                },
                onDismiss = { layoutPendingDelete = null },
            )
        }

        // New profile (in-tree input overlay — no Dialog window)
        if (showNewProfileDialog) {
            InlineProfileSettingsOverlay(
                title = stringResource(R.string.settings_macropad_new_profile),
                initialName = "",
                initialPackage = null,
                accentColor = colors.accent,
                existingNames = profiles.map { it.name },
                onConfirm = { name, pkg ->
                    val assoc = pkg?.let { ProfileAssociation(packageName = it) }
                    val newProfile = PadProfile(id = UUID.randomUUID().toString(), name = name, association = assoc)
                    MacroPadState.addProfile(newProfile)
                    showNewProfileDialog = false
                },
                onDismiss = { showNewProfileDialog = false },
            )
        }

        // Rename profile (in-tree input overlay — no Dialog window)
        if (showRenameProfileDialog && profile != null) {
            InlineProfileSettingsOverlay(
                title = stringResource(R.string.profile_settings_title),
                initialName = profile.name,
                initialPackage = profile.association?.packageName,
                accentColor = colors.accent,
                existingNames = profiles.filter { it.id != profile.id }.map { it.name },
                onConfirm = { name, pkg ->
                    val assoc =
                        if (pkg != null) {
                            val existing = profile.association
                            if (existing != null && existing.packageName.equals(pkg, ignoreCase = true)) {
                                existing
                            } else {
                                ProfileAssociation(packageName = pkg)
                            }
                        } else {
                            null
                        }
                    MacroPadState.renameProfile(profile.id, name, assoc)
                    showRenameProfileDialog = false
                },
                onDismiss = { showRenameProfileDialog = false },
            )
        }

        // Delete profile confirmation (in-tree — no Dialog window)
        if (showDeleteProfileConfirm && profile != null) {
            val activeProfile = profile
            InlineConfirmDeleteOverlay(
                title = stringResource(R.string.macropad_editor_delete_profile),
                body = stringResource(R.string.macropad_editor_confirm_delete),
                onConfirm = {
                    // Delete background files for all layouts in this profile
                    activeProfile.layouts.forEach { layout ->
                        layout.backgroundImagePath?.let { path ->
                            val file = File(context.filesDir, path)
                            if (file.exists()) {
                                try {
                                    file.delete()
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to delete background file $path", e)
                                }
                            }
                        }
                    }
                    MacroPadState.deleteProfile(activeProfile.id)
                    showDeleteProfileConfirm = false
                },
                onDismiss = { showDeleteProfileConfirm = false },
            )
        }

        // Edit layout overlay
        if (showEditLayoutDialog && activeLayout != null) {
            val curLayout = activeLayout!!
            LayoutSettingsEditor(
                title = stringResource(R.string.macropad_editor_title),
                layoutId = curLayout.id,
                initialName = curLayout.name,
                initialButtonTextColor = curLayout.buttonTextColor,
                initialButtonBorderColor = curLayout.buttonBorderColor,
                initialButtonBgColor = curLayout.buttonBgColor,
                initialInvisibleButtons = curLayout.invisibleButtons,
                accentColor = colors.accent,
                existingNames = profile?.layouts?.filter { it.id != curLayout.id }?.map { it.name } ?: emptyList(),
                onConfirm = { name, textCol, borderCol, bgCol, invisibleBtns ->
                    MacroPadState.updateLayout(
                        curLayout.copy(
                            name = name,
                            enabled = true,
                            buttonTextColor = textCol,
                            buttonBorderColor = borderCol,
                            buttonBgColor = bgCol,
                            invisibleButtons = invisibleBtns,
                        ),
                    )
                    showEditLayoutDialog = false
                },
                onDismiss = { showEditLayoutDialog = false },
            )
        }

        // Background settings overlay
        if (showBackgroundSettingsDialog && activeLayout != null) {
            val curLayout = activeLayout!!
            BackgroundSettingsEditor(
                title = stringResource(R.string.layout_settings_bg_section_title),
                layoutId = curLayout.id,
                profileName = profile?.name ?: "",
                initialBackgroundImagePath = curLayout.backgroundImagePath,
                initialUseAsMask = curLayout.useBackgroundImageAsMask,
                initialBgImageScale = curLayout.bgImageScale,
                initialBgImageOffsetX = curLayout.bgImageOffsetX,
                initialBgImageOffsetY = curLayout.bgImageOffsetY,
                initialBackgroundImageDim = curLayout.backgroundImageDim,
                onConfirm = { bgImagePath, useAsMask, bgChanged, bgScale, bgOffsetX, bgOffsetY, bgImageDim ->
                    MacroPadState.updateLayout(
                        curLayout.copy(
                            backgroundImagePath = bgImagePath,
                            useBackgroundImageAsMask = useAsMask,
                            backgroundImageVersion = if (bgChanged) curLayout.backgroundImageVersion + 1 else curLayout.backgroundImageVersion,
                            bgImageScale = bgScale,
                            bgImageOffsetX = bgOffsetX,
                            bgImageOffsetY = bgOffsetY,
                            backgroundImageDim = bgImageDim,
                        ),
                    )
                    showBackgroundSettingsDialog = false
                },
                onDismiss = { showBackgroundSettingsDialog = false },
            )
        }

        // Touchpad settings overlay
        if (showTouchpadSettingsDialog && activeLayout != null) {
            val curLayout = activeLayout!!
            BackgroundTouchpadSettingsEditor(
                layout = curLayout,
                accentColor = colors.accent,
                onConfirm = { updatedConfig, disableProjection ->
                    val newCutouts =
                        if (disableProjection) {
                            curLayout.mirrorCutouts.map { it.copy(touchProjectionEnabled = false) }
                        } else {
                            curLayout.mirrorCutouts
                        }
                    MacroPadState.updateLayout(
                        curLayout.copy(
                            backgroundTouchpad = updatedConfig,
                            mirrorCutouts = newCutouts,
                        ),
                    )
                    showTouchpadSettingsDialog = false
                },
                onDismiss = { showTouchpadSettingsDialog = false },
            )
        }

        // Render ReorderProfilesOverlay
        AnimatedVisibility(
            visible = showReorderProfilesOverlay,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ReorderProfilesOverlay(
                profiles = profiles,
                onDone = { showReorderProfilesOverlay = false },
            )
        }

        // Render ReorderLayoutsOverlay
        AnimatedVisibility(
            visible = showReorderLayoutsOverlay && profile != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (profile != null) {
                ReorderLayoutsOverlay(
                    layouts = profile.layouts,
                    onDone = { showReorderLayoutsOverlay = false },
                )
            }
        }

        // Render MacroListEditor as a full-screen inline overlay (same window — no nested Dialog)
        AnimatedVisibility(
            visible = showMacroListEditor,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            MacroListEditor(
                onDone = {
                    showMacroListEditor = false
                    pendingMacroEditId = null
                },
                initialEditMacroId = pendingMacroEditId,
                onDirectEditSave = { savedMacro ->
                    showMacroListEditor = false
                    pendingMacroEditId = null
                    AppLog.d(TAG, "Direct edit macro saved: ${savedMacro.name}")
                },
                onDirectEditCancel = {
                    showMacroListEditor = false
                    val draftId = pendingMacroEditId
                    pendingMacroEditId = null
                    if (draftId != null) {
                        val macro =
                            MacroPadState.activeProfile.value
                                ?.macros
                                ?.firstOrNull { it.id == draftId }
                        if (macro != null && macro.steps.isEmpty()) {
                            AppLog.d(TAG, "Cleaning up empty macro draft on cancel: $draftId")
                            MacroPadState.deleteMacro(draftId)
                        }
                    }
                },
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
                onDismiss = { showCopyLayoutProfileDialog = false },
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
                onDismiss = { showCopyButtonLayoutDialog = false },
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
    onDone: () -> Unit,
    onHelpClick: () -> Unit,
) {
    val colors = LocalAppColors.current

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.macropad_editor_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = colors.onSurface,
                )
            }
        },
        actions = {
            HelpIconButton(onClick = onHelpClick)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
    )
}

@Composable
private fun MacroPadEditorHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_editor_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_editor_intro))

        HelpSection(stringResource(R.string.help_editor_section_profiles))
        HelpEntry(
            icon = null,
            label = stringResource(R.string.help_editor_profiles_label),
            description = stringResource(R.string.help_editor_profiles_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Add,
            label = stringResource(R.string.help_editor_add_profile_label),
            description = stringResource(R.string.help_editor_add_profile_desc),
        )
        HelpEntry(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            label = stringResource(R.string.help_editor_profile_macros_label),
            description = stringResource(R.string.help_editor_profile_macros_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.MoreVert,
            label = stringResource(R.string.help_editor_profile_options_label),
            description = stringResource(R.string.help_editor_profile_options_desc),
        )

        HelpSection(stringResource(R.string.help_editor_section_layouts))
        HelpEntry(
            icon = null,
            label = stringResource(R.string.help_editor_layouts_label),
            description = stringResource(R.string.help_editor_layouts_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Add,
            label = stringResource(R.string.help_editor_add_layout_label),
            description = stringResource(R.string.help_editor_add_layout_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.MoreVert,
            label = stringResource(R.string.help_editor_layout_options_label),
            description = stringResource(R.string.help_editor_layout_options_desc),
        )

        HelpSection(stringResource(R.string.help_editor_section_toolbar))
        HelpEntry(
            icon = Icons.Rounded.Add,
            label = stringResource(R.string.help_editor_toolbar_button_label),
            description = stringResource(R.string.help_editor_toolbar_button_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Wallpaper,
            label = stringResource(R.string.help_editor_toolbar_background_label),
            description = stringResource(R.string.help_editor_toolbar_background_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Mouse,
            label = stringResource(R.string.help_editor_toolbar_touchpad_label),
            description = stringResource(R.string.help_editor_toolbar_touchpad_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Grid4x4,
            label = stringResource(R.string.help_editor_toolbar_grid_label),
            description = stringResource(R.string.help_editor_toolbar_grid_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Lock,
            label = stringResource(R.string.help_editor_toolbar_lock_label),
            description = stringResource(R.string.help_editor_toolbar_lock_desc),
        )

        HelpSection(stringResource(R.string.help_editor_section_canvas))
        HelpEntry(
            label = stringResource(R.string.help_editor_canvas_drag_label),
            description = stringResource(R.string.help_editor_canvas_drag_desc),
        )

        HelpSection(stringResource(R.string.help_editor_section_buttons))
        HelpEntry(
            label = stringResource(R.string.help_editor_button_edit_label),
            description = stringResource(R.string.help_editor_button_edit_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.DragHandle,
            label = stringResource(R.string.help_editor_button_reorder_label),
            description = stringResource(R.string.help_editor_button_reorder_desc),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Body
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorBody(
    profiles: List<PadProfile>,
    profile: PadProfile,
    layout: PadLayout?,
    accentColor: Color,
    onSelectProfile: (String) -> Unit,
    onNewProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteProfile: () -> Unit,
    onSelectLayout: (String) -> Unit,
    onNewLayout: () -> Unit,
    onEditLayout: () -> Unit,
    onDeleteLayoutRequested: (PadLayout) -> Unit,
    onManageMacros: () -> Unit,
    onAddButton: () -> Unit,
    onEditButton: (PadButton) -> Unit,
    onCopyToProfile: () -> Unit,
    onCopyToLayout: (PadButton) -> Unit,
    onDeleteRequested: (PadButton) -> Unit,
    onReorderProfiles: () -> Unit,
    onReorderLayouts: () -> Unit,
    isCanvasLocked: Boolean,
    onToggleCanvasLock: () -> Unit,
    onManageBackground: () -> Unit,
    onManageTouchpadSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gridMode by remember { mutableStateOf(GridMode.OFF) }
    val profileRef by rememberUpdatedState(profile)
    val layoutRef by rememberUpdatedState(layout)

    val lazyListState = rememberLazyListState()
    // Items before buttons: section_profile(0), profiles(1), section_layout(2), layouts(3),
    // toolbar(4), canvas(5), section_buttons(6) → offset = 7
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val offset = 7
            val curLayout = layoutRef
            if (curLayout != null) {
                val newButtons = curLayout.buttons.toMutableList()
                val fromIdx = (from.index - offset).coerceIn(0, newButtons.lastIndex)
                val toIdx = (to.index - offset).coerceIn(0, newButtons.lastIndex)
                newButtons.add(toIdx, newButtons.removeAt(fromIdx))
                MacroPadState.updateLayout(curLayout.copy(buttons = newButtons))
            }
        }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
    ) {
        // 1. Profile section header
        item(key = "section_profile") {
            EditorSectionHeader(
                textRes = R.string.quick_menu_profile_label,
                actionIcon = Icons.Rounded.Add,
                actionContentDescription = stringResource(R.string.settings_macropad_new_profile),
                onActionClick = onNewProfile,
            )
        }

        // 2. Profile management bar
        item(key = "profiles") {
            EditorProfileChipsBar(
                profiles = profiles,
                activeProfile = profile,
                onSelectProfile = onSelectProfile,
                onEditProfile = onEditProfile,
                onDuplicateProfile = {
                    val originalProfile = profile
                    val originalLayouts = originalProfile?.layouts ?: emptyList()
                    val layoutMapping = originalProfile?.id?.let { MacroPadState.duplicateProfile(it) }
                    if (layoutMapping != null) {
                        for (origLayout in originalLayouts) {
                            val originalPath = origLayout.backgroundImagePath
                            val newLayoutId = layoutMapping[origLayout.id]
                            if (originalPath != null && newLayoutId != null) {
                                scope.launch {
                                    MacroPadMediaRepository.duplicateBackgroundImage(context, origLayout.id, newLayoutId)
                                }
                            }
                        }
                    }
                },
                onReorderProfiles = onReorderProfiles,
                onDeleteProfile = onDeleteProfile,
                modifier =
                    Modifier
                        .background(colors.surface)
                        .padding(horizontal = MPE_PADDING)
                        .padding(top = MPE_PADDING, bottom = 4.dp),
            )
        }

        // 2b. Profile Action Toolbar (Macros button row)
        item(key = "profile_toolbar") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = MPE_PADDING)
                        .padding(top = 4.dp, bottom = MPE_PADDING),
                horizontalArrangement = Arrangement.spacedBy(MPE_ITEM_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorActionChip(
                    label = stringResource(R.string.macropad_editor_manage_macros),
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    accentColor = accentColor,
                    onClick = onManageMacros,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 3. Layout section header
        item(key = "section_layout") {
            EditorSectionHeader(
                textRes = R.string.macropad_editor_section_layout,
                actionIcon = Icons.Rounded.Add,
                actionContentDescription = stringResource(R.string.settings_macropad_new_layout),
                onActionClick = onNewLayout,
            )
        }

        // 4. Layout management bar
        item(key = "layouts") {
            EditorLayoutChipsBar(
                layouts = profile.layouts,
                activeLayout = layout,
                onSelectLayout = onSelectLayout,
                onEditLayout = onEditLayout,
                onDuplicateLayout = {
                    val originalLayout = layout
                    val originalPath = originalLayout?.backgroundImagePath
                    val newLayoutId = originalLayout?.id?.let { MacroPadState.duplicateLayout(it) }
                    if (originalLayout != null && originalPath != null && newLayoutId != null) {
                        scope.launch {
                            MacroPadMediaRepository.duplicateBackgroundImage(context, originalLayout.id, newLayoutId)
                        }
                    }
                },
                onCopyToProfile = onCopyToProfile,
                onReorderLayouts = onReorderLayouts,
                onDeleteLayout = { layout?.let { onDeleteLayoutRequested(it) } },
                modifier =
                    Modifier
                        .background(colors.surface)
                        .padding(horizontal = MPE_PADDING)
                        .padding(top = MPE_PADDING, bottom = 4.dp),
            )
        }

        // 3. Action toolbar (Add Button / Grid toggle)
        item(key = "toolbar") {
            EditorToolbar(
                profile = profile,
                accentColor = accentColor,
                gridMode = gridMode,
                isCanvasLocked = isCanvasLocked,
                onToggleCanvasLock = onToggleCanvasLock,
                onAddButton = onAddButton,
                onGridModeChange = {
                    gridMode =
                        when (gridMode) {
                            GridMode.OFF -> GridMode.RECTANGULAR
                            GridMode.RECTANGULAR -> GridMode.RADIAL
                            GridMode.RADIAL -> GridMode.OFF
                        }
                },
                onManageBackground = onManageBackground,
                onManageTouchpadSettings = onManageTouchpadSettings,
                modifier =
                    Modifier
                        .background(colors.surface)
                        .padding(horizontal = MPE_PADDING)
                        .padding(top = 4.dp, bottom = MPE_PADDING),
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
                onActionClick = onAddButton,
            )
        }

        // 6. Button list — tap to edit, drag handle to reorder
        if (layout?.buttons.isNullOrEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.macropad_editor_add_button),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier =
                        Modifier
                            .padding(horizontal = MPE_PADDING)
                            .padding(vertical = 8.dp),
                )
            }
        } else {
            itemsIndexed(layout?.buttons ?: emptyList(), key = { _, btn -> btn.id }) { _, btn ->
                ReorderableItem(reorderState, key = btn.id) { isDragging ->
                    ButtonListItem(
                        btn = btn,
                        accentColor = accentColor,
                        enableKeyboard = profile.enableKeyboard,
                        enableGamepad = profile.enableGamepad,
                        enableMouse = profile.enableMouse,
                        enableTouch = profile.enableTouch,
                        isDragging = isDragging,
                        onEdit = { onEditButton(btn) },
                        onDuplicate = { MacroPadState.duplicateButtonInLayout(btn, layout.id) },
                        onCopyToLayout = { onCopyToLayout(btn) },
                        onDelete = { onDeleteRequested(btn) },
                        dragHandleModifier = Modifier.draggableHandle(),
                    )
                    AppDivider(modifier = Modifier.padding(horizontal = MPE_PADDING))
                }
            }
        }
    }
}
