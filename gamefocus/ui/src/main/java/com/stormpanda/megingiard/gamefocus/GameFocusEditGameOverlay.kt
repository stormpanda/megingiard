package com.stormpanda.megingiard.gamefocus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayContainer
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "GameFocusEditGameOverlay"
private val GF_CHIP_CORNER = 8.dp
private val GF_CHIP_SHAPE = RoundedCornerShape(GF_CHIP_CORNER)
private val GF_BADGE_CORNER = 4.dp
private val GF_BADGE_SHAPE = RoundedCornerShape(GF_BADGE_CORNER)
private val GF_POSTER_CAROUSEL_HEIGHT = 200.dp
private val GF_POSTER_WIDTH = 110.dp
private val GF_POSTER_HEIGHT = 165.dp
private val GF_POSTER_SPACING = 10.dp
private val GF_POSTER_CORNER = 10.dp
private const val GF_TRANSITION_DURATION_MS = 150
private val GF_PROGRESS_SIZE_LARGE = 36.dp
private val GF_PROGRESS_SIZE_SMALL = 24.dp
private val GF_STATUS_SPACING = 10.dp
private val GF_MESSAGE_HORIZONTAL_PADDING = 16.dp
private val GF_GAP_BETWEEN_CHIPS = 8.dp
private val GF_CHIP_CONTENT_PADDING = 4.dp
private val GF_CHIP_HORIZONTAL_PADDING = 14.dp
private val GF_CHIP_VERTICAL_PADDING = 6.dp
private val GF_BORDER_WIDTH_SELECTED = 2.dp
private val GF_BORDER_WIDTH_UNSELECTED = 1.dp
private val GF_BADGE_HORIZONTAL_PADDING = 7.dp
private val GF_BADGE_VERTICAL_PADDING = 4.dp

internal enum class EditGameCategory(
    val titleResId: Int,
    val icon: ImageVector,
) {
    GAME_INFO(R.string.gamefocus_cat_game_info, Icons.Rounded.Info),
    SCRAPING(R.string.gamefocus_cat_scraping, Icons.Rounded.Image),
}

