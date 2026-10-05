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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.stormpanda.megingiard.shared.ui.R as SharedUiR

private const val TAG = "GameFocusEditGameOverlay"
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

internal enum class EditGameCategory(
    val titleResId: Int,
    val icon: ImageVector,
) {
    GAME_INFO(R.string.gamefocus_cat_game_info, Icons.Rounded.Info),
    SCRAPING(R.string.gamefocus_cat_scraping, Icons.Rounded.Image),
}

@Composable
fun GameFocusEditGameOverlay(
    appInfo: InstalledAppInfo,
    apiKey: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val categories = remember { EditGameCategory.entries.toList() }
    var selectedCategory by remember { mutableStateOf(EditGameCategory.GAME_INFO) }

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
                                appInfo = appInfo,
                                accentColor = appColors.accent,
                                onDismiss = onDismiss,
                            )
                        }

                        EditGameCategory.SCRAPING -> {
                            ScrapingDeckContent(
                                appInfo = appInfo,
                                apiKey = apiKey,
                                accentColor = appColors.accent,
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
    accentColor: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val defaultTitle = appInfo.defaultLabel
    // Label currently persisted in the catalog; tracked locally because [appInfo] is a snapshot.
    var savedTitle by remember(appInfo.packageName) { mutableStateOf(appInfo.label) }
    var editedTitle by remember(appInfo.packageName) { mutableStateOf(appInfo.label) }
    val normalizedTitle = editedTitle.trim()
    val isChanged = normalizedTitle.isNotEmpty() && normalizedTitle != savedTitle
    val canResetToDefault = defaultTitle != null && (savedTitle != defaultTitle || editedTitle != defaultTitle)

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = isChanged,
            onSave = {
                if (isChanged) {
                    if (normalizedTitle == defaultTitle) {
                        InstalledAppsManager.resetAppLabel(context, appInfo.packageName)
                    } else {
                        InstalledAppsManager.updateAppLabel(context, appInfo.packageName, normalizedTitle)
                    }
                    onDismiss()
                }
            },
            onDiscard = onDismiss,
        )

    GamepadDeck(
        accentColor = accentColor,
    ) {
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
                    savedTitle = restored
                    editedTitle = restored
                    AppLog.i(TAG, "Display name reset to default for ${appInfo.packageName}")
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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { GF_POSTER_HEIGHT.roundToPx() }
    val thumbWidthPx = (thumbHeightPx * GF_POSTER_ASPECT_RATIO).roundToInt()

    val initialUseAppIcon = appInfo.coverPath == null
    var useAppIcon by remember(appInfo.packageName, initialUseAppIcon) { mutableStateOf(initialUseAppIcon) }
    // Only a persisted SteamGridDB image ID identifies the applied artwork; untracked covers stay unbadged.
    val initialAppliedImageId = if (appInfo.coverPath != null) appInfo.coverImageId else null
    var appliedImageId by remember(appInfo.packageName, initialAppliedImageId) {
        mutableStateOf(initialAppliedImageId)
    }

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

    val isTokenMissing = apiKey.isBlank()
    val currentGame = games.getOrNull(selectedGameIndex)
    val rowState = rememberLazyListState()

    val onRevertToAppIcon: () -> Unit = {
        scope.launch {
            InstalledAppsManager.revertToDefaultCover(context, appInfo.packageName) {
                AppPaletteExtractor.invalidatePalette(appInfo.packageName)
            }
            appliedImageId = null
        }
    }

    val onApplyArtwork: (SteamGridDbImage) -> Unit = { image ->
        if (!isDownloading) {
            isDownloading = true
            scrapeErrorRes = null
            scope.launch {
                try {
                    AppLog.i(TAG, "Downloading SteamGridDB artwork for ${appInfo.label} from: ${image.url}")
                    val bytesResult = SteamGridDbClient.downloadImageBytes(image.url)
                    val bytes = bytesResult.getOrNull()
                    if (bytes == null) {
                        AppLog.w(TAG, "Artwork download failed for ${appInfo.packageName}: ${bytesResult.exceptionOrNull()?.message}")
                        scrapeErrorRes = steamGridDbErrorMessageRes(bytesResult.exceptionOrNull())
                    } else {
                        val savedPath =
                            InstalledAppsManager.applyCustomCover(context, appInfo.packageName, bytes, image.id) {
                                AppPaletteExtractor.invalidatePalette(appInfo.packageName)
                            }
                        if (savedPath != null) {
                            appliedImageId = image.id
                            useAppIcon = false
                        } else {
                            scrapeErrorRes = R.string.steamgriddb_error_save_failed
                        }
                    }
                } finally {
                    isDownloading = false
                }
            }
        }
    }

    LaunchedEffect(searchQuery, apiKey, useAppIcon) {
        if (useAppIcon || isTokenMissing || searchQuery.isBlank()) {
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

    LaunchedEffect(currentGame?.id, useAppIcon) {
        val gameId = currentGame?.id
        if (useAppIcon || gameId == null || isTokenMissing) {
            images = emptyList()
            isImagesLoading = false
            return@LaunchedEffect
        }
        isImagesLoading = true
        scrapeErrorRes = null
        images = emptyList()
        val imagesRes = SteamGridDbClient.fetchImages(gameId, GF_GRIDS_IMAGE_TYPE, apiKey)
        isImagesLoading = false
        imagesRes
            .onSuccess { fetchedImages ->
                images = fetchedImages
            }.onFailure { err ->
                AppLog.w(TAG, "SteamGridDB image fetch failed for gameId=$gameId: ${err.message}")
                scrapeErrorRes = steamGridDbErrorMessageRes(err)
                images = emptyList()
            }
    }

    GamepadDeck(
        accentColor = accentColor,
    ) {
        // ── 1. Use App Icon Toggle Card ─────────────────────────────
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
            checked = useAppIcon,
            onCheckedChange = { checked ->
                useAppIcon = checked
                if (checked) {
                    onRevertToAppIcon()
                }
            },
            modifier = Modifier.firstDeckItem(),
        )

        // ── 2. Search Query Card ─────────────────────────────────────
        GamepadTextFieldCard(
            title = stringResource(R.string.gamefocus_scraping_search_term_title),
            description = stringResource(R.string.gamefocus_scraping_search_term_desc),
            placeholder = stringResource(R.string.steamgriddb_search_placeholder),
            value = searchQuery,
            onValueChange = { searchQuery = it },
            icon = Icons.Rounded.Search,
            enabled = !useAppIcon,
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
                enabled = !useAppIcon && games.size > 1,
                onPrevious = {
                    selectedGameIndex = (selectedGameIndex - 1 + games.size) % games.size
                },
                onNext = {
                    selectedGameIndex = (selectedGameIndex + 1) % games.size
                },
            )
        }

        // ── 4. Section Header ────────────────────────────────────────
        GamepadSectionHeader(
            text = stringResource(R.string.gamefocus_scraping_images_section),
            color = accentColor,
        )

        // ── 5. Status states or horizontal artwork gallery ───────────
        val errorRes = scrapeErrorRes
        if (useAppIcon) {
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
                text = stringResource(R.string.steamgriddb_no_covers_found, currentGame.name),
                icon = Icons.Rounded.Image,
                iconTint = appColors.onSurfaceSecondary,
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = GF_POSTER_HEIGHT * GF_POSTER_ASPECT_RATIO
                val horizontalPadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)

                LazyRow(
                    state = rowState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = GF_ROW_V_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(GF_ROW_SPACING),
                ) {
                    itemsIndexed(images, key = { _, it -> it.id }) { index, image ->
                        val isApplied = !useAppIcon && appliedImageId == image.id

                        GamepadFocusCard(
                            onClick = {
                                onApplyArtwork(image)
                            },
                            onFocusChanged = { isFocused ->
                                if (isFocused) {
                                    scope.launch {
                                        rowState.animateScrollToItem(index)
                                    }
                                }
                            },
                            shape = GF_POSTER_SHAPE,
                            contentPadding = PaddingValues(0.dp),
                            clipToShape = true,
                            unfocusedBorderColor = if (isApplied) accentColor else appColors.subduedBorder,
                            modifier =
                                Modifier
                                    .height(GF_POSTER_HEIGHT)
                                    .aspectRatio(GF_POSTER_ASPECT_RATIO),
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
                contentScale = ContentScale.Crop,
            )
        }
    }
}
