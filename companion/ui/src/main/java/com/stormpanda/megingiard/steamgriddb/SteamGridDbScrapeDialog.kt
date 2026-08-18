package com.stormpanda.megingiard.steamgriddb

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.media.SteamGridDbClient
import com.stormpanda.megingiard.media.SteamGridDbException
import com.stormpanda.megingiard.media.SteamGridDbGame
import com.stormpanda.megingiard.media.SteamGridDbImage
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadEmptyState
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "SteamGridDbScrapeDlg"

private const val TYPE_GRIDS = "grids"
private const val TYPE_HEROES = "heroes"
private const val TYPE_LOGOS = "logos"
private const val TYPE_ICONS = "icons"

private const val GRID_ASPECT_RATIO_GRID = 0.66f
private const val GRID_ASPECT_RATIO_HERO = 1.77f
private const val GRID_ASPECT_RATIO_LOGO = 1.5f
private const val GRID_ASPECT_RATIO_DEFAULT = 1.0f

private val SG_THUMB_HEIGHT_GRID = 240.dp
private val SG_THUMB_HEIGHT_HERO = 180.dp
private val SG_THUMB_HEIGHT_LOGO = 160.dp
private val SG_THUMB_HEIGHT_ICON = 160.dp

private const val THUMB_CONNECT_TIMEOUT_MS = 5000
private const val THUMB_READ_TIMEOUT_MS = 8000
private const val SELECTION_BG_ALPHA = 0.25f

private val STATUS_BOX_HEIGHT = 120.dp
private val SG_BADGE_PADDING = 6.dp
private val SG_BADGE_SIZE = 22.dp
private val SG_BADGE_ICON_SIZE = 14.dp
private val SG_ROW_SPACING = 10.dp
private val SG_ROW_H_PADDING = 2.dp
private val SG_ROW_V_PADDING = 4.dp
private val SG_PROGRESS_SIZE = 24.dp
private val SG_PROGRESS_STROKE = 2.dp
private val SG_SPACING_12 = 12.dp
private val SG_SPACING_8 = 8.dp
private val SG_ICON_SIZE_36 = 36.dp

