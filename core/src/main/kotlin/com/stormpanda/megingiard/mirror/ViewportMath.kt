package com.stormpanda.megingiard.mirror

/**
 * Fits [srcWidth]×[srcHeight] into [targetWidth]×[targetHeight] preserving aspect ratio
 * (letterbox / pillarbox). Returns the fitted (width, height) pair.
 */
fun fitAspectRatio(
    srcWidth: Int,
    srcHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Pair<Int, Int> {
    val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
    val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

    return if (srcRatio > targetRatio) {
        targetWidth to (targetWidth / srcRatio).toInt()
    } else {
        (targetHeight * srcRatio).toInt() to targetHeight
    }
}
