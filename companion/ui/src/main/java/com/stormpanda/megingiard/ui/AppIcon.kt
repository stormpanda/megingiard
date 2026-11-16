package com.stormpanda.megingiard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog

private const val TAG = "AppIcon"
private val AI_DEFAULT_FALLBACK_ICON_SIZE = 24.dp

/**
 * Renders an installed application's launcher icon lazily loaded from [packageName].
 * Uses [CompanionAppIconCache] for synchronous cache hits and off-thread [android.graphics.Bitmap.prepareToDraw].
 */
@Composable
internal fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    fallbackIconName: String = "apps",
    fallbackIconSize: Dp = AI_DEFAULT_FALLBACK_ICON_SIZE,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    var imageBitmap by remember(packageName) {
        mutableStateOf(CompanionAppIconCache.getCachedIcon(packageName))
    }

    LaunchedEffect(packageName) {
        if (packageName.isBlank()) {
            imageBitmap = null
        } else if (imageBitmap == null) {
            AppLog.d(TAG, "Loading uncached app icon for $packageName")
            imageBitmap = CompanionAppIconCache.loadIcon(context, packageName)
        }
    }

    val bitmap = imageBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = packageName,
            modifier = modifier,
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(colors.surfaceVariant, CircleShape),
        ) {
            MaterialSymbol(
                name = fallbackIconName,
                size = fallbackIconSize,
                tint = colors.onSurfaceSecondary,
            )
        }
    }
}