@Composable
internal fun SteamGridDbScrapeSubPageContent(
    initialSearchQuery: String,
    onImageSelected: (Uri) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var selectedType by remember { mutableStateOf(TYPE_GRIDS) }

    var isSearchingGames by remember { mutableStateOf(false) }
    var gamesList by remember { mutableStateOf<List<SteamGridDbGame>>(emptyList()) }
    var selectedGame by remember { mutableStateOf<SteamGridDbGame?>(null) }

    var isLoadingImages by remember { mutableStateOf(false) }
    var imagesList by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<SteamGridDbImage?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingErrorDialog by remember { mutableStateOf<Throwable?>(null) }

    var activeFetchJob by remember { mutableStateOf<Job?>(null) }

    val apiKey = SettingsManager.steamGridDbApiToken.value
    val imagesCache = remember { HashMap<Pair<Int, String>, List<SteamGridDbImage>>() }

    fun loadImagesForGame(
        gameId: Int,
        type: String,
    ) {
        selectedImage = null
        errorMessage = null
        val cacheKey = gameId to type
        val cached = imagesCache[cacheKey]
        if (cached != null) {
            AppLog.d(TAG, "loadImagesForGame: cache hit for gameId=$gameId type=$type (${cached.size} images)")
            imagesList = cached
            return
        }
        activeFetchJob?.cancel()
        isLoadingImages = true
        AppLog.i(TAG, "loadImagesForGame: fetching images for gameId=$gameId type=$type")
        activeFetchJob =
            scope.launch {
                SteamGridDbClient
                    .fetchImages(gameId, type, apiKey)
                    .onSuccess { images ->
                        AppLog.d(TAG, "loadImagesForGame success: loaded ${images.size} images")
                        imagesCache[cacheKey] = images
                        imagesList = images
                        isLoadingImages = false
                    }.onFailure { err ->
                        AppLog.w(TAG, "loadImagesForGame failure: ${err.message}")
                        errorMessage = err.message
                        isLoadingImages = false
                        pendingErrorDialog = err
                    }
            }
    }

    fun searchGames() {
        if (searchQuery.isBlank()) return
        AppLog.i(TAG, "Starting search for games matching: $searchQuery")
        activeFetchJob?.cancel()
        isSearchingGames = true
        selectedGame = null
        selectedImage = null
        imagesList = emptyList()
        errorMessage = null
        activeFetchJob =
            scope.launch {
                SteamGridDbClient
                    .searchGames(searchQuery, apiKey)
                    .onSuccess { games ->
                        AppLog.d(TAG, "searchGames success: found ${games.size} games")
                        gamesList = games
                        isSearchingGames = false
                        if (games.isNotEmpty()) {
                            val firstGame = games.first()
                            selectedGame = firstGame
                            val cacheKey = firstGame.id to selectedType
                            val cached = imagesCache[cacheKey]
                            if (cached != null) {
                                AppLog.d(TAG, "searchGames auto-fetch: cache hit for gameId=${firstGame.id} type=$selectedType")
                                imagesList = cached
                            } else {
                                isLoadingImages = true
                                AppLog.i(TAG, "searchGames auto-fetch: loading images for gameId=${firstGame.id} type=$selectedType")
                                SteamGridDbClient
                                    .fetchImages(firstGame.id, selectedType, apiKey)
                                    .onSuccess { images ->
                                        imagesCache[cacheKey] = images
                                        imagesList = images
                                        isLoadingImages = false
                                    }.onFailure { err ->
                                        AppLog.w(TAG, "searchGames auto-fetch failure: ${err.message}")
                                        errorMessage = err.message
                                        isLoadingImages = false
                                        pendingErrorDialog = err
                                    }
                            }
                        }
                    }.onFailure { err ->
                        AppLog.w(TAG, "searchGames failure: ${err.message}")
                        errorMessage = err.message
                        isSearchingGames = false
                        pendingErrorDialog = err
                    }
            }
    }

    LaunchedEffect(Unit) {
        if (initialSearchQuery.isNotBlank()) {
            searchGames()
        }
    }

    val typeOptions =
        remember {
            listOf(
                TYPE_GRIDS to R.string.steamgriddb_scrape_type_grid,
                TYPE_HEROES to R.string.steamgriddb_scrape_type_hero,
                TYPE_LOGOS to R.string.steamgriddb_scrape_type_logo,
                TYPE_ICONS to R.string.steamgriddb_scrape_type_icon,
            )
        }
    val currentTypeIndex = typeOptions.indexOfFirst { it.first == selectedType }.coerceAtLeast(0)

    val thumbHeight =
        when (selectedType) {
            TYPE_GRIDS -> SG_THUMB_HEIGHT_GRID
            TYPE_HEROES -> SG_THUMB_HEIGHT_HERO
            TYPE_LOGOS -> SG_THUMB_HEIGHT_LOGO
            else -> SG_THUMB_HEIGHT_ICON
        }

    val thumbAspectRatio =
        when (selectedType) {
            TYPE_GRIDS -> GRID_ASPECT_RATIO_GRID
            TYPE_HEROES -> GRID_ASPECT_RATIO_HERO
            TYPE_LOGOS -> GRID_ASPECT_RATIO_LOGO
            else -> GRID_ASPECT_RATIO_DEFAULT
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SG_SPACING_12),
    ) {
        // ── 1. Search Query Card ────────────────────────────────────────────────
        GamepadTextFieldCard(
            title = stringResource(R.string.steamgriddb_scrape_search_title),
            description = stringResource(R.string.steamgriddb_scrape_search_desc),
            placeholder = stringResource(R.string.steamgriddb_scrape_search_placeholder),
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                searchGames()
            },
            icon = Icons.Rounded.Search,
            modifier = Modifier.firstDeckItem(),
        )

        // ── 2. Category Selector Card ───────────────────────────────────────────
        GamepadChoiceCard(
            title = stringResource(R.string.steamgriddb_scrape_type_title),
            description = stringResource(R.string.steamgriddb_scrape_type_desc),
            selectedText = stringResource(typeOptions[currentTypeIndex].second),
            icon = Icons.Rounded.Layers,
            onPrevious = {
                val newIdx = (currentTypeIndex - 1 + typeOptions.size) % typeOptions.size
                selectedType = typeOptions[newIdx].first
                selectedGame?.let { loadImagesForGame(it.id, selectedType) }
            },
            onNext = {
                val newIdx = (currentTypeIndex + 1) % typeOptions.size
                selectedType = typeOptions[newIdx].first
                selectedGame?.let { loadImagesForGame(it.id, selectedType) }
            },
        )

        // ── 3. Matched Game Selector Card (if matches exist) ────────────────────
        if (gamesList.isNotEmpty()) {
            val gameIndex = gamesList.indexOfFirst { it.id == selectedGame?.id }.coerceAtLeast(0)
            val currentGame = gamesList[gameIndex]
            val gameLabel =
                if (gamesList.size > 1) {
                    "${gameIndex + 1}/${gamesList.size}: ${currentGame.name}"
                } else {
                    currentGame.name
                }

            GamepadChoiceCard(
                title = stringResource(R.string.steamgriddb_scrape_game_title),
                description = stringResource(R.string.steamgriddb_scrape_game_desc),
                selectedText = gameLabel,
                icon = Icons.Rounded.SportsEsports,
                onPrevious = {
                    val newIdx = (gameIndex - 1 + gamesList.size) % gamesList.size
                    selectedGame = gamesList[newIdx]
                    loadImagesForGame(gamesList[newIdx].id, selectedType)
                },
                onNext = {
                    val newIdx = (gameIndex + 1) % gamesList.size
                    selectedGame = gamesList[newIdx]
                    loadImagesForGame(gamesList[newIdx].id, selectedType)
                },
            )
        }

        // ── 4. Apply Artwork Action Card (when an image is selected) ────────────
        if (selectedImage != null) {
            GamepadActionCard(
                title = stringResource(R.string.steamgriddb_scrape_apply_title),
                description = stringResource(R.string.steamgriddb_scrape_apply_desc),
                actionText = stringResource(R.string.macropad_macro_editor_save),
                icon = Icons.Rounded.Download,
                enabled = !isDownloading,
                onClick = {
                    val currentImage = selectedImage ?: return@GamepadActionCard
                    isDownloading = true
                    AppLog.i(TAG, "Starting download for image URL: ${currentImage.url}")
                    scope.launch {
                        val cacheDir = context.cacheDir
                        SteamGridDbClient
                            .downloadImageToTempFile(currentImage.url, cacheDir)
                            .onSuccess { tempFile ->
                                AppLog.i(TAG, "Download successful, file saved to: ${tempFile.absolutePath}")
                                onImageSelected(Uri.fromFile(tempFile))
                                isDownloading = false
                            }.onFailure { err ->
                                AppLog.e(TAG, "Failed to download image: ${err.message}", err)
                                errorMessage = err.message
                                isDownloading = false
                                pendingErrorDialog = err
                            }
                    }
                },
            )
        }

        // ── 5. Section Header ───────────────────────────────────────────────────
        GamepadSectionHeader(
            text = stringResource(R.string.steamgriddb_scrape_images_section),
            color = accentColor,
        )

        // ── 6. Status states or horizontal artwork gallery ───────────────────────
        if (isDownloading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(STATUS_BOX_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentColor)
                    Spacer(Modifier.height(SG_SPACING_12))
                    Text(
                        text = stringResource(R.string.steamgriddb_scrape_downloading),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (isSearchingGames || isLoadingImages) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(STATUS_BOX_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentColor)
                    Spacer(Modifier.height(SG_SPACING_12))
                    Text(
                        text =
                            if (isSearchingGames) {
                                stringResource(R.string.steamgriddb_scrape_searching_games)
                            } else {
                                stringResource(R.string.steamgriddb_scrape_loading_images)
                            },
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(STATUS_BOX_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(SG_ICON_SIZE_36),
                    )
                    Spacer(Modifier.height(SG_SPACING_8))
                    Text(
                        text = stringResource(R.string.steamgriddb_scrape_error, errorMessage ?: ""),
                        color = colors.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (gamesList.isEmpty() && searchQuery.isNotBlank()) {
            GamepadEmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.steamgriddb_scrape_no_games_found),
                description = stringResource(R.string.steamgriddb_empty_games_desc),
            )
        } else if (imagesList.isEmpty() && selectedGame != null) {
            GamepadEmptyState(
                icon = Icons.Rounded.Image,
                title = stringResource(R.string.steamgriddb_scrape_no_images_found),
                description = stringResource(R.string.steamgriddb_empty_images_desc),
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = SG_ROW_H_PADDING, vertical = SG_ROW_V_PADDING),
                horizontalArrangement = Arrangement.spacedBy(SG_ROW_SPACING),
            ) {
                items(imagesList, key = { it.id }) { image ->
                    val isSelected = selectedImage?.id == image.id

                    GamepadFocusCard(
                        onClick = {
                            selectedImage = if (isSelected) null else image
                        },
                        modifier =
                            Modifier
                                .height(thumbHeight)
                                .aspectRatio(thumbAspectRatio),
                        cardBgColor = if (isSelected) accentColor.copy(alpha = SELECTION_BG_ALPHA) else colors.surface,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            SteamGridDbImageThumbnail(
                                url = image.thumb.ifBlank { image.url },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isSelected) {
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(SG_BADGE_PADDING)
                                            .size(SG_BADGE_SIZE)
                                            .background(accentColor, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = colors.onAccent,
                                        modifier = Modifier.size(SG_BADGE_ICON_SIZE),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val currentError = pendingErrorDialog
    if (currentError != null) {
        val (titleRes, messageRes) =
            when (currentError) {
                is SteamGridDbException.Offline -> {
                    R.string.steamgriddb_error_offline_title to R.string.steamgriddb_error_offline_message
                }

                is SteamGridDbException.RateLimited -> {
                    R.string.steamgriddb_error_rate_limited_title to R.string.steamgriddb_error_rate_limited_message
                }

                is SteamGridDbException.ServiceUnavailable -> {
                    R.string.steamgriddb_error_unreachable_title to R.string.steamgriddb_error_unreachable_message
                }

                else -> {
                    R.string.steamgriddb_error_generic_title to R.string.steamgriddb_error_generic_message
                }
            }
        AppAlertDialog(
            onDismissRequest = {
                AppLog.d(TAG, "Dismissing error dialog")
                pendingErrorDialog = null
                errorMessage = null
            },
            title = {
                Text(
                    text = stringResource(titleRes),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(messageRes),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppLog.d(TAG, "Confirm dismissing error dialog")
                    pendingErrorDialog = null
                    errorMessage = null
                }) {
                    Text(
                        text = stringResource(R.string.steamgriddb_error_dismiss),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
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
        val decoded =
            withContext(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = THUMB_CONNECT_TIMEOUT_MS
                    connection.readTimeout = THUMB_READ_TIMEOUT_MS
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to load thumb $url", e)
                    null
                }
            }
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
                modifier = Modifier.size(SG_PROGRESS_SIZE),
                color = colors.accent,
                strokeWidth = SG_PROGRESS_STROKE,
            )
        } else if (isError || bitmap == null) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = stringResource(R.string.steamgriddb_cd_error_thumbnail),
                tint = colors.error,
            )
        } else {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
