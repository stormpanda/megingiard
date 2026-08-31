package com.stormpanda.megingiard.gamefocus

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.ExpandableActionItem
import com.stormpanda.megingiard.ui.ExpandableActionsMenu
import com.stormpanda.megingiard.ui.ExpandableMenuOrientation
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.GamePadButtonIcon
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MaterialSymbol
import android.graphics.Paint as NativePaint

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
private val FLS_BUTTON_GAP_SMALL = 2.dp
private val FLS_HEADER_PADDING_HORIZONTAL = 24.dp
private val FLS_HEADER_PADDING_VERTICAL = 12.dp
private val FLS_HEADER_SPACING_SMALL = 8.dp
private val FLS_L1R1_ICON_SIZE = 20.dp
private val FLS_HEADER_SPACING_MEDIUM = 16.dp
private val FLS_CARD_INNER_PADDING = 10.dp
private val FLS_BADGE_PADDING = 8.dp
private val FLS_BADGE_ICON_SIZE = 18.dp

private const val FLS_FOCUS_BORDER_SPRING_STIFFNESS = 1800f

private const val FLS_HIDDEN_CARD_ALPHA = 0.4f
private const val FLS_VISIBLE_CARD_ALPHA = 1.0f
private const val FLS_HIDDEN_BADGE_ALPHA = 1.0f
private const val FLS_VISIBLE_BADGE_ALPHA = 0.0f
private const val FLS_HIDE_ANIMATION_DURATION_MS = 300
private const val FLS_MARQUEE_INITIAL_DELAY_MS = 500

private val LibraryTab.stringResId: Int
    get() =
        when (this) {
            LibraryTab.GAMES -> R.string.gamefocus_library_tab_games
            LibraryTab.APPS -> R.string.gamefocus_library_tab_apps
            is LibraryTab.RomSystem -> 0
        }

private suspend fun scrollToFocusedItem(
    gridState: LazyGridState,
    targetIndex: Int,
    columns: Int,
    peekOffsetPx: Int,
) {
    val targetRow = targetIndex / columns
    val firstItemOfRow = targetRow * columns
    val scrollOffset = if (targetRow == 0) 0 else -peekOffsetPx
    gridState.animateScrollToItem(index = firstItemOfRow, scrollOffset = scrollOffset)
}

