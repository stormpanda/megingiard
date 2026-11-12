package com.stormpanda.megingiard.gamefocus

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.ExpandableActionsMenu
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.GamePadButtonIcon
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MaterialSymbol
import kotlinx.coroutines.flow.collectLatest
import android.graphics.Paint as NativePaint
import androidx.compose.animation.Animatable as ColorAnimatable

private const val TAG = "FocusLibraryScreen"

internal const val FLS_GRID_COLUMNS = 6
private val FLS_CORNER_RADIUS = 16.dp
private val FLS_CARD_SHAPE = RoundedCornerShape(FLS_CORNER_RADIUS)
private val FLS_ROM_ICON_CORNER_RADIUS = 14.dp
private val FLS_ROM_ICON_SHAPE = RoundedCornerShape(FLS_ROM_ICON_CORNER_RADIUS)
private val FLS_ICON_SIZE = 64.dp
private val FLS_GRID_PADDING = 16.dp
private val FLS_GRID_SPACING = 12.dp
private const val FLS_CATEGORY_ROLL_Y_ANGLE_DEG = 25f
private val FLS_FOCUS_BORDER_WIDTH = 3.dp
private val FLS_ROW_PEEK_OFFSET = 32.dp
private val FLS_GRID_CONTENT_PADDING_TOP = 112.dp
private val FLS_GRID_CONTENT_PADDING_BOTTOM = 112.dp
private val FLS_BOTTOM_GRADIENT_HEIGHT = 96.dp
private val FLS_TOP_GRADIENT_HEIGHT = 112.dp
private val FLS_FALLBACK_ICON_SIZE = 36.dp
private val FLS_LABEL_GAP = 6.dp
private val FLS_SHADOW_BLUR_RADIUS = 16.dp
private val FLS_SHADOW_SPREAD = 2.dp
private const val FLS_SHADOW_ALPHA = 0.45f
private val FLS_BOTTOM_BAR_PADDING_HORIZONTAL = 12.dp
private val FLS_BOTTOM_BAR_PADDING_VERTICAL = 4.dp
private val FLS_HEADER_PADDING_HORIZONTAL = 24.dp
private val FLS_HEADER_PADDING_VERTICAL = 12.dp
private val FLS_HEADER_BAR_HEIGHT = 40.dp
private val FLS_HEADER_SPACING_SMALL = 8.dp
private val FLS_L1R1_ICON_SIZE = 20.dp
private val FLS_HEADER_SPACING_MEDIUM = 16.dp
private val FLS_CARD_INNER_PADDING = 10.dp
private val FLS_BADGE_PADDING = 8.dp
private val FLS_BADGE_ICON_SIZE = 18.dp

private const val FLS_FOCUS_BORDER_SPRING_STIFFNESS = 1800f
private const val FLS_FOCUS_BORDER_ALPHA_DURATION_MS = 150
private const val FLS_FOCUSED_SCALE = 1.05f
private const val FLS_UNFOCUSED_SCALE = 1.0f
private const val FLS_CARD_BG_ANIM_DURATION_MS = 300
private const val FLS_CARD_SCALE_ANIM_DURATION_MS = 200
private const val FLS_UNFOCUSED_CARD_BG_ALPHA = 0.5f
private const val FLS_SPREAD_HALF_DIVISOR = 2f
private const val FLS_MIN_RECT_DIMENSION_PX = 1f

private const val FLS_HIDDEN_CARD_ALPHA = 0.4f
private const val FLS_VISIBLE_CARD_ALPHA = 1.0f
private const val FLS_HIDDEN_BADGE_ALPHA = 1.0f
private const val FLS_VISIBLE_BADGE_ALPHA = 0.0f
private const val FLS_HIDE_ANIMATION_DURATION_MS = 300
private const val FLS_MARQUEE_INITIAL_DELAY_MS = 500
private const val FLS_PROMPT_ANIM_DURATION_MS = 200
private const val FLS_PROMPT_SLIDE_DIVISOR = 2
private val FLS_BUTTON_SLIDE_OFFSET = 10.dp
private const val FLS_HEADER_REEL_SLIDE_DIVISOR = 3
private const val FLS_CAMERA_DISTANCE_MULTIPLIER = 16
private const val FLS_NEIGHBOR_1_ALPHA = 0.45f
private const val FLS_NEIGHBOR_1_ANGLE_FACTOR = 0.7f
private const val FLS_NEIGHBOR_2_ALPHA = 0.25f
private const val FLS_NEIGHBOR_2_ANGLE_FACTOR = 1.4f
private const val FLS_GRADIENT_STOP_SOLID = 0.2f
private const val FLS_GRADIENT_STOP_MID = 0.5f
private const val FLS_GRADIENT_STOP_SOLID_BOTTOM = 0.8f
private const val FLS_GRADIENT_MID_ALPHA = 0.8f

private val FLS_NEIGHBOR_STYLES =
    listOf(
        FLS_NEIGHBOR_1_ALPHA to FLS_NEIGHBOR_1_ANGLE_FACTOR,
        FLS_NEIGHBOR_2_ALPHA to FLS_NEIGHBOR_2_ANGLE_FACTOR,
    )

