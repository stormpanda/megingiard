package com.stormpanda.megingiard.focus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MaterialSymbol
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.CutoutLetterCircleIcon
import com.stormpanda.megingiard.ui.ExpandableActionItem
import com.stormpanda.megingiard.ui.ExpandableActionsMenu
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.flow.collectLatest
import java.io.File

private const val TAG = "FocusTopLauncherScreen"

private val FTL_POSTER_CORNER_RADIUS = 16.dp
private val FTL_POSTER_WIDTH = 158.dp
private val FTL_POSTER_HEIGHT = 237.dp // 2:3 aspect ratio
private val FTL_POSTER_SPACING = 12.dp
private val FTL_ICON_SIZE = 72.dp
private val FTL_GALLERY_TOP_OFFSET = 10.dp
private val FTL_TITLE_GAP = 25.dp

@Composable
fun FocusTopLauncherScreen(
    apps: List<InstalledAppInfo>,
    onAppClickTop: (InstalledAppInfo) -> Unit = {},
    onAppClickBottom: (InstalledAppInfo) -> Unit = {},
    onAppClick: (InstalledAppInfo) -> Unit = onAppClickTop,
    selectedCategory: GameFocusCategory = GameFocusCategory.ALL_APPS,
    favoritesSet: Set<String> = emptySet(),
    isMainOptionsMenuExpanded: Boolean = false,
    onMainOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    onToggleFavorite: (InstalledAppInfo) -> Unit = {},
    editingAppInfo: InstalledAppInfo? = null,
    dialogVirtualIndex: Int = 10_000,
    onDialogVirtualIndexChange: (Int) -> Unit = {},
    confirmDialogTrigger: Int = 0,
    l1Trigger: Int = 0,
    r1Trigger: Int = 0,
    isOptionsMenuExpanded: Boolean = false,
    onOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    dpadUpTrigger: Int = 0,
    dpadRightTrigger: Int = 0,
    dpadLeftTrigger: Int = 0,
    dpadStepRightTrigger: Int = 0,
    onFocusedAppChanged: (InstalledAppInfo?) -> Unit = {},
    onDismissEditingApp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val apiKey by SettingsManager.steamGridDbApiToken.collectAsState()

    LaunchedEffect(Unit) {
        AppPaletteExtractor.init(context)
    }

    var showApiTokenMissingDialog by remember { mutableStateOf(false) }

    val allAppsTargetPage = remember { mutableIntStateOf(10_000) }
    val favoritesTargetPage = remember { mutableIntStateOf(10_000) }
    val lastUsedTargetPage = remember { mutableIntStateOf(10_000) }

    val activeTargetPageState =
        when (selectedCategory) {
            GameFocusCategory.ALL_APPS -> allAppsTargetPage
            GameFocusCategory.FAVORITES -> favoritesTargetPage
            GameFocusCategory.LAST_USED -> lastUsedTargetPage
        }

    val allAppsPagerState = rememberPagerState(initialPage = 10_000) { Int.MAX_VALUE }
    val favoritesPagerState = rememberPagerState(initialPage = 10_000) { Int.MAX_VALUE }
    val lastUsedPagerState = rememberPagerState(initialPage = 10_000) { Int.MAX_VALUE }

    val activePagerState =
        when (selectedCategory) {
            GameFocusCategory.ALL_APPS -> allAppsPagerState
            GameFocusCategory.FAVORITES -> favoritesPagerState
            GameFocusCategory.LAST_USED -> lastUsedPagerState
        }

    LaunchedEffect(dpadLeftTrigger) {
        if (dpadLeftTrigger > 0) {
            activeTargetPageState.intValue--
        }
    }

    LaunchedEffect(dpadStepRightTrigger) {
        if (dpadStepRightTrigger > 0) {
            activeTargetPageState.intValue++
        }
    }

    val targetPage = activeTargetPageState.intValue

    // Keep PagerState smoothly scrolling towards targetPage
    LaunchedEffect(targetPage, activePagerState) {
        if (activePagerState.currentPage != targetPage) {
            activePagerState.animateScrollToPage(targetPage)
        }
    }

    // Synchronize touch dragging back to activeTargetPageState when user releases drag
    val isDragged by activePagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(activePagerState) {
        snapshotFlow { isDragged to activePagerState.settledPage }
            .collectLatest { (dragged, settled) ->
                if (dragged) {
                    activeTargetPageState.intValue = settled
                }
            }
    }

    val currentActualIndex = if (apps.isNotEmpty()) Math.floorMod(targetPage, apps.size) else 0
    val currentApp = apps.getOrNull(currentActualIndex)

    LaunchedEffect(currentApp) {
        onFocusedAppChanged(currentApp)
        currentApp?.let { app ->
            AppLog.d(TAG, "Focused game changed to index=$currentActualIndex, package=${app.packageName}, label=${app.label}")
        }
    }

    // Extract dynamic 2 main colors asynchronously off the UI thread using AndroidX Palette API
    val defaultPalette =
        remember(appColors.accent, appColors.appBackground) {
            ExtractedAppPalette(appColors.accent, appColors.appBackground)
        }

    val activePalette by produceState(
        initialValue = defaultPalette,
        key1 = currentApp?.packageName,
        key2 = currentApp?.coverPath,
    ) {
        value =
            if (currentApp != null) {
                AppPaletteExtractor.extractColorsAsync(currentApp, appColors.accent, appColors.appBackground)
            } else {
                defaultPalette
            }
    }

    val animatedPrimaryColor by animateColorAsState(
        targetValue = activePalette.primaryColor,
        animationSpec = tween(durationMillis = 400),
        label = "animatedPrimaryColor",
    )

    val animatedSecondaryColor by animateColorAsState(
        targetValue = activePalette.secondaryColor,
        animationSpec = tween(durationMillis = 400),
        label = "animatedSecondaryColor",
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = appColors.appBackground,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    animatedPrimaryColor.copy(alpha = 0.35f),
                                    animatedSecondaryColor.copy(alpha = 0.18f),
                                    appColors.appBackground,
                                ),
                        ),
                    ),
        ) {
            // Plane 1: Full-Screen Gallery & App Title Plane
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        val isMovingDown = initialState.next() == targetState
                        if (isMovingDown) {
                            (slideInVertically { height -> height } + fadeIn())
                                .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                        } else {
                            (slideInVertically { height -> -height } + fadeIn())
                                .togetherWith(slideOutVertically { height -> height } + fadeOut())
                        }
                    },
                    label = "CarouselCategoryTransition",
                    modifier = Modifier.fillMaxSize(),
                ) { category ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (apps.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .height(310.dp)
                                        .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text =
                                        when (category) {
                                            GameFocusCategory.FAVORITES -> stringResource(R.string.gamefocus_no_favorites)
                                            GameFocusCategory.LAST_USED -> stringResource(R.string.gamefocus_no_last_used)
                                            else -> stringResource(R.string.focus_launcher_no_apps)
                                        },
                                    style = MaterialTheme.typography.titleMedium.copy(color = appColors.onSurfaceSecondary),
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(top = FTL_GALLERY_TOP_OFFSET),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Carousel
                                val categoryPagerState =
                                    when (category) {
                                        GameFocusCategory.ALL_APPS -> allAppsPagerState
                                        GameFocusCategory.FAVORITES -> favoritesPagerState
                                        GameFocusCategory.LAST_USED -> lastUsedPagerState
                                    }
                                HorizontalPosterCarousel(
                                    itemCount = apps.size,
                                    pagerState = categoryPagerState,
                                    onItemClick = { actualIndex ->
                                        val appInfo = apps.getOrNull(actualIndex)
                                        if (appInfo != null) onAppClick(appInfo)
                                    },
                                    posterWidth = FTL_POSTER_WIDTH,
                                    posterHeight = FTL_POSTER_HEIGHT,
                                    posterSpacing = FTL_POSTER_SPACING,
                                    carouselHeight = 290.dp,
                                    posterCornerRadius = FTL_POSTER_CORNER_RADIUS,
                                    ambientGlowColor = animatedPrimaryColor,
                                ) { actualIndex, _ ->
                                    val appInfo = apps[actualIndex]
                                    PosterCardContent(
                                        appInfo = appInfo,
                                        isFavorite = favoritesSet.contains(appInfo.packageName),
                                    )
                                }

                                Spacer(modifier = Modifier.height(FTL_TITLE_GAP))

                                // Focused App Title
                                if (currentApp != null) {
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
                                        modifier = Modifier.padding(horizontal = 140.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Plane 2: Hovering Controls Layer (Categories, Actions, Touch Launch Buttons)

            // Top-Left Category Header hovering over the gallery plane
            InteractiveCategoryHeader(
                selectedCategory = selectedCategory,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 16.dp),
            )

            // Bottom-Left Main Actions Menu hovering over the gallery plane
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 4.dp),
            ) {
                val isCurrentFavorite = currentApp != null && favoritesSet.contains(currentApp.packageName)
                ExpandableActionsMenu(
                    isExpanded = isMainOptionsMenuExpanded,
                    onExpandedChange = onMainOptionsMenuExpandedChange,
                    actions =
                        listOf(
                            ExpandableActionItem(
                                label =
                                    if (isCurrentFavorite) {
                                        stringResource(R.string.gamefocus_option_remove_favorite)
                                    } else {
                                        stringResource(R.string.gamefocus_option_add_favorite)
                                    },
                                iconSymbol = "gamepad_up",
                                onClick = {
                                    if (currentApp != null) {
                                        onToggleFavorite(currentApp)
                                        onMainOptionsMenuExpandedChange(false)
                                    }
                                },
                            ),
                        ),
                )
            }

            // Bottom-Right subdued touch buttons hovering over the gallery plane
            val noFocusInteractionSource = remember { MutableInteractionSource() }
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        if (currentApp != null) {
                            onAppClickTop(currentApp)
                        }
                    },
                    interactionSource = noFocusInteractionSource,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CutoutLetterCircleIcon(
                            letter = "A",
                            size = 18.dp,
                            tint = appColors.onSurfaceSecondary,
                            cutoutColor = appColors.appBackground,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.gamefocus_launch_top),
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    color = appColors.onSurfaceSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                TextButton(
                    onClick = {
                        if (currentApp != null) {
                            onAppClickBottom(currentApp)
                        }
                    },
                    interactionSource = noFocusInteractionSource,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CutoutLetterCircleIcon(
                            letter = "X",
                            size = 18.dp,
                            tint = appColors.onSurfaceSecondary,
                            cutoutColor = appColors.appBackground,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.gamefocus_launch_bottom),
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    color = appColors.onSurfaceSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                        )
                    }
                }
            }
        }

        // Custom Megingiard Artwork Selection Modal Dialog
        if (editingAppInfo != null) {
            if (apiKey.isBlank()) {
                showApiTokenMissingDialog = true
            } else {
                GameFocusArtworkDialog(
                    appInfo = editingAppInfo,
                    apiKey = apiKey,
                    virtualIndex = dialogVirtualIndex,
                    onVirtualIndexChange = onDialogVirtualIndexChange,
                    confirmTrigger = confirmDialogTrigger,
                    l1Trigger = l1Trigger,
                    r1Trigger = r1Trigger,
                    isOptionsMenuExpanded = isOptionsMenuExpanded,
                    onOptionsMenuExpandedChange = onOptionsMenuExpandedChange,
                    dpadUpTrigger = dpadUpTrigger,
                    dpadRightTrigger = dpadRightTrigger,
                    onDismiss = onDismissEditingApp,
                )
            }
        }

        if (showApiTokenMissingDialog) {
            AppAlertDialog(
                onDismissRequest = {
                    showApiTokenMissingDialog = false
                    onDismissEditingApp()
                },
                title = {
                    Text(
                        text = stringResource(R.string.steamgriddb_token_missing_title),
                        style = MaterialTheme.typography.titleLarge.copy(color = appColors.onSurface),
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.steamgriddb_token_missing_message),
                        style = MaterialTheme.typography.bodyMedium.copy(color = appColors.onSurfaceSecondary),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showApiTokenMissingDialog = false
                            onDismissEditingApp()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.steamgriddb_error_dismiss),
                            color = appColors.accent,
                        )
                    }
                },
            )
        }
    }
}