@Composable
fun FocusLibraryScreen(
    allApps: List<InstalledAppInfo>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onAppClickTop: (InstalledAppInfo) -> Unit,
    onAppClickBottom: (InstalledAppInfo) -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
    favoritesSet: Set<String> = emptySet(),
    hiddenSet: Set<String> = emptySet(),
    isOptionsMenuExpanded: Boolean = false,
    onOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    onToggleFavorite: (InstalledAppInfo) -> Unit = {},
    onToggleHidden: (InstalledAppInfo) -> Unit = {},
    onEditArtwork: (InstalledAppInfo) -> Unit = {},
    onOpenAppInfo: (InstalledAppInfo) -> Unit = {},
    onAddRomFolder: () -> Unit = {},
    onRemoveRomFolder: (CustomRomFolder) -> Unit = {},
    enabled: Boolean = true,
    tabs: List<LibraryTab> = listOf(LibraryTab.GAMES, LibraryTab.APPS),
    isRemoveRomFolderDialogOpen: Boolean = false,
    onRemoveRomFolderDialogOpenChange: (Boolean) -> Unit = {},
    removeRomFolderDialogSelectedIndex: Int = 0,
    onRemoveRomFolderDialogSelectedIndexChange: (Int) -> Unit = {},
    folderToRemove: CustomRomFolder? = null,
    onFolderToRemoveChange: (CustomRomFolder?) -> Unit = {},
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val romFolders by RomManager.romFolders.collectAsState()
    val peekOffsetPx = with(density) { FLS_ROW_PEEK_OFFSET.roundToPx() }

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
            val displayedApps = remember(allApps, activeTab) { activeTab.filterApps(allApps) }
            val gridState: LazyGridState = rememberLazyGridState()

            LaunchedEffect(focusedIndex, displayedApps.size) {
                if (displayedApps.isNotEmpty() && focusedIndex >= 0) {
                    val safeIndex = focusedIndex.coerceIn(0, displayedApps.size - 1)
                    AppLog.d(TAG, "Library focused index changed to $safeIndex (total ${displayedApps.size})")
                    scrollToFocusedItem(gridState, safeIndex, FLS_GRID_COLUMNS, peekOffsetPx)
                }
            }

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
                var gridContainerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var focusedItemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var targetRect by remember { mutableStateOf<Rect?>(null) }
                var isFirstPositioned by remember { mutableStateOf(false) }
                val visibleItemCoords = remember { mutableMapOf<Int, LayoutCoordinates>() }

                LaunchedEffect(activeTab) {
                    targetRect = null
                    isFirstPositioned = false
                    visibleItemCoords.clear()
                    gridState.scrollToItem(0)
                }

                fun updateFocusedRect(
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

                SideEffect {
                    val currentFocusedCoords = visibleItemCoords[focusedIndex] ?: focusedItemCoordinates
                    val parentCoords = gridContainerCoordinates
                    if (currentFocusedCoords != null && parentCoords != null) {
                        updateFocusedRect(currentFocusedCoords, parentCoords)
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onGloballyPositioned { coords ->
                                gridContainerCoordinates = coords
                                val currentFocusedCoords = visibleItemCoords[focusedIndex] ?: focusedItemCoordinates
                                currentFocusedCoords?.let { itemCoords ->
                                    updateFocusedRect(itemCoords, coords)
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
                            val isFocused = (index == focusedIndex)
                            val isHidden = hiddenSet.contains(app.packageName)
                            LibraryGridItem(
                                appInfo = app,
                                isFocused = isFocused,
                                isHidden = isHidden,
                                onClickTop = {
                                    onFocusedIndexChange(index)
                                    onAppClickTop(app)
                                },
                                onClickBottom = {
                                    onFocusedIndexChange(index)
                                    onAppClickBottom(app)
                                },
                                modifier =
                                    Modifier.onGloballyPositioned { itemCoords ->
                                        visibleItemCoords[index] = itemCoords
                                        if (isFocused) {
                                            focusedItemCoordinates = itemCoords
                                            gridContainerCoordinates?.let { parentCoords ->
                                                updateFocusedRect(itemCoords, parentCoords)
                                            }
                                        }
                                    },
                            )
                        }
                    }

                    // Smoothly Animated Moving Focus Indicator Overlay
                    if (targetRect != null) {
                        val currentRect = targetRect!!

                        val borderAnimationSpec =
                            spring<Float>(
                                stiffness = FLS_FOCUS_BORDER_SPRING_STIFFNESS,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            )

                        val animLeft by animateFloatAsState(
                            targetValue = currentRect.left,
                            animationSpec = borderAnimationSpec,
                            label = "LibraryBorderAnimLeft",
                        )
                        val animTop by animateFloatAsState(
                            targetValue = currentRect.top,
                            animationSpec = borderAnimationSpec,
                            label = "LibraryBorderAnimTop",
                        )
                        val borderAlpha by animateFloatAsState(
                            targetValue = if (isFirstPositioned) 1f else 0f,
                            animationSpec = tween(durationMillis = 150),
                            label = "LibraryBorderAlpha",
                        )

                        Box(
                            modifier =
                                Modifier
                                    .graphicsLayer {
                                        translationX = animLeft
                                        translationY = animTop
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        alpha = borderAlpha
                                    }.size(
                                        width = with(density) { currentRect.width.coerceAtLeast(1f).toDp() },
                                        height = with(density) { currentRect.height.coerceAtLeast(1f).toDp() },
                                    ).drawWithCache {
                                        val cornerRadiusPx = FLS_CORNER_RADIUS.toPx()
                                        val blurRadiusPx = FLS_SHADOW_BLUR_RADIUS.toPx()
                                        val spreadPx = FLS_SHADOW_SPREAD.toPx()
                                        val shadowColorArgb = appColors.accent.copy(alpha = FLS_SHADOW_ALPHA).toArgb()

                                        val innerPath =
                                            Path().apply {
                                                addRoundRect(
                                                    RoundRect(
                                                        rect = Rect(0f, 0f, size.width, size.height),
                                                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                                    ),
                                                )
                                            }

                                        val nativePaint =
                                            NativePaint().apply {
                                                isAntiAlias = true
                                                color = shadowColorArgb
                                                style = NativePaint.Style.FILL
                                                maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                                            }

                                        onDrawBehind {
                                            if (borderAlpha > 0f) {
                                                clipPath(innerPath, clipOp = ClipOp.Difference) {
                                                    drawIntoCanvas { canvas ->
                                                        canvas.nativeCanvas.drawRoundRect(
                                                            -spreadPx,
                                                            -spreadPx,
                                                            size.width + spreadPx,
                                                            size.height + spreadPx,
                                                            cornerRadiusPx + spreadPx,
                                                            cornerRadiusPx + spreadPx,
                                                            nativePaint,
                                                        )
                                                    }
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
                    .background(
                        brush =
                            Brush.verticalGradient(
                                0.0f to appColors.appBackground,
                                0.2f to appColors.appBackground,
                                0.5f to appColors.appBackground.copy(alpha = 0.8f),
                                1.0f to Color.Transparent,
                            ),
                    ),
        )

        // Header Bar with Title, Subtitle, and Tabs hovering over the grid
        LibraryHeaderBar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                AppLog.i(TAG, "Library tab clicked/selected: ${tab.id}")
                onTabSelected(tab)
                onFocusedIndexChange(0)
            },
            onCloseRequested = onCloseRequested,
            enabled = enabled,
            tabs = tabs,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        val activeApps = selectedTab.filterApps(allApps)
        val focusedApp = activeApps.getOrNull(focusedIndex.coerceAtLeast(0))
        val isCurrentFavorite = focusedApp != null && favoritesSet.contains(focusedApp.packageName)
        val isCurrentHidden = focusedApp != null && hiddenSet.contains(focusedApp.packageName)

        // Bottom edge shadow overlay to improve button readability
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FLS_BOTTOM_GRADIENT_HEIGHT)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.5f to appColors.appBackground.copy(alpha = 0.8f),
                                0.8f to appColors.appBackground,
                                1.0f to appColors.appBackground,
                            ),
                    ),
        )

        // Bottom Bar containing Action Menu (lower left) and Launch indicators (lower right) hovering over the grid
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = FLS_BOTTOM_BAR_PADDING_HORIZONTAL,
                        end = FLS_BOTTOM_BAR_PADDING_HORIZONTAL,
                        bottom = FLS_BOTTOM_BAR_PADDING_VERTICAL,
                        top = FLS_BOTTOM_BAR_PADDING_VERTICAL,
                    ),
        ) {
            // Lower Left: Library Action Menu
            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                val actions =
                    remember(focusedApp, isCurrentHidden, romFolders) {
                        buildList {
                            if (focusedApp != null) {
                                add(
                                    ExpandableActionItem(
                                        label =
                                            if (isCurrentHidden) {
                                                context.getString(R.string.gamefocus_option_unhide)
                                            } else {
                                                context.getString(R.string.gamefocus_option_hide)
                                            },
                                        iconSymbol = "gamepad_left",
                                        onClick = {
                                            onToggleHidden(focusedApp)
                                            onOptionsMenuExpandedChange(false)
                                        },
                                    ),
                                )
                            }
                            add(
                                ExpandableActionItem(
                                    label = context.getString(R.string.gamefocus_option_add_rom_folder),
                                    iconSymbol = "gamepad_up",
                                    onClick = {
                                        onAddRomFolder()
                                        onOptionsMenuExpandedChange(false)
                                    },
                                ),
                            )
                            if (romFolders.isNotEmpty()) {
                                add(
                                    ExpandableActionItem(
                                        label = context.getString(R.string.gamefocus_option_manage_rom_folders),
                                        iconSymbol = "gamepad_down",
                                        onClick = {
                                            onRemoveRomFolderDialogOpenChange(true)
                                            onOptionsMenuExpandedChange(false)
                                        },
                                    ),
                                )
                            }
                        }
                    }
                ExpandableActionsMenu(
                    isExpanded = isOptionsMenuExpanded,
                    onExpandedChange = onOptionsMenuExpandedChange,
                    orientation = ExpandableMenuOrientation.VERTICAL,
                    enabled = enabled,
                    actions = actions,
                )
            }

            // Lower Right: Subdued touch launch buttons
            DualScreenLaunchButtons(
                appInfo = focusedApp,
                enabled = enabled && !isOptionsMenuExpanded,
                onLaunchTop = onAppClickTop,
                onLaunchBottom = onAppClickBottom,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

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
private fun LibraryHeaderBar(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    onCloseRequested: () -> Unit,
    tabs: List<LibraryTab>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val appColors = LocalAppColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FLS_HEADER_PADDING_HORIZONTAL, vertical = FLS_HEADER_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Horizontal 3D Category Reel with L1/R1 Badges
        InteractiveLibraryCategoryHeader(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            tabs = tabs,
        )

        // Navigation affordance to return to Gallery via R2 button
        GamePadButtonAction(
            button = GamePadButton.BUTTON_R2,
            text = stringResource(R.string.gamefocus_nav_gallery),
            onClick = onCloseRequested,
            enabled = enabled,
        )
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
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current

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
            modifier = Modifier.noFocusClickable { onTabSelected(selectedTab.previous(tabs)) },
        )

        // Animated Horizontal 3D Reel
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (initialState.next(tabs) == targetState) 1 else -1
                (slideInHorizontally { width -> (width / 3) * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -(width / 3) * direction } + fadeOut())
            },
            label = "LibraryHorizontalCategoryTransition",
        ) { currentTab ->
            val next1 = currentTab.next(tabs)
            val next2 = next1.next(tabs)

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

                listOf(next1 to (0.45f to 0.7f), next2 to (0.25f to 1.4f)).forEach { (tab, style) ->
                    val (alpha, angleFactor) = style
                    Text(
                        text = getTabName(tab),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = appColors.onSurfaceSecondary.copy(alpha = alpha),
                            ),
                        maxLines = 1,
                        modifier =
                            Modifier
                                .noFocusClickable { onTabSelected(tab) }
                                .graphicsLayer {
                                    rotationY = FLS_CATEGORY_ROLL_Y_ANGLE_DEG * angleFactor
                                    cameraDistance = 16 * density.density
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
            modifier = Modifier.noFocusClickable { onTabSelected(selectedTab.next(tabs)) },
        )
    }
}

@Composable
private fun LibraryGridItem(
    appInfo: InstalledAppInfo,
    isFocused: Boolean,
    isHidden: Boolean = false,
    onClickTop: () -> Unit,
    onClickBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val iconBitmap = rememberIconBitmap(appInfo)

    val palette =
        remember(appInfo.packageName, appInfo.coverPath, appInfo.coverLastModified) {
            AppPaletteExtractor.getCachedColorsOrNull(appInfo)
        }

    val targetCardBg =
        if (isFocused) {
            if (palette != null && palette.isExtracted) {
                palette.darkenedPrimaryColor
            } else {
                appColors.surfaceVariant
            }
        } else {
            appColors.surface.copy(alpha = 0.5f)
        }

    val animatedCardBg by animateColorAsState(
        targetValue = targetCardBg,
        animationSpec = tween(durationMillis = 300),
        label = "LibraryCardBgAnim",
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "LibraryCardScaleAnim",
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isHidden) FLS_HIDDEN_CARD_ALPHA else FLS_VISIBLE_CARD_ALPHA,
        animationSpec = tween(durationMillis = FLS_HIDE_ANIMATION_DURATION_MS),
        label = "LibraryCardHiddenAlpha",
    )

    val visibilityOffAlpha by animateFloatAsState(
        targetValue = if (isHidden) FLS_HIDDEN_BADGE_ALPHA else FLS_VISIBLE_BADGE_ALPHA,
        animationSpec = tween(durationMillis = FLS_HIDE_ANIMATION_DURATION_MS),
        label = "LibraryVisibilityOffAlpha",
    )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                }.clip(FLS_CARD_SHAPE)
                .drawBehind {
                    drawRect(animatedCardBg)
                }.noFocusClickable(onClickTop),
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
                modifier =
                    Modifier
                        .size(FLS_ICON_SIZE)
                        .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                val currentBitmap = iconBitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
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

        if (visibilityOffAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(FLS_BADGE_PADDING)
                        .graphicsLayer { alpha = visibilityOffAlpha },
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
