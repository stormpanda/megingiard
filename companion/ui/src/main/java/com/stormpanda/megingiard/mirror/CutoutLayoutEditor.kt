package com.stormpanda.megingiard.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "CutoutLayoutEditor"
private val CLE_BORDER_WIDTH = 1.dp
private val CLE_SELECTED_BORDER_WIDTH = 2.dp
private val CLE_EDGE_HANDLE_LENGTH = 36.dp
private val CLE_EDGE_HANDLE_THICKNESS = 6.dp
private val CLE_EDGE_HANDLE_MARGIN = 6.dp
private val CLE_EDGE_TOUCH_LENGTH = 56.dp
private val CLE_EDGE_TOUCH_THICKNESS = 36.dp
private val CLE_EDGE_HANDLE_CORNER = 3.dp

private val CLE_CORNER_TOUCH_SIZE = 56.dp
private val CLE_CORNER_HANDLE_MARGIN = 6.dp
private const val CLE_ROTATION_TL = -45f
private const val CLE_ROTATION_TR = 45f
private const val CLE_ROTATION_BL = 45f
private const val CLE_ROTATION_BR = -45f

private val CLE_RECT_CORNER = 4.dp
private const val CLE_UNSELECTED_BG_ALPHA = 0.05f
private const val CLE_UNSELECTED_BORDER_ALPHA = 0.15f
private const val CLE_SELECTED_BORDER_ALPHA = 0.75f

