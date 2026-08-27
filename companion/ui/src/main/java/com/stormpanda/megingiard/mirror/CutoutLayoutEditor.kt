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
private val BORDER_WIDTH = 1.dp
private val EDGE_HANDLE_LENGTH = 36.dp
private val EDGE_HANDLE_THICKNESS = 6.dp
private val EDGE_HANDLE_MARGIN = 6.dp
private val EDGE_TOUCH_LENGTH = 56.dp
private val EDGE_TOUCH_THICKNESS = 36.dp
private val EDGE_HANDLE_CORNER = 3.dp

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
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .size(diameterDp)
                                    .background(
                                        color = if (isSelected) colors.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                        shape = CircleShape,
                                    ).border(
                                        width = BORDER_WIDTH,
                                        color = if (isSelected) colors.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                                        shape = CircleShape,
                                    ),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = if (isSelected) colors.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(4.dp),
                                    ).border(
                                        width = BORDER_WIDTH,
                                        color = if (isSelected) colors.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp),
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

                // Show edge drag handles if selected
                if (isSelected) {
                    val marginPx = with(density) { EDGE_HANDLE_MARGIN.toPx() }
                    val handleThicknessPx = with(density) { EDGE_HANDLE_THICKNESS.toPx() }
                    val touchLengthPx = with(density) { EDGE_TOUCH_LENGTH.toPx() }
                    val touchThicknessPx = with(density) { EDGE_TOUCH_THICKNESS.toPx() }

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

                    // ── TOP Edge Handle (Horizontal Bar above Top edge) ───────────────
                    val topCenterY = destTop - marginPx - handleThicknessPx / 2f
                    val topTouchX = (destLeft + destW / 2f) - touchLengthPx / 2f
                    val topTouchY = topCenterY - touchThicknessPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(topTouchX.roundToInt(), topTouchY.roundToInt()),
                        touchWidth = EDGE_TOUCH_LENGTH,
                        touchHeight = EDGE_TOUCH_THICKNESS,
                        handleWidth = EDGE_HANDLE_LENGTH,
                        handleHeight = EDGE_HANDLE_THICKNESS,
                        color = colors.accent,
                        onDragStart = { captureDragStart() },
                        onDrag = { totalDx, totalDy -> handleEdgeDrag(ResizeHandle.TOP, totalDx, totalDy) },
                    )

                    // ── BOTTOM Edge Handle (Horizontal Bar below Bottom edge) ──────────
                    val bottomCenterY = destTop + destH + marginPx + handleThicknessPx / 2f
                    val bottomTouchX = (destLeft + destW / 2f) - touchLengthPx / 2f
                    val bottomTouchY = bottomCenterY - touchThicknessPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(bottomTouchX.roundToInt(), bottomTouchY.roundToInt()),
                        touchWidth = EDGE_TOUCH_LENGTH,
                        touchHeight = EDGE_TOUCH_THICKNESS,
                        handleWidth = EDGE_HANDLE_LENGTH,
                        handleHeight = EDGE_HANDLE_THICKNESS,
                        color = colors.accent,
                        onDragStart = { captureDragStart() },
                        onDrag = { totalDx, totalDy -> handleEdgeDrag(ResizeHandle.BOTTOM, totalDx, totalDy) },
                    )

                    // ── LEFT Edge Handle (Vertical Bar to the left of Left edge) ──────
                    val leftCenterX = destLeft - marginPx - handleThicknessPx / 2f
                    val leftTouchX = leftCenterX - touchThicknessPx / 2f
                    val leftTouchY = (destTop + destH / 2f) - touchLengthPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(leftTouchX.roundToInt(), leftTouchY.roundToInt()),
                        touchWidth = EDGE_TOUCH_THICKNESS,
                        touchHeight = EDGE_TOUCH_LENGTH,
                        handleWidth = EDGE_HANDLE_THICKNESS,
                        handleHeight = EDGE_HANDLE_LENGTH,
                        color = colors.accent,
                        onDragStart = { captureDragStart() },
                        onDrag = { totalDx, totalDy -> handleEdgeDrag(ResizeHandle.LEFT, totalDx, totalDy) },
                    )

                    // ── RIGHT Edge Handle (Vertical Bar to the right of Right edge) ───
                    val rightCenterX = destLeft + destW + marginPx + handleThicknessPx / 2f
                    val rightTouchX = rightCenterX - touchThicknessPx / 2f
                    val rightTouchY = (destTop + destH / 2f) - touchLengthPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(rightTouchX.roundToInt(), rightTouchY.roundToInt()),
                        touchWidth = EDGE_TOUCH_THICKNESS,
                        touchHeight = EDGE_TOUCH_LENGTH,
                        handleWidth = EDGE_HANDLE_THICKNESS,
                        handleHeight = EDGE_HANDLE_LENGTH,
                        color = colors.accent,
                        onDragStart = { captureDragStart() },
                        onDrag = { totalDx, totalDy -> handleEdgeDrag(ResizeHandle.RIGHT, totalDx, totalDy) },
                    )
                }
            }
        }
    }
}

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
                    .background(color.copy(alpha = 0.75f), RoundedCornerShape(EDGE_HANDLE_CORNER)),
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
