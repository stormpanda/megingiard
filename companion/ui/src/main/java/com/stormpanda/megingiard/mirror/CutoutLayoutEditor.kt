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
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material3.MaterialTheme
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
private val HANDLE_SIZE = 20.dp
private val BORDER_WIDTH = 1.dp
private const val TOUCH_AREA_RATIO = 0.25f

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
            val handleSizePx = with(density) { HANDLE_SIZE.toPx() }
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

                // Show corner resize handles if selected
                if (isSelected) {
                    val handleSizePx = with(density) { HANDLE_SIZE.toPx() }
                    val touchWPx = kotlin.math.max(handleSizePx, destW * TOUCH_AREA_RATIO)
                    val touchHPx = kotlin.math.max(handleSizePx, destH * TOUCH_AREA_RATIO)
                    val touchWidth = with(density) { touchWPx.toDp() }
                    val touchHeight = with(density) { touchHPx.toDp() }

                    var dragStartX by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartY by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartW by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartH by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartSrcX by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartSrcY by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartSrcW by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartSrcH by remember(cutout.id) { mutableStateOf(0f) }

                    // Top-Left handle
                    val topLeftCenterX = destLeft + handleSizePx / 2f
                    val topLeftCenterY = destTop + handleSizePx / 2f
                    val topLeftTouchX = topLeftCenterX - touchWPx / 2f
                    val topLeftTouchY = topLeftCenterY - touchHPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(topLeftTouchX.roundToInt(), topLeftTouchY.roundToInt()),
                        touchWidth = touchWidth,
                        touchHeight = touchHeight,
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
                            dragStartSrcX = curCutout.srcX
                            dragStartSrcY = curCutout.srcY
                            dragStartSrcW = curCutout.srcWidth
                            dragStartSrcH = curCutout.srcHeight
                            AppLog.d(
                                TAG,
                                "Resize start TOP_LEFT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})",
                            )
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX + totalDx / screenW
                            val targetY = dragStartY + totalDy / screenH
                            val targetWidth = dragStartW - totalDx / screenW
                            val targetHeight = dragStartH - totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom =
                                clampCutoutResize(
                                    cutoutId = curCutout.id,
                                    handle = ResizeHandle.TOP_LEFT,
                                    originalX = dragStartX,
                                    originalY = dragStartY,
                                    originalWidth = dragStartW,
                                    originalHeight = dragStartH,
                                    targetX = targetX,
                                    targetY = targetY,
                                    targetWidth = targetWidth,
                                    targetHeight = targetHeight,
                                    allCutouts = curLayout.mirrorCutouts,
                                    keepAspectRatio = (curCutout.aspectRatioMode == AspectRatioMode.TOP),
                                    cropRatio = cropRatio,
                                    screenW = screenW,
                                    screenH = screenH,
                                )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(
                                    TAG,
                                    "Resize TOP_LEFT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})",
                                )
                            }
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
                        },
                    )

                    // Top-Right handle
                    val topRightCenterX = destLeft + destW - handleSizePx / 2f
                    val topRightCenterY = destTop + handleSizePx / 2f
                    val topRightTouchX = topRightCenterX - touchWPx / 2f
                    val topRightTouchY = topRightCenterY - touchHPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(topRightTouchX.roundToInt(), topRightTouchY.roundToInt()),
                        touchWidth = touchWidth,
                        touchHeight = touchHeight,
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
                            dragStartSrcX = curCutout.srcX
                            dragStartSrcY = curCutout.srcY
                            dragStartSrcW = curCutout.srcWidth
                            dragStartSrcH = curCutout.srcHeight
                            AppLog.d(
                                TAG,
                                "Resize start TOP_RIGHT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})",
                            )
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX
                            val targetY = dragStartY + totalDy / screenH
                            val targetWidth = dragStartW + totalDx / screenW
                            val targetHeight = dragStartH - totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom =
                                clampCutoutResize(
                                    cutoutId = curCutout.id,
                                    handle = ResizeHandle.TOP_RIGHT,
                                    originalX = dragStartX,
                                    originalY = dragStartY,
                                    originalWidth = dragStartW,
                                    originalHeight = dragStartH,
                                    targetX = targetX,
                                    targetY = targetY,
                                    targetWidth = targetWidth,
                                    targetHeight = targetHeight,
                                    allCutouts = curLayout.mirrorCutouts,
                                    keepAspectRatio = (curCutout.aspectRatioMode == AspectRatioMode.TOP),
                                    cropRatio = cropRatio,
                                    screenW = screenW,
                                    screenH = screenH,
                                )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(
                                    TAG,
                                    "Resize TOP_RIGHT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})",
                                )
                            }
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
                        },
                    )

                    // Bottom-Left handle
                    val bottomLeftCenterX = destLeft + handleSizePx / 2f
                    val bottomLeftCenterY = destTop + destH - handleSizePx / 2f
                    val bottomLeftTouchX = bottomLeftCenterX - touchWPx / 2f
                    val bottomLeftTouchY = bottomLeftCenterY - touchHPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(bottomLeftTouchX.roundToInt(), bottomLeftTouchY.roundToInt()),
                        touchWidth = touchWidth,
                        touchHeight = touchHeight,
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
                            dragStartSrcX = curCutout.srcX
                            dragStartSrcY = curCutout.srcY
                            dragStartSrcW = curCutout.srcWidth
                            dragStartSrcH = curCutout.srcHeight
                            AppLog.d(
                                TAG,
                                "Resize start BOTTOM_LEFT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})",
                            )
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX + totalDx / screenW
                            val targetY = dragStartY
                            val targetWidth = dragStartW - totalDx / screenW
                            val targetHeight = dragStartH + totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom =
                                clampCutoutResize(
                                    cutoutId = curCutout.id,
                                    handle = ResizeHandle.BOTTOM_LEFT,
                                    originalX = dragStartX,
                                    originalY = dragStartY,
                                    originalWidth = dragStartW,
                                    originalHeight = dragStartH,
                                    targetX = targetX,
                                    targetY = targetY,
                                    targetWidth = targetWidth,
                                    targetHeight = targetHeight,
                                    allCutouts = curLayout.mirrorCutouts,
                                    keepAspectRatio = (curCutout.aspectRatioMode == AspectRatioMode.TOP),
                                    cropRatio = cropRatio,
                                    screenW = screenW,
                                    screenH = screenH,
                                )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(
                                    TAG,
                                    "Resize BOTTOM_LEFT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})",
                                )
                            }
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
                        },
                    )

                    // Bottom-Right handle
                    val bottomRightCenterX = destLeft + destW - handleSizePx / 2f
                    val bottomRightCenterY = destTop + destH - handleSizePx / 2f
                    val bottomRightTouchX = bottomRightCenterX - touchWPx / 2f
                    val bottomRightTouchY = bottomRightCenterY - touchHPx / 2f
                    ResizeHandleView(
                        offset = IntOffset(bottomRightTouchX.roundToInt(), bottomRightTouchY.roundToInt()),
                        touchWidth = touchWidth,
                        touchHeight = touchHeight,
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
                            dragStartSrcX = curCutout.srcX
                            dragStartSrcY = curCutout.srcY
                            dragStartSrcW = curCutout.srcWidth
                            dragStartSrcH = curCutout.srcHeight
                            AppLog.d(
                                TAG,
                                "Resize start BOTTOM_RIGHT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})",
                            )
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX
                            val targetY = dragStartY
                            val targetWidth = dragStartW + totalDx / screenW
                            val targetHeight = dragStartH + totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom =
                                clampCutoutResize(
                                    cutoutId = curCutout.id,
                                    handle = ResizeHandle.BOTTOM_RIGHT,
                                    originalX = dragStartX,
                                    originalY = dragStartY,
                                    originalWidth = dragStartW,
                                    originalHeight = dragStartH,
                                    targetX = targetX,
                                    targetY = targetY,
                                    targetWidth = targetWidth,
                                    targetHeight = targetHeight,
                                    allCutouts = curLayout.mirrorCutouts,
                                    keepAspectRatio = (curCutout.aspectRatioMode == AspectRatioMode.TOP),
                                    cropRatio = cropRatio,
                                    screenW = screenW,
                                    screenH = screenH,
                                )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(
                                    TAG,
                                    "Resize BOTTOM_RIGHT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})",
                                )
                            }
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
                        },
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
    touchWidth: androidx.compose.ui.unit.Dp,
    touchHeight: androidx.compose.ui.unit.Dp,
    color: Color,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    var accumulatedX by remember { mutableStateOf(0f) }
    var accumulatedY by remember { mutableStateOf(0f) }
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier =
            Modifier
                .offset { offset }
                .size(width = touchWidth, height = touchHeight)
                .pointerInput(Unit) {
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
                    .size(HANDLE_SIZE)
                    .background(color.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
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
