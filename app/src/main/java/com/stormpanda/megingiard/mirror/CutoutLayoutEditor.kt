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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "CutoutLayoutEditor"
private val HANDLE_SIZE = 20.dp
private val BORDER_WIDTH = 2.dp
private val TOOLBAR_SHADOW = 6.dp
private val TOOLBAR_CORNER = 8.dp
private val MP_EDGE_ZONE = 40.dp

@Composable
fun CutoutLayoutEditor(
    overlayAtBottom: Boolean
) {
    val colors = LocalAppColors.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val isMultiCutoutEditMode by AppStateManager.isMultiCutoutEditMode.collectAsState()
    val selectedCutoutId by AppStateManager.selectedCutoutId.collectAsState()
    val density = LocalDensity.current
    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val captureSourceWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val captureSourceHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()
    val srcWidth = if (captureSourceWidth > 0) captureSourceWidth.toFloat() else 1920f
    val srcHeight = if (captureSourceHeight > 0) captureSourceHeight.toFloat() else 1080f

    val layout = activeLayout ?: return

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

        if (!isMultiCutoutEditMode) {
            // ── Single Viewport Edit Mode ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            MirrorViewportController.applyZoomPan(
                                zoom, pan.x, pan.y,
                                ScreenCaptureManager.surfaceWidth.value,
                                ScreenCaptureManager.surfaceHeight.value,
                            )
                        }
                    }
            )
        } else {
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
                Box(
                    modifier = Modifier
                        .offset { IntOffset(destLeft.roundToInt(), destTop.roundToInt()) }
                        .size(
                            width = with(density) { destW.toDp() },
                            height = with(density) { destH.toDp() }
                        )
                        .background(if (isSelected) colors.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            width = BORDER_WIDTH,
                            color = if (isSelected) colors.accent else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
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
                    var dragStartX by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartY by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartW by remember(cutout.id) { mutableStateOf(0f) }
                    var dragStartH by remember(cutout.id) { mutableStateOf(0f) }

                    // Top-Left handle
                    ResizeHandleView(
                        offset = IntOffset(
                            destLeft.roundToInt(),
                            destTop.roundToInt()
                        ),
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
                                keepAspectRatio = curCutout.keepAspectRatio,
                                cropRatio = cropRatio,
                                screenW = screenW,
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize TOP_LEFT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Top-Right handle
                    ResizeHandleView(
                        offset = IntOffset(
                            (destLeft + destW - handleSizePx).roundToInt(),
                            destTop.roundToInt()
                        ),
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
                                keepAspectRatio = curCutout.keepAspectRatio,
                                cropRatio = cropRatio,
                                screenW = screenW,
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize TOP_RIGHT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Bottom-Left handle
                    ResizeHandleView(
                        offset = IntOffset(
                            destLeft.roundToInt(),
                            (destTop + destH - handleSizePx).roundToInt()
                        ),
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
                                keepAspectRatio = curCutout.keepAspectRatio,
                                cropRatio = cropRatio,
                                screenW = screenW,
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize BOTTOM_LEFT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Bottom-Right handle
                    ResizeHandleView(
                        offset = IntOffset(
                            (destLeft + destW - handleSizePx).roundToInt(),
                            (destTop + destH - handleSizePx).roundToInt()
                        ),
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
                                keepAspectRatio = curCutout.keepAspectRatio,
                                cropRatio = cropRatio,
                                screenW = screenW,
                                screenH = screenH
                            )
                            if (geom.x != targetX || geom.y != targetY || geom.w != targetWidth || geom.h != targetHeight) {
                                AppLog.d(TAG, "Resize BOTTOM_RIGHT clamped '${curCutout.name}': target=($targetX, $targetY, $targetWidth, $targetHeight) -> clamped=(${geom.x}, ${geom.y}, ${geom.w}, ${geom.h})")
                            }
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )
                }
            }
        }
    }

        // ── Floating Toolbar Card ───────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(if (overlayAtBottom) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    top = if (overlayAtBottom) 24.dp + MP_EDGE_ZONE else 24.dp,
                    bottom = if (overlayAtBottom) 24.dp else 24.dp + MP_EDGE_ZONE
                )
                .shadow(TOOLBAR_SHADOW, RoundedCornerShape(TOOLBAR_CORNER)),
            color = colors.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(TOOLBAR_CORNER),
            border = borderStrokeFor(colors.controlOverlayBorder)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {


                if (!isMultiCutoutEditMode) {
                    // Mode Toggle Button (Single -> Multi)
                    ToolbarButton(
                        text = stringResource(R.string.mirror_editor_multi_mode),
                        color = colors.accent,
                        onClick = {
                            AppStateManager.setMultiCutoutEditMode(true)
                            MacroPadState.updateLayout(layout.copy(mirrorMultiMode = true))
                            AppStateManager.setSelectedCutoutId(layout.mirrorCutouts.firstOrNull()?.id)
                        }
                    )
                } else {
                    // Add Cutout
                    ToolbarButton(
                        text = stringResource(R.string.mirror_editor_add_cutout),
                        color = colors.accent,
                        enabled = true,
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
                                destX = foundX, destY = foundY, destWidth = 0.3f, destHeight = 0.3f
                            )
                            MacroPadState.updateLayout(layout.copy(mirrorCutouts = layout.mirrorCutouts + newCutout))
                            AppStateManager.setSelectedCutoutId(newId)
                        }
                    )

                    selectedCutoutId?.let { cutoutId ->
                        layout.mirrorCutouts.find { it.id == cutoutId }?.let { cutout ->
                            val isLocked = cutout.keepAspectRatio
                            ToolbarButton(
                                text = if (isLocked) {
                                    stringResource(R.string.mirror_editor_aspect_ratio_locked)
                                } else {
                                    stringResource(R.string.mirror_editor_aspect_ratio_free)
                                },
                                color = if (isLocked) colors.accent else colors.onSurfaceSecondary,
                                onClick = {
                                    val updated = layout.mirrorCutouts.map {
                                        if (it.id == cutoutId) {
                                            val nextLocked = !it.keepAspectRatio
                                            var updatedCutout = it.copy(keepAspectRatio = nextLocked)
                                            if (nextLocked) {
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
                                            }
                                            updatedCutout
                                        } else it
                                    }
                                    MacroPadState.updateLayout(layout.copy(mirrorCutouts = updated))
                                }
                            )
                        }
                    }

                    // Edit Crop of selected
                    ToolbarButton(
                        text = stringResource(R.string.mirror_editor_edit_crop),
                        color = colors.accent,
                        enabled = selectedCutoutId != null,
                        onClick = {
                            AppStateManager.setActiveCropCutoutId(selectedCutoutId)
                        }
                    )

                    // Delete Selected
                    ToolbarButton(
                        text = stringResource(R.string.mirror_editor_delete_cutout),
                        color = colors.error,
                        enabled = selectedCutoutId != null,
                        onClick = {
                            val targetId = selectedCutoutId ?: return@ToolbarButton
                            val remaining = layout.mirrorCutouts.filter { it.id != targetId }
                            MacroPadState.updateLayout(layout.copy(mirrorCutouts = remaining))
                            AppStateManager.setSelectedCutoutId(remaining.firstOrNull()?.id)
                        }
                    )

                    // Mode Toggle Button (Multi -> Single)
                    ToolbarButton(
                        text = stringResource(R.string.mirror_editor_single_mode),
                        color = colors.onSurfaceSecondary,
                        onClick = {
                            AppStateManager.setMultiCutoutEditMode(false)
                            MacroPadState.updateLayout(layout.copy(mirrorMultiMode = false))
                            AppStateManager.setSelectedCutoutId(null)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Done / Exit button
                ToolbarButton(
                    text = stringResource(R.string.mirror_editor_done),
                    color = colors.accent,
                    onClick = {
                        AppStateManager.setViewportEditActive(false)
                    }
                )
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
            .background(color, RoundedCornerShape(4.dp))
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

// Small helper since BorderStroke needs it
@Composable
private fun borderStrokeFor(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
