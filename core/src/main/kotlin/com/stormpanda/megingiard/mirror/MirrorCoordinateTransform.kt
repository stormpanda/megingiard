package com.stormpanda.megingiard.mirror

import kotlin.math.abs

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
        val verticalOverlap = y < other.destY + other.destHeight - 0.001f && y + height > other.destY + 0.001f
        if (!verticalOverlap) continue
        
        if (movingRight) {
            if (other.destX >= originalX + width - 0.001f) {
                limitX = minOf(limitX, other.destX - width)
            }
        } else {
            if (other.destX + other.destWidth <= originalX + 0.001f) {
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
        val horizontalOverlap = x < other.destX + other.destWidth - 0.001f && x + width > other.destX + 0.001f
        if (!horizontalOverlap) continue
        
        if (movingDown) {
            if (other.destY >= originalY + height - 0.001f) {
                limitY = minOf(limitY, other.destY - height)
            }
        } else {
            if (other.destY + other.destHeight <= originalY + 0.001f) {
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
        clampedX < other.destX + other.destWidth - 0.001f && clampedX + width > other.destX + 0.001f &&
        clampedY < other.destY + other.destHeight - 0.001f && clampedY + height > other.destY + 0.001f
    }
    if (!overlapsFull) {
        return Pair(clampedX, clampedY)
    }
    
    // Slide X and Y using clamping
    val slideX = clampMoveX(originalX, clampedX, clampedY, width, height, others)
    val slideY = clampMoveY(originalY, clampedY, clampedX, width, height, others)
    
    val overlapsBothSlide = others.any { other ->
        slideX < other.destX + other.destWidth - 0.001f && slideX + width > other.destX + 0.001f &&
        slideY < other.destY + other.destHeight - 0.001f && slideY + height > other.destY + 0.001f
    }
    if (!overlapsBothSlide) {
        return Pair(slideX, slideY)
    }
    
    // Fallback 1: Try X-only slide with original Y
    val slideXOnly = clampMoveX(originalX, clampedX, originalY, width, height, others)
    val overlapsXOnly = others.any { other ->
        slideXOnly < other.destX + other.destWidth - 0.001f && slideXOnly + width > other.destX + 0.001f &&
        originalY < other.destY + other.destHeight - 0.001f && originalY + height > other.destY + 0.001f
    }
    if (!overlapsXOnly) {
        return Pair(slideXOnly, originalY)
    }
    
    // Fallback 2: Try Y-only slide with original X
    val slideYOnly = clampMoveY(originalY, clampedY, originalX, width, height, others)
    val overlapsYOnly = others.any { other ->
        originalX < other.destX + other.destWidth - 0.001f && originalX + width > other.destX + 0.001f &&
        slideYOnly < other.destY + other.destHeight - 0.001f && slideYOnly + height > other.destY + 0.001f
    }
    if (!overlapsYOnly) {
        return Pair(originalX, slideYOnly)
    }
    
    return Pair(originalX, originalY)
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
    allCutouts: List<ScreenCutout>
): ScreenCutoutGeometry {
    val others = allCutouts.filter { it.id != cutoutId }
    
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
            val tx = targetX
            val ty = targetY
            clampedX = clampedX.coerceIn(0f, originalRight - MIN_CUTOUT_SIZE)
            clampedY = clampedY.coerceIn(0f, originalBottom - MIN_CUTOUT_SIZE)
            
            for (other in others) {
                val xOverlaps = tx < other.destX + other.destWidth - 0.001f && originalRight > other.destX + 0.001f
                val yOverlaps = ty < other.destY + other.destHeight - 0.001f && originalBottom > other.destY + 0.001f
                
                if (xOverlaps && yOverlaps) {
                    val expandingLeft = tx < originalX
                    val expandingUp = ty < originalY
                    
                    if (expandingLeft && expandingUp) {
                        val distX = abs(originalX - (other.destX + other.destWidth))
                        val distY = abs(originalY - (other.destY + other.destHeight))
                        if (distX < distY) {
                            clampedX = maxOf(clampedX, other.destX + other.destWidth)
                        } else {
                            clampedY = maxOf(clampedY, other.destY + other.destHeight)
                        }
                    } else if (expandingLeft) {
                        clampedX = maxOf(clampedX, other.destX + other.destWidth)
                    } else if (expandingUp) {
                        clampedY = maxOf(clampedY, other.destY + other.destHeight)
                    }
                }
            }
            clampedX = clampedX.coerceIn(0f, originalRight - MIN_CUTOUT_SIZE)
            clampedY = clampedY.coerceIn(0f, originalBottom - MIN_CUTOUT_SIZE)
            finalWidth = originalRight - clampedX
            finalHeight = originalBottom - clampedY
        }
        
        ResizeHandle.TOP_RIGHT -> {
            val tr = originalX + targetWidth
            val ty = targetY
            var clampedRight = (originalX + clampedWidth).coerceIn(originalX + MIN_CUTOUT_SIZE, 1f)
            clampedY = clampedY.coerceIn(0f, originalBottom - MIN_CUTOUT_SIZE)
            
            for (other in others) {
                val xOverlaps = originalX < other.destX + other.destWidth - 0.001f && tr > other.destX + 0.001f
                val yOverlaps = ty < other.destY + other.destHeight - 0.001f && originalBottom > other.destY + 0.001f
                
                if (xOverlaps && yOverlaps) {
                    val expandingRight = tr > originalRight
                    val expandingUp = ty < originalY
                    
                    if (expandingRight && expandingUp) {
                        val distX = abs(originalRight - other.destX)
                        val distY = abs(originalY - (other.destY + other.destHeight))
                        if (distX < distY) {
                            clampedRight = minOf(clampedRight, other.destX)
                        } else {
                            clampedY = maxOf(clampedY, other.destY + other.destHeight)
                        }
                    } else if (expandingRight) {
                        clampedRight = minOf(clampedRight, other.destX)
                    } else if (expandingUp) {
                        clampedY = maxOf(clampedY, other.destY + other.destHeight)
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
            val tx = targetX
            val tb = originalY + targetHeight
            clampedX = clampedX.coerceIn(0f, originalRight - MIN_CUTOUT_SIZE)
            var clampedBottom = (originalY + clampedHeight).coerceIn(originalY + MIN_CUTOUT_SIZE, 1f)
            
            for (other in others) {
                val xOverlaps = tx < other.destX + other.destWidth - 0.001f && originalRight > other.destX + 0.001f
                val yOverlaps = originalY < other.destY + other.destHeight - 0.001f && tb > other.destY + 0.001f
                
                if (xOverlaps && yOverlaps) {
                    val expandingLeft = tx < originalX
                    val expandingDown = tb > originalBottom
                    
                    if (expandingLeft && expandingDown) {
                        val distX = abs(originalX - (other.destX + other.destWidth))
                        val distY = abs(originalBottom - other.destY)
                        if (distX < distY) {
                            clampedX = maxOf(clampedX, other.destX + other.destWidth)
                        } else {
                            clampedBottom = minOf(clampedBottom, other.destY)
                        }
                    } else if (expandingLeft) {
                        clampedX = maxOf(clampedX, other.destX + other.destWidth)
                    } else if (expandingDown) {
                        clampedBottom = minOf(clampedBottom, other.destY)
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
            val tr = originalX + targetWidth
            val tb = originalY + targetHeight
            var clampedRight = (originalX + clampedWidth).coerceIn(originalX + MIN_CUTOUT_SIZE, 1f)
            var clampedBottom = (originalY + clampedHeight).coerceIn(originalY + MIN_CUTOUT_SIZE, 1f)
            
            for (other in others) {
                val xOverlaps = originalX < other.destX + other.destWidth - 0.001f && tr > other.destX + 0.001f
                val yOverlaps = originalY < other.destY + other.destHeight - 0.001f && tb > other.destY + 0.001f
                
                if (xOverlaps && yOverlaps) {
                    val expandingRight = tr > originalRight
                    val expandingDown = tb > originalBottom
                    
                    if (expandingRight && expandingDown) {
                        val distX = abs(originalRight - other.destX)
                        val distY = abs(originalBottom - other.destY)
                        if (distX < distY) {
                            clampedRight = minOf(clampedRight, other.destX)
                        } else {
                            clampedBottom = minOf(clampedBottom, other.destY)
                        }
                    } else if (expandingRight) {
                        clampedRight = minOf(clampedRight, other.destX)
                    } else if (expandingDown) {
                        clampedBottom = minOf(clampedBottom, other.destY)
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


