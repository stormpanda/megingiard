package com.stormpanda.megingiard.macropad

import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.mirror.AspectRatioMode
import com.stormpanda.megingiard.mirror.CutoutPlacementHelper
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.mirror.adjustSourceCropToAspectRatio
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.steamgriddb.SteamGridDbScrapeSubPageContent
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardRow
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadReorderCard
import com.stormpanda.megingiard.ui.GamepadReorderDeck
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryModalType
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberGamepadBringIntoViewSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID
import kotlin.math.max

private const val TAG = "MacroPadEditor"
private val MPE_DECK_SPACING = 10.dp
private val MPE_EMPTY_PADDING_V = 12.dp
private const val MPE_BUTTON_HEADER_COUNT = 5
private const val MPE_CANVAS_WIDTH_PX = 1920f
private const val MPE_CANVAS_HEIGHT_PX = 1080f
private const val MPE_EDGE_MARGIN = 0.05f
private const val MPE_MOVE_INITIAL_DELAY_MS = 250L
private const val MPE_MOVE_START_DELAY_MS = 80L
private const val MPE_MOVE_MIN_DELAY_MS = 16L
private const val MPE_MOVE_ACCEL_FACTOR = 0.88f

private fun applyActionToDraftButton(
    draftButton: PadButton,
    newAction: PadAction,
): PadButton {
    var shape = draftButton.buttonShape
    var size = draftButton.buttonSize

    if (newAction is PadAction.ScrollWheel) {
        size = ButtonSize.SIZE_1X2
    } else if (newAction is PadAction.TrackpointMove) {
        shape = ButtonShape.CIRCLE
    }

    return draftButton.copy(
        action = newAction,
        buttonShape = shape,
        buttonSize = size,
    )
}

internal val MPE_PADDING = 16.dp

