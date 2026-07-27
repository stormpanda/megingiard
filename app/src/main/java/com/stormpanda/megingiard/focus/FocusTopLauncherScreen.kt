package com.stormpanda.megingiard.focus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppAlertDialog
import com.stormpanda.megingiard.ui.CutoutLetterCircleIcon
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
    onAppClickTop: (InstalledAppInfo) -> Unit = {},
    onAppClickBottom: (InstalledAppInfo) -> Unit = {},
    onAppClick: (InstalledAppInfo) -> Unit = onAppClickTop,
    editingAppInfo: InstalledAppInfo? = null,
    dialogVirtualIndex: Int = 10_000,
    onDialogVirtualIndexChange: (Int) -> Unit = {},
    confirmDialogTrigger: Int = 0,
    l1Trigger: Int = 0,
    r1Trigger: Int = 0,
    isOptionsMenuExpanded: Boolean = false,
    onOptionsMenuExpandedChange: (Boolean) -> Unit = {},
    dpadUpTrigger: Int = 0,
    dpadRightTrigger: Int = 0,
    onDismissEditingApp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val apiKey by SettingsManager.steamGridDbApiToken.collectAsState()

    LaunchedEffect(Unit) {
        AppPaletteExtractor.init(context)
    }

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

    val currentActualIndex = if (apps.isNotEmpty()) Math.floorMod(virtualIndex, apps.size) else 0
    val currentApp = apps.getOrNull(currentActualIndex)

    LaunchedEffect(currentActualIndex) {
        currentApp?.let { app ->
            AppLog.d(TAG, "Focused game changed to index=$currentActualIndex, package=${app.packageName}, label=${app.label}")
        }
    }

    // Extract dynamic 2 main colors asynchronously off the UI thread using AndroidX Palette API
    val defaultPalette =
        remember(appColors.accent, appColors.appBackground) {
            ExtractedAppPalette(appColors.accent, appColors.appBackground)
        }
    val extractedPalette by produceState(
        initialValue = defaultPalette,
        key1 = currentApp?.packageName,
        key2 = currentApp?.coverPath,
    ) {
        value =
            if (currentApp != null) {
                AppPaletteExtractor.extractColorsAsync(currentApp, appColors.accent, appColors.appBackground)
            } else {
                defaultPalette
            }
    }

    // Smoothly animate background gradient and ambient glow colors when focused app changes
    val animatedPrimaryColor by animateColorAsState(
        targetValue = extractedPalette.primaryColor,
        animationSpec = tween(durationMillis = 400),
        label = "animatedPrimaryColor",
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = extractedPalette.secondaryColor,
        animationSpec = tween(durationMillis = 400),
        label = "animatedSecondaryColor",
    )

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
                                    animatedPrimaryColor.copy(alpha = 0.35f),
                                    animatedSecondaryColor.copy(alpha = 0.18f),
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
                    ambientGlowColor = animatedPrimaryColor,
                ) { actualIndex, _ ->
                    val appInfo = apps[actualIndex]
                    PosterCardContent(appInfo = appInfo)
                }

                // Focused App Title at the bottom of the screen with subdue launch indicator buttons on the right
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
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
                            modifier = Modifier.padding(horizontal = 140.dp),
                        )
                    }

                    // Bottom-Right subdued touch buttons for Top Screen (A) / Bottom Screen (X)
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                if (currentApp != null) {
                                    onAppClickTop(currentApp)
                                }
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CutoutLetterCircleIcon(
                                    letter = "A",
                                    size = 18.dp,
                                    tint = appColors.onSurfaceSecondary,
                                    cutoutColor = appColors.appBackground,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.gamefocus_launch_top),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            color = appColors.onSurfaceSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        TextButton(
                            onClick = {
                                if (currentApp != null) {
                                    onAppClickBottom(currentApp)
                                }
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CutoutLetterCircleIcon(
                                    letter = "X",
                                    size = 18.dp,
                                    tint = appColors.onSurfaceSecondary,
                                    cutoutColor = appColors.appBackground,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.gamefocus_launch_bottom),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            color = appColors.onSurfaceSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                )
                            }
                        }
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
                    isOptionsMenuExpanded = isOptionsMenuExpanded,
                    onOptionsMenuExpandedChange = onOptionsMenuExpandedChange,
                    dpadUpTrigger = dpadUpTrigger,
                    dpadRightTrigger = dpadRightTrigger,
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

private object FocusImageCache {
    private val coverCache = LruCache<String, ImageBitmap>(80)
    private val iconCache = LruCache<String, ImageBitmap>(80)

    fun getCoverBitmap(appInfo: InstalledAppInfo): ImageBitmap? {
        val path = appInfo.coverPath ?: return null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val cacheKey = "$path:${file.lastModified()}"
        val cached = coverCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val startTime = System.currentTimeMillis()
        val bitmap =
            try {
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 2 // Downsample 2x for poster display (saves 4x memory and decodes faster)
                    }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to decode cover file $path: ${e.message}")
                null
            }

        val elapsed = System.currentTimeMillis() - startTime
        AppLog.d(TAG, "Decoded poster card cover for ${appInfo.label} in ${elapsed}ms")

        if (bitmap != null) {
            coverCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    fun getIconBitmap(
        context: Context,
        appInfo: InstalledAppInfo,
    ): ImageBitmap? {
        val cacheKey = appInfo.packageName
        val cached = iconCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val iconsDir = File(context.cacheDir, "gamefocus_icons").apply { mkdirs() }
        val iconFile = File(iconsDir, "${appInfo.packageName}.png")
        if (iconFile.exists() && iconFile.length() > 0) {
            val startTime = System.currentTimeMillis()
            val diskBitmap =
                try {
                    BitmapFactory.decodeFile(iconFile.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }

            if (diskBitmap != null) {
                val elapsed = System.currentTimeMillis() - startTime
                AppLog.d(TAG, "Loaded disk-cached icon PNG for ${appInfo.label} in ${elapsed}ms")
                iconCache.put(cacheKey, diskBitmap)
                return diskBitmap
            }
        }

        val startTime = System.currentTimeMillis()
        val bitmap = appInfo.icon?.toBitmapSafe()

        val elapsed = System.currentTimeMillis() - startTime
        AppLog.d(TAG, "Converted app icon for ${appInfo.label} in ${elapsed}ms")

        if (bitmap != null) {
            iconCache.put(cacheKey, bitmap)
            try {
                val androidBmp = appInfo.icon?.toAndroidBitmap()
                if (androidBmp != null) {
                    java.io.FileOutputStream(iconFile).use { out ->
                        androidBmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    androidBmp.recycle()
                }
            } catch (_: Exception) {
            }
        }
        return bitmap
    }

    private fun Drawable.toAndroidBitmap(): Bitmap? =
        try {
            val w = intrinsicWidth.coerceIn(1, 128)
            val h = intrinsicHeight.coerceIn(1, 128)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, w, h)
            draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
}

@Composable
private fun PosterCardContent(
    appInfo: InstalledAppInfo,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current

    val coverBitmap =
        remember(appInfo.coverPath, appInfo.coverPath?.let { File(it).lastModified() }) {
            FocusImageCache.getCoverBitmap(appInfo)
        }

    val iconBitmap =
        remember(appInfo.icon, coverBitmap) {
            if (coverBitmap == null) FocusImageCache.getIconBitmap(context, appInfo) else null
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
