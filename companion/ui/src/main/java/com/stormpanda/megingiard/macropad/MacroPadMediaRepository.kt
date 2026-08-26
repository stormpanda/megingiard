package com.stormpanda.megingiard.macropad

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MacroPadMediaRepo"
private const val BACKGROUNDS_DIR = "backgrounds"

/**
 * Encapsulates filesystem and bitmap storage operations for MacroPad layout backgrounds.
 */
object MacroPadMediaRepository {
    /**
     * Reads and decodes a scaled bitmap for the given path or URI (relative path under `context.filesDir`,
     * absolute filesystem path, or `content://`/`file://` URI).
     */
    suspend fun loadScaledBitmap(
        context: Context,
        pathOrUri: String,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val (targetW, targetH) = BitmapUtils.getScreenTargetDimensions(context)
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
        }

    /**
     * Saves a background image from [srcUri] to `backgrounds/bg_[layoutId]` as a WebP image.
     * Returns the relative storage path or `null` if saving failed.
     */
    suspend fun saveBackgroundImage(
        context: Context,
        layoutId: String,
        srcUri: Uri,
    ): String? =
        withContext(Dispatchers.IO) {
            val backgroundsDir = File(context.filesDir, BACKGROUNDS_DIR)
            if (!backgroundsDir.exists()) {
                backgroundsDir.mkdirs()
            }
            val destFile = File(backgroundsDir, "bg_$layoutId")
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
                "$BACKGROUNDS_DIR/bg_$layoutId"
            } else {
                null
            }
        }

    /**
     * Deletes the background image associated with [layoutId] if it exists.
     */
    suspend fun deleteBackgroundImage(
        context: Context,
        layoutId: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val backgroundsDir = File(context.filesDir, BACKGROUNDS_DIR)
            val destFile = File(backgroundsDir, "bg_$layoutId")
            if (destFile.exists()) {
                try {
                    destFile.delete()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to delete background file for layout $layoutId", e)
                    false
                }
            } else {
                true
            }
        }

    /**
     * Copies the background image from [originalLayoutId] to [newLayoutId].
     * Returns the relative storage path or `null` if no source background existed or copy failed.
     */
    suspend fun duplicateBackgroundImage(
        context: Context,
        originalLayoutId: String,
        newLayoutId: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val backgroundsDir = File(context.filesDir, BACKGROUNDS_DIR)
            val srcFile = File(backgroundsDir, "bg_$originalLayoutId")
            if (srcFile.exists()) {
                if (!backgroundsDir.exists()) {
                    backgroundsDir.mkdirs()
                }
                val destFile = File(backgroundsDir, "bg_$newLayoutId")
                try {
                    srcFile.copyTo(destFile, overwrite = true)
                    "$BACKGROUNDS_DIR/bg_$newLayoutId"
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to copy background file from $originalLayoutId to $newLayoutId", e)
                    null
                }
            } else {
                null
            }
        }
}
