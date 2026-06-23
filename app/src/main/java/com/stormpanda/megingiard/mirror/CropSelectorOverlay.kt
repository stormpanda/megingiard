package com.stormpanda.megingiard.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.adjustDestSizeToAspectRatio
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "CropSelectorOverlay"
private const val MIN_CROP_SIZE = 0.05f
private val CS_HANDLE_SIZE = 24.dp
private val CS_BORDER_WIDTH = 1.dp
private val CS_CARD_SHADOW = 8.dp
private val CS_CARD_CORNER = 12.dp
private val CS_BOTTOM_PADDING = 32.dp
private val CS_CONTAINER_BORDER = 1.dp
private val CS_ROW_PADDING_V = 4.dp
private val CS_ROW_PADDING_E = 8.dp
private val CS_DRAG_HANDLE_W = 36.dp
private val CS_DRAG_HANDLE_H = 32.dp
private val CS_DRAG_HANDLE_ICON_SIZE = 20.dp
private val CS_DRAG_HANDLE_START_PADDING = 40.dp
private val CS_SPACING_8 = 8.dp
private val CS_BUTTON_CORNER = 4.dp
private val CS_BUTTON_PADDING_H = 8.dp
private val CS_BUTTON_PADDING_V = 6.dp
private val CS_BUTTON_HEIGHT = 32.dp
private val CS_BUTTON_ICON_SIZE = 18.dp
private val CS_INDICATOR_CORNER = 4.dp

