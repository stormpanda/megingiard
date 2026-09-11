package com.stormpanda.megingiard.mirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.DialogToastPill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.isBackKey
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val TAG = "AnchorSelectorOverlay"
private const val MIN_ANCHOR_SIZE = 0.04f
private const val ASO_SCRIM_ALPHA = 0.40f
private const val ASO_INITIAL_FOCUS_DELAY_MS = 80L

private val ASO_BORDER_WIDTH = 2.dp

private val ASO_EDGE_HANDLE_LENGTH = 32.dp
private val ASO_EDGE_HANDLE_THICKNESS = 6.dp
private val ASO_EDGE_HANDLE_MARGIN = 6.dp
private val ASO_EDGE_TOUCH_LENGTH = 56.dp
private val ASO_EDGE_TOUCH_THICKNESS = 36.dp
private val ASO_EDGE_HANDLE_CORNER = 3.dp
private val ASO_EDGE_HANDLE_SHAPE = RoundedCornerShape(ASO_EDGE_HANDLE_CORNER)

/**
 * Interactive full-screen overlay rendered on Display 0 for positioning and resizing
 * a custom presence reference anchor for a [ScreenCutout].
 *
 * Surrounds the target anchor with a semi-transparent scrim, renders prominent vertical and horizontal
 * edge drag handles without internal badges, and hosts a reusable controller toolbox for 2D adjustments.
 */
