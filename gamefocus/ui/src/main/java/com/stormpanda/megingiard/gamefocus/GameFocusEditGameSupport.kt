package com.stormpanda.megingiard.gamefocus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.media.SteamGridDbException

private const val TAG = "GameFocusEditGameSupport"
private const val MIN_SAMPLE_SIZE = 1
private const val SAMPLE_SIZE_STEP = 2

/**
 * Largest power-of-two `inSampleSize` that keeps the decoded bitmap at least
 * [reqWidth] x [reqHeight] pixels (standard Android downsampling rule).
 */
internal fun calculateInSampleSize(
    srcWidth: Int,
    srcHeight: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    if (srcWidth <= 0 || srcHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return MIN_SAMPLE_SIZE
    var sampleSize = MIN_SAMPLE_SIZE
    while (srcWidth / (sampleSize * SAMPLE_SIZE_STEP) >= reqWidth &&
        srcHeight / (sampleSize * SAMPLE_SIZE_STEP) >= reqHeight
    ) {
        sampleSize *= SAMPLE_SIZE_STEP
    }
    return sampleSize
}

/**
 * Decodes [bytes] downsampled to roughly [reqWidth] x [reqHeight]. Must be called off the main thread.
 *
 * @return the decoded bitmap, or null if the bytes are not a decodable image.
 */
internal fun decodeSampledBitmap(
    bytes: ByteArray,
    reqWidth: Int,
    reqHeight: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        AppLog.w(TAG, "decodeSampledBitmap: undecodable image (${bytes.size} bytes)")
        return null
    }
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** Maps a SteamGridDB failure to a localized, user-facing message resource. */
@StringRes
internal fun steamGridDbErrorMessageRes(error: Throwable?): Int =
    when (error) {
        is SteamGridDbException.Offline -> R.string.steamgriddb_error_offline
        is SteamGridDbException.RateLimited -> R.string.steamgriddb_error_rate_limited
        is SteamGridDbException.ServiceUnavailable -> R.string.steamgriddb_error_unavailable
        is SteamGridDbException.Unauthorized -> R.string.steamgriddb_error_unauthorized
        else -> R.string.steamgriddb_error_generic
    }
