package com.stormpanda.megingiard.mirror

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val OVERLAP_TOLERANCE: Float = 0.0001f
private const val BINARY_SEARCH_STEPS = 10
private const val MIN_RESIZE_PX = 8
private const val ROTATION_90 = 90
private const val ROTATION_180 = 180
private const val ROTATION_270 = 270

/**
 * Maps a raw touch position on the mirror surface back through the current zoom/pan
 * transform to obtain the normalised content coordinate [0, 1] that corresponds to
 * the touched point on the primary display.
 *
 * The SurfaceView is centered in the secondary display's FrameLayout, so its pivot
 * point for the scale/translate transform lies at the screen center (screenW/2, screenH/2),
 * NOT at the SurfaceView's own center in its local coordinate space (sw/2, sh/2).
 * These differ when the content is letterboxed (sw != screenW or sh != screenH).
 *
 * Visual transform (screen → SurfaceView local):
 *   screenPos = screenCenter + (svPos - svCenter) * scale + offset
 *   svPos     = (screenPos  - screenCenter - offset) / scale + svCenter
 *
 * Returns `null` when the touch lands outside the visible content area (e.g. letterbox
 * bars), in which case the caller should not inject the touch.
 *
 * @param touchX   Raw X of the touch on the secondary display (pixels)
 * @param touchY   Raw Y of the touch on the secondary display (pixels)
 * @param screenW  Full width of the secondary display Compose surface (gestureBoxSize.width)
 * @param screenH  Full height of the secondary display Compose surface (gestureBoxSize.height)
 * @param sw       Width of the letterboxed content area = ScreenCaptureManager.surfaceWidth
 * @param sh       Height of the letterboxed content area = ScreenCaptureManager.surfaceHeight
 * @param scale    Current zoom scale (1.0 = no zoom)
 * @param offsetX  Current pan offset X (pixels)
 * @param offsetY  Current pan offset Y (pixels)
 * @return Pair(normalizedX, normalizedY) or null if out-of-bounds
 */
fun projectCoordinates(
    touchX: Float,
    touchY: Float,
    screenW: Float,
    screenH: Float,
    sw: Float,
    sh: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): Pair<Float, Float>? {
    if (sw <= 0f || sh <= 0f || scale <= 0f || screenW <= 0f || screenH <= 0f) return null
    // Screen-space center — this is where the SurfaceView is anchored (CENTER gravity).
    val screenCenterX = screenW / 2f
    val screenCenterY = screenH / 2f
    // SurfaceView-local pivot for the scale transform.
    val svCenterX = sw / 2f
    val svCenterY = sh / 2f
    // Invert: svPos = (screenPos - screenCenter - offset) / scale + svCenter
    val svX = (touchX - screenCenterX - offsetX) / scale + svCenterX
    val svY = (touchY - screenCenterY - offsetY) / scale + svCenterY
    val nx = svX / sw
    val ny = svY / sh
    if (nx !in 0f..1f || ny !in 0f..1f) return null
    return Pair(nx, ny)
}

/**
 * Projects a touch coordinate from the secondary screen container bounds
 * back through the cutout's destination bounds to the corresponding normalized
 * source crop coordinates on the primary display.
 *
 * @param touchX      Raw X of the touch on the secondary display (pixels)
 * @param touchY      Raw Y of the touch on the secondary display (pixels)
 * @param destLeft    Normalized or absolute X position of the cutout on the secondary display (pixels)
 * @param destTop     Normalized or absolute Y position of the cutout on the secondary display (pixels)
 * @param destWidth   Width of the cutout on the secondary display (pixels)
 * @param destHeight  Height of the cutout on the secondary display (pixels)
 * @param srcX        Crop X offset on the primary display [0, 1]
 * @param srcY        Crop Y offset on the primary display [0, 1]
 * @param srcWidth    Crop width on the primary display [0, 1]
 * @param srcHeight   Crop height on the primary display [0, 1]
 * @param clampToEdge If true, clamps the result to [0, 1]. Otherwise, returns null if out of bounds.
 * @return Pair(normalizedX, normalizedY) on the primary display, or null if out-of-bounds.
 */
fun projectCutoutCoordinates(
    touchX: Float,
    touchY: Float,
    destLeft: Float,
    destTop: Float,
    destWidth: Float,
    destHeight: Float,
    srcX: Float,
    srcY: Float,
    srcWidth: Float,
    srcHeight: Float,
    clampToEdge: Boolean = false,
    rotation: Int = 0,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
): Pair<Float, Float>? {
    if (destWidth <= 0f || destHeight <= 0f) return null

    val inBounds =
        touchX >= destLeft && touchX <= destLeft + destWidth &&
            touchY >= destTop && touchY <= destTop + destHeight

    if (!inBounds && !clampToEdge) return null

    val rx = ((touchX - destLeft) / destWidth).coerceIn(0f, 1f)
    val ry = ((touchY - destTop) / destHeight).coerceIn(0f, 1f)

    var normU =
        when (rotation) {
            ROTATION_90 -> ry
            ROTATION_180 -> 1f - rx
            ROTATION_270 -> 1f - ry
            else -> rx
        }

    var normV =
        when (rotation) {
            ROTATION_90 -> 1f - rx
            ROTATION_180 -> 1f - ry
            ROTATION_270 -> rx
            else -> ry
        }

    if (flipHorizontal) {
        normU = 1f - normU
    }
    if (flipVertical) {
        normV = 1f - normV
    }

    val px = srcX + normU * srcWidth
    val py = srcY + normV * srcHeight

    return Pair(px.coerceIn(0f, 1f), py.coerceIn(0f, 1f))
}