private val LibraryTab.stringResId: Int
    get() =
        when (this) {
            LibraryTab.GAMES -> R.string.gamefocus_library_tab_games
            LibraryTab.APPS -> R.string.gamefocus_library_tab_apps
            is LibraryTab.RomSystem -> 0
        }

@Stable
private class LibraryFocusOverlayState {
    var targetRect by mutableStateOf<Rect?>(null)
    var isFirstPositioned by mutableStateOf(false)

    fun reset() {
        targetRect = null
        isFirstPositioned = false
    }

    fun updateFrom(
        coords: LayoutCoordinates,
        parentCoords: LayoutCoordinates,
    ) {
        if (coords.isAttached && parentCoords.isAttached) {
            val positionInParent = parentCoords.localPositionOf(coords, Offset.Zero)
            val size = coords.size
            val newRect =
                Rect(
                    left = positionInParent.x,
                    top = positionInParent.y,
                    right = positionInParent.x + size.width,
                    bottom = positionInParent.y + size.height,
                )
            if (targetRect != newRect) {
                targetRect = newRect
                if (!isFirstPositioned) {
                    isFirstPositioned = true
                }
            }
        }
    }
}

private class GridCoordinatesTracker {
    var gridContainerCoordinates: LayoutCoordinates? = null
    var focusedItemCoordinates: LayoutCoordinates? = null
    val visibleItemCoords = HashMap<Int, LayoutCoordinates>()

    fun clear() {
        focusedItemCoordinates = null
        visibleItemCoords.clear()
    }
}

private suspend fun scrollToFocusedItem(
    gridState: LazyGridState,
    targetIndex: Int,
    columns: Int,
    peekOffsetPx: Int,
) {
    val targetRow = targetIndex / columns
    val firstItemOfRow = targetRow * columns
    val scrollOffset = if (targetRow == 0) 0 else peekOffsetPx.coerceAtLeast(0)
    gridState.animateScrollToItem(index = firstItemOfRow, scrollOffset = scrollOffset)
}

