package com.stormpanda.megingiard.gamefocus

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager
import com.stormpanda.megingiard.catalog.LibraryIconRef
import com.stormpanda.megingiard.catalog.PcEmulators
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SYSTEM_ID_PC
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import com.stormpanda.megingiard.catalog.SwitchEmulators
import com.stormpanda.megingiard.gamefocus.domain.GameFocusGalleryManager
import com.stormpanda.megingiard.media.SteamGridDbClient
import com.stormpanda.megingiard.media.SteamGridDbGame
import com.stormpanda.megingiard.media.SteamGridDbImage
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayContainer
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.stormpanda.megingiard.shared.ui.R as SharedUiR

private const val TAG = "GFEditGameOverlay"
private const val GF_TRANSITION_DURATION_MS = 150
private const val GF_POSTER_ASPECT_RATIO = 2f / 3f
private const val GF_CARD_SELECTED_BG_ALPHA = 0.25f
private const val GF_GRIDS_IMAGE_TYPE = "grids"

private val GF_POSTER_HEIGHT = 200.dp
private val GF_POSTER_CORNER_RADIUS = 16.dp
private val GF_POSTER_SHAPE = RoundedCornerShape(GF_POSTER_CORNER_RADIUS)
private val GF_STATUS_BOX_HEIGHT = 160.dp
private val GF_ROW_SPACING = 12.dp
private val GF_ROW_V_PADDING = 4.dp
private val GF_SPACING_12 = 12.dp
private val GF_SPACING_8 = 8.dp
private val GF_PROGRESS_SIZE_SMALL = 24.dp
private val GF_PROGRESS_STROKE = 2.dp
private val GF_BADGE_CONTAINER_SIZE = 24.dp
private val GF_BADGE_ICON_SIZE = 14.dp
private val GF_ICON_THUMB_SIZE = 140.dp
private val GF_ICON_SHAPE = RoundedCornerShape(GF_POSTER_CORNER_RADIUS)

internal enum class EditGameCategory(
    val titleResId: Int,
    val icon: ImageVector,
) {
    GAME_INFO(R.string.gamefocus_cat_game_info, Icons.Rounded.Info),
    SCRAPING(R.string.gamefocus_cat_scraping, Icons.Rounded.Image),
    LIBRARY_ICON(R.string.gamefocus_cat_library_icon, Icons.Rounded.Apps),
    COLLECTIONS(R.string.gamefocus_cat_collections, Icons.Rounded.CollectionsBookmark),
}

