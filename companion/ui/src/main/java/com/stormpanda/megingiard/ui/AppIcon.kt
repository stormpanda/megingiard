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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MaterialSymbol
import com.stormpanda.megingiard.macropad.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AppIcon"

/**
 * Renders an installed application's launcher icon lazily loaded from [packageName].
 * Offloads icon extraction to [Dispatchers.IO] and displays a fallback symbol if unavailable or empty.
 */
@Composable
internal fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    fallbackIconName: String = "apps",
    fallbackIconSize: Dp = 24.dp,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    var imageBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        if (packageName.isNotBlank()) {
            imageBitmap =
                withContext(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager
                        val iconDrawable = pm.getApplicationIcon(packageName)
                        iconDrawable.toImageBitmap()
                    } catch (e: Exception) {
                        AppLog.d(TAG, "Failed to load app icon for $packageName: ${e.message}")
                        null
                    }
                }
        } else {
            imageBitmap = null
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
