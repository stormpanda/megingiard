package com.stormpanda.megingiard.macropad

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.BitmapUtils
import com.stormpanda.megingiard.mirror.CutoutMaskFileStore
import com.stormpanda.megingiard.mirror.CutoutMaskManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MacroPadMediaRepo"
private const val BACKGROUNDS_DIR = "backgrounds"
private const val BG_PREFIX = "bg_"
private const val MASKS_DIR = "masks"
private const val MASK_PREFIX = "mask_"
private const val MAX_BITMAP_CACHE_BYTES = 32 * 1024 * 1024

/**
 * Encapsulates filesystem and bitmap storage operations for MacroPad layout backgrounds and masks.
 */
object MacroPadMediaRepository {
    private val appIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _mediaRevision = MutableStateFlow(0)
    val mediaRevision: StateFlow<Int> = _mediaRevision.asStateFlow()

    private val bitmapCache =
        object : LruCache<String, Bitmap>(MAX_BITMAP_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.allocationByteCount.coerceAtLeast(1)
        }

    fun resetForTesting() {
        ioDispatcher = Dispatchers.IO
        synchronized(bitmapCache) { bitmapCache.evictAll() }
        _mediaRevision.value = 0
    }

    private fun buildCacheKey(
        context: Context,
        pathOrUri: String,
        targetW: Int,
        targetH: Int,
    ): String {
        val normalizedSource =
            when {
                pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://") -> pathOrUri
                pathOrUri.startsWith("/") -> File(pathOrUri).absolutePath
                else -> File(context.filesDir, pathOrUri).absolutePath
            }
        return "$normalizedSource:${targetW}x$targetH"
    }

    private fun invalidateFileCache(file: File) {
        val prefix = "${file.absolutePath}:"
        synchronized(bitmapCache) {
            val keysToRemove = bitmapCache.snapshot().keys.filter { it.startsWith(prefix) }
            keysToRemove.forEach { bitmapCache.remove(it) }
        }
    }