@OptIn(ExperimentalFoundationApi::class)
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
                                    -width *
                                        direction
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
                                onDismiss = onDismiss,
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
    var editedTitle by remember(appInfo.packageName, appInfo.label) { mutableStateOf(appInfo.label) }
    val normalizedTitle = editedTitle.trim()
    val isChanged = normalizedTitle != appInfo.label && normalizedTitle.isNotEmpty()
    val isConfirmEnabled = isChanged && normalizedTitle.isNotBlank()

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = isChanged,
            onSave = {
                if (isConfirmEnabled) {
                    InstalledAppsManager.updateAppLabel(context, appInfo.packageName, normalizedTitle)
                    onDismiss()
                }
            },
            onDiscard = onDismiss,
        )

    GamepadDeck(
        title = stringResource(R.string.gamefocus_cat_game_info),
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

        GamepadSaveExitActionRow(
            title = stringResource(R.string.gamefocus_action_save_game_info_title),
            description = stringResource(R.string.gamefocus_action_save_game_info_desc),
            pulseOnChanges = isChanged,
            saveActionText = stringResource(com.stormpanda.megingiard.shared.ui.R.string.gamepad_action_save),
            saveIcon = Icons.Rounded.Save,
            enabled = isConfirmEnabled,
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
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember(appInfo.packageName) {
        mutableStateOf(SteamGridDbClient.cleanSearchQuery(appInfo.label))
    }

    var games by remember { mutableStateOf<List<SteamGridDbGame>>(emptyList()) }
    var selectedGameIndex by remember { mutableIntStateOf(0) }
    var isSearchLoading by remember { mutableStateOf(true) }
    var isImagesLoading by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectingImage by remember { mutableStateOf<SteamGridDbImage?>(null) }
    val currentGame = games.getOrNull(selectedGameIndex)

    val useAppIcon =
        remember(appInfo.packageName) {
            {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val coversDir = File(context.cacheDir, "gamefocus_covers")
                        val targetFile = File(coversDir, "${appInfo.packageName}.png")
                        if (targetFile.exists()) targetFile.delete()
                    }
                    AppPaletteExtractor.invalidatePalette(appInfo.packageName)
                    InstalledAppsManager.updateAppCover(appInfo.packageName, null)
                    InstalledAppsManager.markAppAsScraped(context, appInfo.packageName)
                    AppLog.i(TAG, "Reverted to app icon for ${appInfo.packageName}")
                    onDismiss()
                }
            }
        }

    val onConfirmSelection =
        remember(appInfo.packageName) {
            { imageItem: SteamGridDbImage ->
                if (selectingImage == null) {
                    selectingImage = imageItem
                    scope.launch(Dispatchers.IO) {
                        AppLog.i(TAG, "Downloading SteamGridDB artwork for ${appInfo.label} from: ${imageItem.url}")
                        val bytesResult = SteamGridDbClient.downloadImageBytes(imageItem.url)
                        val bytes = bytesResult.getOrNull()
                        if (bytes != null) {
                            val coversDir = File(context.cacheDir, "gamefocus_covers").apply { mkdirs() }
                            val targetFile = File(coversDir, "${appInfo.packageName}.png")
                            FileOutputStream(targetFile).use { it.write(bytes) }

                            AppPaletteExtractor.invalidatePalette(appInfo.packageName)
                            InstalledAppsManager.updateAppCover(appInfo.packageName, targetFile.absolutePath)
                            InstalledAppsManager.markAppAsScraped(context, appInfo.packageName)
                            AppLog.i(TAG, "Saved artwork cover for ${appInfo.packageName} -> ${targetFile.absolutePath}")
                            withContext(Dispatchers.Main) {
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }

    // Search for games matching current searchQuery
    LaunchedEffect(searchQuery, apiKey) {
        if (apiKey.isBlank()) {
            isSearchLoading = false
            errorMessage = context.getString(R.string.steamgriddb_token_missing_message)
            games = emptyList()
            selectedGameIndex = 0
            return@LaunchedEffect
        }
        AppLog.i(TAG, "Searching SteamGridDB games for '$searchQuery'")
        isSearchLoading = true
        errorMessage = null
        games = emptyList()
        selectedGameIndex = 0

        scope.launch(Dispatchers.IO) {
            val searchRes = SteamGridDbClient.searchGames(searchQuery, apiKey)
            val fetchedGames = searchRes.getOrNull() ?: emptyList()

            withContext(Dispatchers.Main) {
                games = fetchedGames
                isSearchLoading = false
                if (fetchedGames.isEmpty()) {
                    errorMessage = context.getString(R.string.steamgriddb_no_games_found, searchQuery)
                }
            }
        }
    }

    // When selected game changes, fetch its artwork images
    LaunchedEffect(currentGame?.id) {
        val gameId = currentGame?.id
        if (gameId != null && apiKey.isNotBlank()) {
            isImagesLoading = true
            errorMessage = null
            images = emptyList()

            scope.launch(Dispatchers.IO) {
                val imagesRes = SteamGridDbClient.fetchImages(gameId, "grids", apiKey)
                val fetchedImages = imagesRes.getOrNull() ?: emptyList()

                withContext(Dispatchers.Main) {
                    images = fetchedImages
                    isImagesLoading = false
                    if (fetchedImages.isEmpty()) {
                        errorMessage = context.getString(R.string.steamgriddb_no_covers_found, currentGame.name)
                    }
                }
            }
        } else if (gameId == null) {
            images = emptyList()
            isImagesLoading = false
        }
    }

    GamepadDeck(
        title = stringResource(R.string.gamefocus_cat_scraping),
        accentColor = accentColor,
    ) {
        // ── 1. Permanent Search Field Card ─────────────────────────────
        GamepadTextFieldCard(
            title = stringResource(R.string.gamefocus_scraping_search_term_title),
            description = stringResource(R.string.gamefocus_scraping_search_term_desc),
            placeholder = stringResource(R.string.steamgriddb_search_placeholder),
            value = searchQuery,
            onValueChange = { searchQuery = it },
            icon = Icons.Rounded.Search,
            modifier = Modifier.firstDeckItem(),
        )

        // ── 2. Matched Game Choice Card ────────────────────────────────
        if (games.size > 1) {
            GamepadChoiceCard(
                title = stringResource(R.string.gamefocus_scraping_matched_game_title),
                description = stringResource(R.string.gamefocus_scraping_matched_game_desc),
                selectedText = currentGame?.name ?: "",
                onPrevious = {
                    selectedGameIndex = (selectedGameIndex - 1 + games.size) % games.size
                },
                onNext = {
                    selectedGameIndex = (selectedGameIndex + 1) % games.size
                },
            )
        } else if (games.isNotEmpty()) {
            GameSelectionRow(
                games = games,
                selectedIndex = selectedGameIndex,
                onGameSelect = { selectedGameIndex = it },
            )
        }

        // ── 3. Poster Carousel Container ───────────────────────────────
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(GF_POSTER_CAROUSEL_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            if (isSearchLoading || isImagesLoading || selectingImage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        color = appColors.accent,
                        modifier = Modifier.size(GF_PROGRESS_SIZE_LARGE),
                    )
                    Spacer(modifier = Modifier.height(GF_STATUS_SPACING))
                    val loadingStatusText =
                        when {
                            selectingImage != null -> stringResource(R.string.steamgriddb_status_downloading)
                            isSearchLoading -> stringResource(R.string.steamgriddb_status_searching)
                            else -> stringResource(R.string.steamgriddb_status_fetching)
                        }
                    Text(
                        text = loadingStatusText,
                        style = MaterialTheme.typography.bodySmall.copy(color = appColors.onSurfaceSecondary),
                    )
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(color = appColors.onSurfaceSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = GF_MESSAGE_HORIZONTAL_PADDING),
                )
            } else if (images.isNotEmpty()) {
                val dialogPagerState = rememberPagerState(initialPage = 0) { images.size }
                HorizontalPosterCarousel(
                    itemCount = images.size,
                    pagerState = dialogPagerState,
                    onItemClick = { actualIndex ->
                        images.getOrNull(actualIndex)?.let { imageItem ->
                            onConfirmSelection(imageItem)
                        }
                    },
                    posterWidth = GF_POSTER_WIDTH,
                    posterHeight = GF_POSTER_HEIGHT,
                    posterSpacing = GF_POSTER_SPACING,
                    carouselHeight = GF_POSTER_CAROUSEL_HEIGHT,
                    posterCornerRadius = GF_POSTER_CORNER,
                ) { actualIndex, _ ->
                    images.getOrNull(actualIndex)?.let { imageItem ->
                        ArtworkOptionItem(imageItem = imageItem)
                    }
                }
            }
        }

        // ── 4. Revert to App/Default Icon Action Card ───────────────────
        GamepadActionCard(
            title =
                if (appInfo.isRom) {
                    stringResource(R.string.gamefocus_scraping_revert_icon_rom_title)
                } else {
                    stringResource(R.string.gamefocus_scraping_revert_icon_title)
                },
            description = stringResource(R.string.gamefocus_scraping_revert_icon_desc),
            icon = Icons.Rounded.Apps,
            onClick = { useAppIcon() },
        )
    }
}

@Composable
private fun GameSelectionRow(
    games: List<SteamGridDbGame>,
    selectedIndex: Int,
    onGameSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (games.isNotEmpty() && selectedIndex in games.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ShoulderBadge(label = "L1")

        Spacer(modifier = Modifier.width(GF_GAP_BETWEEN_CHIPS))

        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = GF_CHIP_CONTENT_PADDING),
            horizontalArrangement = Arrangement.spacedBy(GF_GAP_BETWEEN_CHIPS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                val isSelected = index == selectedIndex
                Box(
                    modifier =
                        Modifier
                            .clip(GF_CHIP_SHAPE)
                            .background(if (isSelected) appColors.accent else appColors.surfaceVariant)
                            .border(
                                width = if (isSelected) GF_BORDER_WIDTH_SELECTED else GF_BORDER_WIDTH_UNSELECTED,
                                color = if (isSelected) appColors.accent else appColors.divider,
                                shape = GF_CHIP_SHAPE,
                            ).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onGameSelect(index)
                            }.padding(horizontal = GF_CHIP_HORIZONTAL_PADDING, vertical = GF_CHIP_VERTICAL_PADDING),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = game.name,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) appColors.onAccent else appColors.onSurfaceSecondary,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(GF_GAP_BETWEEN_CHIPS))

        ShoulderBadge(label = "R1")
    }
}

