package com.stormpanda.megingiard.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt

private const val TAG = "CropSelectorOverlay"
private const val MIN_CROP_SIZE = 0.05f
private const val CS_SCRIM_ALPHA = 0.35f
private val CS_BORDER_WIDTH = 2.dp
private val CS_EDGE_HANDLE_LENGTH = 36.dp
private val CS_EDGE_HANDLE_THICKNESS = 6.dp
private val CS_EDGE_HANDLE_MARGIN = 6.dp
private val CS_EDGE_TOUCH_LENGTH = 56.dp
private val CS_EDGE_TOUCH_THICKNESS = 36.dp
private val CS_EDGE_HANDLE_CORNER = 3.dp

private val CS_CORNER_TOUCH_SIZE = 56.dp
private val CS_CORNER_HANDLE_MARGIN = 6.dp
private const val CS_ROTATION_TL = -45f
private const val CS_ROTATION_TR = 45f
private const val CS_ROTATION_BL = 45f
private const val CS_ROTATION_BR = -45f

private const val CS_FALLBACK_SRC_WIDTH = 1920f
private const val CS_FALLBACK_SRC_HEIGHT = 1080f
private const val CS_FALLBACK_SEC_WIDTH = 1240f
private const val CS_FALLBACK_SEC_HEIGHT = 1080f

