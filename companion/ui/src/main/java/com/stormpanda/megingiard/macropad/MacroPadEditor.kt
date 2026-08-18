package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.steamgriddb.SteamGridDbScrapeSubPageContent
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadEmptyState
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberGamepadBringIntoViewSpec
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

private const val TAG = "MacroPadEditor"

internal val MPE_TOP_BAR_HEIGHT = 56.dp
internal val MPE_PADDING = 16.dp
internal val MPE_ITEM_PADDING = 12.dp
internal val MPE_GRID_TOGGLE_SIZE = 36.dp
internal val MPE_SECTION_HEADER_V_PADDING = 10.dp

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

    var selectedSection by remember { mutableStateOf(EditorSection.PROFILES) }
    var subPageStack by remember { mutableStateOf<List<MacroPadSubPage>>(emptyList()) }
    var isCanvasLocked by remember { mutableStateOf(true) }
    var internalShowEditorHelp by remember { mutableStateOf(false) }
    val effectiveShowHelp = showHelp || internalShowEditorHelp

    // Temporary storage for intermediate wizard picks (e.g. app picker for new/edit profile, icon picker)
    var pendingProfilePackage by remember { mutableStateOf<String?>(null) }
    var macroTimelineFocusStepIndex by remember { mutableStateOf<Int?>(null) }
    var appearanceDraft by remember { mutableStateOf<PadLayout?>(null) }

    LaunchedEffect(subPageStack) {
        if (subPageStack.none {
                it is MacroPadSubPage.LayoutAppearance || it is MacroPadSubPage.LayoutColor ||
                    (it is MacroPadSubPage.ColorWheel && it.section == EditorSection.LAYOUTS)
            }
        ) {
            appearanceDraft = null
        }
    }

    BackHandler(enabled = true) {
        if (subPageStack.isNotEmpty()) {
            subPageStack = subPageStack.dropLast(1)
        } else {
            onDone()
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
            subPageStack = emptyList()
            selectedSection = EditorSection.entries[nextIndex]
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.appBackground),
    ) {
        if (showTopBar && subPageStack.isEmpty()) {
            FullScreenTopBar(
                title = stringResource(R.string.macropad_editor_title),
                onDismiss = onDone,
            ) {
                HelpIconButton(onClick = { internalShowEditorHelp = true })
            }
            AppDivider()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (profile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                GamepadTwoPaneScaffold(
                    modifier = Modifier.fillMaxSize(),
                    scrollableDeck = false,
                    isCustomBackActive = subPageStack.isNotEmpty(),
                    onCustomBack = {
                        subPageStack = subPageStack.dropLast(1)
                    },
                    navigationKey = subPageStack,
                    sidebarContent = {
                        GamepadCategoryTile(
                            title = stringResource(R.string.quick_menu_profile_label),
                            icon = Icons.Rounded.Folder,
                            selected = selectedSection == EditorSection.PROFILES,
                            onClick = {
                                subPageStack = emptyList()
                                selectedSection = EditorSection.PROFILES
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.macropad_editor_section_layout),
                            icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                            selected = selectedSection == EditorSection.LAYOUTS,
                            onClick = {
                                subPageStack = emptyList()
                                selectedSection = EditorSection.LAYOUTS
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.macropad_editor_section_canvas),
                            icon = Icons.Rounded.Preview,
                            selected = selectedSection == EditorSection.CANVAS,
                            onClick = {
                                subPageStack = emptyList()
                                selectedSection = EditorSection.CANVAS
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.macropad_editor_section_buttons),
                            icon = Icons.Rounded.SmartButton,
                            selected = selectedSection == EditorSection.BUTTONS,
                            onClick = {
                                subPageStack = emptyList()
                                selectedSection = EditorSection.BUTTONS
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.macropad_editor_manage_macros),
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            selected = selectedSection == EditorSection.MACROS,
                            onClick = {
                                subPageStack = emptyList()
                                selectedSection = EditorSection.MACROS
                            },
                        )
                    },
                    content = {
                        AnimatedContent(
                            targetState = subPageStack,
                            transitionSpec = {
                                val isBackTransition = targetState.size < initialState.size
                                if (isBackTransition) {
                                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> width } + fadeOut()
                                } else {
                                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width } + fadeOut()
                                }
                            },
                            label = "MacroPadSubPageAnimation",
                            modifier = Modifier.fillMaxSize(),
                        ) { stack ->
                            val currentSubPage = stack.lastOrNull()
                            if (currentSubPage == null) {
                                val sectionTitle =
                                    when (selectedSection) {
                                        EditorSection.PROFILES -> stringResource(R.string.quick_menu_profile_label)
                                        EditorSection.LAYOUTS -> stringResource(R.string.macropad_editor_section_layout)
                                        EditorSection.CANVAS -> stringResource(R.string.macropad_editor_section_canvas)
                                        EditorSection.BUTTONS -> stringResource(R.string.macropad_editor_section_buttons)
                                        EditorSection.MACROS -> stringResource(R.string.macropad_editor_manage_macros)
                                    }
                                GamepadDeck(title = sectionTitle) {
                                    // ── Main Section Decks ─────────────────────────────
                                    when (selectedSection) {
                                        EditorSection.PROFILES -> {
                                            ProfilesDeck(
                                                profiles = profiles,
                                                activeProfile = profile,
                                                accentColor = colors.accent,
                                                onSelectProfile = {
                                                    MacroPadState.setActiveProfileId(it)
                                                    AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                                                },
                                                onNewProfile = {
                                                    pendingProfilePackage = null
                                                    subPageStack = subPageStack + MacroPadSubPage.NewProfile
                                                },
                                                onEditProfile = {
                                                    pendingProfilePackage = profile.association?.packageName
                                                    subPageStack = subPageStack + MacroPadSubPage.EditProfile(profile.id)
                                                },
                                                onDuplicateProfile = {
                                                    val originalProfile = profile
                                                    val originalLayouts = originalProfile.layouts
                                                    val layoutMapping = MacroPadState.duplicateProfile(originalProfile.id)
                                                    if (layoutMapping != null) {
                                                        for (origLayout in originalLayouts) {
                                                            val originalPath = origLayout.backgroundImagePath
                                                            val newLayoutId = layoutMapping[origLayout.id]
                                                            if (originalPath != null && newLayoutId != null) {
                                                                scope.launch {
                                                                    MacroPadMediaRepository.duplicateBackgroundImage(
                                                                        context,
                                                                        origLayout.id,
                                                                        newLayoutId,
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onReorderProfiles = {
                                                    subPageStack = subPageStack + MacroPadSubPage.ReorderProfiles
                                                },
                                                onDeleteProfile = {
                                                    val deletedName = profile.name
                                                    val layoutsToDelete = profile.layouts
                                                    scope.launch {
                                                        layoutsToDelete.forEach { lay ->
                                                            MacroPadMediaRepository.deleteBackgroundImage(context, lay.id)
                                                        }
                                                    }
                                                    MacroPadState.deleteProfile(profile.id)
                                                    DialogToastManager.show(
                                                        context.getString(R.string.macropad_profile_deleted_toast, deletedName),
                                                    )
                                                },
                                            )
                                        }

                                        EditorSection.LAYOUTS -> {
                                            LayoutsDeck(
                                                profile = profile,
                                                activeLayout = activeLayout,
                                                accentColor = colors.accent,
                                                onSelectLayout = {
                                                    MacroPadState.setActiveLayoutId(it)
                                                    AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                                                },
                                                onNewLayout = {
                                                    subPageStack = subPageStack + MacroPadSubPage.NewLayout
                                                },
                                                onEditAppearance = {
                                                    if (activeLayout != null) {
                                                        subPageStack = subPageStack + MacroPadSubPage.LayoutAppearance(activeLayout.id)
                                                    }
                                                },
                                                onEditBackground = {
                                                    if (activeLayout != null) {
                                                        subPageStack = subPageStack + MacroPadSubPage.LayoutBackground(activeLayout.id)
                                                    }
                                                },
                                                onEditTouchpad = {
                                                    if (activeLayout != null) {
                                                        subPageStack = subPageStack + MacroPadSubPage.LayoutTouchpad(activeLayout.id)
                                                    }
                                                },
                                                onDuplicateLayout = {
                                                    val originalLayout = activeLayout
                                                    val originalPath = originalLayout?.backgroundImagePath
                                                    val newLayoutId = originalLayout?.id?.let { MacroPadState.duplicateLayout(it) }
                                                    if (originalLayout != null && originalPath != null && newLayoutId != null) {
                                                        scope.launch {
                                                            MacroPadMediaRepository.duplicateBackgroundImage(
                                                                context,
                                                                originalLayout.id,
                                                                newLayoutId,
                                                            )
                                                        }
                                                    }
                                                },
                                                onCopyLayout = {
                                                    if (activeLayout != null) {
                                                        subPageStack = subPageStack + MacroPadSubPage.CopyLayout(activeLayout.id)
                                                    }
                                                },
                                                onReorderLayouts = {
                                                    subPageStack = subPageStack + MacroPadSubPage.ReorderLayouts
                                                },
                                                onDeleteLayout = {
                                                    if (activeLayout != null) {
                                                        val deletedName = activeLayout.name
                                                        scope.launch {
                                                            MacroPadMediaRepository.deleteBackgroundImage(context, activeLayout.id)
                                                        }
                                                        MacroPadState.deleteLayout(activeLayout.id)
                                                        DialogToastManager.show(
                                                            context.getString(R.string.macropad_layout_deleted_toast, deletedName),
                                                        )
                                                    }
                                                },
                                            )
                                        }

                                        EditorSection.CANVAS -> {
                                            CanvasDeck(
                                                profile = profile,
                                                layout = activeLayout,
                                                accentColor = colors.accent,
                                                isLocked = isCanvasLocked,
                                                onToggleLock = { isCanvasLocked = !isCanvasLocked },
                                                onAddButton = {
                                                    subPageStack = subPageStack + MacroPadSubPage.EditButton(null)
                                                },
                                            )
                                        }

                                        EditorSection.BUTTONS -> {
                                            ButtonsDeck(
                                                profile = profile,
                                                layout = activeLayout,
                                                accentColor = colors.accent,
                                                onAddButton = {
                                                    subPageStack = subPageStack + MacroPadSubPage.EditButton(null)
                                                },
                                                onEditButton = { btn ->
                                                    subPageStack = subPageStack + MacroPadSubPage.EditButton(btn)
                                                },
                                                onDuplicateButton = { btn ->
                                                    activeLayout?.id?.let { MacroPadState.duplicateButtonInLayout(btn, it) }
                                                },
                                                onCopyButton = { btn ->
                                                    subPageStack = subPageStack + MacroPadSubPage.CopyButton(btn)
                                                },
                                                onDeleteButton = { btn ->
                                                    activeLayout?.let { lay ->
                                                        MacroPadState.updateLayout(
                                                            lay.copy(buttons = lay.buttons.filter { it.id != btn.id }),
                                                        )
                                                    }
                                                    DialogToastManager.show(
                                                        context.getString(R.string.macropad_button_deleted_toast),
                                                    )
                                                },
                                            )
                                        }

                                        EditorSection.MACROS -> {
                                            MacrosDeck(
                                                profile = profile,
                                                accentColor = colors.accent,
                                                onNewMacro = {
                                                    val newMacro =
                                                        Macro(
                                                            id = UUID.randomUUID().toString(),
                                                            name = context.getString(R.string.macropad_macro_default_name),
                                                            steps = emptyList(),
                                                        )
                                                    MacroPadState.addMacro(newMacro)
                                                    subPageStack = subPageStack + MacroPadSubPage.MacroTimeline(newMacro.id)
                                                },
                                                onEditMacro = { macro ->
                                                    subPageStack = subPageStack + MacroPadSubPage.MacroTimeline(macro.id)
                                                },
                                                onDeleteMacro = { macro ->
                                                    val deletedName = macro.name
                                                    MacroPadState.deleteMacro(macro.id)
                                                    DialogToastManager.show(
                                                        context.getString(R.string.macropad_macro_deleted_toast, deletedName),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                // ── In-Deck Sub-Pages ──────────────────────────────
                                when (currentSubPage) {
                                    is MacroPadSubPage.NewProfile -> {
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.quick_menu_profile_label),
                                                    stringResource(R.string.settings_macropad_new_profile),
                                                ),
                                        ) {
                                            NewProfileSubPageContent(
                                                existingNames = profiles.map { it.name },
                                                selectedPackage = pendingProfilePackage,
                                                accentColor = colors.accent,
                                                onOpenAppPicker = {
                                                    subPageStack = subPageStack + MacroPadSubPage.AppPicker(AppPickerTarget.NewProfile)
                                                },
                                                onClearApp = { pendingProfilePackage = null },
                                                onCreate = { name, pkg ->
                                                    val assoc = pkg?.let { ProfileAssociation(packageName = it) }
                                                    val newProf =
                                                        PadProfile(
                                                            id = UUID.randomUUID().toString(),
                                                            name = name,
                                                            association = assoc,
                                                        )
                                                    MacroPadState.addProfile(newProf)
                                                    subPageStack = subPageStack.dropLast(1)
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.EditProfile -> {
                                        val prof = profiles.firstOrNull { it.id == currentSubPage.profileId } ?: profile
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.quick_menu_profile_label),
                                                    stringResource(R.string.profile_settings_title),
                                                ),
                                        ) {
                                            EditProfileSubPageContent(
                                                profile = prof,
                                                existingNames = profiles.filter { it.id != prof.id }.map { it.name },
                                                selectedPackage = pendingProfilePackage ?: prof.association?.packageName,
                                                accentColor = colors.accent,
                                                onOpenAppPicker = {
                                                    subPageStack =
                                                        subPageStack + MacroPadSubPage.AppPicker(AppPickerTarget.EditProfile(prof.id))
                                                },
                                                onClearApp = { pendingProfilePackage = null },
                                                onSave = { name, pkg ->
                                                    val assoc =
                                                        if (pkg != null) {
                                                            val existing = prof.association
                                                            if (existing != null && existing.packageName.equals(pkg, ignoreCase = true)) {
                                                                existing
                                                            } else {
                                                                ProfileAssociation(packageName = pkg)
                                                            }
                                                        } else {
                                                            null
                                                        }
                                                    MacroPadState.renameProfile(prof.id, name, assoc)
                                                    subPageStack = subPageStack.dropLast(1)
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.AppPicker -> {
                                        val assigned =
                                            profiles
                                                .mapNotNull { it.association?.packageName }
                                                .map { it.trim().lowercase() }
                                                .toSet()
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.quick_menu_profile_label),
                                                    stringResource(R.string.profile_settings_app_mapping),
                                                    stringResource(R.string.profile_settings_search_apps),
                                                ),
                                            scrollable = false,
                                        ) {
                                            AppPickerSubPageContent(
                                                assignedPackages = assigned,
                                                accentColor = colors.accent,
                                                onSelectApp = { pkg ->
                                                    pendingProfilePackage = pkg
                                                    subPageStack = subPageStack.dropLast(1)
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ReorderProfiles -> {
                                        ReorderProfilesSubPage(
                                            profiles = profiles,
                                        )
                                    }

                                    is MacroPadSubPage.NewLayout -> {
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_layout),
                                                    stringResource(R.string.settings_macropad_new_layout),
                                                ),
                                        ) {
                                            NewLayoutSubPageContent(
                                                existingNames = profile.layouts.map { it.name },
                                                accentColor = colors.accent,
                                                onCreate = { name, invisibleBtns ->
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
                                                            id = UUID.randomUUID().toString(),
                                                            name = name,
                                                            enabled = true,
                                                            invisibleButtons = invisibleBtns,
                                                            mirrorCutouts = listOf(defaultCutout),
                                                        )
                                                    MacroPadState.addLayout(newLayout)
                                                    subPageStack = subPageStack.dropLast(1)
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.LayoutAppearance -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            val currentDraft = appearanceDraft?.takeIf { it.id == lay.id } ?: lay
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_layout),
                                                        stringResource(R.string.macropad_editor_appearance_title),
                                                    ),
                                            ) {
                                                LayoutAppearanceSubPageContent(
                                                    layout = currentDraft,
                                                    savedLayout = lay,
                                                    existingNames = profile.layouts.filter { it.id != lay.id }.map { it.name },
                                                    accentColor = colors.accent,
                                                    onNameChange = { newName ->
                                                        MacroPadState.updateLayout(lay.copy(name = newName))
                                                        if (appearanceDraft != null && appearanceDraft?.id == lay.id) {
                                                            appearanceDraft = appearanceDraft?.copy(name = newName)
                                                        }
                                                    },
                                                    onInvisibleButtonsChange = { newInvisible ->
                                                        MacroPadState.updateLayout(lay.copy(invisibleButtons = newInvisible))
                                                        if (appearanceDraft != null && appearanceDraft?.id == lay.id) {
                                                            appearanceDraft = appearanceDraft?.copy(invisibleButtons = newInvisible)
                                                        }
                                                    },
                                                    onOpenColorSubMenu = { target ->
                                                        subPageStack = subPageStack + MacroPadSubPage.LayoutColor(lay.id, target)
                                                    },
                                                    onSaveColors = { textCol, borderCol, bgCol ->
                                                        MacroPadState.updateLayout(
                                                            lay.copy(
                                                                buttonTextColor = textCol,
                                                                buttonBorderColor = borderCol,
                                                                buttonBgColor = bgCol,
                                                            ),
                                                        )
                                                        appearanceDraft = null
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.LayoutColor -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            val currentDraft = appearanceDraft?.takeIf { it.id == lay.id } ?: lay
                                            val targetTitle =
                                                when (currentSubPage.target) {
                                                    LayoutColorTarget.TEXT -> stringResource(R.string.layout_settings_color_text)
                                                    LayoutColorTarget.BORDER -> stringResource(R.string.layout_settings_color_border)
                                                    LayoutColorTarget.BG -> stringResource(R.string.layout_settings_color_bg)
                                                }
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_layout),
                                                        stringResource(R.string.macropad_editor_appearance_title),
                                                        targetTitle,
                                                    ),
                                            ) {
                                                LayoutColorSubPageContent(
                                                    layout = currentDraft,
                                                    savedLayout = lay,
                                                    target = currentSubPage.target,
                                                    accentColor = colors.accent,
                                                    onSave = { updatedDraft ->
                                                        appearanceDraft = updatedDraft
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                    onOpenColorWheel = { title, breadcrumbs, initialColor, inFlightLayout ->
                                                        subPageStack =
                                                            subPageStack +
                                                            MacroPadSubPage.ColorWheel(
                                                                title = title,
                                                                breadcrumbs = breadcrumbs,
                                                                initialColor = initialColor,
                                                                section = EditorSection.LAYOUTS,
                                                                onSave = { savedColor ->
                                                                    val option = ColorOption.Custom(savedColor.toArgb())
                                                                    val updatedLayout =
                                                                        when (currentSubPage.target) {
                                                                            LayoutColorTarget.TEXT -> {
                                                                                inFlightLayout.copy(
                                                                                    buttonTextColor = option,
                                                                                )
                                                                            }

                                                                            LayoutColorTarget.BORDER -> {
                                                                                inFlightLayout.copy(
                                                                                    buttonBorderColor = option,
                                                                                )
                                                                            }

                                                                            LayoutColorTarget.BG -> {
                                                                                inFlightLayout.copy(
                                                                                    buttonBgColor = option,
                                                                                )
                                                                            }
                                                                        }
                                                                    appearanceDraft = updatedLayout
                                                                    subPageStack = subPageStack.dropLast(1)
                                                                },
                                                            )
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.LayoutBackground -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_layout),
                                                        stringResource(R.string.layout_settings_bg_section_title),
                                                    ),
                                            ) {
                                                LayoutBackgroundSubPageContent(
                                                    layout = lay,
                                                    profileName = profile.name,
                                                    accentColor = colors.accent,
                                                    onOpenScrape = {
                                                        subPageStack = subPageStack + MacroPadSubPage.SteamGridDbScrape(lay.id)
                                                    },
                                                    onOpenCrop = { scale, ox, oy ->
                                                        subPageStack =
                                                            subPageStack +
                                                            MacroPadSubPage.BackgroundCrop(
                                                                layoutId = lay.id,
                                                                initialScale = scale,
                                                                initialOffsetX = ox,
                                                                initialOffsetY = oy,
                                                            )
                                                    },
                                                    onConfirm = { bgImagePath, useAsMask, bgChanged, bgScale, bgOffsetX, bgOffsetY, bgDim ->
                                                        MacroPadState.updateLayout(
                                                            lay.copy(
                                                                backgroundImagePath = bgImagePath,
                                                                useBackgroundImageAsMask = useAsMask,
                                                                backgroundImageVersion =
                                                                    if (bgChanged) lay.backgroundImageVersion + 1 else lay.backgroundImageVersion,
                                                                bgImageScale = bgScale,
                                                                bgImageOffsetX = bgOffsetX,
                                                                bgImageOffsetY = bgOffsetY,
                                                                backgroundImageDim = bgDim,
                                                            ),
                                                        )
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.BackgroundCrop -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_layout),
                                                        stringResource(R.string.layout_settings_bg_section_title),
                                                        stringResource(R.string.layout_settings_bg_image_crop),
                                                    ),
                                            ) {
                                                BackgroundCropSubPageContent(
                                                    layout = lay,
                                                    initialScale = currentSubPage.initialScale,
                                                    initialOffsetX = currentSubPage.initialOffsetX,
                                                    initialOffsetY = currentSubPage.initialOffsetY,
                                                    accentColor = colors.accent,
                                                    onConfirmCrop = { scale, ox, oy ->
                                                        MacroPadState.updateLayout(
                                                            lay.copy(
                                                                bgImageScale = scale,
                                                                bgImageOffsetX = ox,
                                                                bgImageOffsetY = oy,
                                                            ),
                                                        )
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.SteamGridDbScrape -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_layout),
                                                        stringResource(R.string.layout_settings_bg_section_title),
                                                        stringResource(R.string.layout_settings_bg_image_scrape),
                                                    ),
                                            ) {
                                                SteamGridDbScrapeSubPageContent(
                                                    initialSearchQuery = profile.name,
                                                    accentColor = colors.accent,
                                                    onImageSelected = { uri ->
                                                        BackgroundPickerManager.setPickedUri(uri)
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.LayoutTouchpad -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_layout),
                                                        stringResource(R.string.settings_touchpad_title),
                                                    ),
                                            ) {
                                                LayoutTouchpadSubPageContent(
                                                    layout = lay,
                                                    accentColor = colors.accent,
                                                    onConfirm = { updatedConfig, disableProjection ->
                                                        val newCutouts =
                                                            if (disableProjection) {
                                                                lay.mirrorCutouts.map { it.copy(touchProjectionEnabled = false) }
                                                            } else {
                                                                lay.mirrorCutouts
                                                            }
                                                        MacroPadState.updateLayout(
                                                            lay.copy(
                                                                backgroundTouchpad = updatedConfig,
                                                                mirrorCutouts = newCutouts,
                                                            ),
                                                        )
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.CopyLayout -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_layout),
                                                        stringResource(R.string.macropad_editor_copy_profile_select),
                                                    ),
                                            ) {
                                                CopyLayoutSubPageContent(
                                                    title = stringResource(R.string.macropad_editor_copy_profile_select),
                                                    profiles = profiles,
                                                    excludeProfileId = profile.id,
                                                    accentColor = colors.accent,
                                                    onSelect = { targetProfileId ->
                                                        MacroPadState.copyLayoutToProfile(lay, profile.id, targetProfileId)
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.ReorderLayouts -> {
                                        ReorderLayoutsSubPage(
                                            layouts = profile.layouts,
                                        )
                                    }

                                    is MacroPadSubPage.EditButton -> {
                                        val effectiveButton = currentSubPage.draftButton ?: currentSubPage.button
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    stringResource(
                                                        if (effectiveButton != null) {
                                                            R.string.macropad_editor_section_button_settings
                                                        } else {
                                                            R.string.macropad_editor_add_button
                                                        },
                                                    ),
                                                ),
                                        ) {
                                            EditButtonSubPageContent(
                                                button = effectiveButton,
                                                accentColor = colors.accent,
                                                onOpenIconPicker = { currentDraft ->
                                                    subPageStack =
                                                        subPageStack.dropLast(1) +
                                                        MacroPadSubPage.EditButton(
                                                            button = currentSubPage.button,
                                                            draftButton = currentDraft,
                                                        ) +
                                                        MacroPadSubPage.ChooseIcon
                                                },
                                                onOpenColorWheel = { title, breadcrumbs, initialColor, onApplyColor ->
                                                    subPageStack =
                                                        subPageStack +
                                                        MacroPadSubPage.ColorWheel(
                                                            title = title,
                                                            breadcrumbs = breadcrumbs,
                                                            initialColor = initialColor,
                                                            section = EditorSection.BUTTONS,
                                                            onSave = { saved ->
                                                                val updatedBtn = onApplyColor(saved)
                                                                subPageStack =
                                                                    subPageStack.dropLast(1).map { subPage ->
                                                                        if (subPage is MacroPadSubPage.EditButton) {
                                                                            subPage.copy(draftButton = updatedBtn)
                                                                        } else {
                                                                            subPage
                                                                        }
                                                                    }
                                                            },
                                                        )
                                                },
                                                onEditMacro = { macro ->
                                                    subPageStack = subPageStack + MacroPadSubPage.MacroTimeline(macro.id)
                                                },
                                                onSave = { savedBtn ->
                                                    val lay = activeLayout
                                                    if (lay != null) {
                                                        val isNew = lay.buttons.none { it.id == savedBtn.id }
                                                        val updatedButtons =
                                                            if (isNew) {
                                                                lay.buttons + savedBtn
                                                            } else {
                                                                lay.buttons.map { if (it.id == savedBtn.id) savedBtn else it }
                                                            }
                                                        MacroPadState.updateLayout(lay.copy(buttons = updatedButtons))
                                                    }
                                                    subPageStack = subPageStack.dropLast(1)
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseIcon -> {
                                        val parentDraftButton =
                                            subPageStack.filterIsInstance<MacroPadSubPage.EditButton>().lastOrNull()?.let {
                                                it.draftButton ?: it.button
                                            }
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    stringResource(R.string.macropad_icon_picker_title),
                                                ),
                                            scrollable = false,
                                        ) {
                                            ChooseIconSubPageContent(
                                                selectedIcon = parentDraftButton?.iconName,
                                                accentColor = colors.accent,
                                                filled = true,
                                                onFilledChange = {},
                                                onSelect = { icon ->
                                                    subPageStack =
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                val cur = subPage.draftButton ?: subPage.button
                                                                subPage.copy(draftButton = cur?.copy(iconName = icon))
                                                            } else {
                                                                subPage
                                                            }
                                                        }
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.CopyButton -> {
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    stringResource(R.string.macropad_editor_copy_layout_select),
                                                ),
                                        ) {
                                            CopyButtonSubPageContent(
                                                title = stringResource(R.string.macropad_editor_copy_layout_select),
                                                profiles = profiles,
                                                excludeLayoutId = activeLayout?.id,
                                                accentColor = colors.accent,
                                                onSelect = { targetProfileId, targetLayoutId ->
                                                    MacroPadState.copyButtonToLayout(
                                                        currentSubPage.button,
                                                        profile.id,
                                                        targetProfileId,
                                                        targetLayoutId,
                                                    )
                                                    subPageStack = subPageStack.dropLast(1)
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.MacroTimeline -> {
                                        val macro = profile.macros.firstOrNull { it.id == currentSubPage.macroId }
                                        if (macro != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_manage_macros),
                                                        macro.name.ifBlank { stringResource(R.string.macropad_editor_open_timeline_title) },
                                                    ),
                                            ) {
                                                MacroTimelineSubPageContent(
                                                    macro = macro,
                                                    accentColor = colors.accent,
                                                    onOpenAddStep = {
                                                        subPageStack =
                                                            subPageStack +
                                                            MacroPadSubPage.MacroStepEdit(macro.id, stepIndex = null)
                                                    },
                                                    onOpenEditStep = { stepIdx ->
                                                        subPageStack =
                                                            subPageStack +
                                                            MacroPadSubPage.MacroStepEdit(macro.id, stepIndex = stepIdx)
                                                    },
                                                    onSave = { updatedMacro ->
                                                        MacroPadState.updateMacro(updatedMacro)
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.MacroStepEdit -> {
                                        val macro = profile.macros.firstOrNull { it.id == currentSubPage.macroId }
                                        if (macro != null) {
                                            val step = currentSubPage.stepIndex?.let { macro.steps.getOrNull(it) }
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_manage_macros),
                                                        macro.name.ifBlank { stringResource(R.string.macropad_editor_open_timeline_title) },
                                                        stringResource(
                                                            if (step ==
                                                                null
                                                            ) {
                                                                R.string.macropad_macro_step_new
                                                            } else {
                                                                R.string.macropad_macro_step_edit
                                                            },
                                                        ),
                                                    ),
                                            ) {
                                                MacroStepEditSubPageContent(
                                                    macroName = macro.name,
                                                    step = step,
                                                    accentColor = colors.accent,
                                                    suggestedStartTimeMs = macro.steps.totalDurationMs(),
                                                    initialShiftMode = ShiftMode.END_DELTA,
                                                    onConfirm = { newStep, shiftMode ->
                                                        val updatedSteps =
                                                            if (currentSubPage.stepIndex != null) {
                                                                macro.steps.mapIndexed { idx, s ->
                                                                    if (idx == currentSubPage.stepIndex) newStep else s
                                                                }
                                                            } else {
                                                                macro.steps + newStep
                                                            }
                                                        MacroPadState.updateMacro(macro.copy(steps = updatedSteps))
                                                        subPageStack = subPageStack.dropLast(1)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.ColorWheel -> {
                                        GamepadDeck(
                                            breadcrumbs = currentSubPage.breadcrumbs,
                                        ) {
                                            ColorWheelSubPageContent(
                                                title = currentSubPage.title,
                                                breadcrumbs = emptyList(),
                                                initialColor = currentSubPage.initialColor,
                                                accentColor = colors.accent,
                                                onSaveColor = currentSubPage.onSave,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }

        MacroPadEditorHelpModal(
            visible = effectiveShowHelp,
            onDismiss = {
                internalShowEditorHelp = false
                onDismissHelp()
            },
        )
    }
}

// ── Decks Implementation ───────────────────────────────────────────────────

@Composable
private fun ProfilesDeck(
    profiles: List<PadProfile>,
    activeProfile: PadProfile,
    accentColor: Color,
    onSelectProfile: (String) -> Unit,
    onNewProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onDuplicateProfile: () -> Unit,
    onReorderProfiles: () -> Unit,
    onDeleteProfile: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val firstItemFocusRequester = remember { FocusRequester() }

    val profileIdx = profiles.indexOf(activeProfile).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.quick_menu_profile_label),
        description = stringResource(R.string.macropad_editor_active_profile_desc),
        selectedText = activeProfile.name,
        icon = Icons.Rounded.Folder,
        onPrevious = {
            val next = profiles[(profileIdx - 1 + profiles.size) % profiles.size]
            onSelectProfile(next.id)
        },
        onNext = {
            val next = profiles[(profileIdx + 1) % profiles.size]
            onSelectProfile(next.id)
        },
        modifier = Modifier.firstDeckItem().focusRequester(firstItemFocusRequester),
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_macropad_new_profile),
        description = stringResource(R.string.macropad_editor_new_profile_desc),
        actionText = stringResource(R.string.gamepad_action_create),
        icon = Icons.Rounded.Add,
        onClick = onNewProfile,
    )

    GamepadActionCard(
        title = stringResource(R.string.profile_settings_title),
        description = stringResource(R.string.macropad_editor_edit_profile_desc),
        actionText = stringResource(R.string.gamepad_action_edit),
        icon = Icons.Rounded.Edit,
        onClick = onEditProfile,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_duplicate_profile),
        description = stringResource(R.string.macropad_editor_duplicate_profile_desc, activeProfile.name),
        actionText = stringResource(R.string.gamepad_action_duplicate),
        icon = Icons.Rounded.ContentCopy,
        onClick = onDuplicateProfile,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_reorder_profiles),
        description = stringResource(R.string.macropad_editor_reorder_profiles_desc),
        actionText = stringResource(R.string.gamepad_action_reorder),
        icon = Icons.Rounded.SwapVert,
        onClick = onReorderProfiles,
    )

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_editor_delete_profile),
        confirmTitle = stringResource(R.string.macropad_profile_delete_confirm_title, activeProfile.name),
        description = stringResource(R.string.macropad_editor_delete_profile_desc, activeProfile.name),
        actionText = stringResource(R.string.gamepad_action_delete),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        isDestructive = true,
        icon = Icons.Rounded.Delete,
        onConfirm = {
            onDeleteProfile()
            scope.launch {
                try {
                    firstItemFocusRequester.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        },
    )
}

@Composable
private fun LayoutsDeck(
    profile: PadProfile,
    activeLayout: PadLayout?,
    accentColor: Color,
    onSelectLayout: (String) -> Unit,
    onNewLayout: () -> Unit,
    onEditAppearance: () -> Unit,
    onEditBackground: () -> Unit,
    onEditTouchpad: () -> Unit,
    onDuplicateLayout: () -> Unit,
    onCopyLayout: () -> Unit,
    onReorderLayouts: () -> Unit,
    onDeleteLayout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val firstItemFocusRequester = remember { FocusRequester() }

    val layouts = profile.layouts
    val layoutIdx = layouts.indexOf(activeLayout).coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_editor_section_layout),
        description = stringResource(R.string.macropad_editor_active_layout_desc, profile.name),
        selectedText = activeLayout?.name ?: stringResource(R.string.macropad_editor_none),
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
        modifier = Modifier.firstDeckItem().focusRequester(firstItemFocusRequester),
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_macropad_new_layout),
        description = stringResource(R.string.macropad_editor_new_layout_desc),
        actionText = stringResource(R.string.gamepad_action_create),
        icon = Icons.Rounded.Add,
        onClick = onNewLayout,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_appearance_title),
        description = stringResource(R.string.macropad_editor_appearance_desc),
        actionText = stringResource(R.string.gamepad_action_appearance),
        icon = Icons.Rounded.Palette,
        onClick = onEditAppearance,
    )

    GamepadActionCard(
        title = stringResource(R.string.layout_settings_bg_section_title),
        description = stringResource(R.string.macropad_editor_background_desc),
        actionText = stringResource(R.string.gamepad_action_background),
        icon = Icons.Rounded.Wallpaper,
        onClick = onEditBackground,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_touchpad_title),
        description = stringResource(R.string.macropad_editor_touchpad_desc),
        actionText = stringResource(R.string.gamepad_action_touchpad),
        icon = Icons.Rounded.Mouse,
        onClick = onEditTouchpad,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_duplicate_layout),
        description = stringResource(R.string.macropad_editor_duplicate_layout_desc),
        actionText = stringResource(R.string.gamepad_action_duplicate),
        icon = Icons.Rounded.ContentCopy,
        onClick = onDuplicateLayout,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_copy_profile_select),
        description = stringResource(R.string.macropad_editor_copy_layout_desc),
        actionText = stringResource(R.string.gamepad_action_copy),
        icon = Icons.Rounded.Share,
        onClick = onCopyLayout,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_reorder_layouts),
        description = stringResource(R.string.macropad_editor_reorder_layouts_desc),
        actionText = stringResource(R.string.gamepad_action_reorder),
        icon = Icons.Rounded.SwapVert,
        onClick = onReorderLayouts,
    )

    if (activeLayout != null) {
        GamepadTwoStepConfirmCard(
            title = stringResource(R.string.macropad_editor_delete_layout),
            confirmTitle = stringResource(R.string.macropad_layout_delete_confirm_title, activeLayout.name),
            description = stringResource(R.string.macropad_editor_delete_layout_desc, activeLayout.name),
            actionText = stringResource(R.string.gamepad_action_delete),
            confirmActionText = stringResource(R.string.gamepad_action_confirm),
            isDestructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                onDeleteLayout()
                scope.launch {
                    try {
                        firstItemFocusRequester.requestFocus()
                    } catch (_: IllegalStateException) {
                    }
                }
            },
        )
    }
}

@Composable
private fun CanvasDeck(
    profile: PadProfile,
    layout: PadLayout?,
    accentColor: Color,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onAddButton: () -> Unit,
) {
    var gridMode by remember { mutableStateOf(GridMode.OFF) }

    GamepadToggleCard(
        title = stringResource(R.string.macropad_editor_lock_canvas),
        description =
            if (isLocked) {
                stringResource(R.string.macropad_editor_canvas_locked_desc)
            } else {
                stringResource(R.string.macropad_editor_canvas_unlocked_desc)
            },
        checked = isLocked,
        icon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
        onCheckedChange = { onToggleLock() },
        modifier = Modifier.firstDeckItem(),
    )

    val gridModes = listOf(GridMode.OFF, GridMode.RECTANGULAR, GridMode.RADIAL)
    val gridIdx = gridModes.indexOf(gridMode)
    GamepadChoiceCard(
        title = stringResource(R.string.macropad_editor_snap_grid),
        description = stringResource(R.string.macropad_editor_snap_grid_desc),
        selectedText =
            when (gridMode) {
                GridMode.OFF -> stringResource(R.string.macropad_editor_grid_off_label)
                GridMode.RECTANGULAR -> stringResource(R.string.macropad_editor_grid_rectangular_label)
                GridMode.RADIAL -> stringResource(R.string.macropad_editor_grid_radial_label)
            },
        icon = Icons.Rounded.Grid4x4,
        onPrevious = { gridMode = gridModes[(gridIdx - 1 + gridModes.size) % gridModes.size] },
        onNext = { gridMode = gridModes[(gridIdx + 1) % gridModes.size] },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_add_button),
        description = stringResource(R.string.macropad_editor_add_button_card_desc),
        actionText = stringResource(R.string.gamepad_action_add),
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
            isLocked = isLocked,
        )
    }
}

@Composable
private fun ButtonsDeck(
    profile: PadProfile,
    layout: PadLayout?,
    accentColor: Color,
    onAddButton: () -> Unit,
    onEditButton: (PadButton) -> Unit,
    onDuplicateButton: (PadButton) -> Unit,
    onCopyButton: (PadButton) -> Unit,
    onDeleteButton: (PadButton) -> Unit,
) {
    val colors = LocalAppColors.current
    val buttons = layout?.buttons ?: emptyList()
    val lazyListState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            if (layout != null) {
                val newButtons = layout.buttons.toMutableList()
                val fromIdx = from.index.coerceIn(0, newButtons.lastIndex)
                val toIdx = to.index.coerceIn(0, newButtons.lastIndex)
                newButtons.add(toIdx, newButtons.removeAt(fromIdx))
                MacroPadState.updateLayout(layout.copy(buttons = newButtons))
            }
        }

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_add_button),
        description = stringResource(R.string.macropad_editor_create_button_desc),
        actionText = stringResource(R.string.gamepad_action_add),
        icon = Icons.Rounded.Add,
        onClick = onAddButton,
        modifier = Modifier.firstDeckItem(),
    )

    if (buttons.isEmpty()) {
        Text(
            text = stringResource(R.string.macropad_editor_no_buttons_in_layout),
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
                        onDuplicate = { onDuplicateButton(btn) },
                        onCopyToLayout = { onCopyButton(btn) },
                        onDelete = { onDeleteButton(btn) },
                        dragHandleModifier = Modifier.draggableHandle(),
                    )
                    AppDivider(modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MacrosDeck(
    profile: PadProfile,
    accentColor: Color,
    onNewMacro: () -> Unit,
    onEditMacro: (Macro) -> Unit,
    onDeleteMacro: (Macro) -> Unit,
) {
    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_open_timeline_title),
        description = stringResource(R.string.macropad_editor_open_timeline_desc),
        actionText = stringResource(R.string.gamepad_action_new),
        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
        onClick = onNewMacro,
        modifier = Modifier.firstDeckItem(),
    )

    val macros = profile.macros
    if (macros.isEmpty()) {
        GamepadEmptyState(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = stringResource(R.string.macropad_editor_no_macros_title),
            description = stringResource(R.string.macropad_editor_no_macros_desc),
            actionText = stringResource(R.string.gamepad_action_create_macro),
            onAction = onNewMacro,
        )
    } else {
        macros.forEach { macro ->
            val stepCountDesc =
                if (macro.steps.size == 1) {
                    stringResource(R.string.macropad_macro_step_count_single)
                } else {
                    stringResource(R.string.macropad_macro_step_count_multiple, macro.steps.size)
                }
            GamepadActionCard(
                title = macro.name,
                description = stepCountDesc,
                actionText = stringResource(R.string.gamepad_action_edit),
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                onClick = { onEditMacro(macro) },
            )
        }
    }
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
