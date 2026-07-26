package com.stormpanda.megingiard.focus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue

private const val TAG = "FocusTopLauncherScreen"

private val FTL_POSTER_CORNER_RADIUS = 16.dp
private val FTL_POSTER_WIDTH = 165.dp
private val FTL_POSTER_HEIGHT = 248.dp
private val FTL_ICON_SIZE = 72.dp

@Composable
fun FocusTopLauncherScreen(
    apps: List<InstalledAppInfo>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onAppClick: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()

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

    val pagerState =
        rememberPagerState(
            initialPage = selectedIndex.coerceIn(0, (apps.size - 1).coerceAtLeast(0)),
            pageCount = { apps.size },
        )

    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex && selectedIndex in apps.indices) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }
            .collectLatest { page ->
                if (page != selectedIndex) {
                    onSelectedIndexChange(page)
                }
            }
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
                                    appColors.accent.copy(alpha = 0.07f),
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
                Spacer(modifier = Modifier.height(16.dp))

                // Centered 2:3 Poster Carousel
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 135.dp),
                    pageSpacing = 6.dp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                ) { page ->
                    val appInfo = apps[page]
                    val isSelected = page == pagerState.currentPage

                    val pageOffset =
                        (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else (1.0f - (pageOffset * 0.14f)).coerceAtLeast(0.84f),
                        animationSpec = tween(durationMillis = 180),
                        label = "posterScale",
                    )

                    val alpha by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else (1.0f - (pageOffset * 0.4f)).coerceIn(0.45f, 1.0f),
                        animationSpec = tween(durationMillis = 180),
                        label = "posterAlpha",
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(FTL_POSTER_WIDTH, FTL_POSTER_HEIGHT)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }.shadow(
                                    elevation = if (isSelected) 18.dp else 4.dp,
                                    shape = RoundedCornerShape(FTL_POSTER_CORNER_RADIUS),
                                    ambientColor = if (isSelected) appColors.accent else Color.Black,
                                    spotColor = if (isSelected) appColors.accent else Color.Black,
                                ).clip(RoundedCornerShape(FTL_POSTER_CORNER_RADIUS))
                                .background(
                                    if (isSelected) appColors.surfaceVariant else appColors.surface,
                                ).border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) appColors.accent else appColors.divider,
                                    shape = RoundedCornerShape(FTL_POSTER_CORNER_RADIUS),
                                ).clickable {
                                    if (isSelected) {
                                        onAppClick(appInfo)
                                    } else {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(page)
                                        }
                                    }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        PosterCardContent(appInfo = appInfo)
                    }
                }

                // Focused App Name at the Bottom of Screen
                val currentApp = apps.getOrNull(pagerState.currentPage)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
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
    }
}

@Composable
private fun PosterCardContent(
    appInfo: InstalledAppInfo,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    val coverBitmap =
        remember(appInfo.coverPath) {
            appInfo.coverPath?.let { path ->
                if (File(path).exists()) {
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
                        .clip(RoundedCornerShape(16.dp))
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