enum class ResizeHandle {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

data class ScreenCutoutGeometry(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

const val MIN_TOUCH_CUTOUT_SIZE = 0.05f
const val MIN_GAMEPAD_CUTOUT_SIZE = 0.01f
const val MIN_CUTOUT_SIZE = MIN_GAMEPAD_CUTOUT_SIZE

private fun intervalsOverlap(
    min1: Float,
    max1: Float,
    min2: Float,
    max2: Float,
): Boolean = min1 < max2 - OVERLAP_TOLERANCE && max1 > min2 + OVERLAP_TOLERANCE

private fun rectsOverlap(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    other: ScreenCutout,
): Boolean =
    intervalsOverlap(x, x + w, other.destX, other.destX + other.destWidth) &&
        intervalsOverlap(y, y + h, other.destY, other.destY + other.destHeight)

fun clampMoveX(
    originalX: Float,
    targetX: Float,
    y: Float,
    width: Float,
    height: Float,
    others: List<ScreenCutout>,
): Float {
    val maxX = (1f - width).coerceAtLeast(0f)
    val clampedTargetX = targetX.coerceIn(0f, maxX)
    if (clampedTargetX == originalX) return originalX

    var limitX = clampedTargetX
    val movingRight = clampedTargetX > originalX

    for (other in others) {
        if (!intervalsOverlap(y, y + height, other.destY, other.destY + other.destHeight)) continue

        if (movingRight) {
            if (other.destX >= originalX + width - OVERLAP_TOLERANCE) {
                limitX = minOf(limitX, other.destX - width)
            }
        } else {
            if (other.destX + other.destWidth <= originalX + OVERLAP_TOLERANCE) {
                limitX = maxOf(limitX, other.destX + other.destWidth)
            }
        }
    }
    return limitX.coerceIn(0f, maxX)
}

fun clampMoveY(
    originalY: Float,
    targetY: Float,
    x: Float,
    width: Float,
    height: Float,
    others: List<ScreenCutout>,
): Float {
    val maxY = (1f - height).coerceAtLeast(0f)
    val clampedTargetY = targetY.coerceIn(0f, maxY)
    if (clampedTargetY == originalY) return originalY

    var limitY = clampedTargetY
    val movingDown = clampedTargetY > originalY

    for (other in others) {
        if (!intervalsOverlap(x, x + width, other.destX, other.destX + other.destWidth)) continue

        if (movingDown) {
            if (other.destY >= originalY + height - OVERLAP_TOLERANCE) {
                limitY = minOf(limitY, other.destY - height)
            }
        } else {
            if (other.destY + other.destHeight <= originalY + OVERLAP_TOLERANCE) {
                limitY = maxOf(limitY, other.destY + other.destHeight)
            }
        }
    }
    return limitY.coerceIn(0f, maxY)
}

fun clampCutoutDrag(
    cutoutId: String,
    originalX: Float,
    originalY: Float,
    targetX: Float,
    targetY: Float,
    width: Float,
    height: Float,
    allCutouts: List<ScreenCutout>,
): Pair<Float, Float> {
    val others = allCutouts.filter { it.id != cutoutId }

    val maxX = (1f - width).coerceAtLeast(0f)
    val maxY = (1f - height).coerceAtLeast(0f)
    val clampedX = targetX.coerceIn(0f, maxX)
    val clampedY = targetY.coerceIn(0f, maxY)

    if (others.none { rectsOverlap(clampedX, clampedY, width, height, it) }) {
        return Pair(clampedX, clampedY)
    }

    // Slide X and Y using clamping
    val slideX = clampMoveX(originalX, clampedX, clampedY, width, height, others)
    val slideY = clampMoveY(originalY, clampedY, clampedX, width, height, others)

    val overlapsCand1 = others.any { rectsOverlap(slideX, clampedY, width, height, it) }
    val overlapsCand2 = others.any { rectsOverlap(clampedX, slideY, width, height, it) }

    if (!overlapsCand1 && !overlapsCand2) {
        val dist1 = abs(slideX - clampedX)
        val dist2 = abs(slideY - clampedY)
        return if (dist1 <= dist2) {
            Pair(slideX, clampedY)
        } else {
            Pair(clampedX, slideY)
        }
    } else if (!overlapsCand1) {
        return Pair(slideX, clampedY)
    } else if (!overlapsCand2) {
        return Pair(clampedX, slideY)
    }

    if (others.none { rectsOverlap(slideX, slideY, width, height, it) }) {
        return Pair(slideX, slideY)
    }

    val slideXOnly = clampMoveX(originalX, clampedX, originalY, width, height, others)
    if (others.none { rectsOverlap(slideXOnly, originalY, width, height, it) }) {
        return Pair(slideXOnly, originalY)
    }

    val slideYOnly = clampMoveY(originalY, clampedY, originalX, width, height, others)
    if (others.none { rectsOverlap(originalX, slideYOnly, width, height, it) }) {
        return Pair(originalX, slideYOnly)
    }

    return Pair(originalX, originalY)
}

fun adjustSourceCropToAspectRatio(
    cutout: ScreenCutout,
    screenW: Float,
    screenH: Float,
    srcW: Float,
    srcH: Float,
    baseSrcX: Float = cutout.srcX,
    baseSrcY: Float = cutout.srcY,
    baseSrcW: Float = cutout.srcWidth,
    baseSrcH: Float = cutout.srcHeight,
    minSize: Float = MIN_GAMEPAD_CUTOUT_SIZE,
): ScreenCutout {
    if (screenW <= 0f || screenH <= 0f || srcW <= 0f || srcH <= 0f ||
        cutout.destWidth <= 0f || cutout.destHeight <= 0f
    ) {
        return cutout
    }

    val rawTargetRatio = (cutout.destWidth * screenW) / (cutout.destHeight * screenH)
    if (rawTargetRatio <= 0f) return cutout
    val isQuarter = (cutout.rotation == ROTATION_90 || cutout.rotation == ROTATION_270)
    val targetRatio = if (isQuarter) (1f / rawTargetRatio) else rawTargetRatio
    val factor = targetRatio * (srcH / srcW)
    if (factor <= 0f) return cutout

    val safeBaseW = baseSrcW.coerceIn(minSize, 1f)
    val safeBaseH = baseSrcH.coerceIn(minSize, 1f)
    val centerX = baseSrcX + safeBaseW / 2f
    val centerY = baseSrcY + safeBaseH / 2f

    var candW: Float
    var candH: Float

    if (factor > safeBaseW / safeBaseH) {
        candW = safeBaseW
        candH = candW / factor
    } else {
        candH = safeBaseH
        candW = candH * factor
    }

    if (candH < minSize) {
        candH = minSize
        candW = (candH * factor).coerceIn(minSize, 1f)
    }
    if (candW < minSize) {
        candW = minSize
        candH = (candW / factor).coerceIn(minSize, 1f)
    }
    if (candW > 1f) {
        candW = 1f
        candH = (candW / factor).coerceIn(minSize, 1f)
    }
    if (candH > 1f) {
        candH = 1f
        candW = (candH * factor).coerceIn(minSize, 1f)
    }

    val newX = (centerX - candW / 2f).coerceIn(0f, (1f - candW).coerceAtLeast(0f))
    val newY = (centerY - candH / 2f).coerceIn(0f, (1f - candH).coerceAtLeast(0f))

    return cutout.copy(
        srcX = newX,
        srcY = newY,
        srcWidth = candW,
        srcHeight = candH,
    )
}

fun adjustDestSizeToAspectRatio(
    destX: Float,
    destY: Float,
    destWidth: Float,
    destHeight: Float,
    cropRatio: Float,
    screenW: Float,
    screenH: Float,
    minCutoutSize: Float = MIN_GAMEPAD_CUTOUT_SIZE,
    rotation: Int = 0,
): Pair<Float, Float> {
    if (screenW <= 0f || screenH <= 0f || cropRatio <= 0f) return Pair(destWidth, destHeight)

    val isQuarter = (rotation == ROTATION_90 || rotation == ROTATION_270)
    val effectiveCropRatio = if (isQuarter) (1f / cropRatio) else cropRatio

    val normRatio = effectiveCropRatio * (screenH / screenW)
    if (normRatio <= 0f) return Pair(destWidth, destHeight)

    val maxH = (1f - destY).coerceIn(minCutoutSize, 1f)
    val maxW = (1f - destX).coerceIn(minCutoutSize, 1f)

    var targetW = destWidth.coerceIn(minCutoutSize, maxW)
    var targetH = targetW / normRatio

    if (targetH > maxH) {
        targetH = maxH
        targetW = (targetH * normRatio).coerceIn(minCutoutSize, maxW)
    }

    if (targetH < minCutoutSize) {
        targetH = minCutoutSize
        targetW = (targetH * normRatio).coerceIn(minCutoutSize, maxW)
    }

    if (targetW > maxW) {
        targetW = maxW
        targetH = (targetW / normRatio).coerceIn(minCutoutSize, maxH)
    }

    if (targetW < minCutoutSize) {
        targetW = minCutoutSize
        targetH = (targetW / normRatio).coerceIn(minCutoutSize, maxH)
    }

    return Pair(targetW, targetH)
}

/**
 * Calculates new destination bounds for a cutout when rotating to [targetRotation].
 *
 * If rotating between landscape and portrait (0°/180° <-> 90°/270°), destination physical pixel
 * dimensions are swapped (converting normalized dimensions through [screenW] and [screenH])
 * while remaining centered around the cutout's current midpoint and clamped to screen bounds.
 *
 * @param cutout The cutout to rotate.
 * @param targetRotation Target rotation angle in degrees (0, 90, 180, 270).
 * @param allCutouts Sibling cutouts used for collision detection.
 * @param maxDimension Maximum normalized dimension (defaults to 1.0f).
 * @param screenW Secondary display surface width in physical pixels (used to preserve physical aspect ratio).
 * @param screenH Secondary display surface height in physical pixels (used to preserve physical aspect ratio).
 * @return The updated [ScreenCutout] if the rotated cutout fits without colliding with any
 * other cutout in [allCutouts], or `null` if the rotation is blocked by collision or boundaries.
 */
fun calculateRotatedCutoutBounds(
    cutout: ScreenCutout,
    targetRotation: Int,
    allCutouts: List<ScreenCutout>,
    maxDimension: Float = 1.0f,
    screenW: Float = 0f,
    screenH: Float = 0f,
): ScreenCutout? {
    val currentIsQuarter = (cutout.rotation == ROTATION_90 || cutout.rotation == ROTATION_270)
    val targetIsQuarter = (targetRotation == ROTATION_90 || targetRotation == ROTATION_270)
    val isSwappingDimensions = currentIsQuarter != targetIsQuarter

    val newW =
        if (!isSwappingDimensions) {
            cutout.destWidth
        } else if (screenW > 0f && screenH > 0f) {
            (cutout.destHeight * screenH) / screenW
        } else {
            cutout.destHeight
        }

    val newH =
        if (!isSwappingDimensions) {
            cutout.destHeight
        } else if (screenW > 0f && screenH > 0f) {
            (cutout.destWidth * screenW) / screenH
        } else {
            cutout.destWidth
        }

    if (newW > maxDimension || newH > maxDimension) {
        return null
    }

    val centerX = cutout.destX + cutout.destWidth / 2f
    val centerY = cutout.destY + cutout.destHeight / 2f

    val targetX = (centerX - newW / 2f).coerceIn(0f, (maxDimension - newW).coerceAtLeast(0f))
    val targetY = (centerY - newH / 2f).coerceIn(0f, (maxDimension - newH).coerceAtLeast(0f))

    val others = allCutouts.filter { it.id != cutout.id }
    val hasOverlap =
        others.any { other ->
            rectsOverlap(targetX, targetY, newW, newH, other)
        }

    if (hasOverlap) {
        return null
    }

    return cutout.copy(
        rotation = targetRotation,
        destX = targetX,
        destY = targetY,
        destWidth = newW,
        destHeight = newH,
    )
}

fun isCutoutGeometryValid(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    others: List<ScreenCutout>,
    minCutoutSize: Float = MIN_CUTOUT_SIZE,
): Boolean {
    if (x < -OVERLAP_TOLERANCE || y < -OVERLAP_TOLERANCE ||
        x + w > 1f + OVERLAP_TOLERANCE || y + h > 1f + OVERLAP_TOLERANCE
    ) {
        return false
    }
    if (w < minCutoutSize - OVERLAP_TOLERANCE || h < minCutoutSize - OVERLAP_TOLERANCE) {
        return false
    }
    return others.none { rectsOverlap(x, y, w, h, it) }
}

private fun isGeometryValid(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    others: List<ScreenCutout>,
    minCutoutSize: Float = MIN_TOUCH_CUTOUT_SIZE,
): Boolean = isCutoutGeometryValid(x, y, w, h, others, minCutoutSize)

fun getTargetGeometryWithAspectRatio(
    handle: ResizeHandle,
    originalX: Float,
    originalY: Float,
    originalWidth: Float,
    originalHeight: Float,
    targetX: Float,
    targetY: Float,
    targetWidth: Float,
    targetHeight: Float,
    cropRatio: Float,
    screenW: Float,
    screenH: Float,
    minCutoutSize: Float = MIN_TOUCH_CUTOUT_SIZE,
): ScreenCutoutGeometry {
    val dx = targetWidth - originalWidth
    val dy = targetHeight - originalHeight
    val normRatio = cropRatio * (screenH / screenW)

    val originalRight = originalX + originalWidth
    val originalBottom = originalY + originalHeight

    var finalW = targetWidth
    var finalH = targetHeight
    var finalX = targetX
    var finalY = targetY

    if (abs(dx) >= abs(dy * normRatio)) {
        finalW = targetWidth.coerceIn(minCutoutSize, 1f)
        finalH = finalW / normRatio
    } else {
        finalH = targetHeight.coerceIn(minCutoutSize, 1f)
        finalW = finalH * normRatio
    }

    when (handle) {
        ResizeHandle.TOP -> {
            finalX = originalX
            finalY = originalBottom - finalH
        }

        ResizeHandle.BOTTOM -> {
            finalX = originalX
            finalY = originalY
        }

        ResizeHandle.LEFT -> {
            finalX = originalRight - finalW
            finalY = originalY
        }

        ResizeHandle.RIGHT -> {
            finalX = originalX
            finalY = originalY
        }

        ResizeHandle.TOP_LEFT -> {
            finalX = originalRight - finalW
            finalY = originalBottom - finalH
        }

        ResizeHandle.TOP_RIGHT -> {
            finalX = originalX
            finalY = originalBottom - finalH
        }

        ResizeHandle.BOTTOM_LEFT -> {
            finalX = originalRight - finalW
            finalY = originalY
        }

        ResizeHandle.BOTTOM_RIGHT -> {
            finalX = originalX
            finalY = originalY
        }
    }

    return ScreenCutoutGeometry(finalX, finalY, finalW, finalH)
}

private fun resolveCornerCand(
    prevXOverlaps: Boolean,
    prevYOverlaps: Boolean,
    candHorizontal: Float,
    candVertical: Float,
    currentHorizontal: Float,
    currentVertical: Float,
    prevHorizontal: Float,
    prevVertical: Float,
): Pair<Float, Float> =
    if (prevYOverlaps && !prevXOverlaps) {
        Pair(candHorizontal, currentVertical)
    } else if (prevXOverlaps && !prevYOverlaps) {
        Pair(currentHorizontal, candVertical)
    } else {
        val distH = abs(candHorizontal - prevHorizontal) + abs(currentVertical - prevVertical)
        val distV = abs(currentHorizontal - prevHorizontal) + abs(candVertical - prevVertical)
        if (distH < distV) Pair(candHorizontal, currentVertical) else Pair(currentHorizontal, candVertical)
    }

fun clampCutoutResize(
    cutoutId: String,
    handle: ResizeHandle,
    originalX: Float,
    originalY: Float,
    originalWidth: Float,
    originalHeight: Float,
    targetX: Float,
    targetY: Float,
    targetWidth: Float,
    targetHeight: Float,
    allCutouts: List<ScreenCutout>,
    keepAspectRatio: Boolean = false,
    cropRatio: Float = 0f,
    screenW: Float = 0f,
    screenH: Float = 0f,
    minCutoutSize: Float = MIN_TOUCH_CUTOUT_SIZE,
): ScreenCutoutGeometry {
    val others = allCutouts.filter { it.id != cutoutId }

    if (keepAspectRatio && cropRatio > 0f && screenW > 0f && screenH > 0f) {
        val targetGeom =
            getTargetGeometryWithAspectRatio(
                handle = handle,
                originalX = originalX,
                originalY = originalY,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                targetX = targetX,
                targetY = targetY,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                cropRatio = cropRatio,
                screenW = screenW,
                screenH = screenH,
                minCutoutSize = minCutoutSize,
            )

        val origGeom = ScreenCutoutGeometry(originalX, originalY, originalWidth, originalHeight)

        var low = 0f
        var high = 1f
        var bestGeom = origGeom

        for (i in 0 until BINARY_SEARCH_STEPS) {
            val mid = (low + high) / 2f
            val w = originalWidth + mid * (targetGeom.w - originalWidth)
            val h = originalHeight + mid * (targetGeom.h - originalHeight)

            var x = originalX
            var y = originalY
            when (handle) {
                ResizeHandle.TOP -> {
                    x = originalX
                    y = (originalY + originalHeight) - h
                }

                ResizeHandle.BOTTOM -> {
                    x = originalX
                    y = originalY
                }

                ResizeHandle.LEFT -> {
                    x = (originalX + originalWidth) - w
                    y = originalY
                }

                ResizeHandle.RIGHT -> {
                    x = originalX
                    y = originalY
                }

                ResizeHandle.TOP_LEFT -> {
                    x = (originalX + originalWidth) - w
                    y = (originalY + originalHeight) - h
                }

                ResizeHandle.TOP_RIGHT -> {
                    x = originalX
                    y = (originalY + originalHeight) - h
                }

                ResizeHandle.BOTTOM_LEFT -> {
                    x = (originalX + originalWidth) - w
                    y = originalY
                }

                ResizeHandle.BOTTOM_RIGHT -> {
                    x = originalX
                    y = originalY
                }
            }

            if (isGeometryValid(x, y, w, h, others, minCutoutSize)) {
                bestGeom = ScreenCutoutGeometry(x, y, w, h)
                low = mid
            } else {
                high = mid
            }
        }
        return bestGeom
    }

    val prevCutout = allCutouts.find { it.id == cutoutId }
    val prevX = prevCutout?.destX ?: originalX
    val prevY = prevCutout?.destY ?: originalY
    val prevW = prevCutout?.destWidth ?: originalWidth
    val prevH = prevCutout?.destHeight ?: originalHeight
    val prevRight = prevX + prevW
    val prevBottom = prevY + prevH

    val clampedWidth = targetWidth.coerceIn(minCutoutSize, 1f)
    val clampedHeight = targetHeight.coerceIn(minCutoutSize, 1f)

    val originalRight = originalX + originalWidth
    val originalBottom = originalY + originalHeight

    var clampedX = targetX
    var clampedY = targetY
    var finalWidth = clampedWidth
    var finalHeight = clampedHeight
    when (handle) {
        ResizeHandle.TOP -> {
            val maxTop = (originalBottom - minCutoutSize).coerceAtLeast(0f)
            clampedY = clampedY.coerceIn(0f, maxTop)
            for (other in others) {
                val xOverlaps = intervalsOverlap(originalX, originalRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(clampedY, originalBottom, other.destY, other.destY + other.destHeight)
                if (xOverlaps && yOverlaps) {
                    clampedY = maxOf(clampedY, other.destY + other.destHeight)
                }
            }
            clampedY = clampedY.coerceIn(0f, maxTop)
            clampedX = originalX
            finalWidth = originalWidth
            finalHeight = originalBottom - clampedY
        }

        ResizeHandle.BOTTOM -> {
            val minBottom = (originalY + minCutoutSize).coerceAtMost(1f)
            var clampedBottom = (originalY + clampedHeight).coerceIn(minBottom, 1f)
            for (other in others) {
                val xOverlaps = intervalsOverlap(originalX, originalRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(originalY, clampedBottom, other.destY, other.destY + other.destHeight)
                if (xOverlaps && yOverlaps) {
                    clampedBottom = minOf(clampedBottom, other.destY)
                }
            }
            clampedBottom = clampedBottom.coerceIn(minBottom, 1f)
            clampedX = originalX
            clampedY = originalY
            finalWidth = originalWidth
            finalHeight = clampedBottom - originalY
        }

        ResizeHandle.LEFT -> {
            val maxLeft = (originalRight - minCutoutSize).coerceAtLeast(0f)
            clampedX = clampedX.coerceIn(0f, maxLeft)
            for (other in others) {
                val xOverlaps = intervalsOverlap(clampedX, originalRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(originalY, originalBottom, other.destY, other.destY + other.destHeight)
                if (xOverlaps && yOverlaps) {
                    clampedX = maxOf(clampedX, other.destX + other.destWidth)
                }
            }
            clampedX = clampedX.coerceIn(0f, maxLeft)
            clampedY = originalY
            finalWidth = originalRight - clampedX
            finalHeight = originalHeight
        }

        ResizeHandle.RIGHT -> {
            val minRight = (originalX + minCutoutSize).coerceAtMost(1f)
            var clampedRight = (originalX + clampedWidth).coerceIn(minRight, 1f)
            for (other in others) {
                val xOverlaps = intervalsOverlap(originalX, clampedRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(originalY, originalBottom, other.destY, other.destY + other.destHeight)
                if (xOverlaps && yOverlaps) {
                    clampedRight = minOf(clampedRight, other.destX)
                }
            }
            clampedRight = clampedRight.coerceIn(minRight, 1f)
            clampedX = originalX
            clampedY = originalY
            finalWidth = clampedRight - originalX
            finalHeight = originalHeight
        }

        ResizeHandle.TOP_LEFT -> {
            val maxLeft = (originalRight - minCutoutSize).coerceAtLeast(0f)
            val maxTop = (originalBottom - minCutoutSize).coerceAtLeast(0f)
            clampedX = clampedX.coerceIn(0f, maxLeft)
            clampedY = clampedY.coerceIn(0f, maxTop)

            for (other in others) {
                val xOverlaps = intervalsOverlap(clampedX, originalRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(clampedY, originalBottom, other.destY, other.destY + other.destHeight)

                if (xOverlaps && yOverlaps) {
                    val prevXOverlaps = intervalsOverlap(prevX, prevRight, other.destX, other.destX + other.destWidth)
                    val prevYOverlaps = intervalsOverlap(prevY, prevBottom, other.destY, other.destY + other.destHeight)
                    val (resX, resY) =
                        resolveCornerCand(
                            prevXOverlaps,
                            prevYOverlaps,
                            other.destX + other.destWidth,
                            other.destY + other.destHeight,
                            clampedX,
                            clampedY,
                            prevX,
                            prevY,
                        )
                    clampedX = resX
                    clampedY = resY
                }
            }
            clampedX = clampedX.coerceIn(0f, maxLeft)
            clampedY = clampedY.coerceIn(0f, maxTop)
            finalWidth = originalRight - clampedX
            finalHeight = originalBottom - clampedY
        }

        ResizeHandle.TOP_RIGHT -> {
            val minRight = (originalX + minCutoutSize).coerceAtMost(1f)
            val maxTop = (originalBottom - minCutoutSize).coerceAtLeast(0f)
            var clampedRight = (originalX + clampedWidth).coerceIn(minRight, 1f)
            clampedY = clampedY.coerceIn(0f, maxTop)

            for (other in others) {
                val xOverlaps = intervalsOverlap(originalX, clampedRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(clampedY, originalBottom, other.destY, other.destY + other.destHeight)

                if (xOverlaps && yOverlaps) {
                    val prevXOverlaps = intervalsOverlap(prevX, prevRight, other.destX, other.destX + other.destWidth)
                    val prevYOverlaps = intervalsOverlap(prevY, prevBottom, other.destY, other.destY + other.destHeight)
                    val (resRight, resY) =
                        resolveCornerCand(
                            prevXOverlaps,
                            prevYOverlaps,
                            other.destX,
                            other.destY + other.destHeight,
                            clampedRight,
                            clampedY,
                            prevRight,
                            prevY,
                        )
                    clampedRight = resRight
                    clampedY = resY
                }
            }
            clampedRight = clampedRight.coerceIn(minRight, 1f)
            clampedY = clampedY.coerceIn(0f, maxTop)
            clampedX = originalX
            finalWidth = clampedRight - originalX
            finalHeight = originalBottom - clampedY
        }

        ResizeHandle.BOTTOM_LEFT -> {
            val maxLeft = (originalRight - minCutoutSize).coerceAtLeast(0f)
            val minBottom = (originalY + minCutoutSize).coerceAtMost(1f)
            clampedX = clampedX.coerceIn(0f, maxLeft)
            var clampedBottom = (originalY + clampedHeight).coerceIn(minBottom, 1f)

            for (other in others) {
                val xOverlaps = intervalsOverlap(clampedX, originalRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(originalY, clampedBottom, other.destY, other.destY + other.destHeight)

                if (xOverlaps && yOverlaps) {
                    val prevXOverlaps = intervalsOverlap(prevX, prevRight, other.destX, other.destX + other.destWidth)
                    val prevYOverlaps = intervalsOverlap(prevY, prevBottom, other.destY, other.destY + other.destHeight)
                    val (resX, resBottom) =
                        resolveCornerCand(
                            prevXOverlaps,
                            prevYOverlaps,
                            other.destX + other.destWidth,
                            other.destY,
                            clampedX,
                            clampedBottom,
                            prevX,
                            prevBottom,
                        )
                    clampedX = resX
                    clampedBottom = resBottom
                }
            }
            clampedX = clampedX.coerceIn(0f, maxLeft)
            clampedBottom = clampedBottom.coerceIn(minBottom, 1f)
            clampedY = originalY
            finalWidth = originalRight - clampedX
            finalHeight = clampedBottom - originalY
        }

        ResizeHandle.BOTTOM_RIGHT -> {
            val minRight = (originalX + minCutoutSize).coerceAtMost(1f)
            val minBottom = (originalY + minCutoutSize).coerceAtMost(1f)
            var clampedRight = (originalX + clampedWidth).coerceIn(minRight, 1f)
            var clampedBottom = (originalY + clampedHeight).coerceIn(minBottom, 1f)

            for (other in others) {
                val xOverlaps = intervalsOverlap(originalX, clampedRight, other.destX, other.destX + other.destWidth)
                val yOverlaps = intervalsOverlap(originalY, clampedBottom, other.destY, other.destY + other.destHeight)

                if (xOverlaps && yOverlaps) {
                    val prevXOverlaps = intervalsOverlap(prevX, prevRight, other.destX, other.destX + other.destWidth)
                    val prevYOverlaps = intervalsOverlap(prevY, prevBottom, other.destY, other.destY + other.destHeight)
                    val (resRight, resBottom) =
                        resolveCornerCand(
                            prevXOverlaps,
                            prevYOverlaps,
                            other.destX,
                            other.destY,
                            clampedRight,
                            clampedBottom,
                            prevRight,
                            prevBottom,
                        )
                    clampedRight = resRight
                    clampedBottom = resBottom
                }
            }
            clampedRight = clampedRight.coerceIn(minRight, 1f)
            clampedBottom = clampedBottom.coerceIn(minBottom, 1f)
            clampedX = originalX
            clampedY = originalY
            finalWidth = clampedRight - originalX
            finalHeight = clampedBottom - originalY
        }
    }

    return ScreenCutoutGeometry(clampedX, clampedY, finalWidth, finalHeight)
}

/**
 * Bounds in normalized [0, 1] coordinates along with alternating step toggle states
 * for horizontal and vertical 1-pixel resizing.
 */
data class CutoutPixelBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val hToggle: Int,
    val vToggle: Int,
)

/**
 * Resizes cutout bounds 1 pixel at a time while holding R2:
 * - Direction Up (dy < 0): increases vertical size by 1 pixel, alternating top and bottom border.
 * - Direction Down (dy > 0): decreases vertical size by 1 pixel, alternating top and bottom border.
 * - Direction Right (dx > 0): increases horizontal size by 1 pixel, alternating right and left border.
 * - Direction Left (dx < 0): decreases horizontal size by 1 pixel, alternating right and left border.
 */
fun calculateResizedBounds(
    normX: Float,
    normY: Float,
    normW: Float,
    normH: Float,
    screenWidth: Float,
    screenHeight: Float,
    dx: Int,
    dy: Int,
    hToggle: Int = 0,
    vToggle: Int = 0,
    minSizeRatio: Float = MIN_GAMEPAD_CUTOUT_SIZE,
    others: List<ScreenCutout> = emptyList(),
): CutoutPixelBounds {
    if (screenWidth <= 0f || screenHeight <= 0f) {
        return CutoutPixelBounds(normX, normY, normW, normH, hToggle, vToggle)
    }

    var pxX = (normX * screenWidth).roundToInt()
    var pxY = (normY * screenHeight).roundToInt()
    var pxW = (normW * screenWidth).roundToInt()
    var pxH = (normH * screenHeight).roundToInt()

    val minW = (minSizeRatio * screenWidth).roundToInt().coerceAtLeast(MIN_RESIZE_PX)
    val minH = (minSizeRatio * screenHeight).roundToInt().coerceAtLeast(MIN_RESIZE_PX)
    val maxW = screenWidth.roundToInt()
    val maxH = screenHeight.roundToInt()

    fun isValid(
        testPxX: Int,
        testPxY: Int,
        testPxW: Int,
        testPxH: Int,
    ): Boolean {
        if (testPxX < 0 || testPxY < 0 || testPxX + testPxW > maxW || testPxY + testPxH > maxH) {
            return false
        }
        if (testPxW < minW && testPxW < pxW) {
            return false
        }
        if (testPxH < minH && testPxH < pxH) {
            return false
        }
        if (others.isEmpty()) {
            return true
        }
        val normTestX = testPxX.toFloat() / screenWidth
        val normTestY = testPxY.toFloat() / screenHeight
        val normTestW = testPxW.toFloat() / screenWidth
        val normTestH = testPxH.toFloat() / screenHeight
        return isCutoutGeometryValid(normTestX, normTestY, normTestW, normTestH, others, minSizeRatio)
    }

    var nextHToggle = hToggle
    var nextVToggle = vToggle

    // Horizontal resize
    repeat(abs(dx)) {
        if (dx > 0) { // Direction RIGHT: increase horizontal size by 1 px
            if (pxW < maxW) {
                if (nextHToggle == 0) {
                    // Try expand right border first
                    if (isValid(pxX, pxY, pxW + 1, pxH)) {
                        pxW += 1
                        nextHToggle = 1
                    } else if (isValid(pxX - 1, pxY, pxW + 1, pxH)) {
                        // Expand left instead if right is blocked
                        pxX -= 1
                        pxW += 1
                        nextHToggle = 0
                    }
                } else {
                    // Try expand left border first
                    if (isValid(pxX - 1, pxY, pxW + 1, pxH)) {
                        pxX -= 1
                        pxW += 1
                        nextHToggle = 0
                    } else if (isValid(pxX, pxY, pxW + 1, pxH)) {
                        // Expand right instead if left is blocked
                        pxW += 1
                        nextHToggle = 1
                    }
                }
            }
        } else if (dx < 0) { // Direction LEFT: decrease horizontal size by 1 px
            if (pxW > minW) {
                if (nextHToggle == 1) {
                    // Try shrink right border first
                    if (isValid(pxX, pxY, pxW - 1, pxH)) {
                        pxW -= 1
                        nextHToggle = 0
                    } else if (isValid(pxX + 1, pxY, pxW - 1, pxH)) {
                        pxX += 1
                        pxW -= 1
                        nextHToggle = 1
                    }
                } else {
                    // Try shrink left border first
                    if (isValid(pxX + 1, pxY, pxW - 1, pxH)) {
                        pxX += 1
                        pxW -= 1
                        nextHToggle = 1
                    } else if (isValid(pxX, pxY, pxW - 1, pxH)) {
                        pxW -= 1
                        nextHToggle = 0
                    }
                }
            }
        }
    }

    // Vertical resize
    repeat(abs(dy)) {
        if (dy < 0) { // Direction UP: increase vertical size by 1 px
            if (pxH < maxH) {
                if (nextVToggle == 0) {
                    // Try expand top border first
                    if (isValid(pxX, pxY - 1, pxW, pxH + 1)) {
                        pxY -= 1
                        pxH += 1
                        nextVToggle = 1
                    } else if (isValid(pxX, pxY, pxW, pxH + 1)) {
                        // Expand bottom instead if top is blocked
                        pxH += 1
                        nextVToggle = 0
                    }
                } else {
                    // Try expand bottom border first
                    if (isValid(pxX, pxY, pxW, pxH + 1)) {
                        pxH += 1
                        nextVToggle = 0
                    } else if (isValid(pxX, pxY - 1, pxW, pxH + 1)) {
                        // Expand top instead if bottom is blocked
                        pxY -= 1
                        pxH += 1
                        nextVToggle = 1
                    }
                }
            }
        } else if (dy > 0) { // Direction DOWN: decrease vertical size by 1 px
            if (pxH > minH) {
                if (nextVToggle == 1) {
                    // Try shrink top border first
                    if (isValid(pxX, pxY + 1, pxW, pxH - 1)) {
                        pxY += 1
                        pxH -= 1
                        nextVToggle = 0
                    } else if (isValid(pxX, pxY, pxW, pxH - 1)) {
                        pxH -= 1
                        nextVToggle = 1
                    }
                } else {
                    // Try shrink bottom border first
                    if (isValid(pxX, pxY, pxW, pxH - 1)) {
                        pxH -= 1
                        nextVToggle = 1
                    } else if (isValid(pxX, pxY + 1, pxW, pxH - 1)) {
                        pxY += 1
                        pxH -= 1
                        nextVToggle = 0
                    }
                }
            }
        }
    }

    val finalNormX = (pxX.toFloat() / screenWidth).coerceIn(0f, 1f)
    val finalNormY = (pxY.toFloat() / screenHeight).coerceIn(0f, 1f)
    val finalNormW = (pxW.toFloat() / screenWidth).coerceIn(0f, (1f - finalNormX).coerceAtLeast(0f))
    val finalNormH = (pxH.toFloat() / screenHeight).coerceIn(0f, (1f - finalNormY).coerceAtLeast(0f))

    return CutoutPixelBounds(
        x = finalNormX,
        y = finalNormY,
        width = finalNormW,
        height = finalNormH,
        hToggle = nextHToggle,
        vToggle = nextVToggle,
    )
}

/**
 * Clamps proportional resize of a crop rectangle on the primary display anchored at the opposite corner.
 * Used when aspect ratio is locked to BOTTOM (cutout aspect ratio is master).
 *
 * @param handle The active corner handle (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT).
 * @param originalX Starting normalized X of the crop rectangle.
 * @param originalY Starting normalized Y of the crop rectangle.
 * @param originalWidth Starting normalized width of the crop rectangle.
 * @param originalHeight Starting normalized height of the crop rectangle.
 * @param totalDx Accumulated drag delta in pixels along X.
 * @param totalDy Accumulated drag delta in pixels along Y.
 * @param topScreenW Width in pixels of the primary screen.
 * @param topScreenH Height in pixels of the primary screen.
 * @param cutoutRatio Physical aspect ratio of the follower/master cutout (cutoutWidthPx / cutoutHeightPx).
 * @param minSize Minimum normalized size.
 * @return The resulting clamped [ScreenCutoutGeometry].
 */
fun clampCropResizeProportional(
    handle: ResizeHandle,
    originalX: Float,
    originalY: Float,
    originalWidth: Float,
    originalHeight: Float,
    totalDx: Float,
    totalDy: Float,
    topScreenW: Float,
    topScreenH: Float,
    cutoutRatio: Float,
    minSize: Float = MIN_TOUCH_CUTOUT_SIZE,
): ScreenCutoutGeometry {
    if (topScreenW <= 0f || topScreenH <= 0f || cutoutRatio <= 0f) {
        return ScreenCutoutGeometry(originalX, originalY, originalWidth, originalHeight)
    }

    val normCropRatio = cutoutRatio * (topScreenH / topScreenW)
    if (normCropRatio <= 0f) {
        return ScreenCutoutGeometry(originalX, originalY, originalWidth, originalHeight)
    }

    val origRight = originalX + originalWidth
    val origBottom = originalY + originalHeight

    val rawW: Float
    val rawH: Float
    val maxW: Float
    val maxH: Float

    when (handle) {
        ResizeHandle.TOP_LEFT -> {
            rawW = originalWidth - totalDx / topScreenW
            rawH = originalHeight - totalDy / topScreenH
            maxW = origRight
            maxH = origBottom
        }

        ResizeHandle.TOP_RIGHT, ResizeHandle.TOP -> {
            rawW = originalWidth + totalDx / topScreenW
            rawH = originalHeight - totalDy / topScreenH
            maxW = 1f - originalX
            maxH = origBottom
        }

        ResizeHandle.BOTTOM_LEFT, ResizeHandle.LEFT -> {
            rawW = originalWidth - totalDx / topScreenW
            rawH = originalHeight + totalDy / topScreenH
            maxW = origRight
            maxH = 1f - originalY
        }

        ResizeHandle.BOTTOM_RIGHT, ResizeHandle.BOTTOM, ResizeHandle.RIGHT -> {
            rawW = originalWidth + totalDx / topScreenW
            rawH = originalHeight + totalDy / topScreenH
            maxW = 1f - originalX
            maxH = 1f - originalY
        }
    }

    val dw = rawW - originalWidth
    val dh = rawH - originalHeight

    val targetW: Float =
        if (abs(dw) >= abs(dh * normCropRatio)) {
            rawW
        } else {
            rawH * normCropRatio
        }

    val limitW = min(maxW, maxH * normCropRatio)
    val minW = min(limitW, max(minSize, minSize * normCropRatio))

    val finalW = targetW.coerceIn(minW, limitW)
    val finalH = finalW / normCropRatio

    val finalX: Float
    val finalY: Float

    when (handle) {
        ResizeHandle.TOP_LEFT -> {
            finalX = origRight - finalW
            finalY = origBottom - finalH
        }

        ResizeHandle.TOP_RIGHT, ResizeHandle.TOP -> {
            finalX = originalX
            finalY = origBottom - finalH
        }

        ResizeHandle.BOTTOM_LEFT, ResizeHandle.LEFT -> {
            finalX = origRight - finalW
            finalY = originalY
        }

        ResizeHandle.BOTTOM_RIGHT, ResizeHandle.BOTTOM, ResizeHandle.RIGHT -> {
            finalX = originalX
            finalY = originalY
        }
    }

    val clampedX = finalX.coerceIn(0f, 1f)
    val clampedY = finalY.coerceIn(0f, 1f)
    return ScreenCutoutGeometry(
        x = clampedX,
        y = clampedY,
        w = finalW.coerceIn(0f, (1f - clampedX).coerceAtLeast(0f)),
        h = finalH.coerceIn(0f, (1f - clampedY).coerceAtLeast(0f)),
    )
}

/**
 * Resizes cutout or crop bounds proportionally by 1 step while preserving the specified aspect ratio.
 * Used for gamepad D-pad resizing when aspect ratio is locked.
 *
 * @param normX Current normalized X position.
 * @param normY Current normalized Y position.
 * @param normW Current normalized width.
 * @param normH Current normalized height.
 * @param screenWidth Screen width in pixels.
 * @param screenHeight Screen height in pixels.
 * @param stepDelta Positive (+1) to expand, negative (-1) to shrink.
 * @param targetNormRatio The locked aspect ratio in normalized coordinates (w / h).
 * @param minSizeRatio Minimum size in normalized coordinates.
 * @param others Other cutouts on the display for collision detection (optional).
 * @return The updated [ScreenCutoutGeometry].
 */
fun calculateProportionalResizedBounds(
    normX: Float,
    normY: Float,
    normW: Float,
    normH: Float,
    screenWidth: Float,
    screenHeight: Float,
    stepDelta: Int,
    targetNormRatio: Float,
    minSizeRatio: Float = MIN_GAMEPAD_CUTOUT_SIZE,
    others: List<ScreenCutout> = emptyList(),
): ScreenCutoutGeometry {
    if (screenWidth <= 0f || screenHeight <= 0f || targetNormRatio <= 0f || stepDelta == 0) {
        return ScreenCutoutGeometry(normX, normY, normW, normH)
    }

    val stepW = if (targetNormRatio >= 1f) (1f / screenWidth) * targetNormRatio else (1f / screenWidth)
    val stepH = stepW / targetNormRatio

    val rawMinW = max(minSizeRatio, minSizeRatio * targetNormRatio)
    val rawMaxW = min(1f, targetNormRatio)
    val maxW = max(rawMaxW, minSizeRatio)
    val minW = min(rawMinW, maxW)
    val maxH = maxW / targetNormRatio
    val minH = minW / targetNormRatio

    val singleDelta = if (stepDelta > 0) 1 else -1
    var curW = normW
    var curX = normX
    var curY = normY

    repeat(abs(stepDelta)) {
        val targetW =
            if (singleDelta > 0) {
                when {
                    curW < minW -> minW
                    curW < maxW && curW + stepW > maxW -> maxW
                    else -> curW + stepW
                }
            } else {
                when {
                    curW > maxW -> maxW
                    curW > minW && curW - stepW < minW -> minW
                    else -> curW - stepW
                }
            }
        val targetH = targetW / targetNormRatio

        if (singleDelta > 0 && (targetW > maxW || targetH > maxH)) {
            return ScreenCutoutGeometry(curX, curY, curW, curW / targetNormRatio)
        }
        if (singleDelta < 0 && (targetW < minW || targetH < minH)) {
            return ScreenCutoutGeometry(curX, curY, curW, curW / targetNormRatio)
        }

        val centerX = curX + curW / 2f
        val centerY = curY + (curW / targetNormRatio) / 2f

        val rawX = centerX - targetW / 2f
        val rawY = centerY - targetH / 2f

        val clampedX = rawX.coerceIn(0f, (1f - targetW).coerceAtLeast(0f))
        val clampedY = rawY.coerceIn(0f, (1f - targetH).coerceAtLeast(0f))

        if (others.isNotEmpty() && !isCutoutGeometryValid(clampedX, clampedY, targetW, targetH, others, minSizeRatio)) {
            return ScreenCutoutGeometry(curX, curY, curW, curW / targetNormRatio)
        }

        curW = targetW
        curX = clampedX
        curY = clampedY
    }

    return ScreenCutoutGeometry(curX, curY, curW, curW / targetNormRatio)
}
