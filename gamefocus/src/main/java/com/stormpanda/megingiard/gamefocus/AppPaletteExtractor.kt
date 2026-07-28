package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.focus.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AppPaletteExtractor"
private const val PALETTE_CACHE_SIZE = 100
private const val PALETTE_TARGET_AREA = 128 * 128
private const val PALETTE_FILE_NAME = "gamefocus_palettes.txt"
private const val CARD_BG_DARKEN_FACTOR = 0.35f

data class ExtractedAppPalette(
    val primaryColor: Color,
    val secondaryColor: Color,
    val isExtracted: Boolean = true,
) {
    val darkenedPrimaryColor: Color
        get() = primaryColor.darken(CARD_BG_DARKEN_FACTOR)
}

fun Color.darken(factor: Float = CARD_BG_DARKEN_FACTOR): Color {
    if (this == Color.Unspecified) return Color.Unspecified
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

object AppPaletteExtractor {
    private val paletteCache = LruCache<String, ExtractedAppPalette>(PALETTE_CACHE_SIZE)
    private var isInitialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        loadPersistedPalettes(context.applicationContext)
        isInitialized = true
    }

    private fun loadPersistedPalettes(context: Context) {
        val file = File(context.filesDir, PALETTE_FILE_NAME)
        if (!file.exists()) return

        try {
            var count = 0
            file.readLines().forEach { line ->
                val parts = line.split("|")
                if (parts.size == 3) {
                    val key = parts[0]
                    val primaryInt = parts[1].toLongOrNull()?.toInt()
                    val secondaryInt = parts[2].toLongOrNull()?.toInt()
                    if (primaryInt != null && secondaryInt != null) {
                        paletteCache.put(key, ExtractedAppPalette(Color(primaryInt), Color(secondaryInt), isExtracted = true))
                        count++
                    }
                }
            }
            AppLog.d(TAG, "Loaded $count persisted game palettes from disk")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to load persisted palettes: ${e.message}")
        }
    }

    private fun persistPalette(
        cacheKey: String,
        palette: ExtractedAppPalette,
    ) {
        val context = appContext ?: return
        try {
            val file = File(context.filesDir, PALETTE_FILE_NAME)
            file.appendText("$cacheKey|${palette.primaryColor.toArgb()}|${palette.secondaryColor.toArgb()}\n")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to persist palette entry: ${e.message}")
        }
    }

    fun invalidatePalette(packageName: String) {
        val context = appContext
        val keysToRemove = mutableListOf<String>()
        paletteCache.snapshot().keys.forEach { key ->
            if (key.startsWith("$packageName:")) keysToRemove.add(key)
        }
        keysToRemove.forEach { paletteCache.remove(it) }

        if (context != null) {
            try {
                val file = File(context.filesDir, PALETTE_FILE_NAME)
                if (file.exists()) {
                    val remainingLines =
                        file.readLines().filterNot { line ->
                            line.startsWith("$packageName:")
                        }
                    file.writeText(remainingLines.joinToString("\n") + if (remainingLines.isNotEmpty()) "\n" else "")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to invalidate persisted palette for $packageName: ${e.message}")
            }
        }
    }

    private fun getCacheKey(appInfo: InstalledAppInfo): String {
        val coverPath = appInfo.coverPath
        return if (coverPath != null) {
            val file = File(coverPath)
            val modTime = if (file.exists()) file.lastModified() else 0L
            "${appInfo.packageName}:$coverPath:$modTime"
        } else {
            "${appInfo.packageName}:icon"
        }
    }

    suspend fun extractColorsAsync(
        appInfo: InstalledAppInfo,
        defaultPrimary: Color,
        defaultSecondary: Color,
    ): ExtractedAppPalette =
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val cacheKey = getCacheKey(appInfo)
            val cached = paletteCache.get(cacheKey)
            if (cached != null) {
                AppLog.d(TAG, "Palette cache HIT for ${appInfo.label} [0ms]")
                return@withContext cached
            }

            val palette = extractColorsInternal(appInfo, defaultPrimary, defaultSecondary)
            paletteCache.put(cacheKey, palette)
            persistPalette(cacheKey, palette)
            val elapsed = System.currentTimeMillis() - startTime
            AppLog.d(TAG, "Palette extracted for ${appInfo.label} in ${elapsed}ms")
            palette
        }

    fun extractColors(
        appInfo: InstalledAppInfo,
        defaultPrimary: Color,
        defaultSecondary: Color,
    ): ExtractedAppPalette {
        val startTime = System.currentTimeMillis()
        val cacheKey = getCacheKey(appInfo)
        val cached = paletteCache.get(cacheKey)
        if (cached != null) {
            AppLog.d(TAG, "Palette cache HIT for ${appInfo.label} [0ms]")
            return cached
        }

        val palette = extractColorsInternal(appInfo, defaultPrimary, defaultSecondary)
        paletteCache.put(cacheKey, palette)
        persistPalette(cacheKey, palette)
        val elapsed = System.currentTimeMillis() - startTime
        AppLog.d(TAG, "Palette extracted (sync) for ${appInfo.label} in ${elapsed}ms")
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
                    isExtracted = true,
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
            isExtracted = false,
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
