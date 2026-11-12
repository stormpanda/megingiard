package com.stormpanda.megingiard.gamefocus

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.accessibility.AccessibilityManager
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.IcoImageExtractor
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager
import com.stormpanda.megingiard.catalog.LetterNavigationHelper
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.gamefocus.domain.GalleryConfig
import com.stormpanda.megingiard.gamefocus.viewmodel.FocusTopLauncherViewModel
import com.stormpanda.megingiard.math.floorMod
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.ExpandableActionsMenu
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MaterialSymbol
import com.stormpanda.megingiard.ui.VerticalRollingCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "FocusTopLauncherScreen"

private val FTL_POSTER_CORNER_RADIUS = 16.dp
private val FTL_POSTER_WIDTH = 158.dp
private val FTL_POSTER_HEIGHT = 237.dp // 2:3 aspect ratio
private val FTL_POSTER_SPACING = 12.dp
private val FTL_ICON_SIZE = 72.dp
private val FTL_GALLERY_TOP_OFFSET = 10.dp
private val FTL_TITLE_GAP = 25.dp
private val FTL_ROM_ICON_CORNER_RADIUS = 14.dp
private val FTL_ROM_ICON_SHAPE = RoundedCornerShape(FTL_ROM_ICON_CORNER_RADIUS)
private val FTL_ROM_LOGO_HORIZONTAL_PADDING = 4.dp
private const val FTL_ROM_LOGO_WIDTH_FRACTION = 0.85f
private const val FTL_SQUARE_ASPECT_RATIO = 1f
private val FTL_FALLBACK_ICON_SIZE = 48.dp
private val FTL_BADGE_PADDING = 10.dp
private val FTL_BADGE_ICON_SIZE = 22.dp
private const val FTL_BACKGROUND_COLOR_DEBOUNCE_MS = 220L
private const val FTL_BACKGROUND_COLOR_ANIM_MS = 500
private const val FTL_LETTER_NAV_DEBOUNCE_MS = 500L
private const val FTL_LIBRARY_TRANSITION_MS = 350
private const val FTL_BG_PRIMARY_ALPHA = 0.20f
private const val FTL_BG_SECONDARY_ALPHA = 0.10f

private const val FTL_HIDDEN_BADGE_ALPHA = 1.0f
private const val FTL_VISIBLE_BADGE_ALPHA = 0.0f
private const val FTL_HIDE_ANIMATION_DURATION_MS = 300
private const val FTL_CAROUSEL_ANIMATION_DURATION_MS = 120
private const val FTL_TITLE_OVERLAY_Z_INDEX = 1f

private val FTL_CAROUSEL_HEIGHT = 310.dp
private val FTL_LETTER_NAV_PADDING = 40.dp
private val FTL_CATEGORY_HEADER_PADDING_START = 24.dp
private val FTL_CATEGORY_HEADER_PADDING_TOP = 16.dp
private val FTL_NAV_PADDING_END = 12.dp
private val FTL_NAV_PADDING_TOP = 12.dp
private val FTL_BOTTOM_BAR_PADDING_START = 12.dp
private val FTL_BOTTOM_BAR_PADDING_END = 12.dp
private val FTL_BOTTOM_BAR_PADDING_BOTTOM = 4.dp
private val FTL_BUTTON_GAP = 2.dp
private val FTL_SPLITSCREEN_BADGE_SIZE = 14.dp
private val FTL_SUBTITLE_GAP = 2.dp
private val FTL_BADGE_TEXT_GAP = 4.dp
private const val FTL_PROMPT_ANIM_DURATION_MS = 200
private const val FTL_PROMPT_SLIDE_DIVISOR = 2

@Stable
private class LetterNavigationOverlayState {
    var isActive by mutableStateOf(false)
    var selectedIndex by mutableIntStateOf(0)
}

