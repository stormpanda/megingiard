package com.stormpanda.megingiard.focus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AppPaletteExtractor"
private const val PALETTE_CACHE_SIZE = 100
private const val PALETTE_TARGET_AREA = 128 * 128

data class ExtractedAppPalette(
    val primaryColor: Color,
    val secondaryColor: Color,
)

object AppPaletteExtractor {
    private val paletteCache = LruCache<String, ExtractedAppPalette>(PALETTE_CACHE_SIZE)

    suspend fun extractColorsAsync(
        appInfo: InstalledAppInfo,
        defaultPrimary: Color,
        defaultSecondary: Color,
    ): ExtractedAppPalette =
        withContext(Dispatchers.Default) {
            val cacheKey = "${appInfo.packageName}:${appInfo.coverPath ?: "icon"}"
            paletteCache.get(cacheKey)?.let { return@withContext it }

            val palette = extractColorsInternal(appInfo, defaultPrimary, defaultSecondary)
            paletteCache.put(cacheKey, palette)
            palette
        }

    fun extractColors(
        appInfo: InstalledAppInfo,
        defaultPrimary: Color,
        defaultSecondary: Color,
    ): ExtractedAppPalette {
        val cacheKey = "${appInfo.packageName}:${appInfo.coverPath ?: "icon"}"
        paletteCache.get(cacheKey)?.let { return it }

        val palette = extractColorsInternal(appInfo, defaultPrimary, defaultSecondary)
        paletteCache.put(cacheKey, palette)
        return palette
    }

    private fun extractColorsInternal(
        appInfo: InstalledAppInfo,
        defaultPrimary: Color,
        defaultSecondary: Color,
    ): ExtractedAppPalette {
        var bitmap: Bitmap? = null
        try {
            appInfo.coverPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    val options =
                        BitmapFactory.Options().apply {
                            inSampleSize = 4 // Downsample 4x for fast decoding & memory efficiency
                        }
                    bitmap = BitmapFactory.decodeFile(path, options)
                }
            }

            val iconDrawable = appInfo.icon
            if (bitmap == null && iconDrawable != null) {
                bitmap = iconDrawable.toAndroidBitmap()
            }

            val targetBitmap = bitmap
            if (targetBitmap != null && !targetBitmap.isRecycled) {
                val palette =
                    Palette
                        .from(targetBitmap)
                        .resizeBitmapArea(PALETTE_TARGET_AREA)
                        .generate()

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
        } finally {
            try {
                bitmap?.recycle()
            } catch (_: Exception) {
            }
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
