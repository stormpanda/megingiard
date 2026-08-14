package com.stormpanda.megingiard.gamefocus

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.LetterNavigationHelper
import com.stormpanda.megingiard.catalog.LibraryTab
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.gamefocus.R
import com.stormpanda.megingiard.math.floorMod
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.CutoutLetterButton
import com.stormpanda.megingiard.ui.CutoutLetterCircleIcon
import com.stormpanda.megingiard.ui.ExpandableActionItem
import com.stormpanda.megingiard.ui.ExpandableActionsMenu
import com.stormpanda.megingiard.ui.ExpandableMenuOrientation
import com.stormpanda.megingiard.ui.GamePadButton
import com.stormpanda.megingiard.ui.GamePadButtonAction
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.MaterialSymbol
import com.stormpanda.megingiard.ui.VerticalRollingCarousel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FocusTopLauncherScreen"

private val FTL_POSTER_CORNER_RADIUS = 16.dp
private val FTL_POSTER_WIDTH = 158.dp
private val FTL_POSTER_HEIGHT = 237.dp // 2:3 aspect ratio
private val FTL_POSTER_SPACING = 12.dp
private val FTL_ICON_SIZE = 72.dp
private val FTL_GALLERY_TOP_OFFSET = 10.dp
private val FTL_TITLE_GAP = 25.dp
private val FTL_ROM_ICON_CORNER_RADIUS = 14.dp
private val FTL_FALLBACK_ICON_SIZE = 48.dp
private val FTL_BADGE_PADDING = 10.dp
private val FTL_BADGE_ICON_SIZE = 22.dp
private const val FTL_CATEGORY_ROLL_ANGLE_DEG = 35f
private const val FTL_BACKGROUND_COLOR_DEBOUNCE_MS = 220L
private const val FTL_BACKGROUND_COLOR_ANIM_MS = 500
private const val FTL_LETTER_NAV_DEBOUNCE_MS = 500L

private const val FTL_HIDDEN_BADGE_ALPHA = 1.0f
private const val FTL_VISIBLE_BADGE_ALPHA = 0.0f
private const val FTL_HIDE_ANIMATION_DURATION_MS = 300

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

private class JobRefHolder(
    var job: Job? = null,
)