@Composable
fun CutoutLayoutEditor() {
    val colors = LocalAppColors.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val layout = activeLayout ?: return

    val selectedCutoutId by AppStateManager.selectedCutoutId.collectAsState()
    val density = LocalDensity.current
    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val captureSourceWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val captureSourceHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()
    val srcWidth = if (captureSourceWidth > 0) captureSourceWidth.toFloat() else 1920f
    val srcHeight = if (captureSourceHeight > 0) captureSourceHeight.toFloat() else 1080f

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        if (containerW <= 0f || containerH <= 0f) return@BoxWithConstraints

        val screenW = if (surfaceWidth > 0f) surfaceWidth else containerW
        val screenH = if (surfaceHeight > 0f) surfaceHeight else containerH

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(
                        width = with(density) { screenW.toDp() },
                        height = with(density) { screenH.toDp() },
                    ),
        ) {
            // ── Multi-Cutout Arrangement Mode ──────────────────────────────────────
            // Draw all active cutout destinations
            for (cutout in layout.mirrorCutouts) {
                val currentCutoutState = rememberUpdatedState(cutout)
                val currentLayoutState = rememberUpdatedState(layout)
                val destLeft = cutout.destX * screenW
                val destTop = cutout.destY * screenH
                val destW = cutout.destWidth * screenW
                val destH = cutout.destHeight * screenH
                val isSelected = cutout.id == selectedCutoutId

                // Render destination bounding box
                val isCircle = cutout.shape == CutoutShape.CIRCLE
                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(destLeft.roundToInt(), destTop.roundToInt()) }
                            .size(
                                width = with(density) { destW.toDp() },
                                height = with(density) { destH.toDp() },
                            ).clickable {
                                AppStateManager.setSelectedCutoutId(cutout.id)
                            }.pointerInput(cutout.id) {
                                var dragStartX = 0f
                                var dragStartY = 0f
                                var accumulatedX = 0f
                                var accumulatedY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        val curCutout = currentCutoutState.value
                                        dragStartX = curCutout.destX
                                        dragStartY = curCutout.destY
                                        accumulatedX = 0f
                                        accumulatedY = 0f
                                        AppLog.d(TAG, "Drag start cutout '${curCutout.name}' at (${curCutout.destX}, ${curCutout.destY})")
                                        AppStateManager.setSelectedCutoutId(curCutout.id)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val curLayout = currentLayoutState.value
                                        val curCutout = currentCutoutState.value
                                        accumulatedX += dragAmount.x
                                        accumulatedY += dragAmount.y
                                        val targetX = dragStartX + accumulatedX / screenW
                                        val targetY = dragStartY + accumulatedY / screenH

                                        val (clampedX, clampedY) =
                                            clampCutoutDrag(
                                                cutoutId = curCutout.id,
                                                originalX = curCutout.destX,
                                                originalY = curCutout.destY,
                                                targetX = targetX,
                                                targetY = targetY,
                                                width = curCutout.destWidth,
                                                height = curCutout.destHeight,
                                                allCutouts = curLayout.mirrorCutouts,
                                            )

                                        if (clampedX != targetX || clampedY != targetY) {
                                            AppLog.d(
                                                TAG,
                                                "Drag clamped '${curCutout.name}': target=($targetX, $targetY) -> clamped=($clampedX, $clampedY)",
                                            )
                                        }

                                        val updated =
                                            curLayout.mirrorCutouts.map {
                                                if (it.id == curCutout.id) it.copy(destX = clampedX, destY = clampedY) else it
                                            }
                                        MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                                    },
                                )
                            },
                ) {
                    if (isCircle) {
                        val diameterDp = with(density) { min(destW, destH).toDp() }
                        if (isSelected) {
                            // Show collision rectangle bounding box in unselected style
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            color = Color.White.copy(alpha = CLE_UNSELECTED_BG_ALPHA),
                                            shape = RoundedCornerShape(CLE_RECT_CORNER),
                                        ).border(
                                            width = CLE_BORDER_WIDTH,
                                            color = Color.White.copy(alpha = CLE_UNSELECTED_BORDER_ALPHA),
                                            shape = RoundedCornerShape(CLE_RECT_CORNER),
                                        ),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .size(diameterDp)
                                    .background(
                                        color =
                                            if (isSelected) {
                                                Color.Transparent
                                            } else {
                                                Color.White.copy(alpha = CLE_UNSELECTED_BG_ALPHA)
                                            },
                                        shape = CircleShape,
                                    ).border(
                                        width = if (isSelected) CLE_SELECTED_BORDER_WIDTH else CLE_BORDER_WIDTH,
                                        color =
                                            if (isSelected) {
                                                colors.accent.copy(alpha = CLE_SELECTED_BORDER_ALPHA)
                                            } else {
                                                Color.White.copy(alpha = CLE_UNSELECTED_BORDER_ALPHA)
                                            },
                                        shape = CircleShape,
                                    ),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        color =
                                            if (isSelected) {
                                                Color.Transparent
                                            } else {
                                                Color.White.copy(alpha = CLE_UNSELECTED_BG_ALPHA)
                                            },
                                        shape = RoundedCornerShape(CLE_RECT_CORNER),
                                    ).border(
                                        width = if (isSelected) CLE_SELECTED_BORDER_WIDTH else CLE_BORDER_WIDTH,
                                        color =
                                            if (isSelected) {
                                                colors.accent.copy(alpha = CLE_SELECTED_BORDER_ALPHA)
                                            } else {
                                                Color.White.copy(alpha = CLE_UNSELECTED_BORDER_ALPHA)
                                            },
                                        shape = RoundedCornerShape(CLE_RECT_CORNER),
                                    ),
                        )
                    }
                    Text(
                        text = cutout.name.ifBlank { "Cutout" },
                        color = if (isSelected) colors.accent else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Show drag handles if selected
                if (isSelected) {
                    var dragStartX by remember(cutout.id) { mutableFloatStateOf(0f) }
                    var dragStartY by remember(cutout.id) { mutableFloatStateOf(0f) }
                    var dragStartW by remember(cutout.id) { mutableFloatStateOf(0f) }
                    var dragStartH by remember(cutout.id) { mutableFloatStateOf(0f) }
                    var dragStartSrcX by remember(cutout.id) { mutableFloatStateOf(0f) }
                    var dragStartSrcY by remember(cutout.id) { mutableFloatStateOf(0f) }
                    var dragStartSrcW by remember(cutout.id) { mutableFloatStateOf(0f) }
                    var dragStartSrcH by remember(cutout.id) { mutableFloatStateOf(0f) }

                    fun captureDragStart() {
                        val curCutout = currentCutoutState.value
                        dragStartX = curCutout.destX
                        dragStartY = curCutout.destY
                        dragStartW = curCutout.destWidth
                        dragStartH = curCutout.destHeight
                        dragStartSrcX = curCutout.srcX
                        dragStartSrcY = curCutout.srcY
                        dragStartSrcW = curCutout.srcWidth
                        dragStartSrcH = curCutout.srcHeight
                    }

                    fun handleEdgeDrag(
                        handle: ResizeHandle,
                        totalDx: Float,
                        totalDy: Float,
                    ) {
                        val curLayout = currentLayoutState.value
                        val curCutout = currentCutoutState.value
                        val targetX =
                            when (handle) {
                                ResizeHandle.LEFT -> dragStartX + totalDx / screenW
                                else -> dragStartX
                            }
                        val targetY =
                            when (handle) {
                                ResizeHandle.TOP -> dragStartY + totalDy / screenH
                                else -> dragStartY
                            }
                        val targetWidth =
                            when (handle) {
                                ResizeHandle.LEFT -> dragStartW - totalDx / screenW
                                ResizeHandle.RIGHT -> dragStartW + totalDx / screenW
                                else -> dragStartW
                            }
                        val targetHeight =
                            when (handle) {
                                ResizeHandle.TOP -> dragStartH - totalDy / screenH
                                ResizeHandle.BOTTOM -> dragStartH + totalDy / screenH
                                else -> dragStartH
                            }
                        val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                        val geom =
                            clampCutoutResize(
                                cutoutId = curCutout.id,
                                handle = handle,
                                originalX = dragStartX,
                                originalY = dragStartY,
                                originalWidth = dragStartW,
                                originalHeight = dragStartH,
                                targetX = targetX,
                                targetY = targetY,
                                targetWidth = targetWidth,
                                targetHeight = targetHeight,
                                allCutouts = curLayout.mirrorCutouts,
                                keepAspectRatio = false,
                                cropRatio = cropRatio,
                                screenW = screenW,
                                screenH = screenH,
                            )
                        val updated =
                            curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) {
                                    val next = it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h)
                                    if (next.aspectRatioMode == AspectRatioMode.BOTTOM) {
                                        adjustSourceCropToAspectRatio(
                                            next,
                                            screenW = screenW,
                                            screenH = screenH,
                                            srcW = srcWidth,
                                            srcH = srcHeight,
                                            baseSrcX = dragStartSrcX,
                                            baseSrcY = dragStartSrcY,
                                            baseSrcW = dragStartSrcW,
                                            baseSrcH = dragStartSrcH,
                                        )
                                    } else {
                                        next
                                    }
                                } else {
                                    it
                                }
                            }
                        MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                    }

                    fun handleCornerDrag(
                        handle: ResizeHandle,
                        totalDx: Float,
                        totalDy: Float,
                    ) {
                        val curLayout = currentLayoutState.value
                        val curCutout = currentCutoutState.value
                        val targetX =
                            when (handle) {
                                ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> dragStartX + totalDx / screenW
                                else -> dragStartX
                            }
                        val targetY =
                            when (handle) {
                                ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> dragStartY + totalDy / screenH
                                else -> dragStartY
                            }
                        val targetWidth =
                            when (handle) {
                                ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> dragStartW - totalDx / screenW
                                ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> dragStartW + totalDx / screenW
                                else -> dragStartW
                            }
                        val targetHeight =
                            when (handle) {
                                ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> dragStartH - totalDy / screenH
                                ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> dragStartH + totalDy / screenH
                                else -> dragStartH
                            }
                        val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                        val geom =
                            clampCutoutResize(
                                cutoutId = curCutout.id,
                                handle = handle,
                                originalX = dragStartX,
                                originalY = dragStartY,
                                originalWidth = dragStartW,
                                originalHeight = dragStartH,
                                targetX = targetX,
                                targetY = targetY,
                                targetWidth = targetWidth,
                                targetHeight = targetHeight,
                                allCutouts = curLayout.mirrorCutouts,
                                keepAspectRatio = true,
                                cropRatio = cropRatio,
                                screenW = screenW,
                                screenH = screenH,
                            )
                        val updated =
                            curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) {
                                    it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h)
                                } else {
                                    it
                                }
                            }
                        MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                    }

                    if (cutout.aspectRatioMode == AspectRatioMode.TOP) {
                        // ── CORNER Handles (Aspect ratio locked to TOP) ──────────────────
                        val cornerMarginPx = with(density) { CLE_CORNER_HANDLE_MARGIN.toPx() }
                        val handleThicknessPx = with(density) { CLE_EDGE_HANDLE_THICKNESS.toPx() }
                        val cornerTouchSizePx = with(density) { CLE_CORNER_TOUCH_SIZE.toPx() }

                        val corners =
                            listOf(
                                CornerHandleDef(
                                    destLeft - cornerMarginPx - handleThicknessPx / 2f,
                                    destTop - cornerMarginPx - handleThicknessPx / 2f,
                                    CLE_ROTATION_TL,
                                    ResizeHandle.TOP_LEFT,
                                ),
                                CornerHandleDef(
                                    destLeft + destW + cornerMarginPx + handleThicknessPx / 2f,
                                    destTop - cornerMarginPx - handleThicknessPx / 2f,
                                    CLE_ROTATION_TR,
                                    ResizeHandle.TOP_RIGHT,
                                ),
                                CornerHandleDef(
                                    destLeft - cornerMarginPx - handleThicknessPx / 2f,
                                    destTop + destH + cornerMarginPx + handleThicknessPx / 2f,
                                    CLE_ROTATION_BL,
                                    ResizeHandle.BOTTOM_LEFT,
                                ),
                                CornerHandleDef(
                                    destLeft + destW + cornerMarginPx + handleThicknessPx / 2f,
                                    destTop + destH + cornerMarginPx + handleThicknessPx / 2f,
                                    CLE_ROTATION_BR,
                                    ResizeHandle.BOTTOM_RIGHT,
                                ),
                            )

                        corners.forEach { def ->
                            ResizeHandleView(
                                offset =
                                    IntOffset(
                                        (def.centerX - cornerTouchSizePx / 2f).roundToInt(),
                                        (def.centerY - cornerTouchSizePx / 2f).roundToInt(),
                                    ),
                                touchWidth = CLE_CORNER_TOUCH_SIZE,
                                touchHeight = CLE_CORNER_TOUCH_SIZE,
                                handleWidth = CLE_EDGE_HANDLE_LENGTH,
                                handleHeight = CLE_EDGE_HANDLE_THICKNESS,
                                rotation = def.rotation,
                                color = colors.accent,
                                onDragStart = { captureDragStart() },
                                onDrag = { totalDx, totalDy -> handleCornerDrag(def.handle, totalDx, totalDy) },
                            )
                        }
                    } else {
                        // ── EDGE Handles (FREE or BOTTOM aspect ratio) ───────────────────
                        val marginPx = with(density) { CLE_EDGE_HANDLE_MARGIN.toPx() }
                        val handleThicknessPx = with(density) { CLE_EDGE_HANDLE_THICKNESS.toPx() }
                        val touchLengthPx = with(density) { CLE_EDGE_TOUCH_LENGTH.toPx() }
                        val touchThicknessPx = with(density) { CLE_EDGE_TOUCH_THICKNESS.toPx() }

                        val topCenterY = destTop - marginPx - handleThicknessPx / 2f
                        val bottomCenterY = destTop + destH + marginPx + handleThicknessPx / 2f
                        val leftCenterX = destLeft - marginPx - handleThicknessPx / 2f
                        val rightCenterX = destLeft + destW + marginPx + handleThicknessPx / 2f

                        val horizTouchX = (destLeft + destW / 2f) - touchLengthPx / 2f
                        val vertTouchY = (destTop + destH / 2f) - touchLengthPx / 2f

                        val edges =
                            listOf(
                                EdgeHandleDef(
                                    horizTouchX,
                                    topCenterY - touchThicknessPx / 2f,
                                    CLE_EDGE_TOUCH_LENGTH,
                                    CLE_EDGE_TOUCH_THICKNESS,
                                    CLE_EDGE_HANDLE_LENGTH,
                                    CLE_EDGE_HANDLE_THICKNESS,
                                    ResizeHandle.TOP,
                                ),
                                EdgeHandleDef(
                                    horizTouchX,
                                    bottomCenterY - touchThicknessPx / 2f,
                                    CLE_EDGE_TOUCH_LENGTH,
                                    CLE_EDGE_TOUCH_THICKNESS,
                                    CLE_EDGE_HANDLE_LENGTH,
                                    CLE_EDGE_HANDLE_THICKNESS,
                                    ResizeHandle.BOTTOM,
                                ),
                                EdgeHandleDef(
                                    leftCenterX - touchThicknessPx / 2f,
                                    vertTouchY,
                                    CLE_EDGE_TOUCH_THICKNESS,
                                    CLE_EDGE_TOUCH_LENGTH,
                                    CLE_EDGE_HANDLE_THICKNESS,
                                    CLE_EDGE_HANDLE_LENGTH,
                                    ResizeHandle.LEFT,
                                ),
                                EdgeHandleDef(
                                    rightCenterX - touchThicknessPx / 2f,
                                    vertTouchY,
                                    CLE_EDGE_TOUCH_THICKNESS,
                                    CLE_EDGE_TOUCH_LENGTH,
                                    CLE_EDGE_HANDLE_THICKNESS,
                                    CLE_EDGE_HANDLE_LENGTH,
                                    ResizeHandle.RIGHT,
                                ),
                            )

                        edges.forEach { def ->
                            ResizeHandleView(
                                offset = IntOffset(def.touchX.roundToInt(), def.touchY.roundToInt()),
                                touchWidth = def.touchWidth,
                                touchHeight = def.touchHeight,
                                handleWidth = def.handleWidth,
                                handleHeight = def.handleHeight,
                                color = colors.accent,
                                onDragStart = { captureDragStart() },
                                onDrag = { totalDx, totalDy -> handleEdgeDrag(def.handle, totalDx, totalDy) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CornerHandleDef(
    val centerX: Float,
    val centerY: Float,
    val rotation: Float,
    val handle: ResizeHandle,
)

private data class EdgeHandleDef(
    val touchX: Float,
    val touchY: Float,
    val touchWidth: Dp,
    val touchHeight: Dp,
    val handleWidth: Dp,
    val handleHeight: Dp,
    val handle: ResizeHandle,
)

@Composable
internal fun CutoutLayoutEditorHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_mirror_editor_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_mirror_editor_intro))

        HelpSection(stringResource(R.string.help_mirror_editor_section_selected))
        HelpEntry(
            icon = Icons.Rounded.AspectRatio,
            label = stringResource(R.string.mirror_editor_aspect_ratio_mode),
            description = stringResource(R.string.help_mirror_editor_aspect_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.CropSquare,
            label = stringResource(R.string.help_mirror_editor_shape_label),
            description = stringResource(R.string.help_mirror_editor_shape_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Crop,
            label = stringResource(R.string.help_mirror_editor_adjust_label),
            description = stringResource(R.string.help_mirror_editor_adjust_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.VisibilityOff,
            label = stringResource(R.string.mirror_editor_hide_background),
            description = stringResource(R.string.help_mirror_editor_hide_bg_desc),
        )

        HelpSection(stringResource(R.string.help_mirror_editor_section_finish))
        HelpEntry(
            icon = Icons.Rounded.Check,
            label = stringResource(R.string.mirror_editor_toolbar_done),
            description = stringResource(R.string.help_mirror_editor_done_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Close,
            label = stringResource(R.string.mirror_editor_toolbar_cancel),
            description = stringResource(R.string.help_mirror_editor_cancel_desc),
        )
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
                    .background(color.copy(alpha = 0.75f), RoundedCornerShape(CLE_EDGE_HANDLE_CORNER)),
        )
    }
}

internal fun adjustSourceCropToAspectRatio(
    cutout: ScreenCutout,
    screenW: Float,
    screenH: Float,
    srcW: Float,
    srcH: Float,
    baseSrcX: Float = cutout.srcX,
    baseSrcY: Float = cutout.srcY,
    baseSrcW: Float = cutout.srcWidth,
    baseSrcH: Float = cutout.srcHeight,
): ScreenCutout {
    val targetRatio = (cutout.destWidth * screenW) / (cutout.destHeight * screenH)
    val factor = targetRatio * (srcH / srcW)

    val centerX = baseSrcX + baseSrcW / 2f
    val centerY = baseSrcY + baseSrcH / 2f

    val newW: Float
    val newH: Float

    if (factor > baseSrcW / baseSrcH) {
        newW = baseSrcW
        newH = newW / factor
    } else {
        newH = baseSrcH
        newW = newH * factor
    }

    val newX = (centerX - newW / 2f).coerceIn(0f, 1f - newW)
    val newY = (centerY - newH / 2f).coerceIn(0f, 1f - newH)

    return cutout.copy(
        srcX = newX,
        srcY = newY,
        srcWidth = newW,
        srcHeight = newH,
    )
}
