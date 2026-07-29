package com.stormpanda.megingiard.gamefocus

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.focus.InstalledAppInfo
import com.stormpanda.megingiard.focus.LibraryTab
import com.stormpanda.megingiard.ui.LocalAppColors
import android.graphics.Paint as NativePaint

private const val TAG = "FocusLibraryScreen"

internal const val FLS_GRID_COLUMNS = 6
private val FLS_CORNER_RADIUS = 16.dp
private val FLS_ICON_SIZE = 64.dp
private val FLS_GRID_PADDING = 16.dp
private val FLS_GRID_SPACING = 12.dp
private val FLS_TAB_HEIGHT = 42.dp
private val FLS_FOCUS_BORDER_WIDTH = 3.dp

private val LibraryTab.stringResId: Int
    get() =
        when (this) {
            LibraryTab.ALL -> R.string.gamefocus_library_tab_all
            LibraryTab.APPS -> R.string.gamefocus_library_tab_apps
            LibraryTab.GAMES -> R.string.gamefocus_library_tab_games
        }

@Composable
private fun Modifier.noFocusClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .focusProperties { canFocus = false }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
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
) {
    val appColors = LocalAppColors.current
    val displayedApps = remember(allApps, selectedTab) { selectedTab.filterApps(allApps) }
    val gridState: LazyGridState = rememberLazyGridState()

    LaunchedEffect(focusedIndex, displayedApps.size) {
        if (displayedApps.isNotEmpty() && focusedIndex >= 0) {
            val safeIndex = focusedIndex.coerceIn(0, displayedApps.size - 1)
            AppLog.d(TAG, "Library focused index changed to $safeIndex (total ${displayedApps.size})")
            gridState.animateScrollToItem(safeIndex)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(appColors.appBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header Bar with Title, Subtitle, and Tabs
            LibraryHeaderBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    AppLog.i(TAG, "Library tab clicked/selected: ${tab.name}")
                    onTabSelected(tab)
                    onFocusedIndexChange(0)
                },
                onCloseRequested = onCloseRequested,
            )

            // Scrollable Condensed App Grid
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
                val density = LocalDensity.current
                var gridContainerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var focusedItemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var targetRect by remember { mutableStateOf<Rect?>(null) }
                var isFirstPositioned by remember { mutableStateOf(false) }

                LaunchedEffect(selectedTab) {
                    targetRect = null
                    isFirstPositioned = false
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

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clipToBounds()
                            .onGloballyPositioned { coords ->
                                gridContainerCoordinates = coords
                                focusedItemCoordinates?.let { itemCoords ->
                                    updateFocusedRect(itemCoords, coords)
                                }
                            },
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(FLS_GRID_COLUMNS),
                        state = gridState,
                        contentPadding = PaddingValues(FLS_GRID_PADDING),
                        horizontalArrangement = Arrangement.spacedBy(FLS_GRID_SPACING),
                        verticalArrangement = Arrangement.spacedBy(FLS_GRID_SPACING),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = displayedApps,
                            key = { _, app -> app.packageName },
                        ) { index, app ->
                            val isFocused = (index == focusedIndex)
                            LibraryGridItem(
                                appInfo = app,
                                isFocused = isFocused,
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

                        val animLeft by animateFloatAsState(
                            targetValue = currentRect.left,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "LibraryBorderAnimLeft",
                        )
                        val animTop by animateFloatAsState(
                            targetValue = currentRect.top,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "LibraryBorderAnimTop",
                        )
                        val animWidth by animateFloatAsState(
                            targetValue = currentRect.width,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "LibraryBorderAnimWidth",
                        )
                        val animHeight by animateFloatAsState(
                            targetValue = currentRect.height,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "LibraryBorderAnimHeight",
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
                                        width = with(density) { animWidth.coerceAtLeast(1f).toDp() },
                                        height = with(density) { animHeight.coerceAtLeast(1f).toDp() },
                                    ).drawBehind {
                                        if (borderAlpha > 0f) {
                                            val cornerRadiusPx = FLS_CORNER_RADIUS.toPx()
                                            val blurRadiusPx = 16.dp.toPx()
                                            val spreadPx = 2.dp.toPx()
                                            val shadowColorArgb = appColors.accent.copy(alpha = 0.45f * borderAlpha).toArgb()

                                            val innerPath =
                                                Path().apply {
                                                    addRoundRect(
                                                        RoundRect(
                                                            rect = Rect(0f, 0f, size.width, size.height),
                                                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                                        ),
                                                    )
                                                }

                                            clipPath(innerPath, clipOp = ClipOp.Difference) {
                                                drawIntoCanvas { canvas ->
                                                    val nativePaint =
                                                        NativePaint().apply {
                                                            isAntiAlias = true
                                                            color = shadowColorArgb
                                                            style = NativePaint.Style.FILL
                                                            maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                                                        }
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
                                    }.border(
                                        width = FLS_FOCUS_BORDER_WIDTH,
                                        color = appColors.accent,
                                        shape = RoundedCornerShape(FLS_CORNER_RADIUS),
                                    ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeaderBar(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Title & Hint Subtitle
        Column(
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.gamefocus_library_title),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.onSurface,
                    ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.gamefocus_library_hint_close),
                style = MaterialTheme.typography.labelSmall,
                color = appColors.onSurfaceSecondary.copy(alpha = 0.7f),
            )
        }

        // Tabs Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryTab.entries.forEach { tab ->
                LibraryTabItem(
                    label = stringResource(tab.stringResId),
                    isSelected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun LibraryTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    val bgColor = if (isSelected) appColors.accent else appColors.surface
    val textColor = if (isSelected) appColors.appBackground else appColors.onSurface

    Box(
        modifier =
            modifier
                .height(FLS_TAB_HEIGHT)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .noFocusClickable(onClick)
                .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                ),
        )
    }
}

@Composable
private fun LibraryGridItem(
    appInfo: InstalledAppInfo,
    isFocused: Boolean,
    onClickTop: () -> Unit,
    onClickBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current

    val iconBitmap =
        remember(appInfo.icon) {
            FocusImageCache.getIconBitmap(context, appInfo)
        }

    val palette =
        remember(appInfo.packageName, appInfo.coverPath) {
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .scale(cardScale)
                .clip(RoundedCornerShape(FLS_CORNER_RADIUS))
                .background(animatedCardBg)
                .noFocusClickable(onClickTop)
                .padding(10.dp),
    ) {
        // Square Icon
        Box(
            modifier =
                Modifier
                    .size(FLS_ICON_SIZE)
                    .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = appInfo.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = appInfo.label,
                    tint = appColors.accent,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // App Label
        Text(
            text = appInfo.label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) appColors.onSurface else appColors.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