@Composable
fun FocusTopLauncherScreen(
    apps: List<InstalledAppInfo>,
    onAppClickTop: (InstalledAppInfo) -> Unit = {},
    onAppClickBottom: (InstalledAppInfo) -> Unit = {},
    onAppClick: (InstalledAppInfo) -> Unit = onAppClickTop,
    selectedCategory: GameFocusCategory = GameFocusCategory.GAMES,
    categories: List<GameFocusCategory> = GameFocusCategory.builtIns,
    onCategoryUp: () -> Unit = {},
    onCategoryDown: () -> Unit = {},
    favoritesSet: Set<String> = emptySet(),
    hiddenSet: Set<String> = emptySet(),
    isMainOptionsMenuExpanded: Boolean = false,
    onMainOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    onToggleFavorite: (InstalledAppInfo) -> Unit = {},
    onToggleHidden: (InstalledAppInfo) -> Unit = {},
    onEditArtwork: (InstalledAppInfo) -> Unit = {},
    onOpenAppInfo: (InstalledAppInfo) -> Unit = {},
    onAddRomFolder: () -> Unit = {},
    onRemoveRomFolder: (CustomRomFolder) -> Unit = {},
    editingAppInfo: InstalledAppInfo? = null,
    dialogVirtualIndex: Int = 10_000,
    onDialogVirtualIndexChange: (Int) -> Unit = {},
    confirmDialogTrigger: Int = 0,
    dialogL1Trigger: Int = 0,
    dialogR1Trigger: Int = 0,
    prevLetterTrigger: Int = 0,
    nextLetterTrigger: Int = 0,
    isOptionsMenuExpanded: Boolean = false,
    onOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    dpadUpTrigger: Int = 0,
    dpadRightTrigger: Int = 0,
    dpadLeftTrigger: Int = 0,
    dpadStepRightTrigger: Int = 0,
    onFocusedAppChanged: (InstalledAppInfo?) -> Unit = {},
    onDismissEditingApp: () -> Unit = {},
    newlyAddedFolder: CustomRomFolder? = null,
    onDismissNewlyAddedFolder: () -> Unit = {},
    onConfirmNewlyAddedFolderCore: (CustomRomFolder, String?) -> Unit = { _, _ -> },
    coreChooserDialogSelectedIndex: Int = 0,
    onCoreChooserDialogSelectedIndexChange: (Int) -> Unit = {},
    confirmCoreChooserTrigger: Int = 0,
    isRemoveRomFolderDialogOpen: Boolean = false,
    onRemoveRomFolderDialogOpenChange: (Boolean) -> Unit = {},
    removeRomFolderDialogSelectedIndex: Int = 0,
    onRemoveRomFolderDialogSelectedIndexChange: (Int) -> Unit = {},
    folderToRemove: CustomRomFolder? = null,
    onFolderToRemoveChange: (CustomRomFolder?) -> Unit = {},
    allApps: List<InstalledAppInfo> = emptyList(),
    lastUsed: List<String> = emptyList(),
    isLibraryOpen: Boolean = false,
    librarySelectedTab: LibraryTab = LibraryTab.GAMES,
    onLibraryTabSelected: (LibraryTab) -> Unit = {},
    libraryFocusedIndex: Int = 0,
    onLibraryFocusedIndexChange: (Int) -> Unit = {},
    isLibraryOptionsMenuExpanded: Boolean = false,
    onLibraryOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onCloseLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val apiKey by remember(context) { MegingiardSettingsClient.observeSteamGridDbApiToken(context) }
        .collectAsState(initial = "")

    LaunchedEffect(Unit) {
        AppPaletteExtractor.init(context)
    }

    val frozenHiddenSet = remember(allApps, selectedCategory, isLibraryOpen) { hiddenSet.toSet() }

    val romFolders by RomManager.romFolders.collectAsState()

    val libraryTabs =
        remember(romFolders) {
            listOf(LibraryTab.GAMES, LibraryTab.APPS) +
                romFolders
                    .map { folder ->
                        LibraryTab.RomSystem(
                            id = "rom_${folder.systemId}",
                            systemId = folder.systemId,
                            displayName = SUPPORTED_SYSTEMS.find { it.id == folder.systemId }?.displayName ?: folder.systemName,
                        )
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }

    val gamesApps =
        remember(allApps, frozenHiddenSet) {
            allApps.filter {
                it.isGame && !it.isRom &&
                    !frozenHiddenSet.contains(it.packageName)
            }
        }
    val appsApps =
        remember(allApps, frozenHiddenSet) {
            allApps.filter {
                !it.isGame && !it.isRom &&
                    !frozenHiddenSet.contains(it.packageName)
            }
        }
    val favoritesApps = remember(allApps, favoritesSet) { allApps.filter { favoritesSet.contains(it.packageName) } }
    val lastUsedApps =
        remember(allApps, frozenHiddenSet, lastUsed) {
            lastUsed
                .mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
                .filter { !frozenHiddenSet.contains(it.packageName) }
        }

    fun getAppsForCategory(cat: GameFocusCategory): List<InstalledAppInfo> =
        when (cat) {
            GameFocusCategory.GAMES -> {
                gamesApps
            }

            GameFocusCategory.APPS -> {
                appsApps
            }

            GameFocusCategory.FAVORITES -> {
                favoritesApps
            }

            GameFocusCategory.LAST_USED -> {
                lastUsedApps
            }

            is GameFocusCategory.RomSystem -> {
                allApps.filter {
                    it.isRom && it.systemId == cat.systemId &&
                        !frozenHiddenSet.contains(it.packageName)
                }
            }
        }

    val categoryApps = getAppsForCategory(selectedCategory)

    val uniqueLetters = remember(categoryApps) { LetterNavigationHelper.getUniqueStartingLetters(categoryApps) }

    var showApiTokenMissingDialog by remember { mutableStateOf(false) }

    var lastHighlightedPackages by remember {
        mutableStateOf<Map<GameFocusCategory, String>>(emptyMap())
    }

    val gamesPagerState = rememberPagerState(initialPage = 0) { gamesApps.size }
    val appsPagerState = rememberPagerState(initialPage = 0) { appsApps.size }
    val favoritesPagerState = rememberPagerState(initialPage = 0) { favoritesApps.size }
    val lastUsedPagerState = rememberPagerState(initialPage = 0) { lastUsedApps.size }

    val romPagerStates = remember { mutableMapOf<String, PagerState>() }

    val activePagerState =
        when (selectedCategory) {
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

            is GameFocusCategory.RomSystem -> {
                romPagerStates.getOrPut(selectedCategory.id) {
                    RomPagerState(0, 0f) { categoryApps.size }
                }
            }
        }

    // Clamp active pager state if list shrinks (e.g. app hidden)
    LaunchedEffect(categoryApps) {
        if (categoryApps.isNotEmpty() && activePagerState.currentPage >= categoryApps.size) {
            val safeIndex = (categoryApps.size - 1).coerceAtLeast(0)
            activePagerState.scrollToPage(safeIndex)
        }
    }

    // Sync active pager state with package memory when category changes
    LaunchedEffect(selectedCategory) {
        val lastPkg = lastHighlightedPackages[selectedCategory]
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

    val scope = rememberCoroutineScope()
    var isLetterOverlayActive by remember { mutableStateOf(false) }
    var selectedLetterNavIndex by remember { mutableIntStateOf(0) }
    val letterCommitJobRef = remember { JobRefHolder() }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(categoryApps, isLibraryOpen) {
        if (!isLibraryOpen && categoryApps.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }

    // Dismiss letter overlay without action if user manually scrolls the pager
    LaunchedEffect(Unit) {
        snapshotFlow { activePagerState.isScrollInProgress }
            .collectLatest { isScrolling ->
                if (isScrolling && isLetterOverlayActive) {
                    AppLog.i(TAG, "Pager scroll in progress while letter carousel active -> cancelling overlay without action")
                    letterCommitJobRef.job?.cancel()
                    isLetterOverlayActive = false
                }
            }
    }

    // Handle D-pad LEFT step with wrap-around
    LaunchedEffect(dpadLeftTrigger) {
        if (dpadLeftTrigger > 0 && categoryApps.isNotEmpty()) {
            if (isLetterOverlayActive) {
                AppLog.i(TAG, "D-pad LEFT pressed while letter carousel active -> cancelling overlay without action")
                letterCommitJobRef.job?.cancel()
                isLetterOverlayActive = false
            } else {
                val prevIndex = if (activePagerState.currentPage > 0) activePagerState.currentPage - 1 else categoryApps.size - 1
                AppLog.d(TAG, "D-pad LEFT step for ${selectedCategory.id}: current=${activePagerState.currentPage} -> target=$prevIndex")
                activePagerState.animateScrollToPage(prevIndex)
            }
        }
    }

    // Handle D-pad RIGHT step with wrap-around
    LaunchedEffect(dpadStepRightTrigger) {
        if (dpadStepRightTrigger > 0 && categoryApps.isNotEmpty()) {
            if (isLetterOverlayActive) {
                AppLog.i(TAG, "D-pad RIGHT pressed while letter carousel active -> cancelling overlay without action")
                letterCommitJobRef.job?.cancel()
                isLetterOverlayActive = false
            } else {
                val nextIndex = if (activePagerState.currentPage < categoryApps.size - 1) activePagerState.currentPage + 1 else 0
                AppLog.d(TAG, "D-pad RIGHT step for ${selectedCategory.id}: current=${activePagerState.currentPage} -> target=$nextIndex")
                activePagerState.animateScrollToPage(nextIndex)
            }
        }
    }

    val currentApp =
        remember(activePagerState.currentPage, categoryApps) {
            categoryApps.getOrNull(activePagerState.currentPage.coerceIn(0, (categoryApps.size - 1).coerceAtLeast(0)))
        }

    // Reset letter overlay on category switch or dialog edit
    LaunchedEffect(selectedCategory, editingAppInfo) {
        letterCommitJobRef.job?.cancel()
        isLetterOverlayActive = false
    }

    fun scheduleLetterCommit() {
        letterCommitJobRef.job?.cancel()
        letterCommitJobRef.job =
            scope.launch {
                delay(FTL_LETTER_NAV_DEBOUNCE_MS)
                val targetLetter = uniqueLetters.getOrNull(selectedLetterNavIndex)
                isLetterOverlayActive = false
                if (targetLetter != null) {
                    val targetAppIndex = LetterNavigationHelper.findFirstIndexOfLetter(apps, targetLetter)
                    AppLog.i(TAG, "500ms debounce expired -> committing letter '$targetLetter' at index $targetAppIndex")
                    activePagerState.animateScrollToPage(targetAppIndex)
                }
            }
    }

    // Handle Gamepad L1 step (previous starting letter in letter carousel)
    LaunchedEffect(prevLetterTrigger) {
        if (prevLetterTrigger > 0 && apps.isNotEmpty()) {
            if (uniqueLetters.isNotEmpty()) {
                if (!isLetterOverlayActive) {
                    isLetterOverlayActive = true
                    val currentLetter = currentApp?.let { LetterNavigationHelper.getStartingLetter(it.label) }
                    val initialIndex = if (currentLetter != null) uniqueLetters.indexOf(currentLetter).coerceAtLeast(0) else 0
                    selectedLetterNavIndex = initialIndex
                }
                selectedLetterNavIndex = (selectedLetterNavIndex - 1).coerceAtLeast(0)
                AppLog.d(
                    TAG,
                    "L1 letter carousel step for ${selectedCategory.id}: selected index=$selectedLetterNavIndex ('${uniqueLetters[selectedLetterNavIndex]}')",
                )
                scheduleLetterCommit()
            }
        }
    }

    // Handle Gamepad R1 step (next starting letter in letter carousel)
    LaunchedEffect(nextLetterTrigger) {
        if (nextLetterTrigger > 0 && apps.isNotEmpty()) {
            if (uniqueLetters.isNotEmpty()) {
                if (!isLetterOverlayActive) {
                    isLetterOverlayActive = true
                    val currentLetter = currentApp?.let { LetterNavigationHelper.getStartingLetter(it.label) }
                    val initialIndex = if (currentLetter != null) uniqueLetters.indexOf(currentLetter).coerceAtLeast(0) else 0
                    selectedLetterNavIndex = initialIndex
                }
                selectedLetterNavIndex = (selectedLetterNavIndex + 1).coerceAtMost(uniqueLetters.size - 1)
                AppLog.d(
                    TAG,
                    "R1 letter carousel step for ${selectedCategory.id}: selected index=$selectedLetterNavIndex ('${uniqueLetters[selectedLetterNavIndex]}')",
                )
                scheduleLetterCommit()
            }
        }
    }

    // Bi-directional synchronization: update lastHighlightedPackages and notify focused app
    LaunchedEffect(currentApp, selectedCategory) {
        onFocusedAppChanged(currentApp)
        if (currentApp != null) {
            lastHighlightedPackages = lastHighlightedPackages + (selectedCategory to currentApp.packageName)
            AppLog.d(TAG, "Focused game changed for ${selectedCategory.id}: package=${currentApp.packageName}, label=${currentApp.label}")
        }
    }

    var backgroundApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    // Throttle background color updates during rapid gallery navigation
    LaunchedEffect(activePagerState, apps) {
        snapshotFlow {
            val page = activePagerState.currentPage
            apps.getOrNull(page)
        }.collectLatest { target ->
            if (activePagerState.isScrollInProgress) {
                delay(FTL_BACKGROUND_COLOR_DEBOUNCE_MS)
            }
            backgroundApp = target
        }
    }

    LaunchedEffect(selectedCategory) {
        backgroundApp = apps.getOrNull(activePagerState.currentPage)
    }

    // Extract dynamic 2 main colors asynchronously off the UI thread using AndroidX Palette API
    val defaultPalette =
        remember(appColors.accent, appColors.appBackground) {
            ExtractedAppPalette(appColors.accent, appColors.appBackground)
        }

    val activePalette by produceState(
        initialValue = defaultPalette,
        key1 = backgroundApp?.packageName,
        key2 = backgroundApp?.coverPath,
    ) {
        value =
            if (backgroundApp != null) {
                AppPaletteExtractor.extractColorsAsync(backgroundApp!!, appColors.accent, appColors.appBackground)
            } else {
                defaultPalette
            }
    }

    val animatedPrimaryColor by animateColorAsState(
        targetValue = activePalette.primaryColor,
        animationSpec = tween(durationMillis = FTL_BACKGROUND_COLOR_ANIM_MS),
        label = "animatedPrimaryColor",
    )

    val animatedSecondaryColor by animateColorAsState(
        targetValue = activePalette.secondaryColor,
        animationSpec = tween(durationMillis = FTL_BACKGROUND_COLOR_ANIM_MS),
        label = "animatedSecondaryColor",
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = appColors.appBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = isLibraryOpen,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(animationSpec = tween(350), initialOffsetX = { it }) + fadeIn(animationSpec = tween(350)))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = tween(350), targetOffsetX = { -it }) +
                                    fadeOut(animationSpec = tween(350)),
                            )
                    } else {
                        (slideInHorizontally(animationSpec = tween(350), initialOffsetX = { -it }) + fadeIn(animationSpec = tween(350)))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = tween(350), targetOffsetX = { it }) +
                                    fadeOut(animationSpec = tween(350)),
                            )
                    }
                },
                label = "LibrarySlideTransition",
                modifier = Modifier.fillMaxSize(),
            ) { showLibrary ->
                if (showLibrary) {
                    FocusLibraryScreen(
                        allApps = allApps,
                        selectedTab = librarySelectedTab,
                        onTabSelected = onLibraryTabSelected,
                        focusedIndex = libraryFocusedIndex,
                        onFocusedIndexChange = onLibraryFocusedIndexChange,
                        favoritesSet = favoritesSet,
                        hiddenSet = hiddenSet,
                        isOptionsMenuExpanded = isLibraryOptionsMenuExpanded,
                        onOptionsMenuExpandedChange = onLibraryOptionsMenuExpandedChange,
                        onToggleFavorite = onToggleFavorite,
                        onToggleHidden = onToggleHidden,
                        onEditArtwork = onEditArtwork,
                        onOpenAppInfo = onOpenAppInfo,
                        onAddRomFolder = onAddRomFolder,
                        onRemoveRomFolder = onRemoveRomFolder,
                        onAppClickTop = onAppClickTop,
                        onAppClickBottom = onAppClickBottom,
                        onCloseRequested = onCloseLibrary,
                        enabled = isLibraryOpen,
                        tabs = libraryTabs,
                        isRemoveRomFolderDialogOpen = isRemoveRomFolderDialogOpen,
                        onRemoveRomFolderDialogOpenChange = onRemoveRomFolderDialogOpenChange,
                        removeRomFolderDialogSelectedIndex = removeRomFolderDialogSelectedIndex,
                        onRemoveRomFolderDialogSelectedIndexChange = onRemoveRomFolderDialogSelectedIndexChange,
                        folderToRemove = folderToRemove,
                        onFolderToRemoveChange = onFolderToRemoveChange,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    drawRect(
                                        brush =
                                            Brush.verticalGradient(
                                                colors =
                                                    listOf(
                                                        animatedPrimaryColor.copy(alpha = 0.20f),
                                                        animatedSecondaryColor.copy(alpha = 0.10f),
                                                        appColors.appBackground,
                                                    ),
                                            ),
                                    )
                                },
                    ) {
                        // Plane 1: Full-Screen Gallery & App Title Plane
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    val isMovingDown = initialState.next(categories) == targetState
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
                                                        GameFocusCategory.GAMES -> stringResource(R.string.gamefocus_no_games)
                                                        GameFocusCategory.APPS -> stringResource(R.string.gamefocus_no_apps_category)
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

                                                    is GameFocusCategory.RomSystem -> {
                                                        romPagerStates.getOrPut(category.id) {
                                                            RomPagerState(0, 0f) { currentCategoryApps.size }
                                                        }
                                                    }
                                                }
                                            HorizontalPosterCarousel(
                                                itemCount = currentCategoryApps.size,
                                                pagerState = categoryPagerState,
                                                modifier = Modifier.focusRequester(focusRequester),
                                                key = { page -> currentCategoryApps.getOrNull(page)?.packageName ?: page },
                                                targetPage = categoryPagerState.targetPage,
                                                onItemClick = { actualIndex ->
                                                    val appInfo = currentCategoryApps.getOrNull(actualIndex)
                                                    if (appInfo != null) onAppClick(appInfo)
                                                },
                                                posterWidth = FTL_POSTER_WIDTH,
                                                posterHeight = FTL_POSTER_HEIGHT,
                                                posterSpacing = FTL_POSTER_SPACING,
                                                carouselHeight = FTL_CAROUSEL_HEIGHT,
                                                posterCornerRadius = FTL_POSTER_CORNER_RADIUS,
                                                cardBackgroundColor = { actualIndex, isSelected ->
                                                    val appInfo = currentCategoryApps.getOrNull(actualIndex)
                                                    if (appInfo != null) {
                                                        val palette = AppPaletteExtractor.getCachedColorsOrNull(appInfo)
                                                        if (palette != null && palette.isExtracted) {
                                                            palette.darkenedPrimaryColor
                                                        } else {
                                                            if (isSelected) appColors.surfaceVariant else appColors.surface
                                                        }
                                                    } else {
                                                        if (isSelected) appColors.surfaceVariant else appColors.surface
                                                    }
                                                },
                                                isHidden = { actualIndex ->
                                                    currentCategoryApps.getOrNull(actualIndex)?.let {
                                                        hiddenSet.contains(it.packageName)
                                                    } ?: false
                                                },
                                            ) { actualIndex, _ ->
                                                val appInfo = currentCategoryApps[actualIndex]
                                                PosterCardContent(
                                                    appInfo = appInfo,
                                                    isFavorite = favoritesSet.contains(appInfo.packageName),
                                                    isHidden = hiddenSet.contains(appInfo.packageName),
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(FTL_TITLE_GAP))

                                            // Focused App Title or Horizontal Letter Carousel Overlay
                                            AnimatedContent(
                                                targetState = isLetterOverlayActive,
                                                transitionSpec = {
                                                    (slideInVertically { height -> height / 2 } + fadeIn())
                                                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut())
                                                },
                                                label = "TitleLetterCarouselTransition",
                                                modifier =
                                                    Modifier
                                                        .zIndex(1f)
                                                        .padding(horizontal = FTL_LETTER_NAV_PADDING),
                                            ) { isOverlay ->
                                                if (isOverlay && uniqueLetters.isNotEmpty()) {
                                                    HorizontalLetterCarousel(
                                                        letters = uniqueLetters,
                                                        selectedIndex = selectedLetterNavIndex,
                                                    )
                                                } else {
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
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Plane 2: Hovering Controls Layer (Categories, Actions, Touch Launch Buttons)

                        // Top-Right Library navigation button hovering over the gallery plane
                        val isControlsEnabled = editingAppInfo == null && !isLibraryOpen

                        // Top-Left Category Header hovering over the gallery plane
                        InteractiveCategoryHeader(
                            selectedCategory = selectedCategory,
                            categories = categories,
                            onCategoryUp = onCategoryUp,
                            onCategoryDown = onCategoryDown,
                            enabled = isControlsEnabled,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = FTL_CATEGORY_HEADER_PADDING_START, top = FTL_CATEGORY_HEADER_PADDING_TOP),
                        )

                        GamePadButtonAction(
                            button = GamePadButton.BUTTON_R2,
                            text = stringResource(R.string.gamefocus_nav_library),
                            enabled = isControlsEnabled,
                            onClick = onOpenLibrary,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = FTL_NAV_PADDING_END, top = FTL_NAV_PADDING_TOP),
                        )

                        // Bottom-Left Main Actions Menu hovering over the gallery plane
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = FTL_BOTTOM_BAR_PADDING_START, bottom = FTL_BOTTOM_BAR_PADDING_BOTTOM),
                        ) {
                            val isCurrentFavorite = currentApp != null && favoritesSet.contains(currentApp.packageName)
                            val isCurrentHidden = currentApp != null && hiddenSet.contains(currentApp.packageName)
                            val actions =
                                remember(currentApp, isCurrentFavorite, isCurrentHidden) {
                                    buildList {
                                        if (currentApp != null) {
                                            add(
                                                ExpandableActionItem(
                                                    label =
                                                        if (isCurrentFavorite) {
                                                            context.getString(R.string.gamefocus_option_remove_favorite)
                                                        } else {
                                                            context.getString(R.string.gamefocus_option_add_favorite)
                                                        },
                                                    iconSymbol = "gamepad_up",
                                                    onClick = {
                                                        onToggleFavorite(currentApp)
                                                        onMainOptionsMenuExpandedChange(false)
                                                    },
                                                ),
                                            )
                                            add(
                                                ExpandableActionItem(
                                                    label = context.getString(R.string.gamefocus_option_edit),
                                                    iconSymbol = "gamepad_right",
                                                    onClick = {
                                                        onEditArtwork(currentApp)
                                                        onMainOptionsMenuExpandedChange(false)
                                                    },
                                                ),
                                            )
                                            if (!currentApp.isRom) {
                                                add(
                                                    ExpandableActionItem(
                                                        label = context.getString(R.string.gamefocus_option_app_info),
                                                        iconSymbol = "gamepad_down",
                                                        onClick = {
                                                            onOpenAppInfo(currentApp)
                                                            onMainOptionsMenuExpandedChange(false)
                                                        },
                                                    ),
                                                )
                                            }
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
                                                        onToggleHidden(currentApp)
                                                        onMainOptionsMenuExpandedChange(false)
                                                    },
                                                ),
                                            )
                                        }
                                    }
                                }
                            ExpandableActionsMenu(
                                isExpanded = isMainOptionsMenuExpanded,
                                onExpandedChange = onMainOptionsMenuExpandedChange,
                                orientation = ExpandableMenuOrientation.VERTICAL,
                                enabled = isControlsEnabled,
                                actions = actions,
                            )
                        }

                        // Bottom-Right subdued touch buttons hovering over the gallery plane
                        val noFocusInteractionSource = remember { MutableInteractionSource() }
                        val isBottomBarEnabled = isControlsEnabled && !isMainOptionsMenuExpanded
                        Row(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = FTL_BOTTOM_BAR_PADDING_END, bottom = FTL_BOTTOM_BAR_PADDING_BOTTOM),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GamePadButtonAction(
                                button = GamePadButton.BUTTON_A,
                                text = stringResource(R.string.gamefocus_launch_top),
                                enabled = isBottomBarEnabled,
                                onClick = {
                                    if (currentApp != null) {
                                        onAppClickTop(currentApp)
                                    }
                                },
                            )

                            Spacer(modifier = Modifier.width(FTL_BUTTON_GAP))

                            GamePadButtonAction(
                                button = GamePadButton.BUTTON_X,
                                text = stringResource(R.string.gamefocus_launch_bottom),
                                enabled = isBottomBarEnabled,
                                onClick = {
                                    if (currentApp != null) {
                                        onAppClickBottom(currentApp)
                                    }
                                },
                            )
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
                                l1Trigger = dialogL1Trigger,
                                r1Trigger = dialogR1Trigger,
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
        }

        if (newlyAddedFolder != null) {
            RomFolderCoreChooserDialog(
                folder = newlyAddedFolder,
                selectedIndex = coreChooserDialogSelectedIndex,
                onSelectedIndexChange = onCoreChooserDialogSelectedIndexChange,
                confirmTrigger = confirmCoreChooserTrigger,
                onDismiss = onDismissNewlyAddedFolder,
                onConfirm = { core -> onConfirmNewlyAddedFolderCore(newlyAddedFolder, core) },
            )
        }
    }
}