@Composable
fun FocusLibraryScreen(
    allApps: List<InstalledAppInfo>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    focusedIndexState: State<Int>,
    onFocusedIndexChange: (Int) -> Unit,
    onAppClickTop: (InstalledAppInfo) -> Unit,
    onAppClickBottom: (InstalledAppInfo) -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
    hiddenSet: Set<String> = emptySet(),
    isOptionsMenuExpanded: Boolean = false,
    onOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    selectedIndex: Int = 0,
    onToggleHidden: (InstalledAppInfo) -> Unit = {},
    onEditArtwork: (InstalledAppInfo) -> Unit = {},
    onOpenAppInfo: (InstalledAppInfo) -> Unit = {},
    onUninstallApp: (InstalledAppInfo) -> Unit = {},
    onChangeCore: (CustomRomFolder) -> Unit = {},
    onAddRomFolder: () -> Unit = {},
    onRemoveRomFolder: (CustomRomFolder) -> Unit = {},
    onOpenManageGalleries: () -> Unit = {},
    enabled: Boolean = true,
    tabs: List<LibraryTab> = listOf(LibraryTab.GAMES, LibraryTab.APPS),
    isRemoveRomFolderDialogOpen: Boolean = false,
    onRemoveRomFolderDialogOpenChange: (Boolean) -> Unit = {},
    removeRomFolderDialogSelectedIndex: Int = 0,
    onRemoveRomFolderDialogSelectedIndexChange: (Int) -> Unit = {},
    folderToRemove: CustomRomFolder? = null,
    onFolderToRemoveChange: (CustomRomFolder?) -> Unit = {},
    areButtonPromptsVisible: Boolean = true,
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current
    val romFolders by RomManager.romFolders.collectAsState()
    val peekOffsetPx = remember(density) { with(density) { FLS_ROW_PEEK_OFFSET.roundToPx() } }

    // Precompute filtered tab lists once per catalog change so L1/R1 tab transitions are O(1) lookups.
    val appsByTabId =
        remember(allApps, tabs, selectedTab) {
            buildMap {
                for (tab in tabs) {
                    put(tab.id, tab.filterApps(allApps))
                }
                if (!containsKey(selectedTab.id)) {
                    put(selectedTab.id, selectedTab.filterApps(allApps))
                }
            }
        }
    val activeApps = appsByTabId[selectedTab.id] ?: emptyList()

    val topGradientBrush =
        remember(appColors.appBackground) {
            Brush.verticalGradient(
                0.0f to appColors.appBackground,
                FLS_GRADIENT_STOP_SOLID to appColors.appBackground,
                FLS_GRADIENT_STOP_MID to appColors.appBackground.copy(alpha = FLS_GRADIENT_MID_ALPHA),
                FLS_VISIBLE_CARD_ALPHA to Color.Transparent,
            )
        }
    val bottomGradientBrush =
        remember(appColors.appBackground) {
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                FLS_GRADIENT_STOP_MID to appColors.appBackground.copy(alpha = FLS_GRADIENT_MID_ALPHA),
                FLS_GRADIENT_STOP_SOLID_BOTTOM to appColors.appBackground,
                FLS_VISIBLE_CARD_ALPHA to appColors.appBackground,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(appColors.appBackground),
    ) {
        // Scrollable Condensed App Grid with Horizontal Category Switch Animation
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (initialState.next(tabs) == targetState) 1 else -1
                (slideInHorizontally { width -> width * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -width * direction } + fadeOut())
            },
            label = "LibraryCategoryTransition",
            modifier = Modifier.fillMaxSize(),
        ) { activeTab ->
            val displayedApps =
                remember(allApps, activeTab, appsByTabId) {
                    appsByTabId[activeTab.id] ?: activeTab.filterApps(allApps)
                }
            val gridState: LazyGridState = rememberLazyGridState()

            if (displayedApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.focus_launcher_no_apps),
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors.onSurfaceSecondary,
                    )
                }
            } else {
                val overlayState = remember(activeTab) { LibraryFocusOverlayState() }
                val coordsTracker = remember(activeTab) { GridCoordinatesTracker() }
                val currentOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)
                val currentOnAppClickTop by rememberUpdatedState(onAppClickTop)

                LibraryGridFocusScroller(
                    activeTab = activeTab,
                    focusedIndexState = focusedIndexState,
                    itemCount = displayedApps.size,
                    gridState = gridState,
                    peekOffsetPx = peekOffsetPx,
                    overlayState = overlayState,
                    coordsTracker = coordsTracker,
                )

                val handleItemClick: (Int, InstalledAppInfo) -> Unit =
                    remember(activeTab) {
                        { index, app ->
                            currentOnFocusedIndexChange(index)
                            currentOnAppClickTop(app)
                        }
                    }

                val handleItemPositioned: (Int, Boolean, LayoutCoordinates) -> Unit =
                    remember(activeTab, focusedIndexState) {
                        { index, itemIsFocused, itemCoords ->
                            coordsTracker.visibleItemCoords[index] = itemCoords
                            if (itemIsFocused || index == focusedIndexState.value) {
                                coordsTracker.focusedItemCoordinates = itemCoords
                                coordsTracker.gridContainerCoordinates?.let { parentCoords ->
                                    overlayState.updateFrom(itemCoords, parentCoords)
                                }
                            }
                        }
                    }

                LibraryFocusPositionSync(
                    focusedIndexState = focusedIndexState,
                    coordsTracker = coordsTracker,
                    overlayState = overlayState,
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onGloballyPositioned { coords ->
                                coordsTracker.gridContainerCoordinates = coords
                                val currentFocusedCoords =
                                    coordsTracker.visibleItemCoords[focusedIndexState.value] ?: coordsTracker.focusedItemCoordinates
                                currentFocusedCoords?.let { itemCoords ->
                                    overlayState.updateFrom(itemCoords, coords)
                                }
                            },
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(FLS_GRID_COLUMNS),
                        state = gridState,
                        contentPadding =
                            PaddingValues(
                                start = FLS_GRID_PADDING,
                                top = FLS_GRID_CONTENT_PADDING_TOP,
                                end = FLS_GRID_PADDING,
                                bottom = FLS_GRID_CONTENT_PADDING_BOTTOM,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(FLS_GRID_SPACING),
                        verticalArrangement = Arrangement.spacedBy(FLS_GRID_SPACING),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = displayedApps,
                            key = { _, app -> app.packageName },
                        ) { index, app ->
                            val isFocused by remember(focusedIndexState, index) {
                                derivedStateOf { focusedIndexState.value == index }
                            }
                            val isHidden = hiddenSet.contains(app.packageName)
                            LibraryGridItem(
                                index = index,
                                appInfo = app,
                                isFocused = isFocused,
                                isHidden = isHidden,
                                onItemClick = handleItemClick,
                                onItemPositioned = handleItemPositioned,
                            )
                        }
                    }

                    // Isolated leaf overlay so per-pixel scroll position updates never recompose LazyVerticalGrid.
                    LibraryFocusIndicatorOverlay(
                        overlayState = overlayState,
                        appColors = appColors,
                    )
                }
            }
        }

        // Top edge shadow overlay to improve header readability
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(FLS_TOP_GRADIENT_HEIGHT)
                    .background(brush = topGradientBrush),
        )

        // Header Bar with Title, Subtitle, and Tabs hovering over the grid
        val handleHeaderTabSelected: (LibraryTab) -> Unit =
            remember(onTabSelected) {
                { tab ->
                    AppLog.i(TAG, "Library tab clicked/selected: ${tab.id}")
                    onTabSelected(tab)
                }
            }
        LibraryHeaderBar(
            selectedTab = selectedTab,
            onTabSelected = handleHeaderTabSelected,
            onCloseRequested = onCloseRequested,
            enabled = enabled,
            tabs = tabs,
            areButtonPromptsVisible = areButtonPromptsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Bottom edge shadow overlay to improve button readability
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FLS_BOTTOM_GRADIENT_HEIGHT)
                    .background(brush = bottomGradientBrush),
        )

        if (isOptionsMenuExpanded) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onOptionsMenuExpandedChange(false)
                            }
                        },
            )
        }

        LibraryBottomControls(
            activeApps = activeApps,
            focusedIndexState = focusedIndexState,
            hiddenSet = hiddenSet,
            selectedTab = selectedTab,
            romFolders = romFolders,
            areButtonPromptsVisible = areButtonPromptsVisible,
            isOptionsMenuExpanded = isOptionsMenuExpanded,
            onOptionsMenuExpandedChange = onOptionsMenuExpandedChange,
            selectedIndex = selectedIndex,
            enabled = enabled,
            onEditArtwork = onEditArtwork,
            onToggleHidden = onToggleHidden,
            onOpenAppInfo = onOpenAppInfo,
            onUninstallApp = onUninstallApp,
            onAddRomFolder = onAddRomFolder,
            onRemoveRomFolderDialogSelectedIndexChange = onRemoveRomFolderDialogSelectedIndexChange,
            onRemoveRomFolderDialogOpenChange = onRemoveRomFolderDialogOpenChange,
            onChangeCore = onChangeCore,
            onOpenManageGalleries = onOpenManageGalleries,
            onAppClickTop = onAppClickTop,
            onAppClickBottom = onAppClickBottom,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (isRemoveRomFolderDialogOpen) {
            RemoveRomFolderDialog(
                romFolders = romFolders,
                selectedIndex = removeRomFolderDialogSelectedIndex,
                onSelectedIndexChange = onRemoveRomFolderDialogSelectedIndexChange,
                onDismiss = { onRemoveRomFolderDialogOpenChange(false) },
                onSelectFolder = { folder ->
                    onFolderToRemoveChange(folder)
                    onRemoveRomFolderDialogOpenChange(false)
                },
            )
        }

        folderToRemove?.let { folder ->
            AppAlertDialog(
                onDismissRequest = { onFolderToRemoveChange(null) },
                confirmButton = {
                    GamePadButtonAction(
                        button = GamePadButton.BUTTON_A,
                        text = stringResource(R.string.gamefocus_option_manage_rom_folders),
                        onClick = {
                            onRemoveRomFolder(folder)
                            onFolderToRemoveChange(null)
                        },
                    )
                },
                dismissButton = {
                    GamePadButtonAction(
                        button = GamePadButton.BUTTON_B,
                        text = stringResource(R.string.settings_cancel),
                        onClick = { onFolderToRemoveChange(null) },
                    )
                },
                title = { Text(stringResource(R.string.gamefocus_dialog_remove_confirm_title)) },
                text = { Text(stringResource(R.string.gamefocus_dialog_remove_confirm_msg, folder.systemName)) },
            )
        }
    }
}

