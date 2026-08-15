package com.stormpanda.megingiard.steamgriddb
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.media.SteamGridDbClient
import com.stormpanda.megingiard.media.SteamGridDbException
import com.stormpanda.megingiard.media.SteamGridDbGame
import com.stormpanda.megingiard.media.SteamGridDbImage
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.LocalAppColors
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
private const val GRID_ASPECT_RATIO_DEFAULT = 1.0f

private const val THUMB_CONNECT_TIMEOUT_MS = 5000
private const val THUMB_READ_TIMEOUT_MS = 8000

private const val BACK_TO_TOP_THRESHOLD = 3
private val STATUS_BOX_HEIGHT = 300.dp
private val SELECTION_ALPHA = 0.15f
private val DISABLE_ALPHA = 0.5f

@Composable
internal fun SteamGridDbScrapeDialog(
    initialSearchQuery: String,
    onImageSelected: (Uri) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        AppLog.i(TAG, "SteamGridDbScrapeDialog initialized with query: $initialSearchQuery")
    }

    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var selectedType by remember { mutableStateOf(TYPE_GRIDS) } // grids, heroes, logos, icons

    var isSearchingGames by remember { mutableStateOf(false) }
    var gamesList by remember { mutableStateOf<List<SteamGridDbGame>>(emptyList()) }
    var selectedGame by remember { mutableStateOf<SteamGridDbGame?>(null) }

    var isLoadingImages by remember { mutableStateOf(false) }
    var imagesList by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<SteamGridDbImage?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingErrorDialog by remember { mutableStateOf<Throwable?>(null) }

    // Track active loading job to cancel and prevent race conditions
    var activeFetchJob by remember { mutableStateOf<Job?>(null) }

    val apiKey = SettingsManager.steamGridDbApiToken.value
    val gridState = rememberLazyGridState()
    // Cache keyed on (gameId, type) to avoid redundant authenticated API calls within a session
    val imagesCache = remember { HashMap<Pair<Int, String>, List<SteamGridDbImage>>() }

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
                            // Automatically select the first match and fetch its images
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

    // Trigger initial search if layout name is prefilled
    LaunchedEffect(Unit) {
        if (initialSearchQuery.isNotBlank()) {
            searchGames()
        }
    }

    BackHandler(onBack = {
        AppLog.i(TAG, "Dialog dismissed via BackHandler")
        onDismiss()
    })

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.appBackground),
    ) {
        FullScreenTopBar(
            title = stringResource(R.string.steamgriddb_scrape_dialog_title),
            onDismiss = {
                AppLog.i(TAG, "Dialog dismissed via TopBar dismiss button")
                onDismiss()
            },
        ) {
            TextButton(
                onClick = {
                    val currentSelectedImage = selectedImage
                    if (currentSelectedImage != null) {
                        isDownloading = true
                        AppLog.i(TAG, "Starting download for image URL: ${currentSelectedImage.url}")
                        scope.launch {
                            val cacheDir = context.cacheDir
                            SteamGridDbClient
                                .downloadImageToTempFile(currentSelectedImage.url, cacheDir)
                                .onSuccess { tempFile ->
                                    AppLog.i(TAG, "Download successful, file saved to: ${tempFile.absolutePath}")
                                    onImageSelected(Uri.fromFile(tempFile))
                                    isDownloading = false
                                    onDismiss()
                                }.onFailure { err ->
                                    AppLog.e(TAG, "Failed to download image: ${err.message}", err)
                                    errorMessage = err.message
                                    isDownloading = false
                                    pendingErrorDialog = err
                                }
                        }
                    }
                },
                enabled = selectedImage != null && !isDownloading,
            ) {
                Text(
                    text = stringResource(R.string.macropad_macro_editor_save),
                    color =
                        if (selectedImage != null &&
                            !isDownloading
                        ) {
                            colors.accent
                        } else {
                            colors.onSurfaceSecondary.copy(alpha = DISABLE_ALPHA)
                        },
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Search row ──────────────────────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.steamgriddb_scrape_search_placeholder),
                                    color = colors.onSurfaceSecondary,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = colors.onSurfaceSecondary,
                                )
                            },
                            singleLine = true,
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { searchGames() },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            enabled = searchQuery.isNotBlank() && !isSearchingGames,
                        ) {
                            Text(text = stringResource(R.string.steamgriddb_scrape_search_btn), color = colors.onAccent)
                        }
                    }
                }

                // ── Type selector chips ─────────────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf(TYPE_GRIDS, TYPE_HEROES, TYPE_LOGOS, TYPE_ICONS).forEach { type ->
                            val isSelected = selectedType == type
                            val label =
                                when (type) {
                                    TYPE_GRIDS -> stringResource(R.string.steamgriddb_scrape_type_grid)
                                    TYPE_HEROES -> stringResource(R.string.steamgriddb_scrape_type_hero)
                                    TYPE_LOGOS -> stringResource(R.string.steamgriddb_scrape_type_logo)
                                    else -> stringResource(R.string.steamgriddb_scrape_type_icon)
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) accentColor else colors.surface)
                                        .border(
                                            1.dp,
                                            if (isSelected) accentColor else colors.onSurface.copy(alpha = 0.15f),
                                            RoundedCornerShape(8.dp),
                                        ).clickable {
                                            selectedType = type
                                            if (selectedGame != null) {
                                                loadImagesForGame(selectedGame!!.id, type)
                                            }
                                        }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.onAccent else colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                // ── Game search results ─────────────────────────────────────────────────
                if (isSearchingGames || gamesList.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (isSearchingGames) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = accentColor,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.steamgriddb_scrape_searching_games),
                                        color = colors.onSurfaceSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.steamgriddb_scrape_select_game),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp),
                                ) {
                                    items(gamesList) { game ->
                                        val isSelectedGame = selectedGame?.id == game.id
                                        Box(
                                            modifier =
                                                Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelectedGame) {
                                                            accentColor.copy(alpha = SELECTION_ALPHA)
                                                        } else {
                                                            colors.surface
                                                        },
                                                    ).border(
                                                        1.dp,
                                                        if (isSelectedGame) accentColor else colors.onSurface.copy(alpha = 0.15f),
                                                        RoundedCornerShape(8.dp),
                                                    ).clickable {
                                                        selectedGame = game
                                                        loadImagesForGame(game.id, selectedType)
                                                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        ) {
                                            Text(
                                                text = game.name,
                                                color = if (isSelectedGame) colors.onSurface else colors.onSurfaceSecondary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelectedGame) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Divider ─────────────────────────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        AppDivider()
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // ── Status states (full-width) or lazy image grid ───────────────────────
                if (isDownloading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(STATUS_BOX_HEIGHT),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = accentColor)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.steamgriddb_scrape_downloading),
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                } else if (isLoadingImages) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(STATUS_BOX_HEIGHT),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = accentColor)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.steamgriddb_scrape_loading_images),
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                } else if (errorMessage != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.steamgriddb_scrape_error, errorMessage ?: ""),
                                    color = colors.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                } else if (gamesList.isEmpty() && searchQuery.isNotBlank() && !isSearchingGames) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(STATUS_BOX_HEIGHT),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.steamgriddb_scrape_no_games_found),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else if (imagesList.isEmpty() && selectedGame != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(STATUS_BOX_HEIGHT),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.steamgriddb_scrape_no_images_found),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    // One item per image — LazyVerticalGrid handles 2-column layout and
                    // only composes/downloads thumbnails that are currently visible.
                    items(imagesList) { image ->
                        val isSelected = selectedImage?.id == image.id
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(
                                        when (selectedType) {
                                            TYPE_GRIDS -> GRID_ASPECT_RATIO_GRID
                                            TYPE_HEROES -> GRID_ASPECT_RATIO_HERO
                                            else -> GRID_ASPECT_RATIO_DEFAULT
                                        },
                                    ).clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) accentColor else colors.onSurface.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                    ).clickable {
                                        selectedImage = if (isSelected) null else image
                                    },
                        ) {
                            SteamGridDbImageThumbnail(
                                url = image.thumb.ifBlank { image.url },
                                contentDescription = null, // decorative image, screen readers can ignore
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // Bottom padding so FAB doesn't obscure the last row
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(72.dp))
                }
            }

            val showBackToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > BACK_TO_TOP_THRESHOLD } }
            if (showBackToTop) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                gridState.animateScrollToItem(0)
                            }
                        },
                        containerColor = accentColor,
                        contentColor = colors.onAccent,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Back to Top",
                        )
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
                        errorMessage = null // Reset error text state on dialog dismissal
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
                            errorMessage = null // Reset error text state on dialog dismissal
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
                modifier = Modifier.size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
        } else if (isError || bitmap == null) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Error loading thumbnail",
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
