package com.stormpanda.megingiard.focus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.stormpanda.megingiard.AppLog
import java.io.File

private const val TAG = "AppPaletteExtractor"

data class ExtractedAppPalette(
    val primaryColor: Color,
    val secondaryColor: Color,
)

object AppPaletteExtractor {
    fun extractColors(
        appInfo: InstalledAppInfo,
        defaultPrimary: Color,
        defaultSecondary: Color,
    ): ExtractedAppPalette {
        try {
            var bitmap: Bitmap? = null
            appInfo.coverPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    bitmap = BitmapFactory.decodeFile(path)
                }
            }

            val iconDrawable = appInfo.icon
            if (bitmap == null && iconDrawable != null) {
                bitmap = iconDrawable.toAndroidBitmap()
            }

            if (bitmap != null) {
                val palette = Palette.from(bitmap!!).generate()
                val swatches = palette.swatches.sortedByDescending { it.population }

                val primaryInt =
                    swatches.getOrNull(0)?.rgb
                        ?: palette.getDominantColor(defaultPrimary.toArgb())

                val secondaryInt =
                    swatches.getOrNull(1)?.rgb
                        ?: palette.getVibrantColor(palette.getMutedColor(primaryInt))

                return ExtractedAppPalette(
                    primaryColor = Color(primaryInt),
                    secondaryColor = Color(secondaryInt),
                )
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to extract palette for ${appInfo.label}: ${e.message}")
        }

        return ExtractedAppPalette(
            primaryColor = defaultPrimary,
            secondaryColor = defaultSecondary,
        )
    }

    private fun Drawable.toAndroidBitmap(): Bitmap? =
        try {
            val w = intrinsicWidth.coerceAtLeast(1)
            val h = intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
}