@Composable
fun FocusTopLauncherScreen(
    viewModel: FocusTopLauncherViewModel,
    allApps: List<InstalledAppInfo> = emptyList(),
    favoritesSet: Set<String> = emptySet(),
    hiddenSet: Set<String> = emptySet(),
    lastUsed: List<String> = emptyList(),
    collectionMembers: Map<String, Set<String>> = emptyMap(),
    categories: List<GameFocusCategory> = GameFocusCategory.builtIns,
    allGalleryCategories: List<GameFocusCategory> = emptyList(),
    libraryTabs: List<LibraryTab> = listOf(LibraryTab.GAMES, LibraryTab.APPS),
    galleryConfig: GalleryConfig = GalleryConfig(),
    pairedApps: Map<String, String> = emptyMap(),
    availableAndroidApps: List<InstalledAppInfo> = emptyList(),
    onAppClickTop: (InstalledAppInfo) -> Unit = {},
    onAppClickBottom: (InstalledAppInfo) -> Unit = {},
    onToggleFavorite: (InstalledAppInfo) -> Unit = {},
    onToggleHidden: (InstalledAppInfo) -> Unit = {},
    onOpenAppInfo: (InstalledAppInfo) -> Unit = {},
    onUninstallApp: (InstalledAppInfo) -> Unit = {},
    onChangeCore: (CustomRomFolder) -> Unit = {},
    onAddRomFolder: () -> Unit = {},
    onRemoveRomFolder: (CustomRomFolder) -> Unit = {},
    onConfirmNewlyAddedFolderCore: (CustomRomFolder, String?) -> Unit = { _, _ -> },
    onOpenPairingDialog: (InstalledAppInfo) -> Unit = {},
    onRemovePair: (InstalledAppInfo) -> Unit = {},
    onConfirmPairApp: (InstalledAppInfo, InstalledAppInfo) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLibraryOpen by viewModel.isLibraryOpen.collectAsState()
    val isMainOptionsMenuExpanded by viewModel.isMainOptionsMenuExpanded.collectAsState()
    val newlyAddedFolder by viewModel.newlyAddedFolder.collectAsState()
    val coreChooserDialogSelectedIndex by viewModel.coreChooserDialogSelectedIndex.collectAsState()
    val confirmCoreChooserTrigger by viewModel.confirmCoreChooserTrigger.collectAsState()
    val editingAppInfo by viewModel.editingAppInfo.collectAsState()
    val isManageGalleriesOpen by viewModel.isManageGalleriesOpen.collectAsState()
    val pairingTargetApp by viewModel.pairingTargetApp.collectAsState()
    val pairingFocusedIndex by viewModel.pairingFocusedIndex.collectAsState()
    val areButtonPromptsVisible by viewModel.areButtonPromptsVisible.collectAsState()

    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val isScreenReaderActive = rememberScreenReaderActive(context)
    val apiKey by remember(context) { MegingiardSettingsClient.observeSteamGridDbApiToken(context) }
        .collectAsState(initial = "")

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            InstalledAppsManager.triggerSteamGridDbScraping(context, apiKey)
        }
    }

    val availableAppsByPackage = remember(availableAndroidApps) { availableAndroidApps.associateBy { it.packageName } }

    val allKnownCategories =
        remember(categories, allGalleryCategories, selectedCategory) {
            buildList {
                addAll(GameFocusCategory.builtIns)
                addAll(categories)
                addAll(allGalleryCategories)
                add(selectedCategory)
            }.distinctBy { it.id }
        }

    // Memoize filtered category lists once per catalog/filter change so PagerState.pageCount
    // (invoked ~100x per animation frame by HorizontalPager) is an O(1) map lookup with zero allocations.
    val appsByCategoryId =
        remember(allKnownCategories, allApps, favoritesSet, hiddenSet, lastUsed, collectionMembers) {
            GameFocusCategory.buildAppsByCategoryId(
                categories = allKnownCategories,
                allApps = allApps,
                favorites = favoritesSet,
                hidden = hiddenSet,
                lastUsed = lastUsed,
                collectionMembers = collectionMembers,
            )
        }
    val appsByCategoryIdState = rememberUpdatedState(appsByCategoryId)

    fun getAppsForCategory(cat: GameFocusCategory): List<InstalledAppInfo> =
        appsByCategoryId[cat.id] ?: cat.filterApps(allApps, favoritesSet, hiddenSet, lastUsed, collectionMembers)

    val categoryApps = getAppsForCategory(selectedCategory)
    val uniqueLetters = remember(categoryApps) { LetterNavigationHelper.getUniqueStartingLetters(categoryApps) }

    // Plain non-Compose map: only read on category switch, so updating focused package memory never triggers recomposition.
    val lastHighlightedPackages = remember { HashMap<String, String>() }

    val gamesPagerState = rememberPagerState(initialPage = 0) { appsByCategoryIdState.value[GameFocusCategory.GAMES.id]?.size ?: 0 }
    val appsPagerState = rememberPagerState(initialPage = 0) { appsByCategoryIdState.value[GameFocusCategory.APPS.id]?.size ?: 0 }
    val favoritesPagerState =
        rememberPagerState(initialPage = 0) { appsByCategoryIdState.value[GameFocusCategory.FAVORITES.id]?.size ?: 0 }
    val lastUsedPagerState =
        rememberPagerState(initialPage = 0) { appsByCategoryIdState.value[GameFocusCategory.LAST_USED.id]?.size ?: 0 }

    val romPagerStates = remember { mutableMapOf<String, PagerState>() }
    val activeGalleryIds =
        remember(allKnownCategories) {
            allKnownCategories.mapTo(HashSet(allKnownCategories.size)) { it.id }
        }
    romPagerStates.keys.retainAll(activeGalleryIds)

    fun getPagerStateForCategory(cat: GameFocusCategory): PagerState =
        when (cat) {
            GameFocusCategory.GAMES -> {
                gamesPagerState
            }

            GameFocusCategory.APPS -> {
                appsPagerState
            }

            GameFocusCategory.FAVORITES -> {
                favoritesPagerState
            }

            GameFocusCategory.LAST_USED -> {
                lastUsedPagerState
            }

            is GameFocusCategory.RomSystem,
            is GameFocusCategory.CustomCollection,
            -> {
                val categoryId = cat.id
                romPagerStates.getOrPut(categoryId) {
                    RomPagerState(0, 0f) { appsByCategoryIdState.value[categoryId]?.size ?: 0 }
                }
            }
        }

    val activePagerState = getPagerStateForCategory(selectedCategory)

    // Clamp active pager state if list shrinks (e.g. app hidden)
    LaunchedEffect(categoryApps) {
        if (categoryApps.isNotEmpty() && activePagerState.currentPage >= categoryApps.size) {
            val safeIndex = (categoryApps.size - 1).coerceAtLeast(0)
            activePagerState.scrollToPage(safeIndex)
        }
    }

    // Sync active pager state with package memory when category changes
    LaunchedEffect(selectedCategory) {
        val lastPkg = lastHighlightedPackages[selectedCategory.id]
        val targetIndex =
            if (lastPkg != null) {
                val found = categoryApps.indexOfFirst { it.packageName == lastPkg }
                if (found >= 0) found else 0
            } else {
                0
            }
        if (categoryApps.isNotEmpty() && activePagerState.currentPage != targetIndex) {
            val safeIndex = targetIndex.coerceIn(0, categoryApps.size - 1)
            AppLog.d(TAG, "Category switched to ${selectedCategory.id} -> scrolling to remembered index $safeIndex (package=$lastPkg)")
            activePagerState.scrollToPage(safeIndex)
        }
    }

    val letterNavState = remember { LetterNavigationOverlayState() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(categoryApps, isLibraryOpen) {
        if (!isLibraryOpen && categoryApps.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }

    // Derived focused app state: never read in FocusTopLauncherScreen's own scope so page crossings
    // only recompose the small leaf Composables that display or sync the focused app.
    val currentAppState =
        remember(activePagerState, categoryApps) {
            derivedStateOf {
                if (categoryApps.isEmpty()) {
                    null
                } else {
                    categoryApps.getOrNull(activePagerState.currentPage.coerceIn(0, categoryApps.lastIndex))
                }
            }
        }

    GalleryNavigationEffects(
        viewModel = viewModel,
        selectedCategory = selectedCategory,
        editingAppInfo = editingAppInfo,
        categoryApps = categoryApps,
        uniqueLetters = uniqueLetters,
        activePagerState = activePagerState,
        currentAppState = currentAppState,
        letterNavState = letterNavState,
        lastHighlightedPackages = lastHighlightedPackages,
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = appColors.appBackground,
    ) {
        // Prune 120 Hz scrolling semantics when only non-screen-reader services (e.g. Companion's
        // MegingiardAccessibilityService or OEM foreground monitors) are enabled, avoiding ~8 ms/frame
        // full-tree bounds walks and Binder IPC while keeping overlays/dialogs fully accessible.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isScreenReaderActive) {
                            Modifier
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    ),
        ) {
            AnimatedContent(
                targetState = isLibraryOpen,
                transitionSpec = {
                    if (targetState) {
                        (
                            slideInHorizontally(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS), initialOffsetX = { it }) +
                                fadeIn(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS))
                        ).togetherWith(
                            slideOutHorizontally(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS), targetOffsetX = { -it }) +
                                fadeOut(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS)),
                        )
                    } else {
                        (
                            slideInHorizontally(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS), initialOffsetX = { -it }) +
                                fadeIn(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS))
                        ).togetherWith(
                            slideOutHorizontally(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS), targetOffsetX = { it }) +
                                fadeOut(animationSpec = tween(FTL_LIBRARY_TRANSITION_MS)),
                        )
                    }
                },
                label = "LibrarySlideTransition",
                modifier = Modifier.fillMaxSize(),
            ) { showLibrary ->
                if (showLibrary) {
                    FocusLibraryHost(
                        viewModel = viewModel,
                        allApps = allApps,
                        hiddenSet = hiddenSet,
                        libraryTabs = libraryTabs,
                        enabled = isLibraryOpen && editingAppInfo == null && pairingTargetApp == null && !isManageGalleriesOpen,
                        areButtonPromptsVisible = areButtonPromptsVisible,
                        onToggleHidden = onToggleHidden,
                        onOpenAppInfo = onOpenAppInfo,
                        onUninstallApp = onUninstallApp,
                        onChangeCore = onChangeCore,
                        onAddRomFolder = onAddRomFolder,
                        onRemoveRomFolder = onRemoveRomFolder,
                        onAppClickTop = onAppClickTop,
                        onAppClickBottom = onAppClickBottom,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DynamicLauncherBackground(
                            activePagerState = activePagerState,
                            categoryApps = categoryApps,
                            appColors = appColors,
                        )

                        if (isMainOptionsMenuExpanded) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures {
                                                viewModel.setMainOptionsMenuExpanded(false)
                                            }
                                        },
                            )
                        }

                        // Plane 1: Full-Screen Gallery & App Title Plane
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    val direction = if (initialState.next(categories) == targetState) 1 else -1
                                    (slideInVertically { height -> height * direction } + fadeIn())
                                        .togetherWith(slideOutVertically { height -> -height * direction } + fadeOut())
                                },
                                label = "CarouselCategoryTransition",
                                modifier = Modifier.fillMaxSize(),
                            ) { category ->
                                val currentCategoryApps = getAppsForCategory(category)
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (currentCategoryApps.isEmpty()) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .height(FTL_CAROUSEL_HEIGHT)
                                                    .fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text =
                                                    when (category) {
                                                        GameFocusCategory.GAMES -> {
                                                            stringResource(R.string.gamefocus_no_games)
                                                        }

                                                        GameFocusCategory.APPS -> {
                                                            stringResource(R.string.gamefocus_no_apps_category)
                                                        }

                                                        GameFocusCategory.FAVORITES -> {
                                                            stringResource(R.string.gamefocus_no_favorites)
                                                        }

                                                        GameFocusCategory.LAST_USED -> {
                                                            stringResource(R.string.gamefocus_no_last_used)
                                                        }

                                                        is GameFocusCategory.CustomCollection -> {
                                                            stringResource(
                                                                R.string.gamefocus_no_collection_items,
                                                            )
                                                        }

                                                        else -> {
                                                            stringResource(R.string.focus_launcher_no_apps)
                                                        }
                                                    },
                                                style = MaterialTheme.typography.titleMedium.copy(color = appColors.onSurfaceSecondary),
                                            )
                                        }
                                    } else {
                                        val categoryPagerState = getPagerStateForCategory(category)
                                        val currentOnAppClickTop by rememberUpdatedState(onAppClickTop)
                                        val keyProvider: (Int) -> Any =
                                            remember(category.id, currentCategoryApps) {
                                                { page ->
                                                    "${category.id}_${currentCategoryApps.getOrNull(page)?.packageName ?: page}"
                                                }
                                            }
                                        val handleCarouselItemClick: (Int) -> Unit =
                                            remember(currentCategoryApps) {
                                                { actualIndex ->
                                                    val appInfo = currentCategoryApps.getOrNull(actualIndex)
                                                    if (appInfo != null) currentOnAppClickTop(appInfo)
                                                }
                                            }
                                        val isItemHiddenProvider: (Int) -> Boolean =
                                            remember(currentCategoryApps, hiddenSet) {
                                                { actualIndex ->
                                                    currentCategoryApps.getOrNull(actualIndex)?.let {
                                                        hiddenSet.contains(it.packageName)
                                                    } ?: false
                                                }
                                            }

                                        Column(
                                            modifier = Modifier.padding(top = FTL_GALLERY_TOP_OFFSET),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            HorizontalPosterCarousel(
                                                itemCount = currentCategoryApps.size,
                                                pagerState = categoryPagerState,
                                                modifier =
                                                    if (category == selectedCategory) {
                                                        Modifier.focusRequester(focusRequester)
                                                    } else {
                                                        Modifier
                                                    },
                                                key = keyProvider,
                                                onItemClick = handleCarouselItemClick,
                                                posterWidth = FTL_POSTER_WIDTH,
                                                posterHeight = FTL_POSTER_HEIGHT,
                                                posterSpacing = FTL_POSTER_SPACING,
                                                carouselHeight = FTL_CAROUSEL_HEIGHT,
                                                posterCornerRadius = FTL_POSTER_CORNER_RADIUS,
                                                cardBackgroundColor = { actualIndex, isSelected ->
                                                    val appInfo = currentCategoryApps.getOrNull(actualIndex)
                                                    if (appInfo == null) {
                                                        if (isSelected) appColors.surfaceVariant else appColors.surface
                                                    } else {
                                                        val palette = rememberAppPalette(appInfo)
                                                        if (palette != null && palette.isExtracted) {
                                                            palette.darkenedPrimaryColor
                                                        } else if (isSelected) {
                                                            appColors.surfaceVariant
                                                        } else {
                                                            appColors.surface
                                                        }
                                                    }
                                                },
                                                isHidden = isItemHiddenProvider,
                                            ) { actualIndex, _ ->
                                                val appInfo = currentCategoryApps[actualIndex]
                                                PosterCardContent(
                                                    appInfo = appInfo,
                                                    isFavorite = favoritesSet.contains(appInfo.packageName),
                                                    isHidden = hiddenSet.contains(appInfo.packageName),
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(FTL_TITLE_GAP))

                                            val categoryCurrentAppState =
                                                remember(categoryPagerState, currentCategoryApps) {
                                                    derivedStateOf {
                                                        if (currentCategoryApps.isEmpty()) {
                                                            null
                                                        } else {
                                                            currentCategoryApps.getOrNull(
                                                                categoryPagerState.currentPage.coerceIn(0, currentCategoryApps.lastIndex),
                                                            )
                                                        }
                                                    }
                                                }
                                            GalleryTitleOrLetterSection(
                                                currentAppState =
                                                    if (category ==
                                                        selectedCategory
                                                    ) {
                                                        currentAppState
                                                    } else {
                                                        categoryCurrentAppState
                                                    },
                                                uniqueLetters = uniqueLetters,
                                                letterNavState = letterNavState,
                                                isSelectedCategory = category == selectedCategory,
                                                pairedApps = pairedApps,
                                                availableAppsByPackage = availableAppsByPackage,
                                                appColors = appColors,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Plane 2: Hovering Controls Layer (Categories, Actions, Touch Launch Buttons)
                        val isControlsEnabled =
                            editingAppInfo == null && pairingTargetApp == null && !isLibraryOpen && !isManageGalleriesOpen

                        InteractiveCategoryHeader(
                            selectedCategory = selectedCategory,
                            categories = categories,
                            onCategoryUp = { viewModel.cycleCategoryUp(categories) },
                            onCategoryDown = { viewModel.cycleCategoryDown(categories) },
                            enabled = isControlsEnabled,
                            areButtonPromptsVisible = areButtonPromptsVisible,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = FTL_CATEGORY_HEADER_PADDING_START, top = FTL_CATEGORY_HEADER_PADDING_TOP),
                        )

                        AnimatedVisibility(
                            visible = areButtonPromptsVisible,
                            enter =
                                fadeIn(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) +
                                    slideInVertically(
                                        animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS),
                                    ) { -it / FTL_PROMPT_SLIDE_DIVISOR } +
                                    slideInHorizontally(
                                        animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS),
                                    ) { it / FTL_PROMPT_SLIDE_DIVISOR },
                            exit =
                                fadeOut(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) +
                                    slideOutVertically(
                                        animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS),
                                    ) { -it / FTL_PROMPT_SLIDE_DIVISOR } +
                                    slideOutHorizontally(
                                        animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS),
                                    ) { it / FTL_PROMPT_SLIDE_DIVISOR },
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = FTL_NAV_PADDING_END, top = FTL_NAV_PADDING_TOP),
                        ) {
                            GamePadButtonAction(
                                button = GamePadButton.BUTTON_R2,
                                text = stringResource(R.string.gamefocus_nav_library),
                                enabled = isControlsEnabled,
                                onClick = { viewModel.setLibraryOpen(true) },
                            )
                        }

                        GalleryBottomControls(
                            viewModel = viewModel,
                            currentAppState = currentAppState,
                            favoritesSet = favoritesSet,
                            hiddenSet = hiddenSet,
                            pairedApps = pairedApps,
                            areButtonPromptsVisible = areButtonPromptsVisible,
                            isMainOptionsMenuExpanded = isMainOptionsMenuExpanded,
                            isControlsEnabled = isControlsEnabled,
                            onAppClickTop = onAppClickTop,
                            onAppClickBottom = onAppClickBottom,
                            onToggleFavorite = onToggleFavorite,
                            onToggleHidden = onToggleHidden,
                            onOpenPairingDialog = onOpenPairingDialog,
                            onRemovePair = onRemovePair,
                        )
                    }
                }
            }
        }

        if (newlyAddedFolder != null) {
            RomFolderCoreChooserDialog(
                folder = newlyAddedFolder!!,
                selectedIndex = coreChooserDialogSelectedIndex,
                onSelectedIndexChange = viewModel::setCoreChooserDialogSelectedIndex,
                confirmTrigger = confirmCoreChooserTrigger,
                onDismiss = { viewModel.setNewlyAddedFolder(null) },
                onConfirm = { core -> onConfirmNewlyAddedFolderCore(newlyAddedFolder!!, core) },
            )
        }

        if (pairingTargetApp != null) {
            GameFocusPairAppDialog(
                targetApp = pairingTargetApp!!,
                availableApps = availableAndroidApps,
                focusedIndex = pairingFocusedIndex,
                onFocusedIndexChange = viewModel::setPairingFocusedIndex,
                onDismiss = viewModel::dismissPairingDialog,
                onSelectApp = { selectedApp ->
                    onConfirmPairApp(pairingTargetApp!!, selectedApp)
                },
            )
        }

        // Game Edit Primary Overlay (Game Info, Gallery Artwork, Library Icon, Collections)
        if (editingAppInfo != null) {
            GameFocusEditGameOverlay(
                appInfo = editingAppInfo!!,
                apiKey = apiKey,
                onDismiss = { viewModel.setEditingAppInfo(null) },
                onOpenManageGalleries = {
                    viewModel.setEditingAppInfo(null)
                    viewModel.setManageGalleriesOpen(true)
                },
            )
        }

        // Manage Galleries Primary Overlay (Visibility, Order, Collections)
        if (isManageGalleriesOpen) {
            GameFocusManageGalleriesOverlay(
                galleries = allGalleryCategories,
                galleryConfig = galleryConfig,
                allApps = allApps,
                onDismiss = { viewModel.setManageGalleriesOpen(false) },
            )
        }
    }
}