@Composable
private fun LibraryGridFocusScroller(
    activeTab: LibraryTab,
    focusedIndexState: State<Int>,
    itemCount: Int,
    gridState: LazyGridState,
    peekOffsetPx: Int,
    overlayState: LibraryFocusOverlayState,
    coordsTracker: GridCoordinatesTracker,
) {
    var lastScrolledRow by remember(activeTab) { mutableIntStateOf(-1) }
    val currentItemCount by rememberUpdatedState(itemCount)

    LaunchedEffect(activeTab) {
        overlayState.reset()
        coordsTracker.clear()
        lastScrolledRow = -1
        gridState.scrollToItem(0)
    }

    LaunchedEffect(activeTab, gridState, peekOffsetPx) {
        snapshotFlow { focusedIndexState.value to currentItemCount }
            .collectLatest { (focusedIndex, totalCount) ->
                if (totalCount > 0 && focusedIndex >= 0) {
                    val safeIndex = focusedIndex.coerceIn(0, totalCount - 1)
                    val targetRow = safeIndex / FLS_GRID_COLUMNS
                    if (targetRow != lastScrolledRow) {
                        lastScrolledRow = targetRow
                        AppLog.d(TAG, "Library focused index changed to $safeIndex (row $targetRow, total $totalCount)")
                        scrollToFocusedItem(gridState, safeIndex, FLS_GRID_COLUMNS, peekOffsetPx)
                    } else {
                        AppLog.d(TAG, "Library focused index changed to $safeIndex in same row $targetRow (total $totalCount)")
                    }
                }
            }
    }
}

@Composable
private fun LibraryFocusPositionSync(
    focusedIndexState: State<Int>,
    coordsTracker: GridCoordinatesTracker,
    overlayState: LibraryFocusOverlayState,
) {
    val focusedIndex = focusedIndexState.value
    SideEffect {
        val currentFocusedCoords =
            coordsTracker.visibleItemCoords[focusedIndex] ?: coordsTracker.focusedItemCoordinates
        val parentCoords = coordsTracker.gridContainerCoordinates
        if (currentFocusedCoords != null && parentCoords != null) {
            overlayState.updateFrom(currentFocusedCoords, parentCoords)
        }
    }
}

