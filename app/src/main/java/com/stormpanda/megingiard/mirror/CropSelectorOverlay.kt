package com.stormpanda.megingiard.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlin.math.roundToInt

private const val TAG = "CropSelectorOverlay"
private const val MIN_CROP_SIZE = 0.05f
private val HANDLE_SIZE = 24.dp
private val BORDER_WIDTH = 2.dp
private val CARD_SHADOW = 8.dp
private val CARD_CORNER = 12.dp

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
    val currentCutoutState = rememberUpdatedState(cutout)
    val currentLayoutState = rememberUpdatedState(layout)
    val density = LocalDensity.current

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
                .background(Color.Black.copy(alpha = 0.7f))
        )
        // Left scrim
        Box(
            modifier = Modifier
                .offset { IntOffset(0, cropTop.roundToInt()) }
                .size(
                    width = with(density) { cropLeft.toDp() },
                    height = with(density) { cropH.toDp() }
                )
                .background(Color.Black.copy(alpha = 0.7f))
        )
        // Right scrim
        Box(
            modifier = Modifier
                .offset { IntOffset((cropLeft + cropW).roundToInt(), cropTop.roundToInt()) }
                .size(
                    width = this@BoxWithConstraints.maxWidth - with(density) { (cropLeft + cropW).toDp() },
                    height = with(density) { cropH.toDp() }
                )
                .background(Color.Black.copy(alpha = 0.7f))
        )
        // Bottom scrim
        Box(
            modifier = Modifier
                .offset { IntOffset(0, (cropTop + cropH).roundToInt()) }
                .size(
                    width = this@BoxWithConstraints.maxWidth,
                    height = this@BoxWithConstraints.maxHeight - with(density) { (cropTop + cropH).toDp() }
                )
                .background(Color.Black.copy(alpha = 0.7f))
        )

        val handleSizePx = with(density) { HANDLE_SIZE.toPx() }

        // 2. Crop rectangle border and drag area
        Box(
            modifier = Modifier
                .offset { IntOffset(cropLeft.roundToInt(), cropTop.roundToInt()) }
                .size(
                    width = with(density) { cropW.toDp() },
                    height = with(density) { cropH.toDp() }
                )
                .border(BORDER_WIDTH, colors.accent)
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
        var dragStartX = 0f
        var dragStartY = 0f
        var dragStartW = 0f
        var dragStartH = 0f

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
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val rightEdge = dragStartX + dragStartW
                val bottomEdge = dragStartY + dragStartH
                
                val newX = (dragStartX + totalDx / screenW).coerceIn(0f, rightEdge - MIN_CROP_SIZE)
                val newW = rightEdge - newX
                val newY = (dragStartY + totalDy / screenH).coerceIn(0f, bottomEdge - MIN_CROP_SIZE)
                val newH = bottomEdge - newY
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) it.copy(srcX = newX, srcWidth = newW, srcY = newY, srcHeight = newH) else it
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
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val bottomEdge = dragStartY + dragStartH
                
                val newW = (dragStartW + totalDx / screenW).coerceIn(MIN_CROP_SIZE, 1f - dragStartX)
                val newY = (dragStartY + totalDy / screenH).coerceIn(0f, bottomEdge - MIN_CROP_SIZE)
                val newH = bottomEdge - newY
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) it.copy(srcWidth = newW, srcY = newY, srcHeight = newH) else it
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
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val rightEdge = dragStartX + dragStartW
                
                val newX = (dragStartX + totalDx / screenW).coerceIn(0f, rightEdge - MIN_CROP_SIZE)
                val newW = rightEdge - newX
                val newH = (dragStartH + totalDy / screenH).coerceIn(MIN_CROP_SIZE, 1f - dragStartY)
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) it.copy(srcX = newX, srcWidth = newW, srcHeight = newH) else it
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
            },
            onDrag = { totalDx, totalDy ->
                val curLayout = currentLayoutState.value
                val newW = (dragStartW + totalDx / screenW).coerceIn(MIN_CROP_SIZE, 1f - dragStartX)
                val newH = (dragStartH + totalDy / screenH).coerceIn(MIN_CROP_SIZE, 1f - dragStartY)
                
                val updated = curLayout.mirrorCutouts.map {
                    if (it.id == cutoutId) it.copy(srcWidth = newW, srcHeight = newH) else it
                }
                MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
            }
        )

        // 4. Control panel card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .shadow(CARD_SHADOW, RoundedCornerShape(CARD_CORNER))
                .background(colors.surface, RoundedCornerShape(CARD_CORNER))
                .border(BORDER_WIDTH, colors.controlOverlayBorder, RoundedCornerShape(CARD_CORNER))
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.mirror_crop_editor_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.mirror_crop_editor_subtitle, cutout.name.ifBlank { "Cutout" }),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text(
                        text = stringResource(R.string.mirror_crop_editor_done),
                        color = colors.onAccent
                    )
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
