package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadEmptyState
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.primaryOverlayFocusable
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.util.UUID

private const val TAG = "MacroPadEditor"

internal val MPE_TOP_BAR_HEIGHT = 56.dp
internal val MPE_PADDING = 16.dp
internal val MPE_ITEM_PADDING = 12.dp
internal val MPE_GRID_TOGGLE_SIZE = 36.dp
internal val MPE_SECTION_HEADER_V_PADDING = 10.dp

internal enum class EditorSection {
    OVERVIEW,
    PROFILES,
    LAYOUTS,
    CANVAS,
    BUTTONS,
    MACROS,
}

@Composable
fun MacroPadEditor(
    onDone: () -> Unit,
    showTopBar: Boolean = true,
    showHelp: Boolean = false,
    onDismissHelp: () -> Unit = {},
) {
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
    var internalShowEditorHelp by remember { mutableStateOf(false) }
    val effectiveShowHelp = showHelp || internalShowEditorHelp

    // Intercept system Back when an overlay is visible
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

    val editorContent: @Composable (PaddingValues) -> Unit = { innerPadding ->
        if (profile == null) {
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
            EditorTwoPaneBody(
                profiles = profiles,
                profile = profile,
                layout = activeLayout,
                accentColor = colors.accent,
                onSelectProfile = {
                    MacroPadState.setActiveProfileId(it)
                    AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                },
                onNewProfile = { showNewProfileDialog = true },
                onEditProfile = { showRenameProfileDialog = true },
                onDeleteProfile = { showDeleteProfileConfirm = true },
                onSelectLayout = {
                    MacroPadState.setActiveLayoutId(it)
                    AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                },
                onNewLayout = { showNewLayoutDialog = true },
                onEditLayout = { showEditLayoutDialog = true },
                onDeleteLayoutRequested = { lay -> layoutPendingDelete = lay },
                onManageMacros = { showMacroListEditor = true },
                onAddButton = { showAddButton = true },
                onEditButton = { btn ->
                    editingButton = btn
                    editingButtonActive = true
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
                onManageBackground = { showBackgroundSettingsDialog = true },
                onManageTouchpadSettings = { showTouchpadSettingsDialog = true },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showTopBar) {
            Scaffold(
                containerColor = colors.appBackground,
                topBar = {
                    EditorTopBar(
                        onDone = onDone,
                        onHelpClick = { internalShowEditorHelp = true },
                    )
                },
            ) { innerPadding ->
                editorContent(innerPadding)
            }
        } else {
            editorContent(PaddingValues(0.dp))
        }

        MacroPadEditorHelpModal(
            visible = effectiveShowHelp,
            onDismiss = {
                internalShowEditorHelp = false
                onDismissHelp()
            },
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

        // Delete button confirmation
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

        // New layout
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

        // New profile
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

        // Rename profile
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

        // Delete profile confirmation
        if (showDeleteProfileConfirm && profile != null) {
            val activeProfile = profile
            InlineConfirmDeleteOverlay(
                title = stringResource(R.string.macropad_editor_delete_profile),
                body = stringResource(R.string.macropad_editor_confirm_delete),
                onConfirm = {
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

        // ReorderProfilesOverlay
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

        // ReorderLayoutsOverlay
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

        // MacroListEditor
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

        // Copy layout selection
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

        // Copy button selection
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
    }
}

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
private fun EditorTwoPaneBody(
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
    var selectedSection by remember { mutableStateOf(EditorSection.OVERVIEW) }
    var gridMode by remember { mutableStateOf(GridMode.OFF) }

    val lazyListState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val curLayout = layout
            if (curLayout != null) {
                val newButtons = curLayout.buttons.toMutableList()
                val fromIdx = from.index.coerceIn(0, newButtons.lastIndex)
                val toIdx = to.index.coerceIn(0, newButtons.lastIndex)
                newButtons.add(toIdx, newButtons.removeAt(fromIdx))
                MacroPadState.updateLayout(curLayout.copy(buttons = newButtons))
            }
        }

    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
            val currentIndex = EditorSection.entries.indexOf(selectedSection)
            val nextIndex =
                when (direction) {
                    BumperDirection.PREV -> (currentIndex - 1 + EditorSection.entries.size) % EditorSection.entries.size
                    BumperDirection.NEXT -> (currentIndex + 1) % EditorSection.entries.size
                }
            selectedSection = EditorSection.entries[nextIndex]
        }
    }

    GamepadTwoPaneScaffold(
        sidebarContent = {
            GamepadCategoryTile(
                title = stringResource(R.string.settings_jump_all),
                icon = Icons.Rounded.Dashboard,
                selected = selectedSection == EditorSection.OVERVIEW,
                onClick = { selectedSection = EditorSection.OVERVIEW },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.quick_menu_profile_label),
                icon = Icons.Rounded.Folder,
                selected = selectedSection == EditorSection.PROFILES,
                onClick = { selectedSection = EditorSection.PROFILES },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.macropad_editor_section_layout),
                icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                selected = selectedSection == EditorSection.LAYOUTS,
                onClick = { selectedSection = EditorSection.LAYOUTS },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.macropad_editor_section_buttons),
                icon = Icons.Rounded.SmartButton,
                selected = selectedSection == EditorSection.BUTTONS,
                onClick = { selectedSection = EditorSection.BUTTONS },
            )
            GamepadCategoryTile(
                title = stringResource(R.string.macropad_editor_manage_macros),
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                selected = selectedSection == EditorSection.MACROS,
                onClick = { selectedSection = EditorSection.MACROS },
            )
        },
        content = {
            // PROFILES & LAYOUTS CHOOSER (Available in OVERVIEW)
            if (selectedSection == EditorSection.OVERVIEW) {
                GamepadSectionHeader(
                    text = "ACTIVE CONFIGURATION",
                    color = accentColor,
                )

                val profileIdx = profiles.indexOf(profile).coerceAtLeast(0)
                GamepadChoiceCard(
                    title = stringResource(R.string.quick_menu_profile_label),
                    description = "Active MacroPad profile",
                    selectedText = profile.name,
                    icon = Icons.Rounded.Folder,
                    onPrevious = {
                        val next = profiles[(profileIdx - 1 + profiles.size) % profiles.size]
                        onSelectProfile(next.id)
                    },
                    onNext = {
                        val next = profiles[(profileIdx + 1) % profiles.size]
                        onSelectProfile(next.id)
                    },
                    modifier = Modifier.firstDeckItem(isFirst = selectedSection == EditorSection.OVERVIEW),
                )

                val layouts = profile.layouts
                val layoutIdx = layouts.indexOf(layout).coerceAtLeast(0)
                GamepadChoiceCard(
                    title = stringResource(R.string.macropad_editor_section_layout),
                    description = "Active button layout",
                    selectedText = layout?.name ?: "None",
                    icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                    enabled = layouts.isNotEmpty(),
                    onPrevious = {
                        if (layouts.isNotEmpty()) {
                            val next = layouts[(layoutIdx - 1 + layouts.size) % layouts.size]
                            onSelectLayout(next.id)
                        }
                    },
                    onNext = {
                        if (layouts.isNotEmpty()) {
                            val next = layouts[(layoutIdx + 1) % layouts.size]
                            onSelectLayout(next.id)
                        }
                    },
                )

                GamepadSectionHeader(
                    text = "CANVAS PREVIEW",
                    color = accentColor,
                )

                GamepadToggleCard(
                    title = "Lock Canvas Drag",
                    description = if (isCanvasLocked) "Canvas is locked (buttons cannot be moved)" else "Drag buttons freely on the canvas below",
                    checked = isCanvasLocked,
                    icon = if (isCanvasLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    onCheckedChange = { onToggleCanvasLock() },
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PadCanvas(
                        profile = profile,
                        layout = layout,
                        accentColor = accentColor,
                        gridMode = gridMode,
                        isLocked = isCanvasLocked,
                    )
                }

                Text(
                    text = "QUICK ACTIONS",
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )

                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_add_button),
                    description = "Create and position a new interactive button",
                    actionText = "Add",
                    icon = Icons.Rounded.Add,
                    onClick = onAddButton,
                )

                GamepadActionCard(
                    title = stringResource(R.string.layout_settings_bg_section_title),
                    description = "Background image, transparency mask, scaling, and dimming",
                    actionText = "Background",
                    icon = Icons.Rounded.Wallpaper,
                    onClick = onManageBackground,
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_touchpad_title),
                    description = "Background virtual touchpad & touch projection settings",
                    actionText = "Touchpad",
                    icon = Icons.Rounded.Mouse,
                    onClick = onManageTouchpadSettings,
                )
            }

            // PROFILES DECK
            if (selectedSection == EditorSection.PROFILES) {
                Text(
                    text = stringResource(R.string.quick_menu_profile_label).uppercase(),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                val profileIdx = profiles.indexOf(profile).coerceAtLeast(0)
                GamepadChoiceCard(
                    title = stringResource(R.string.quick_menu_profile_label),
                    description = "Current active profile",
                    selectedText = profile.name,
                    icon = Icons.Rounded.Folder,
                    onPrevious = {
                        val next = profiles[(profileIdx - 1 + profiles.size) % profiles.size]
                        onSelectProfile(next.id)
                    },
                    onNext = {
                        val next = profiles[(profileIdx + 1) % profiles.size]
                        onSelectProfile(next.id)
                    },
                    modifier = Modifier.firstDeckItem(isFirst = selectedSection == EditorSection.PROFILES),
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_macropad_new_profile),
                    description = "Create a new profile with custom layouts and macros",
                    actionText = "New",
                    icon = Icons.Rounded.Add,
                    onClick = onNewProfile,
                )

                GamepadActionCard(
                    title = stringResource(R.string.profile_settings_title),
                    description = "Rename profile or edit app package association",
                    actionText = "Edit",
                    icon = Icons.Rounded.Edit,
                    onClick = onEditProfile,
                )

                GamepadActionCard(
                    title = "Duplicate Profile",
                    description = "Create a full copy of '${profile.name}' with all layouts and assets",
                    actionText = "Duplicate",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        val originalProfile = profile
                        val originalLayouts = originalProfile.layouts
                        val layoutMapping = MacroPadState.duplicateProfile(originalProfile.id)
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
                )

                GamepadActionCard(
                    title = "Reorder Profiles",
                    description = "Change the display order of profiles in the switcher",
                    actionText = "Reorder",
                    icon = Icons.Rounded.SwapVert,
                    onClick = onReorderProfiles,
                )

                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_delete_profile),
                    description = "Permanently delete '${profile.name}' and all its layouts",
                    actionText = "Delete",
                    isDestructive = true,
                    icon = Icons.Rounded.Delete,
                    onClick = onDeleteProfile,
                )
            }

            // LAYOUTS DECK
            if (selectedSection == EditorSection.LAYOUTS) {
                Text(
                    text = stringResource(R.string.macropad_editor_section_layout).uppercase(),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                val layouts = profile.layouts
                val layoutIdx = layouts.indexOf(layout).coerceAtLeast(0)
                GamepadChoiceCard(
                    title = stringResource(R.string.macropad_editor_section_layout),
                    description = "Active layout in '${profile.name}'",
                    selectedText = layout?.name ?: "None",
                    icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                    enabled = layouts.isNotEmpty(),
                    onPrevious = {
                        if (layouts.isNotEmpty()) {
                            val next = layouts[(layoutIdx - 1 + layouts.size) % layouts.size]
                            onSelectLayout(next.id)
                        }
                    },
                    onNext = {
                        if (layouts.isNotEmpty()) {
                            val next = layouts[(layoutIdx + 1) % layouts.size]
                            onSelectLayout(next.id)
                        }
                    },
                    modifier = Modifier.firstDeckItem(isFirst = selectedSection == EditorSection.LAYOUTS),
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_macropad_new_layout),
                    description = "Add a new button layout to this profile",
                    actionText = "New",
                    icon = Icons.Rounded.Add,
                    onClick = onNewLayout,
                )

                GamepadActionCard(
                    title = "Layout Appearance & Colors",
                    description = "Customize button text, border, and background color styling",
                    actionText = "Appearance",
                    icon = Icons.Rounded.Palette,
                    onClick = onEditLayout,
                )

                GamepadActionCard(
                    title = stringResource(R.string.layout_settings_bg_section_title),
                    description = "Set background artwork, scaling, dimming, and mask effects",
                    actionText = "Background",
                    icon = Icons.Rounded.Wallpaper,
                    onClick = onManageBackground,
                )

                GamepadActionCard(
                    title = stringResource(R.string.settings_touchpad_title),
                    description = "Configure background touchpad and touch projection cutouts",
                    actionText = "Touchpad",
                    icon = Icons.Rounded.Mouse,
                    onClick = onManageTouchpadSettings,
                )

                GamepadActionCard(
                    title = "Duplicate Layout",
                    description = "Duplicate active layout inside this profile",
                    actionText = "Duplicate",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        val originalLayout = layout
                        val originalPath = originalLayout?.backgroundImagePath
                        val newLayoutId = originalLayout?.id?.let { MacroPadState.duplicateLayout(it) }
                        if (originalLayout != null && originalPath != null && newLayoutId != null) {
                            scope.launch {
                                MacroPadMediaRepository.duplicateBackgroundImage(context, originalLayout.id, newLayoutId)
                            }
                        }
                    },
                )

                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_copy_profile_select),
                    description = "Copy this layout to another profile",
                    actionText = "Copy",
                    icon = Icons.Rounded.Share,
                    onClick = onCopyToProfile,
                )

                GamepadActionCard(
                    title = "Reorder Layouts",
                    description = "Change the order of layouts in this profile",
                    actionText = "Reorder",
                    icon = Icons.Rounded.SwapVert,
                    onClick = onReorderLayouts,
                )

                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_delete_layout),
                    description = "Delete '${layout?.name}' from this profile",
                    actionText = "Delete",
                    isDestructive = true,
                    icon = Icons.Rounded.Delete,
                    onClick = { layout?.let { onDeleteLayoutRequested(it) } },
                )
            }

            // CANVAS DECK
            if (selectedSection == EditorSection.CANVAS) {
                GamepadSectionHeader(
                    text = "CANVAS CONTROLS",
                    color = accentColor,
                )

                GamepadToggleCard(
                    title = "Lock Canvas Dragging",
                    description = if (isCanvasLocked) "Canvas is locked (buttons cannot be accidentally moved)" else "Drag buttons freely on the canvas to reposition",
                    checked = isCanvasLocked,
                    icon = if (isCanvasLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    onCheckedChange = { onToggleCanvasLock() },
                    modifier = Modifier.firstDeckItem(isFirst = selectedSection == EditorSection.CANVAS),
                )

                val gridModes = listOf(GridMode.OFF, GridMode.RECTANGULAR, GridMode.RADIAL)
                val gridIdx = gridModes.indexOf(gridMode)
                GamepadChoiceCard(
                    title = "Snap Grid Mode",
                    description = "Display alignment grid on canvas",
                    selectedText =
                        when (gridMode) {
                            GridMode.OFF -> "Off"
                            GridMode.RECTANGULAR -> "Rectangular"
                            GridMode.RADIAL -> "Radial"
                        },
                    icon = Icons.Rounded.Grid4x4,
                    onPrevious = { gridMode = gridModes[(gridIdx - 1 + gridModes.size) % gridModes.size] },
                    onNext = { gridMode = gridModes[(gridIdx + 1) % gridModes.size] },
                )

                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_add_button),
                    description = "Create a new button and place it on this canvas",
                    actionText = "Add",
                    icon = Icons.Rounded.Add,
                    onClick = onAddButton,
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PadCanvas(
                        profile = profile,
                        layout = layout,
                        accentColor = accentColor,
                        gridMode = gridMode,
                        isLocked = isCanvasLocked,
                    )
                }
            }

            // BUTTONS DECK
            if (selectedSection == EditorSection.BUTTONS || selectedSection == EditorSection.OVERVIEW) {
                GamepadSectionHeader(
                    text = stringResource(R.string.macropad_editor_section_buttons),
                    color = accentColor,
                )

                GamepadActionCard(
                    title = stringResource(R.string.macropad_editor_add_button),
                    description = "Create and configure a new interactive button",
                    actionText = "Add",
                    icon = Icons.Rounded.Add,
                    onClick = onAddButton,
                    modifier = Modifier.firstDeckItem(isFirst = selectedSection == EditorSection.BUTTONS),
                )

                val buttons = layout?.buttons ?: emptyList()
                if (buttons.isEmpty()) {
                    Text(
                        text = "No buttons in this layout yet.",
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height((buttons.size * 72).coerceAtMost(480).dp),
                    ) {
                        itemsIndexed(buttons, key = { _, btn -> btn.id }) { _, btn ->
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
                                    onDuplicate = { MacroPadState.duplicateButtonInLayout(btn, layout?.id ?: "") },
                                    onCopyToLayout = { onCopyToLayout(btn) },
                                    onDelete = { onDeleteRequested(btn) },
                                    dragHandleModifier = Modifier.draggableHandle(),
                                )
                                AppDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }

            // MACROS DECK
            if (selectedSection == EditorSection.MACROS) {
                Text(
                    text = stringResource(R.string.macropad_editor_manage_macros).uppercase(),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                GamepadActionCard(
                    title = "Open Macro Timeline Editor",
                    description = "Create, record, and edit timed input macro sequences",
                    actionText = "Open",
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    onClick = onManageMacros,
                    modifier = Modifier.firstDeckItem(isFirst = selectedSection == EditorSection.MACROS),
                )

                val macros = profile.macros
                if (macros.isEmpty()) {
                    GamepadEmptyState(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        title = "No macros created yet",
                        subtitle = "Create and edit input macro sequences",
                        actionText = "Create Macro",
                        onAction = onManageMacros,
                    )
                } else {
                    macros.forEach { macro ->
                        GamepadActionCard(
                            title = macro.name,
                            description = "${macro.steps.size} step${if (macro.steps.size != 1) "s" else ""}",
                            actionText = "Edit",
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            onClick = onManageMacros,
                        )
                    }
                }
            }
        },
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

        HelpSection(stringResource(R.string.help_editor_section_layouts))
        HelpEntry(
            label = stringResource(R.string.help_editor_layouts_label),
            description = stringResource(R.string.help_editor_layouts_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Add,
            label = stringResource(R.string.help_editor_add_layout_label),
            description = stringResource(R.string.help_editor_add_layout_desc),
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