@Composable
fun CropSelectorOverlay(
    cutoutId: String,
    onDismiss: () -> Unit
) {
    AppLog.d(TAG, "CropSelectorOverlay composed for cutoutId=$cutoutId")
    val colors = LocalAppColors.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val layout = activeLayout ?: return
    val cutout = layout.mirrorCutouts.find { it.id == cutoutId } ?: return
    val initialCutout = remember(cutoutId) { cutout }
    val currentCutoutState = rememberUpdatedState(cutout)
    val currentLayoutState = rememberUpdatedState(layout)
    val density = LocalDensity.current

    val onCancel = {
        val curLayout = currentLayoutState.value
        val updated = curLayout.mirrorCutouts.map {
            if (it.id == cutoutId) {
                it.copy(
                    srcX = initialCutout.srcX,
                    srcY = initialCutout.srcY,
                    srcWidth = initialCutout.srcWidth,
                    srcHeight = initialCutout.srcHeight,
                    destWidth = initialCutout.destWidth,
                    destHeight = initialCutout.destHeight
                )
            } else it
        }
        MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
        onDismiss()
    }

    val captureSourceWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val captureSourceHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()
    val srcWidth = if (captureSourceWidth > 0) captureSourceWidth.toFloat() else 1920f
    val srcHeight = if (captureSourceHeight > 0) captureSourceHeight.toFloat() else 1080f
    
    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val secScreenW = if (surfaceWidth > 0f) surfaceWidth else 1280f
    val secScreenH = if (surfaceHeight > 0f) surfaceHeight else 960f

    fun updateCutoutWithNewCrop(
        cutout: ScreenCutout,
        newX: Float,
        newY: Float,
        newW: Float,
        newH: Float,
        maxDestW: Float = 0f,
        maxDestH: Float = 0f
    ): ScreenCutout {
        var updated = cutout.copy(srcX = newX, srcY = newY, srcWidth = newW, srcHeight = newH)
        if (updated.aspectRatioMode == AspectRatioMode.TOP) {
            val cropRatio = (newW * srcWidth) / (newH * srcHeight)
            val normRatio = cropRatio * (secScreenH / secScreenW)
            val (newDestW, newDestH) = adjustDestSizeToAspectRatio(
                destX = updated.destX,
                destY = updated.destY,
                destWidth = updated.destWidth,
                destHeight = updated.destHeight,
                cropRatio = cropRatio,
                screenW = secScreenW,
                screenH = secScreenH
            )
            
            var finalW = newDestW
            var finalH = newDestH
            
            if (maxDestW > 0f && maxDestH > 0f) {
                val limitW = minOf(maxDestW, 1f - updated.destX)
                val limitH = minOf(maxDestH, 1f - updated.destY)
                finalW = minOf(limitW, limitH * normRatio)
                finalH = minOf(limitH, limitW / normRatio)
            }
            
            updated = updated.copy(destWidth = finalW, destHeight = finalH)
        }
        return updated
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        if (screenW <= 0f || screenH <= 0f) return@BoxWithConstraints

        val cropLeft = cutout.srcX * screenW
        val cropTop = cutout.srcY * screenH
        val cropW = cutout.srcWidth * screenW
        val cropH = cutout.srcHeight * screenH

        // 1. Semi-transparent scrim rects surrounding the crop region
        // Top scrim
        Box(
            modifier = Modifier
                .offset { IntOffset(0, 0) }
                .size(
                    width = this@BoxWithConstraints.maxWidth,
                    height = with(density) { cropTop.toDp() }
                )
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
        )
        // Left scrim
        Box(
            modifier = Modifier
                .offset { IntOffset(0, cropTop.roundToInt()) }
                .size(
                    width = with(density) { cropLeft.toDp() },
                    height = with(density) { cropH.toDp() }
                )
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
        )
        // Right scrim
        Box(
            modifier = Modifier
                .offset { IntOffset((cropLeft + cropW).roundToInt(), cropTop.roundToInt()) }
                .size(
                    width = this@BoxWithConstraints.maxWidth - with(density) { (cropLeft + cropW).toDp() },
                    height = with(density) { cropH.toDp() }
                )
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
        )
        // Bottom scrim
        Box(
            modifier = Modifier
                .offset { IntOffset(0, (cropTop + cropH).roundToInt()) }
                .size(
                    width = this@BoxWithConstraints.maxWidth,
                    height = this@BoxWithConstraints.maxHeight - with(density) { (cropTop + cropH).toDp() }
                )
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
        )

        val handleSizePx = with(density) { CS_HANDLE_SIZE.toPx() }

        // 2. Crop rectangle border and drag area
        Box(
            modifier = Modifier
                .offset { IntOffset(cropLeft.roundToInt(), cropTop.roundToInt()) }
                .size(
                    width = with(density) { cropW.toDp() },
                    height = with(density) { cropH.toDp() }
                )
                .border(CS_BORDER_WIDTH, colors.accent.copy(alpha = 0.5f))
                .pointerInput(cutoutId) {
                    var dragStartX = 0f
                    var dragStartY = 0f
                    var accumulatedX = 0f
                    var accumulatedY = 0f
                    detectDragGestures(
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.srcX
                            dragStartY = curCutout.srcY
                            accumulatedX = 0f
                            accumulatedY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y
                            val newX = (dragStartX + accumulatedX / screenW).coerceIn(0f, 1f - curCutout.srcWidth)
                            val newY = (dragStartY + accumulatedY / screenH).coerceIn(0f, 1f - curCutout.srcHeight)
                            
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == cutoutId) it.copy(srcX = newX, srcY = newY) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )
                }
        )

        // 3. Corner resize handles
        var dragStartX by remember(cutoutId) { mutableStateOf(0f) }
        var dragStartY by remember(cutoutId) { mutableStateOf(0f) }
        var dragStartW by remember(cutoutId) { mutableStateOf(0f) }
        var dragStartH by remember(cutoutId) { mutableStateOf(0f) }
        var gestureStartDestW by remember(cutoutId) { mutableStateOf(0f) }
        var gestureStartDestH by remember(cutoutId) { mutableStateOf(0f) }

        // Top-Left handle
        ResizeHandleView(
            offset = IntOffset(
                cropLeft.roundToInt(),
                cropTop.roundToInt()
            ),
            color = colors.accent,
            onDragStart = {
                val curCutout = currentCutoutState.value
                dragStartX = curCutout.srcX
                dragStartY = curCutout.srcY
                dragStartW = curCutout.srcWidth
                dragStartH = curCutout.srcHeight
                gestureStartDestW = curCutout.destWidth
                gestureStartDestH = curCutout.destHeight
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val curCutout = currentCutoutState.value
                val rightEdge = dragStartX + dragStartW
                val bottomEdge = dragStartY + dragStartH
                
                val geom = if (curCutout.aspectRatioMode == AspectRatioMode.BOTTOM) {
                    val targetRatio = (curCutout.destWidth * secScreenW) / (curCutout.destHeight * secScreenH)
                    val targetWidth = dragStartW - totalDx / screenW
                    val targetHeight = dragStartH - totalDy / screenH
                    adjustCropResizeToAspectRatio(
                        handle = CropResizeHandle.TOP_LEFT,
                        originalX = dragStartX,
                        originalY = dragStartY,
                        originalWidth = dragStartW,
                        originalHeight = dragStartH,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        targetRatio = targetRatio,
                        srcW = srcWidth,
                        srcH = srcHeight
                    )
                } else {
                    val newX = (dragStartX + totalDx / screenW).coerceIn(0f, rightEdge - MIN_CROP_SIZE)
                    val newW = rightEdge - newX
                    val newY = (dragStartY + totalDy / screenH).coerceIn(0f, bottomEdge - MIN_CROP_SIZE)
                    val newH = bottomEdge - newY
                    ScreenCutoutGeometry(newX, newY, newW, newH)
                }
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) updateCutoutWithNewCrop(it, geom.x, geom.y, geom.w, geom.h, gestureStartDestW, gestureStartDestH) else it
                }
                MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
            }
        )

        // Top-Right handle
        ResizeHandleView(
            offset = IntOffset(
                (cropLeft + cropW - handleSizePx).roundToInt(),
                cropTop.roundToInt()
            ),
            color = colors.accent,
            onDragStart = {
                val curCutout = currentCutoutState.value
                dragStartX = curCutout.srcX
                dragStartY = curCutout.srcY
                dragStartW = curCutout.srcWidth
                dragStartH = curCutout.srcHeight
                gestureStartDestW = curCutout.destWidth
                gestureStartDestH = curCutout.destHeight
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val curCutout = currentCutoutState.value
                val bottomEdge = dragStartY + dragStartH
                
                val geom = if (curCutout.aspectRatioMode == AspectRatioMode.BOTTOM) {
                    val targetRatio = (curCutout.destWidth * secScreenW) / (curCutout.destHeight * secScreenH)
                    val targetWidth = dragStartW + totalDx / screenW
                    val targetHeight = dragStartH - totalDy / screenH
                    adjustCropResizeToAspectRatio(
                        handle = CropResizeHandle.TOP_RIGHT,
                        originalX = dragStartX,
                        originalY = dragStartY,
                        originalWidth = dragStartW,
                        originalHeight = dragStartH,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        targetRatio = targetRatio,
                        srcW = srcWidth,
                        srcH = srcHeight
                    )
                } else {
                    val newW = (dragStartW + totalDx / screenW).coerceIn(MIN_CROP_SIZE, 1f - dragStartX)
                    val newY = (dragStartY + totalDy / screenH).coerceIn(0f, bottomEdge - MIN_CROP_SIZE)
                    val newH = bottomEdge - newY
                    ScreenCutoutGeometry(dragStartX, newY, newW, newH)
                }
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) updateCutoutWithNewCrop(it, geom.x, geom.y, geom.w, geom.h, gestureStartDestW, gestureStartDestH) else it
                }
                MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
            }
        )

        // Bottom-Left handle
        ResizeHandleView(
            offset = IntOffset(
                cropLeft.roundToInt(),
                (cropTop + cropH - handleSizePx).roundToInt()
            ),
            color = colors.accent,
            onDragStart = {
                val curCutout = currentCutoutState.value
                dragStartX = curCutout.srcX
                dragStartY = curCutout.srcY
                dragStartW = curCutout.srcWidth
                dragStartH = curCutout.srcHeight
                gestureStartDestW = curCutout.destWidth
                gestureStartDestH = curCutout.destHeight
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val curCutout = currentCutoutState.value
                val rightEdge = dragStartX + dragStartW
                
                val geom = if (curCutout.aspectRatioMode == AspectRatioMode.BOTTOM) {
                    val targetRatio = (curCutout.destWidth * secScreenW) / (curCutout.destHeight * secScreenH)
                    val targetWidth = dragStartW - totalDx / screenW
                    val targetHeight = dragStartH + totalDy / screenH
                    adjustCropResizeToAspectRatio(
                        handle = CropResizeHandle.BOTTOM_LEFT,
                        originalX = dragStartX,
                        originalY = dragStartY,
                        originalWidth = dragStartW,
                        originalHeight = dragStartH,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        targetRatio = targetRatio,
                        srcW = srcWidth,
                        srcH = srcHeight
                    )
                } else {
                    val newX = (dragStartX + totalDx / screenW).coerceIn(0f, rightEdge - MIN_CROP_SIZE)
                    val newW = rightEdge - newX
                    val newH = (dragStartH + totalDy / screenH).coerceIn(MIN_CROP_SIZE, 1f - dragStartY)
                    ScreenCutoutGeometry(newX, dragStartY, newW, newH)
                }
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) updateCutoutWithNewCrop(it, geom.x, geom.y, geom.w, geom.h, gestureStartDestW, gestureStartDestH) else it
                }
                MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
            }
        )

        // Bottom-Right handle
        ResizeHandleView(
            offset = IntOffset(
                (cropLeft + cropW - handleSizePx).roundToInt(),
                (cropTop + cropH - handleSizePx).roundToInt()
            ),
            color = colors.accent,
            onDragStart = {
                val curCutout = currentCutoutState.value
                dragStartX = curCutout.srcX
                dragStartY = curCutout.srcY
                dragStartW = curCutout.srcWidth
                dragStartH = curCutout.srcHeight
                gestureStartDestW = curCutout.destWidth
                gestureStartDestH = curCutout.destHeight
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val curCutout = currentCutoutState.value
                
                val geom = if (curCutout.aspectRatioMode == AspectRatioMode.BOTTOM) {
                    val targetRatio = (curCutout.destWidth * secScreenW) / (curCutout.destHeight * secScreenH)
                    val targetWidth = dragStartW + totalDx / screenW
                    val targetHeight = dragStartH + totalDy / screenH
                    adjustCropResizeToAspectRatio(
                        handle = CropResizeHandle.BOTTOM_RIGHT,
                        originalX = dragStartX,
                        originalY = dragStartY,
                        originalWidth = dragStartW,
                        originalHeight = dragStartH,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        targetRatio = targetRatio,
                        srcW = srcWidth,
                        srcH = srcHeight
                    )
                } else {
                    val newW = (dragStartW + totalDx / screenW).coerceIn(MIN_CROP_SIZE, 1f - dragStartX)
                    val newH = (dragStartH + totalDy / screenH).coerceIn(MIN_CROP_SIZE, 1f - dragStartY)
                    ScreenCutoutGeometry(dragStartX, dragStartY, newW, newH)
                }
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) updateCutoutWithNewCrop(it, geom.x, geom.y, geom.w, geom.h, gestureStartDestW, gestureStartDestH) else it
                }
                MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
            }
        )

        var toolbarOffset by remember { mutableStateOf(IntOffset.Zero) }

        // 4. Control panel card
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { toolbarOffset }
                .padding(bottom = CS_BOTTOM_PADDING)
                .shadow(CS_CARD_SHADOW, RoundedCornerShape(CS_CARD_CORNER)),
            color = colors.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(CS_CARD_CORNER),
            border = androidx.compose.foundation.BorderStroke(CS_CONTAINER_BORDER, colors.controlOverlayBorder)
        ) {
            Box(
                modifier = Modifier.padding(top = CS_ROW_PADDING_V, bottom = CS_ROW_PADDING_V, start = CS_ROW_PADDING_V, end = CS_ROW_PADDING_E)
            ) {
                // Drag handle at top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(width = CS_DRAG_HANDLE_W, height = CS_DRAG_HANDLE_H)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                toolbarOffset = IntOffset(
                                    x = toolbarOffset.x + dragAmount.x.roundToInt(),
                                    y = toolbarOffset.y + dragAmount.y.roundToInt()
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = stringResource(R.string.cd_drag_toolbar),
                        tint = colors.onSurfaceSecondary,
                        modifier = Modifier.size(CS_DRAG_HANDLE_ICON_SIZE)
                    )
                }

                // Row of buttons
                Row(
                    modifier = Modifier.padding(start = CS_DRAG_HANDLE_START_PADDING), // clear drag handle (36dp + 4dp space)
                    horizontalArrangement = Arrangement.spacedBy(CS_SPACING_8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel button (X)
                    ToolbarIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.mirror_crop_editor_cancel),
                        color = colors.error,
                        onClick = onCancel
                    )

                    // Done button (Checkmark)
                    ToolbarIconButton(
                        icon = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.mirror_crop_editor_done),
                        color = colors.accent,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(CS_BUTTON_CORNER),
        contentPadding = PaddingValues(horizontal = CS_BUTTON_PADDING_H, vertical = CS_BUTTON_PADDING_V),
        modifier = Modifier.height(CS_BUTTON_HEIGHT)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.onAccent,
            modifier = Modifier.size(CS_BUTTON_ICON_SIZE)
        )
    }
}

