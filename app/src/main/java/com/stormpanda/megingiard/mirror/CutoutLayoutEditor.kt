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

@Composable
fun CutoutLayoutEditor(
    overlayAtBottom: Boolean
) {
    val colors = LocalAppColors.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val isMultiCutoutEditMode by AppStateManager.isMultiCutoutEditMode.collectAsState()
    val selectedCutoutId by AppStateManager.selectedCutoutId.collectAsState()
    val isPrivilegedMirror by ScreenCaptureManager.isPrivilegedMirror.collectAsState()
    val density = LocalDensity.current

    val layout = activeLayout ?: return

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        if (screenW <= 0f || screenH <= 0f) return@BoxWithConstraints

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
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val curCutout = currentCutoutState.value
                                val curLayout = currentLayoutState.value
                                AppStateManager.setSelectedCutoutId(curCutout.id)
                                val targetX = curCutout.destX + dragAmount.x / screenW
                                val targetY = curCutout.destY + dragAmount.y / screenH
                                
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
                                
                                val updated = curLayout.mirrorCutouts.map {
                                    if (it.id == curCutout.id) it.copy(destX = clampedX, destY = clampedY) else it
                                }
                                MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                            }
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
                    // Top-Left handle
                    ResizeHandleView(
                        offset = IntOffset(
                            (destLeft - handleSizePx / 2f).roundToInt(),
                            (destTop - handleSizePx / 2f).roundToInt()
                        ),
                        color = colors.accent,
                        onDrag = { dragAmount ->
                            val curCutout = currentCutoutState.value
                            val curLayout = currentLayoutState.value
                            val geom = clampCutoutResize(
                                cutoutId = curCutout.id,
                                handle = ResizeHandle.TOP_LEFT,
                                originalX = curCutout.destX,
                                originalY = curCutout.destY,
                                originalWidth = curCutout.destWidth,
                                originalHeight = curCutout.destHeight,
                                targetX = curCutout.destX + dragAmount.x / screenW,
                                targetY = curCutout.destY + dragAmount.y / screenH,
                                targetWidth = curCutout.destWidth - dragAmount.x / screenW,
                                targetHeight = curCutout.destHeight - dragAmount.y / screenH,
                                allCutouts = curLayout.mirrorCutouts
                            )
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Top-Right handle
                    ResizeHandleView(
                        offset = IntOffset(
                            (destLeft + destW - handleSizePx / 2f).roundToInt(),
                            (destTop - handleSizePx / 2f).roundToInt()
                        ),
                        color = colors.accent,
                        onDrag = { dragAmount ->
                            val curCutout = currentCutoutState.value
                            val curLayout = currentLayoutState.value
                            val geom = clampCutoutResize(
                                cutoutId = curCutout.id,
                                handle = ResizeHandle.TOP_RIGHT,
                                originalX = curCutout.destX,
                                originalY = curCutout.destY,
                                originalWidth = curCutout.destWidth,
                                originalHeight = curCutout.destHeight,
                                targetX = curCutout.destX,
                                targetY = curCutout.destY + dragAmount.y / screenH,
                                targetWidth = curCutout.destWidth + dragAmount.x / screenW,
                                targetHeight = curCutout.destHeight - dragAmount.y / screenH,
                                allCutouts = curLayout.mirrorCutouts
                            )
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Bottom-Left handle
                    ResizeHandleView(
                        offset = IntOffset(
                            (destLeft - handleSizePx / 2f).roundToInt(),
                            (destTop + destH - handleSizePx / 2f).roundToInt()
                        ),
                        color = colors.accent,
                        onDrag = { dragAmount ->
                            val curCutout = currentCutoutState.value
                            val curLayout = currentLayoutState.value
                            val geom = clampCutoutResize(
                                cutoutId = curCutout.id,
                                handle = ResizeHandle.BOTTOM_LEFT,
                                originalX = curCutout.destX,
                                originalY = curCutout.destY,
                                originalWidth = curCutout.destWidth,
                                originalHeight = curCutout.destHeight,
                                targetX = curCutout.destX + dragAmount.x / screenW,
                                targetY = curCutout.destY,
                                targetWidth = curCutout.destWidth - dragAmount.x / screenW,
                                targetHeight = curCutout.destHeight + dragAmount.y / screenH,
                                allCutouts = curLayout.mirrorCutouts
                            )
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )

                    // Bottom-Right handle
                    ResizeHandleView(
                        offset = IntOffset(
                            (destLeft + destW - handleSizePx / 2f).roundToInt(),
                            (destTop + destH - handleSizePx / 2f).roundToInt()
                        ),
                        color = colors.accent,
                        onDrag = { dragAmount ->
                            val curCutout = currentCutoutState.value
                            val curLayout = currentLayoutState.value
                            val geom = clampCutoutResize(
                                cutoutId = curCutout.id,
                                handle = ResizeHandle.BOTTOM_RIGHT,
                                originalX = curCutout.destX,
                                originalY = curCutout.destY,
                                originalWidth = curCutout.destWidth,
                                originalHeight = curCutout.destHeight,
                                targetX = curCutout.destX,
                                targetY = curCutout.destY,
                                targetWidth = curCutout.destWidth + dragAmount.x / screenW,
                                targetHeight = curCutout.destHeight + dragAmount.y / screenH,
                                allCutouts = curLayout.mirrorCutouts
                            )
                            val updated = curLayout.mirrorCutouts.map {
                                if (it.id == curCutout.id) it.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h) else it
                            }
                            MacroPadState.updateLayout(curLayout.copy(mirrorCutouts = updated))
                        }
                    )
                }
            }
        }

        // ── Floating Toolbar Card ───────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(if (overlayAtBottom) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(vertical = 24.dp)
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
                if (!isPrivilegedMirror) {
                    Text(
                        text = stringResource(R.string.mirror_editor_multi_cutout_privileged_only),
                        color = colors.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (!isMultiCutoutEditMode) {
                    // Mode Toggle Button (Single -> Multi)
                    ToolbarButton(
                        text = stringResource(R.string.mirror_editor_multi_mode),
                        color = colors.accent,
                        onClick = {
                            AppStateManager.setMultiCutoutEditMode(true)
                            val selectedId = layout.mirrorCutouts.firstOrNull()?.id
                            AppStateManager.setSelectedCutoutId(selectedId)
                        }
                    )
                } else {
                    // Add Cutout
                    ToolbarButton(
                        text = stringResource(R.string.mirror_editor_add_cutout),
                        color = colors.accent,
                        enabled = isPrivilegedMirror,
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
                        enabled = selectedCutoutId != null && layout.mirrorCutouts.size > 1,
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
                            val singleCutout = ScreenCutout(
                                id = UUID.randomUUID().toString(),
                                name = "Full Screen",
                                srcX = 0f, srcY = 0f, srcWidth = 1f, srcHeight = 1f,
                                destX = 0f, destY = 0f, destWidth = 1f, destHeight = 1f
                            )
                            MacroPadState.updateLayout(layout.copy(mirrorCutouts = listOf(singleCutout)))
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
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .offset { offset }
            .size(HANDLE_SIZE)
            .background(color, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount)
                }
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