internal object FocusImageCache {
    private val coverCache = LruCache<String, ImageBitmap>(80)
    private val iconCache = LruCache<String, ImageBitmap>(80)

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
            bitmap
        }
    }

    fun getCachedIconBitmap(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun getIconBitmapAsync(
        context: Context,
        appInfo: InstalledAppInfo,
    ): ImageBitmap? {
        val cacheKey = appInfo.packageName
        val cached = iconCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            val file =
                if (appInfo.isRom) {
                    val logosDir = File(context.cacheDir, "gamefocus_logos").apply { mkdirs() }
                    File(logosDir, "${appInfo.packageName}.png")
                } else {
                    val iconsDir = File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
                    File(iconsDir, "${appInfo.packageName}.png")
                }
            if (file.exists() && file.length() > 0) {
                val startTime = System.currentTimeMillis()
                val diskBitmap =
                    try {
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }

                if (diskBitmap != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    AppLog.d(TAG, "Loaded disk-cached ${if (appInfo.isRom) "logo" else "icon"} PNG for ${appInfo.label} in ${elapsed}ms")
                    iconCache.put(cacheKey, diskBitmap)
                    return@withContext diskBitmap
                }
            }

            val iconDrawable = getAppIcon(context, appInfo.packageName, appInfo.activityName) ?: return@withContext null
            val startTime = System.currentTimeMillis()
            val androidBmp = iconDrawable.toAndroidBitmap() ?: return@withContext null

            val elapsed = System.currentTimeMillis() - startTime
            AppLog.d(TAG, "Converted app icon for ${appInfo.label} in ${elapsed}ms")

            val imageBitmap = androidBmp.asImageBitmap()
            iconCache.put(cacheKey, imageBitmap)

            try {
                val fileDir =
                    if (appInfo.isRom) {
                        File(context.cacheDir, "gamefocus_logos").apply { mkdirs() }
                    } else {
                        File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
                    }
                val iconFile = File(fileDir, "${appInfo.packageName}.png")
                FileOutputStream(iconFile).use { out ->
                    androidBmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to cache icon PNG for ${appInfo.label}: ${e.message}")
            }
            imageBitmap
        }
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
private fun Modifier.noFocusClickable(
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
private fun getCategoryName(category: GameFocusCategory): String =
    when (category) {
        is GameFocusCategory.RomSystem -> category.displayName
        else -> stringResource(category.stringResId)
    }

@Composable
private fun InteractiveCategoryHeader(
    selectedCategory: GameFocusCategory,
    categories: List<GameFocusCategory>,
    onCategoryUp: () -> Unit = {},
    onCategoryDown: () -> Unit = {},
    enabled: Boolean = true,
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
    val context = LocalContext.current

    val coverBitmap by produceState<ImageBitmap?>(
        initialValue = FocusImageCache.getCoverBitmap(appInfo),
        key1 = appInfo.packageName,
        key2 = appInfo.coverLastModified,
    ) {
        value = FocusImageCache.getCoverBitmap(appInfo)
        if (appInfo.coverPath != null && value == null) {
            value = FocusImageCache.getCoverBitmapAsync(appInfo)
        }
    }

    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = FocusImageCache.getCachedIconBitmap(appInfo.packageName),
        key1 = appInfo.packageName,
        key2 = appInfo.coverLastModified,
    ) {
        value = FocusImageCache.getCachedIconBitmap(appInfo.packageName)
        if (coverBitmap == null && value == null) {
            value = FocusImageCache.getIconBitmapAsync(context, appInfo)
        }
    }

    val visibilityOffAlpha by animateFloatAsState(
        targetValue = if (isHidden) FTL_HIDDEN_BADGE_ALPHA else FTL_VISIBLE_BADGE_ALPHA,
        animationSpec = tween(durationMillis = FTL_HIDE_ANIMATION_DURATION_MS),
        label = "LauncherVisibilityOffAlpha",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val currentCover = coverBitmap
        val currentIcon = iconBitmap
        if (currentCover != null) {
            Image(
                bitmap = currentCover,
                contentDescription = appInfo.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (appInfo.isRom) {
            if (currentIcon != null) {
                Image(
                    bitmap = currentIcon,
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
                            .clip(RoundedCornerShape(FTL_ROM_ICON_CORNER_RADIUS))
                            .background(appColors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialSymbol(
                        name = "sports_esports",
                        size = FTL_FALLBACK_ICON_SIZE,
                        tint = appColors.accent,
                    )
                }
            }
        } else if (currentIcon != null) {
            Image(
                bitmap = currentIcon,
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
                        .clip(RoundedCornerShape(FTL_ROM_ICON_CORNER_RADIUS))
                        .background(appColors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = appInfo.label,
                    tint = appColors.accent,
                    modifier = Modifier.size(FTL_FALLBACK_ICON_SIZE),
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

        if (visibilityOffAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(FTL_BADGE_PADDING)
                        .graphicsLayer { alpha = visibilityOffAlpha },
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
