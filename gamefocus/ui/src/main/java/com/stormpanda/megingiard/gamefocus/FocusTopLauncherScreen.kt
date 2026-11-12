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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.stormpanda.megingiard.ui.ExpandableActionItem
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
import kotlin.math.abs

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
private const val FTL_BACKGROUND_UNCACHED_DEBOUNCE_MS = 120L
private const val FTL_BACKGROUND_COLOR_ANIM_MS = 500
private const val FTL_LETTER_NAV_DEBOUNCE_MS = 500L
private const val FTL_LIBRARY_TRANSITION_MS = 350
private const val FTL_BG_PRIMARY_ALPHA = 0.20f
private const val FTL_BG_SECONDARY_ALPHA = 0.10f

private const val FTL_HIDDEN_BADGE_ALPHA = 1.0f
private const val FTL_VISIBLE_BADGE_ALPHA = 0.0f
private const val FTL_FULL_ALPHA = 1.0f
private const val FTL_HIDE_ANIMATION_DURATION_MS = 300
private const val FTL_CAROUSEL_ANIMATION_DURATION_MS = 120
private const val FTL_TITLE_OVERLAY_Z_INDEX = 1f
private const val FTL_MAX_RETAINED_CATEGORIES = 16
private const val FTL_MAX_IN_FLIGHT_HEADROOM = 2
private const val FTL_PREWARM_DELAY_MS = 480L
private const val FTL_IDLE_BATCH_PREWARM_GAP_MS = 120L
private const val FTL_SLIDE_VISIBILITY_THRESHOLD = 0.001f
private const val FTL_DIRECTION_NEXT = 1
private const val FTL_DIRECTION_PREV = -1
private const val FTL_NEXT_NEIGHBOR_OFFSET = 1f
private const val FTL_PREV_NEIGHBOR_OFFSET = -1f
private const val FTL_INACTIVE_PARK_MULTIPLIER = 2f
private const val FTL_INACTIVE_Z_ORDER = -1f

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

internal fun categoryStepDirection(
    previous: GameFocusCategory,
    target: GameFocusCategory,
    categories: List<GameFocusCategory>,
): Int {
    if (previous == target || categories.size <= 1) return 0
    return if (previous.next(categories) == target) FTL_DIRECTION_NEXT else FTL_DIRECTION_PREV
}

@Stable
internal class CategorySlideEntry(
    category: GameFocusCategory,
    initialOffset: Float,
    initiallyActive: Boolean,
    initiallySelected: Boolean,
    initialZOrder: Float,
    initialLastUsedTick: Long,
) {
    var category by mutableStateOf(category)
    var renderOffset by mutableFloatStateOf(initialOffset)
    var targetOffset: Float = initialOffset
    var isActive by mutableStateOf(initiallyActive)
    var isSelected by mutableStateOf(initiallySelected)
    var zOrder by mutableFloatStateOf(initialZOrder)
    var animGeneration by mutableIntStateOf(0)
    var lastUsedTick: Long = initialLastUsedTick
}