@Composable
fun AnchorSelectorOverlay(
    layoutId: String,
    onDismiss: () -> Unit = {},
) {
    AppLog.d(TAG, "AnchorSelectorOverlay composed for layoutId=$layoutId")
    val colors = LocalAppColors.current
    val activeProfile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    val profiles by MacroPadState.profiles.collectAsStateWithLifecycle()
    val layout =
        activeProfile?.layouts?.find { it.id == layoutId }
            ?: profiles.flatMap { it.layouts }.find { it.id == layoutId }
            ?: return
    val currentLayoutState = rememberUpdatedState(layout)
    val density = LocalDensity.current

    var isMinimized by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }
    val collapseButtonFocusRequester = remember { FocusRequester() }
    val activeToast by DialogToastManager.currentToast.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            delay(ASO_INITIAL_FOCUS_DELAY_MS)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus requester not attached
            }
        }
    }

    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.focusRecoveryEvents.collect {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }

    // Root key handler to catch Back / B-Button
    val rootKeyModifier =
        Modifier.onKeyEvent { keyEvent ->
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyUp && isBackKey(keyCode)) {
                onDismiss()
                true
            } else {
                false
            }
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .then(rootKeyModifier)
                .background(Color.Transparent),
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        if (screenW <= 0f || screenH <= 0f) return@BoxWithConstraints

        fun getCurrentCrop(): AnchorCrop {
            val a = currentLayoutState.value.visualAnchor
            return AnchorCrop(a.srcX, a.srcY, a.srcWidth, a.srcHeight)
        }

        val effectiveCrop = getCurrentCrop()
        val anchorLeft = effectiveCrop.x * screenW
        val anchorTop = effectiveCrop.y * screenH
        val anchorW = effectiveCrop.width * screenW
        val anchorH = effectiveCrop.height * screenH

        fun updateAnchorCrop(
            newX: Float,
            newY: Float,
            newW: Float,
            newH: Float,
        ) {
            val clampedW = newW.coerceIn(MIN_ANCHOR_SIZE, (1f - newX).coerceAtLeast(MIN_ANCHOR_SIZE))
            val clampedH = newH.coerceIn(MIN_ANCHOR_SIZE, (1f - newY).coerceAtLeast(MIN_ANCHOR_SIZE))
            val clampedX = newX.coerceIn(0f, (1f - clampedW).coerceAtLeast(0f))
            val clampedY = newY.coerceIn(0f, (1f - clampedH).coerceAtLeast(0f))

            val curLayout = currentLayoutState.value
            val updatedAnchor =
                curLayout.visualAnchor.copy(
                    enabled = true,
                    srcX = clampedX,
                    srcY = clampedY,
                    srcWidth = clampedW,
                    srcHeight = clampedH,
                )
            MacroPadState.updateLayout(curLayout.copy(visualAnchor = updatedAnchor))
        }

        // 1. Semi-transparent scrim rects surrounding the anchor region
        // Top scrim
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, 0) }
                    .size(
                        width = this@BoxWithConstraints.maxWidth,
                        height = with(density) { anchorTop.toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = ASO_SCRIM_ALPHA)),
        )
        // Bottom scrim
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, (anchorTop + anchorH).roundToInt()) }
                    .size(
                        width = this@BoxWithConstraints.maxWidth,
                        height = with(density) { (screenH - (anchorTop + anchorH)).coerceAtLeast(0f).toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = ASO_SCRIM_ALPHA)),
        )
        // Left scrim
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, anchorTop.roundToInt()) }
                    .size(
                        width = with(density) { anchorLeft.toDp() },
                        height = with(density) { anchorH.toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = ASO_SCRIM_ALPHA)),
        )
        // Right scrim
        Box(
            modifier =
                Modifier
                    .offset { IntOffset((anchorLeft + anchorW).roundToInt(), anchorTop.roundToInt()) }
                    .size(
                        width = with(density) { (screenW - (anchorLeft + anchorW)).coerceAtLeast(0f).toDp() },
                        height = with(density) { anchorH.toDp() },
                    ).background(MaterialTheme.colorScheme.scrim.copy(alpha = ASO_SCRIM_ALPHA)),
        )

        // 2. Anchor Bounding Box
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(anchorLeft.roundToInt(), anchorTop.roundToInt()) }
                    .size(
                        width = with(density) { anchorW.toDp() },
                        height = with(density) { anchorH.toDp() },
                    ).border(ASO_BORDER_WIDTH, colors.accent)
                    .pointerInput(layoutId) {
                        var boxDragStartX = 0f
                        var boxDragStartY = 0f
                        var boxDragStartW = 0f
                        var boxDragStartH = 0f
                        var accumulatedX = 0f
                        var accumulatedY = 0f
                        detectDragGestures(
                            onDragStart = {
                                val curCrop = getCurrentCrop()
                                boxDragStartX = curCrop.x
                                boxDragStartY = curCrop.y
                                boxDragStartW = curCrop.width
                                boxDragStartH = curCrop.height
                                accumulatedX = 0f
                                accumulatedY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedX += dragAmount.x
                                accumulatedY += dragAmount.y
                                val newX = (boxDragStartX + accumulatedX / screenW).coerceIn(0f, (1f - boxDragStartW).coerceAtLeast(0f))
                                val newY = (boxDragStartY + accumulatedY / screenH).coerceIn(0f, (1f - boxDragStartH).coerceAtLeast(0f))
                                updateAnchorCrop(newX, newY, boxDragStartW, boxDragStartH)
                            },
                        )
                    },
        )

        // 3. Horizontal and Vertical Edge Resize Handles (Touch)
        var dragStartX by remember(layoutId) { mutableFloatStateOf(0f) }
        var dragStartY by remember(layoutId) { mutableFloatStateOf(0f) }
        var dragStartW by remember(layoutId) { mutableFloatStateOf(0f) }
        var dragStartH by remember(layoutId) { mutableFloatStateOf(0f) }

        fun captureDragStart() {
            val crop = getCurrentCrop()
            dragStartX = crop.x
            dragStartY = crop.y
            dragStartW = crop.width
            dragStartH = crop.height
        }

        val marginPx = with(density) { ASO_EDGE_HANDLE_MARGIN.toPx() }
        val touchLengthPx = with(density) { ASO_EDGE_TOUCH_LENGTH.toPx() }
        val touchThicknessPx = with(density) { ASO_EDGE_TOUCH_THICKNESS.toPx() }
        val handleThicknessPx = with(density) { ASO_EDGE_HANDLE_THICKNESS.toPx() }

        // Top Edge Handle (Horizontal pill)
        val topCenterY = anchorTop - marginPx - handleThicknessPx / 2f
        val topTouchX = (anchorLeft + anchorW / 2f) - touchLengthPx / 2f
        val topTouchY = topCenterY - touchThicknessPx / 2f
        AnchorResizeHandleView(
            offset = IntOffset(topTouchX.roundToInt(), topTouchY.roundToInt()),
            touchWidth = ASO_EDGE_TOUCH_LENGTH,
            touchHeight = ASO_EDGE_TOUCH_THICKNESS,
            handleWidth = ASO_EDGE_HANDLE_LENGTH,
            handleHeight = ASO_EDGE_HANDLE_THICKNESS,
            color = colors.accent,
            onDragStart = { captureDragStart() },
            onDrag = { _, totalDy ->
                val bottom = dragStartY + dragStartH
                val newY = (dragStartY + totalDy / screenH).coerceIn(0f, bottom - MIN_ANCHOR_SIZE)
                updateAnchorCrop(dragStartX, newY, dragStartW, bottom - newY)
            },
        )

        // Bottom Edge Handle (Horizontal pill)
        val bottomCenterY = anchorTop + anchorH + marginPx + handleThicknessPx / 2f
        val bottomTouchX = (anchorLeft + anchorW / 2f) - touchLengthPx / 2f
        val bottomTouchY = bottomCenterY - touchThicknessPx / 2f
        AnchorResizeHandleView(
            offset = IntOffset(bottomTouchX.roundToInt(), bottomTouchY.roundToInt()),
            touchWidth = ASO_EDGE_TOUCH_LENGTH,
            touchHeight = ASO_EDGE_TOUCH_THICKNESS,
            handleWidth = ASO_EDGE_HANDLE_LENGTH,
            handleHeight = ASO_EDGE_HANDLE_THICKNESS,
            color = colors.accent,
            onDragStart = { captureDragStart() },
            onDrag = { _, totalDy ->
                val newH = ((dragStartY + dragStartH + totalDy / screenH).coerceIn(dragStartY + MIN_ANCHOR_SIZE, 1f)) - dragStartY
                updateAnchorCrop(dragStartX, dragStartY, dragStartW, newH)
            },
        )

        // Left Edge Handle (Vertical pill)
        val leftCenterX = anchorLeft - marginPx - handleThicknessPx / 2f
        val leftTouchX = leftCenterX - touchThicknessPx / 2f
        val leftTouchY = (anchorTop + anchorH / 2f) - touchLengthPx / 2f
        AnchorResizeHandleView(
            offset = IntOffset(leftTouchX.roundToInt(), leftTouchY.roundToInt()),
            touchWidth = ASO_EDGE_TOUCH_THICKNESS,
            touchHeight = ASO_EDGE_TOUCH_LENGTH,
            handleWidth = ASO_EDGE_HANDLE_THICKNESS,
            handleHeight = ASO_EDGE_HANDLE_LENGTH,
            color = colors.accent,
            onDragStart = { captureDragStart() },
            onDrag = { totalDx, _ ->
                val right = dragStartX + dragStartW
                val newX = (dragStartX + totalDx / screenW).coerceIn(0f, right - MIN_ANCHOR_SIZE)
                updateAnchorCrop(newX, dragStartY, right - newX, dragStartH)
            },
        )

        // Right Edge Handle (Vertical pill)
        val rightCenterX = anchorLeft + anchorW + marginPx + handleThicknessPx / 2f
        val rightTouchX = rightCenterX - touchThicknessPx / 2f
        val rightTouchY = (anchorTop + anchorH / 2f) - touchLengthPx / 2f
        AnchorResizeHandleView(
            offset = IntOffset(rightTouchX.roundToInt(), rightTouchY.roundToInt()),
            touchWidth = ASO_EDGE_TOUCH_THICKNESS,
            touchHeight = ASO_EDGE_TOUCH_LENGTH,
            handleWidth = ASO_EDGE_HANDLE_THICKNESS,
            handleHeight = ASO_EDGE_HANDLE_LENGTH,
            color = colors.accent,
            onDragStart = { captureDragStart() },
            onDrag = { totalDx, _ ->
                val newW = ((dragStartX + dragStartW + totalDx / screenW).coerceIn(dragStartX + MIN_ANCHOR_SIZE, 1f)) - dragStartX
                updateAnchorCrop(dragStartX, dragStartY, newW, dragStartH)
            },
        )

        // 4. Floating Controller Toolbox (Right Side)
        ToolboxContainer(
            isMinimized = isMinimized,
            onToggleMinimize = { isMinimized = !isMinimized },
            toggleButtonFocusRequester = collapseButtonFocusRequester,
            firstItemFocusRequester = firstItemFocusRequester,
        ) {
            // Card 0: Adjust Anchor Coordinates (D-pad Move, R2 Resize, L2 Precision)
            AdjustCoordinatesCard(
                title = stringResource(R.string.settings_cutout_anchor_position_title),
                icon = Icons.Rounded.FilterCenterFocus,
                onMove = { dx, dy ->
                    val cur = getCurrentCrop()
                    val newX = (cur.x + dx.toFloat() / screenW).coerceIn(0f, (1f - cur.width).coerceAtLeast(0f))
                    val newY = (cur.y + dy.toFloat() / screenH).coerceIn(0f, (1f - cur.height).coerceAtLeast(0f))
                    updateAnchorCrop(newX, newY, cur.width, cur.height)
                },
                onResize = { dx, dy ->
                    val cur = getCurrentCrop()
                    val newW = (cur.width + dx.toFloat() / screenW).coerceIn(MIN_ANCHOR_SIZE, (1f - cur.x).coerceAtLeast(MIN_ANCHOR_SIZE))
                    val newH = (cur.height + dy.toFloat() / screenH).coerceIn(MIN_ANCHOR_SIZE, (1f - cur.y).coerceAtLeast(MIN_ANCHOR_SIZE))
                    updateAnchorCrop(cur.x, cur.y, newW, newH)
                },
                resetKey = layout.id,
                cardFocusRequester = firstItemFocusRequester,
                modifier =
                    Modifier
                        .firstDeckItem()
                        .focusProperties {
                            up = collapseButtonFocusRequester
                        },
            )

            // Card 1: Confirm & Save
            ToolboxActionCard(
                title = stringResource(R.string.mirror_anchor_selector_done),
                icon = Icons.Rounded.Check,
                actionBadge = stringResource(R.string.gamepad_action_save),
                isAccent = true,
                cardBgColor = colors.accent.copy(alpha = 0.20f),
                onClick = onDismiss,
            )
        }

        // 5. Toast Notifications (Display 0 Top)
        DialogToastPill(
            toast = activeToast,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
        )
    }
}

@Composable
private fun AnchorResizeHandleView(
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
                    .background(color.copy(alpha = 0.85f), ASO_EDGE_HANDLE_SHAPE),
        )
    }
}
