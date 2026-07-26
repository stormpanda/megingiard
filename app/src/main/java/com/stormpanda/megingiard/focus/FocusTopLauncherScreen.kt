package com.stormpanda.megingiard.focus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.LocalAppColors
import java.io.File

private const val TAG = "FocusTopLauncherScreen"

private val FTL_POSTER_CORNER_RADIUS = 16.dp
private val FTL_POSTER_WIDTH = 175.dp
private val FTL_POSTER_HEIGHT = 262.dp // 2:3 aspect ratio (~30% larger)
private val FTL_POSTER_SPACING = 12.dp
private val FTL_ICON_SIZE = 80.dp

@Composable
fun FocusTopLauncherScreen(
    apps: List<InstalledAppInfo>,
    virtualIndex: Int,
    onVirtualIndexChange: (Int) -> Unit,
    onAppClick: (InstalledAppInfo) -> Unit,
    editingAppInfo: InstalledAppInfo? = null,
    dialogVirtualIndex: Int = 10_000,
    onDialogVirtualIndexChange: (Int) -> Unit = {},
    confirmDialogTrigger: Int = 0,
    l1Trigger: Int = 0,
    r1Trigger: Int = 0,
    onDismissEditingApp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val apiKey by SettingsManager.steamGridDbApiToken.collectAsState()

    var showApiTokenMissingDialog by remember { mutableStateOf(false) }

    if (apps.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = appColors.appBackground,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.focus_launcher_no_apps),
                    style = MaterialTheme.typography.titleMedium.copy(color = appColors.onSurfaceSecondary),
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = appColors.appBackground,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    appColors.appBackground,
                                    appColors.accent.copy(alpha = 0.08f),
                                    appColors.appBackground,
                                ),
                        ),
                    ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Endless centered 2:3 poster carousel using reusable HorizontalPosterCarousel
                HorizontalPosterCarousel(
                    itemCount = apps.size,
                    virtualIndex = virtualIndex,
                    onVirtualIndexChange = onVirtualIndexChange,
                    onItemClick = { actualIndex ->
                        val appInfo = apps[actualIndex]
                        onAppClick(appInfo)
                    },
                    posterWidth = FTL_POSTER_WIDTH,
                    posterHeight = FTL_POSTER_HEIGHT,
                    posterSpacing = FTL_POSTER_SPACING,
                    carouselHeight = 310.dp,
                    posterCornerRadius = FTL_POSTER_CORNER_RADIUS,
                ) { actualIndex, _ ->
                    val appInfo = apps[actualIndex]
                    PosterCardContent(appInfo = appInfo)
                }

                // Focused App Title at the bottom of the screen
                val currentActualIndex = Math.floorMod(virtualIndex, apps.size)
                val currentApp = apps.getOrNull(currentActualIndex)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
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
                    l1Trigger = l1Trigger,
                    r1Trigger = r1Trigger,
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

@Composable
private fun PosterCardContent(
    appInfo: InstalledAppInfo,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    val coverBitmap =
        remember(appInfo.coverPath, appInfo.coverPath?.let { File(it).lastModified() }) {
            appInfo.coverPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    try {
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        }

    val iconBitmap =
        remember(appInfo.icon) {
            if (coverBitmap == null) appInfo.icon?.toBitmapSafe() else null
        }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = appInfo.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = appInfo.label,
                    tint = appColors.accent,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

private fun Drawable.toBitmapSafe(): ImageBitmap? =
    try {
        val w = intrinsicWidth.coerceAtLeast(1)
        val h = intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
