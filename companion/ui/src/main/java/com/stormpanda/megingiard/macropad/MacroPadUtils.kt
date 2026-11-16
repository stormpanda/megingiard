package com.stormpanda.megingiard.macropad

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.stormpanda.megingiard.AppLog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "MacroPadUtils"
private const val DEFAULT_ICON_SIZE_PX = 48
private const val MAX_ICON_SIZE_PX = 192

/**
 * Converts an Android [Drawable] into a Jetpack Compose [ImageBitmap].
 * Caps oversized adaptive icons to [MAX_ICON_SIZE_PX] and calls [Bitmap.prepareToDraw]
 * so GPU texture upload does not stall the UI RenderThread.
 * Returns `null` if bitmap allocation or rendering fails.
 */
internal fun Drawable.toImageBitmap(): ImageBitmap? =
    try {
        val rawWidth = if (intrinsicWidth > 0) intrinsicWidth else DEFAULT_ICON_SIZE_PX
        val rawHeight = if (intrinsicHeight > 0) intrinsicHeight else DEFAULT_ICON_SIZE_PX
        val maxDim = max(rawWidth, rawHeight)
        val scale = if (maxDim > MAX_ICON_SIZE_PX) MAX_ICON_SIZE_PX.toFloat() / maxDim.toFloat() else 1f
        val width = (rawWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (rawHeight * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, min(width, canvas.width), min(height, canvas.height))
        draw(canvas)
        bitmap.prepareToDraw()
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        AppLog.w(TAG, "Failed to convert Drawable to ImageBitmap: ${e.message}")
        null
    }
