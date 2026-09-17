package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CutoutMaskManager"
private const val MASKS_DIR = "cutout_masks"
private const val MASK_FILE_PREFIX = "mask_"
private const val LAYOUT_ANCHOR_FILE_PREFIX = "layout_anchor_"
private const val PNG_EXTENSION = ".png"
private const val VARIANCE_EXTENSION = "_var.bin"
private const val ANCHOR_EXTENSION = "_anchor.json"
private const val FREEZE_EXTENSION = "_freeze.png"
private const val PNG_QUALITY = 100

/**
 * Manages in-memory caching and filesystem persistence for auto-tuned cutout transparency masks,
 * raw variance maps for dynamic translucency, layout anchor signatures for presence detection,
 * and high-resolution freeze frames for freeze frame preservation.
 *
 * Base masks are stored as lossless PNG files under `context.filesDir/cutout_masks/mask_<cutoutId>.png`.
 * Variance maps are stored as binary byte arrays under `context.filesDir/cutout_masks/mask_<cutoutId>_var.bin`.
 * Layout anchor signatures are stored as JSON under `context.filesDir/cutout_masks/layout_anchor_<layoutId>_anchor.json`.
 * Freeze frames are stored as PNG under `context.filesDir/cutout_masks/mask_<cutoutId>_freeze.png`.
 */
object CutoutMaskManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseMaskCache = ConcurrentHashMap<String, Bitmap>()
    private val tunedMaskCache = ConcurrentHashMap<String, Bitmap>()
    private val varianceCache = ConcurrentHashMap<String, ByteArray>()
    private val layoutAnchorCache = ConcurrentHashMap<String, VisualAnchorSignature>()
    private val freezeFrameCache = ConcurrentHashMap<String, Bitmap>()
    private val staticAssetCache = ConcurrentHashMap<String, Bitmap>()
    private val maskExistenceCache = ConcurrentHashMap<String, Boolean>()
    private val layoutAnchorExistenceCache = ConcurrentHashMap<String, Boolean>()

    /**
     * Retrieves the transparency mask bitmap for [cutoutId] with optional [translucency] (0..100)
     * and [sensitivity] (0..255).
     *
     * If [translucency] is 0, [sensitivity] is default 14, and [cavityHealing] is true,
     * returns the base unfeathered mask.
     * When parameters are customized and a variance map is available, regenerates the mask dynamically
     * via [CutoutAutoTuner.buildMask] and caches the resulting bitmap in memory keyed by
     * `"$cutoutId:$sensitivity:$translucency:$cavityHealing"`.
     */
    fun getMask(
        context: Context,
        cutoutId: String,
        translucency: Int = MIN_TRANSLUCENCY,
        sensitivity: Int = DEFAULT_SENSITIVITY,
        cavityHealing: Boolean = true,
    ): Bitmap? {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedSensitivity = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)

        if (clampedTranslucency == MIN_TRANSLUCENCY &&
            clampedSensitivity == DEFAULT_SENSITIVITY &&
            cavityHealing
        ) {
            return getBaseMask(context, cutoutId)
        }

        val cacheKey =
            "$cutoutId:$clampedSensitivity:$clampedTranslucency:$cavityHealing"
        tunedMaskCache[cacheKey]?.let { cached ->
            if (!cached.isRecycled) return cached
            tunedMaskCache.remove(cacheKey)
        }

        val baseBitmap = getBaseMask(context, cutoutId) ?: return null
        val width = baseBitmap.width
        val height = baseBitmap.height

        return try {
            val varMap = getVarianceMap(context, cutoutId)
            val tunedPixels =
                if (varMap != null && varMap.size == width * height) {
                    CutoutAutoTuner.buildMask(
                        varianceMap = varMap,
                        width = width,
                        height = height,
                        translucency = clampedTranslucency,
                        colorChangeThreshold = clampedSensitivity,
                        cavityHealing = cavityHealing,
                    )
                } else {
                    val pixels = IntArray(width * height)
                    baseBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    pixels
                }

            val tunedBitmap = Bitmap.createBitmap(tunedPixels, width, height, Bitmap.Config.ARGB_8888)
            tunedMaskCache[cacheKey] = tunedBitmap
            AppLog.d(
                TAG,
                "Generated tuned mask (s=$clampedSensitivity, t=$clampedTranslucency, c=$cavityHealing) for cutout $cutoutId (${width}x$height)",
            )
            tunedBitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to generate tuned mask for cutout $cutoutId", e)
            baseBitmap
        }
    }

    /**
     * Retrieves a pre-rendered 32-bit ARGB static asset bitmap for [cutoutId] combining the reference
     * freeze frame's RGB colors with the tuned transparency mask's alpha channel.
     *
     * Used when [ScreenCutout.renderAsStaticAsset] is enabled to render a completely static HUD element
     * without live stream background motion bleed-through.
     */
    fun getStaticAsset(
        context: Context,
        cutoutId: String,
        translucency: Int = MIN_TRANSLUCENCY,
        sensitivity: Int = DEFAULT_SENSITIVITY,
        cavityHealing: Boolean = true,
    ): Bitmap? {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedSensitivity = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)

        val cacheKey =
            "$cutoutId:static:$clampedSensitivity:$clampedTranslucency:$cavityHealing"
        staticAssetCache[cacheKey]?.let { cached ->
            if (!cached.isRecycled) return cached
            staticAssetCache.remove(cacheKey)
        }

        val freezeFrame = getFreezeFrame(context, cutoutId) ?: return null
        val maskBitmap =
            getMask(
                context = context,
                cutoutId = cutoutId,
                translucency = clampedTranslucency,
                sensitivity = clampedSensitivity,
                cavityHealing = cavityHealing,
            ) ?: return null

        val width = freezeFrame.width
        val height = freezeFrame.height
        if (maskBitmap.width != width || maskBitmap.height != height) {
            return null
        }

        return try {
            val basePixels = IntArray(width * height)
            freezeFrame.getPixels(basePixels, 0, width, 0, 0, width, height)

            val maskPixels = IntArray(width * height)
            maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)

            val staticPixels = CutoutAutoTuner.buildStaticAsset(basePixels, maskPixels, width, height)
            val staticBitmap = Bitmap.createBitmap(staticPixels, width, height, Bitmap.Config.ARGB_8888)
            staticAssetCache[cacheKey] = staticBitmap
            AppLog.d(
                TAG,
                "Generated tuned static asset (s=$clampedSensitivity, t=$clampedTranslucency) for cutout $cutoutId (${width}x$height)",
            )
            staticBitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to generate static asset for cutout $cutoutId", e)
            null
        }
    }

    private fun getBaseMask(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        baseMaskCache[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            baseMaskCache.remove(cutoutId)
        }

        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$PNG_EXTENSION")
        if (!file.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                baseMaskCache[cutoutId] = bitmap
                AppLog.d(TAG, "Loaded mask for cutout $cutoutId (${bitmap.width}x${bitmap.height}) from disk")
            }
            bitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode mask for cutout $cutoutId", e)
            null
        }
    }

    /**
     * Retrieves the raw per-pixel variance map for [cutoutId] if available.
     */
    fun getVarianceMap(
        context: Context,
        cutoutId: String,
    ): ByteArray? {
        varianceCache[cutoutId]?.let { return it }

        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$VARIANCE_EXTENSION")
        if (!file.exists()) return null

        return try {
            val bytes = file.readBytes()
            varianceCache[cutoutId] = bytes
            AppLog.d(TAG, "Loaded variance map for cutout $cutoutId (${bytes.size} bytes) from disk")
            bytes
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to read variance map for cutout $cutoutId", e)
            null
        }
    }

    /**
     * Persists [bitmap] to disk as the base mask and updates the in-memory cache for [cutoutId].
     * Optionally persists the raw [varianceMap], [anchorSignature], and [freezeFrame] alongside the mask.
     */
    fun saveMask(
        context: Context,
        cutoutId: String,
        bitmap: Bitmap,
        varianceMap: ByteArray? = null,
        freezeFrame: Bitmap? = null,
    ) {
        clearTunedCacheFor(cutoutId)
        baseMaskCache[cutoutId] = bitmap
        maskExistenceCache[cutoutId] = true
        if (varianceMap != null) {
            varianceCache[cutoutId] = varianceMap
        } else {
            varianceCache.remove(cutoutId)
        }

        try {
            val dir = File(context.filesDir, MASKS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$PNG_EXTENSION")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
            AppLog.i(TAG, "Saved mask for cutout $cutoutId (${bitmap.width}x${bitmap.height}) to ${file.absolutePath}")

            val varFile = File(dir, "$MASK_FILE_PREFIX$cutoutId$VARIANCE_EXTENSION")
            if (varianceMap != null) {
                varFile.writeBytes(varianceMap)
                AppLog.i(TAG, "Saved variance map for cutout $cutoutId (${varianceMap.size} bytes)")
            } else if (varFile.exists()) {
                varFile.delete()
            }

            if (freezeFrame != null) {
                saveFreezeFrame(context, cutoutId, freezeFrame)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist mask/variance for cutout $cutoutId", e)
        }
    }

    /**
     * Retrieves the reference freeze frame bitmap for [cutoutId] if available.
     */
    fun getFreezeFrame(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        freezeFrameCache[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            freezeFrameCache.remove(cutoutId)
        }

        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$FREEZE_EXTENSION")
        if (!file.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                freezeFrameCache[cutoutId] = bitmap
                AppLog.d(TAG, "Loaded freeze frame for cutout $cutoutId (${bitmap.width}x${bitmap.height}) from disk")
            }
            bitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode freeze frame for cutout $cutoutId", e)
            null
        }
    }

    /**
     * Persists [bitmap] as the reference freeze frame for [cutoutId].
     */
    fun saveFreezeFrame(
        context: Context,
        cutoutId: String,
        bitmap: Bitmap,
    ) {
        freezeFrameCache[cutoutId] = bitmap
        try {
            val dir = File(context.filesDir, MASKS_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$FREEZE_EXTENSION")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
            AppLog.i(TAG, "Saved freeze frame for cutout $cutoutId (${bitmap.width}x${bitmap.height}) to ${file.absolutePath}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist freeze frame for cutout $cutoutId", e)
        }
    }

    /**
     * Deletes the mask and variance files and clears all in-memory caches for [cutoutId].
     */
    fun deleteMask(
        context: Context,
        cutoutId: String,
    ) {
        clearTunedCacheFor(cutoutId)
        varianceCache.remove(cutoutId)
        maskExistenceCache[cutoutId] = false
        freezeFrameCache.remove(cutoutId)?.let { cached ->
            if (!cached.isRecycled) {
                cached.recycle()
            }
        }
        baseMaskCache.remove(cutoutId)?.let { cached ->
            if (!cached.isRecycled) {
                cached.recycle()
            }
        }
        try {
            val dir = File(context.filesDir, MASKS_DIR)
            val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$PNG_EXTENSION")
            if (file.exists()) {
                file.delete()
                AppLog.i(TAG, "Deleted mask file for cutout $cutoutId")
            }
            val varFile = File(dir, "$MASK_FILE_PREFIX$cutoutId$VARIANCE_EXTENSION")
            if (varFile.exists()) {
                varFile.delete()
                AppLog.i(TAG, "Deleted variance file for cutout $cutoutId")
            }
            val freezeFile = File(dir, "$MASK_FILE_PREFIX$cutoutId$FREEZE_EXTENSION")
            if (freezeFile.exists()) {
                freezeFile.delete()
                AppLog.i(TAG, "Deleted freeze file for cutout $cutoutId")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete mask files for cutout $cutoutId", e)
        }
    }

    private fun clearTunedCacheFor(cutoutId: String) {
        val prefix = "$cutoutId:"
        val keysToRemove = tunedMaskCache.keys.filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            tunedMaskCache.remove(key)?.let {
                if (!it.isRecycled) it.recycle()
            }
        }
        val staticKeysToRemove = staticAssetCache.keys.filter { it.startsWith(prefix) }
        for (key in staticKeysToRemove) {
            staticAssetCache.remove(key)?.let {
                if (!it.isRecycled) it.recycle()
            }
        }
    }

    /**
     * Checks if a transparency mask exists for [cutoutId] either in cache or on disk.
     */
    fun hasMask(
        context: Context,
        cutoutId: String,
    ): Boolean {
        if (baseMaskCache.containsKey(cutoutId)) return true
        maskExistenceCache[cutoutId]?.let { return it }
        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$PNG_EXTENSION")
        val exists = file.exists()
        maskExistenceCache[cutoutId] = exists
        return exists
    }

    /**
     * Checks if calibration assets (transparency mask) exist for [cutoutId].
     */
    fun isCalibrated(
        context: Context,
        cutoutId: String,
    ): Boolean = hasMask(context, cutoutId)

    /**
     * Retrieves the reference anchor signature for [layoutId] if available.
     */
    fun getLayoutAnchorSignature(
        context: Context,
        layoutId: String,
    ): VisualAnchorSignature? {
        layoutAnchorCache[layoutId]?.let { return it }

        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$LAYOUT_ANCHOR_FILE_PREFIX$layoutId$ANCHOR_EXTENSION")
        if (!file.exists()) return null

        return try {
            val text = file.readText()
            val signature = json.decodeFromString(VisualAnchorSignature.serializer(), text)
            layoutAnchorCache[layoutId] = signature
            AppLog.d(TAG, "Loaded anchor signature for layout $layoutId (${signature.points.size} points) from disk")
            signature
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to read anchor signature for layout $layoutId", e)
            null
        }
    }

    /**
     * Persists [signature] as the reference anchor signature for [layoutId].
     */
    fun saveLayoutAnchorSignature(
        context: Context,
        layoutId: String,
        signature: VisualAnchorSignature,
    ) {
        layoutAnchorCache[layoutId] = signature
        layoutAnchorExistenceCache[layoutId] = true
        try {
            val dir = File(context.filesDir, MASKS_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$LAYOUT_ANCHOR_FILE_PREFIX$layoutId$ANCHOR_EXTENSION")
            file.writeText(json.encodeToString(VisualAnchorSignature.serializer(), signature))
            AppLog.i(TAG, "Saved anchor signature for layout $layoutId (${signature.points.size} points)")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist anchor signature for layout $layoutId", e)
        }
    }

    /**
     * Deletes the anchor signature for [layoutId] from memory and disk.
     */
    fun deleteLayoutAnchorSignature(
        context: Context,
        layoutId: String,
    ) {
        layoutAnchorCache.remove(layoutId)
        layoutAnchorExistenceCache[layoutId] = false
        try {
            val dir = File(context.filesDir, MASKS_DIR)
            val file = File(dir, "$LAYOUT_ANCHOR_FILE_PREFIX$layoutId$ANCHOR_EXTENSION")
            if (file.exists()) {
                file.delete()
                AppLog.i(TAG, "Deleted anchor signature for layout $layoutId")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete anchor signature for layout $layoutId", e)
        }
    }

    /**
     * Checks if a reference anchor signature is calibrated for [layoutId].
     */
    fun isLayoutAnchorCalibrated(
        context: Context,
        layoutId: String,
    ): Boolean {
        if (layoutAnchorCache.containsKey(layoutId)) return true
        layoutAnchorExistenceCache[layoutId]?.let { return it }
        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$LAYOUT_ANCHOR_FILE_PREFIX$layoutId$ANCHOR_EXTENSION")
        val exists = file.exists()
        layoutAnchorExistenceCache[layoutId] = exists
        return exists
    }

    /**
     * Clones the reference anchor signature from [sourceLayoutId] to [targetLayoutId].
     */
    fun duplicateLayoutAnchorSignature(
        context: Context,
        sourceLayoutId: String,
        targetLayoutId: String,
    ) {
        val signature = getLayoutAnchorSignature(context, sourceLayoutId) ?: return
        val copiedSignature = signature.copy(cutoutId = targetLayoutId)
        saveLayoutAnchorSignature(context, targetLayoutId, copiedSignature)
        AppLog.i(TAG, "Duplicated anchor signature from $sourceLayoutId to $targetLayoutId")
    }
}
