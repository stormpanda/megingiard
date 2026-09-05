package com.stormpanda.megingiard.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stormpanda.megingiard.AppLog
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CutoutMaskManager"
private const val MASKS_DIR = "cutout_masks"
private const val MASK_FILE_PREFIX = "mask_"
private const val PNG_EXTENSION = ".png"
private const val PNG_QUALITY = 100

/**
 * Manages in-memory caching and filesystem persistence for auto-tuned cutout transparency masks.
 * Masks are stored as lossless PNG files under `context.filesDir/cutout_masks/mask_<cutoutId>.png`.
 */
object CutoutMaskManager {
    private val maskCache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Retrieves the transparency mask bitmap for [cutoutId], loading from disk if not yet in cache.
     * Returns null if no mask exists.
     */
    fun getMask(
        context: Context,
        cutoutId: String,
    ): Bitmap? {
        maskCache[cutoutId]?.let { cached ->
            if (!cached.isRecycled) return cached
            maskCache.remove(cutoutId)
        }

        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$PNG_EXTENSION")
        if (!file.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                maskCache[cutoutId] = bitmap
                AppLog.d(TAG, "Loaded mask for cutout $cutoutId (${bitmap.width}x${bitmap.height}) from disk")
            }
            bitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode mask for cutout $cutoutId", e)
            null
        }
    }

    /**
     * Persists [bitmap] to disk and updates the in-memory cache for [cutoutId].
     */
    fun saveMask(
        context: Context,
        cutoutId: String,
        bitmap: Bitmap,
    ) {
        maskCache[cutoutId] = bitmap
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
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist mask for cutout $cutoutId", e)
        }
    }

    /**
     * Deletes the mask file and clears the in-memory cache for [cutoutId].
     */
    fun deleteMask(
        context: Context,
        cutoutId: String,
    ) {
        maskCache.remove(cutoutId)?.let { cached ->
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
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete mask file for cutout $cutoutId", e)
        }
    }

    /**
     * Checks if a transparency mask exists for [cutoutId] either in cache or on disk.
     */
    fun hasMask(
        context: Context,
        cutoutId: String,
    ): Boolean {
        if (maskCache.containsKey(cutoutId)) return true
        val dir = File(context.filesDir, MASKS_DIR)
        val file = File(dir, "$MASK_FILE_PREFIX$cutoutId$PNG_EXTENSION")
        return file.exists()
    }
}