    /**
     * Synchronously returns an already-decoded scaled bitmap for [pathOrUri] if present in the in-memory LRU cache.
     */
    fun getCachedScaledBitmap(
        context: Context,
        pathOrUri: String,
    ): Bitmap? {
        if (pathOrUri.isBlank()) return null
        val (targetW, targetH) = BitmapUtils.getScreenTargetDimensions(context)
        val cacheKey = buildCacheKey(context, pathOrUri, targetW, targetH)
        return synchronized(bitmapCache) {
            val cached = bitmapCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                cached
            } else {
                if (cached != null) bitmapCache.remove(cacheKey)
                null
            }
        }
    }

    /**
     * Copies layout background, mask, and cutout calibration files asynchronously on an application-scoped
     * IO coroutine so unmounting UI screens cannot cancel duplication mid-flight, then invalidates negative
     * caches and bumps [mediaRevision] so active canvases reload the newly copied assets.
     */
    fun duplicateLayoutAssetsAsync(
        context: Context,
        sourceLayout: PadLayout,
        targetLayout: PadLayout,
    ) {
        val appContext = context.applicationContext
        appIoScope.launch(ioDispatcher) {
            if (sourceLayout.backgroundImagePath != null) {
                duplicateBackgroundImage(appContext, sourceLayout.id, targetLayout.id)
            }
            if (sourceLayout.maskImagePath != null) {
                duplicateMaskImage(appContext, sourceLayout.id, targetLayout.id)
            }
            CutoutMaskFileStore.duplicateLayoutCutoutAssets(appContext.filesDir, sourceLayout, targetLayout)
            CutoutMaskManager.invalidateCache(targetLayout.id)
            targetLayout.mirrorCutouts.forEach { cutout ->
                CutoutMaskManager.invalidateCache(cutout.id)
            }
            CutoutMaskManager.notifyAssetsChanged()
            _mediaRevision.update { it + 1 }
            AppLog.i(TAG, "Duplicated media and cutout assets from layout ${sourceLayout.id} to ${targetLayout.id}")
        }
    }

    /**
     * Deletes background, mask, and cutout calibration files for [layouts] on an application-scoped
     * IO coroutine so immediate navigation (`MacroPadNavState.pop()`) never cancels cleanup.
     */
    fun deleteLayoutsAssetsAsync(
        context: Context,
        layouts: List<PadLayout>,
    ) {
        if (layouts.isEmpty()) return
        val appContext = context.applicationContext
        layouts.forEach { lay ->
            CutoutMaskManager.invalidateCache(lay.id)
            lay.mirrorCutouts.forEach { cutout ->
                CutoutMaskManager.invalidateCache(cutout.id)
            }
        }
        appIoScope.launch(ioDispatcher) {
            layouts.forEach { lay ->
                deleteBackgroundImage(appContext, lay.id)
                deleteMaskImage(appContext, lay.id)
                CutoutMaskManager.deleteMask(appContext, lay.id)
                lay.mirrorCutouts.forEach { cutout ->
                    CutoutMaskManager.deleteMask(appContext, cutout.id)
                }
            }
            CutoutMaskManager.notifyAssetsChanged()
            _mediaRevision.update { it + 1 }
            AppLog.i(TAG, "Deleted media and cutout assets for ${layouts.size} layout(s)")
        }
    }

    /**
     * Reads and decodes a scaled bitmap for the given path or URI (relative path under `context.filesDir`,
     * absolute filesystem path, or `content://`/`file://` URI).
     */
    suspend fun loadScaledBitmap(
        context: Context,
        pathOrUri: String,
    ): Bitmap? {
        if (pathOrUri.isBlank()) return null
        getCachedScaledBitmap(context, pathOrUri)?.let { return it }

        return withContext(ioDispatcher) {
            val (targetW, targetH) = BitmapUtils.getScreenTargetDimensions(context)
            val cacheKey = buildCacheKey(context, pathOrUri, targetW, targetH)
            synchronized(bitmapCache) {
                val cached = bitmapCache.get(cacheKey)
                if (cached != null && !cached.isRecycled) return@withContext cached
            }

            val decoded =
                when {
                    pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://") -> {
                        try {
                            BitmapUtils.decodeScaledBitmapFromUri(context, Uri.parse(pathOrUri), targetW, targetH)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Failed to decode scaled bitmap from uri $pathOrUri", e)
                            null
                        }
                    }

                    pathOrUri.startsWith("/") -> {
                        val file = File(pathOrUri)
                        if (!file.exists()) {
                            null
                        } else {
                            BitmapUtils.decodeScaledBitmap(file, targetW, targetH)
                        }
                    }

                    else -> {
                        val file = File(context.filesDir, pathOrUri)
                        if (!file.exists()) {
                            null
                        } else {
                            BitmapUtils.decodeScaledBitmap(file, targetW, targetH)
                        }
                    }
                }

            if (decoded != null && !decoded.isRecycled) {
                decoded.prepareToDraw()
                synchronized(bitmapCache) {
                    bitmapCache.put(cacheKey, decoded)
                }
            }
            decoded
        }
    }

    /**
     * Saves a background image from [srcUri] to `backgrounds/bg_[layoutId]` as a WebP image.
     * Returns the relative storage path or `null` if saving failed.
     */
    suspend fun saveBackgroundImage(
        context: Context,
        layoutId: String,
        srcUri: Uri,
    ): String? = saveLayerImage(context, BACKGROUNDS_DIR, BG_PREFIX, layoutId, srcUri)

    /**
     * Deletes the background image associated with [layoutId] if it exists.
     */
    suspend fun deleteBackgroundImage(
        context: Context,
        layoutId: String,
    ): Boolean = deleteLayerImage(context, BACKGROUNDS_DIR, BG_PREFIX, layoutId)

    /**
     * Copies the background image from [originalLayoutId] to [newLayoutId].
     * Returns the relative storage path or `null` if no source background existed or copy failed.
     */
    suspend fun duplicateBackgroundImage(
        context: Context,
        originalLayoutId: String,
        newLayoutId: String,
    ): String? = duplicateLayerImage(context, BACKGROUNDS_DIR, BG_PREFIX, originalLayoutId, newLayoutId)

    /**
     * Saves a mask image from [srcUri] to `masks/mask_[layoutId]` as a WebP image.
     * Returns the relative storage path or `null` if saving failed.
     */
    suspend fun saveMaskImage(
        context: Context,
        layoutId: String,
        srcUri: Uri,
    ): String? = saveLayerImage(context, MASKS_DIR, MASK_PREFIX, layoutId, srcUri)

    /**
     * Deletes the mask image associated with [layoutId] if it exists.
     */
    suspend fun deleteMaskImage(
        context: Context,
        layoutId: String,
    ): Boolean = deleteLayerImage(context, MASKS_DIR, MASK_PREFIX, layoutId)

    /**
     * Copies the mask image from [originalLayoutId] to [newLayoutId].
     * Returns the relative storage path or `null` if no source mask existed or copy failed.
     */
    suspend fun duplicateMaskImage(
        context: Context,
        originalLayoutId: String,
        newLayoutId: String,
    ): String? = duplicateLayerImage(context, MASKS_DIR, MASK_PREFIX, originalLayoutId, newLayoutId)

    private suspend fun saveLayerImage(
        context: Context,
        dirName: String,
        filePrefix: String,
        layoutId: String,
        srcUri: Uri,
    ): String? =
        withContext(ioDispatcher) {
            val dir = File(context.filesDir, dirName)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val fileName = "$filePrefix$layoutId"
            val destFile = File(dir, fileName)
            val (targetW, targetH) = BitmapUtils.getScreenTargetDimensions(context)
            val saved =
                BitmapUtils.saveScaledWebp(
                    context = context,
                    srcUri = srcUri,
                    srcFile = null,
                    destFile = destFile,
                    targetW = targetW,
                    targetH = targetH,
                )
            if (saved) {
                invalidateFileCache(destFile)
                AppLog.d(TAG, "Saved $dirName image for layout $layoutId")
                "$dirName/$fileName"
            } else {
                null
            }
        }

    private suspend fun deleteLayerImage(
        context: Context,
        dirName: String,
        filePrefix: String,
        layoutId: String,
    ): Boolean =
        withContext(ioDispatcher) {
            val dir = File(context.filesDir, dirName)
            val destFile = File(dir, "$filePrefix$layoutId")
            invalidateFileCache(destFile)
            if (destFile.exists()) {
                try {
                    destFile.delete()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to delete $dirName file for layout $layoutId", e)
                    false
                }
            } else {
                true
            }
        }

    private suspend fun duplicateLayerImage(
        context: Context,
        dirName: String,
        filePrefix: String,
        originalLayoutId: String,
        newLayoutId: String,
    ): String? =
        withContext(ioDispatcher) {
            val dir = File(context.filesDir, dirName)
            val srcFile = File(dir, "$filePrefix$originalLayoutId")
            if (srcFile.exists()) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val destFileName = "$filePrefix$newLayoutId"
                val destFile = File(dir, destFileName)
                try {
                    srcFile.copyTo(destFile, overwrite = true)
                    invalidateFileCache(destFile)
                    "$dirName/$destFileName"
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to copy $dirName file from $originalLayoutId to $newLayoutId", e)
                    null
                }
            } else {
                null
            }
        }
}
