package com.stormpanda.megingiard.focus

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.steamgriddb.SteamGridDbClient
import com.stormpanda.megingiard.steamgriddb.SteamGridDbGame
import com.stormpanda.megingiard.steamgriddb.SteamGridDbImage
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.ExpandableOptionItem
import com.stormpanda.megingiard.ui.ExpandableOptionsMenu
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "GameFocusArtworkDialog"

@Composable
fun GameFocusArtworkDialog(
    appInfo: InstalledAppInfo,
    apiKey: String,
    virtualIndex: Int = 10_000,
    onVirtualIndexChange: (Int) -> Unit = {},
    confirmTrigger: Int = 0,
    l1Trigger: Int = 0,
    r1Trigger: Int = 0,
    isOptionsMenuExpanded: Boolean = false,
    onOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    dpadUpTrigger: Int = 0,
    dpadRightTrigger: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember(appInfo.packageName) { mutableStateOf(appInfo.label) }
    var isEditingQuery by remember { mutableStateOf(false) }
    var searchInputText by remember(searchQuery) { mutableStateOf(searchQuery) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var games by remember { mutableStateOf<List<SteamGridDbGame>>(emptyList()) }
    var selectedGameIndex by remember { mutableIntStateOf(0) }
    var isSearchLoading by remember { mutableStateOf(true) }
    var isImagesLoading by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectingImage by remember { mutableStateOf<SteamGridDbImage?>(null) }

    val useAppIcon =
        remember(appInfo.packageName) {
            {
                val coversDir = File(context.cacheDir, "gamefocus_covers")
                val targetFile = File(coversDir, "${appInfo.packageName}.png")
                if (targetFile.exists()) targetFile.delete()
                InstalledAppsManager.updateAppCover(appInfo.packageName, null)
                InstalledAppsManager.markAppAsScraped(context, appInfo.packageName)
                AppLog.i(TAG, "Reverted to app icon for ${appInfo.packageName}")
                onDismiss()
            }
        }

    // React to Dpad Up / Dpad Right option shortcuts
    LaunchedEffect(dpadUpTrigger) {
        if (dpadUpTrigger > 0) {
            isEditingQuery = true
        }
    }

    LaunchedEffect(dpadRightTrigger) {
        if (dpadRightTrigger > 0) {
            useAppIcon()
        }
    }

    // Request focus and open soft keyboard automatically when editing search query
    LaunchedEffect(isEditingQuery) {
        if (isEditingQuery) {
            focusRequester.requestFocus()
            delay(100)
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    // Search for games matching current searchQuery
    LaunchedEffect(searchQuery) {
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
                    errorMessage = "No SteamGridDB game found for '$searchQuery'"
                }
            }
        }
    }

    // Handle L1 and R1 triggers to switch selected game
    LaunchedEffect(l1Trigger) {
        if (l1Trigger > 0 && games.isNotEmpty()) {
            selectedGameIndex = Math.floorMod(selectedGameIndex - 1, games.size)
        }
    }

    LaunchedEffect(r1Trigger) {
        if (r1Trigger > 0 && games.isNotEmpty()) {
            selectedGameIndex = Math.floorMod(selectedGameIndex + 1, games.size)
        }
    }

    // Fetch images whenever selectedGameIndex or games list changes
    LaunchedEffect(selectedGameIndex, games) {
        val currentGame = games.getOrNull(selectedGameIndex)
        if (currentGame != null) {
            AppLog.i(TAG, "Fetching SteamGridDB artwork options for game '${currentGame.name}' (id=${currentGame.id})")
            isImagesLoading = true
            errorMessage = null
            images = emptyList()
            onVirtualIndexChange(10_000)

            scope.launch(Dispatchers.IO) {
                val imgRes = SteamGridDbClient.fetchImages(currentGame.id, "grids", apiKey)
                val fetchedImages = imgRes.getOrNull() ?: emptyList()

                withContext(Dispatchers.Main) {
                    images = fetchedImages
                    if (fetchedImages.isEmpty()) {
                        errorMessage = "No cover artwork found for '${currentGame.name}'"
                    }
                    isImagesLoading = false
                }
            }
        }
    }

    val onConfirmSelection: (SteamGridDbImage) -> Unit =
        remember(appInfo.packageName) {
            { imageItem ->
                if (selectingImage == null) {
                    selectingImage = imageItem
                    scope.launch(Dispatchers.IO) {
                        try {
                            val tempRes = SteamGridDbClient.downloadImageToTempFile(imageItem.url, context.cacheDir)
                            val tempFile = tempRes.getOrNull()
                            if (tempFile != null) {
                                val coversDir = File(context.cacheDir, "gamefocus_covers").apply { mkdirs() }
                                val targetFile = File(coversDir, "${appInfo.packageName}.png")
                                tempFile.copyTo(targetFile, overwrite = true)
                                tempFile.delete()

                                InstalledAppsManager.updateAppCover(appInfo.packageName, targetFile.absolutePath)
                                InstalledAppsManager.markAppAsScraped(context, appInfo.packageName)
                                AppLog.i(TAG, "Selected and updated artwork for ${appInfo.packageName}")
                            }
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Failed to download selected artwork: ${e.message}", e)
                        } finally {
                            withContext(Dispatchers.Main) {
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }

    LaunchedEffect(confirmTrigger) {
        if (confirmTrigger > 0 && images.isNotEmpty()) {
            val selectedIndex = Math.floorMod(virtualIndex, images.size)
            images.getOrNull(selectedIndex)?.let { imageItem ->
                onConfirmSelection(imageItem)
            }
        }
    }

    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = 0.88f,
        contentPadding = 20.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Main Dialog Headline
            Text(
                text = stringResource(R.string.focus_change_artwork_title),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.onSurface,
                    ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Conditional Edit Search Term Input Field
            if (isEditingQuery) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = searchInputText,
                        onValueChange = { searchInputText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = appColors.onSurface),
                        placeholder = {
                            Text(
                                text = "Enter search term...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = appColors.onSurfaceSecondary),
                            )
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = appColors.accent,
                                unfocusedBorderColor = appColors.divider,
                                cursorColor = appColors.accent,
                            ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions =
                            KeyboardActions(
                                onSearch = {
                                    if (searchInputText.isNotBlank()) {
                                        searchQuery = searchInputText.trim()
                                        isEditingQuery = false
                                    }
                                },
                            ),
                    )

                    IconButton(
                        onClick = {
                            if (searchInputText.isNotBlank()) {
                                searchQuery = searchInputText.trim()
                                isEditingQuery = false
                            }
                        },
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(appColors.accent),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Search",
                            tint = appColors.onAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = { isEditingQuery = false },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel edit",
                            tint = appColors.onSurfaceSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Selectable Games Row (Touch or L1/R1 navigable with auto-scroll)
            if (games.isNotEmpty()) {
                GameSelectionRow(
                    games = games,
                    selectedIndex = selectedGameIndex,
                    onGameSelect = { selectedGameIndex = it },
                )
            } else {
                Text(
                    text = searchQuery,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = appColors.onSurfaceSecondary,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Poster Carousel Container
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSearchLoading || isImagesLoading || selectingImage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = appColors.accent,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val loadingStatusText =
                            when {
                                selectingImage != null -> "Downloading artwork..."
                                isSearchLoading -> "Searching SteamGridDB..."
                                else -> "Fetching game covers..."
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    HorizontalPosterCarousel(
                        itemCount = images.size,
                        virtualIndex = virtualIndex,
                        onVirtualIndexChange = onVirtualIndexChange,
                        onItemClick = { actualIndex ->
                            images.getOrNull(actualIndex)?.let { imageItem ->
                                onConfirmSelection(imageItem)
                            }
                        },
                        posterWidth = 120.dp,
                        posterHeight = 180.dp,
                        posterSpacing = 12.dp,
                        carouselHeight = 220.dp,
                        posterCornerRadius = 12.dp,
                    ) { actualIndex, _ ->
                        images.getOrNull(actualIndex)?.let { imageItem ->
                            ArtworkOptionItem(imageItem = imageItem)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Navigation Row with Lower Left Reusable Expandable Options Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpandableOptionsMenu(
                    isExpanded = isOptionsMenuExpanded,
                    onExpandedChange = onOptionsMenuExpandedChange,
                    options =
                        listOf(
                            ExpandableOptionItem(
                                label = "Close",
                                iconSymbol = "menu",
                                onClick = { onOptionsMenuExpandedChange(false) },
                            ),
                            ExpandableOptionItem(
                                label = "Change Search Term",
                                iconSymbol = "gamepad_up",
                                onClick = { isEditingQuery = true },
                            ),
                            ExpandableOptionItem(
                                label = "Use App Icon",
                                iconSymbol = "gamepad_right",
                                onClick = { useAppIcon() },
                            ),
                        ),
                )

                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.settings_cancel),
                        color = appColors.onSurfaceSecondary,
                    )
                }
            }
        }
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

    // Smoothly scroll LazyRow to keep the L1/R1 selected item visible on screen
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
        // L1 Badge Indicator
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.divider, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "L1",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.accent,
                    ),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Scrollable Row of Game Chips with Auto-Scroll State
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                val isSelected = index == selectedIndex
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) appColors.accent else appColors.surfaceVariant)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) appColors.accent else appColors.divider,
                                shape = RoundedCornerShape(20.dp),
                            ).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onGameSelect(index)
                            }.padding(horizontal = 14.dp, vertical = 6.dp),
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

        Spacer(modifier = Modifier.width(8.dp))

        // R1 Badge Indicator
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.divider, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R1",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.accent,
                    ),
            )
        }
    }
}

@Composable
private fun ArtworkOptionItem(
    imageItem: SteamGridDbImage,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isThumbLoading by remember { mutableStateOf(true) }

    LaunchedEffect(imageItem.thumb) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(imageItem.thumb)
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 8000
                val stream = connection.getInputStream()
                val decoded = BitmapFactory.decodeStream(stream)
                stream.close()
                withContext(Dispatchers.Main) {
                    bitmap = decoded?.asImageBitmap()
                    isThumbLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isThumbLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "SteamGridDB Artwork Option",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (isThumbLoading) {
            CircularProgressIndicator(
                color = appColors.accent,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Text(
                text = "Preview Unavailable",
                style = MaterialTheme.typography.labelSmall.copy(color = appColors.onSurfaceSecondary),
                textAlign = TextAlign.Center,
            )
        }
    }
}