@Stable
internal class CategoryTransitionController(
    initialCategory: GameFocusCategory,
    private val maxRetained: Int = FTL_MAX_RETAINED_CATEGORIES,
) {
    private var lastSelectedCategory: GameFocusCategory = initialCategory
    private var nextZOrder: Int = 1
    private var lruCounter: Long = 1L
    private var lastCategoriesRef: List<GameFocusCategory>? = null
    private var latestAllowedIds: Set<String> = setOf(initialCategory.id)

    val entries =
        mutableStateListOf(
            CategorySlideEntry(
                category = initialCategory,
                initialOffset = 0f,
                initiallyActive = true,
                initiallySelected = true,
                initialZOrder = 1f,
                initialLastUsedTick = 1L,
            ),
        )

    private fun findEntryById(categoryId: String): CategorySlideEntry? {
        for (i in 0 until entries.size) {
            val candidate = entries[i]
            if (candidate.category.id == categoryId) {
                return candidate
            }
        }
        return null
    }

    private fun updateAllowedIdsIfNeeded(
        selectedCategory: GameFocusCategory,
        categories: List<GameFocusCategory>,
    ): Boolean {
        if (categories === lastCategoriesRef && selectedCategory.id in latestAllowedIds) {
            return false
        }
        val allowedIds = HashSet<String>(categories.size + 1)
        for (i in categories.indices) {
            allowedIds.add(categories[i].id)
        }
        allowedIds.add(selectedCategory.id)
        lastCategoriesRef = categories
        latestAllowedIds = allowedIds
        return true
    }

    fun onSelectedCategoryChanged(
        selectedCategory: GameFocusCategory,
        categories: List<GameFocusCategory>,
    ) {
        Snapshot.withoutReadObservation {
            val categoriesChanged = updateAllowedIdsIfNeeded(selectedCategory, categories)

            if (selectedCategory == lastSelectedCategory) {
                val existing = findEntryById(selectedCategory.id)
                if (existing != null && existing.category !== selectedCategory) {
                    existing.category = selectedCategory
                }
                if (categoriesChanged) {
                    pruneRemovedInactive(latestAllowedIds)
                }
                return@withoutReadObservation
            }

            val previousCategory = lastSelectedCategory
            lastSelectedCategory = selectedCategory
            val direction = categoryStepDirection(previousCategory, selectedCategory, categories)
            val effectiveDirection = if (direction == 0) FTL_DIRECTION_NEXT else direction
            val exitTargetOffset = -effectiveDirection.toFloat()

            var outgoing: CategorySlideEntry? = null
            var incoming: CategorySlideEntry? = null
            for (i in 0 until entries.size) {
                val item = entries[i]
                when (item.category.id) {
                    previousCategory.id -> {
                        outgoing = item
                    }

                    selectedCategory.id -> {
                        incoming = item
                    }

                    else -> {
                        if (item.isActive) {
                            item.isSelected = false
                            item.targetOffset = exitTargetOffset
                            item.animGeneration++
                        }
                    }
                }
            }

            if (outgoing != null) {
                outgoing.isSelected = false
                outgoing.isActive = true
                outgoing.targetOffset = exitTargetOffset
                outgoing.lastUsedTick = ++lruCounter
                outgoing.animGeneration++
            }

            val newZOrder = (++nextZOrder).toFloat()
            val newTick = ++lruCounter
            if (incoming != null) {
                incoming.category = selectedCategory
                if (!incoming.isActive) {
                    incoming.renderOffset = effectiveDirection.toFloat()
                }
                incoming.isSelected = true
                incoming.isActive = true
                incoming.targetOffset = 0f
                incoming.zOrder = newZOrder
                incoming.lastUsedTick = newTick
                incoming.animGeneration++
            } else {
                entries.add(
                    CategorySlideEntry(
                        category = selectedCategory,
                        initialOffset = effectiveDirection.toFloat(),
                        initiallyActive = true,
                        initiallySelected = true,
                        initialZOrder = newZOrder,
                        initialLastUsedTick = newTick,
                    ),
                )
            }

            if (categoriesChanged) {
                pruneRemovedInactive(latestAllowedIds)
            }
            // Avoid Frame-1 disposal work during single switches; only cap runaway continuous holds immediately.
            evictDownTo(maxRetained + FTL_MAX_IN_FLIGHT_HEADROOM)
        }
    }

    fun prewarm(
        category: GameFocusCategory,
        initialOffset: Float,
    ): Boolean =
        Snapshot.withoutReadObservation {
            if (findEntryById(category.id) != null) {
                return@withoutReadObservation false
            }
            var activeCount = 0
            for (i in 0 until entries.size) {
                if (entries[i].isActive) activeCount++
            }
            if (activeCount >= maxRetained) {
                return@withoutReadObservation false
            }
            entries.add(
                CategorySlideEntry(
                    category = category,
                    initialOffset = initialOffset,
                    initiallyActive = false,
                    initiallySelected = false,
                    initialZOrder = 0f,
                    initialLastUsedTick = ++lruCounter,
                ),
            )
            evictDownTo(maxRetained, protectId = category.id)
            true
        }

    fun onEntrySettled(entry: CategorySlideEntry) {
        Snapshot.withoutReadObservation {
            if (!entry.isSelected && entry.targetOffset != 0f) {
                entry.renderOffset = entry.targetOffset
                entry.isActive = false
                pruneRemovedInactive(latestAllowedIds)
                evictDownTo(maxRetained)
            } else if (entry.isSelected) {
                entry.renderOffset = 0f
            }
        }
    }

    fun resetToSelected() {
        Snapshot.withoutReadObservation {
            val selectedId = lastSelectedCategory.id
            entries.removeAll { it.category.id != selectedId }
            val current = entries.firstOrNull()
            if (current != null) {
                current.isSelected = true
                current.isActive = true
                current.renderOffset = 0f
                current.targetOffset = 0f
                current.zOrder = 1f
                nextZOrder = 1
            } else {
                nextZOrder = 1
                entries.add(
                    CategorySlideEntry(
                        category = lastSelectedCategory,
                        initialOffset = 0f,
                        initiallyActive = true,
                        initiallySelected = true,
                        initialZOrder = 1f,
                        initialLastUsedTick = ++lruCounter,
                    ),
                )
            }
        }
    }

    private fun pruneRemovedInactive(allowedIds: Set<String>) {
        var hasRemovable = false
        for (i in 0 until entries.size) {
            val item = entries[i]
            if (!item.isActive && !item.isSelected && item.category.id !in allowedIds) {
                hasRemovable = true
                break
            }
        }
        if (hasRemovable) {
            entries.removeAll { !it.isActive && !it.isSelected && it.category.id !in allowedIds }
        }
    }

    private fun evictDownTo(
        limit: Int,
        protectId: String? = null,
    ) {
        while (entries.size > limit) {
            var victim: CategorySlideEntry? = null
            for (i in 0 until entries.size) {
                val item = entries[i]
                if (!item.isActive && !item.isSelected && item.category.id != protectId) {
                    if (victim == null || item.lastUsedTick < victim.lastUsedTick) {
                        victim = item
                    }
                }
            }
            if (victim == null) break
            entries.remove(victim)
        }
    }
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
    val selectedCategoryState = viewModel.selectedCategory.collectAsState()
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
        remember(categories, allGalleryCategories) {
            buildList {
                addAll(GameFocusCategory.builtIns)
                addAll(categories)
                addAll(allGalleryCategories)
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
    val allAppsState = rememberUpdatedState(allApps)
    val favoritesSetState = rememberUpdatedState(favoritesSet)
    val hiddenSetState = rememberUpdatedState(hiddenSet)
    val lastUsedState = rememberUpdatedState(lastUsed)
    val collectionMembersState = rememberUpdatedState(collectionMembers)
    val uniqueLettersByCategoryId =
        remember(appsByCategoryId) {
            appsByCategoryId.mapValues { (_, apps) ->
                LetterNavigationHelper.getUniqueStartingLetters(apps)
            }
        }
    val uniqueLettersByCategoryIdState = rememberUpdatedState(uniqueLettersByCategoryId)

    val resolveCategoryApps: (GameFocusCategory) -> List<InstalledAppInfo> =
        remember {
            { cat ->
                appsByCategoryIdState.value[cat.id]
                    ?: cat.filterApps(
                        allAppsState.value,
                        favoritesSetState.value,
                        hiddenSetState.value,
                        lastUsedState.value,
                        collectionMembersState.value,
                    )
            }
        }

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

    val pagerStateProvider: (GameFocusCategory) -> PagerState =
        remember(gamesPagerState, appsPagerState, favoritesPagerState, lastUsedPagerState, romPagerStates) {
            { cat ->
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
            }
        }

    val letterNavState = remember { LetterNavigationOverlayState() }
    val categoryTransitionController =
        remember {
            CategoryTransitionController(Snapshot.withoutReadObservation { selectedCategoryState.value })
        }

    // Single stable derived focused-app State: never read in FocusTopLauncherScreen's own scope so
    // neither horizontal page crossings nor vertical category switches recompose the root screen.
    val currentAppState =
        remember(selectedCategoryState, pagerStateProvider) {
            derivedStateOf {
                val cat = selectedCategoryState.value
                val categoryApps = resolveCategoryApps(cat)
                if (categoryApps.isEmpty()) {
                    null
                } else {
                    val activePager = pagerStateProvider(cat)
                    categoryApps.getOrNull(activePager.currentPage.coerceIn(0, categoryApps.lastIndex))
                }
            }
        }

    GalleryNavigationEffects(
        viewModel = viewModel,
        selectedCategoryState = selectedCategoryState,
        editingAppInfo = editingAppInfo,
        resolveCategoryApps = resolveCategoryApps,
        uniqueLettersByCategoryIdState = uniqueLettersByCategoryIdState,
        pagerStateProvider = pagerStateProvider,
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
                            selectedCategoryState = selectedCategoryState,
                            pagerStateProvider = pagerStateProvider,
                            currentAppState = currentAppState,
                            appColors = appColors,
                        )

                        GalleryCategoryCoordinator(
                            controller = categoryTransitionController,
                            selectedCategoryState = selectedCategoryState,
                            categories = categories,
                            appsByCategoryId = appsByCategoryId,
                            resolveCategoryApps = resolveCategoryApps,
                            lastHighlightedPackages = lastHighlightedPackages,
                            pagerStateProvider = pagerStateProvider,
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

                        // Plane 1: Full-Screen Gallery & App Title Plane (Retained 120 Hz Carousel Pool)
                        GalleryCategoryCarouselStack(
                            controller = categoryTransitionController,
                            appsByCategoryId = appsByCategoryId,
                            resolveCategoryApps = resolveCategoryApps,
                            uniqueLettersByCategoryId = uniqueLettersByCategoryId,
                            favoritesSet = favoritesSet,
                            hiddenSet = hiddenSet,
                            pairedApps = pairedApps,
                            availableAppsByPackage = availableAppsByPackage,
                            isScreenReaderActive = isScreenReaderActive,
                            letterNavState = letterNavState,
                            appColors = appColors,
                            pagerStateProvider = pagerStateProvider,
                            onAppClickTop = onAppClickTop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Plane 2: Hovering Controls Layer (Categories, Actions, Touch Launch Buttons)
                        val isControlsEnabled =
                            editingAppInfo == null && pairingTargetApp == null && !isLibraryOpen && !isManageGalleriesOpen
                        val currentCategories by rememberUpdatedState(categories)
                        val handleCategoryUp =
                            remember(viewModel) {
                                {
                                    viewModel.cycleCategoryUp(currentCategories)
                                    Unit
                                }
                            }
                        val handleCategoryDown =
                            remember(viewModel) {
                                {
                                    viewModel.cycleCategoryDown(currentCategories)
                                    Unit
                                }
                            }

                        InteractiveCategoryHeader(
                            selectedCategoryState = selectedCategoryState,
                            categories = categories,
                            onCategoryUp = handleCategoryUp,
                            onCategoryDown = handleCategoryDown,
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

private data class LetterOverlayResetKey(
    val category: GameFocusCategory,
    val editingAppInfo: InstalledAppInfo?,
)

private data class FocusedCategoryObservation(
    val category: GameFocusCategory,
    val categoryApps: List<InstalledAppInfo>,
    val currentApp: InstalledAppInfo?,
)

private data class BackgroundFocusObservation(
    val app: InstalledAppInfo?,
    val isScrolling: Boolean,
)

private data class CategoryPrewarmSnapshot(
    val selectedCategory: GameFocusCategory,
    val categories: List<GameFocusCategory>,
    val appsByCategoryId: Map<String, List<InstalledAppInfo>>,
)

@Composable
private fun GalleryNavigationEffects(
    viewModel: FocusTopLauncherViewModel,
    selectedCategoryState: State<GameFocusCategory>,
    editingAppInfo: InstalledAppInfo?,
    resolveCategoryApps: (GameFocusCategory) -> List<InstalledAppInfo>,
    uniqueLettersByCategoryIdState: State<Map<String, List<Char>>>,
    pagerStateProvider: (GameFocusCategory) -> PagerState,
    currentAppState: State<InstalledAppInfo?>,
    letterNavState: LetterNavigationOverlayState,
    lastHighlightedPackages: MutableMap<String, String>,
) {
    val prevLetterTrigger by viewModel.prevLetterTrigger.collectAsState()
    val nextLetterTrigger by viewModel.nextLetterTrigger.collectAsState()
    val dpadLeftTrigger by viewModel.dpadLeftTrigger.collectAsState()
    val dpadStepRightTrigger by viewModel.dpadStepRightTrigger.collectAsState()
    val scope = rememberCoroutineScope()
    val currentEditingAppInfo by rememberUpdatedState(editingAppInfo)
    val currentResolveCategoryApps by rememberUpdatedState(resolveCategoryApps)
    val currentPagerStateProvider by rememberUpdatedState(pagerStateProvider)

    // Dismiss letter overlay without action if user manually scrolls the active pager
    LaunchedEffect(Unit) {
        snapshotFlow {
            val activePager = currentPagerStateProvider(selectedCategoryState.value)
            activePager.isScrollInProgress
        }.collectLatest { isScrolling ->
            if (isScrolling && letterNavState.isActive) {
                AppLog.i(TAG, "Pager scroll in progress while letter carousel active -> cancelling overlay without action")
                letterNavState.isActive = false
            }
        }
    }

    // Handle D-pad LEFT step with wrap-around
    LaunchedEffect(dpadLeftTrigger) {
        if (dpadLeftTrigger > 0) {
            val selectedCategory = selectedCategoryState.value
            val categoryApps = currentResolveCategoryApps(selectedCategory)
            if (categoryApps.isNotEmpty()) {
                if (letterNavState.isActive) {
                    AppLog.i(TAG, "D-pad LEFT pressed while letter carousel active -> cancelling overlay without action")
                    letterNavState.isActive = false
                } else {
                    val activePagerState = currentPagerStateProvider(selectedCategory)
                    val baseIndex =
                        if (activePagerState.isScrollInProgress) activePagerState.targetPage else activePagerState.currentPage
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
    }

    // Handle D-pad RIGHT step with wrap-around
    LaunchedEffect(dpadStepRightTrigger) {
        if (dpadStepRightTrigger > 0) {
            val selectedCategory = selectedCategoryState.value
            val categoryApps = currentResolveCategoryApps(selectedCategory)
            if (categoryApps.isNotEmpty()) {
                if (letterNavState.isActive) {
                    AppLog.i(TAG, "D-pad RIGHT pressed while letter carousel active -> cancelling overlay without action")
                    letterNavState.isActive = false
                } else {
                    val activePagerState = currentPagerStateProvider(selectedCategory)
                    val baseIndex =
                        if (activePagerState.isScrollInProgress) activePagerState.targetPage else activePagerState.currentPage
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
    }

    // Reset letter overlay on category switch or dialog edit without tearing down LaunchedEffect
    LaunchedEffect(Unit) {
        snapshotFlow { LetterOverlayResetKey(selectedCategoryState.value, currentEditingAppInfo) }
            .collectLatest {
                if (letterNavState.isActive) {
                    letterNavState.isActive = false
                }
            }
    }

    // Auto-hide and commit letter navigation after debounce
    LaunchedEffect(letterNavState.isActive, letterNavState.selectedIndex, prevLetterTrigger, nextLetterTrigger) {
        if (letterNavState.isActive) {
            delay(FTL_LETTER_NAV_DEBOUNCE_MS)
            val selectedCategory = selectedCategoryState.value
            val categoryApps = currentResolveCategoryApps(selectedCategory)
            val uniqueLetters = uniqueLettersByCategoryIdState.value[selectedCategory.id].orEmpty()
            val targetLetter = uniqueLetters.getOrNull(letterNavState.selectedIndex)
            letterNavState.isActive = false
            if (targetLetter != null && categoryApps.isNotEmpty()) {
                val targetAppIndex = LetterNavigationHelper.findFirstIndexOfLetter(categoryApps, targetLetter)
                val activePagerState = currentPagerStateProvider(selectedCategory)
                AppLog.i(TAG, "500ms debounce expired -> committing letter '$targetLetter' at index $targetAppIndex")
                scope.launch {
                    activePagerState.animateScrollToPage(targetAppIndex)
                }
            }
        }
    }

    // Handle Gamepad L1 step (previous starting letter in letter carousel)
    LaunchedEffect(prevLetterTrigger) {
        if (prevLetterTrigger > 0) {
            val selectedCategory = selectedCategoryState.value
            val categoryApps = currentResolveCategoryApps(selectedCategory)
            val uniqueLetters = uniqueLettersByCategoryIdState.value[selectedCategory.id].orEmpty()
            if (categoryApps.isNotEmpty() && uniqueLetters.isNotEmpty()) {
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
    }

    // Handle Gamepad R1 step (next starting letter in letter carousel)
    LaunchedEffect(nextLetterTrigger) {
        if (nextLetterTrigger > 0) {
            val selectedCategory = selectedCategoryState.value
            val categoryApps = currentResolveCategoryApps(selectedCategory)
            val uniqueLetters = uniqueLettersByCategoryIdState.value[selectedCategory.id].orEmpty()
            if (categoryApps.isNotEmpty() && uniqueLetters.isNotEmpty()) {
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
    }

    // Persistent bi-directional synchronization: restore remembered category page on cold/changed switches,
    // then update lastHighlightedPackages and notify focused app without LaunchedEffect teardown churn.
    LaunchedEffect(Unit) {
        var syncedCategoryId: String? = null
        var syncedCategoryAppsRef: List<InstalledAppInfo>? = null
        var lastReportedCategoryId: String? = null
        var lastReportedApp: InstalledAppInfo? = null

        snapshotFlow {
            val cat = selectedCategoryState.value
            FocusedCategoryObservation(
                category = cat,
                categoryApps = currentResolveCategoryApps(cat),
                currentApp = currentAppState.value,
            )
        }.collectLatest { observation ->
            val category = observation.category
            val categoryApps = observation.categoryApps
            if (category.id != syncedCategoryId || categoryApps !== syncedCategoryAppsRef) {
                if (categoryApps.isNotEmpty()) {
                    val pager = currentPagerStateProvider(category)
                    val lastPkg = lastHighlightedPackages[category.id]
                    val targetIndex =
                        if (lastPkg != null) {
                            val found = categoryApps.indexOfFirst { it.packageName == lastPkg }
                            if (found >= 0) found else pager.currentPage.coerceIn(0, categoryApps.lastIndex)
                        } else {
                            pager.currentPage.coerceIn(0, categoryApps.lastIndex)
                        }
                    if (!pager.isScrollInProgress && pager.currentPage != targetIndex) {
                        pager.scrollToPage(targetIndex)
                    }
                }
                syncedCategoryId = category.id
                syncedCategoryAppsRef = categoryApps
            }

            val resolvedApp =
                if (categoryApps.isEmpty()) {
                    null
                } else {
                    val pager = currentPagerStateProvider(category)
                    categoryApps.getOrNull(pager.currentPage.coerceIn(0, categoryApps.lastIndex))
                }

            if (category.id != lastReportedCategoryId || resolvedApp != lastReportedApp) {
                lastReportedCategoryId = category.id
                lastReportedApp = resolvedApp
                viewModel.setFocusedApp(resolvedApp)
                if (resolvedApp != null) {
                    lastHighlightedPackages[category.id] = resolvedApp.packageName
                    AppLog.d(TAG, "Focused game changed for ${category.id}: package=${resolvedApp.packageName}, label=${resolvedApp.label}")
                }
            }
        }
    }
}

@Composable
private fun DynamicLauncherBackground(
    selectedCategoryState: State<GameFocusCategory>,
    pagerStateProvider: (GameFocusCategory) -> PagerState,
    currentAppState: State<InstalledAppInfo?>,
    appColors: AppColors,
) {
    var backgroundApp by remember {
        mutableStateOf(Snapshot.withoutReadObservation { currentAppState.value })
    }
    val currentPagerStateProvider by rememberUpdatedState(pagerStateProvider)
    val currentFocusedAppState by rememberUpdatedState(currentAppState)

    // Single persistent observer (never torn down on category switch) with scroll & uncached-palette debounce
    LaunchedEffect(Unit) {
        snapshotFlow {
            val activePager = currentPagerStateProvider(selectedCategoryState.value)
            BackgroundFocusObservation(
                app = currentFocusedAppState.value,
                isScrolling = activePager.isScrollInProgress,
            )
        }.collectLatest { observation ->
            val target = observation.app
            if (observation.isScrolling) {
                delay(FTL_BACKGROUND_COLOR_DEBOUNCE_MS)
            } else if (target != null && AppPaletteExtractor.getCachedColorsOrNull(target) == null) {
                delay(FTL_BACKGROUND_UNCACHED_DEBOUNCE_MS)
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
private fun GalleryCategoryCoordinator(
    controller: CategoryTransitionController,
    selectedCategoryState: State<GameFocusCategory>,
    categories: List<GameFocusCategory>,
    appsByCategoryId: Map<String, List<InstalledAppInfo>>,
    resolveCategoryApps: (GameFocusCategory) -> List<InstalledAppInfo>,
    lastHighlightedPackages: Map<String, String>,
    pagerStateProvider: (GameFocusCategory) -> PagerState,
) {
    val selectedCategory = selectedCategoryState.value
    controller.onSelectedCategoryChanged(selectedCategory, categories)

    val currentCategories by rememberUpdatedState(categories)
    val currentAppsByCategoryId by rememberUpdatedState(appsByCategoryId)
    val currentResolveCategoryApps by rememberUpdatedState(resolveCategoryApps)
    val currentPagerStateProvider by rememberUpdatedState(pagerStateProvider)

    LaunchedEffect(Unit) {
        snapshotFlow {
            CategoryPrewarmSnapshot(
                selectedCategory = selectedCategoryState.value,
                categories = currentCategories,
                appsByCategoryId = currentAppsByCategoryId,
            )
        }.collectLatest { snapshot ->
            val categoryList = snapshot.categories
            if (categoryList.size <= 1) return@collectLatest
            // Wait until any in-flight StiffnessMediumLow slide has completely settled before pre-warming
            delay(FTL_PREWARM_DELAY_MS)

            val active = snapshot.selectedCategory
            val nextCategory = active.next(categoryList)
            if (nextCategory != active) {
                syncRememberedCategoryPage(
                    category = nextCategory,
                    apps = snapshot.appsByCategoryId[nextCategory.id] ?: currentResolveCategoryApps(nextCategory),
                    lastHighlightedPackages = lastHighlightedPackages,
                    pagerStateProvider = currentPagerStateProvider,
                )
                if (controller.prewarm(nextCategory, FTL_NEXT_NEIGHBOR_OFFSET)) {
                    AppLog.d(TAG, "Pre-warmed next gallery carousel: ${nextCategory.id}")
                    delay(FTL_IDLE_BATCH_PREWARM_GAP_MS)
                }
            }

            val prevCategory = active.previous(categoryList)
            if (prevCategory != active && prevCategory != nextCategory) {
                syncRememberedCategoryPage(
                    category = prevCategory,
                    apps = snapshot.appsByCategoryId[prevCategory.id] ?: currentResolveCategoryApps(prevCategory),
                    lastHighlightedPackages = lastHighlightedPackages,
                    pagerStateProvider = currentPagerStateProvider,
                )
                if (controller.prewarm(prevCategory, FTL_PREV_NEIGHBOR_OFFSET)) {
                    AppLog.d(TAG, "Pre-warmed previous gallery carousel: ${prevCategory.id}")
                    delay(FTL_IDLE_BATCH_PREWARM_GAP_MS)
                }
            }

            // Stagger remaining galleries during idle stillness so full gallery sweeps stay 100% pre-composed
            for (candidate in categoryList) {
                if (controller.entries.size >= FTL_MAX_RETAINED_CATEGORIES) break
                if (candidate == active || candidate == nextCategory || candidate == prevCategory) continue
                syncRememberedCategoryPage(
                    category = candidate,
                    apps = snapshot.appsByCategoryId[candidate.id] ?: currentResolveCategoryApps(candidate),
                    lastHighlightedPackages = lastHighlightedPackages,
                    pagerStateProvider = currentPagerStateProvider,
                )
                if (controller.prewarm(candidate, FTL_NEXT_NEIGHBOR_OFFSET)) {
                    AppLog.d(TAG, "Pre-warmed gallery carousel during idle: ${candidate.id}")
                    delay(FTL_IDLE_BATCH_PREWARM_GAP_MS)
                }
            }
        }
    }
}

private suspend fun syncRememberedCategoryPage(
    category: GameFocusCategory,
    apps: List<InstalledAppInfo>,
    lastHighlightedPackages: Map<String, String>,
    pagerStateProvider: (GameFocusCategory) -> PagerState,
) {
    if (apps.isEmpty()) return
    val pagerState = pagerStateProvider(category)
    val lastPkg = lastHighlightedPackages[category.id]
    val targetIndex =
        if (lastPkg != null) {
            val found = apps.indexOfFirst { it.packageName == lastPkg }
            if (found >= 0) found else 0
        } else {
            0
        }
    val safeIndex = targetIndex.coerceIn(0, apps.lastIndex)
    if (pagerState.currentPage != safeIndex) {
        pagerState.scrollToPage(safeIndex)
    }
}

@Composable
private fun GalleryCategoryCarouselStack(
    controller: CategoryTransitionController,
    appsByCategoryId: Map<String, List<InstalledAppInfo>>,
    resolveCategoryApps: (GameFocusCategory) -> List<InstalledAppInfo>,
    uniqueLettersByCategoryId: Map<String, List<Char>>,
    favoritesSet: Set<String>,
    hiddenSet: Set<String>,
    pairedApps: Map<String, String>,
    availableAppsByPackage: Map<String, InstalledAppInfo>,
    isScreenReaderActive: Boolean,
    letterNavState: LetterNavigationOverlayState,
    appColors: AppColors,
    pagerStateProvider: (GameFocusCategory) -> PagerState,
    onAppClickTop: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var stackWidthPx by remember { mutableIntStateOf(0) }
    val containerWidth =
        if (stackWidthPx > 0) {
            with(density) { stackWidthPx.toDp() }
        } else {
            configuration.screenWidthDp.dp
        }
    val onSettled: (CategorySlideEntry) -> Unit =
        remember(controller) {
            { settledEntry ->
                controller.onEntrySettled(settledEntry)
            }
        }

    DisposableEffect(controller) {
        onDispose {
            controller.resetToSelected()
        }
    }

    // Gallery gamepad input is dispatched directly via FocusTopLauncherActivity.onKeyDown/onGenericMotionEvent
    // (primary overlays sit outside this stack), so disabling Compose focus targets here eliminates ~2.8 ms
    // of FocusOwnerImpl.clearFocus + full-tree 2D focus search on every category switch.
    Box(
        modifier =
            modifier
                .focusProperties { canFocus = false }
                .onSizeChanged { size ->
                    if (stackWidthPx != size.width) {
                        stackWidthPx = size.width
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        for (entry in controller.entries) {
            key(entry.category.id) {
                val pageCategory = entry.category
                val pageApps = appsByCategoryId[pageCategory.id] ?: resolveCategoryApps(pageCategory)
                val pageUniqueLetters =
                    uniqueLettersByCategoryId[pageCategory.id]
                        ?: LetterNavigationHelper.getUniqueStartingLetters(pageApps)
                val pagePagerState = pagerStateProvider(pageCategory)

                GalleryCategoryPage(
                    entry = entry,
                    categoryApps = pageApps,
                    uniqueLetters = pageUniqueLetters,
                    pagerState = pagePagerState,
                    containerWidth = containerWidth,
                    favoritesSet = favoritesSet,
                    hiddenSet = hiddenSet,
                    pairedApps = pairedApps,
                    availableAppsByPackage = availableAppsByPackage,
                    isScreenReaderActive = isScreenReaderActive,
                    letterNavState = letterNavState,
                    appColors = appColors,
                    onSettled = onSettled,
                    onAppClickTop = onAppClickTop,
                )
            }
        }
    }
}

@Composable
private fun GalleryCategoryPage(
    entry: CategorySlideEntry,
    categoryApps: List<InstalledAppInfo>,
    uniqueLetters: List<Char>,
    pagerState: PagerState,
    containerWidth: Dp,
    favoritesSet: Set<String>,
    hiddenSet: Set<String>,
    pairedApps: Map<String, String>,
    availableAppsByPackage: Map<String, InstalledAppInfo>,
    isScreenReaderActive: Boolean,
    letterNavState: LetterNavigationOverlayState,
    appColors: AppColors,
    onSettled: (CategorySlideEntry) -> Unit,
    onAppClickTop: (InstalledAppInfo) -> Unit,
) {
    val category = entry.category

    CategorySlideEffect(
        entry = entry,
        onSettled = onSettled,
    )

    // Keep Placeable placed off-screen with alpha=0f when inactive so SubcomposeLayout (HorizontalPager)
    // never deactivates or tears down its 11 visible card subcompositions across category switches,
    // while HardwareUI short-circuits alpha=0f RenderNodes in 0.00 ms on RenderThread.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (isScreenReaderActive && !entry.isSelected) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
                ).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val z = if (entry.isActive) entry.zOrder else FTL_INACTIVE_Z_ORDER
                        placeable.placeWithLayer(0, 0, zIndex = z) {
                            if (entry.isActive) {
                                val offset = entry.renderOffset
                                translationY = offset * constraints.maxHeight
                                alpha = (FTL_FULL_ALPHA - abs(offset)).coerceIn(0f, FTL_FULL_ALPHA)
                            } else {
                                translationY = FTL_INACTIVE_PARK_MULTIPLIER * constraints.maxHeight
                                alpha = 0f
                            }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (categoryApps.isEmpty()) {
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
                                stringResource(R.string.gamefocus_no_collection_items)
                            }

                            else -> {
                                stringResource(R.string.focus_launcher_no_apps)
                            }
                        },
                    style = MaterialTheme.typography.titleMedium.copy(color = appColors.onSurfaceSecondary),
                )
            }
        } else {
            val currentOnAppClickTop by rememberUpdatedState(onAppClickTop)
            val keyProvider: (Int) -> Any =
                remember(category.id, categoryApps) {
                    { page ->
                        "${category.id}_${categoryApps.getOrNull(page)?.packageName ?: page}"
                    }
                }
            val handleCarouselItemClick: (Int) -> Unit =
                remember(categoryApps) {
                    { actualIndex ->
                        val appInfo = categoryApps.getOrNull(actualIndex)
                        if (appInfo != null) currentOnAppClickTop(appInfo)
                    }
                }
            val isItemHiddenProvider: (Int) -> Boolean =
                remember(categoryApps, hiddenSet) {
                    { actualIndex ->
                        categoryApps.getOrNull(actualIndex)?.let {
                            hiddenSet.contains(it.packageName)
                        } ?: false
                    }
                }
            val pageCurrentAppState =
                remember(pagerState, categoryApps) {
                    derivedStateOf {
                        if (categoryApps.isEmpty()) {
                            null
                        } else {
                            categoryApps.getOrNull(pagerState.currentPage.coerceIn(0, categoryApps.lastIndex))
                        }
                    }
                }

            Column(
                modifier = Modifier.padding(top = FTL_GALLERY_TOP_OFFSET),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HorizontalPosterCarousel(
                    itemCount = categoryApps.size,
                    pagerState = pagerState,
                    key = keyProvider,
                    containerWidth = containerWidth,
                    onItemClick = handleCarouselItemClick,
                    posterWidth = FTL_POSTER_WIDTH,
                    posterHeight = FTL_POSTER_HEIGHT,
                    posterSpacing = FTL_POSTER_SPACING,
                    carouselHeight = FTL_CAROUSEL_HEIGHT,
                    posterCornerRadius = FTL_POSTER_CORNER_RADIUS,
                    cardBackgroundColor = { actualIndex, isSelected ->
                        val appInfo = categoryApps.getOrNull(actualIndex)
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
                    val appInfo = categoryApps[actualIndex]
                    PosterCardContent(
                        appInfo = appInfo,
                        isFavorite = favoritesSet.contains(appInfo.packageName),
                        isHidden = hiddenSet.contains(appInfo.packageName),
                    )
                }

                Spacer(modifier = Modifier.height(FTL_TITLE_GAP))

                GalleryTitleOrLetterSection(
                    currentAppState = pageCurrentAppState,
                    uniqueLetters = uniqueLetters,
                    letterNavState = letterNavState,
                    entry = entry,
                    pairedApps = pairedApps,
                    availableAppsByPackage = availableAppsByPackage,
                    appColors = appColors,
                )
            }
        }
    }
}

@Composable
private fun CategorySlideEffect(
    entry: CategorySlideEntry,
    onSettled: (CategorySlideEntry) -> Unit,
) {
    val currentOnSettled by rememberUpdatedState(onSettled)
    LaunchedEffect(entry) {
        snapshotFlow { entry.animGeneration }
            .collectLatest {
                val start = Snapshot.withoutReadObservation { entry.renderOffset }
                val target = Snapshot.withoutReadObservation { entry.targetOffset }
                if (start == target) {
                    if (!Snapshot.withoutReadObservation { entry.isSelected } && target != 0f) {
                        currentOnSettled(entry)
                    }
                    return@collectLatest
                }
                animate(
                    initialValue = start,
                    targetValue = target,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = FTL_SLIDE_VISIBILITY_THRESHOLD,
                        ),
                ) { value, _ ->
                    entry.renderOffset = value
                }
                entry.renderOffset = target
                currentOnSettled(entry)
            }
    }
}

@Composable
private fun GalleryTitleOrLetterSection(
    currentAppState: State<InstalledAppInfo?>,
    uniqueLetters: List<Char>,
    letterNavState: LetterNavigationOverlayState,
    entry: CategorySlideEntry,
    pairedApps: Map<String, String>,
    availableAppsByPackage: Map<String, InstalledAppInfo>,
    appColors: AppColors,
) {
    val showLetterOverlay by remember(entry, letterNavState) {
        derivedStateOf { letterNavState.isActive && entry.isSelected }
    }
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
    val pairedAppsState = rememberUpdatedState(pairedApps)
    val isCurrentPaired by remember(currentAppState) {
        derivedStateOf {
            val app = currentAppState.value
            app != null && pairedAppsState.value.containsKey(app.packageName)
        }
    }
    val appInfoProvider: () -> InstalledAppInfo? =
        remember(currentAppState) {
            { currentAppState.value }
        }

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
            val lastActionsHolder = remember { mutableStateOf<List<ExpandableActionItem>>(emptyList()) }
            val lastDividerHolder = remember { mutableStateOf<Int?>(null) }
            val actions: List<ExpandableActionItem>
            val dividerAfterIndex: Int?
            if (isMainOptionsMenuExpanded) {
                val currentApp = currentAppState.value
                val isCurrentFavorite = currentApp != null && favoritesSet.contains(currentApp.packageName)
                val isCurrentHidden = currentApp != null && hiddenSet.contains(currentApp.packageName)
                actions =
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
                    }.also { built ->
                        lastActionsHolder.value = built
                    }
                dividerAfterIndex =
                    (if (currentApp != null && actions.size > 1) actions.lastIndex - 1 else null).also { div ->
                        lastDividerHolder.value = div
                    }
            } else {
                actions = lastActionsHolder.value
                dividerAfterIndex = lastDividerHolder.value
            }
            ExpandableActionsMenu(
                isExpanded = isMainOptionsMenuExpanded,
                onExpandedChange = viewModel::setMainOptionsMenuExpanded,
                actions = actions,
                selectedIndex = mainMenuSelectedIndex,
                dividerAfterIndex = dividerAfterIndex,
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
                isPaired = isCurrentPaired,
                enabled = isControlsEnabled && !isMainOptionsMenuExpanded,
                onLaunchTop = onAppClickTop,
                onLaunchBottom = onAppClickBottom,
                appInfoProvider = appInfoProvider,
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
    appInfo: InstalledAppInfo? = null,
    isPaired: Boolean = false,
    enabled: Boolean,
    onLaunchTop: (InstalledAppInfo) -> Unit,
    onLaunchBottom: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
    appInfoProvider: () -> InstalledAppInfo? = { appInfo },
) {
    val currentOnLaunchTop by rememberUpdatedState(onLaunchTop)
    val currentOnLaunchBottom by rememberUpdatedState(onLaunchBottom)
    val currentAppInfoProvider by rememberUpdatedState(appInfoProvider)
    val handleLaunchTop =
        remember {
            {
                val target = currentAppInfoProvider()
                if (target != null) currentOnLaunchTop(target)
            }
        }
    val handleLaunchBottom =
        remember {
            {
                val target = currentAppInfoProvider()
                if (target != null) currentOnLaunchBottom(target)
            }
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GamePadButtonAction(
            button = GamePadButton.BUTTON_A,
            text = stringResource(if (isPaired) R.string.gamefocus_launch_dual else R.string.gamefocus_launch_top),
            enabled = enabled,
            onClick = handleLaunchTop,
        )

        Spacer(modifier = Modifier.width(FTL_BUTTON_GAP))

        GamePadButtonAction(
            button = GamePadButton.BUTTON_X,
            text = stringResource(R.string.gamefocus_launch_bottom),
            enabled = enabled,
            onClick = handleLaunchBottom,
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
    selectedCategoryState: State<GameFocusCategory>,
    categories: List<GameFocusCategory>,
    onCategoryUp: () -> Unit = {},
    onCategoryDown: () -> Unit = {},
    enabled: Boolean = true,
    areButtonPromptsVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val selectedCategory = selectedCategoryState.value
    val selectedIndex =
        remember(selectedCategory, categories) {
            categories.indexOf(selectedCategory).coerceAtLeast(0)
        }
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentSelectedCategory by rememberUpdatedState(selectedCategory)
    val currentCategories by rememberUpdatedState(categories)
    val currentOnCategoryUp by rememberUpdatedState(onCategoryUp)
    val currentOnCategoryDown by rememberUpdatedState(onCategoryDown)
    val labelProvider: @Composable (GameFocusCategory) -> String =
        remember {
            { category ->
                getCategoryName(category)
            }
        }

    val handleIndexChange: (Int) -> Unit =
        remember {
            { index ->
                val list = currentCategories
                if (index in list.indices) {
                    val targetCategory = list[index]
                    if (targetCategory != currentSelectedCategory) {
                        if ((currentSelectedIndex + 1).floorMod(list.size) == index) {
                            currentOnCategoryDown()
                        } else {
                            currentOnCategoryUp()
                        }
                    }
                }
            }
        }

    VerticalRollingCarousel(
        selectedIndex = selectedIndex,
        items = categories,
        onSelectedIndexChange = handleIndexChange,
        labelProvider = labelProvider,
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

    val initiallyHidden = remember { isHidden }
    var wasEverHidden by remember { mutableStateOf(isHidden) }
    if (isHidden && !wasEverHidden) {
        wasEverHidden = true
    }

    val visibilityOffAlphaAnim =
        if (wasEverHidden) {
            val anim = remember { Animatable(if (initiallyHidden) FTL_HIDDEN_BADGE_ALPHA else FTL_VISIBLE_BADGE_ALPHA) }
            LaunchedEffect(isHidden) {
                val target = if (isHidden) FTL_HIDDEN_BADGE_ALPHA else FTL_VISIBLE_BADGE_ALPHA
                anim.animateTo(target, tween(durationMillis = FTL_HIDE_ANIMATION_DURATION_MS))
            }
            anim
        } else {
            null
        }
    val showVisibilityBadge by remember(visibilityOffAlphaAnim) {
        derivedStateOf { (visibilityOffAlphaAnim?.value ?: 0f) > 0f }
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
                        Modifier.size(FTL_ICON_SIZE)
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
                        .graphicsLayer { alpha = visibilityOffAlphaAnim?.value ?: 0f },
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
