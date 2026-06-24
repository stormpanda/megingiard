package com.stormpanda.megingiard.mirror

import kotlin.math.abs

private const val OVERLAP_TOLERANCE: Float = 0.001f
private const val BINARY_SEARCH_STEPS = 10

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
    offsetY: Float
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
    clampToEdge: Boolean = false
): Pair<Float, Float>? {
    if (destWidth <= 0f || destHeight <= 0f) return null
    
    val inBounds = touchX >= destLeft && touchX <= destLeft + destWidth &&
                   touchY >= destTop && touchY <= destTop + destHeight
                   
    if (!inBounds && !clampToEdge) return null

    val rx = ((touchX - destLeft) / destWidth).coerceIn(0f, 1f)
    val ry = ((touchY - destTop) / destHeight).coerceIn(0f, 1f)
    
    val px = srcX + rx * srcWidth
    val py = srcY + ry * srcHeight
    
    return Pair(px.coerceIn(0f, 1f), py.coerceIn(0f, 1f))
}

enum class ResizeHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

data class ScreenCutoutGeometry(val x: Float, val y: Float, val w: Float, val h: Float)

private const val MIN_CUTOUT_SIZE = 0.05f

fun clampMoveX(originalX: Float, targetX: Float, y: Float, width: Float, height: Float, others: List<ScreenCutout>): Float {
    val clampedTargetX = targetX.coerceIn(0f, 1f - width)
    if (clampedTargetX == originalX) return originalX
    
    var limitX = clampedTargetX
    val movingRight = clampedTargetX > originalX
    
    for (other in others) {
        val verticalOverlap = y < other.destY + other.destHeight - OVERLAP_TOLERANCE && y + height > other.destY + OVERLAP_TOLERANCE
        if (!verticalOverlap) continue
        
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
    return limitX.coerceIn(0f, 1f - width)
}

fun clampMoveY(originalY: Float, targetY: Float, x: Float, width: Float, height: Float, others: List<ScreenCutout>): Float {
    val clampedTargetY = targetY.coerceIn(0f, 1f - height)
    if (clampedTargetY == originalY) return originalY
    
    var limitY = clampedTargetY
    val movingDown = clampedTargetY > originalY
    
    for (other in others) {
        val horizontalOverlap = x < other.destX + other.destWidth - OVERLAP_TOLERANCE && x + width > other.destX + OVERLAP_TOLERANCE
        if (!horizontalOverlap) continue
        
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
    return limitY.coerceIn(0f, 1f - height)
}

fun clampCutoutDrag(
    cutoutId: String,
    originalX: Float,
    originalY: Float,
    targetX: Float,
    targetY: Float,
    width: Float,
    height: Float,
    allCutouts: List<ScreenCutout>
): Pair<Float, Float> {
    val others = allCutouts.filter { it.id != cutoutId }
    
    val clampedX = targetX.coerceIn(0f, 1f - width)
    val clampedY = targetY.coerceIn(0f, 1f - height)
    
    val overlapsFull = others.any { other ->
        clampedX < other.destX + other.destWidth - OVERLAP_TOLERANCE && clampedX + width > other.destX + OVERLAP_TOLERANCE &&
        clampedY < other.destY + other.destHeight - OVERLAP_TOLERANCE && clampedY + height > other.destY + OVERLAP_TOLERANCE
    }
    if (!overlapsFull) {
        return Pair(clampedX, clampedY)
    }
    
    // Slide X and Y using clamping
    val slideX = clampMoveX(originalX, clampedX, clampedY, width, height, others)
    val slideY = clampMoveY(originalY, clampedY, clampedX, width, height, others)
    
    // Check Candidate 1: Clamp X, keep target Y
    val overlapsCand1 = others.any { other ->
        slideX < other.destX + other.destWidth - OVERLAP_TOLERANCE && slideX + width > other.destX + OVERLAP_TOLERANCE &&
        clampedY < other.destY + other.destHeight - OVERLAP_TOLERANCE && clampedY + height > other.destY + OVERLAP_TOLERANCE
    }
    
    // Check Candidate 2: Keep target X, clamp Y
    val overlapsCand2 = others.any { other ->
        clampedX < other.destX + other.destWidth - OVERLAP_TOLERANCE && clampedX + width > other.destX + OVERLAP_TOLERANCE &&
        slideY < other.destY + other.destHeight - OVERLAP_TOLERANCE && slideY + height > other.destY + OVERLAP_TOLERANCE
    }
    
    if (!overlapsCand1 && !overlapsCand2) {
        // Both are valid, pick the one closer to target (clampedX, clampedY)
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
    
    // Fallback to both clamped
    val overlapsBothSlide = others.any { other ->
        slideX < other.destX + other.destWidth - OVERLAP_TOLERANCE && slideX + width > other.destX + OVERLAP_TOLERANCE &&
        slideY < other.destY + other.destHeight - OVERLAP_TOLERANCE && slideY + height > other.destY + OVERLAP_TOLERANCE
    }
    if (!overlapsBothSlide) {
        return Pair(slideX, slideY)
    }
    
    // Fallback 1: Try X-only slide with original Y
    val slideXOnly = clampMoveX(originalX, clampedX, originalY, width, height, others)
    val overlapsXOnly = others.any { other ->
        slideXOnly < other.destX + other.destWidth - OVERLAP_TOLERANCE && slideXOnly + width > other.destX + OVERLAP_TOLERANCE &&
        originalY < other.destY + other.destHeight - OVERLAP_TOLERANCE && originalY + height > other.destY + OVERLAP_TOLERANCE
    }
    if (!overlapsXOnly) {
        return Pair(slideXOnly, originalY)
    }
    
    // Fallback 2: Try Y-only slide with original X
    val slideYOnly = clampMoveY(originalY, clampedY, originalX, width, height, others)
    val overlapsYOnly = others.any { other ->
        originalX < other.destX + other.destWidth - OVERLAP_TOLERANCE && originalX + width > other.destX + OVERLAP_TOLERANCE &&
        slideYOnly < other.destY + other.destHeight - OVERLAP_TOLERANCE && slideYOnly + height > other.destY + OVERLAP_TOLERANCE
    }
    if (!overlapsYOnly) {
        return Pair(originalX, slideYOnly)
    }
    
    return Pair(originalX, originalY)
}

fun adjustDestSizeToAspectRatio(
    destX: Float,
    destY: Float,
    destWidth: Float,
    destHeight: Float,
    cropRatio: Float,
    screenW: Float,
    screenH: Float
): Pair<Float, Float> {
    if (screenW <= 0f || screenH <= 0f || cropRatio <= 0f) return Pair(destWidth, destHeight)
    
    val normRatio = cropRatio * (screenH / screenW)
    var targetH = destWidth / normRatio
    var targetW = destWidth
    
    val maxH = 1f - destY
    if (targetH > maxH) {
        targetH = maxH
        targetW = targetH * normRatio
    }
    
    val maxW = 1f - destX
    if (targetW > maxW) {
        targetW = maxW
        targetH = targetW / normRatio
    }
    
    return Pair(targetW, targetH)
}

private fun isGeometryValid(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    others: List<ScreenCutout>
): Boolean {
    if (x < 0f || y < 0f || x + w > 1f || y + h > 1f) return false
    if (w < MIN_CUTOUT_SIZE || h < MIN_CUTOUT_SIZE) return false
    val overlaps = others.any { other ->
        x < other.destX + other.destWidth - OVERLAP_TOLERANCE && x + w > other.destX + OVERLAP_TOLERANCE &&
        y < other.destY + other.destHeight - OVERLAP_TOLERANCE && y + h > other.destY + OVERLAP_TOLERANCE
    }
    return !overlaps
}

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
    screenH: Float
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
        finalW = targetWidth.coerceIn(MIN_CUTOUT_SIZE, 1f)
        finalH = finalW / normRatio
    } else {
        finalH = targetHeight.coerceIn(MIN_CUTOUT_SIZE, 1f)
        finalW = finalH * normRatio
    }
    
    when (handle) {
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
    screenH: Float = 0f
): ScreenCutoutGeometry {
    val others = allCutouts.filter { it.id != cutoutId }
    
    if (keepAspectRatio && cropRatio > 0f && screenW > 0f && screenH > 0f) {
        val targetGeom = getTargetGeometryWithAspectRatio(
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
            screenH = screenH
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
            
            if (isGeometryValid(x, y, w, h, others)) {
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
    
    val clampedWidth = targetWidth.coerceIn(MIN_CUTOUT_SIZE, 1f)
    val clampedHeight = targetHeight.coerceIn(MIN_CUTOUT_SIZE, 1f)
    
    val originalRight = originalX + originalWidth
    val originalBottom = originalY + originalHeight
    
    var clampedX = targetX
    var clampedY = targetY
    var finalWidth = clampedWidth
    var finalHeight = clampedHeight
    when (handle) {
        ResizeHandle.TOP_LEFT -> {
            clampedX = clampedX.coerceIn(0f, originalRight - MIN_CUTOUT_SIZE)
            clampedY = clampedY.coerceIn(0f, originalBottom - MIN_CUTOUT_SIZE)
            
            for (other in others) {
                val xOverlaps = clampedX < other.destX + other.destWidth - OVERLAP_TOLERANCE && originalRight > other.destX + OVERLAP_TOLERANCE
                val yOverlaps = clampedY < other.destY + other.destHeight - OVERLAP_TOLERANCE && originalBottom > other.destY + OVERLAP_TOLERANCE
                
                if (xOverlaps && yOverlaps) {
                    val candX = other.destX + other.destWidth
                    val candY = other.destY + other.destHeight
                    
                    val prevXOverlaps = prevX < other.destX + other.destWidth - OVERLAP_TOLERANCE && prevRight > other.destX + OVERLAP_TOLERANCE
                    val prevYOverlaps = prevY < other.destY + other.destHeight - OVERLAP_TOLERANCE && prevBottom > other.destY + OVERLAP_TOLERANCE
                    
                    if (prevYOverlaps && !prevXOverlaps) {
                        clampedX = maxOf(clampedX, candX)
                    } else if (prevXOverlaps && !prevYOverlaps) {
                        clampedY = maxOf(clampedY, candY)
                    } else {
                        val distX = abs(candX - prevX) + abs(clampedY - prevY)
                        val distY = abs(clampedX - prevX) + abs(candY - prevY)
                        if (distX < distY) {
                            clampedX = maxOf(clampedX, candX)
                        } else {
                            clampedY = maxOf(clampedY, candY)
                        }
                    }
                }
            }
            clampedX = clampedX.coerceIn(0f, originalRight - MIN_CUTOUT_SIZE)
            clampedY = clampedY.coerceIn(0f, originalBottom - MIN_CUTOUT_SIZE)
            finalWidth = originalRight - clampedX
            finalHeight = originalBottom - clampedY
        }
        
        ResizeHandle.TOP_RIGHT -> {
            var clampedRight = (originalX + clampedWidth).coerceIn(originalX + MIN_CUTOUT_SIZE, 1f)
            clampedY = clampedY.coerceIn(0f, originalBottom - MIN_CUTOUT_SIZE)
            
            for (other in others) {
                val xOverlaps = originalX < other.destX + other.destWidth - OVERLAP_TOLERANCE && clampedRight > other.destX + OVERLAP_TOLERANCE
                val yOverlaps = clampedY < other.destY + other.destHeight - OVERLAP_TOLERANCE && originalBottom > other.destY + OVERLAP_TOLERANCE
                
                if (xOverlaps && yOverlaps) {
                    val candRight = other.destX
                    val candY = other.destY + other.destHeight
                    
                    val prevXOverlaps = prevX < other.destX + other.destWidth - OVERLAP_TOLERANCE && prevRight > other.destX + OVERLAP_TOLERANCE
                    val prevYOverlaps = prevY < other.destY + other.destHeight - OVERLAP_TOLERANCE && prevBottom > other.destY + OVERLAP_TOLERANCE
                    
                    if (prevYOverlaps && !prevXOverlaps) {
                        clampedRight = minOf(clampedRight, candRight)
                    } else if (prevXOverlaps && !prevYOverlaps) {
                        clampedY = maxOf(clampedY, candY)
                    } else {
                        val distX = abs(candRight - prevRight) + abs(clampedY - prevY)
                        val distY = abs(clampedRight - prevRight) + abs(candY - prevY)
                        if (distX < distY) {
                            clampedRight = minOf(clampedRight, candRight)
                        } else {
                            clampedY = maxOf(clampedY, candY)
                        }
                    }
                }
            }
            clampedRight = clampedRight.coerceIn(originalX + MIN_CUTOUT_SIZE, 1f)
            clampedY = clampedY.coerceIn(0f, originalBottom - MIN_CUTOUT_SIZE)
            clampedX = originalX
            finalWidth = clampedRight - originalX
            finalHeight = originalBottom - clampedY
        }
        
        ResizeHandle.BOTTOM_LEFT -> {
            clampedX = clampedX.coerceIn(0f, originalRight - MIN_CUTOUT_SIZE)
            var clampedBottom = (originalY + clampedHeight).coerceIn(originalY + MIN_CUTOUT_SIZE, 1f)
            
            for (other in others) {
                val xOverlaps = clampedX < other.destX + other.destWidth - OVERLAP_TOLERANCE && originalRight > other.destX + OVERLAP_TOLERANCE
                val yOverlaps = originalY < other.destY + other.destHeight - OVERLAP_TOLERANCE && clampedBottom > other.destY + OVERLAP_TOLERANCE
                
                if (xOverlaps && yOverlaps) {
                    val candX = other.destX + other.destWidth
                    val candBottom = other.destY
                    
                    val prevXOverlaps = prevX < other.destX + other.destWidth - OVERLAP_TOLERANCE && prevRight > other.destX + OVERLAP_TOLERANCE
                    val prevYOverlaps = prevY < other.destY + other.destHeight - OVERLAP_TOLERANCE && prevBottom > other.destY + OVERLAP_TOLERANCE
                    
                    if (prevYOverlaps && !prevXOverlaps) {
                        clampedX = maxOf(clampedX, candX)
                    } else if (prevXOverlaps && !prevYOverlaps) {
                        clampedBottom = minOf(clampedBottom, candBottom)
                    } else {
                        val distX = abs(candX - prevX) + abs(clampedBottom - prevBottom)
                        val distY = abs(clampedX - prevX) + abs(candBottom - prevBottom)
                        if (distX < distY) {
                            clampedX = maxOf(clampedX, candX)
                        } else {
                            clampedBottom = minOf(clampedBottom, candBottom)
                        }
                    }
                }
            }
            clampedX = clampedX.coerceIn(0f, originalRight - MIN_CUTOUT_SIZE)
            clampedBottom = clampedBottom.coerceIn(originalY + MIN_CUTOUT_SIZE, 1f)
            clampedY = originalY
            finalWidth = originalRight - clampedX
            finalHeight = clampedBottom - originalY
        }
        
        ResizeHandle.BOTTOM_RIGHT -> {
            var clampedRight = (originalX + clampedWidth).coerceIn(originalX + MIN_CUTOUT_SIZE, 1f)
            var clampedBottom = (originalY + clampedHeight).coerceIn(originalY + MIN_CUTOUT_SIZE, 1f)
            
            for (other in others) {
                val xOverlaps = originalX < other.destX + other.destWidth - OVERLAP_TOLERANCE && clampedRight > other.destX + OVERLAP_TOLERANCE
                val yOverlaps = originalY < other.destY + other.destHeight - OVERLAP_TOLERANCE && clampedBottom > other.destY + OVERLAP_TOLERANCE
                
                if (xOverlaps && yOverlaps) {
                    val candRight = other.destX
                    val candBottom = other.destY
                    
                    val prevXOverlaps = prevX < other.destX + other.destWidth - OVERLAP_TOLERANCE && prevRight > other.destX + OVERLAP_TOLERANCE
                    val prevYOverlaps = prevY < other.destY + other.destHeight - OVERLAP_TOLERANCE && prevBottom > other.destY + OVERLAP_TOLERANCE
                    
                    if (prevYOverlaps && !prevXOverlaps) {
                        clampedRight = minOf(clampedRight, candRight)
                    } else if (prevXOverlaps && !prevYOverlaps) {
                        clampedBottom = minOf(clampedBottom, candBottom)
                    } else {
                        val distX = abs(candRight - prevRight) + abs(clampedBottom - prevBottom)
                        val distY = abs(clampedRight - prevRight) + abs(candBottom - prevBottom)
                        if (distX < distY) {
                            clampedRight = minOf(clampedRight, candRight)
                        } else {
                            clampedBottom = minOf(clampedBottom, candBottom)
                        }
                    }
                }
            }
            clampedRight = clampedRight.coerceIn(originalX + MIN_CUTOUT_SIZE, 1f)
            clampedBottom = clampedBottom.coerceIn(originalY + MIN_CUTOUT_SIZE, 1f)
            clampedX = originalX
            clampedY = originalY
            finalWidth = clampedRight - originalX
            finalHeight = clampedBottom - originalY
        }
    }
    
    return ScreenCutoutGeometry(clampedX, clampedY, finalWidth, finalHeight)
}


