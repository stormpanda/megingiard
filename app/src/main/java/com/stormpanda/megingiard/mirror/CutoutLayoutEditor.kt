package com.stormpanda.megingiard.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.MirrorSettings
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt


private const val TAG = "CutoutLayoutEditor"
private val HANDLE_SIZE = 20.dp
private val BORDER_WIDTH = 1.dp
private val TOOLBAR_SHADOW = 6.dp
private val TOOLBAR_CORNER = 8.dp
private val MP_EDGE_ZONE = 40.dp
private val SLIDER_LABEL_WIDTH = 100.dp
private val SLIDER_VALUE_WIDTH = 80.dp
private val TOOLBAR_EXPANDED_WIDTH = 360.dp
private const val SLIDER_VALUE_MIN = 0f
private const val SLIDER_VALUE_MAX = 100f
private val TOOLBAR_SAFE_MARGIN = 0.dp
private const val TOUCH_AREA_RATIO = 0.25f
private val CLE_HELP_BTN_SIZE = 32.dp
private val CLE_HELP_ICON_SIZE = 20.dp
private val CLE_HELP_BTN_CORNER = 4.dp
private val CLE_SPACER_WIDTH = 32.dp

@Composable
fun CutoutLayoutEditor(
    overlayAtBottom: Boolean
) {
    val colors = LocalAppColors.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val layout = activeLayout ?: return
    val context = LocalContext.current

    val initialCutouts = remember(layout.id) { layout.mirrorCutouts }

    var toolbarOffset by remember { mutableStateOf<IntOffset?>(null) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val selectedCutoutId by AppStateManager.selectedCutoutId.collectAsState()
    val density = LocalDensity.current
    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val captureSourceWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val captureSourceHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()
    val srcWidth = if (captureSourceWidth > 0) captureSourceWidth.toFloat() else 1920f
    val srcHeight = if (captureSourceHeight > 0) captureSourceHeight.toFloat() else 1080f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        if (containerW <= 0f || containerH <= 0f) return@BoxWithConstraints

        val screenW = if (surfaceWidth > 0f) surfaceWidth else containerW
        val screenH = if (surfaceHeight > 0f) surfaceHeight else containerH

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(
                    width = with(density) { screenW.toDp() },
                    height = with(density) { screenH.toDp() }
                )
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
                    modifier = Modifier
                        .offset { IntOffset(destLeft.roundToInt(), destTop.roundToInt()) }
                        .size(
                            width = with(density) { destW.toDp() },
                            height = with(density) { destH.toDp() }
                        )
                        .clickable {
                            AppStateManager.setSelectedCutoutId(cutout.id)
                        }
                        .pointerInput(cutout.id) {
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

                                    val (clampedX, clampedY) = clampCutoutDrag(
                                        cutoutId = curCutout.id,
                                        originalX = curCutout.destX,
                                        originalY = curCutout.destY,
                                        targetX = targetX,
                                        targetY = targetY,
                                        width = curCutout.destWidth,
                                        height = curCutout.destHeight,
                                        allCutouts = curLayout.mirrorCutouts
                                    )

                                    if (clampedX != targetX || clampedY != targetY) {
                                        AppLog.d(TAG, "Drag clamped '${curCutout.name}': target=($targetX, $targetY) -> clamped=($clampedX, $clampedY)")
                                    }

                                    val updated = curLayout.mirrorCutouts.map {
                                        if (it.id == curCutout.id) it.copy(destX = clampedX, destY = clampedY) else it
                                    }
                                    MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                                }
                            )
                        }
                ) {
                    if (isCircle) {
                        val diameterDp = with(density) { min(destW, destH).toDp() }
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(diameterDp)
                                .background(
                                    color = if (isSelected) colors.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = BORDER_WIDTH,
                                    color = if (isSelected) colors.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = if (isSelected) colors.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = BORDER_WIDTH,
                                    color = if (isSelected) colors.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }
                    Text(
                        text = cutout.name.ifBlank { "Cutout" },
                        color = if (isSelected) colors.accent else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                            AppLog.d(TAG, "Resize start TOP_LEFT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})")
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX + totalDx / screenW
                            val targetY = dragStartY + totalDy / screenH
                            val targetWidth = dragStartW - totalDx / screenW
                            val targetHeight = dragStartH - totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom = clampCutoutResize(
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
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize TOP_LEFT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
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
                                            baseSrcH = dragStartSrcH
                                        )
                                    } else {
                                        next
                                    }
                                } else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
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
                            AppLog.d(TAG, "Resize start TOP_RIGHT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})")
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX
                            val targetY = dragStartY + totalDy / screenH
                            val targetWidth = dragStartW + totalDx / screenW
                            val targetHeight = dragStartH - totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom = clampCutoutResize(
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
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize TOP_RIGHT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
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
                                            baseSrcH = dragStartSrcH
                                        )
                                    } else {
                                        next
                                    }
                                } else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
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
                            AppLog.d(TAG, "Resize start BOTTOM_LEFT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})")
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX + totalDx / screenW
                            val targetY = dragStartY
                            val targetWidth = dragStartW - totalDx / screenW
                            val targetHeight = dragStartH + totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom = clampCutoutResize(
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
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize BOTTOM_LEFT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
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
                                            baseSrcH = dragStartSrcH
                                        )
                                    } else {
                                        next
                                    }
                                } else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
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
                            AppLog.d(TAG, "Resize start BOTTOM_RIGHT cutout '${curCutout.name}' bounds=(${curCutout.destX}, ${curCutout.destY}, ${curCutout.destWidth}, ${curCutout.destHeight})")
                        },
                        onDrag = { totalDx, totalDy ->
                            val curLayout = currentLayoutState.value
                            val curCutout = currentCutoutState.value
                            val targetX = dragStartX
                            val targetY = dragStartY
                            val targetWidth = dragStartW + totalDx / screenW
                            val targetHeight = dragStartH + totalDy / screenH
                            val cropRatio = (curCutout.srcWidth * srcWidth) / (curCutout.srcHeight * srcHeight)
                            val geom = clampCutoutResize(
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
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize BOTTOM_RIGHT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
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
                                            baseSrcH = dragStartSrcH
                                        )
                                    } else {
                                        next
                                    }
                                } else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )
                }
            }
    }

        val marginPx = with(density) { TOOLBAR_SAFE_MARGIN.toPx() }

        if (toolbarOffset == null && toolbarSize != IntSize.Zero) {
            val initialY = if (overlayAtBottom) {
                marginPx
            } else {
                containerH - toolbarSize.height.toFloat() - marginPx
            }
            toolbarOffset = IntOffset(0, initialY.roundToInt())
        }

        val currentOffset = toolbarOffset ?: IntOffset.Zero
        val clampedOffset = if (toolbarSize != IntSize.Zero) {
            val maxStartX = ((containerW - toolbarSize.width) / 2f - marginPx).coerceAtLeast(0f)
            val clampedX = currentOffset.x.toFloat().coerceIn(-maxStartX, maxStartX)

            val minY = marginPx
            val maxY = containerH - toolbarSize.height.toFloat() - marginPx
            val clampedY = currentOffset.y.toFloat().coerceIn(minY, maxY.coerceAtLeast(minY))

            IntOffset(clampedX.roundToInt(), clampedY.roundToInt())
        } else {
            currentOffset
        }

        val currentClampedOffset by rememberUpdatedState(clampedOffset)

        var showEditorHelp by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onGloballyPositioned { coords -> toolbarSize = coords.size }
                .offset { clampedOffset }
                .shadow(TOOLBAR_SHADOW, RoundedCornerShape(TOOLBAR_CORNER)),
            color = colors.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(TOOLBAR_CORNER),
            border = borderStrokeFor(colors.controlOverlayBorder)
        ) {
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Button grid (left side)
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    val selectedCutout = selectedCutoutId?.let { cutoutId ->
                        layout.mirrorCutouts.find { it.id == cutoutId }
                    }
                    val currentMode = selectedCutout?.aspectRatioMode ?: AspectRatioMode.FREE
                    val isCircle = selectedCutout?.shape == CutoutShape.CIRCLE

                    // Row 1: Add Cutout | Settings | Help
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.mirror_editor_add_cutout),
                            color = colors.accent,
                            label = stringResource(R.string.mirror_editor_toolbar_add),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (layout.mirrorCutouts.size >= 10) {
                                    Toast.makeText(context, context.getString(R.string.mirror_editor_max_cutouts), Toast.LENGTH_SHORT).show()
                                } else {
                                    val newId = UUID.randomUUID().toString()
                                    var foundX = 0f
                                    var foundY = 0f
                                    var collides = true
                                    for (y in listOf(0f, 0.35f, 0.7f)) {
                                        for (x in listOf(0f, 0.35f, 0.7f)) {
                                            collides = layout.mirrorCutouts.any { other ->
                                                x < other.destX + other.destWidth && x + 0.3f > other.destX &&
                                                y < other.destY + other.destHeight && y + 0.3f > other.destY
                                            }
                                            if (!collides) {
                                                foundX = x
                                                foundY = y
                                                break
                                            }
                                        }
                                        if (!collides) break
                                    }
                                    if (collides) {
                                        Toast.makeText(context, context.getString(R.string.mirror_editor_no_space), Toast.LENGTH_SHORT).show()
                                    } else {
                                        val initialCutout = ScreenCutout(
                                            id = newId,
                                            name = "Cutout ${layout.mirrorCutouts.size + 1}",
                                            srcX = 0.25f, srcY = 0.25f, srcWidth = 0.5f, srcHeight = 0.5f,
                                            destX = foundX, destY = foundY, destWidth = 0.3f, destHeight = 0.3f,
                                            aspectRatioMode = AspectRatioMode.BOTTOM
                                        )
                                        val newCutout = adjustSourceCropToAspectRatio(
                                            cutout = initialCutout,
                                            screenW = screenW,
                                            screenH = screenH,
                                            srcW = srcWidth,
                                            srcH = srcHeight
                                        )
                                        MacroPadState.updateLayout(layout.copy(mirrorCutouts = layout.mirrorCutouts + newCutout))
                                        AppStateManager.setSelectedCutoutId(newId)
                                    }
                                }
                            }
                        )

                        ToolbarIconButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.quick_menu_ambient_settings),
                            color = colors.accent,
                            label = stringResource(R.string.mirror_editor_toolbar_settings),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                AppStateManager.setBackgroundSettingsActive(true)
                            }
                        )

                        Box(
                            modifier = Modifier
                                .size(CLE_HELP_BTN_SIZE)
                                .clip(RoundedCornerShape(CLE_HELP_BTN_CORNER))
                                .clickable { showEditorHelp = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = stringResource(R.string.help_open_cd),
                                tint = colors.onSurfaceSecondary,
                                modifier = Modifier.size(CLE_HELP_ICON_SIZE)
                            )
                        }
                    }

                    // Row 2: Aspect Ratio | Shape Toggle | Spacer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarIconButton(
                            icon = Icons.Rounded.AspectRatio,
                            contentDescription = stringResource(R.string.mirror_editor_aspect_ratio_mode),
                            color = colors.accent,
                            label = when (currentMode) {
                                AspectRatioMode.FREE -> stringResource(R.string.mirror_editor_aspect_ratio_free)
                                AspectRatioMode.TOP -> stringResource(R.string.mirror_editor_aspect_ratio_top)
                                AspectRatioMode.BOTTOM -> stringResource(R.string.mirror_editor_aspect_ratio_bottom)
                            },
                            enabled = selectedCutout != null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val cutoutId = selectedCutoutId ?: return@ToolbarIconButton
                                val updated = layout.mirrorCutouts.map {
                                    if (it.id == cutoutId) {
                                        val nextMode = when (it.aspectRatioMode) {
                                            AspectRatioMode.FREE -> AspectRatioMode.TOP
                                            AspectRatioMode.TOP -> AspectRatioMode.BOTTOM
                                            AspectRatioMode.BOTTOM -> AspectRatioMode.FREE
                                        }
                                        var updatedCutout = it.copy(
                                            aspectRatioMode = nextMode,
                                            keepAspectRatio = (nextMode == AspectRatioMode.TOP)
                                        )
                                        if (nextMode == AspectRatioMode.TOP) {
                                            val cropRatio = (updatedCutout.srcWidth * srcWidth) / (updatedCutout.srcHeight * srcHeight)
                                            val (newDestW, newDestH) = adjustDestSizeToAspectRatio(
                                                destX = updatedCutout.destX,
                                                destY = updatedCutout.destY,
                                                destWidth = updatedCutout.destWidth,
                                                destHeight = updatedCutout.destHeight,
                                                cropRatio = cropRatio,
                                                screenW = screenW,
                                                screenH = screenH
                                            )
                                            updatedCutout = updatedCutout.copy(destWidth = newDestW, destHeight = newDestH)
                                        } else if (nextMode == AspectRatioMode.BOTTOM) {
                                            updatedCutout = adjustSourceCropToAspectRatio(
                                                updatedCutout,
                                                screenW = screenW,
                                                screenH = screenH,
                                                srcW = srcWidth,
                                                srcH = srcHeight
                                            )
                                        }
                                        updatedCutout
                                    } else it
                                }
                                MacroPadState.updateLayout(layout.copy(mirrorCutouts = updated))
                            }
                        )

                        ToolbarIconButton(
                            icon = if (isCircle) Icons.Rounded.Circle else Icons.Rounded.CropSquare,
                            contentDescription = if (isCircle) {
                                stringResource(R.string.mirror_editor_shape_circle)
                            } else {
                                stringResource(R.string.mirror_editor_shape_rectangle)
                            },
                            color = colors.accent,
                            label = if (isCircle) {
                                stringResource(R.string.mirror_editor_toolbar_shape_circle)
                            } else {
                                stringResource(R.string.mirror_editor_toolbar_shape_rect)
                            },
                            enabled = selectedCutout != null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val cutoutId = selectedCutoutId ?: return@ToolbarIconButton
                                val updated = layout.mirrorCutouts.map {
                                    if (it.id == cutoutId) {
                                        val nextShape = if (it.shape == CutoutShape.CIRCLE) {
                                            CutoutShape.RECTANGLE
                                        } else {
                                            CutoutShape.CIRCLE
                                        }
                                        it.copy(shape = nextShape)
                                    } else it
                                }
                                MacroPadState.updateLayout(layout.copy(mirrorCutouts = updated))
                            }
                        )

                        Spacer(Modifier.width(CLE_SPACER_WIDTH))
                    }

                    // Row 3: Edit Crop | Delete Selected | Spacer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarIconButton(
                            icon = Icons.Rounded.Crop,
                            contentDescription = stringResource(R.string.mirror_editor_edit_crop),
                            color = colors.accent,
                            label = stringResource(R.string.mirror_editor_toolbar_crop),
                            enabled = selectedCutoutId != null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                AppStateManager.setActiveCropCutoutId(selectedCutoutId)
                            }
                        )

                        ToolbarIconButton(
                            icon = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.mirror_editor_delete_cutout),
                            color = colors.error,
                            label = stringResource(R.string.mirror_editor_toolbar_delete),
                            enabled = selectedCutoutId != null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val targetId = selectedCutoutId ?: return@ToolbarIconButton
                                val remaining = layout.mirrorCutouts.filter { it.id != targetId }
                                val wasFollowing = layout.mirrorCutouts.find { it.id == targetId }?.followTouch == true
                                val newFollowActive = if (wasFollowing) false else layout.mirrorFollowActive
                                MacroPadState.updateLayout(layout.copy(
                                    mirrorCutouts = remaining,
                                    mirrorFollowActive = newFollowActive
                                ))
                                if (wasFollowing) {
                                    ScreenCaptureManager.setFollowActive(false, persist = false)
                                }
                                AppStateManager.setSelectedCutoutId(remaining.firstOrNull()?.id)
                            }
                        )

                        Spacer(Modifier.width(CLE_SPACER_WIDTH))
                    }

                    // Row 4: Done / Save | Cancel / Revert | Drag Handle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarIconButton(
                            icon = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.mirror_editor_done),
                            color = colors.accent,
                            label = stringResource(R.string.mirror_editor_toolbar_done),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                AppStateManager.setViewportEditActive(false)
                            }
                        )

                        ToolbarIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_color_cancel),
                            color = colors.error,
                            label = stringResource(R.string.mirror_editor_toolbar_cancel),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val updatedLayout = layout.copy(
                                    mirrorCutouts = initialCutouts
                                )
                                MacroPadState.updateLayout(updatedLayout)
                                AppStateManager.setViewportEditActive(false)
                            }
                        )

                        Box(
                            modifier = Modifier
                                .size(CLE_HELP_BTN_SIZE)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val cur = currentClampedOffset
                                        toolbarOffset = IntOffset(
                                            x = cur.x + dragAmount.x.roundToInt(),
                                            y = cur.y + dragAmount.y.roundToInt()
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = stringResource(R.string.cd_drag_toolbar),
                                tint = colors.onSurfaceSecondary,
                                modifier = Modifier.size(CLE_HELP_ICON_SIZE)
                            )
                        }
                    }
                }
            } // end Row
        } // end Surface

        CutoutLayoutEditorHelpModal(
            visible = showEditorHelp,
            onDismiss = { showEditorHelp = false },
        )
    }
}