@Composable
private fun LibraryBottomControls(
    activeApps: List<InstalledAppInfo>,
    focusedIndexState: State<Int>,
    hiddenSet: Set<String>,
    selectedTab: LibraryTab,
    romFolders: List<CustomRomFolder>,
    areButtonPromptsVisible: Boolean,
    isOptionsMenuExpanded: Boolean,
    onOptionsMenuExpandedChange: (Boolean) -> Unit,
    selectedIndex: Int,
    enabled: Boolean,
    onEditArtwork: (InstalledAppInfo) -> Unit,
    onToggleHidden: (InstalledAppInfo) -> Unit,
    onOpenAppInfo: (InstalledAppInfo) -> Unit,
    onUninstallApp: (InstalledAppInfo) -> Unit,
    onAddRomFolder: () -> Unit,
    onRemoveRomFolderDialogSelectedIndexChange: (Int) -> Unit,
    onRemoveRomFolderDialogOpenChange: (Boolean) -> Unit,
    onChangeCore: (CustomRomFolder) -> Unit,
    onOpenManageGalleries: () -> Unit,
    onAppClickTop: (InstalledAppInfo) -> Unit,
    onAppClickBottom: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusedApp by remember(activeApps, focusedIndexState) {
        derivedStateOf { activeApps.getOrNull(focusedIndexState.value.coerceAtLeast(0)) }
    }
    val isCurrentHidden = focusedApp != null && hiddenSet.contains(focusedApp!!.packageName)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = FLS_BOTTOM_BAR_PADDING_HORIZONTAL,
                    end = FLS_BOTTOM_BAR_PADDING_HORIZONTAL,
                    bottom = FLS_BOTTOM_BAR_PADDING_VERTICAL,
                    top = FLS_BOTTOM_BAR_PADDING_VERTICAL,
                ),
    ) {
        // Lower Left: Library Action Menu
        AnimatedVisibility(
            visible = areButtonPromptsVisible || isOptionsMenuExpanded,
            enter =
                fadeIn(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) +
                    slideInVertically(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) { it / FLS_PROMPT_SLIDE_DIVISOR },
            exit =
                fadeOut(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) +
                    slideOutVertically(
                        animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS),
                    ) { it / FLS_PROMPT_SLIDE_DIVISOR },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Box {
                val currentOnEditArtwork by rememberUpdatedState(onEditArtwork)
                val actions =
                    remember(
                        focusedApp,
                        isCurrentHidden,
                        selectedTab,
                        romFolders,
                    ) {
                        buildLibraryMenuActions(
                            context = context,
                            focusedApp = focusedApp,
                            isHidden = isCurrentHidden,
                            selectedTab = selectedTab,
                            romFolders = romFolders,
                            onEditArtwork = { currentOnEditArtwork(it) },
                            onToggleHidden = onToggleHidden,
                            onOpenAppInfo = onOpenAppInfo,
                            onUninstallApp = onUninstallApp,
                            onAddRomFolder = onAddRomFolder,
                            onManageRomFolders = {
                                onRemoveRomFolderDialogSelectedIndexChange(0)
                                onRemoveRomFolderDialogOpenChange(true)
                            },
                            onChangeCore = onChangeCore,
                            onOpenManageGalleries = onOpenManageGalleries,
                            onDismissMenu = { onOptionsMenuExpandedChange(false) },
                        )
                    }
                val currentFocusedApp = focusedApp
                val itemActionsCount =
                    if (currentFocusedApp != null) {
                        if (!currentFocusedApp.isRom) LIBRARY_APP_ACTIONS_COUNT else LIBRARY_ROM_ACTIONS_COUNT
                    } else {
                        0
                    }
                val dividerAfterIndex =
                    if (itemActionsCount > 0 && itemActionsCount < actions.size) {
                        itemActionsCount - 1
                    } else {
                        null
                    }
                ExpandableActionsMenu(
                    isExpanded = isOptionsMenuExpanded,
                    onExpandedChange = onOptionsMenuExpandedChange,
                    actions = actions,
                    selectedIndex = selectedIndex,
                    dividerAfterIndex = dividerAfterIndex,
                    enabled = enabled,
                )
            }
        }

        // Lower Right: Subdued touch launch buttons
        AnimatedVisibility(
            visible = areButtonPromptsVisible,
            enter =
                fadeIn(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) +
                    slideInVertically(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) { it / FLS_PROMPT_SLIDE_DIVISOR },
            exit =
                fadeOut(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) +
                    slideOutVertically(
                        animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS),
                    ) { it / FLS_PROMPT_SLIDE_DIVISOR },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            DualScreenLaunchButtons(
                appInfo = focusedApp,
                enabled = enabled && !isOptionsMenuExpanded,
                onLaunchTop = onAppClickTop,
                onLaunchBottom = onAppClickBottom,
            )
        }
    }
}