@Composable
fun CropSelectorOverlay(
    cutoutId: String,
    onDismiss: () -> Unit = {},
) {
    AppLog.d(TAG, "CropSelectorOverlay composed for cutoutId=$cutoutId")
    val colors = LocalAppColors.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val layout = activeLayout ?: return
    val cutout = layout.mirrorCutouts.find { it.id == cutoutId } ?: return
    val currentCutoutState = rememberUpdatedState(cutout)
    val currentLayoutState = rememberUpdatedState(layout)
    val density = LocalDensity.current

    val captureSourceWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val captureSourceHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()
    val srcWidth = if (captureSourceWidth > 0) captureSourceWidth.toFloat() else CS_FALLBACK_SRC_WIDTH
    val srcHeight = if (captureSourceHeight > 0) captureSourceHeight.toFloat() else CS_FALLBACK_SRC_HEIGHT

    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val secScreenW = if (surfaceWidth > 0f) surfaceWidth else CS_FALLBACK_SEC_WIDTH
    val secScreenH = if (surfaceHeight > 0f) surfaceHeight else CS_FALLBACK_SEC_HEIGHT

    fun updateCutoutWithNewCrop(
        cutout: ScreenCutout,
        newX: Float,
        newY: Float,
        newW: Float,
        newH: Float,
        maxDestW: Float = 0f,
        maxDestH: Float = 0f,
    ): ScreenCutout {
        var updated = cutout.copy(srcX = newX, srcY = newY, srcWidth = newW, srcHeight = newH)
        if (updated.aspectRatioMode == AspectRatioMode.TOP) {
            val cropRatio = (newW * srcWidth) / (newH * srcHeight)
            val normRatio = cropRatio * (secScreenH / secScreenW)
            val (newDestW, newDestH) =
                adjustDestSizeToAspectRatio(
                    destX = updated.destX,
                    destY = updated.destY,
                    destWidth = updated.destWidth,
                    destHeight = updated.destHeight,
                    cropRatio = cropRatio,
                    screenW = secScreenW,
                    screenH = secScreenH,
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
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
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
            modifier =
                Modifier
                    .offset { IntOffset(0, 0) }
                    .size(
                        width = this@BoxWithConstraints.maxWidth,
                        height = with(density) { cropTop.toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = CS_SCRIM_ALPHA)),
        )
        // Left scrim
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, cropTop.roundToInt()) }
                    .size(
                        width = with(density) { cropLeft.toDp() },
                        height = with(density) { cropH.toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = CS_SCRIM_ALPHA)),
        )
        // Right scrim
        Box(
            modifier =
                Modifier
                    .offset { IntOffset((cropLeft + cropW).roundToInt(), cropTop.roundToInt()) }
                    .size(
                        width = this@BoxWithConstraints.maxWidth - with(density) { (cropLeft + cropW).toDp() },
                        height = with(density) { cropH.toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = CS_SCRIM_ALPHA)),
        )
        // Bottom scrim
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, (cropTop + cropH).roundToInt()) }
                    .size(
                        width = this@BoxWithConstraints.maxWidth,
                        height = this@BoxWithConstraints.maxHeight - with(density) { (cropTop + cropH).toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = CS_SCRIM_ALPHA)),
        )

        // 2. Crop rectangle border and drag area
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(cropLeft.roundToInt(), cropTop.roundToInt()) }
                    .size(
                        width = with(density) { cropW.toDp() },
                        height = with(density) { cropH.toDp() },
                    ).border(CS_BORDER_WIDTH, colors.accent.copy(alpha = 0.75f))
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

                                val updated =
                                    curLayout.mirrorCutouts.map {
                                        if (it.id == cutoutId) it.copy(srcX = newX, srcY = newY) else it
                                    }
                                MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                            },
                        )
                    },
        )

        // 3. Drag handles
        var dragStartX by remember(cutoutId) { mutableFloatStateOf(0f) }
        var dragStartY by remember(cutoutId) { mutableFloatStateOf(0f) }
        var dragStartW by remember(cutoutId) { mutableFloatStateOf(0f) }
        var dragStartH by remember(cutoutId) { mutableFloatStateOf(0f) }
        var gestureStartDestW by remember(cutoutId) { mutableFloatStateOf(0f) }
        var gestureStartDestH by remember(cutoutId) { mutableFloatStateOf(0f) }

        fun captureDragStart() {
            val curCutout = currentCutoutState.value
            dragStartX = curCutout.srcX
            dragStartY = curCutout.srcY
            dragStartW = curCutout.srcWidth
            dragStartH = curCutout.srcHeight
            gestureStartDestW = curCutout.destWidth
            gestureStartDestH = curCutout.destHeight
        }

        fun handleCornerDrag(
            handle: ResizeHandle,
            totalDx: Float,
            totalDy: Float,
        ) {
            val curLayout = currentLayoutState.value
            val curCutout = currentCutoutState.value
            val cutoutRatio = (curCutout.destWidth * secScreenW) / (curCutout.destHeight * secScreenH)
            val geom =
                clampCropResizeProportional(
                    handle = handle,
                    originalX = dragStartX,
                    originalY = dragStartY,
                    originalWidth = dragStartW,
                    originalHeight = dragStartH,
                    totalDx = totalDx,
                    totalDy = totalDy,
                    topScreenW = screenW,
                    topScreenH = screenH,
                    cutoutRatio = cutoutRatio,
                )
            val updated =
                curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) {
                        it.copy(srcX = geom.x, srcY = geom.y, srcWidth = geom.w, srcHeight = geom.h)
                    } else {
                        it
                    }
                }
            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
        }

        fun updateCropEdge(
            newX: Float,
            newY: Float,
            newW: Float,
            newH: Float,
        ) {
            val curLayout = currentLayoutState.value
            val updated =
                curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) {
                        updateCutoutWithNewCrop(it, newX, newY, newW, newH, gestureStartDestW, gestureStartDestH)
                    } else {
                        it
                    }
                }
            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
        }

        if (cutout.aspectRatioMode == AspectRatioMode.BOTTOM) {
            // ── CORNER Handles (Aspect ratio locked to BOTTOM) ───────────────
            val cornerMarginPx = with(density) { CS_CORNER_HANDLE_MARGIN.toPx() }
            val handleThicknessPx = with(density) { CS_EDGE_HANDLE_THICKNESS.toPx() }
            val cornerTouchSizePx = with(density) { CS_CORNER_TOUCH_SIZE.toPx() }

            val corners =
                listOf(
                    Triple(
                        ResizeHandle.TOP_LEFT,
                        cropLeft - cornerMarginPx - handleThicknessPx / 2f,
                        (cropTop - cornerMarginPx - handleThicknessPx / 2f) to CS_ROTATION_TL,
                    ),
                    Triple(
                        ResizeHandle.TOP_RIGHT,
                        cropLeft + cropW + cornerMarginPx + handleThicknessPx / 2f,
                        (cropTop - cornerMarginPx - handleThicknessPx / 2f) to CS_ROTATION_TR,
                    ),
                    Triple(
                        ResizeHandle.BOTTOM_LEFT,
                        cropLeft - cornerMarginPx - handleThicknessPx / 2f,
                        (cropTop + cropH + cornerMarginPx + handleThicknessPx / 2f) to CS_ROTATION_BL,
                    ),
                    Triple(
                        ResizeHandle.BOTTOM_RIGHT,
                        cropLeft + cropW + cornerMarginPx + handleThicknessPx / 2f,
                        (cropTop + cropH + cornerMarginPx + handleThicknessPx / 2f) to CS_ROTATION_BR,
                    ),
                )
            for ((handle, centerX, yAndRot) in corners) {
                val (centerY, rot) = yAndRot
                ResizeHandleView(
                    offset = IntOffset((centerX - cornerTouchSizePx / 2f).roundToInt(), (centerY - cornerTouchSizePx / 2f).roundToInt()),
                    touchWidth = CS_CORNER_TOUCH_SIZE,
                    touchHeight = CS_CORNER_TOUCH_SIZE,
                    handleWidth = CS_EDGE_HANDLE_LENGTH,
                    handleHeight = CS_EDGE_HANDLE_THICKNESS,
                    rotation = rot,
                    color = colors.accent,
                    onDragStart = { captureDragStart() },
                    onDrag = { totalDx, totalDy -> handleCornerDrag(handle, totalDx, totalDy) },
                )
            }
        } else {
            // ── EDGE Handles (FREE or TOP aspect ratio) ──────────────────────
            val marginPx = with(density) { CS_EDGE_HANDLE_MARGIN.toPx() }
            val handleThicknessPx = with(density) { CS_EDGE_HANDLE_THICKNESS.toPx() }
            val touchLengthPx = with(density) { CS_EDGE_TOUCH_LENGTH.toPx() }
            val touchThicknessPx = with(density) { CS_EDGE_TOUCH_THICKNESS.toPx() }

            // ── TOP Edge Handle (Horizontal Bar above Top edge) ───────────────
            val topCenterY = cropTop - marginPx - handleThicknessPx / 2f
            val topTouchX = (cropLeft + cropW / 2f) - touchLengthPx / 2f
            val topTouchY = topCenterY - touchThicknessPx / 2f
            ResizeHandleView(
                offset = IntOffset(topTouchX.roundToInt(), topTouchY.roundToInt()),
                touchWidth = CS_EDGE_TOUCH_LENGTH,
                touchHeight = CS_EDGE_TOUCH_THICKNESS,
                handleWidth = CS_EDGE_HANDLE_LENGTH,
                handleHeight = CS_EDGE_HANDLE_THICKNESS,
                color = colors.accent,
                onDragStart = { captureDragStart() },
                onDrag = { _, totalDy ->
                    val bottomEdge = dragStartY + dragStartH
                    val newY = (dragStartY + totalDy / screenH).coerceIn(0f, bottomEdge - MIN_CROP_SIZE)
                    updateCropEdge(dragStartX, newY, dragStartW, bottomEdge - newY)
                },
            )

            // ── BOTTOM Edge Handle (Horizontal Bar below Bottom edge) ──────────
            val bottomCenterY = cropTop + cropH + marginPx + handleThicknessPx / 2f
            val bottomTouchX = (cropLeft + cropW / 2f) - touchLengthPx / 2f
            val bottomTouchY = bottomCenterY - touchThicknessPx / 2f
            ResizeHandleView(
                offset = IntOffset(bottomTouchX.roundToInt(), bottomTouchY.roundToInt()),
                touchWidth = CS_EDGE_TOUCH_LENGTH,
                touchHeight = CS_EDGE_TOUCH_THICKNESS,
                handleWidth = CS_EDGE_HANDLE_LENGTH,
                handleHeight = CS_EDGE_HANDLE_THICKNESS,
                color = colors.accent,
                onDragStart = { captureDragStart() },
                onDrag = { _, totalDy ->
                    val newH = ((dragStartY + dragStartH + totalDy / screenH).coerceIn(dragStartY + MIN_CROP_SIZE, 1f)) - dragStartY
                    updateCropEdge(dragStartX, dragStartY, dragStartW, newH)
                },
            )

            // ── LEFT Edge Handle (Vertical Bar to the left of Left edge) ──────
            val leftCenterX = cropLeft - marginPx - handleThicknessPx / 2f
            val leftTouchX = leftCenterX - touchThicknessPx / 2f
            val leftTouchY = (cropTop + cropH / 2f) - touchLengthPx / 2f
            ResizeHandleView(
                offset = IntOffset(leftTouchX.roundToInt(), leftTouchY.roundToInt()),
                touchWidth = CS_EDGE_TOUCH_THICKNESS,
                touchHeight = CS_EDGE_TOUCH_LENGTH,
                handleWidth = CS_EDGE_HANDLE_THICKNESS,
                handleHeight = CS_EDGE_HANDLE_LENGTH,
                color = colors.accent,
                onDragStart = { captureDragStart() },
                onDrag = { totalDx, _ ->
                    val rightEdge = dragStartX + dragStartW
                    val newX = (dragStartX + totalDx / screenW).coerceIn(0f, rightEdge - MIN_CROP_SIZE)
                    updateCropEdge(newX, dragStartY, rightEdge - newX, dragStartH)
                },
            )

            // ── RIGHT Edge Handle (Vertical Bar to the right of Right edge) ───
            val rightCenterX = cropLeft + cropW + marginPx + handleThicknessPx / 2f
            val rightTouchX = rightCenterX - touchThicknessPx / 2f
            val rightTouchY = (cropTop + cropH / 2f) - touchLengthPx / 2f
            ResizeHandleView(
                offset = IntOffset(rightTouchX.roundToInt(), rightTouchY.roundToInt()),
                touchWidth = CS_EDGE_TOUCH_THICKNESS,
                touchHeight = CS_EDGE_TOUCH_LENGTH,
                handleWidth = CS_EDGE_HANDLE_THICKNESS,
                handleHeight = CS_EDGE_HANDLE_LENGTH,
                color = colors.accent,
                onDragStart = { captureDragStart() },
                onDrag = { totalDx, _ ->
                    val newW = ((dragStartX + dragStartW + totalDx / screenW).coerceIn(dragStartX + MIN_CROP_SIZE, 1f)) - dragStartX
                    updateCropEdge(dragStartX, dragStartY, newW, dragStartH)
                },
            )
        }
    }
}

@Composable
private fun ResizeHandleView(
    offset: IntOffset,
    touchWidth: Dp,
    touchHeight: Dp,
    handleWidth: Dp,
    handleHeight: Dp,
    color: Color,
    rotation: Float = 0f,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier =
            Modifier
                .offset { offset }
                .size(width = touchWidth, height = touchHeight)
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
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = handleWidth, height = handleHeight)
                    .graphicsLayer { rotationZ = rotation }
                    .background(color.copy(alpha = 0.75f), RoundedCornerShape(CS_EDGE_HANDLE_CORNER)),
        )
    }
}
