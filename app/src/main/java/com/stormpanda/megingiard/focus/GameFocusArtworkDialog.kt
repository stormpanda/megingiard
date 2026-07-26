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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
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
import com.stormpanda.megingiard.steamgriddb.SteamGridDbClient
import com.stormpanda.megingiard.steamgriddb.SteamGridDbImage
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "GameFocusArtworkDialog"
private val ARTWORK_POSTER_WIDTH = 120.dp
private val ARTWORK_POSTER_HEIGHT = 180.dp

@Composable
fun GameFocusArtworkDialog(
    appInfo: InstalledAppInfo,
    apiKey: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var images by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectingImage by remember { mutableStateOf<SteamGridDbImage?>(null) }

    LaunchedEffect(appInfo.packageName) {
        AppLog.i(TAG, "Fetching SteamGridDB artwork options for ${appInfo.label}")
        isLoading = true
        errorMessage = null

        scope.launch(Dispatchers.IO) {
            val searchRes = SteamGridDbClient.searchGames(appInfo.label, apiKey)
            val games = searchRes.getOrNull()
            val gameId = games?.firstOrNull()?.id

            if (gameId == null) {
                withContext(Dispatchers.Main) {
                    errorMessage = "No SteamGridDB game found for '${appInfo.label}'"
                    isLoading = false
                }
                return@launch
            }

            val imgRes = SteamGridDbClient.fetchImages(gameId, "grids", apiKey)
            val fetchedImages = imgRes.getOrNull() ?: emptyList()

            withContext(Dispatchers.Main) {
                images = fetchedImages
                if (fetchedImages.isEmpty()) {
                    errorMessage = "No cover artwork found for '${appInfo.label}'"
                }
                isLoading = false
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
            Text(
                text = stringResource(R.string.focus_change_artwork_title),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = appColors.onSurface,
                    ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = appInfo.label,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = appColors.onSurfaceSecondary,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading || selectingImage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = appColors.accent,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (selectingImage != null) "Downloading artwork..." else "Searching SteamGridDB...",
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
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(images, key = { it.id }) { imageItem ->
                            ArtworkOptionItem(
                                imageItem = imageItem,
                                onClick = {
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
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
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
private fun ArtworkOptionItem(
    imageItem: SteamGridDbImage,
    onClick: () -> Unit,
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
        modifier =
            modifier
                .size(ARTWORK_POSTER_WIDTH, ARTWORK_POSTER_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(appColors.surfaceVariant)
                .border(1.5.dp, appColors.divider, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
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