@Composable
private fun LibraryFocusIndicatorOverlay(
    overlayState: LibraryFocusOverlayState,
    appColors: AppColors,
) {
    val currentRect = overlayState.targetRect ?: return
    val density = LocalDensity.current

    val borderAnimationSpec =
        remember {
            spring<Float>(
                stiffness = FLS_FOCUS_BORDER_SPRING_STIFFNESS,
                dampingRatio = Spring.DampingRatioNoBouncy,
            )
        }

    val animLeftState =
        animateFloatAsState(
            targetValue = currentRect.left,
            animationSpec = borderAnimationSpec,
            label = "LibraryBorderAnimLeft",
        )
    val animTopState =
        animateFloatAsState(
            targetValue = currentRect.top,
            animationSpec = borderAnimationSpec,
            label = "LibraryBorderAnimTop",
        )
    val borderAlphaState =
        animateFloatAsState(
            targetValue = if (overlayState.isFirstPositioned) FLS_VISIBLE_CARD_ALPHA else 0f,
            animationSpec = tween(durationMillis = FLS_FOCUS_BORDER_ALPHA_DURATION_MS),
            label = "LibraryBorderAlpha",
        )

    val cardWidthDp = with(density) { currentRect.width.coerceAtLeast(FLS_MIN_RECT_DIMENSION_PX).toDp() }
    val cardHeightDp = with(density) { currentRect.height.coerceAtLeast(FLS_MIN_RECT_DIMENSION_PX).toDp() }

    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    translationX = animLeftState.value
                    translationY = animTopState.value
                    scaleX = FLS_FOCUSED_SCALE
                    scaleY = FLS_FOCUSED_SCALE
                    alpha = borderAlphaState.value
                }.size(
                    width = cardWidthDp,
                    height = cardHeightDp,
                ).drawWithCache {
                    val cornerRadiusPx = FLS_CORNER_RADIUS.toPx()
                    val blurRadiusPx = FLS_SHADOW_BLUR_RADIUS.toPx()
                    val spreadPx = FLS_SHADOW_SPREAD.toPx()
                    val shadowColorArgb = appColors.accent.copy(alpha = FLS_SHADOW_ALPHA).toArgb()

                    // The overlay is drawn ABOVE the grid, so the glow must never paint
                    // inside the card bounds. OUTER blur renders only the halo outside the
                    // shape, and a solid stroke band covers the spread ring — no stencil clip.
                    val glowPaint =
                        NativePaint().apply {
                            isAntiAlias = true
                            color = shadowColorArgb
                            style = NativePaint.Style.FILL
                            maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.OUTER)
                        }
                    val spreadBandPaint =
                        NativePaint().apply {
                            isAntiAlias = true
                            color = shadowColorArgb
                            style = NativePaint.Style.STROKE
                            strokeWidth = spreadPx
                        }
                    val halfSpreadPx = spreadPx / FLS_SPREAD_HALF_DIVISOR

                    onDrawBehind {
                        if (borderAlphaState.value > 0f) {
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawRoundRect(
                                    -spreadPx,
                                    -spreadPx,
                                    size.width + spreadPx,
                                    size.height + spreadPx,
                                    cornerRadiusPx + spreadPx,
                                    cornerRadiusPx + spreadPx,
                                    glowPaint,
                                )
                                canvas.nativeCanvas.drawRoundRect(
                                    -halfSpreadPx,
                                    -halfSpreadPx,
                                    size.width + halfSpreadPx,
                                    size.height + halfSpreadPx,
                                    cornerRadiusPx + halfSpreadPx,
                                    cornerRadiusPx + halfSpreadPx,
                                    spreadBandPaint,
                                )
                            }
                        }
                    }
                }.border(
                    width = FLS_FOCUS_BORDER_WIDTH,
                    color = appColors.accent,
                    shape = FLS_CARD_SHAPE,
                ),
    )
}