@Composable
private fun CutoutLayoutEditorHelpModal(visible: Boolean, onDismiss: () -> Unit) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_mirror_editor_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_mirror_editor_intro))

        HelpSection(stringResource(R.string.help_mirror_editor_section_global))
        HelpEntry(
            icon = Icons.Rounded.Add,
            label = stringResource(R.string.mirror_editor_toolbar_add),
            description = stringResource(R.string.help_mirror_editor_add_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.mirror_editor_toolbar_settings),
            description = stringResource(R.string.help_mirror_editor_settings_desc),
        )

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
            label = stringResource(R.string.mirror_editor_toolbar_crop),
            description = stringResource(R.string.help_mirror_editor_crop_desc),
        )
        HelpEntry(
            icon = Icons.Rounded.Delete,
            label = stringResource(R.string.mirror_editor_toolbar_delete),
            description = stringResource(R.string.help_mirror_editor_delete_desc),
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
    color: Color,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
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
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(HANDLE_SIZE)
                .background(color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = colors.onSurfaceSecondary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) colors.onAccent else colors.onSurfaceSecondary.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    color: Color,
    enabled: Boolean = true,
    label: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = colors.onSurfaceSecondary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) colors.onAccent else colors.onSurfaceSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            if (label != null) {
                Text(
                    text = label,
                    color = if (enabled) colors.onAccent else colors.onSurfaceSecondary.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// Small helper since BorderStroke needs it
@Composable
private fun borderStrokeFor(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)

private fun adjustSourceCropToAspectRatio(
    cutout: ScreenCutout,
    screenW: Float,
    screenH: Float,
    srcW: Float,
    srcH: Float,
    baseSrcX: Float = cutout.srcX,
    baseSrcY: Float = cutout.srcY,
    baseSrcW: Float = cutout.srcWidth,
    baseSrcH: Float = cutout.srcHeight
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
        srcHeight = newH
    )
}
