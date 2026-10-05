package com.stormpanda.megingiard.gamefocus

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager
import com.stormpanda.megingiard.media.SteamGridDbClient
import com.stormpanda.megingiard.media.SteamGridDbException
import com.stormpanda.megingiard.media.SteamGridDbGame
import com.stormpanda.megingiard.media.SteamGridDbImage
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
import java.io.File
import java.io.FileOutputStream
import com.stormpanda.megingiard.shared.ui.R as SharedUiR

private const val TAG = "GameFocusEditGameOverlay"
private const val GF_TRANSITION_DURATION_MS = 150
private const val GF_POSTER_ASPECT_RATIO = 0.66f
private const val GF_CARD_SELECTED_BG_ALPHA = 0.25f

private val GF_POSTER_HEIGHT = 200.dp
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
            saveActionText = stringResource(SharedUiR.string.gamepad_action_save),
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    val initialUseAppIcon = appInfo.coverPath == null
    var useAppIcon by remember(appInfo.packageName, initialUseAppIcon) { mutableStateOf(initialUseAppIcon) }
    var appliedImageId by remember(appInfo.packageName) { mutableStateOf<Int?>(null) }

    var searchQuery by remember(appInfo.packageName) {
        mutableStateOf(SteamGridDbClient.cleanSearchQuery(appInfo.label))
    }

    var games by remember { mutableStateOf<List<SteamGridDbGame>>(emptyList()) }
    var selectedGameIndex by remember { mutableIntStateOf(0) }
    var isSearchLoading by remember { mutableStateOf(false) }
    var isImagesLoading by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var scrapeError by remember { mutableStateOf<Throwable?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    val currentGame = games.getOrNull(selectedGameIndex)
    val rowState = rememberLazyListState()

    val onRevertToAppIcon = {
        scope.launch {
            withContext(Dispatchers.IO) {
                val coversDir = File(context.cacheDir, "gamefocus_covers")
                val targetFile = File(coversDir, "${appInfo.packageName}.png")
                if (targetFile.exists()) targetFile.delete()
            }
            AppPaletteExtractor.invalidatePalette(appInfo.packageName)
            InstalledAppsManager.updateAppCover(appInfo.packageName, null)
            InstalledAppsManager.markAppAsScraped(context, appInfo.packageName)
            appliedImageId = null
            AppLog.i(TAG, "Reverted to app icon for ${appInfo.packageName}")
        }
    }

    val onApplyArtwork: (SteamGridDbImage) -> Unit = { image ->
        if (!isDownloading) {
            isDownloading = true
            scope.launch(Dispatchers.IO) {
                AppLog.i(TAG, "Downloading SteamGridDB artwork for ${appInfo.label} from: ${image.url}")
                val bytesResult = SteamGridDbClient.downloadImageBytes(image.url)
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
                        isDownloading = false
                        appliedImageId = image.id
                        useAppIcon = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        scrapeError = bytesResult.exceptionOrNull()
                    }
                }
            }
        }
    }

    LaunchedEffect(searchQuery, apiKey, useAppIcon) {
        if (useAppIcon) {
            isSearchLoading = false
            return@LaunchedEffect
        }
        if (apiKey.isBlank()) {
            isSearchLoading = false
            scrapeError = SteamGridDbException.Unauthorized("Token missing")
            games = emptyList()
            selectedGameIndex = 0
            return@LaunchedEffect
        }
        if (searchQuery.isBlank()) {
            isSearchLoading = false
            games = emptyList()
            selectedGameIndex = 0
            return@LaunchedEffect
        }

        AppLog.i(TAG, "Searching SteamGridDB games for '$searchQuery'")
        isSearchLoading = true
        scrapeError = null

        withContext(Dispatchers.IO) {
            val searchRes = SteamGridDbClient.searchGames(searchQuery, apiKey)
            withContext(Dispatchers.Main) {
                isSearchLoading = false
                searchRes
                    .onSuccess { fetchedGames ->
                        games = fetchedGames
                        selectedGameIndex = 0
                    }.onFailure { err ->
                        scrapeError = err
                        games = emptyList()
                        selectedGameIndex = 0
                    }
            }
        }
    }

    LaunchedEffect(currentGame?.id, useAppIcon) {
        if (useAppIcon) {
            isImagesLoading = false
            return@LaunchedEffect
        }
        val gameId = currentGame?.id
        if (gameId != null && apiKey.isNotBlank()) {
            isImagesLoading = true
            scrapeError = null
            images = emptyList()

            withContext(Dispatchers.IO) {
                val imagesRes = SteamGridDbClient.fetchImages(gameId, "grids", apiKey)
                withContext(Dispatchers.Main) {
                    isImagesLoading = false
                    imagesRes
                        .onSuccess { fetchedImages ->
                            images = fetchedImages
                        }.onFailure { err ->
                            scrapeError = err
                            images = emptyList()
                        }
                }
            }
        } else {
            images = emptyList()
            isImagesLoading = false
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
        } else if (isDownloading) {
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
                        text = stringResource(R.string.steamgriddb_status_downloading),
                        color = appColors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (isSearchLoading || isImagesLoading) {
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
                        text =
                            if (isSearchLoading) {
                                stringResource(R.string.steamgriddb_status_searching)
                            } else {
                                stringResource(R.string.steamgriddb_status_fetching)
                            },
                        color = appColors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (scrapeError != null) {
            val errorText =
                scrapeError?.message?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.steamgriddb_preview_unavailable)
            GamepadInfoBox(
                text = errorText,
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
private fun SteamGridDbImageThumbnail(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var isError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        isLoading = true
        isError = false
        val bytes = SteamGridDbClient.downloadImageBytes(url).getOrNull()
        val decoded = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (decoded != null) {
            bitmap = decoded.asImageBitmap()
        } else {
            isError = true
        }
        isLoading = false
    }

    Box(
        modifier = modifier.background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(GF_PROGRESS_SIZE_SMALL),
                color = colors.accent,
                strokeWidth = GF_PROGRESS_STROKE,
            )
        } else if (isError || bitmap == null) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.error,
            )
        } else {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