@Composable
private fun LibraryHeaderBar(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    onCloseRequested: () -> Unit,
    tabs: List<LibraryTab>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    areButtonPromptsVisible: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FLS_HEADER_PADDING_HORIZONTAL, vertical = FLS_HEADER_PADDING_VERTICAL)
                .height(FLS_HEADER_BAR_HEIGHT),
    ) {
        // Horizontal 3D Category Reel with L1/R1 Badges
        InteractiveLibraryCategoryHeader(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            tabs = tabs,
            areButtonPromptsVisible = areButtonPromptsVisible,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        // Navigation affordance to return to Gallery via R2 button
        AnimatedVisibility(
            visible = areButtonPromptsVisible,
            enter =
                fadeIn(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) +
                    slideInVertically(
                        animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS),
                    ) { -it / FLS_PROMPT_SLIDE_DIVISOR } +
                    slideInHorizontally(
                        animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS),
                    ) { it / FLS_PROMPT_SLIDE_DIVISOR },
            exit =
                fadeOut(animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS)) +
                    slideOutVertically(
                        animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS),
                    ) { -it / FLS_PROMPT_SLIDE_DIVISOR } +
                    slideOutHorizontally(
                        animationSpec = tween(FLS_PROMPT_ANIM_DURATION_MS),
                    ) { it / FLS_PROMPT_SLIDE_DIVISOR },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            GamePadButtonAction(
                button = GamePadButton.BUTTON_R2,
                text = stringResource(R.string.gamefocus_nav_gallery),
                onClick = onCloseRequested,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun getTabName(tab: LibraryTab): String =
    when (tab) {
        is LibraryTab.RomSystem -> tab.displayName
        else -> stringResource(tab.stringResId)
    }

@Composable
private fun InteractiveLibraryCategoryHeader(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    tabs: List<LibraryTab>,
    modifier: Modifier = Modifier,
    areButtonPromptsVisible: Boolean = true,
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current
    val slideOffsetPx = remember(density) { with(density) { FLS_BUTTON_SLIDE_OFFSET.toPx() } }

    val l1AlphaState =
        animateFloatAsState(
            targetValue = if (areButtonPromptsVisible) FLS_VISIBLE_CARD_ALPHA else 0f,
            animationSpec = tween(durationMillis = FLS_PROMPT_ANIM_DURATION_MS),
            label = "LibraryL1Alpha",
        )
    val l1TranslationXState =
        animateFloatAsState(
            targetValue = if (areButtonPromptsVisible) 0f else -slideOffsetPx,
            animationSpec = tween(durationMillis = FLS_PROMPT_ANIM_DURATION_MS),
            label = "LibraryL1TranslationX",
        )
    val r1AlphaState =
        animateFloatAsState(
            targetValue = if (areButtonPromptsVisible) FLS_VISIBLE_CARD_ALPHA else 0f,
            animationSpec = tween(durationMillis = FLS_PROMPT_ANIM_DURATION_MS),
            label = "LibraryR1Alpha",
        )
    val r1TranslationXState =
        animateFloatAsState(
            targetValue = if (areButtonPromptsVisible) 0f else slideOffsetPx,
            animationSpec = tween(durationMillis = FLS_PROMPT_ANIM_DURATION_MS),
            label = "LibraryR1TranslationX",
        )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FLS_HEADER_SPACING_SMALL),
    ) {
        // (L1) GamePad Button Icon
        GamePadButtonIcon(
            button = GamePadButton.BUTTON_L1,
            size = FLS_L1R1_ICON_SIZE,
            tint = appColors.onSurfaceSecondary,
            cutoutColor = appColors.appBackground,
            modifier =
                Modifier
                    .graphicsLayer {
                        alpha = l1AlphaState.value
                        translationX = l1TranslationXState.value
                    }.noFocusClickable(enabled = areButtonPromptsVisible) {
                        onTabSelected(selectedTab.previous(tabs))
                    },
        )

        // Animated Horizontal 3D Reel
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (initialState.next(tabs) == targetState) 1 else -1
                (slideInHorizontally { width -> (width / FLS_HEADER_REEL_SLIDE_DIVISOR) * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -(width / FLS_HEADER_REEL_SLIDE_DIVISOR) * direction } + fadeOut())
            },
            label = "LibraryHorizontalCategoryTransition",
        ) { currentTab ->
            val next1 = currentTab.next(tabs)
            val next2 = next1.next(tabs)
            val neighborTabs = listOf(next1, next2)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FLS_HEADER_SPACING_MEDIUM),
            ) {
                // Active category (leftmost, center-front)
                Text(
                    text = getTabName(currentTab),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = appColors.onSurfaceSecondary,
                        ),
                    maxLines = 1,
                    modifier = Modifier.noFocusClickable { onTabSelected(currentTab) },
                )

                neighborTabs.forEachIndexed { idx, tab ->
                    val stylePair = FLS_NEIGHBOR_STYLES[idx]
                    val neighborAlpha = stylePair.first
                    val angleFactor = stylePair.second
                    Text(
                        text = getTabName(tab),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = appColors.onSurfaceSecondary.copy(alpha = neighborAlpha),
                            ),
                        maxLines = 1,
                        modifier =
                            Modifier
                                .noFocusClickable { onTabSelected(tab) }
                                .graphicsLayer {
                                    rotationY = FLS_CATEGORY_ROLL_Y_ANGLE_DEG * angleFactor
                                    cameraDistance = FLS_CAMERA_DISTANCE_MULTIPLIER * density.density
                                },
                    )
                }
            }
        }

        // (R1) GamePad Button Icon
        GamePadButtonIcon(
            button = GamePadButton.BUTTON_R1,
            size = FLS_L1R1_ICON_SIZE,
            tint = appColors.onSurfaceSecondary,
            cutoutColor = appColors.appBackground,
            modifier =
                Modifier
                    .graphicsLayer {
                        alpha = r1AlphaState.value
                        translationX = r1TranslationXState.value
                    }.noFocusClickable(enabled = areButtonPromptsVisible) {
                        onTabSelected(selectedTab.next(tabs))
                    },
        )
    }
}