@Composable
private fun ShoulderBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Box(
        modifier =
            modifier
                .clip(GF_BADGE_SHAPE)
                .background(appColors.surfaceVariant)
                .border(GF_BORDER_WIDTH_UNSELECTED, appColors.divider, GF_BADGE_SHAPE)
                .padding(horizontal = GF_BADGE_HORIZONTAL_PADDING, vertical = GF_BADGE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = appColors.accent,
                ),
        )
    }
}

@Composable
private fun ArtworkOptionItem(
    imageItem: SteamGridDbImage,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isThumbLoading by remember { mutableStateOf(true) }

    DisposableEffect(imageItem.thumb) {
        onDispose {
            rawBitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    LaunchedEffect(imageItem.thumb) {
        val bytes = SteamGridDbClient.downloadImageBytes(imageItem.thumb).getOrNull()
        if (!isActive) return@LaunchedEffect
        val decoded = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        rawBitmap = decoded
        bitmap = decoded?.asImageBitmap()
        isThumbLoading = false
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = stringResource(R.string.steamgriddb_cd_artwork_option),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (isThumbLoading) {
            CircularProgressIndicator(
                color = appColors.accent,
                modifier = Modifier.size(GF_PROGRESS_SIZE_SMALL),
            )
        } else {
            Text(
                text = stringResource(R.string.steamgriddb_preview_unavailable),
                style = MaterialTheme.typography.labelSmall.copy(color = appColors.onSurfaceSecondary),
                textAlign = TextAlign.Center,
            )
        }
    }
}