@Composable
private fun GalleryNavigationEffects(
    viewModel: FocusTopLauncherViewModel,
    selectedCategory: GameFocusCategory,
    editingAppInfo: InstalledAppInfo?,
    categoryApps: List<InstalledAppInfo>,
    uniqueLetters: List<Char>,
    activePagerState: PagerState,
    currentAppState: State<InstalledAppInfo?>,
    letterNavState: LetterNavigationOverlayState,
    lastHighlightedPackages: MutableMap<String, String>,
) {
    val prevLetterTrigger by viewModel.prevLetterTrigger.collectAsState()
    val nextLetterTrigger by viewModel.nextLetterTrigger.collectAsState()
    val dpadLeftTrigger by viewModel.dpadLeftTrigger.collectAsState()
    val dpadStepRightTrigger by viewModel.dpadStepRightTrigger.collectAsState()
    val scope = rememberCoroutineScope()
    val currentActivePagerState by rememberUpdatedState(activePagerState)

    // Dismiss letter overlay without action if user manually scrolls the pager
    LaunchedEffect(Unit) {
        snapshotFlow { currentActivePagerState.isScrollInProgress }
            .collectLatest { isScrolling ->
                if (isScrolling && letterNavState.isActive) {
                    AppLog.i(TAG, "Pager scroll in progress while letter carousel active -> cancelling overlay without action")
                    letterNavState.isActive = false
                }
            }
    }

    // Handle D-pad LEFT step with wrap-around
    LaunchedEffect(dpadLeftTrigger) {
        if (dpadLeftTrigger > 0 && categoryApps.isNotEmpty()) {
            if (letterNavState.isActive) {
                AppLog.i(TAG, "D-pad LEFT pressed while letter carousel active -> cancelling overlay without action")
                letterNavState.isActive = false
            } else {
                val baseIndex = if (activePagerState.isScrollInProgress) activePagerState.targetPage else activePagerState.currentPage
                val safeBase = baseIndex.coerceIn(0, categoryApps.size - 1)
                val prevIndex = if (safeBase > 0) safeBase - 1 else categoryApps.size - 1
                AppLog.d(
                    TAG,
                    "D-pad LEFT step for ${selectedCategory.id}: current=${activePagerState.currentPage}, targetPage=${activePagerState.targetPage} -> target=$prevIndex",
                )
                activePagerState.animateScrollToPage(
                    page = prevIndex,
                    animationSpec = tween(durationMillis = FTL_CAROUSEL_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    // Handle D-pad RIGHT step with wrap-around
    LaunchedEffect(dpadStepRightTrigger) {
        if (dpadStepRightTrigger > 0 && categoryApps.isNotEmpty()) {
            if (letterNavState.isActive) {
                AppLog.i(TAG, "D-pad RIGHT pressed while letter carousel active -> cancelling overlay without action")
                letterNavState.isActive = false
            } else {
                val baseIndex = if (activePagerState.isScrollInProgress) activePagerState.targetPage else activePagerState.currentPage
                val safeBase = baseIndex.coerceIn(0, categoryApps.size - 1)
                val nextIndex = if (safeBase < categoryApps.size - 1) safeBase + 1 else 0
                AppLog.d(
                    TAG,
                    "D-pad RIGHT step for ${selectedCategory.id}: current=${activePagerState.currentPage}, targetPage=${activePagerState.targetPage} -> target=$nextIndex",
                )
                activePagerState.animateScrollToPage(
                    page = nextIndex,
                    animationSpec = tween(durationMillis = FTL_CAROUSEL_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    // Reset letter overlay on category switch or dialog edit
    LaunchedEffect(selectedCategory, editingAppInfo) {
        letterNavState.isActive = false
    }

    // Auto-hide and commit letter navigation after debounce
    LaunchedEffect(letterNavState.isActive, letterNavState.selectedIndex, prevLetterTrigger, nextLetterTrigger) {
        if (letterNavState.isActive) {
            delay(FTL_LETTER_NAV_DEBOUNCE_MS)
            val targetLetter = uniqueLetters.getOrNull(letterNavState.selectedIndex)
            letterNavState.isActive = false
            if (targetLetter != null) {
                val targetAppIndex = LetterNavigationHelper.findFirstIndexOfLetter(categoryApps, targetLetter)
                AppLog.i(TAG, "500ms debounce expired -> committing letter '$targetLetter' at index $targetAppIndex")
                scope.launch {
                    activePagerState.animateScrollToPage(targetAppIndex)
                }
            }
        }
    }

    // Handle Gamepad L1 step (previous starting letter in letter carousel)
    LaunchedEffect(prevLetterTrigger) {
        if (prevLetterTrigger > 0 && categoryApps.isNotEmpty() && uniqueLetters.isNotEmpty()) {
            if (!letterNavState.isActive) {
                letterNavState.isActive = true
                val currentLetter = currentAppState.value?.let { LetterNavigationHelper.getStartingLetter(it.label) }
                val initialIndex = if (currentLetter != null) uniqueLetters.indexOf(currentLetter).coerceAtLeast(0) else 0
                letterNavState.selectedIndex = initialIndex
            }
            letterNavState.selectedIndex = (letterNavState.selectedIndex - 1).coerceAtLeast(0)
            AppLog.d(
                TAG,
                "L1 letter carousel step for ${selectedCategory.id}: selected index=${letterNavState.selectedIndex} ('${uniqueLetters[letterNavState.selectedIndex]}')",
            )
        }
    }

    // Handle Gamepad R1 step (next starting letter in letter carousel)
    LaunchedEffect(nextLetterTrigger) {
        if (nextLetterTrigger > 0 && categoryApps.isNotEmpty() && uniqueLetters.isNotEmpty()) {
            if (!letterNavState.isActive) {
                letterNavState.isActive = true
                val currentLetter = currentAppState.value?.let { LetterNavigationHelper.getStartingLetter(it.label) }
                val initialIndex = if (currentLetter != null) uniqueLetters.indexOf(currentLetter).coerceAtLeast(0) else 0
                letterNavState.selectedIndex = initialIndex
            }
            letterNavState.selectedIndex = (letterNavState.selectedIndex + 1).coerceAtMost(uniqueLetters.size - 1)
            AppLog.d(
                TAG,
                "R1 letter carousel step for ${selectedCategory.id}: selected index=${letterNavState.selectedIndex} ('${uniqueLetters[letterNavState.selectedIndex]}')",
            )
        }
    }

    // Bi-directional synchronization: update lastHighlightedPackages and notify focused app
    val currentApp = currentAppState.value
    LaunchedEffect(currentApp, selectedCategory) {
        viewModel.setFocusedApp(currentApp)
        if (currentApp != null) {
            lastHighlightedPackages[selectedCategory.id] = currentApp.packageName
            AppLog.d(TAG, "Focused game changed for ${selectedCategory.id}: package=${currentApp.packageName}, label=${currentApp.label}")
        }
    }
}

@Composable
private fun DynamicLauncherBackground(
    activePagerState: PagerState,
    categoryApps: List<InstalledAppInfo>,
    appColors: AppColors,
) {
    var backgroundApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    // Throttle background color updates during rapid gallery navigation
    LaunchedEffect(activePagerState, categoryApps) {
        snapshotFlow {
            val page = activePagerState.currentPage
            categoryApps.getOrNull(page)
        }.collectLatest { target ->
            if (activePagerState.isScrollInProgress) {
                delay(FTL_BACKGROUND_COLOR_DEBOUNCE_MS)
            }
            backgroundApp = target
        }
    }

    val defaultPalette =
        remember(appColors.accent, appColors.appBackground) {
            ExtractedAppPalette(
                primaryColor = appColors.accent,
                secondaryColor = appColors.appBackground,
                isExtracted = false,
            )
        }

    val backgroundIconVersion =
        backgroundApp?.let { if (it.isRom) it.logoLastModified else it.lastUpdateTime } ?: 0L
    val activePalette by produceState(
        initialValue = backgroundApp?.let { AppPaletteExtractor.getCachedColorsOrNull(it) } ?: defaultPalette,
        backgroundApp?.packageName,
        backgroundApp?.coverPath,
        backgroundApp?.coverLastModified,
        backgroundIconVersion,
    ) {
        val currentTarget = backgroundApp
        val cached = currentTarget?.let { AppPaletteExtractor.getCachedColorsOrNull(it) }
        value =
            if (cached != null) {
                cached
            } else if (currentTarget != null) {
                AppPaletteExtractor.extractColorsAsync(currentTarget, appColors.accent, appColors.appBackground)
            } else {
                defaultPalette
            }
    }

    val animatedPrimaryColorState =
        animateColorAsState(
            targetValue = activePalette.primaryColor,
            animationSpec = tween(durationMillis = FTL_BACKGROUND_COLOR_ANIM_MS),
            label = "animatedPrimaryColor",
        )

    val animatedSecondaryColorState =
        animateColorAsState(
            targetValue = activePalette.secondaryColor,
            animationSpec = tween(durationMillis = FTL_BACKGROUND_COLOR_ANIM_MS),
            label = "animatedSecondaryColor",
        )

    Spacer(
        modifier =
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        animatedPrimaryColorState.value.copy(alpha = FTL_BG_PRIMARY_ALPHA),
                                        animatedSecondaryColorState.value.copy(alpha = FTL_BG_SECONDARY_ALPHA),
                                        appColors.appBackground,
                                    ),
                            ),
                    )
                },
    )
}

@Composable
private fun GalleryTitleOrLetterSection(
    currentAppState: State<InstalledAppInfo?>,
    uniqueLetters: List<Char>,
    letterNavState: LetterNavigationOverlayState,
    isSelectedCategory: Boolean,
    pairedApps: Map<String, String>,
    availableAppsByPackage: Map<String, InstalledAppInfo>,
    appColors: AppColors,
) {
    val showLetterOverlay = isSelectedCategory && letterNavState.isActive
    AnimatedContent(
        targetState = showLetterOverlay,
        transitionSpec = {
            (slideInVertically { height -> height / FTL_PROMPT_SLIDE_DIVISOR } + fadeIn())
                .togetherWith(slideOutVertically { height -> -height / FTL_PROMPT_SLIDE_DIVISOR } + fadeOut())
        },
        label = "TitleLetterCarouselTransition",
        modifier =
            Modifier
                .zIndex(FTL_TITLE_OVERLAY_Z_INDEX)
                .padding(horizontal = FTL_LETTER_NAV_PADDING),
    ) { isOverlay ->
        if (isOverlay && uniqueLetters.isNotEmpty()) {
            HorizontalLetterCarousel(
                letters = uniqueLetters,
                selectedIndex = letterNavState.selectedIndex,
            )
        } else {
            val currentApp = currentAppState.value
            if (currentApp != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = currentApp.label,
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = appColors.onSurface,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    val currentPairedPackage = pairedApps[currentApp.packageName]
                    val pairedApp =
                        if (currentPairedPackage != null) {
                            availableAppsByPackage[currentPairedPackage]
                        } else {
                            null
                        }
                    if (pairedApp != null) {
                        Box(
                            modifier =
                                Modifier.layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, 0) {
                                        placeable.placeRelative(0, 0)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(modifier = Modifier.height(FTL_SUBTITLE_GAP))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    MaterialSymbol(
                                        name = "splitscreen",
                                        size = FTL_SPLITSCREEN_BADGE_SIZE,
                                        tint = appColors.onSurfaceSecondary,
                                    )
                                    Spacer(modifier = Modifier.width(FTL_BADGE_TEXT_GAP))
                                    Text(
                                        text = pairedApp.label,
                                        style =
                                            MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = appColors.onSurfaceSecondary,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(FTL_SQUARE_ASPECT_RATIO, fill = false),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryBottomControls(
    viewModel: FocusTopLauncherViewModel,
    currentAppState: State<InstalledAppInfo?>,
    favoritesSet: Set<String>,
    hiddenSet: Set<String>,
    pairedApps: Map<String, String>,
    areButtonPromptsVisible: Boolean,
    isMainOptionsMenuExpanded: Boolean,
    isControlsEnabled: Boolean,
    onAppClickTop: (InstalledAppInfo) -> Unit,
    onAppClickBottom: (InstalledAppInfo) -> Unit,
    onToggleFavorite: (InstalledAppInfo) -> Unit,
    onToggleHidden: (InstalledAppInfo) -> Unit,
    onOpenPairingDialog: (InstalledAppInfo) -> Unit,
    onRemovePair: (InstalledAppInfo) -> Unit,
) {
    val context = LocalContext.current
    val mainMenuSelectedIndex by viewModel.mainMenuSelectedIndex.collectAsState()
    val currentApp = currentAppState.value
    val isCurrentPaired = currentApp != null && pairedApps.containsKey(currentApp.packageName)

    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom-Left Main Actions Menu hovering over the gallery plane
        AnimatedVisibility(
            visible = areButtonPromptsVisible || isMainOptionsMenuExpanded,
            enter =
                fadeIn(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) +
                    slideInVertically(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) { it / FTL_PROMPT_SLIDE_DIVISOR },
            exit =
                fadeOut(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) +
                    slideOutVertically(
                        animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS),
                    ) { it / FTL_PROMPT_SLIDE_DIVISOR },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = FTL_BOTTOM_BAR_PADDING_START, bottom = FTL_BOTTOM_BAR_PADDING_BOTTOM),
        ) {
            val isCurrentFavorite = currentApp != null && favoritesSet.contains(currentApp.packageName)
            val isCurrentHidden = currentApp != null && hiddenSet.contains(currentApp.packageName)
            val actions =
                remember(currentApp, isCurrentFavorite, isCurrentHidden, isCurrentPaired) {
                    buildGalleryMenuActions(
                        context = context,
                        currentApp = currentApp,
                        isFavorite = isCurrentFavorite,
                        isHidden = isCurrentHidden,
                        isPaired = isCurrentPaired,
                        onToggleFavorite = onToggleFavorite,
                        onEditGame = viewModel::openEditGameOverlay,
                        onToggleHidden = onToggleHidden,
                        onOpenPairingDialog = onOpenPairingDialog,
                        onRemovePair = onRemovePair,
                        onOpenManageGalleries = { viewModel.setManageGalleriesOpen(true) },
                        onDismissMenu = { viewModel.setMainOptionsMenuExpanded(false) },
                    )
                }
            ExpandableActionsMenu(
                isExpanded = isMainOptionsMenuExpanded,
                onExpandedChange = viewModel::setMainOptionsMenuExpanded,
                actions = actions,
                selectedIndex = mainMenuSelectedIndex,
                dividerAfterIndex = if (currentApp != null) actions.lastIndex - 1 else null,
                enabled = isControlsEnabled,
            )
        }

        // Bottom-Right subdued touch buttons hovering over the gallery plane
        AnimatedVisibility(
            visible = areButtonPromptsVisible,
            enter =
                fadeIn(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) +
                    slideInVertically(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) { it / FTL_PROMPT_SLIDE_DIVISOR },
            exit =
                fadeOut(animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS)) +
                    slideOutVertically(
                        animationSpec = tween(FTL_PROMPT_ANIM_DURATION_MS),
                    ) { it / FTL_PROMPT_SLIDE_DIVISOR },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = FTL_BOTTOM_BAR_PADDING_END, bottom = FTL_BOTTOM_BAR_PADDING_BOTTOM),
        ) {
            DualScreenLaunchButtons(
                appInfo = currentApp,
                isPaired = isCurrentPaired,
                enabled = isControlsEnabled && !isMainOptionsMenuExpanded,
                onLaunchTop = onAppClickTop,
                onLaunchBottom = onAppClickBottom,
            )
        }
    }
}

@Composable
private fun FocusLibraryHost(
    viewModel: FocusTopLauncherViewModel,
    allApps: List<InstalledAppInfo>,
    hiddenSet: Set<String>,
    libraryTabs: List<LibraryTab>,
    enabled: Boolean,
    areButtonPromptsVisible: Boolean,
    onToggleHidden: (InstalledAppInfo) -> Unit,
    onOpenAppInfo: (InstalledAppInfo) -> Unit,
    onUninstallApp: (InstalledAppInfo) -> Unit,
    onChangeCore: (CustomRomFolder) -> Unit,
    onAddRomFolder: () -> Unit,
    onRemoveRomFolder: (CustomRomFolder) -> Unit,
    onAppClickTop: (InstalledAppInfo) -> Unit,
    onAppClickBottom: (InstalledAppInfo) -> Unit,
) {
    val librarySelectedTab by viewModel.librarySelectedTab.collectAsState()
    val libraryFocusedIndexState = viewModel.libraryFocusedIndex.collectAsState()
    val isLibraryOptionsMenuExpanded by viewModel.isLibraryOptionsMenuExpanded.collectAsState()
    val libraryMenuSelectedIndex by viewModel.libraryMenuSelectedIndex.collectAsState()
    val isRemoveRomFolderDialogOpen by viewModel.isRemoveRomFolderDialogOpen.collectAsState()
    val removeRomFolderDialogSelectedIndex by viewModel.removeRomFolderDialogSelectedIndex.collectAsState()
    val folderToRemove by viewModel.folderToRemove.collectAsState()

    val openManageGalleries = remember(viewModel) { { viewModel.setManageGalleriesOpen(true) } }
    val closeLibrary = remember(viewModel) { { viewModel.setLibraryOpen(false) } }

    FocusLibraryScreen(
        allApps = allApps,
        selectedTab = librarySelectedTab,
        onTabSelected = viewModel::setLibrarySelectedTab,
        focusedIndexState = libraryFocusedIndexState,
        onFocusedIndexChange = viewModel::setLibraryFocusedIndex,
        hiddenSet = hiddenSet,
        isOptionsMenuExpanded = isLibraryOptionsMenuExpanded,
        onOptionsMenuExpandedChange = viewModel::setLibraryOptionsMenuExpanded,
        selectedIndex = libraryMenuSelectedIndex,
        onToggleHidden = onToggleHidden,
        onEditArtwork = viewModel::openEditGameOverlay,
        onOpenAppInfo = onOpenAppInfo,
        onUninstallApp = onUninstallApp,
        onChangeCore = onChangeCore,
        onAddRomFolder = onAddRomFolder,
        onRemoveRomFolder = onRemoveRomFolder,
        onOpenManageGalleries = openManageGalleries,
        onAppClickTop = onAppClickTop,
        onAppClickBottom = onAppClickBottom,
        onCloseRequested = closeLibrary,
        enabled = enabled,
        tabs = libraryTabs,
        isRemoveRomFolderDialogOpen = isRemoveRomFolderDialogOpen,
        onRemoveRomFolderDialogOpenChange = viewModel::setRemoveRomFolderDialogOpen,
        removeRomFolderDialogSelectedIndex = removeRomFolderDialogSelectedIndex,
        onRemoveRomFolderDialogSelectedIndexChange = viewModel::setRemoveRomFolderDialogSelectedIndex,
        folderToRemove = folderToRemove,
        onFolderToRemoveChange = viewModel::setFolderToRemove,
        areButtonPromptsVisible = areButtonPromptsVisible,
    )
}

internal object FocusImageCache {
    private const val MAX_ICON_DIMENSION_PX = 256
    internal const val MAX_COVER_WIDTH_PX = 480
    internal const val MAX_COVER_HEIGHT_PX = 720
    private const val COVER_CACHE_MAX_KB = 64 * 1024
    private const val ICON_CACHE_MAX_KB = 24 * 1024
    private const val BYTES_PER_ARGB_PIXEL = 4
    private const val BYTES_PER_KB = 1024
    private const val DEFAULT_APP_ICON_DRAW_PX = 128
    private const val SUBDIR_ROM_LOGOS = "gamefocus_logos"
    private const val SUBDIR_APP_ICONS = "gamefocus_icons"
    private const val MS_PER_SECOND = 1_000L
    private const val ICON_PNG_COMPRESS_QUALITY = 90
    private const val SAMPLE_HALF_DIVISOR = 2
    private const val SAMPLE_MULTIPLIER = 2

    private val coverCache =
        object : LruCache<String, ImageBitmap>(COVER_CACHE_MAX_KB) {
            override fun sizeOf(
                key: String,
                value: ImageBitmap,
            ): Int = ((value.width * value.height * BYTES_PER_ARGB_PIXEL) / BYTES_PER_KB).coerceAtLeast(1)
        }
    private val iconCache =
        object : LruCache<String, ImageBitmap>(ICON_CACHE_MAX_KB) {
            override fun sizeOf(
                key: String,
                value: ImageBitmap,
            ): Int = ((value.width * value.height * BYTES_PER_ARGB_PIXEL) / BYTES_PER_KB).coerceAtLeast(1)
        }
    private val missingIconKeys = ConcurrentHashMap.newKeySet<String>()
    private val missingLibraryIconKeys = ConcurrentHashMap.newKeySet<String>()

    /** Separates package name and version timestamp in versioned icon cache keys. */
    private const val ICON_KEY_VERSION_SEPARATOR = ":"

    /**
     * Icons are keyed by version timestamp ([InstalledAppInfo.logoLastModified] for ROMs,
     * [InstalledAppInfo.lastUpdateTime] for Android apps) so newly scraped ROM logos and
     * updated Android packages automatically bypass stale in-memory and negative cache entries.
     */
    internal fun iconCacheKey(appInfo: InstalledAppInfo): String {
        val version = if (appInfo.isRom) appInfo.logoLastModified else appInfo.lastUpdateTime
        return if (version > 0L) {
            "${appInfo.packageName}$ICON_KEY_VERSION_SEPARATOR$version"
        } else {
            appInfo.packageName
        }
    }

    /**
     * Computes a power-of-two `BitmapFactory.Options.inSampleSize` so small covers (e.g. 300x450 or
     * 600x900 SteamGridDB grids) retain full crispness while multi-megapixel custom covers are
     * downsampled to poster display resolution.
     */
    internal fun calculateCoverSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        maxWidthPx: Int = MAX_COVER_WIDTH_PX,
        maxHeightPx: Int = MAX_COVER_HEIGHT_PX,
    ): Int {
        if (rawWidth <= 0 || rawHeight <= 0) return 1
        var sampleSize = 1
        if (rawHeight > maxHeightPx || rawWidth > maxWidthPx) {
            val halfH = rawHeight / SAMPLE_HALF_DIVISOR
            val halfW = rawWidth / SAMPLE_HALF_DIVISOR
            while ((halfH / sampleSize) >= maxHeightPx || (halfW / sampleSize) >= maxWidthPx) {
                sampleSize *= SAMPLE_MULTIPLIER
            }
        }
        return sampleSize
    }

    /** Removes icon entries keyed by an older version timestamp so stale versions do not accumulate. */
    private fun evictStaleIconVersions(
        appInfo: InstalledAppInfo,
        currentKey: String,
    ) {
        val packageName = appInfo.packageName
        val prefix = "$packageName$ICON_KEY_VERSION_SEPARATOR"
        val staleKeys =
            iconCache.snapshot().keys.filter { (it == packageName || it.startsWith(prefix)) && it != currentKey } +
                missingIconKeys.filter { (it == packageName || it.startsWith(prefix)) && it != currentKey }
        if (staleKeys.isEmpty()) return
        staleKeys.forEach {
            iconCache.remove(it)
            missingIconKeys.remove(it)
        }
        AppLog.d(TAG, "Evicted ${staleKeys.size} stale icon cache entries for $packageName")
    }

    internal fun appIconFileFor(
        context: Context,
        packageName: String,
        isRom: Boolean = false,
    ): File {
        val subDirName = if (isRom) SUBDIR_ROM_LOGOS else SUBDIR_APP_ICONS
        val fileDir = File(context.cacheDir, subDirName).apply { mkdirs() }
        return File(fileDir, "$packageName.png")
    }

    fun getAppIcon(
        context: Context,
        packageName: String,
        activityName: String?,
    ): Drawable? {
        val pm = context.packageManager
        return try {
            if (!activityName.isNullOrBlank()) {
                val componentName = ComponentName(packageName, activityName)
                pm.getActivityIcon(componentName)
            } else {
                pm.getApplicationIcon(packageName)
            }
        } catch (e: Exception) {
            try {
                pm.getApplicationIcon(packageName)
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun getCoverBitmap(appInfo: InstalledAppInfo): ImageBitmap? {
        val path = appInfo.coverPath ?: return null
        val cacheKey = "$path:${appInfo.coverLastModified}"
        return coverCache.get(cacheKey)
    }

    suspend fun getCoverBitmapAsync(appInfo: InstalledAppInfo): ImageBitmap? {
        val path = appInfo.coverPath ?: return null
        val cacheKey = "$path:${appInfo.coverLastModified}"
        val cached = coverCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return@withContext null

            val startTime = System.currentTimeMillis()
            val bitmap =
                try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, boundsOptions)
                    val sampleSize = calculateCoverSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
                    val decodeOptions =
                        BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                    BitmapFactory
                        .decodeFile(path, decodeOptions)
                        ?.also { decoded ->
                            decoded.prepareToDraw()
                        }?.asImageBitmap()
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to decode cover file $path: ${e.message}")
                    null
                }

            val elapsed = System.currentTimeMillis() - startTime
            AppLog.d(TAG, "Decoded poster card cover for ${appInfo.label} in ${elapsed}ms")

            if (bitmap != null) {
                coverCache.put(cacheKey, bitmap)
            }
            bitmap
        }
    }

    fun isIconMissing(appInfo: InstalledAppInfo): Boolean = missingIconKeys.contains(iconCacheKey(appInfo))

    fun getCachedIconBitmap(appInfo: InstalledAppInfo): ImageBitmap? {
        val cacheKey = iconCacheKey(appInfo)
        if (missingIconKeys.contains(cacheKey)) return null
        return iconCache.get(cacheKey)
    }

    fun getCachedIconBitmap(packageName: String): ImageBitmap? {
        if (missingIconKeys.contains(packageName)) return null
        return iconCache.get(packageName)
    }

    fun isLibraryIconMissing(
        packageName: String,
        lastModified: Long,
    ): Boolean {
        val cacheKey = "libicon:$packageName:$lastModified"
        return missingLibraryIconKeys.contains(cacheKey)
    }

    fun getCachedLibraryIconBitmap(
        packageName: String,
        lastModified: Long,
    ): ImageBitmap? {
        val cacheKey = "libicon:$packageName:$lastModified"
        if (missingLibraryIconKeys.contains(cacheKey)) return null
        return iconCache.get(cacheKey)
    }

    suspend fun getLibraryIconBitmapAsync(appInfo: InstalledAppInfo): ImageBitmap? {
        val path = appInfo.libraryIconPath ?: return null
        val cacheKey = "libicon:${appInfo.packageName}:${appInfo.libraryIconLastModified}"
        if (missingLibraryIconKeys.contains(cacheKey)) return null
        val cached = iconCache.get(cacheKey)
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                missingLibraryIconKeys.add(cacheKey)
                AppLog.w(TAG, "Library icon file does not exist or is empty: $path")
                return@withContext null
            }
            val bitmap = decodeSampledIconBitmap(file)
            if (bitmap == null) {
                missingLibraryIconKeys.add(cacheKey)
                AppLog.w(TAG, "Failed to decode library icon bitmap from $path")
                return@withContext null
            }
            bitmap.prepareToDraw()
            val diskBitmap = bitmap.asImageBitmap()
            iconCache.put(cacheKey, diskBitmap)
            diskBitmap
        }
    }

    fun invalidateLibraryIcon(
        packageName: String,
        lastModified: Long? = null,
    ) {
        if (lastModified != null) {
            val key = "libicon:$packageName:$lastModified"
            iconCache.remove(key)
            missingLibraryIconKeys.remove(key)
        } else {
            val prefix = "libicon:$packageName:"
            iconCache
                .snapshot()
                .keys
                .filter { it.startsWith(prefix) }
                .forEach { iconCache.remove(it) }
            missingLibraryIconKeys.removeAll { it.startsWith(prefix) }
        }
        val iconPrefix = "$packageName$ICON_KEY_VERSION_SEPARATOR"
        iconCache
            .snapshot()
            .keys
            .filter { it == packageName || it.startsWith(iconPrefix) }
            .forEach { iconCache.remove(it) }
        missingIconKeys.remove(packageName)
        missingIconKeys.removeAll { it.startsWith(iconPrefix) }
    }

    fun invalidateIcon(packageName: String) {
        val prefix = "$packageName$ICON_KEY_VERSION_SEPARATOR"
        iconCache
            .snapshot()
            .keys
            .filter { it == packageName || it.startsWith(prefix) }
            .forEach { iconCache.remove(it) }
        missingIconKeys.remove(packageName)
        missingIconKeys.removeAll { it.startsWith(prefix) }
        invalidateLibraryIcon(packageName)
    }

    internal fun deleteDiskCachedIcon(
        context: Context,
        packageName: String,
    ) {
        val diskIcon = appIconFileFor(context, packageName, isRom = false)
        if (diskIcon.exists()) {
            if (diskIcon.delete()) {
                AppLog.d(TAG, "Deleted disk-cached icon file for $packageName")
            } else {
                AppLog.w(TAG, "Failed to delete disk-cached icon file ${diskIcon.absolutePath}")
            }
        }
    }

    @VisibleForTesting
    internal fun clearForTesting() {
        coverCache.evictAll()
        iconCache.evictAll()
        missingIconKeys.clear()
        missingLibraryIconKeys.clear()
    }

    /**
     * Decodes [file] downsampled to at most [maxDimensionPx]. Windows ICO containers that the
     * platform decoder cannot read are unpacked via [IcoImageExtractor]. Has no disk side effects.
     */
    internal fun decodeSampledIconBitmap(
        file: File,
        maxDimensionPx: Int = MAX_ICON_DIMENSION_PX,
    ): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            var rawW = boundsOptions.outWidth
            var rawH = boundsOptions.outHeight
            var extractedBytes: ByteArray? = null

            if (rawW <= 0 || rawH <= 0) {
                try {
                    val fileBytes = file.readBytes()
                    if (IcoImageExtractor.isIco(fileBytes)) {
                        val extracted = IcoImageExtractor.extractBestImage(fileBytes)
                        if (extracted != null) {
                            extractedBytes = extracted
                            boundsOptions.inJustDecodeBounds = true
                            BitmapFactory.decodeByteArray(extracted, 0, extracted.size, boundsOptions)
                            rawW = boundsOptions.outWidth
                            rawH = boundsOptions.outHeight
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to inspect file as ICO: ${e.message}")
                }
            }

            if (rawW <= 0 || rawH <= 0) return null

            var inSampleSize = 1
            if (rawH > maxDimensionPx || rawW > maxDimensionPx) {
                val halfH = rawH / SAMPLE_HALF_DIVISOR
                val halfW = rawW / SAMPLE_HALF_DIVISOR
                while ((halfH / inSampleSize) >= maxDimensionPx && (halfW / inSampleSize) >= maxDimensionPx) {
                    inSampleSize *= SAMPLE_MULTIPLIER
                }
            }

            val decodeOptions =
                BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val decoded =
                if (extractedBytes != null) {
                    BitmapFactory.decodeByteArray(extractedBytes, 0, extractedBytes.size, decodeOptions)
                } else {
                    BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                } ?: return null

            if (decoded.width > maxDimensionPx || decoded.height > maxDimensionPx) {
                val scale = minOf(maxDimensionPx.toFloat() / decoded.width, maxDimensionPx.toFloat() / decoded.height)
                val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                if (scaled != decoded) {
                    decoded.recycle()
                }
                scaled
            } else {
                decoded
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to decode sampled icon from ${file.name}: ${e.message}")
            null
        }
    }

    internal fun loadOrGetAppIconBitmap(
        context: Context,
        appInfo: InstalledAppInfo,
    ): Bitmap? {
        val iconFile = appIconFileFor(context, appInfo.packageName, appInfo.isRom)
        val isStaleAndroidIcon =
            !appInfo.isRom &&
                appInfo.lastUpdateTime > 0L &&
                iconFile.exists() &&
                iconFile.lastModified() < appInfo.lastUpdateTime
        if (isStaleAndroidIcon) {
            AppLog.i(
                TAG,
                "Evicting stale disk-cached icon for ${appInfo.packageName} " +
                    "(cachedAt=${iconFile.lastModified()}, lastUpdateTime=${appInfo.lastUpdateTime})",
            )
            if (!iconFile.delete()) {
                AppLog.w(TAG, "Failed to delete stale disk-cached icon ${iconFile.absolutePath}")
            }
        }
        if (iconFile.exists() && iconFile.length() > 0) {
            val startTime = System.currentTimeMillis()
            val bitmap = decodeSampledIconBitmap(iconFile)
            if (bitmap != null) {
                val elapsed = System.currentTimeMillis() - startTime
                AppLog.d(TAG, "Loaded disk-cached ${if (appInfo.isRom) "logo" else "icon"} PNG for ${appInfo.label} in ${elapsed}ms")
                return bitmap
            }
        }

        val iconDrawable = getAppIcon(context, appInfo.packageName, appInfo.activityName) ?: return null
        val startTime = System.currentTimeMillis()
        val androidBmp = iconDrawable.toAndroidBitmap() ?: return null
        val elapsed = System.currentTimeMillis() - startTime
        AppLog.d(TAG, "Converted app icon for ${appInfo.label} in ${elapsed}ms")

        try {
            FileOutputStream(iconFile).use { out ->
                androidBmp.compress(Bitmap.CompressFormat.PNG, ICON_PNG_COMPRESS_QUALITY, out)
            }
            if (!appInfo.isRom && appInfo.lastUpdateTime > 0L && iconFile.lastModified() < appInfo.lastUpdateTime) {
                // Round up to the next whole second so filesystems with 1-second mtime resolution
                // never report lastModified() < appInfo.lastUpdateTime after writing.
                val alignedTimestamp = ((appInfo.lastUpdateTime / MS_PER_SECOND) + 1L) * MS_PER_SECOND
                iconFile.setLastModified(alignedTimestamp)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to cache icon PNG for ${appInfo.label}: ${e.message}")
        }
        return androidBmp
    }

    suspend fun getIconBitmapAsync(
        context: Context,
        appInfo: InstalledAppInfo,
    ): ImageBitmap? {
        val cacheKey = iconCacheKey(appInfo)
        if (missingIconKeys.contains(cacheKey)) {
            return null
        }
        val cached = iconCache.get(cacheKey)
        if (cached != null) {
            return cached
        }
        evictStaleIconVersions(appInfo, cacheKey)

        return withContext(Dispatchers.IO) {
            val androidBmp = loadOrGetAppIconBitmap(context, appInfo)
            if (androidBmp == null) {
                missingIconKeys.add(cacheKey)
                return@withContext null
            }
            androidBmp.prepareToDraw()
            val imageBitmap = androidBmp.asImageBitmap()
            iconCache.put(cacheKey, imageBitmap)
            imageBitmap
        }
    }

    private fun Drawable.toAndroidBitmap(): Bitmap? =
        try {
            val w = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(DEFAULT_APP_ICON_DRAW_PX) else DEFAULT_APP_ICON_DRAW_PX
            val h = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(DEFAULT_APP_ICON_DRAW_PX) else DEFAULT_APP_ICON_DRAW_PX
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, w, h)
            draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
}

@Composable
internal fun Modifier.noFocusClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .focusProperties { canFocus = false }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

@Composable
internal fun GameFocusFallbackIcon(
    appInfo: InstalledAppInfo,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = LocalAppColors.current.accent,
) {
    if (appInfo.isRom) {
        MaterialSymbol(
            name = "sports_esports",
            size = size,
            tint = tint,
            modifier = modifier,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = appInfo.label,
            tint = tint,
            modifier = modifier.size(size),
        )
    }
}

@Composable
internal fun DualScreenLaunchButtons(
    appInfo: InstalledAppInfo?,
    isPaired: Boolean = false,
    enabled: Boolean,
    onLaunchTop: (InstalledAppInfo) -> Unit,
    onLaunchBottom: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GamePadButtonAction(
            button = GamePadButton.BUTTON_A,
            text = stringResource(if (isPaired) R.string.gamefocus_launch_dual else R.string.gamefocus_launch_top),
            enabled = enabled,
            onClick = {
                if (appInfo != null) onLaunchTop(appInfo)
            },
        )

        Spacer(modifier = Modifier.width(FTL_BUTTON_GAP))

        GamePadButtonAction(
            button = GamePadButton.BUTTON_X,
            text = stringResource(R.string.gamefocus_launch_bottom),
            enabled = enabled,
            onClick = {
                if (appInfo != null) onLaunchBottom(appInfo)
            },
        )
    }
}

@Composable
internal fun rememberCoverBitmap(appInfo: InstalledAppInfo): ImageBitmap? {
    if (appInfo.coverPath == null) return null
    val cached = FocusImageCache.getCoverBitmap(appInfo)
    if (cached != null) return cached

    var loadedBitmap by remember(appInfo.packageName, appInfo.coverPath, appInfo.coverLastModified) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(appInfo.packageName, appInfo.coverPath, appInfo.coverLastModified) {
        loadedBitmap = FocusImageCache.getCoverBitmapAsync(appInfo)
    }
    return loadedBitmap
}

@Composable
internal fun rememberIconBitmap(
    appInfo: InstalledAppInfo,
    enabled: Boolean = true,
): ImageBitmap? {
    if (!enabled) return null
    val cached = FocusImageCache.getCachedIconBitmap(appInfo)
    if (cached != null) return cached
    if (FocusImageCache.isIconMissing(appInfo)) return null

    val context = LocalContext.current
    val iconVersion = if (appInfo.isRom) appInfo.logoLastModified else appInfo.lastUpdateTime
    var loadedBitmap by remember(appInfo.packageName, appInfo.activityName, iconVersion) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(appInfo.packageName, appInfo.activityName, iconVersion) {
        loadedBitmap = FocusImageCache.getIconBitmapAsync(context, appInfo)
    }
    return loadedBitmap
}

private sealed interface LibraryIconLoadResult {
    data class Success(
        val bitmap: ImageBitmap,
    ) : LibraryIconLoadResult

    data object Loading : LibraryIconLoadResult

    data object Failed : LibraryIconLoadResult
}

@Composable
internal fun rememberLibraryIconBitmap(
    appInfo: InstalledAppInfo,
    enabled: Boolean = true,
): ImageBitmap? {
    if (!enabled) return null
    val customPath = appInfo.libraryIconPath
    if (customPath == null || FocusImageCache.isLibraryIconMissing(appInfo.packageName, appInfo.libraryIconLastModified)) {
        return rememberIconBitmap(appInfo, enabled = true)
    }
    val cachedCustom = FocusImageCache.getCachedLibraryIconBitmap(appInfo.packageName, appInfo.libraryIconLastModified)
    if (cachedCustom != null) return cachedCustom

    var customResult by remember(appInfo.packageName, customPath, appInfo.libraryIconLastModified) {
        mutableStateOf<LibraryIconLoadResult>(LibraryIconLoadResult.Loading)
    }
    LaunchedEffect(appInfo.packageName, customPath, appInfo.libraryIconLastModified) {
        val diskBitmap = FocusImageCache.getLibraryIconBitmapAsync(appInfo)
        customResult = if (diskBitmap != null) LibraryIconLoadResult.Success(diskBitmap) else LibraryIconLoadResult.Failed
    }
    return when (val result = customResult) {
        is LibraryIconLoadResult.Success -> result.bitmap
        LibraryIconLoadResult.Loading -> null
        LibraryIconLoadResult.Failed -> rememberIconBitmap(appInfo, enabled = true)
    }
}

@Composable
internal fun rememberAppPalette(
    appInfo: InstalledAppInfo,
    enabled: Boolean = true,
): ExtractedAppPalette? {
    if (!enabled) return null
    val cached = AppPaletteExtractor.getCachedColorsOrNull(appInfo)
    if (cached != null) return cached

    val appColors = LocalAppColors.current
    val iconVersion = if (appInfo.isRom) appInfo.logoLastModified else appInfo.lastUpdateTime
    var loadedPalette by remember(appInfo.packageName, appInfo.coverPath, appInfo.coverLastModified, iconVersion) {
        mutableStateOf<ExtractedAppPalette?>(null)
    }
    LaunchedEffect(appInfo.packageName, appInfo.coverPath, appInfo.coverLastModified, iconVersion) {
        loadedPalette = AppPaletteExtractor.extractColorsAsync(appInfo, appColors.accent, appColors.appBackground)
    }
    return loadedPalette
}

@Composable
private fun getCategoryName(category: GameFocusCategory): String =
    when (category) {
        is GameFocusCategory.RomSystem -> category.displayName
        is GameFocusCategory.CustomCollection -> category.displayName
        else -> stringResource(category.stringResId)
    }

@Composable
private fun InteractiveCategoryHeader(
    selectedCategory: GameFocusCategory,
    categories: List<GameFocusCategory>,
    onCategoryUp: () -> Unit = {},
    onCategoryDown: () -> Unit = {},
    enabled: Boolean = true,
    areButtonPromptsVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val selectedIndex =
        remember(selectedCategory, categories) {
            categories.indexOf(selectedCategory).coerceAtLeast(0)
        }

    VerticalRollingCarousel(
        selectedIndex = selectedIndex,
        items = categories,
        onSelectedIndexChange = { index ->
            val targetCategory = categories[index]
            if (targetCategory != selectedCategory) {
                if ((selectedIndex + 1).floorMod(categories.size) == index) {
                    onCategoryDown()
                } else {
                    onCategoryUp()
                }
            }
        },
        labelProvider = { category ->
            getCategoryName(category)
        },
        enabled = enabled,
        showButtonIcons = areButtonPromptsVisible,
        showNeighboringItems = true,
        modifier = modifier,
    )
}

@Composable
private fun PosterCardContent(
    appInfo: InstalledAppInfo,
    isFavorite: Boolean = false,
    isHidden: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val coverBitmap = rememberCoverBitmap(appInfo)
    val iconBitmap = rememberIconBitmap(appInfo, enabled = coverBitmap == null)

    val visibilityOffAlphaState =
        animateFloatAsState(
            targetValue = if (isHidden) FTL_HIDDEN_BADGE_ALPHA else FTL_VISIBLE_BADGE_ALPHA,
            animationSpec = tween(durationMillis = FTL_HIDE_ANIMATION_DURATION_MS),
            label = "LauncherVisibilityOffAlpha",
        )
    val showVisibilityBadge by remember {
        derivedStateOf { visibilityOffAlphaState.value > 0f }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = appInfo.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = appInfo.label,
                contentScale = ContentScale.Fit,
                modifier =
                    if (appInfo.isRom) {
                        Modifier
                            .fillMaxWidth(FTL_ROM_LOGO_WIDTH_FRACTION)
                            .heightIn(max = FTL_ICON_SIZE)
                            .padding(horizontal = FTL_ROM_LOGO_HORIZONTAL_PADDING)
                    } else {
                        Modifier
                            .size(FTL_ICON_SIZE)
                            .aspectRatio(FTL_SQUARE_ASPECT_RATIO)
                    },
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(FTL_ICON_SIZE)
                        .clip(FTL_ROM_ICON_SHAPE)
                        .background(appColors.surface),
                contentAlignment = Alignment.Center,
            ) {
                GameFocusFallbackIcon(
                    appInfo = appInfo,
                    size = FTL_FALLBACK_ICON_SIZE,
                )
            }
        }

        if (isFavorite) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(FTL_BADGE_PADDING),
                contentAlignment = Alignment.TopEnd,
            ) {
                MaterialSymbol(
                    name = "kid_star",
                    size = FTL_BADGE_ICON_SIZE,
                    tint = appColors.accent,
                )
            }
        }

        if (showVisibilityBadge) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(FTL_BADGE_PADDING)
                        .graphicsLayer { alpha = visibilityOffAlphaState.value },
                contentAlignment = Alignment.TopStart,
            ) {
                MaterialSymbol(
                    name = "visibility_off",
                    size = FTL_BADGE_ICON_SIZE,
                    tint = appColors.onSurfaceSecondary,
                )
            }
        }
    }
}