@Composable
private fun ResizeHandleView(
    offset: IntOffset,
    color: Color,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .offset { offset }
            .size(CS_HANDLE_SIZE)
            .background(color.copy(alpha = 0.5f), RoundedCornerShape(CS_INDICATOR_CORNER))
            .pointerInput(Unit) {
                var accumulatedX = 0f
                var accumulatedY = 0f
                detectDragGestures(
                    onDragStart = {
                        accumulatedX = 0f
                        accumulatedY = 0f
                        currentOnDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedX += dragAmount.x
                        accumulatedY += dragAmount.y
                        currentOnDrag(accumulatedX, accumulatedY)
                    }
                )
            }
    )
}

private enum class CropResizeHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private fun adjustCropResizeToAspectRatio(
    handle: CropResizeHandle,
    originalX: Float,
    originalY: Float,
    originalWidth: Float,
    originalHeight: Float,
    targetWidth: Float,
    targetHeight: Float,
    targetRatio: Float,
    srcW: Float,
    srcH: Float
): ScreenCutoutGeometry {
    val normRatio = targetRatio * (srcH / srcW)

    val dx = targetWidth - originalWidth
    val dy = targetHeight - originalHeight

    var finalW = targetWidth
    var finalH = targetHeight

    if (abs(dx) >= abs(dy * normRatio)) {
        finalW = targetWidth.coerceIn(MIN_CROP_SIZE, 1f)
        finalH = finalW / normRatio
    } else {
        finalH = targetHeight.coerceIn(MIN_CROP_SIZE, 1f)
        finalW = finalH * normRatio
    }

    val originalRight = originalX + originalWidth
    val originalBottom = originalY + originalHeight

    var finalX = originalX
    var finalY = originalY

    when (handle) {
        CropResizeHandle.TOP_LEFT -> {
            finalX = originalRight - finalW
            finalY = originalBottom - finalH
        }
        CropResizeHandle.TOP_RIGHT -> {
            finalX = originalX
            finalY = originalBottom - finalH
        }
        CropResizeHandle.BOTTOM_LEFT -> {
            finalX = originalRight - finalW
            finalY = originalY
        }
        CropResizeHandle.BOTTOM_RIGHT -> {
            finalX = originalX
            finalY = originalY
        }
    }

    var scale = 1f

    if (finalX < 0f) {
        val maxW = originalRight
        scale = min(scale, maxW / finalW)
    }
    if (finalX + finalW > 1f) {
        val maxW = 1f - originalX
        scale = min(scale, maxW / finalW)
    }

    if (finalY < 0f) {
        val maxH = originalBottom
        scale = min(scale, maxH / finalH)
    }
    if (finalY + finalH > 1f) {
        val maxH = 1f - originalY
        scale = min(scale, maxH / finalH)
    }

    if (scale < 1f) {
        finalW *= scale
        finalH *= scale

        when (handle) {
            CropResizeHandle.TOP_LEFT -> {
                finalX = originalRight - finalW
                finalY = originalBottom - finalH
            }
            CropResizeHandle.TOP_RIGHT -> {
                finalX = originalX
                finalY = originalBottom - finalH
            }
            CropResizeHandle.BOTTOM_LEFT -> {
                finalX = originalRight - finalW
                finalY = originalY
            }
            CropResizeHandle.BOTTOM_RIGHT -> {
                finalX = originalX
                finalY = originalY
            }
        }
    }

    finalX = finalX.coerceIn(0f, 1f - finalW)
    finalY = finalY.coerceIn(0f, 1f - finalH)

    return ScreenCutoutGeometry(finalX, finalY, finalW, finalH)
}