private object FocusImageCache {
    private val coverCache = LruCache<String, ImageBitmap>(80)
    private val iconCache = LruCache<String, ImageBitmap>(80)

    fun getCoverBitmap(appInfo: InstalledAppInfo): ImageBitmap? {
        val path = appInfo.coverPath ?: return null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val cacheKey = "$path:${file.lastModified()}"
        val cached = coverCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val startTime = System.currentTimeMillis()
        val bitmap =
            try {
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 2 // Downsample 2x for poster display (saves 4x memory and decodes faster)
                    }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to decode cover file $path: ${e.message}")
                null
            }

        val elapsed = System.currentTimeMillis() - startTime
        AppLog.d(TAG, "Decoded poster card cover for ${appInfo.label} in ${elapsed}ms")

        if (bitmap != null) {
            coverCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    fun getIconBitmap(
        context: Context,
        appInfo: InstalledAppInfo,
    ): ImageBitmap? {
        val cacheKey = appInfo.packageName
        val cached = iconCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val iconsDir = File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
        val iconFile = File(iconsDir, "${appInfo.packageName}.png")
        if (iconFile.exists() && iconFile.length() > 0) {
            val startTime = System.currentTimeMillis()
            val diskBitmap =
                try {
                    BitmapFactory.decodeFile(iconFile.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }

            if (diskBitmap != null) {
                val elapsed = System.currentTimeMillis() - startTime
                AppLog.d(TAG, "Loaded disk-cached icon PNG for ${appInfo.label} in ${elapsed}ms")
                iconCache.put(cacheKey, diskBitmap)
                return diskBitmap
            }
        }

        val startTime = System.currentTimeMillis()
        val bitmap = appInfo.icon?.toBitmapSafe()

        val elapsed = System.currentTimeMillis() - startTime
        AppLog.d(TAG, "Converted app icon for ${appInfo.label} in ${elapsed}ms")

        if (bitmap != null) {
            iconCache.put(cacheKey, bitmap)
            try {
                val androidBmp = appInfo.icon?.toAndroidBitmap()
                if (androidBmp != null) {
                    java.io.FileOutputStream(iconFile).use { out ->
                        androidBmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    androidBmp.recycle()
                }
            } catch (_: Exception) {
            }
        }
        return bitmap
    }

    private fun Drawable.toAndroidBitmap(): Bitmap? =
        try {
            val w = intrinsicWidth.coerceIn(1, 128)
            val h = intrinsicHeight.coerceIn(1, 128)
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
private fun InteractiveCategoryHeader(
    selectedCategory: GameFocusCategory,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    AnimatedContent(
        targetState = selectedCategory,
        transitionSpec = {
            val isMovingDown = initialState.next() == targetState
            if (isMovingDown) {
                (slideInVertically { height -> height / 3 } + fadeIn())
                    .togetherWith(slideOutVertically { height -> -height / 3 } + fadeOut())
            } else {
                (slideInVertically { height -> -height / 3 } + fadeIn())
                    .togetherWith(slideOutVertically { height -> height / 3 } + fadeOut())
            }
        },
        label = "CategoryRollingTransition",
        modifier = modifier,
    ) { currentCat ->
        val currentPrev = currentCat.previous()
        val currentNext = currentCat.next()

        Column(
            horizontalAlignment = Alignment.Start,
        ) {
            // Previous category (fainter, top fade)
            Text(
                text = stringResource(currentPrev.stringResId),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.onSurfaceSecondary.copy(alpha = 0.35f),
                    ),
                maxLines = 1,
                textAlign = TextAlign.Start,
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Active category (highlighted, top-left aligned)
            Text(
                text = stringResource(currentCat.stringResId),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.onSurfaceSecondary,
                    ),
                maxLines = 1,
                textAlign = TextAlign.Start,
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Next category (fainter, bottom fade)
            Text(
                text = stringResource(currentNext.stringResId),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.onSurfaceSecondary.copy(alpha = 0.35f),
                    ),
                maxLines = 1,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun PosterCardContent(
    appInfo: InstalledAppInfo,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current

    val coverBitmap =
        remember(appInfo.coverPath, appInfo.coverPath?.let { File(it).lastModified() }) {
            FocusImageCache.getCoverBitmap(appInfo)
        }

    val iconBitmap =
        remember(appInfo.icon, coverBitmap) {
            if (coverBitmap == null) FocusImageCache.getIconBitmap(context, appInfo) else null
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
                modifier =
                    Modifier
                        .size(FTL_ICON_SIZE)
                        .aspectRatio(1f),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(FTL_ICON_SIZE)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = appInfo.label,
                    tint = appColors.accent,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        if (isFavorite) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                MaterialSymbol(
                    name = "kid_star",
                    size = 22.dp,
                    tint = appColors.accent,
                )
            }
        }
    }
}

private fun Drawable.toBitmapSafe(): ImageBitmap? =
    try {
        val w = intrinsicWidth.coerceAtLeast(1)
        val h = intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