@Composable
private fun LibraryGridItem(
    index: Int,
    appInfo: InstalledAppInfo,
    isFocused: Boolean,
    isHidden: Boolean = false,
    onItemClick: (Int, InstalledAppInfo) -> Unit,
    onItemPositioned: (Int, Boolean, LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val iconBitmap = rememberLibraryIconBitmap(appInfo)
    val unfocusedCardBg = appColors.surface.copy(alpha = FLS_UNFOCUSED_CARD_BG_ALPHA)

    val initiallyFocused = remember { isFocused }
    var wasEverFocused by remember { mutableStateOf(isFocused) }
    if (isFocused && !wasEverFocused) {
        wasEverFocused = true
    }

    val initiallyHidden = remember { isHidden }
    var wasEverHidden by remember { mutableStateOf(isHidden) }
    if (isHidden && !wasEverHidden) {
        wasEverHidden = true
    }

    val currentPalette = if (isFocused) rememberAppPalette(appInfo, enabled = true) else null
    val targetCardBg =
        if (isFocused) {
            if (currentPalette != null && currentPalette.isExtracted) {
                currentPalette.darkenedPrimaryColor
            } else {
                appColors.surfaceVariant
            }
        } else {
            unfocusedCardBg
        }

    val cardBgAnim =
        if (wasEverFocused) {
            val anim = remember { ColorAnimatable(if (initiallyFocused) targetCardBg else unfocusedCardBg) }
            LaunchedEffect(targetCardBg) {
                anim.animateTo(targetCardBg, tween(durationMillis = FLS_CARD_BG_ANIM_DURATION_MS))
            }
            anim
        } else {
            null
        }

    val cardScaleAnim =
        if (wasEverFocused) {
            val anim = remember { Animatable(if (initiallyFocused) FLS_FOCUSED_SCALE else FLS_UNFOCUSED_SCALE) }
            LaunchedEffect(isFocused) {
                val targetScale = if (isFocused) FLS_FOCUSED_SCALE else FLS_UNFOCUSED_SCALE
                anim.animateTo(
                    targetValue = targetScale,
                    animationSpec = tween(durationMillis = FLS_CARD_SCALE_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
            anim
        } else {
            null
        }

    val cardAlphaAnim =
        if (wasEverHidden) {
            val anim = remember { Animatable(if (initiallyHidden) FLS_HIDDEN_CARD_ALPHA else FLS_VISIBLE_CARD_ALPHA) }
            LaunchedEffect(isHidden) {
                val targetAlpha = if (isHidden) FLS_HIDDEN_CARD_ALPHA else FLS_VISIBLE_CARD_ALPHA
                anim.animateTo(targetAlpha, tween(durationMillis = FLS_HIDE_ANIMATION_DURATION_MS))
            }
            anim
        } else {
            null
        }

    val badgeAlphaAnim =
        if (wasEverHidden) {
            val anim = remember { Animatable(if (initiallyHidden) FLS_HIDDEN_BADGE_ALPHA else FLS_VISIBLE_BADGE_ALPHA) }
            LaunchedEffect(isHidden) {
                val targetBadgeAlpha = if (isHidden) FLS_HIDDEN_BADGE_ALPHA else FLS_VISIBLE_BADGE_ALPHA
                anim.animateTo(targetBadgeAlpha, tween(durationMillis = FLS_HIDE_ANIMATION_DURATION_MS))
            }
            anim
        } else {
            null
        }

    val showVisibilityBadge by remember(badgeAlphaAnim) {
        derivedStateOf { (badgeAlphaAnim?.value ?: FLS_VISIBLE_BADGE_ALPHA) > 0f }
    }

    val handleClick: () -> Unit =
        remember(index, appInfo, onItemClick) {
            { onItemClick(index, appInfo) }
        }
    val handlePositioned: (LayoutCoordinates) -> Unit =
        remember(index, isFocused, onItemPositioned) {
            { coords -> onItemPositioned(index, isFocused, coords) }
        }

    Box(
        modifier =
            modifier
                .onGloballyPositioned(handlePositioned)
                .graphicsLayer {
                    val scale = cardScaleAnim?.value ?: FLS_UNFOCUSED_SCALE
                    scaleX = scale
                    scaleY = scale
                    alpha = cardAlphaAnim?.value ?: FLS_VISIBLE_CARD_ALPHA
                }.clip(FLS_CARD_SHAPE)
                .drawBehind {
                    drawRect(cardBgAnim?.value ?: unfocusedCardBg)
                }.noFocusClickable(onClick = handleClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .padding(FLS_CARD_INNER_PADDING)
                    .fillMaxWidth(),
        ) {
            // Square Icon
            Box(
                modifier = Modifier.size(FLS_ICON_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = appInfo.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(FLS_ROM_ICON_SHAPE),
                    )
                } else {
                    GameFocusFallbackIcon(
                        appInfo = appInfo,
                        size = FLS_FALLBACK_ICON_SIZE,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FLS_LABEL_GAP))

            // App Label
            Text(
                text = appInfo.label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isFocused) appColors.onSurface else appColors.onSurfaceSecondary,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFocused) {
                                Modifier
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = FLS_MARQUEE_INITIAL_DELAY_MS,
                                    )
                            } else {
                                Modifier
                            },
                        ),
            )
        }

        if (showVisibilityBadge) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(FLS_BADGE_PADDING)
                        .graphicsLayer { alpha = badgeAlphaAnim?.value ?: FLS_VISIBLE_BADGE_ALPHA },
            ) {
                MaterialSymbol(
                    name = "visibility_off",
                    size = FLS_BADGE_ICON_SIZE,
                    tint = appColors.onSurfaceSecondary,
                )
            }
        }
    }
}
