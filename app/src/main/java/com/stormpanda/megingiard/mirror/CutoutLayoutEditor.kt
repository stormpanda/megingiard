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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.MirrorSettings
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
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
private val TOOLBAR_SAFE_MARGIN = 16.dp
private val HANDLE_INSET_THRESHOLD = 16.dp
private val HANDLE_INSET_SHIFT = 8.dp

@Composable
fun CutoutLayoutEditor(
    overlayAtBottom: Boolean
) {
    val colors = LocalAppColors.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val layout = activeLayout ?: return

    val initialCutouts = remember(layout.id) { layout.mirrorCutouts }
    val initialCrossfade = remember(layout.id) { layout.mirrorCrossfadeBlendWidth }
    val initialSmoothing = remember(layout.id) { layout.mirrorSmoothingStrength }

    var toolbarOffset by remember { mutableStateOf<IntOffset?>(null) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    var isExpanded by remember { mutableStateOf(false) }
    val selectedCutoutId by AppStateManager.selectedCutoutId.collectAsState()
    val crossfadeBlendWidthDp = layout.mirrorCrossfadeBlendWidth
    val smoothingStrength = layout.mirrorSmoothingStrength
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
                    val insetThresholdPx = with(density) { HANDLE_INSET_THRESHOLD.toPx() }
                    val insetShiftPx = with(density) { HANDLE_INSET_SHIFT.toPx() }

                    var dragStartX by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartY by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartW by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartH by remember(cutout.id) { mutableStateOf(0f) }

                    // Top-Left handle
                    val topLeftX = destLeft + (if (destLeft < insetThresholdPx) insetShiftPx else 0f)
                    val topLeftY = destTop + (if (destTop < insetThresholdPx) insetShiftPx else 0f)
                    ResizeHandleView(
                        offset = IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()),
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
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
                                        adjustSourceCropToAspectRatio(next, screenW = screenW, screenH = screenH, srcW = srcWidth, srcH = srcHeight)
                                    } else {
                                        next
                                    }
                                } else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Top-Right handle
                    val topRightX = destLeft + destW - handleSizePx - (if (destLeft + destW > screenW - insetThresholdPx) insetShiftPx else 0f)
                    val topRightY = destTop + (if (destTop < insetThresholdPx) insetShiftPx else 0f)
                    ResizeHandleView(
                        offset = IntOffset(topRightX.roundToInt(), topRightY.roundToInt()),
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
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
                                        adjustSourceCropToAspectRatio(next, screenW = screenW, screenH = screenH, srcW = srcWidth, srcH = srcHeight)
                                    } else {
                                        next
                                    }
                                } else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Bottom-Left handle
                    val bottomLeftX = destLeft + (if (destLeft < insetThresholdPx) insetShiftPx else 0f)
                    val bottomLeftY = destTop + destH - handleSizePx - (if (destTop + destH > screenH - insetThresholdPx) insetShiftPx else 0f)
                    ResizeHandleView(
                        offset = IntOffset(bottomLeftX.roundToInt(), bottomLeftY.roundToInt()),
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
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
                                        adjustSourceCropToAspectRatio(next, screenW = screenW, screenH = screenH, srcW = srcWidth, srcH = srcHeight)
                                    } else {
                                        next
                                    }
                                } else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Bottom-Right handle
                    val bottomRightX = destLeft + destW - handleSizePx - (if (destLeft + destW > screenW - insetThresholdPx) insetShiftPx else 0f)
                    val bottomRightY = destTop + destH - handleSizePx - (if (destTop + destH > screenH - insetThresholdPx) insetShiftPx else 0f)
                    ResizeHandleView(
                        offset = IntOffset(bottomRightX.roundToInt(), bottomRightY.roundToInt()),
                        color = colors.accent,
                        onDragStart = {
                            val curCutout = currentCutoutState.value
                            dragStartX = curCutout.destX
                            dragStartY = curCutout.destY
                            dragStartW = curCutout.destWidth
                            dragStartH = curCutout.destHeight
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
                                        adjustSourceCropToAspectRatio(next, screenW = screenW, screenH = screenH, srcW = srcWidth, srcH = srcHeight)
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
            Box(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 8.dp)
            ) {
                // Drag handle at top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(width = 36.dp, height = 32.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(start = 40.dp) // clear drag handle (36dp + 4dp space)
                        .width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add Cutout
                        ToolbarIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.mirror_editor_add_cutout),
                            color = colors.accent,
                            onClick = {
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
                                val newCutout = ScreenCutout(
                                    id = newId,
                                    name = "Cutout ${layout.mirrorCutouts.size + 1}",
                                    srcX = 0.25f, srcY = 0.25f, srcWidth = 0.5f, srcHeight = 0.5f,
                                    destX = foundX, destY = foundY, destWidth = 0.3f, destHeight = 0.3f,
                                    aspectRatioMode = AspectRatioMode.BOTTOM
                                )
                                MacroPadState.updateLayout(layout.copy(mirrorCutouts = layout.mirrorCutouts + newCutout))
                                AppStateManager.setSelectedCutoutId(newId)
                            }
                        )

                        val selectedCutout = selectedCutoutId?.let { cutoutId ->
                            layout.mirrorCutouts.find { it.id == cutoutId }
                        }
                        val currentMode = selectedCutout?.aspectRatioMode ?: AspectRatioMode.FREE

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

                        // Shape Toggle button (Rectangle / Circle)
                        val isCircle = selectedCutout?.shape == CutoutShape.CIRCLE
                        ToolbarIconButton(
                            icon = if (isCircle) Icons.Rounded.Circle else Icons.Rounded.CropSquare,
                            contentDescription = if (isCircle) {
                                stringResource(R.string.mirror_editor_shape_circle)
                            } else {
                                stringResource(R.string.mirror_editor_shape_rectangle)
                            },
                            color = colors.accent,
                            enabled = selectedCutout != null,
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

                        // Edit Crop of selected
                        ToolbarIconButton(
                            icon = Icons.Rounded.Crop,
                            contentDescription = stringResource(R.string.mirror_editor_edit_crop),
                            color = colors.accent,
                            enabled = selectedCutoutId != null,
                            onClick = {
                                AppStateManager.setActiveCropCutoutId(selectedCutoutId)
                            }
                        )

                        // Done / Save button
                        ToolbarIconButton(
                            icon = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.mirror_editor_done),
                            color = colors.accent,
                            onClick = {
                                AppStateManager.setViewportEditActive(false)
                            }
                        )

                        // Cancel / Revert button
                        ToolbarIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_color_cancel),
                            color = colors.error,
                            onClick = {
                                val updatedLayout = layout.copy(
                                    mirrorCutouts = initialCutouts,
                                    mirrorCrossfadeBlendWidth = initialCrossfade,
                                    mirrorSmoothingStrength = initialSmoothing
                                )
                                MacroPadState.updateLayout(updatedLayout)
                                AppStateManager.setViewportEditActive(false)
                            }
                        )

                        // Expand / Collapse button
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 32.dp)
                                .clickable { isExpanded = !isExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = stringResource(
                                    if (isExpanded) R.string.settings_section_collapse else R.string.settings_section_expand
                                ),
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            // Crossfade Slider
                            val crossfadeValueText = when (crossfadeBlendWidthDp.roundToInt()) {
                                in 0..12 -> stringResource(R.string.mirror_crossfade_strength_off)
                                in 13..37 -> stringResource(R.string.mirror_crossfade_strength_light)
                                in 38..62 -> stringResource(R.string.mirror_crossfade_strength_medium)
                                in 63..87 -> stringResource(R.string.mirror_crossfade_strength_strong)
                                else -> stringResource(R.string.mirror_crossfade_strength_max)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.mirror_crossfade_label),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(SLIDER_LABEL_WIDTH)
                                )
                                Slider(
                                    value = crossfadeBlendWidthDp,
                                    onValueChange = { value ->
                                        val idx = (value / 25f).roundToInt().coerceIn(0, 4)
                                        MacroPadState.updateLayout(layout.copy(mirrorCrossfadeBlendWidth = idx * 25f))
                                    },
                                    valueRange = SLIDER_VALUE_MIN..SLIDER_VALUE_MAX,
                                    steps = 3,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accent,
                                        activeTrackColor = colors.accent,
                                        inactiveTrackColor = colors.onSurfaceSecondary.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = crossfadeValueText,
                                    color = colors.onSurfaceSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(SLIDER_VALUE_WIDTH),
                                    textAlign = TextAlign.End
                                )
                            }

                            val selectedCutout = layout.mirrorCutouts.find { it.id == selectedCutoutId }
                            if (selectedCutout != null) {
                                val isSmoothingEnabled = selectedCutout.motionSmoothing
                                val currentSliderIndex = if (!isSmoothingEnabled) {
                                    0
                                } else {
                                    when (smoothingStrength) {
                                        in 0..77 -> 1
                                        in 78..82 -> 2
                                        else -> 3
                                    }
                                }
                                val sliderValueText = when (currentSliderIndex) {
                                    0 -> stringResource(R.string.mirror_smoothing_strength_off)
                                    1 -> stringResource(R.string.mirror_smoothing_strength_light)
                                    2 -> stringResource(R.string.mirror_smoothing_strength_medium)
                                    else -> stringResource(R.string.mirror_smoothing_strength_strong)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.mirror_smoothing_strength_label),
                                        color = colors.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.width(SLIDER_LABEL_WIDTH)
                                    )
                                    Slider(
                                        value = currentSliderIndex.toFloat(),
                                        onValueChange = { indexFloat ->
                                            val idx = indexFloat.roundToInt().coerceIn(0, 3)
                                            val isSmooth = idx > 0
                                            val strength = when (idx) {
                                                1 -> 75
                                                2 -> 80
                                                3 -> 85
                                                else -> layout.mirrorSmoothingStrength
                                            }
                                            val updated = layout.mirrorCutouts.map {
                                                if (it.id == selectedCutoutId) it.copy(motionSmoothing = isSmooth) else it
                                            }
                                            MacroPadState.updateLayout(
                                                layout.copy(
                                                    mirrorCutouts = updated,
                                                    mirrorSmoothingStrength = strength
                                                )
                                            )
                                        },
                                        valueRange = 0f..3f,
                                        steps = 2,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accent,
                                            activeTrackColor = colors.accent,
                                            inactiveTrackColor = colors.onSurfaceSecondary.copy(alpha = 0.24f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = sliderValueText,
                                        color = colors.onSurfaceSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.width(SLIDER_VALUE_WIDTH),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }

                            // Delete button in the bottom right corner
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                ToolbarIconButton(
                                    icon = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.mirror_editor_delete_cutout),
                                    color = colors.error,
                                    label = stringResource(R.string.mirror_editor_delete_cutout_label),
                                    enabled = selectedCutoutId != null,
                                    onClick = {
                                        val targetId = selectedCutoutId ?: return@ToolbarIconButton
                                        val remaining = layout.mirrorCutouts.filter { it.id != targetId }
                                        MacroPadState.updateLayout(layout.copy(mirrorCutouts = remaining))
                                        AppStateManager.setSelectedCutoutId(remaining.firstOrNull()?.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
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
            .size(HANDLE_SIZE)
            .background(color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
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
        modifier = Modifier.height(32.dp)
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
    srcH: Float
): ScreenCutout {
    val targetRatio = (cutout.destWidth * screenW) / (cutout.destHeight * screenH)
    val factor = targetRatio * (srcH / srcW)

    val centerX = cutout.srcX + cutout.srcWidth / 2f
    val centerY = cutout.srcY + cutout.srcHeight / 2f

    val newW: Float
    val newH: Float

    if (factor > cutout.srcWidth / cutout.srcHeight) {
        newW = cutout.srcWidth
        newH = newW / factor
    } else {
        newH = cutout.srcHeight
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
