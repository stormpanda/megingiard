package com.stormpanda.megingiard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import java.io.File
import kotlin.math.max

object BitmapUtils {
    private const val TAG = "BitmapUtils"

    fun getScreenTargetDimensions(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        // Use the larger dimension for width and height target to handle any rotation/spanning.
        val maxDim = max(dm.widthPixels, dm.heightPixels)
        return Pair(maxDim, maxDim)
    }

    fun decodeScaledBitmap(
        file: File,
        targetW: Int,
        targetH: Int,
    ): Bitmap? {
        if (!file.exists()) return null
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val srcW = options.outWidth
            val srcH = options.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            val sampleSize = calculateInSampleSize(srcW, srcH, targetW, targetH)
            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode scaled bitmap from file: ${file.absolutePath}", e)
            null
        }
    }

    fun decodeScaledBitmapFromUri(
        context: Context,
        uri: Uri,
        targetW: Int,
        targetH: Int,
    ): Bitmap? {
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            val srcW = options.outWidth
            val srcH = options.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            val sampleSize = calculateInSampleSize(srcW, srcH, targetW, targetH)
            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode scaled bitmap from uri: $uri", e)
            null
        }
    }

    fun saveScaledWebp(
        context: Context,
        srcUri: Uri?,
        srcFile: File?,
        destFile: File,
        targetW: Int,
        targetH: Int,
    ): Boolean {
        return try {
            val bitmap =
                when {
                    srcUri != null -> decodeScaledBitmapFromUri(context, srcUri, targetW, targetH)
                    srcFile != null && srcFile.exists() -> decodeScaledBitmap(srcFile, targetW, targetH)
                    else -> null
                }
            if (bitmap == null) return false

            destFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, output)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save scaled WebP image", e)
            false
        }
    }

    private fun calculateInSampleSize(
        srcW: Int,
        srcH: Int,
        targetW: Int,
        targetH: Int,
    ): Int {
        var inSampleSize = 1
        if (srcW > targetW || srcH > targetH) {
            val halfHeight = srcH / 2
            val halfWidth = srcW / 2
            while ((halfHeight / inSampleSize) >= targetH && (halfWidth / inSampleSize) >= targetW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
