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
 * raw variance maps for dynamic translucency/feathering, layout anchor signatures for presence detection,
 * and high-resolution freeze frames for cutscene preservation.
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
    private val layoutAnchorCache = ConcurrentHashMap<String, HudAnchorSignature>()
    private val freezeFrameCache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Retrieves the transparency mask bitmap for [cutoutId] with optional [translucency] (0..10)
     * and edge [featheringPx] (0..10).
     *
     * If both [translucency] and [featheringPx] are 0, returns the base unfeathered mask.
     * When [translucency] > 0 and a variance map is available, regenerates the mask dynamically
     * via [HudAutoTuner.buildMask] and caches the resulting bitmap in memory keyed by
     * `"$cutoutId:$translucency:$featheringPx"`.
     */
    fun getMask(
        context: Context,
        cutoutId: String,
        translucency: Int = MIN_TRANSLUCENCY,
        featheringPx: Int = MIN_FEATHERING_PX,
    ): Bitmap? {
        val clampedTranslucency = translucency.coerceIn(MIN_TRANSLUCENCY, MAX_TRANSLUCENCY)
        val clampedFeathering = featheringPx.coerceIn(MIN_FEATHERING_PX, MAX_FEATHERING_PX)

        if (clampedTranslucency == MIN_TRANSLUCENCY && clampedFeathering == MIN_FEATHERING_PX) {
            return getBaseMask(context, cutoutId)
        }

        val cacheKey = "$cutoutId:$clampedTranslucency:$clampedFeathering"
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
                    HudAutoTuner.buildMask(
                        varianceMap = varMap,
                        width = width,
                        height = height,
                        translucency = clampedTranslucency,
                        featheringPx = clampedFeathering,
                    )
                } else {
                    // Fallback: If no variance map is available (e.g. legacy mask), apply edge feathering to base mask
                    val pixels = IntArray(width * height)
                    baseBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    if (clampedFeathering > 0) {
                        HudAutoTuner.applyEdgeFeathering(pixels, width, height, clampedFeathering)
                    } else {
                        pixels
                    }
                }

            val tunedBitmap = Bitmap.createBitmap(tunedPixels, width, height, Bitmap.Config.ARGB_8888)
            tunedMaskCache[cacheKey] = tunedBitmap
            AppLog.d(
                TAG,
                "Generated tuned mask (t=$clampedTranslucency, f=$clampedFeathering px) for cutout $cutoutId (${width}x$height)",
            )
            tunedBitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to generate tuned mask for cutout $cutoutId", e)
            baseBitmap
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
    }

    /**
     * Checks if a transparency mask exists for [cutoutId] either in cache or on disk.
     */
    fun hasMask(
        context: Context,
        cutoutId: String,
    ): Boolean {
        if (baseMaskCache.containsKey(cutoutId)) return true
        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$PNG_EXTENSION")
        return file.exists()
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
    ): HudAnchorSignature? {
        layoutAnchorCache[layoutId]?.let { return it }

        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$LAYOUT_ANCHOR_FILE_PREFIX$layoutId$ANCHOR_EXTENSION")
        if (!file.exists()) return null

        return try {
            val text = file.readText()
            val signature = json.decodeFromString(HudAnchorSignature.serializer(), text)
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
        signature: HudAnchorSignature,
    ) {
        layoutAnchorCache[layoutId] = signature
        try {
            val dir = File(context.filesDir, MASKS_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$LAYOUT_ANCHOR_FILE_PREFIX$layoutId$ANCHOR_EXTENSION")
            file.writeText(json.encodeToString(HudAnchorSignature.serializer(), signature))
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
        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$LAYOUT_ANCHOR_FILE_PREFIX$layoutId$ANCHOR_EXTENSION")
        return file.exists()
    }
}