@Composable
fun GameFocusEditGameOverlay(
    appInfo: InstalledAppInfo,
    apiKey: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenManageGalleries: (() -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    val categories = remember { EditGameCategory.entries.toList() }
    var selectedCategory by remember { mutableStateOf(EditGameCategory.GAME_INFO) }

    val installedApps by InstalledAppsManager.installedApps.collectAsStateWithLifecycle()
    val romApps by RomManager.romApps.collectAsStateWithLifecycle()

    val liveAppInfo =
        remember(appInfo.packageName, appInfo.isRom, installedApps, romApps) {
            if (appInfo.isRom) {
                romApps.find { it.packageName == appInfo.packageName } ?: appInfo
            } else {
                installedApps.find { it.packageName == appInfo.packageName } ?: appInfo
            }
        }

    PrimaryOverlayContainer(
        title = stringResource(R.string.gamefocus_edit_overlay_title),
        icon = Icons.Rounded.Edit,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        GamepadTwoPaneScaffold(
            breadcrumbs = listOf(stringResource(selectedCategory.titleResId)),
            scrollableDeck = false,
            sidebarContent = {
                categories.forEach { category ->
                    GamepadCategoryTile(
                        title = stringResource(category.titleResId),
                        icon = category.icon,
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = category
                        },
                    )
                }
            },
            content = {
                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        slideInHorizontally(animationSpec = tween(GF_TRANSITION_DURATION_MS)) { width -> width * direction }
                            .togetherWith(
                                slideOutHorizontally(animationSpec = tween(GF_TRANSITION_DURATION_MS)) { width ->
                                    -width * direction
                                },
                            )
                    },
                    label = "EditCategoryAnimation",
                ) { category ->
                    when (category) {
                        EditGameCategory.GAME_INFO -> {
                            GameInfoDeckContent(
                                appInfo = liveAppInfo,
                                onDismiss = onDismiss,
                            )
                        }

                        EditGameCategory.SCRAPING -> {
                            ScrapingDeckContent(
                                appInfo = liveAppInfo,
                                apiKey = apiKey,
                                accentColor = appColors.accent,
                            )
                        }

                        EditGameCategory.LIBRARY_ICON -> {
                            LibraryIconDeckContent(
                                appInfo = liveAppInfo,
                                apiKey = apiKey,
                                accentColor = appColors.accent,
                            )
                        }

                        EditGameCategory.COLLECTIONS -> {
                            CollectionsMembershipDeckContent(
                                appInfo = liveAppInfo,
                                onOpenManageGalleries = onOpenManageGalleries,
                            )
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameInfoDeckContent(
    appInfo: InstalledAppInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val defaultTitle = appInfo.defaultLabel
    var editedTitle by remember(appInfo.packageName) { mutableStateOf(appInfo.label) }
    val normalizedTitle = editedTitle.trim()
    val isTitleChanged = normalizedTitle.isNotEmpty() && normalizedTitle != appInfo.label
    val canResetToDefault = defaultTitle != null && (appInfo.label != defaultTitle || editedTitle != defaultTitle)

    val isMultiEmulatorRom =
        appInfo.isRom && (appInfo.systemId == SYSTEM_ID_SWITCH || appInfo.systemId == SYSTEM_ID_PC)
    val runnerChoices =
        remember(
            appInfo.packageName,
            appInfo.isRom,
            appInfo.systemId,
            appInfo.defaultEmulatorPackage,
            appInfo.defaultRetroArchCore,
        ) {
            val installedSwitch =
                if (appInfo.systemId == SYSTEM_ID_SWITCH) {
                    SwitchEmulators.getInstalledEmulators(context)
                } else {
                    emptyList()
                }
            val installedPc =
                if (appInfo.systemId == SYSTEM_ID_PC) {
                    PcEmulators.getInstalledEmulators(context)
                } else {
                    emptyList()
                }
            buildRomRunnerChoices(
                appInfo = appInfo,
                installedSwitchEmulators = installedSwitch,
                installedPcEmulators = installedPc,
                formatFolderDefault = { defaultName ->
                    context.getString(R.string.gamefocus_rom_runner_folder_default, defaultName)
                },
            )
        }
    var editedRomRunner by remember(appInfo.packageName) { mutableStateOf(appInfo.customRomRunner) }
    val selectedRunnerIndex =
        remember(runnerChoices, editedRomRunner) {
            runnerChoices.indexOfFirst { it.overrideValue == editedRomRunner }.takeIf { it >= 0 } ?: 0
        }
    val isRunnerChanged =
        appInfo.isRom && runnerChoices.isNotEmpty() && editedRomRunner != appInfo.customRomRunner
    val isChanged = isTitleChanged || isRunnerChanged

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = isChanged,
            onSave = {
                if (isChanged && normalizedTitle.isNotEmpty()) {
                    if (isTitleChanged) {
                        if (normalizedTitle == defaultTitle) {
                            InstalledAppsManager.resetAppLabel(context, appInfo.packageName)
                        } else {
                            InstalledAppsManager.updateAppLabel(context, appInfo.packageName, normalizedTitle)
                        }
                    }
                    if (isRunnerChanged) {
                        InstalledAppsManager.setCustomRomRunner(context, appInfo.packageName, editedRomRunner)
                    }
                    onDismiss()
                }
            },
            onDiscard = onDismiss,
        )

    GamepadDeck {
        GamepadTextFieldCard(
            title = stringResource(R.string.gamefocus_field_display_name_title),
            description = stringResource(R.string.gamefocus_field_display_name_desc),
            placeholder = stringResource(R.string.gamefocus_field_display_name_placeholder),
            value = editedTitle,
            onValueChange = { editedTitle = it },
            icon = Icons.Rounded.Edit,
            isError = normalizedTitle.isEmpty(),
            modifier = Modifier.firstDeckItem(),
        )

        if (defaultTitle != null) {
            GamepadActionCard(
                title = stringResource(R.string.gamefocus_action_reset_display_name_title),
                description = stringResource(R.string.gamefocus_action_reset_display_name_desc, defaultTitle),
                icon = Icons.Rounded.RestartAlt,
                enabled = canResetToDefault,
                onClick = {
                    val restored = InstalledAppsManager.resetAppLabel(context, appInfo.packageName) ?: defaultTitle
                    editedTitle = restored
                    AppLog.i(TAG, "Display name reset to default for ${appInfo.packageName}")
                },
            )
        }

        if (runnerChoices.isNotEmpty()) {
            val currentChoice = runnerChoices[selectedRunnerIndex]
            GamepadChoiceCard(
                title =
                    stringResource(
                        if (isMultiEmulatorRom) {
                            R.string.gamefocus_field_rom_emulator_title
                        } else {
                            R.string.gamefocus_field_rom_core_title
                        },
                    ),
                description =
                    stringResource(
                        if (isMultiEmulatorRom) {
                            R.string.gamefocus_field_rom_emulator_desc
                        } else {
                            R.string.gamefocus_field_rom_core_desc
                        },
                    ),
                selectedText = currentChoice.displayLabel,
                icon = Icons.Rounded.Tune,
                enabled = runnerChoices.size > 1,
                onPrevious = {
                    val prevIdx = (selectedRunnerIndex - 1 + runnerChoices.size) % runnerChoices.size
                    editedRomRunner = runnerChoices[prevIdx].overrideValue
                    AppLog.d(TAG, "Selected previous ROM runner for ${appInfo.packageName}: ${editedRomRunner ?: "default"}")
                },
                onNext = {
                    val nextIdx = (selectedRunnerIndex + 1) % runnerChoices.size
                    editedRomRunner = runnerChoices[nextIdx].overrideValue
                    AppLog.d(TAG, "Selected next ROM runner for ${appInfo.packageName}: ${editedRomRunner ?: "default"}")
                },
            )
        }

        GamepadSaveExitActionRow(
            title = stringResource(R.string.gamefocus_action_save_game_info_title),
            description = stringResource(R.string.gamefocus_action_save_game_info_desc),
            pulseOnChanges = isChanged,
            saveActionText = stringResource(SharedUiR.string.gamepad_action_save),
            saveIcon = Icons.Rounded.Save,
            enabled = isChanged,
            showExitPrompt = promptState.showExitPrompt,
            onDismissPrompt = promptState.dismissPrompt,
            saveFocusRequester = promptState.focusRequester,
            bringIntoViewRequester = promptState.bringIntoViewRequester,
            onSave = promptState.onSave,
            onDiscard = promptState.onDiscard,
        )
    }
}

@Composable
private fun ScrapingDeckContent(
    appInfo: InstalledAppInfo,
    apiKey: String,
    accentColor: Color,
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    SteamGridDbArtworkDeck(
        appInfo = appInfo,
        apiKey = apiKey,
        accentColor = accentColor,
        appliedImageId = if (appInfo.coverPath != null) appInfo.coverImageId else null,
        appliedImageType = null,
        isDefault = appInfo.coverOptedOut,
        initialArtworkType = GF_GRIDS_IMAGE_TYPE,
        thumbWidth = GF_POSTER_HEIGHT * GF_POSTER_ASPECT_RATIO,
        thumbHeight = GF_POSTER_HEIGHT,
        thumbShape = GF_POSTER_SHAPE,
        contentScaleForType = { ContentScale.Crop },
        renderToggleCard = { isDefault, isDownloading, onToggleChange ->
            GamepadToggleCard(
                title =
                    if (appInfo.isRom) {
                        stringResource(R.string.gamefocus_toggle_use_rom_icon_title)
                    } else {
                        stringResource(R.string.gamefocus_toggle_use_app_icon_title)
                    },
                description =
                    if (appInfo.isRom) {
                        stringResource(R.string.gamefocus_toggle_use_rom_icon_desc)
                    } else {
                        stringResource(R.string.gamefocus_toggle_use_app_icon_desc)
                    },
                checked = isDefault,
                onCheckedChange = onToggleChange,
                enabled = !isDownloading,
                modifier = Modifier.firstDeckItem(),
            )
        },
        renderDefaultActiveNotice = {
            GamepadInfoBox(
                text =
                    if (appInfo.isRom) {
                        stringResource(R.string.gamefocus_scraping_using_rom_icon_notice)
                    } else {
                        stringResource(R.string.gamefocus_scraping_using_app_icon_notice)
                    },
                icon = Icons.Rounded.Apps,
                iconTint = appColors.onSurfaceSecondary,
            )
        },
        emptyImagesNoticeRes = R.string.steamgriddb_no_covers_found,
        onSaveArtworkBytes = { image, bytes, _ ->
            val savedPath =
                InstalledAppsManager.applyCustomCover(context, appInfo.packageName, bytes, image.id) {
                    AppPaletteExtractor.invalidatePalette(appInfo.packageName)
                }
            savedPath != null
        },
        onRevertToDefault = {
            InstalledAppsManager.revertToDefaultCover(context, appInfo.packageName) {
                AppPaletteExtractor.invalidatePalette(appInfo.packageName)
            }
        },
        onSetOptOut = { optedOut ->
            InstalledAppsManager.setCoverOptedOut(context, appInfo.packageName, optedOut)
        },
    )
}

@Composable
private fun LibraryIconDeckContent(
    appInfo: InstalledAppInfo,
    apiKey: String,
    accentColor: Color,
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    SteamGridDbArtworkDeck(
        appInfo = appInfo,
        apiKey = apiKey,
        accentColor = accentColor,
        appliedImageId = if (appInfo.libraryIconPath != null) appInfo.libraryIconImageId else null,
        appliedImageType = if (appInfo.libraryIconPath != null) appInfo.libraryIconType else null,
        isDefault = appInfo.libraryIconOptedOut,
        initialArtworkType = appInfo.libraryIconType ?: LibraryIconRef.TYPE_ICONS,
        thumbWidth = GF_ICON_THUMB_SIZE,
        thumbHeight = GF_ICON_THUMB_SIZE,
        thumbShape = GF_ICON_SHAPE,
        contentScaleForType = { type ->
            if (type == LibraryIconRef.TYPE_LOGOS) {
                ContentScale.Fit
            } else {
                ContentScale.Crop
            }
        },
        renderToggleCard = { isDefault, isDownloading, onToggleChange ->
            GamepadToggleCard(
                title = stringResource(R.string.gamefocus_toggle_use_default_icon_title),
                description = stringResource(R.string.gamefocus_toggle_use_default_icon_desc),
                checked = isDefault,
                onCheckedChange = onToggleChange,
                enabled = !isDownloading,
                modifier = Modifier.firstDeckItem(),
            )
        },
        renderDefaultActiveNotice = {
            GamepadInfoBox(
                text = stringResource(R.string.gamefocus_scraping_using_default_icon_notice),
                icon = Icons.Rounded.Apps,
                iconTint = appColors.onSurfaceSecondary,
            )
        },
        renderTypeChoiceCard = { currentType, onToggleType ->
            GamepadChoiceCard(
                title = stringResource(R.string.gamefocus_scraping_type_choice_title),
                description = stringResource(R.string.gamefocus_scraping_type_choice_desc),
                selectedText =
                    if (currentType == LibraryIconRef.TYPE_ICONS) {
                        stringResource(R.string.gamefocus_scraping_type_icons)
                    } else {
                        stringResource(R.string.gamefocus_scraping_type_logos)
                    },
                icon = Icons.Rounded.Tune,
                enabled = true,
                onPrevious = onToggleType,
                onNext = onToggleType,
            )
        },
        emptyImagesNoticeRes = R.string.gamefocus_scraping_no_icons_found,
        onSaveArtworkBytes = { image, bytes, type ->
            val savedPath =
                InstalledAppsManager.applyCustomLibraryIcon(
                    context = context,
                    packageName = appInfo.packageName,
                    bytes = bytes,
                    imageId = image.id,
                    type = type,
                    onIconWritten = {
                        FocusImageCache.invalidateLibraryIcon(appInfo.packageName)
                    },
                )
            savedPath != null
        },
        onRevertToDefault = {
            InstalledAppsManager.revertToDefaultLibraryIcon(
                context = context,
                packageName = appInfo.packageName,
                onIconRemoved = {
                    FocusImageCache.invalidateLibraryIcon(appInfo.packageName)
                },
            )
        },
        onSetOptOut = { optedOut ->
            InstalledAppsManager.setLibraryIconOptedOut(context, appInfo.packageName, optedOut)
        },
    )
}

@Composable
private fun SteamGridDbArtworkDeck(
    appInfo: InstalledAppInfo,
    apiKey: String,
    accentColor: Color,
    appliedImageId: Int?,
    appliedImageType: String?,
    isDefault: Boolean,
    initialArtworkType: String,
    thumbWidth: Dp,
    thumbHeight: Dp,
    thumbShape: Shape,
    contentScaleForType: (String) -> ContentScale,
    renderToggleCard: @Composable (isDefault: Boolean, isDownloading: Boolean, onToggleChange: (Boolean) -> Unit) -> Unit,
    renderDefaultActiveNotice: @Composable () -> Unit,
    renderTypeChoiceCard: (@Composable (currentType: String, onToggleType: () -> Unit) -> Unit)? = null,
    @StringRes emptyImagesNoticeRes: Int,
    onSaveArtworkBytes: suspend (image: SteamGridDbImage, bytes: ByteArray, type: String) -> Boolean,
    onRevertToDefault: suspend () -> Unit,
    onSetOptOut: (Boolean) -> Unit,
) {
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { thumbWidth.roundToPx() }
    val thumbHeightPx = with(density) { thumbHeight.roundToPx() }

    var selectedArtworkType by remember(appInfo.packageName, initialArtworkType) { mutableStateOf(initialArtworkType) }

    var searchQuery by remember(appInfo.packageName) {
        mutableStateOf(SteamGridDbClient.cleanSearchQuery(appInfo.label))
    }

    var games by remember { mutableStateOf<List<SteamGridDbGame>>(emptyList()) }
    var selectedGameIndex by remember { mutableIntStateOf(0) }
    var isSearchLoading by remember { mutableStateOf(false) }
    var isImagesLoading by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var scrapeErrorRes by remember { mutableStateOf<Int?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val isTokenMissing = apiKey.isBlank()
    val currentGame = games.getOrNull(selectedGameIndex)
    val rowState = rememberLazyListState()

    val onRevert: () -> Unit = {
        downloadJob?.cancel()
        downloadJob = null
        isDownloading = false
        scope.launch {
            // revertToDefault* persists the opt-out itself; no separate onSetOptOut(true) needed.
            onRevertToDefault()
        }
    }

    val onApplyArtwork: (SteamGridDbImage) -> Unit = { image ->
        if (!isDownloading) {
            isDownloading = true
            scrapeErrorRes = null
            downloadJob?.cancel()
            downloadJob =
                scope.launch {
                    try {
                        AppLog.i(TAG, "Downloading SteamGridDB artwork for ${appInfo.label} from: ${image.url}")
                        val bytesResult = SteamGridDbClient.downloadImageBytes(image.url)
                        val bytes = bytesResult.getOrNull()
                        if (bytes == null) {
                            AppLog.w(
                                TAG,
                                "Artwork download failed for ${appInfo.packageName}: ${bytesResult.exceptionOrNull()?.message}",
                            )
                            scrapeErrorRes = steamGridDbErrorMessageRes(bytesResult.exceptionOrNull())
                        } else {
                            val saved = onSaveArtworkBytes(image, bytes, selectedArtworkType)
                            if (!saved) {
                                scrapeErrorRes = R.string.steamgriddb_error_save_failed
                            }
                        }
                    } finally {
                        isDownloading = false
                    }
                }
        }
    }

    LaunchedEffect(searchQuery, apiKey, isDefault) {
        if (isDefault || isTokenMissing || searchQuery.isBlank()) {
            isSearchLoading = false
            games = emptyList()
            selectedGameIndex = 0
            return@LaunchedEffect
        }

        AppLog.i(TAG, "Searching SteamGridDB games for '$searchQuery'")
        isSearchLoading = true
        scrapeErrorRes = null
        val searchRes = SteamGridDbClient.searchGames(searchQuery, apiKey)
        isSearchLoading = false
        searchRes
            .onSuccess { fetchedGames ->
                games = fetchedGames
                selectedGameIndex = 0
            }.onFailure { err ->
                AppLog.w(TAG, "SteamGridDB search failed for '$searchQuery': ${err.message}")
                scrapeErrorRes = steamGridDbErrorMessageRes(err)
                games = emptyList()
                selectedGameIndex = 0
            }
    }

    LaunchedEffect(currentGame?.id, selectedArtworkType, isDefault) {
        val gameId = currentGame?.id
        if (isDefault || gameId == null || isTokenMissing) {
            images = emptyList()
            isImagesLoading = false
            return@LaunchedEffect
        }
        isImagesLoading = true
        scrapeErrorRes = null
        images = emptyList()
        val imagesRes = SteamGridDbClient.fetchImages(gameId, selectedArtworkType, apiKey)
        isImagesLoading = false
        imagesRes
            .onSuccess { fetchedImages ->
                images = fetchedImages
            }.onFailure { err ->
                AppLog.w(TAG, "SteamGridDB image fetch failed for gameId=$gameId, type=$selectedArtworkType: ${err.message}")
                scrapeErrorRes = steamGridDbErrorMessageRes(err)
                images = emptyList()
            }
    }

    GamepadDeck {
        // ── 1. Toggle Card ───────────────────────────────────────────
        renderToggleCard(isDefault, isDownloading) { checked ->
            if (checked) {
                onRevert()
            } else {
                onSetOptOut(false)
            }
        }

        // ── 2. Search Query Card ─────────────────────────────────────
        GamepadTextFieldCard(
            title = stringResource(R.string.gamefocus_scraping_search_term_title),
            description = stringResource(R.string.gamefocus_scraping_search_term_desc),
            placeholder = stringResource(R.string.steamgriddb_search_placeholder),
            value = searchQuery,
            onValueChange = { searchQuery = it },
            icon = Icons.Rounded.Search,
            enabled = !isDefault,
        )

        // ── 3. Matched Game Card ─────────────────────────────────────
        if (games.isNotEmpty()) {
            val gameLabel =
                if (games.size > 1) {
                    "${selectedGameIndex + 1}/${games.size}: ${currentGame?.name.orEmpty()}"
                } else {
                    currentGame?.name.orEmpty()
                }
            GamepadChoiceCard(
                title = stringResource(R.string.gamefocus_scraping_matched_game_title),
                description = stringResource(R.string.gamefocus_scraping_matched_game_desc),
                selectedText = gameLabel,
                icon = Icons.Rounded.SportsEsports,
                enabled = !isDefault && games.size > 1,
                onPrevious = {
                    selectedGameIndex = (selectedGameIndex - 1 + games.size) % games.size
                },
                onNext = {
                    selectedGameIndex = (selectedGameIndex + 1) % games.size
                },
            )
        }

        // ── 4. Optional Type Choice Card ─────────────────────────────
        renderTypeChoiceCard?.invoke(selectedArtworkType) {
            selectedArtworkType =
                if (selectedArtworkType == LibraryIconRef.TYPE_ICONS) {
                    LibraryIconRef.TYPE_LOGOS
                } else {
                    LibraryIconRef.TYPE_ICONS
                }
        }

        // ── 5. Section Header ────────────────────────────────────────
        GamepadSectionHeader(
            text = stringResource(R.string.gamefocus_scraping_images_section),
            color = accentColor,
        )

        // ── 6. Status states or horizontal artwork gallery ───────────
        val errorRes = scrapeErrorRes
        if (isDefault) {
            renderDefaultActiveNotice()
        } else if (isTokenMissing) {
            GamepadInfoBox(
                text = stringResource(R.string.steamgriddb_token_missing_message),
                icon = Icons.Rounded.Key,
                iconTint = appColors.onSurfaceSecondary,
            )
        } else if (isDownloading) {
            ScrapingStatusBox(textRes = R.string.steamgriddb_status_downloading, accentColor = accentColor)
        } else if (isSearchLoading || isImagesLoading) {
            ScrapingStatusBox(
                textRes = if (isSearchLoading) R.string.steamgriddb_status_searching else R.string.steamgriddb_status_fetching,
                accentColor = accentColor,
            )
        } else if (errorRes != null) {
            GamepadInfoBox(
                text = stringResource(errorRes),
                icon = Icons.Rounded.Warning,
                iconTint = appColors.error,
            )
        } else if (games.isEmpty() && searchQuery.isNotBlank()) {
            GamepadInfoBox(
                text = stringResource(R.string.steamgriddb_no_games_found, searchQuery),
                icon = Icons.Rounded.Search,
                iconTint = appColors.onSurfaceSecondary,
            )
        } else if (images.isEmpty() && currentGame != null) {
            GamepadInfoBox(
                text = stringResource(emptyImagesNoticeRes, currentGame.name),
                icon = Icons.Rounded.Image,
                iconTint = appColors.onSurfaceSecondary,
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = thumbWidth
                val horizontalPadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)

                LazyRow(
                    state = rowState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = GF_ROW_V_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(GF_ROW_SPACING),
                ) {
                    itemsIndexed(images, key = { _, it -> it.id }) { index, image ->
                        val isApplied =
                            !isDefault &&
                                appliedImageId == image.id &&
                                (appliedImageType == null || appliedImageType == selectedArtworkType)

                        GamepadFocusCard(
                            onClick = { onApplyArtwork(image) },
                            onFocusChanged = { isFocused ->
                                if (isFocused) {
                                    scope.launch {
                                        rowState.animateScrollToItem(index)
                                    }
                                }
                            },
                            shape = thumbShape,
                            contentPadding = PaddingValues(0.dp),
                            clipToShape = true,
                            unfocusedBorderColor = if (isApplied) accentColor else appColors.subduedBorder,
                            modifier = Modifier.size(thumbWidth, thumbHeight),
                            cardBgColor =
                                if (isApplied) {
                                    accentColor.copy(alpha = GF_CARD_SELECTED_BG_ALPHA)
                                } else {
                                    appColors.surface
                                },
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SteamGridDbImageThumbnail(
                                    url = image.thumb.ifBlank { image.url },
                                    reqWidthPx = thumbWidthPx,
                                    reqHeightPx = thumbHeightPx,
                                    contentDescription = stringResource(R.string.steamgriddb_cd_artwork_option),
                                    contentScale = contentScaleForType(selectedArtworkType),
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (isApplied) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(GF_SPACING_8)
                                                .size(GF_BADGE_CONTAINER_SIZE)
                                                .background(accentColor, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.gamefocus_scraping_applied_badge_cd),
                                            tint = appColors.onAccent,
                                            modifier = Modifier.size(GF_BADGE_ICON_SIZE),
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
}

@Composable
private fun ScrapingStatusBox(
    @StringRes textRes: Int,
    accentColor: Color,
) {
    val appColors = LocalAppColors.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GF_STATUS_BOX_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = accentColor)
            Spacer(Modifier.height(GF_SPACING_12))
            Text(
                text = stringResource(textRes),
                color = appColors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SteamGridDbImageThumbnail(
    url: String,
    reqWidthPx: Int,
    reqHeightPx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val colors = LocalAppColors.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url, reqWidthPx, reqHeightPx) {
        isLoading = true
        val bytes = SteamGridDbClient.downloadImageBytes(url).getOrNull()
        val decoded =
            bytes?.let {
                withContext(Dispatchers.Default) { decodeSampledBitmap(it, reqWidthPx, reqHeightPx) }
            }
        if (decoded == null) {
            AppLog.w(TAG, "Thumbnail unavailable for $url")
        }
        bitmap = decoded?.asImageBitmap()
        isLoading = false
    }

    Box(
        modifier = modifier.background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val loadedBitmap = bitmap
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(GF_PROGRESS_SIZE_SMALL),
                color = colors.accent,
                strokeWidth = GF_PROGRESS_STROKE,
            )
        } else if (loadedBitmap == null) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.error,
            )
        } else {
            Image(
                bitmap = loadedBitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

@Composable
private fun CollectionsMembershipDeckContent(
    appInfo: InstalledAppInfo,
    onOpenManageGalleries: (() -> Unit)?,
) {
    val context = LocalContext.current
    val galleryConfig by GameFocusGalleryManager.config.collectAsStateWithLifecycle()
    val installedApps by InstalledAppsManager.installedApps.collectAsStateWithLifecycle()
    val romApps by RomManager.romApps.collectAsStateWithLifecycle()
    val catalogPackages =
        remember(installedApps, romApps) {
            buildSet(installedApps.size + romApps.size) {
                installedApps.forEach { add(it.packageName) }
                romApps.forEach { add(it.packageName) }
            }
        }
    val sortedCollections =
        remember(galleryConfig.collections) {
            galleryConfig.collections.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

    GamepadDeck {
        if (sortedCollections.isEmpty()) {
            GamepadInfoBox(
                text = stringResource(R.string.gamefocus_edit_collections_empty_info),
                icon = Icons.Rounded.Info,
                modifier = Modifier.firstDeckItem(),
            )
            if (onOpenManageGalleries != null) {
                GamepadActionCard(
                    title = stringResource(R.string.gamefocus_manage_galleries_title),
                    description = stringResource(R.string.gamefocus_edit_open_manage_galleries_desc),
                    icon = Icons.Rounded.ViewCarousel,
                    onClick = onOpenManageGalleries,
                )
            }
        } else {
            sortedCollections.forEachIndexed { index, collection ->
                val isMember = appInfo.packageName in collection.members
                val memberCount = collectionMemberCount(collection, catalogPackages)
                GamepadToggleCard(
                    title = collection.name,
                    description = stringResource(R.string.gamefocus_collection_member_count, memberCount),
                    checked = isMember,
                    onCheckedChange = { isChecked ->
                        val changed =
                            GameFocusGalleryManager.setCollectionMembership(
                                context = context,
                                collectionId = collection.id,
                                packageName = appInfo.packageName,
                                isMember = isChecked,
                            )
                        AppLog.d(
                            TAG,
                            "Toggled collection ${collection.id} for ${appInfo.packageName} -> isMember=$isChecked (changed=$changed)",
                        )
                    },
                    modifier = if (index == 0) Modifier.firstDeckItem() else Modifier,
                )
            }
        }
    }
}