class RomPagerState(
    initialPage: Int = 0,
    initialPageOffsetFraction: Float = 0f,
    private val pageCountProvider: () -> Int,
) : PagerState(initialPage, initialPageOffsetFraction) {
    override val pageCount: Int
        get() = pageCountProvider()
}

internal fun isScreenReaderFeedbackMask(feedbackType: Int): Boolean =
    (feedbackType and (AccessibilityServiceInfo.FEEDBACK_SPOKEN or AccessibilityServiceInfo.FEEDBACK_BRAILLE)) != 0

internal fun isScreenReaderService(
    feedbackType: Int,
    capabilities: Int,
): Boolean =
    isScreenReaderFeedbackMask(feedbackType) &&
        (capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0

internal fun isScreenReaderAccessibilityActive(context: Context): Boolean {
    val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
    if (!am.isEnabled) return false
    if (am.isTouchExplorationEnabled) return true
    return am
        .getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_SPOKEN or AccessibilityServiceInfo.FEEDBACK_BRAILLE,
        ).any { isScreenReaderService(it.feedbackType, it.capabilities) }
}

@Composable
private fun rememberScreenReaderActive(context: Context): Boolean {
    var isActive by remember(context) { mutableStateOf(isScreenReaderAccessibilityActive(context)) }
    DisposableEffect(context) {
        val am = context.getSystemService(AccessibilityManager::class.java)
        if (am == null) {
            onDispose {}
        } else {
            val a11yListener =
                AccessibilityManager.AccessibilityStateChangeListener {
                    val updated = isScreenReaderAccessibilityActive(context)
                    AppLog.d(TAG, "Accessibility state changed -> isScreenReaderActive=$updated")
                    isActive = updated
                }
            val touchListener =
                AccessibilityManager.TouchExplorationStateChangeListener {
                    val updated = isScreenReaderAccessibilityActive(context)
                    AppLog.d(TAG, "Touch exploration state changed -> isScreenReaderActive=$updated")
                    isActive = updated
                }
            am.addAccessibilityStateChangeListener(a11yListener)
            am.addTouchExplorationStateChangeListener(touchListener)
            isActive = isScreenReaderAccessibilityActive(context)
            onDispose {
                am.removeAccessibilityStateChangeListener(a11yListener)
                am.removeTouchExplorationStateChangeListener(touchListener)
            }
        }
    }
    return isActive
}
