package com.stormpanda.megingiard.focus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R

private const val TAG = "FocusTopLauncherScreen"

private val FTL_BG_COLOR = Color(0xFF101216)
private val FTL_SURFACE_COLOR = Color(0xFF1B1E26)
private val FTL_CARD_BG = Color(0xFF232733)
private val FTL_BORDER_COLOR = Color(0xFF383E50)
private val FTL_ACCENT_COLOR = Color(0xFF6366F1)
private val FTL_TEXT_PRIMARY = Color(0xFFF3F4F6)
private val FTL_TEXT_SECONDARY = Color(0xFF9CA3AF)

private val FTL_CORNER_RADIUS = 14.dp
private val FTL_ICON_SIZE = 54.dp
private val FTL_GRID_SPACING = 12.dp

@Composable
fun FocusTopLauncherScreen(
    apps: List<InstalledAppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredApps =
        remember(apps, searchQuery) {
            if (searchQuery.isBlank()) {
                apps
            } else {
                apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
            }
        }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FTL_BG_COLOR,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            // Header Bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = stringResource(R.string.focus_launcher_title),
                        tint = FTL_ACCENT_COLOR,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.focus_launcher_title),
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = FTL_TEXT_PRIMARY,
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(FTL_SURFACE_COLOR)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${filteredApps.size}",
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    color = FTL_TEXT_SECONDARY,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                        )
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.focus_launcher_refresh),
                        tint = FTL_TEXT_SECONDARY,
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.focus_launcher_search_hint),
                        color = FTL_TEXT_SECONDARY,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.focus_launcher_search_hint),
                        tint = FTL_TEXT_SECONDARY,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(FTL_CORNER_RADIUS),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FTL_SURFACE_COLOR,
                        unfocusedContainerColor = FTL_SURFACE_COLOR,
                        disabledContainerColor = FTL_SURFACE_COLOR,
                        focusedBorderColor = FTL_ACCENT_COLOR,
                        unfocusedBorderColor = FTL_BORDER_COLOR,
                        focusedTextColor = FTL_TEXT_PRIMARY,
                        unfocusedTextColor = FTL_TEXT_PRIMARY,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
            )

            // App Grid Browser
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.focus_launcher_no_apps),
                        style = MaterialTheme.typography.bodyLarge.copy(color = FTL_TEXT_SECONDARY),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(FTL_GRID_SPACING),
                    verticalArrangement = Arrangement.spacedBy(FTL_GRID_SPACING),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = filteredApps,
                        key = { "${it.packageName}/${it.activityName}" },
                    ) { appInfo ->
                        AppCard(
                            appInfo = appInfo,
                            onClick = { onAppClick(appInfo) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    appInfo: InstalledAppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(appInfo.icon) { appInfo.icon?.toBitmapSafe() }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(FTL_CORNER_RADIUS))
                .background(FTL_CARD_BG)
                .border(1.dp, FTL_BORDER_COLOR, RoundedCornerShape(FTL_CORNER_RADIUS))
                .clickable(onClick = onClick)
                .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(FTL_SURFACE_COLOR),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = appInfo.label,
                        tint = FTL_ACCENT_COLOR,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appInfo.label,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = FTL_TEXT_PRIMARY,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