@Composable
fun MacroPadEditor(
    onDone: () -> Unit,
    showTopBar: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by MacroPadState.profiles.collectAsState()
    val activeId by MacroPadState.activeProfileId.collectAsState()
    val colors = LocalAppColors.current

    DisposableEffect(Unit) {
        AppLog.i(TAG, "MacroPadEditor visible")
        onDispose {
            AppLog.i(TAG, "MacroPadEditor dismissed")
            MacroPadState.setEditingButtonPositions(false)
            MacroPadState.setSelectedButtonId(null)
            MacroPadState.clearPreviewLayout()
        }
    }

    val profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val activeLayout =
        remember(profile) {
            val layoutId = profile?.activeLayoutId
            profile?.layouts?.firstOrNull { it.id == layoutId } ?: profile?.layouts?.firstOrNull()
        }

    val selectedSection by MacroPadNavState.selectedSection.collectAsState()
    val subPageStack by MacroPadNavState.subPageStack.collectAsState()

    // Temporary storage for intermediate wizard picks (e.g. app picker for new/edit profile, icon picker)
    var pendingProfilePackage by remember { mutableStateOf<String?>(null) }
    val macroTimelineFocusStepIndex by MacroPadNavState.macroTimelineFocusStepIndex.collectAsState()
    var appearanceDraft by remember { mutableStateOf<PadLayout?>(null) }
    var buttonDraft by remember { mutableStateOf<PadButton?>(null) }

    val activePrimaryModal by AppStateManager.activePrimaryModal.collectAsState()
    val savedFocusKeys by MacroPadNavState.savedFocusKeysByDepth.collectAsState()

    LaunchedEffect(selectedSection) {
        MacroPadState.setSelectedButtonId(null)
    }

    LaunchedEffect(activePrimaryModal) {
        MacroPadNavState.applyPrimaryModalPayload(activePrimaryModal?.payload)
    }

    LaunchedEffect(subPageStack) {
        val isEditingPositionsSubPage =
            subPageStack.any { it is MacroPadSubPage.EditButtonPositions }
        MacroPadState.setEditingButtonPositions(isEditingPositionsSubPage)
    }

    LaunchedEffect(subPageStack, selectedSection) {
        val hasAppearanceSubPages =
            subPageStack.any {
                it is MacroPadSubPage.LayoutAppearance || it is MacroPadSubPage.LayoutColor ||
                    (it is MacroPadSubPage.ColorWheel && it.section == EditorSection.LAYOUTS)
            }
        val hasButtonSubPages =
            subPageStack.any {
                it is MacroPadSubPage.EditButton || it is MacroPadSubPage.ButtonColor ||
                    (it is MacroPadSubPage.ColorWheel && it.section == EditorSection.BUTTONS)
            }
        val hasBackgroundSubPages =
            subPageStack.any {
                it is MacroPadSubPage.SteamGridDbScrape
            }
        val isBackgroundSection = selectedSection == EditorSection.BACKGROUND
        if (!hasAppearanceSubPages) {
            appearanceDraft = null
        }
        if (!hasButtonSubPages) {
            buttonDraft = null
        }
        if (!hasAppearanceSubPages && !hasButtonSubPages && !hasBackgroundSubPages && !isBackgroundSection) {
            MacroPadState.clearPreviewLayout()
        }
    }

    BackHandler(enabled = true) {
        if (subPageStack.isNotEmpty()) {
            MacroPadNavState.pop()
        } else {
            MacroPadNavState.reset()
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
            MacroPadNavState.selectSection(EditorSection.entries[nextIndex])
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
                onDismiss = {
                    MacroPadNavState.reset()
                    onDone()
                },
            )
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
                        MacroPadNavState.pop()
                    },
                    navigationKey = subPageStack,
                    savedFocusKeys = savedFocusKeys,
                    onRecordFocusedKey = { depth, key -> MacroPadNavState.recordFocusedKey(depth, key) },
                    onRemoveFocusedKey = { depth -> MacroPadNavState.removeFocusedKey(depth) },
                    sidebarContent = {
                        GamepadCategoryTile(
                            title = stringResource(R.string.quick_actions_title),
                            icon = Icons.Rounded.Bolt,
                            selected = selectedSection == EditorSection.QUICK_ACTIONS,
                            onClick = {
                                MacroPadNavState.selectSection(EditorSection.QUICK_ACTIONS)
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.quick_menu_profile_label),
                            icon = Icons.Rounded.Folder,
                            selected = selectedSection == EditorSection.PROFILES,
                            onClick = {
                                MacroPadNavState.selectSection(EditorSection.PROFILES)
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.macropad_editor_section_layout),
                            icon = Icons.AutoMirrored.Rounded.ViewQuilt,
                            selected = selectedSection == EditorSection.LAYOUTS,
                            onClick = {
                                MacroPadNavState.selectSection(EditorSection.LAYOUTS)
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.quick_menu_screen_mirroring),
                            icon = Icons.Rounded.Videocam,
                            selected = selectedSection == EditorSection.MIRROR,
                            onClick = {
                                MacroPadNavState.selectSection(EditorSection.MIRROR)
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.layout_settings_bg_section_title),
                            icon = Icons.Rounded.Wallpaper,
                            selected = selectedSection == EditorSection.BACKGROUND,
                            onClick = {
                                MacroPadNavState.selectSection(EditorSection.BACKGROUND)
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.macropad_editor_section_buttons),
                            icon = Icons.Rounded.SmartButton,
                            selected = selectedSection == EditorSection.BUTTONS,
                            onClick = {
                                MacroPadNavState.selectSection(EditorSection.BUTTONS)
                            },
                        )
                        GamepadCategoryTile(
                            title = stringResource(R.string.macropad_editor_manage_macros),
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            selected = selectedSection == EditorSection.MACROS,
                            onClick = {
                                MacroPadNavState.selectSection(EditorSection.MACROS)
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
                                        EditorSection.QUICK_ACTIONS -> stringResource(R.string.quick_actions_title)
                                        EditorSection.PROFILES -> stringResource(R.string.quick_menu_profile_label)
                                        EditorSection.LAYOUTS -> stringResource(R.string.macropad_editor_section_layout)
                                        EditorSection.MIRROR -> stringResource(R.string.quick_menu_screen_mirroring)
                                        EditorSection.BACKGROUND -> stringResource(R.string.layout_settings_bg_section_title)
                                        EditorSection.BUTTONS -> stringResource(R.string.macropad_editor_section_buttons)
                                        EditorSection.MACROS -> stringResource(R.string.macropad_editor_manage_macros)
                                    }
                                GamepadDeck(
                                    title = sectionTitle,
                                    scrollable = selectedSection != EditorSection.BUTTONS,
                                ) {
                                    // ── Main Section Decks ─────────────────────────────
                                    when (selectedSection) {
                                        EditorSection.QUICK_ACTIONS -> {
                                            QuickActionsDeckContent(
                                                onNewButton = {
                                                    MacroPadNavState.setStack(listOf(MacroPadSubPage.ChooseButtonType))
                                                },
                                                onNewMacro = {
                                                    MacroPadNavState.setMacroTimelineFocusStepIndex(null)
                                                    MacroPadNavState.setStack(
                                                        listOf(MacroPadSubPage.ChooseMacroMode),
                                                    )
                                                },
                                                onNewLayout = {
                                                    MacroPadNavState.setStack(listOf(MacroPadSubPage.NewLayout))
                                                },
                                                onNewProfile = {
                                                    pendingProfilePackage = null
                                                    MacroPadNavState.setStack(listOf(MacroPadSubPage.NewProfile))
                                                },
                                                onArrangeButtons = {
                                                    MacroPadNavState.setStack(listOf(MacroPadSubPage.EditButtonPositions))
                                                },
                                                onEditMirrorLayout = {
                                                    onDone()
                                                    AppStateManager.setViewportEditActive(true)
                                                },
                                            )
                                        }

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
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.NewProfile)
                                                },
                                                onEditProfile = {
                                                    pendingProfilePackage = profile.association?.packageName
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.EditProfile(profile.id))
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
                                                        val duplicatedProfile = MacroPadState.activeProfile.value
                                                        val duplicatedName = duplicatedProfile?.name ?: originalProfile.name
                                                        DialogToastManager.show(
                                                            context.getString(R.string.macropad_profile_duplicated_toast, duplicatedName),
                                                        )
                                                    }
                                                },
                                                onReorderProfiles = {
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.ReorderProfiles)
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
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.NewLayout)
                                                },
                                                onEditAppearance = {
                                                    if (activeLayout != null) {
                                                        MacroPadNavState.setStack(
                                                            subPageStack + MacroPadSubPage.LayoutAppearance(activeLayout.id),
                                                        )
                                                    }
                                                },
                                                onEditTouchpad = {
                                                    if (activeLayout != null) {
                                                        MacroPadNavState.setStack(
                                                            subPageStack + MacroPadSubPage.LayoutTouchpad(activeLayout.id),
                                                        )
                                                    }
                                                },
                                                onDuplicateLayout = {
                                                    val originalLayout = activeLayout
                                                    val originalPath = originalLayout?.backgroundImagePath
                                                    val newLayoutId = originalLayout?.id?.let { MacroPadState.duplicateLayout(it) }
                                                    if (originalLayout != null && newLayoutId != null) {
                                                        if (originalPath != null) {
                                                            scope.launch {
                                                                MacroPadMediaRepository.duplicateBackgroundImage(
                                                                    context,
                                                                    originalLayout.id,
                                                                    newLayoutId,
                                                                )
                                                            }
                                                        }
                                                        val duplicatedLayout =
                                                            MacroPadState.activeProfile.value?.layouts?.firstOrNull {
                                                                it.id ==
                                                                    newLayoutId
                                                            }
                                                        val duplicatedName = duplicatedLayout?.name ?: originalLayout.name
                                                        DialogToastManager.show(
                                                            context.getString(R.string.macropad_layout_duplicated_toast, duplicatedName),
                                                        )
                                                    }
                                                },
                                                onCopyLayout = {
                                                    if (activeLayout != null) {
                                                        MacroPadNavState.setStack(
                                                            subPageStack + MacroPadSubPage.CopyLayout(activeLayout.id),
                                                        )
                                                    }
                                                },
                                                onReorderLayouts = {
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.ReorderLayouts)
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

                                        EditorSection.MIRROR -> {
                                            if (activeLayout != null) {
                                                MirrorDeck(
                                                    profile = profile,
                                                    layout = activeLayout,
                                                    accentColor = colors.accent,
                                                    onArrangeCutouts = {
                                                        onDone()
                                                        AppStateManager.setViewportEditActive(true)
                                                    },
                                                    onOpenAdvancedSettings = {
                                                        MacroPadNavState.setStack(
                                                            subPageStack + MacroPadSubPage.MirrorAdvancedSettings(activeLayout.id),
                                                        )
                                                    },
                                                    onAddCutout = {
                                                        val layout = activeLayout
                                                        val slot = CutoutPlacementHelper.findAvailableSlot(layout.mirrorCutouts)
                                                        if (slot == null) {
                                                            DialogToastManager.show(
                                                                context.getString(R.string.mirror_editor_no_space),
                                                            )
                                                        } else {
                                                            val newId = UUID.randomUUID().toString()
                                                            val initialCutout =
                                                                ScreenCutout(
                                                                    id = newId,
                                                                    name = "Cutout ${layout.mirrorCutouts.size + 1}",
                                                                    srcX = 0.25f,
                                                                    srcY = 0.25f,
                                                                    srcWidth = 0.5f,
                                                                    srcHeight = 0.5f,
                                                                    destX = slot.destX,
                                                                    destY = slot.destY,
                                                                    destWidth = slot.destWidth,
                                                                    destHeight = slot.destHeight,
                                                                    aspectRatioMode = AspectRatioMode.BOTTOM,
                                                                )
                                                            val screenW =
                                                                ScreenCaptureManager.surfaceWidth.value.toFloat().let {
                                                                    if (it > 0f) it else 1920f
                                                                }
                                                            val screenH =
                                                                ScreenCaptureManager.surfaceHeight.value.toFloat().let {
                                                                    if (it > 0f) it else 1080f
                                                                }
                                                            val srcW =
                                                                ScreenCaptureManager.captureSourceWidth.value.toFloat().let {
                                                                    if (it > 0f) it else 1920f
                                                                }
                                                            val srcH =
                                                                ScreenCaptureManager.captureSourceHeight.value.toFloat().let {
                                                                    if (it > 0f) it else 1080f
                                                                }
                                                            val newCutout =
                                                                adjustSourceCropToAspectRatio(
                                                                    cutout = initialCutout,
                                                                    screenW = screenW,
                                                                    screenH = screenH,
                                                                    srcW = srcW,
                                                                    srcH = srcH,
                                                                )
                                                            MacroPadState.updateLayout(
                                                                layout.copy(
                                                                    mirrorCutouts = layout.mirrorCutouts + newCutout,
                                                                ),
                                                            )
                                                            MacroPadNavState.setStack(
                                                                subPageStack + MacroPadSubPage.CutoutSettings(newId),
                                                            )
                                                        }
                                                    },
                                                    onEditCutout = { cutout ->
                                                        MacroPadNavState.setStack(
                                                            subPageStack + MacroPadSubPage.CutoutSettings(cutout.id),
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        EditorSection.BACKGROUND -> {
                                            if (activeLayout != null) {
                                                LayoutBackgroundSubPageContent(
                                                    layout = activeLayout,
                                                    profileName = profile.name,
                                                    accentColor = colors.accent,
                                                    onOpenScrape = {
                                                        MacroPadNavState.setStack(
                                                            subPageStack + MacroPadSubPage.SteamGridDbScrape(activeLayout.id),
                                                        )
                                                    },
                                                    onDiscard = {
                                                        MacroPadState.clearPreviewLayout()
                                                    },
                                                    onConfirm = { bgImagePath, useAsMask, bgChanged, bgScale, bgOffsetX, bgOffsetY, bgDim ->
                                                        MacroPadState.clearPreviewLayout()
                                                        MacroPadState.updateLayout(
                                                            activeLayout.copy(
                                                                backgroundImagePath = bgImagePath,
                                                                useBackgroundImageAsMask = useAsMask,
                                                                backgroundImageVersion =
                                                                    if (bgChanged) activeLayout.backgroundImageVersion + 1 else activeLayout.backgroundImageVersion,
                                                                bgImageScale = bgScale,
                                                                bgImageOffsetX = bgOffsetX,
                                                                bgImageOffsetY = bgOffsetY,
                                                                backgroundImageDim = bgDim,
                                                            ),
                                                        )
                                                        DialogToastManager.show(
                                                            context.getString(R.string.gamepad_action_save_and_exit_desc),
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        EditorSection.BUTTONS -> {
                                            ButtonsDeck(
                                                profile = profile,
                                                layout = activeLayout,
                                                accentColor = colors.accent,
                                                onEditButtonPositions = {
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.EditButtonPositions)
                                                },
                                                onAddButton = {
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.ChooseButtonType)
                                                },
                                                onEditButton = { btn ->
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.EditButton(btn))
                                                },
                                            )
                                        }

                                        EditorSection.MACROS -> {
                                            MacrosDeck(
                                                profile = profile,
                                                accentColor = colors.accent,
                                                onNewMacro = {
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.ChooseMacroMode)
                                                },
                                                onEditMacro = { macro ->
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.MacroTimeline(macro = macro))
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
                                                    MacroPadNavState.setStack(
                                                        subPageStack + MacroPadSubPage.AppPicker(AppPickerTarget.NewProfile),
                                                    )
                                                },
                                                onClearApp = { pendingProfilePackage = null },
                                                onDiscard = { MacroPadNavState.pop() },
                                                onCreate = { name, pkg ->
                                                    val assoc = pkg?.let { ProfileAssociation(packageName = it) }
                                                    val newProf =
                                                        PadProfile(
                                                            id = UUID.randomUUID().toString(),
                                                            name = name,
                                                            association = assoc,
                                                        )
                                                    MacroPadState.addProfile(newProf)
                                                    MacroPadNavState.pop()
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
                                                    MacroPadNavState.setStack(
                                                        subPageStack + MacroPadSubPage.AppPicker(AppPickerTarget.EditProfile(prof.id)),
                                                    )
                                                },
                                                onClearApp = { pendingProfilePackage = null },
                                                onDiscard = { MacroPadNavState.pop() },
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
                                                    MacroPadNavState.pop()
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.AppPicker -> {
                                        val isButtonTarget = currentSubPage.target is AppPickerTarget.EditButton
                                        val assigned =
                                            if (isButtonTarget) {
                                                emptySet()
                                            } else {
                                                profiles
                                                    .mapNotNull { it.association?.packageName }
                                                    .map { it.trim().lowercase() }
                                                    .toSet()
                                            }
                                        GamepadDeck(
                                            breadcrumbs =
                                                if (isButtonTarget) {
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_section_buttons),
                                                        stringResource(R.string.app_launcher_picker_title),
                                                    )
                                                } else {
                                                    listOf(
                                                        stringResource(R.string.quick_menu_profile_label),
                                                        stringResource(R.string.profile_settings_app_mapping),
                                                        stringResource(R.string.profile_settings_search_apps),
                                                    )
                                                },
                                            scrollable = false,
                                        ) {
                                            AppPickerSubPageContent(
                                                assignedPackages = assigned,
                                                accentColor = colors.accent,
                                                onSelectApp = { pkg ->
                                                    when (currentSubPage.target) {
                                                        is AppPickerTarget.NewProfile,
                                                        is AppPickerTarget.EditProfile,
                                                        -> {
                                                            pendingProfilePackage = pkg
                                                            MacroPadNavState.pop()
                                                        }

                                                        is AppPickerTarget.EditButton -> {
                                                            MacroPadNavState.setStack(
                                                                subPageStack.dropLast(1).map { subPage ->
                                                                    if (subPage is MacroPadSubPage.EditButton) {
                                                                        val draft =
                                                                            buttonDraft
                                                                                ?: subPage.draftButton
                                                                                ?: subPage.button
                                                                                ?: PadButton(
                                                                                    id = UUID.randomUUID().toString(),
                                                                                    label =
                                                                                        context.getString(
                                                                                            R.string.macropad_editor_new_button_default_label,
                                                                                        ),
                                                                                    posX = 0.5f,
                                                                                    posY = 0.5f,
                                                                                    action = PadAction.AppLauncher(pkg),
                                                                                )
                                                                        val newBtn = draft.copy(action = PadAction.AppLauncher(pkg))
                                                                        buttonDraft = newBtn
                                                                        subPage.copy(draftButton = newBtn)
                                                                    } else {
                                                                        subPage
                                                                    }
                                                                },
                                                            )
                                                        }
                                                    }
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
                                                onDiscard = { MacroPadNavState.pop() },
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
                                                    MacroPadNavState.pop()
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
                                                        MacroPadNavState.setStack(
                                                            subPageStack + MacroPadSubPage.LayoutColor(lay.id, target),
                                                        )
                                                    },
                                                    onDiscard = { MacroPadNavState.pop() },
                                                    onSaveColors = { textCol, borderCol, bgCol ->
                                                        MacroPadState.updateLayout(
                                                            lay.copy(
                                                                buttonTextColor = textCol,
                                                                buttonBorderColor = borderCol,
                                                                buttonBgColor = bgCol,
                                                            ),
                                                        )
                                                        appearanceDraft = null
                                                        MacroPadNavState.pop()
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
                                                    onColorOptionChanged = { option ->
                                                        val updatedDraft =
                                                            when (currentSubPage.target) {
                                                                LayoutColorTarget.TEXT -> currentDraft.copy(buttonTextColor = option)
                                                                LayoutColorTarget.BORDER -> currentDraft.copy(buttonBorderColor = option)
                                                                LayoutColorTarget.BG -> currentDraft.copy(buttonBgColor = option)
                                                            }
                                                        appearanceDraft = updatedDraft
                                                        MacroPadState.setPreviewLayout(updatedDraft)
                                                    },
                                                    onOpenColorWheel = { title, breadcrumbs, initialColor, inFlightLayout ->
                                                        MacroPadNavState.setStack(
                                                            subPageStack +
                                                                MacroPadSubPage.ColorWheel(
                                                                    title = title,
                                                                    breadcrumbs = breadcrumbs,
                                                                    initialColor = initialColor,
                                                                    section = EditorSection.LAYOUTS,
                                                                    onColorChange = { liveColor ->
                                                                        val option = ColorOption.Custom(liveColor.toArgb())
                                                                        val liveLayout =
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
                                                                        appearanceDraft = liveLayout
                                                                        MacroPadState.setPreviewLayout(liveLayout)
                                                                    },
                                                                ),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.MirrorAdvancedSettings -> {
                                        val lay = profile.layouts.firstOrNull { it.id == currentSubPage.layoutId } ?: activeLayout
                                        if (lay != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.quick_menu_screen_mirroring),
                                                        stringResource(R.string.settings_mirror_advanced_title),
                                                    ),
                                            ) {
                                                MirrorAdvancedSettingsSubPageContent(
                                                    layout = lay,
                                                    accentColor = colors.accent,
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.CutoutSettings -> {
                                        val layout = activeLayout
                                        val cutout =
                                            layout?.mirrorCutouts?.firstOrNull { it.id == currentSubPage.cutoutId }
                                        if (cutout != null) {
                                            val cutoutTitle =
                                                cutout.name.ifBlank {
                                                    val index = layout.mirrorCutouts.indexOfFirst { it.id == cutout.id }
                                                    stringResource(
                                                        R.string.settings_mirror_cutout_default_name_fmt,
                                                        if (index >= 0) index + 1 else 1,
                                                    )
                                                }
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.quick_menu_screen_mirroring),
                                                        cutoutTitle,
                                                    ),
                                            ) {
                                                CutoutSettingsSubPageContent(
                                                    cutout = cutout,
                                                    layout = layout,
                                                    accentColor = colors.accent,
                                                    onUpdateCutout = { updatedCutout, disableTouchpad ->
                                                        val updatedList =
                                                            layout.mirrorCutouts.map {
                                                                if (it.id == updatedCutout.id) updatedCutout else it
                                                            }
                                                        val updatedLayout =
                                                            if (disableTouchpad) {
                                                                layout.copy(
                                                                    mirrorCutouts = updatedList,
                                                                    backgroundTouchpad = layout.backgroundTouchpad.copy(enabled = false),
                                                                )
                                                            } else {
                                                                layout.copy(mirrorCutouts = updatedList)
                                                            }
                                                        MacroPadState.updateLayout(updatedLayout)
                                                    },
                                                    onDeleteCutout = { cutoutIdToDelete ->
                                                        val deletedName = cutout.name.ifBlank { cutoutTitle }
                                                        val updatedList = layout.mirrorCutouts.filter { it.id != cutoutIdToDelete }
                                                        MacroPadState.updateLayout(layout.copy(mirrorCutouts = updatedList))
                                                        DialogToastManager.show(
                                                            context.getString(R.string.macropad_cutout_deleted_toast, deletedName),
                                                        )
                                                        MacroPadNavState.pop()
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
                                                        stringResource(R.string.layout_settings_bg_section_title),
                                                        stringResource(R.string.layout_settings_bg_image_scrape),
                                                    ),
                                            ) {
                                                SteamGridDbScrapeSubPageContent(
                                                    initialSearchQuery = profile.name,
                                                    accentColor = colors.accent,
                                                    onImageSelected = { uri ->
                                                        BackgroundPickerManager.setPickedUri(uri)
                                                        MacroPadNavState.pop()
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
                                                    onUpdate = { updatedConfig, disableProjection ->
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
                                                        MacroPadNavState.pop()
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

                                    is MacroPadSubPage.EditButtonPositions -> {
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    stringResource(R.string.macropad_editor_edit_button_positions),
                                                ),
                                        ) {
                                            EditButtonPositionsSubPageContent(
                                                layout = activeLayout,
                                                accentColor = colors.accent,
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseButtonType -> {
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    stringResource(R.string.macropad_editor_add_button),
                                                    stringResource(R.string.macropad_editor_button_type),
                                                ),
                                        ) {
                                            ChooseButtonTypeSubPageContent(
                                                onSelectType = { group ->
                                                    val hasMacros = profile.macros.isNotEmpty()
                                                    val defaultCategory =
                                                        group.actions().firstOrNull { it.isEnabled(true, true, true, hasMacros) }
                                                            ?: group.actions().first()
                                                    val defaultAction = defaultCategory.defaultAction()
                                                    val newDraft =
                                                        PadButton(
                                                            id = UUID.randomUUID().toString(),
                                                            label = context.getString(R.string.macropad_editor_new_button_default_label),
                                                            posX = 0.5f,
                                                            posY = 0.5f,
                                                            action = defaultAction,
                                                        )
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = null,
                                                                draftButton = newDraft,
                                                            ),
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.EditButton -> {
                                        val effectiveButton =
                                            buttonDraft?.takeIf { it.id == (currentSubPage.button?.id ?: currentSubPage.draftButton?.id) }
                                                ?: currentSubPage.draftButton
                                                ?: currentSubPage.button
                                                ?: PadButton(
                                                    id = UUID.randomUUID().toString(),
                                                    label = stringResource(R.string.macropad_editor_new_button_default_label),
                                                    posX = 0.5f,
                                                    posY = 0.5f,
                                                    action = PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A"),
                                                )
                                        val isNew = currentSubPage.button == null
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    stringResource(
                                                        if (!isNew) {
                                                            R.string.macropad_editor_section_button_settings
                                                        } else {
                                                            R.string.macropad_editor_add_button
                                                        },
                                                    ),
                                                ),
                                        ) {
                                            EditButtonSubPageContent(
                                                button = effectiveButton,
                                                savedButton = currentSubPage.button,
                                                accentColor = colors.accent,
                                                onOpenIconPicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseIcon,
                                                    )
                                                },
                                                onOpenAppPicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.AppPicker(AppPickerTarget.EditButton),
                                                    )
                                                },
                                                onOpenColorSubMenu = { currentDraft, target ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ButtonColor(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                                target = target,
                                                            ),
                                                    )
                                                },
                                                onOpenKeyboardPicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseKeyboardKey(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ),
                                                    )
                                                },
                                                onOpenGamepadPicker = { currentDraft, slotIndex ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseGamepadButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                                slotIndex = slotIndex,
                                                            ),
                                                    )
                                                },
                                                onOpenMousePicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseMouseAction(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ),
                                                    )
                                                },
                                                onOpenMirrorPicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseMirrorAction(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ),
                                                    )
                                                },
                                                onOpenOverlayPicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseOverlayAction(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ),
                                                    )
                                                },
                                                onOpenLayoutPicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseLayoutAction(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ),
                                                    )
                                                },
                                                onOpenMacroPicker = { currentDraft ->
                                                    buttonDraft = currentDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1) +
                                                            MacroPadSubPage.EditButton(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ) +
                                                            MacroPadSubPage.ChooseMacroAction(
                                                                button = currentSubPage.button,
                                                                draftButton = currentDraft,
                                                            ),
                                                    )
                                                },
                                                onDuplicate = { btn ->
                                                    buttonDraft = null
                                                    activeLayout?.id?.let { MacroPadState.duplicateButtonInLayout(btn, it) }
                                                    MacroPadNavState.pop()
                                                },
                                                onCopyToLayout = { btn ->
                                                    MacroPadNavState.setStack(subPageStack + MacroPadSubPage.CopyButton(btn))
                                                },
                                                onDelete = { btn ->
                                                    buttonDraft = null
                                                    activeLayout?.let { lay ->
                                                        MacroPadState.updateLayout(
                                                            lay.copy(buttons = lay.buttons.filter { it.id != btn.id }),
                                                        )
                                                    }
                                                    DialogToastManager.show(
                                                        context.getString(R.string.macropad_button_deleted_toast),
                                                    )
                                                    MacroPadNavState.pop()
                                                },
                                                onDiscard = {
                                                    buttonDraft = null
                                                    MacroPadNavState.pop()
                                                },
                                                onSave = { savedBtn ->
                                                    buttonDraft = null
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
                                                    MacroPadNavState.pop()
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ButtonColor -> {
                                        val effectiveButton =
                                            buttonDraft?.takeIf { it.id == (currentSubPage.button?.id ?: currentSubPage.draftButton.id) }
                                                ?: currentSubPage.draftButton
                                        val targetTitle =
                                            when (currentSubPage.target) {
                                                ButtonColorTarget.TEXT -> stringResource(R.string.layout_settings_color_text)
                                                ButtonColorTarget.BORDER -> stringResource(R.string.layout_settings_color_border)
                                                ButtonColorTarget.BG -> stringResource(R.string.layout_settings_color_bg)
                                            }
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    targetTitle,
                                                ),
                                        ) {
                                            ButtonColorSubPageContent(
                                                button = effectiveButton,
                                                savedButton = currentSubPage.button,
                                                activeLayout = activeLayout,
                                                target = currentSubPage.target,
                                                accentColor = colors.accent,
                                                onColorOptionChanged = { option ->
                                                    val updatedDraft =
                                                        when (currentSubPage.target) {
                                                            ButtonColorTarget.TEXT -> effectiveButton.copy(buttonTextColor = option)
                                                            ButtonColorTarget.BORDER -> effectiveButton.copy(buttonBorderColor = option)
                                                            ButtonColorTarget.BG -> effectiveButton.copy(buttonBgColor = option)
                                                        }
                                                    buttonDraft = updatedDraft
                                                    MacroPadState.setPreviewButton(updatedDraft)
                                                },
                                                onOpenColorWheel = { title, breadcrumbs, initialColor, inFlightButton ->
                                                    buttonDraft = inFlightButton
                                                    MacroPadNavState.setStack(
                                                        subPageStack +
                                                            MacroPadSubPage.ColorWheel(
                                                                title = title,
                                                                breadcrumbs = breadcrumbs,
                                                                initialColor = initialColor,
                                                                section = EditorSection.BUTTONS,
                                                                onColorChange = { liveColor ->
                                                                    val option = ColorOption.Custom(liveColor.toArgb())
                                                                    val base = buttonDraft ?: inFlightButton
                                                                    val liveButton =
                                                                        when (currentSubPage.target) {
                                                                            ButtonColorTarget.TEXT -> {
                                                                                base.copy(
                                                                                    buttonTextColor = option,
                                                                                )
                                                                            }

                                                                            ButtonColorTarget.BORDER -> {
                                                                                base.copy(
                                                                                    buttonBorderColor = option,
                                                                                )
                                                                            }

                                                                            ButtonColorTarget.BG -> {
                                                                                base.copy(
                                                                                    buttonBgColor = option,
                                                                                )
                                                                            }
                                                                        }
                                                                    buttonDraft = liveButton
                                                                    MacroPadState.setPreviewButton(liveButton)
                                                                },
                                                            ),
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseKeyboardKey -> {
                                        val effectiveButton = currentSubPage.draftButton
                                        val currentKeyAction = effectiveButton.action as? PadAction.KeyboardKey
                                        val currentKeycode = currentKeyAction?.keycode ?: LinuxKeycodes.KEY_SPACE
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    stringResource(R.string.macropad_picker_visual_keyboard_title),
                                                ),
                                        ) {
                                            VisualKeyboardPicker(
                                                selectedKeycode = currentKeycode,
                                                accentColor = colors.accent,
                                                onSelectKey = { keycode, label ->
                                                    val newAction =
                                                        PadAction.KeyboardKey(
                                                            keycode = keycode,
                                                            label = label,
                                                            modifiers = currentKeyAction?.modifiers ?: emptyList(),
                                                        )
                                                    val updatedDraft = effectiveButton.copy(action = newAction)
                                                    buttonDraft = updatedDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updatedDraft)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseGamepadButton -> {
                                        val effectiveButton = currentSubPage.draftButton
                                        val currentBtnAction = effectiveButton.action as? PadAction.GamepadButton
                                        val slotIndex = currentSubPage.slotIndex
                                        val currentBtnCode =
                                            when (slotIndex) {
                                                1 -> currentBtnAction?.extraBtnCodes?.getOrNull(0) ?: -1
                                                2 -> currentBtnAction?.extraBtnCodes?.getOrNull(1) ?: -1
                                                3 -> currentBtnAction?.extraBtnCodes?.getOrNull(2) ?: -1
                                                else -> currentBtnAction?.btnCode ?: GamepadKeycodes.BTN_SOUTH
                                            }
                                        val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
                                        val slotTitle =
                                            when (slotIndex) {
                                                1 -> stringResource(R.string.macropad_picker_label_extra_1)
                                                2 -> stringResource(R.string.macropad_picker_label_extra_2)
                                                3 -> stringResource(R.string.macropad_picker_label_extra_3)
                                                else -> stringResource(R.string.macropad_picker_visual_gamepad_title)
                                            }
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    slotTitle,
                                                ),
                                        ) {
                                            VisualGamepadPicker(
                                                selectedBtnCode = currentBtnCode,
                                                accentColor = colors.accent,
                                                onSelectButton = { preset ->
                                                    val baseAction =
                                                        currentBtnAction
                                                            ?: PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
                                                    val newAction =
                                                        updateGamepadButtonSlot(
                                                            currentAction = baseAction,
                                                            slotIndex = slotIndex,
                                                            selectedCode = preset.code,
                                                            swapFaceButtons = swapFaceButtons,
                                                        )
                                                    val updatedDraft = effectiveButton.copy(action = newAction)
                                                    buttonDraft = updatedDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updatedDraft)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
                                                },
                                                onClear =
                                                    if (slotIndex in 1..3 && currentBtnCode != -1) {
                                                        {
                                                            val baseAction =
                                                                currentBtnAction
                                                                    ?: PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
                                                            val newAction =
                                                                updateGamepadButtonSlot(
                                                                    currentAction = baseAction,
                                                                    slotIndex = slotIndex,
                                                                    selectedCode = null,
                                                                    swapFaceButtons = swapFaceButtons,
                                                                )
                                                            val updatedDraft = effectiveButton.copy(action = newAction)
                                                            buttonDraft = updatedDraft
                                                            MacroPadNavState.setStack(
                                                                subPageStack.dropLast(1).map { subPage ->
                                                                    if (subPage is MacroPadSubPage.EditButton) {
                                                                        subPage.copy(draftButton = updatedDraft)
                                                                    } else {
                                                                        subPage
                                                                    }
                                                                },
                                                            )
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                modifier = Modifier.firstDeckItem(),
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseMouseAction -> {
                                        val effectiveButton = currentSubPage.draftButton
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    stringResource(R.string.macropad_picker_visual_mouse_title),
                                                ),
                                        ) {
                                            VisualMousePicker(
                                                currentAction = effectiveButton.action,
                                                accentColor = colors.accent,
                                                onSelectAction = { act ->
                                                    val updatedDraft = applyActionToDraftButton(effectiveButton, act)
                                                    buttonDraft = updatedDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updatedDraft)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseMirrorAction -> {
                                        val effectiveButton = currentSubPage.draftButton
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    stringResource(R.string.macropad_picker_mirror_title),
                                                ),
                                        ) {
                                            MirrorActionPickerSubPageContent(
                                                currentAction = effectiveButton.action,
                                                accentColor = colors.accent,
                                                onSelectAction = { act ->
                                                    val updatedDraft = applyActionToDraftButton(effectiveButton, act)
                                                    buttonDraft = updatedDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updatedDraft)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseOverlayAction -> {
                                        val effectiveButton = currentSubPage.draftButton
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    stringResource(R.string.macropad_picker_overlay_title),
                                                ),
                                        ) {
                                            OverlayActionPickerSubPageContent(
                                                currentAction = effectiveButton.action,
                                                accentColor = colors.accent,
                                                onSelectAction = { act ->
                                                    val updatedDraft = applyActionToDraftButton(effectiveButton, act)
                                                    buttonDraft = updatedDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updatedDraft)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseLayoutAction -> {
                                        val effectiveButton = currentSubPage.draftButton
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    stringResource(R.string.macropad_picker_layout_title),
                                                ),
                                        ) {
                                            LayoutActionPickerSubPageContent(
                                                currentAction = effectiveButton.action,
                                                accentColor = colors.accent,
                                                onSelectAction = { act ->
                                                    val updatedDraft = applyActionToDraftButton(effectiveButton, act)
                                                    buttonDraft = updatedDraft
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updatedDraft)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseIcon -> {
                                        val parentDraftButton =
                                            buttonDraft ?: subPageStack.filterIsInstance<MacroPadSubPage.EditButton>().lastOrNull()?.let {
                                                it.draftButton ?: it.button
                                            }
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    stringResource(R.string.macropad_icon_picker_title),
                                                ),
                                        ) {
                                            ChooseIconSubPageContent(
                                                selectedIcon = parentDraftButton?.iconName,
                                                accentColor = colors.accent,
                                                filled = true,
                                                onFilledChange = {},
                                                onSelect = { icon ->
                                                    val cur = buttonDraft ?: parentDraftButton
                                                    val updated = cur?.copy(iconName = icon)
                                                    buttonDraft = updated
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updated)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
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
                                                    MacroPadNavState.pop()
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseMacroAction -> {
                                        val effectiveButton = currentSubPage.draftButton
                                        GamepadDeck(
                                            breadcrumbs =
                                                listOf(
                                                    stringResource(R.string.macropad_editor_section_buttons),
                                                    effectiveButton.label.ifBlank {
                                                        stringResource(
                                                            if (currentSubPage.button != null) {
                                                                R.string.macropad_editor_section_button_settings
                                                            } else {
                                                                R.string.macropad_editor_add_button
                                                            },
                                                        )
                                                    },
                                                    stringResource(R.string.macropad_action_macro),
                                                ),
                                        ) {
                                            MacroActionPickerSubPageContent(
                                                currentAction = effectiveButton.action,
                                                accentColor = colors.accent,
                                                onSelectAction = { act ->
                                                    val updatedDraft = applyActionToDraftButton(effectiveButton, act)
                                                    MacroPadNavState.setStack(
                                                        subPageStack.dropLast(1).map { subPage ->
                                                            if (subPage is MacroPadSubPage.EditButton) {
                                                                subPage.copy(draftButton = updatedDraft)
                                                            } else {
                                                                subPage
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.ChooseMacroMode -> {
                                        val privdState by PrivdManager.state.collectAsState()
                                        val defaultMacroName = stringResource(R.string.macropad_macro_default_name)
                                        val existingMacroNames = profile.macros.map { it.name }

                                        fun createNewMacro(): Macro {
                                            val newMacroName =
                                                if (existingMacroNames.none { it.equals(defaultMacroName, ignoreCase = true) }) {
                                                    defaultMacroName
                                                } else {
                                                    var index = 2
                                                    while (existingMacroNames.any {
                                                            it.equals(
                                                                "$defaultMacroName ($index)",
                                                                ignoreCase = true,
                                                            )
                                                        }
                                                    ) {
                                                        index++
                                                    }
                                                    "$defaultMacroName ($index)"
                                                }
                                            return Macro(
                                                id = UUID.randomUUID().toString(),
                                                name = newMacroName,
                                                steps = emptyList(),
                                            )
                                        }

                                        fun applyNewMacroToStack(newMacro: Macro) {
                                            MacroPadNavState.setStack(
                                                subPageStack.dropLast(1) +
                                                    MacroPadSubPage.MacroTimeline(macro = null, draftMacro = newMacro),
                                            )
                                        }

                                        val breadcrumbs =
                                            listOf(
                                                stringResource(R.string.macropad_editor_manage_macros),
                                                stringResource(R.string.macropad_macro_create_title),
                                            )

                                        GamepadDeck(breadcrumbs = breadcrumbs) {
                                            ChooseMacroModeSubPageContent(
                                                onRecordGamepad = {
                                                    if (privdState != PrivdState.RUNNING) {
                                                        DialogToastManager.show(context.getString(R.string.privd_error_daemon_unreachable))
                                                        return@ChooseMacroModeSubPageContent
                                                    }
                                                    val newMacro = createNewMacro()
                                                    applyNewMacroToStack(newMacro)
                                                    AppStateManager.suspendCurrentAndDismiss()
                                                    PhysicalGamepadRecordingManager.startRecording()
                                                },
                                                onBuildManual = {
                                                    val newMacro = createNewMacro()
                                                    applyNewMacroToStack(newMacro)
                                                },
                                                onRecordTouchTap = {
                                                    val newMacro = createNewMacro()
                                                    applyNewMacroToStack(newMacro)
                                                    AppStateManager.suspendCurrentAndDismiss()
                                                    TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
                                                },
                                                onRecordTouchGesture = {
                                                    val newMacro = createNewMacro()
                                                    applyNewMacroToStack(newMacro)
                                                    AppStateManager.suspendCurrentAndDismiss()
                                                    TouchRecordingManager.requestRecording(TouchRecordingMode.GESTURE)
                                                },
                                            )
                                        }
                                    }

                                    is MacroPadSubPage.MacroTimeline -> {
                                        val macro =
                                            currentSubPage.effectiveMacro
                                                ?: profile?.macros?.firstOrNull { it.id == currentSubPage.macroId }
                                                ?: profiles.flatMap { it.macros }.firstOrNull { it.id == currentSubPage.macroId }
                                        val savedMacro = currentSubPage.macro
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
                                                    savedMacro = savedMacro,
                                                    accentColor = colors.accent,
                                                    onOpenManualSteps = { draftMacro ->
                                                        val updatedStack =
                                                            subPageStack.map { page ->
                                                                if (page is MacroPadSubPage.MacroTimeline &&
                                                                    page.macroId == draftMacro.id
                                                                ) {
                                                                    page.copy(draftMacro = draftMacro)
                                                                } else {
                                                                    page
                                                                }
                                                            } +
                                                                MacroPadSubPage.ManualMacroSteps(
                                                                    macro = currentSubPage.macro,
                                                                    draftMacro = draftMacro,
                                                                )
                                                        MacroPadNavState.setStack(updatedStack)
                                                    },
                                                    onDiscard = {
                                                        MacroPadNavState.pop()
                                                    },
                                                    onSave = { updatedMacro ->
                                                        if (savedMacro == null) {
                                                            MacroPadState.addMacro(updatedMacro)
                                                        } else {
                                                            MacroPadState.updateMacro(updatedMacro)
                                                        }
                                                        MacroPadNavState.pop()
                                                    },
                                                    onDelete = {
                                                        val deletedName = macro.name
                                                        if (savedMacro != null) {
                                                            MacroPadState.deleteMacro(macro.id)
                                                        }
                                                        MacroPadNavState.pop()
                                                        DialogToastManager.show(
                                                            context.getString(R.string.macropad_macro_deleted_toast, deletedName),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.ManualMacroSteps -> {
                                        val macro =
                                            currentSubPage.effectiveMacro
                                                ?: profile?.macros?.firstOrNull { it.id == currentSubPage.macroId }
                                                ?: profiles.flatMap { it.macros }.firstOrNull { it.id == currentSubPage.macroId }
                                        if (macro != null) {
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_manage_macros),
                                                        macro.name.ifBlank { stringResource(R.string.macropad_editor_open_timeline_title) },
                                                        stringResource(R.string.macropad_macro_manual_steps_title),
                                                    ),
                                            ) {
                                                ManualMacroStepsSubPageContent(
                                                    macro = macro,
                                                    accentColor = colors.accent,
                                                    onOpenAddStep = {
                                                        MacroPadNavState.setStack(
                                                            subPageStack +
                                                                MacroPadSubPage.MacroStepEdit(
                                                                    macro = currentSubPage.macro,
                                                                    draftMacro = macro,
                                                                    stepIndex = null,
                                                                ),
                                                        )
                                                    },
                                                    onOpenEditStep = { stepIdx ->
                                                        MacroPadNavState.setStack(
                                                            subPageStack +
                                                                MacroPadSubPage.MacroStepEdit(
                                                                    macro = currentSubPage.macro,
                                                                    draftMacro = macro,
                                                                    stepIndex = stepIdx,
                                                                ),
                                                        )
                                                    },
                                                    onOpenReorderSteps = {
                                                        MacroPadNavState.setStack(
                                                            subPageStack +
                                                                MacroPadSubPage.ReorderMacroSteps(
                                                                    macro = currentSubPage.macro,
                                                                    draftMacro = macro,
                                                                ),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.MacroStepEdit -> {
                                        val macro =
                                            currentSubPage.effectiveMacro
                                                ?: profile?.macros?.firstOrNull { it.id == currentSubPage.macroId }
                                                ?: profiles.flatMap { it.macros }.firstOrNull { it.id == currentSubPage.macroId }
                                        if (macro != null &&
                                            (currentSubPage.stepIndex == null || currentSubPage.stepIndex < macro.steps.size)
                                        ) {
                                            val step = currentSubPage.stepIndex?.let { macro.steps.getOrNull(it) }
                                            GamepadDeck(
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_manage_macros),
                                                        macro.name.ifBlank { stringResource(R.string.macropad_editor_open_timeline_title) },
                                                        stringResource(
                                                            if (step == null) {
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
                                                    stepIndex = currentSubPage.stepIndex,
                                                    accentColor = colors.accent,
                                                    suggestedStartTimeMs = macro.steps.totalDurationMs(),
                                                    initialShiftMode = ShiftMode.END_DELTA,
                                                    onConfirm = { newStep, shiftMode ->
                                                        val (updatedSteps, targetIndex) =
                                                            if (currentSubPage.stepIndex != null && step != null) {
                                                                applyShiftSubsequent(
                                                                    macro.steps,
                                                                    currentSubPage.stepIndex,
                                                                    step,
                                                                    newStep,
                                                                    shiftMode,
                                                                ) to currentSubPage.stepIndex
                                                            } else {
                                                                (macro.steps + newStep) to macro.steps.size
                                                            }
                                                        val updatedMacro = macro.copy(steps = updatedSteps)
                                                        val parentDepth = subPageStack.size - 1
                                                        MacroPadNavState.recordFocusedKey(parentDepth, "macro_manual_step_$targetIndex")
                                                        val updatedStack =
                                                            subPageStack.dropLast(1).map { page ->
                                                                when (page) {
                                                                    is MacroPadSubPage.MacroTimeline -> {
                                                                        page.copy(draftMacro = updatedMacro)
                                                                    }

                                                                    is MacroPadSubPage.ManualMacroSteps -> {
                                                                        page.copy(
                                                                            draftMacro = updatedMacro,
                                                                        )
                                                                    }

                                                                    else -> {
                                                                        page
                                                                    }
                                                                }
                                                            }
                                                        MacroPadNavState.setStack(updatedStack)
                                                        if (currentSubPage.macro != null) {
                                                            MacroPadState.updateMacro(updatedMacro)
                                                        }
                                                    },
                                                    onDiscard = { MacroPadNavState.pop() },
                                                    onDuplicate = { dupStep ->
                                                        val newStart = macro.steps.totalDurationMs()
                                                        val duplicated = dupStep.withStartTime(newStart)
                                                        val updatedMacro = macro.copy(steps = macro.steps + duplicated)
                                                        val parentDepth = subPageStack.size - 1
                                                        val newIndex = macro.steps.size
                                                        MacroPadNavState.recordFocusedKey(parentDepth, "macro_manual_step_$newIndex")
                                                        val updatedStack =
                                                            subPageStack.dropLast(1).map { page ->
                                                                when (page) {
                                                                    is MacroPadSubPage.MacroTimeline -> {
                                                                        page.copy(draftMacro = updatedMacro)
                                                                    }

                                                                    is MacroPadSubPage.ManualMacroSteps -> {
                                                                        page.copy(
                                                                            draftMacro = updatedMacro,
                                                                        )
                                                                    }

                                                                    else -> {
                                                                        page
                                                                    }
                                                                }
                                                            }
                                                        MacroPadNavState.setStack(updatedStack)
                                                        if (currentSubPage.macro != null) {
                                                            MacroPadState.updateMacro(updatedMacro)
                                                        }
                                                        DialogToastManager.show(
                                                            context.getString(R.string.macropad_macro_step_duplicate),
                                                        )
                                                    },
                                                    onDelete = {
                                                        if (currentSubPage.stepIndex != null) {
                                                            val updatedSteps =
                                                                macro.steps.filterIndexed { i, _ ->
                                                                    i !=
                                                                        currentSubPage.stepIndex
                                                                }
                                                            val updatedMacro = macro.copy(steps = updatedSteps)
                                                            val parentDepth = subPageStack.size - 1
                                                            if (updatedSteps.isNotEmpty()) {
                                                                val targetIndex =
                                                                    if (currentSubPage.stepIndex >= updatedSteps.size) {
                                                                        updatedSteps.size - 1
                                                                    } else {
                                                                        currentSubPage.stepIndex
                                                                    }
                                                                MacroPadNavState.recordFocusedKey(
                                                                    parentDepth,
                                                                    "macro_manual_step_$targetIndex",
                                                                )
                                                            } else {
                                                                MacroPadNavState.removeFocusedKey(parentDepth)
                                                            }
                                                            val updatedStack =
                                                                subPageStack.dropLast(1).map { page ->
                                                                    when (page) {
                                                                        is MacroPadSubPage.MacroTimeline -> {
                                                                            page.copy(
                                                                                draftMacro = updatedMacro,
                                                                            )
                                                                        }

                                                                        is MacroPadSubPage.ManualMacroSteps -> {
                                                                            page.copy(
                                                                                draftMacro = updatedMacro,
                                                                            )
                                                                        }

                                                                        else -> {
                                                                            page
                                                                        }
                                                                    }
                                                                }
                                                            MacroPadNavState.setStack(updatedStack)
                                                            if (currentSubPage.macro != null) {
                                                                MacroPadState.updateMacro(updatedMacro)
                                                            }
                                                            DialogToastManager.show(
                                                                context.getString(R.string.macropad_macro_step_delete),
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    is MacroPadSubPage.ReorderMacroSteps -> {
                                        val macro =
                                            currentSubPage.effectiveMacro
                                                ?: profile?.macros?.firstOrNull { it.id == currentSubPage.macroId }
                                                ?: profiles.flatMap { it.macros }.firstOrNull { it.id == currentSubPage.macroId }
                                        if (macro != null) {
                                            val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsState()
                                            GamepadReorderDeck(
                                                items = macro.steps,
                                                itemKey = { step -> "${step.startTimeMs}_${step.durationMs}_${step.hashCode()}" },
                                                itemTitle = { step ->
                                                    val stepIdx = macro.steps.indexOf(step)
                                                    "${stepIdx + 1}. ${stepTypeLabel(
                                                        step,
                                                        context,
                                                    )}: ${stepActionDescription(step, swapFaceButtons, context)}"
                                                },
                                                itemDescription = { step ->
                                                    context.getString(
                                                        R.string.macropad_macro_step_timing,
                                                        step.startTimeMs,
                                                        step.durationMs,
                                                    )
                                                },
                                                itemIcon = { step -> stepIcon(step) },
                                                onReorder = { reorderedSteps ->
                                                    val updatedMacro = macro.copy(steps = reorderedSteps)
                                                    val updatedStack =
                                                        subPageStack.map { page ->
                                                            when (page) {
                                                                is MacroPadSubPage.MacroTimeline -> page.copy(draftMacro = updatedMacro)
                                                                is MacroPadSubPage.ManualMacroSteps -> page.copy(draftMacro = updatedMacro)
                                                                is MacroPadSubPage.ReorderMacroSteps -> page.copy(draftMacro = updatedMacro)
                                                                else -> page
                                                            }
                                                        }
                                                    MacroPadNavState.setStack(updatedStack)
                                                    if (currentSubPage.macro != null) {
                                                        MacroPadState.updateMacro(updatedMacro)
                                                    }
                                                },
                                                breadcrumbs =
                                                    listOf(
                                                        stringResource(R.string.macropad_editor_manage_macros),
                                                        macro.name.ifBlank { stringResource(R.string.macropad_editor_open_timeline_title) },
                                                        stringResource(R.string.macropad_macro_reorder_steps_title),
                                                    ),
                                                emptyMessage = stringResource(R.string.macropad_macro_reorder_steps_empty),
                                            )
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
                                                showAlphaSlider = currentSubPage.showAlphaSlider,
                                                onColorChange = currentSubPage.onColorChange,
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
        icon = Icons.Rounded.Add,
        onClick = onNewProfile,
    )

    GamepadActionCard(
        title = stringResource(R.string.profile_settings_title),
        description = stringResource(R.string.macropad_editor_edit_profile_desc),
        icon = Icons.Rounded.Edit,
        onClick = onEditProfile,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_duplicate_profile),
        description = stringResource(R.string.macropad_editor_duplicate_profile_desc, activeProfile.name),
        icon = Icons.Rounded.ContentCopy,
        onClick = {
            onDuplicateProfile()
            scope.launch {
                try {
                    firstItemFocusRequester.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_reorder_profiles),
        description = stringResource(R.string.macropad_editor_reorder_profiles_desc),
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
        icon = Icons.Rounded.Add,
        onClick = onNewLayout,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_appearance_title),
        description = stringResource(R.string.macropad_editor_appearance_desc),
        icon = Icons.Rounded.Palette,
        onClick = onEditAppearance,
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_touchpad_title),
        description = stringResource(R.string.macropad_editor_touchpad_desc),
        icon = Icons.Rounded.Mouse,
        onClick = onEditTouchpad,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_duplicate_layout),
        description = stringResource(R.string.macropad_editor_duplicate_layout_desc),
        icon = Icons.Rounded.ContentCopy,
        onClick = {
            onDuplicateLayout()
            scope.launch {
                try {
                    firstItemFocusRequester.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        },
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_copy_profile_select),
        description = stringResource(R.string.macropad_editor_copy_layout_desc),
        icon = Icons.Rounded.Share,
        onClick = onCopyLayout,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_reorder_layouts),
        description = stringResource(R.string.macropad_editor_reorder_layouts_desc),
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
private fun ButtonsDeck(
    profile: PadProfile,
    layout: PadLayout?,
    accentColor: Color,
    onEditButtonPositions: () -> Unit,
    onAddButton: () -> Unit,
    onEditButton: (PadButton) -> Unit,
) {
    val colors = LocalAppColors.current
    val buttons = layout?.buttons ?: emptyList()
    val isEditingPositions by MacroPadState.isEditingButtonPositions.collectAsState()
    val gridMode by MacroPadState.gridMode.collectAsState()
    var isReordering by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    var movingItemKey by remember { mutableStateOf<Any?>(null) }
    val movingIndex = if (movingItemKey != null) buttons.indexOfFirst { it.id == movingItemKey } else -1

    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            if (layout != null && buttons.isNotEmpty()) {
                val fromButtonIdx = (from.index - MPE_BUTTON_HEADER_COUNT).coerceIn(0, buttons.lastIndex)
                val toButtonIdx = (to.index - MPE_BUTTON_HEADER_COUNT).coerceIn(0, buttons.lastIndex)
                if (fromButtonIdx != toButtonIdx) {
                    val mutable = layout.buttons.toMutableList()
                    mutable.add(toButtonIdx, mutable.removeAt(fromButtonIdx))
                    MacroPadState.updateLayout(layout.copy(buttons = mutable))
                }
            }
        }

    LaunchedEffect(movingItemKey, movingIndex) {
        if (movingItemKey != null && movingIndex >= 0) {
            lazyListState.animateScrollToItem(movingIndex + MPE_BUTTON_HEADER_COUNT)
        }
    }

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(MPE_DECK_SPACING),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            GamepadActionCard(
                title = stringResource(R.string.macropad_editor_edit_button_positions),
                description = stringResource(R.string.macropad_editor_edit_button_positions_card_desc),
                icon = Icons.Rounded.OpenWith,
                onClick = onEditButtonPositions,
                modifier = Modifier.firstDeckItem(),
                onFocusChanged = { if (it) MacroPadState.setSelectedButtonId(null) },
            )
        }

        item {
            val gridModes = listOf(GridMode.OFF, GridMode.RECTANGULAR, GridMode.RADIAL)
            val gridIdx = gridModes.indexOf(gridMode).coerceAtLeast(0)
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
                onPrevious = { MacroPadState.setGridMode(gridModes[(gridIdx - 1 + gridModes.size) % gridModes.size]) },
                onNext = { MacroPadState.setGridMode(gridModes[(gridIdx + 1) % gridModes.size]) },
                onFocusChanged = { if (it) MacroPadState.setSelectedButtonId(null) },
            )
        }

        item {
            GamepadActionCard(
                title = stringResource(R.string.macropad_editor_add_button),
                description = stringResource(R.string.macropad_editor_create_button_desc),
                icon = Icons.Rounded.Add,
                onClick = onAddButton,
                onFocusChanged = { if (it) MacroPadState.setSelectedButtonId(null) },
            )
        }

        item {
            GamepadSectionHeader(
                text = stringResource(R.string.macropad_editor_manage_buttons),
                color = accentColor,
            )
        }

        item {
            GamepadToggleCard(
                title = stringResource(R.string.macropad_editor_reorder_buttons),
                description =
                    if (isReordering) {
                        stringResource(R.string.macropad_editor_reorder_buttons_enabled_desc)
                    } else {
                        stringResource(R.string.macropad_editor_reorder_buttons_disabled_desc)
                    },
                checked = isReordering,
                icon = Icons.Rounded.SwapVert,
                onCheckedChange = {
                    isReordering = it
                    if (!it) movingItemKey = null
                },
                onFocusChanged = { if (it) MacroPadState.setSelectedButtonId(null) },
            )
        }

        if (buttons.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.macropad_editor_no_buttons_in_layout),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = MPE_EMPTY_PADDING_V),
                )
            }
        } else if (!isReordering) {
            items(buttons, key = { it.id }) { btn ->
                val isTrackpoint = btn.action is PadAction.TrackpointMove
                val hapticLabel =
                    when (btn.hapticStrength) {
                        HapticStrength.OFF -> stringResource(R.string.macropad_haptic_off)
                        HapticStrength.LIGHT -> stringResource(R.string.macropad_haptic_light)
                        HapticStrength.MEDIUM -> stringResource(R.string.macropad_haptic_medium)
                        HapticStrength.STRONG -> stringResource(R.string.macropad_haptic_strong)
                        HapticStrength.CUSTOM -> stringResource(R.string.macropad_haptic_custom)
                    }
                val desc =
                    if (isTrackpoint) {
                        val sizeLabel =
                            when ((btn.action as PadAction.TrackpointMove).size) {
                                TrackpointSize.SMALL -> stringResource(R.string.macropad_trackpoint_size_small)
                                TrackpointSize.MEDIUM -> stringResource(R.string.macropad_trackpoint_size_medium)
                                TrackpointSize.LARGE -> stringResource(R.string.macropad_trackpoint_size_large)
                            }
                        listOf(sizeLabel, hapticLabel).joinToString(" • ")
                    } else {
                        val actionLabel = btn.action.displayLabel()
                        val sizeLabel =
                            if (btn.action !is PadAction.ScrollWheel) {
                                "${btn.buttonSize.cols}×${btn.buttonSize.rows}"
                            } else {
                                null
                            }
                        listOfNotNull(actionLabel, sizeLabel, hapticLabel).joinToString(" • ")
                    }

                GamepadActionCard(
                    title = btn.label.ifBlank { btn.action.displayLabel() },
                    description = desc,
                    icon = btn.action.toCategory().icon(),
                    onClick = { onEditButton(btn) },
                    onFocusChanged = { isFocused ->
                        if (isFocused) {
                            MacroPadState.setSelectedButtonId(btn.id)
                        }
                    },
                )
            }
        } else {
            itemsIndexed(buttons, key = { _, btn -> btn.id }) { index, btn ->
                val key = btn.id
                ReorderableItem(reorderState, key = key) { isDragging ->
                    val isMoving = movingItemKey == key
                    val isTrackpoint = btn.action is PadAction.TrackpointMove
                    val desc =
                        if (isTrackpoint) {
                            stringResource(R.string.macropad_action_trackpoint)
                        } else {
                            val actionLabel = btn.action.displayLabel()
                            val sizeLabel =
                                if (btn.action !is PadAction.ScrollWheel) {
                                    "${btn.buttonSize.cols}×${btn.buttonSize.rows}"
                                } else {
                                    null
                                }
                            listOfNotNull(actionLabel, sizeLabel).joinToString(" • ")
                        }

                    GamepadReorderCard(
                        title = btn.label.ifBlank { btn.action.displayLabel() },
                        description = desc,
                        icon = btn.action.toCategory().icon(),
                        index = index,
                        totalCount = buttons.size,
                        isMoving = isMoving,
                        isDragging = isDragging,
                        onToggleMoving = {
                            movingItemKey = if (isMoving) null else key
                        },
                        onMoveUp = {
                            if (index > 0 && layout != null) {
                                val mutable = buttons.toMutableList()
                                java.util.Collections.swap(mutable, index, index - 1)
                                MacroPadState.updateLayout(layout.copy(buttons = mutable))
                            }
                        },
                        onMoveDown = {
                            if (index < buttons.size - 1 && layout != null) {
                                val mutable = buttons.toMutableList()
                                java.util.Collections.swap(mutable, index, index + 1)
                                MacroPadState.updateLayout(layout.copy(buttons = mutable))
                            }
                        },
                        dragHandleModifier = Modifier.draggableHandle(),
                        itemKey = key,
                        onFocusChanged = { isFocused ->
                            if (isFocused || isMoving) {
                                MacroPadState.setSelectedButtonId(btn.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditButtonPositionsSubPageContent(
    layout: PadLayout?,
    accentColor: Color,
) {
    val colors = LocalAppColors.current
    val buttons = layout?.buttons ?: emptyList()
    val coroutineScope = rememberCoroutineScope()
    val selectedButtonId by MacroPadState.selectedButtonId.collectAsState()
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var movingButtonId by remember { mutableStateOf<String?>(null) }
    var activeRepeatJob by remember { mutableStateOf<Job?>(null) }
    var activeDirectionKey by remember { mutableIntStateOf(0) }

    fun stopMovingImmediate() {
        activeRepeatJob?.cancel()
        activeRepeatJob = null
        activeDirectionKey = 0
    }

    // Intercept system back gesture/button when precision moving
    BackHandler(enabled = movingButtonId != null) {
        stopMovingImmediate()
        movingButtonId = null
    }

    LaunchedEffect(buttons) {
        if (selectedButtonId == null && buttons.isNotEmpty()) {
            MacroPadState.setSelectedButtonId(buttons.first().id)
        }
    }

    LaunchedEffect(selectedButtonId) {
        val targetId = selectedButtonId ?: return@LaunchedEffect
        if (movingButtonId != null && movingButtonId != targetId) {
            stopMovingImmediate()
            movingButtonId = null
        }
        try {
            cardRequesters[targetId]?.requestFocus()
        } catch (_: IllegalStateException) {
            // Focus requester unattached
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopMovingImmediate()
            MacroPadState.setSelectedButtonId(null)
        }
    }

    fun moveButton(
        btnId: String,
        dx: Int,
        dy: Int,
    ) {
        val currentLayout = MacroPadState.activeLayout.value ?: return
        val targetBtn = currentLayout.buttons.firstOrNull { it.id == btnId } ?: return
        val stepX = 1f / MPE_CANVAS_WIDTH_PX
        val stepY = 1f / MPE_CANVAS_HEIGHT_PX
        val newX = (targetBtn.posX + dx * stepX).coerceIn(MPE_EDGE_MARGIN, 1f - MPE_EDGE_MARGIN)
        val newY = (targetBtn.posY + dy * stepY).coerceIn(MPE_EDGE_MARGIN, 1f - MPE_EDGE_MARGIN)
        if (newX != targetBtn.posX || newY != targetBtn.posY) {
            val updated =
                currentLayout.buttons.map {
                    if (it.id == btnId) it.copy(posX = newX, posY = newY) else it
                }
            MacroPadState.updateLayout(currentLayout.copy(buttons = updated))
        }
    }

    fun startMoving(
        btnId: String,
        keyCode: Int,
        dx: Int,
        dy: Int,
    ) {
        if (activeDirectionKey == keyCode && activeRepeatJob?.isActive == true) {
            return
        }
        activeRepeatJob?.cancel()
        activeDirectionKey = keyCode
        moveButton(btnId, dx, dy)
        activeRepeatJob =
            coroutineScope.launch {
                delay(MPE_MOVE_INITIAL_DELAY_MS)
                var delayMs = MPE_MOVE_START_DELAY_MS
                while (isActive && activeDirectionKey == keyCode) {
                    moveButton(btnId, dx, dy)
                    delay(delayMs)
                    delayMs = max(MPE_MOVE_MIN_DELAY_MS, (delayMs * MPE_MOVE_ACCEL_FACTOR).toLong())
                }
            }
    }

    fun stopMoving(keyCode: Int) {
        if (activeDirectionKey == keyCode) {
            stopMovingImmediate()
        }
    }

    // Non-highlightable Info Box
    GamepadInfoBox(
        text = stringResource(R.string.macropad_editor_move_buttons_info),
        iconTint = accentColor,
    )

    if (buttons.isEmpty()) {
        Text(
            text = stringResource(R.string.macropad_editor_no_buttons_in_layout),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = MPE_EMPTY_PADDING_V),
        )
    } else {
        buttons.forEachIndexed { index, btn ->
            val cardRequester = remember(btn.id) { FocusRequester() }
            DisposableEffect(btn.id) {
                cardRequesters[btn.id] = cardRequester
                onDispose {
                    cardRequesters.remove(btn.id)
                }
            }
            val isMoving = movingButtonId == btn.id
            val isTrackpoint = btn.action is PadAction.TrackpointMove
            val hapticLabel =
                when (btn.hapticStrength) {
                    HapticStrength.OFF -> stringResource(R.string.macropad_haptic_off)
                    HapticStrength.LIGHT -> stringResource(R.string.macropad_haptic_light)
                    HapticStrength.MEDIUM -> stringResource(R.string.macropad_haptic_medium)
                    HapticStrength.STRONG -> stringResource(R.string.macropad_haptic_strong)
                    HapticStrength.CUSTOM -> stringResource(R.string.macropad_haptic_custom)
                }
            val desc =
                if (isTrackpoint) {
                    val sizeLabel =
                        when ((btn.action as PadAction.TrackpointMove).size) {
                            TrackpointSize.SMALL -> stringResource(R.string.macropad_trackpoint_size_small)
                            TrackpointSize.MEDIUM -> stringResource(R.string.macropad_trackpoint_size_medium)
                            TrackpointSize.LARGE -> stringResource(R.string.macropad_trackpoint_size_large)
                        }
                    listOf(sizeLabel, hapticLabel).joinToString(" • ")
                } else {
                    val actionLabel = btn.action.displayLabel()
                    val sizeLabel =
                        if (btn.action !is PadAction.ScrollWheel) {
                            "${btn.buttonSize.cols}×${btn.buttonSize.rows}"
                        } else {
                            null
                        }
                    listOfNotNull(actionLabel, sizeLabel, hapticLabel).joinToString(" • ")
                }

            GamepadFocusCard(
                cardFocusRequester = cardRequester,
                onClick = {
                    if (isMoving) {
                        stopMovingImmediate()
                        movingButtonId = null
                    } else {
                        movingButtonId = btn.id
                        MacroPadState.setSelectedButtonId(btn.id)
                    }
                },
                itemKey = btn.id,
                modifier = Modifier.firstDeckItem(index == 0),
                isAdjusting = isMoving,
                onFocusChanged = { isFocused ->
                    if (isFocused) {
                        if (movingButtonId != null && movingButtonId != btn.id) {
                            stopMovingImmediate()
                            movingButtonId = null
                        }
                        MacroPadState.setSelectedButtonId(btn.id)
                    } else if (movingButtonId == btn.id) {
                        stopMovingImmediate()
                        movingButtonId = null
                    }
                },
                onCustomKeyEvent = { keyEvent ->
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    if (isMoving) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    startMoving(btn.id, keyCode, 0, -1)
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    startMoving(btn.id, keyCode, 0, 1)
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    startMoving(btn.id, keyCode, -1, 0)
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    startMoving(btn.id, keyCode, 1, 0)
                                    true
                                }

                                KeyEvent.KEYCODE_BUTTON_B,
                                KeyEvent.KEYCODE_BACK,
                                KeyEvent.KEYCODE_ESCAPE,
                                KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                -> {
                                    stopMovingImmediate()
                                    movingButtonId = null
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                -> {
                                    stopMoving(keyCode)
                                    true
                                }

                                KeyEvent.KEYCODE_BUTTON_B,
                                KeyEvent.KEYCODE_BACK,
                                KeyEvent.KEYCODE_ESCAPE,
                                KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                -> {
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
            ) { isFocused ->
                GamepadCardRow(
                    title = btn.label.ifBlank { btn.action.displayLabel() },
                    description = desc,
                    icon = btn.action.toCategory().icon(),
                    trailingContent = {
                        if (isMoving) {
                            GamepadPill(
                                text = stringResource(R.string.gamepad_action_moving),
                                isAccent = true,
                            )
                        } else {
                            GamepadPill(
                                text = stringResource(R.string.gamepad_action_move),
                                isHighlighted = isFocused,
                            )
                        }
                    },
                )
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
        icon = Icons.Rounded.Add,
        onClick = onNewMacro,
        modifier = Modifier.firstDeckItem(),
    )

    val macros = profile.macros
    if (macros.isEmpty()) {
        GamepadInfoBox(
            text = stringResource(R.string.macropad_editor_no_macros_desc),
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
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                onClick = { onEditMacro(macro) },
            )
        }
    }
}
