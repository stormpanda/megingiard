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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
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
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppTextField
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "SteamGridDbScrapeDialog"

@Composable
internal fun SteamGridDbScrapeDialog(
    initialSearchQuery: String,
    onImageSelected: (Uri) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var selectedType by remember { mutableStateOf("grids") } // grids, heroes, logos, icons

    var isSearchingGames by remember { mutableStateOf(false) }
    var gamesList by remember { mutableStateOf<List<SteamGridDbGame>>(emptyList()) }
    var selectedGame by remember { mutableStateOf<SteamGridDbGame?>(null) }

    var isLoadingImages by remember { mutableStateOf(false) }
    var imagesList by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<SteamGridDbImage?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val apiKey = SettingsManager.steamGridDbApiToken.value
    val scrollState = rememberScrollState()

    fun searchGames() {
        if (searchQuery.isBlank()) return
        isSearchingGames = true
        selectedGame = null
        selectedImage = null
        imagesList = emptyList()
        errorMessage = null
        scope.launch {
            SteamGridDbClient.searchGames(searchQuery, apiKey)
                .onSuccess { games ->
                    gamesList = games
                    isSearchingGames = false
                    if (games.isNotEmpty()) {
                        // Automatically select the first match and fetch its images
                        val firstGame = games.first()
                        selectedGame = firstGame
                        isLoadingImages = true
                        SteamGridDbClient.fetchImages(firstGame.id, selectedType, apiKey)
                            .onSuccess { images ->
                                imagesList = images
                                isLoadingImages = false
                            }
                            .onFailure { err ->
                                errorMessage = err.message
                                isLoadingImages = false
                            }
                    }
                }
                .onFailure { err ->
                    errorMessage = err.message
                    isSearchingGames = false
                }
        }
    }

    fun loadImagesForGame(gameId: Int, type: String) {
        isLoadingImages = true
        selectedImage = null
        errorMessage = null
        scope.launch {
            SteamGridDbClient.fetchImages(gameId, type, apiKey)
                .onSuccess { images ->
                    imagesList = images
                    isLoadingImages = false
                }
                .onFailure { err ->
                    errorMessage = err.message
                    isLoadingImages = false
                }
        }
    }

    // Trigger initial search if layout name is prefilled
    LaunchedEffect(Unit) {
        if (initialSearchQuery.isNotBlank()) {
            searchGames()
        }
    }

    BackHandler(onBack = onDismiss)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        FullScreenTopBar(
            title = stringResource(R.string.steamgriddb_scrape_dialog_title),
            onDismiss = onDismiss,
        ) {
            TextButton(
                onClick = {
                    val currentSelectedImage = selectedImage
                    if (currentSelectedImage != null) {
                        isDownloading = true
                        scope.launch {
                            val cacheDir = context.cacheDir
                            SteamGridDbClient.downloadImageToTempFile(currentSelectedImage.url, cacheDir)
                                .onSuccess { tempFile ->
                                    onImageSelected(Uri.fromFile(tempFile))
                                    isDownloading = false
                                    onDismiss()
                                }
                                .onFailure { err ->
                                    errorMessage = err.message
                                    isDownloading = false
                                }
                        }
                    }
                },
                enabled = selectedImage != null && !isDownloading
            ) {
                Text(
                    text = stringResource(R.string.macropad_macro_editor_save),
                    color = if (selectedImage != null && !isDownloading) colors.accent else colors.onSurfaceSecondary.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Search Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.steamgriddb_scrape_search_placeholder),
                                color = colors.onSurfaceSecondary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.onSurfaceSecondary
                            )
                        },
                        singleLine = true
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { searchGames() },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        enabled = searchQuery.isNotBlank() && !isSearchingGames
                    ) {
                        Text(text = stringResource(R.string.steamgriddb_scrape_search_btn), color = colors.onAccent)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Type selector chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("grids", "heroes", "logos", "icons").forEach { type ->
                        val isSelected = selectedType == type
                        val label = when (type) {
                            "grids" -> stringResource(R.string.steamgriddb_scrape_type_grid)
                            "heroes" -> stringResource(R.string.steamgriddb_scrape_type_hero)
                            "logos" -> stringResource(R.string.steamgriddb_scrape_type_logo)
                            else -> stringResource(R.string.steamgriddb_scrape_type_icon)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accentColor else colors.surface)
                                .border(1.dp, if (isSelected) accentColor else colors.accentBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedType = type
                                    if (selectedGame != null) {
                                        loadImagesForGame(selectedGame!!.id, type)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) colors.onAccent else colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Game results list
                if (isSearchingGames) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = accentColor, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.steamgriddb_scrape_searching_games),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (gamesList.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.steamgriddb_scrape_select_game),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(gamesList) { game ->
                                val isSelectedGame = selectedGame?.id == game.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelectedGame) accentColor.copy(alpha = 0.15f) else colors.surface)
                                        .border(
                                            1.dp,
                                            if (isSelectedGame) accentColor else colors.accentBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedGame = game
                                            loadImagesForGame(game.id, selectedType)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = game.name,
                                        color = if (isSelectedGame) colors.onSurface else colors.onSurfaceSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelectedGame) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                AppDivider()
                Spacer(Modifier.height(12.dp))

                // Main Content Area: images or states
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (isDownloading || isLoadingImages || errorMessage != null ||
                                (gamesList.isEmpty() && searchQuery.isNotBlank() && !isSearchingGames) ||
                                (imagesList.isEmpty() && selectedGame != null)) {
                                it.height(300.dp)
                            } else {
                                it
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accentColor)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.steamgriddb_scrape_downloading),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (isLoadingImages) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accentColor)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.steamgriddb_scrape_loading_images),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (errorMessage != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.steamgriddb_scrape_error, errorMessage ?: ""),
                                color = colors.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (gamesList.isEmpty() && searchQuery.isNotBlank() && !isSearchingGames) {
                        Text(
                            text = stringResource(R.string.steamgriddb_scrape_no_games_found),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (imagesList.isEmpty() && selectedGame != null) {
                        Text(
                            text = stringResource(R.string.steamgriddb_scrape_no_images_found),
                            color = colors.onSurfaceSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        val chunkedImages = remember(imagesList) { imagesList.chunked(2) }
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            chunkedImages.forEach { rowImages ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowImages.forEach { image ->
                                        val isSelected = selectedImage?.id == image.id
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(
                                                    when (selectedType) {
                                                        "grids" -> 0.66f
                                                        "heroes" -> 1.77f
                                                        "logos" -> 1f
                                                        else -> 1f
                                                    }
                                                )
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colors.surface)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) accentColor else colors.accentBorder,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    selectedImage = if (isSelected) null else image
                                                }
                                        ) {
                                            SteamGridDbImageThumbnail(
                                                url = image.thumb.ifBlank { image.url },
                                                contentDescription = "SteamGridDB Image",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    if (rowImages.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val showBackToTop by remember { derivedStateOf { scrollState.value > 400 } }
            if (showBackToTop) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        },
                        containerColor = accentColor,
                        contentColor = colors.onAccent,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Back to Top"
                        )
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
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var isError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        isLoading = true
        isError = false
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 8000
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        val decoded = BitmapFactory.decodeStream(input)
                        bitmap = decoded?.asImageBitmap()
                    }
                } else {
                    isError = true
                }
            } catch (e: Exception) {
                AppLog.e("ThumbLoader", "Failed to load thumb $url", e)
                isError = true
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier.background(colors.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp
            )
        } else if (isError || bitmap == null) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Error loading thumbnail",
                tint = colors.error
            )
        } else {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
