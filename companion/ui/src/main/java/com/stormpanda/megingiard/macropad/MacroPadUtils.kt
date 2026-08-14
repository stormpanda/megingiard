package com.stormpanda.megingiard.macropad

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.stormpanda.megingiard.AppLog

private const val TAG = "MacroPadUtils"
private const val DEFAULT_ICON_SIZE_PX = 48

/**
 * Converts an Android [Drawable] into a Jetpack Compose [ImageBitmap].
 * Returns `null` if bitmap allocation or rendering fails.
 */
internal fun Drawable.toImageBitmap(): ImageBitmap? =
    try {
        val width = if (intrinsicWidth > 0) intrinsicWidth else DEFAULT_ICON_SIZE_PX
        val height = if (intrinsicHeight > 0) intrinsicHeight else DEFAULT_ICON_SIZE_PX
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        AppLog.w(TAG, "Failed to convert Drawable to ImageBitmap: ${e.message}")
        null
    }
