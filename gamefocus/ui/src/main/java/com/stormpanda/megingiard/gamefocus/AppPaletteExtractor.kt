package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import android.graphics.Color as AndroidColor

private const val TAG = "AppPaletteExtractor"
private const val PALETTE_CACHE_SIZE = 100
private const val PALETTE_TARGET_AREA = 128 * 128
private const val PREFS_NAME = "gamefocus_palettes_v2"
private const val CARD_BG_DARKEN_FACTOR = 0.35f

private const val VIBRANCY_LIGHTNESS_MIN = 0.15f
private const val VIBRANCY_LIGHTNESS_MAX = 0.85f
private const val MIN_VIBRANCY_SCORE_PRIMARY = 0.15f
private const val MIN_VIBRANCY_SCORE_SECONDARY = 0.10f
private const val DISTINCT_HUE_THRESHOLD_DEG = 20f
private const val DISTINCT_SUM_DIFF_THRESHOLD = 0.35f
private const val HUE_SHIFT_COMPLEMENTARY_DEG = 30f

/**
 * Calculates a vibrancy score (0.0 to 1.0) for a [Palette.Swatch].
 * Higher saturation yields higher vibrancy, while extreme dark or light colors are penalized.
 */
private fun Palette.Swatch.vibrancyScore(): Float {
    val saturation = hsl[1]
    val lightness = hsl[2]
    val lightnessFactor =
        when {
            lightness < VIBRANCY_LIGHTNESS_MIN -> (lightness / VIBRANCY_LIGHTNESS_MIN).coerceIn(0f, 1f)
            lightness > VIBRANCY_LIGHTNESS_MAX -> ((1.0f - lightness) / (1.0f - VIBRANCY_LIGHTNESS_MAX)).coerceIn(0f, 1f)
            else -> 1.0f
        }
    return saturation * lightnessFactor
}

/**
 * Checks whether two ARGB colors are visually distinct in HSV space.
 */
private fun isDistinctColor(
    color1: Int,
    color2: Int,
): Boolean {
    val hsv1 = FloatArray(3)
    val hsv2 = FloatArray(3)
    AndroidColor.colorToHSV(color1, hsv1)
    AndroidColor.colorToHSV(color2, hsv2)

    val rawHueDiff = abs(hsv1[0] - hsv2[0])
    val hueDiff = min(rawHueDiff, 360f - rawHueDiff)
    val satDiff = abs(hsv1[1] - hsv2[1])
    val valDiff = abs(hsv1[2] - hsv2[2])

    return hueDiff >= DISTINCT_HUE_THRESHOLD_DEG || (satDiff + valDiff) >= DISTINCT_SUM_DIFF_THRESHOLD
}

/**
 * Creates a complementary vibrant color derived from [baseColorInt] by shifting hue and enforcing saturation.
 */
private fun createComplementaryVibrant(baseColorInt: Int): Int {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(baseColorInt, hsv)
    hsv[0] = (hsv[0] + HUE_SHIFT_COMPLEMENTARY_DEG) % 360f
    hsv[1] = hsv[1].coerceAtLeast(0.6f)
    hsv[2] = hsv[2].coerceIn(0.4f, 0.8f)
    return AndroidColor.HSVToColor(hsv)
}

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
    AndroidColor.colorToHSV(this.toArgb(), hsv)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}

object AppPaletteExtractor {
    private val paletteCache = LruCache<String, ExtractedAppPalette>(PALETTE_CACHE_SIZE)
    private var isInitialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        if (isInitialized && appContext == context.applicationContext) return
        appContext = context.applicationContext
        loadPersistedPalettes(context.applicationContext)
        isInitialized = true
    }

    internal fun resetForTesting() {
        paletteCache.evictAll()
        isInitialized = false
        appContext = null
    }

    private fun loadPersistedPalettes(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var count = 0
            prefs.all.forEach { (key, value) ->
                if (value is String) {
                    val parts = value.split("|")
                    if (parts.size == 2) {
                        val primaryInt = parts[0].toIntOrNull()
                        val secondaryInt = parts[1].toIntOrNull()
                        if (primaryInt != null && secondaryInt != null) {
                            paletteCache.put(key, ExtractedAppPalette(Color(primaryInt), Color(secondaryInt), isExtracted = true))
                            count++
                        }
                    }
                }
            }
            AppLog.d(TAG, "Loaded $count persisted game palettes from SharedPreferences")
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
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val value = "${palette.primaryColor.toArgb()}|${palette.secondaryColor.toArgb()}"
            prefs.edit().putString(cacheKey, value).apply()
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to persist palette entry: ${e.message}")
        }
    }

    fun invalidatePalette(packageName: String) {
        val context = appContext
        val keysToRemove = paletteCache.snapshot().keys.filter { it.startsWith("$packageName:") }
        keysToRemove.forEach { paletteCache.remove(it) }

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                prefs.all.keys.forEach { key ->
                    if (key.startsWith("$packageName:")) {
                        editor.remove(key)
                    }
                }
                editor.apply()
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to invalidate persisted palette for $packageName: ${e.message}")
            }
        }
    }

    fun getCachedColorsOrNull(appInfo: InstalledAppInfo): ExtractedAppPalette? {
        val cacheKey = getCacheKey(appInfo)
        return paletteCache.get(cacheKey)
    }

    private fun getCacheKey(appInfo: InstalledAppInfo): String {
        val coverPath = appInfo.coverPath
        if (coverPath != null) {
            val file = File(coverPath)
            val modTime = if (file.exists()) file.lastModified() else 0L
            return "${appInfo.packageName}:$coverPath:$modTime"
        }
        val context = appContext
        if (context != null) {
            val file =
                if (appInfo.isRom) {
                    val logosDir = File(context.cacheDir, "gamefocus_logos")
                    File(logosDir, "${appInfo.packageName}.png")
                } else {
                    val iconsDir = File(context.cacheDir, "gamefocus_icons")
                    File(iconsDir, "${appInfo.packageName}.png")
                }
            if (file.exists()) {
                return "${appInfo.packageName}:${if (appInfo.isRom) "logo" else "icon"}:${file.lastModified()}"
            }
        }
        return "${appInfo.packageName}:${if (appInfo.isRom) "logo" else "icon"}"
    }

    suspend fun extractColorsAsync(
        appInfo: InstalledAppInfo,
        defaultPrimary: Color,
        defaultSecondary: Color,
    ): ExtractedAppPalette = withContext(Dispatchers.Default) { extractColors(appInfo, defaultPrimary, defaultSecondary) }

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

            if (bitmap == null) {
                val context = appContext
                if (context != null) {
                    val file =
                        if (appInfo.isRom) {
                            val logosDir = File(context.cacheDir, "gamefocus_logos")
                            File(logosDir, "${appInfo.packageName}.png")
                        } else {
                            val iconsDir = File(context.cacheDir, "gamefocus_icons")
                            File(iconsDir, "${appInfo.packageName}.png")
                        }
                    if (file.exists() && file.length() > 0) {
                        try {
                            bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        } catch (_: Exception) {
                            // Ignore
                        }
                    }
                }
            }

            if (bitmap == null) {
                val context = appContext
                if (context != null) {
                    val iconDrawable = FocusImageCache.getAppIcon(context, appInfo.packageName, appInfo.activityName)
                    if (iconDrawable != null) {
                        bitmap = iconDrawable.toAndroidBitmap()
                    }
                }
            }

            val targetBitmap = bitmap
            if (targetBitmap != null && !targetBitmap.isRecycled) {
                val palette =
                    Palette
                        .from(targetBitmap)
                        .resizeBitmapArea(PALETTE_TARGET_AREA)
                        .generate()

                val swatches = palette.swatches
                val sortedVibrantSwatches = swatches.sortedByDescending { it.vibrancyScore() }

                val primarySwatch =
                    sortedVibrantSwatches.firstOrNull { it.vibrancyScore() >= MIN_VIBRANCY_SCORE_PRIMARY }
                        ?: palette.vibrantSwatch
                        ?: palette.lightVibrantSwatch
                        ?: palette.darkVibrantSwatch
                        ?: sortedVibrantSwatches.firstOrNull()

                val primaryInt =
                    primarySwatch?.rgb
                        ?: palette.getVibrantColor(
                            palette.getLightVibrantColor(
                                palette.getDarkVibrantColor(
                                    palette.getDominantColor(defaultPrimary.toArgb()),
                                ),
                            ),
                        )

                val secondarySwatch =
                    sortedVibrantSwatches.firstOrNull { swatch ->
                        swatch.vibrancyScore() >= MIN_VIBRANCY_SCORE_SECONDARY && isDistinctColor(swatch.rgb, primaryInt)
                    } ?: listOfNotNull(
                        palette.lightVibrantSwatch,
                        palette.darkVibrantSwatch,
                        palette.vibrantSwatch,
                    ).firstOrNull { isDistinctColor(it.rgb, primaryInt) }

                val secondaryInt =
                    secondarySwatch?.rgb
                        ?: createComplementaryVibrant(primaryInt)

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
