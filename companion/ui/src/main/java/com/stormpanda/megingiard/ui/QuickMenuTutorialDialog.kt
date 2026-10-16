package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.SwipeGestureProcessor
import com.stormpanda.megingiard.SwipeGestureType
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.triggerHapticFeedback
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "QuickMenuTutorialDialog"

// ── Animation ────────────────────────────────────────────────────────────────
private const val QM_BOUNCE_MIN_PX = -10f
private const val QM_BOUNCE_MAX_PX = 10f
private const val QM_BOUNCE_DURATION_MS = 1000
private const val QM_ALPHA_FULL = 1f
private const val QM_ALPHA_DIM = 0.2f
private const val QM_ALPHA_HIDDEN = 0f
private const val QM_BLINK_STEP_MS = 100
private const val QM_HOLD_DURATION_MS = 1200L
private const val QM_FADE_OUT_DURATION_MS = 300

// ── Appearance ───────────────────────────────────────────────────────────────
private const val QM_SCRIM_ALPHA = 0.6f
private val QM_TITLE_BODY_SPACING = 12.dp
private val QM_ROW_SPACING = 8.dp
private val QM_SUB_ROW_SPACING = 4.dp
private val QM_DESC_SPACING = 2.dp
private val QM_ARROW_EDGE_PADDING = 16.dp
private val QM_ARROW_SIZE = 32.dp

private data class PostReleasePillState(
    val type: SwipeGestureType,
    val offsetDp: Dp,
)

@Composable
fun QuickMenuStepContent(overlayAtBottom: Boolean) {
    val colors = LocalAppColors.current
    val overlayFadeOut by SettingsManager.overlayFadeOut.collectAsStateWithLifecycle()

    Column {
        Text(
            text = stringResource(R.string.quick_menu_tutorial_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(QM_TITLE_BODY_SPACING))
        Text(
            text = stringResource(R.string.quick_menu_tutorial_body),
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(QM_ROW_SPACING))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_overlay_position),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = QM_ROW_SPACING),
            )
            Switch(
                checked = overlayAtBottom,
                onCheckedChange = { SettingsManager.setOverlayAtBottom(it) },
            )
        }
        Spacer(modifier = Modifier.height(QM_SUB_ROW_SPACING))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = QM_ROW_SPACING)) {
                Text(
                    text = stringResource(R.string.settings_overlay_fade_out),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(QM_DESC_SPACING))
                Text(
                    text = stringResource(R.string.settings_overlay_fade_out_desc),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = overlayFadeOut,
                onCheckedChange = { SettingsManager.setOverlayFadeOut(it) },
            )
        }
    }
}

@Composable
fun QuickMenuGestureTrialOverlay(
    overlayAtBottom: Boolean,
    enabled: Boolean = true,
    content: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    var activePostRelease by remember { mutableStateOf<PostReleasePillState?>(null) }
    var postReleaseJob by remember { mutableStateOf<Job?>(null) }
    val postReleaseAlpha = remember { Animatable(QM_ALPHA_FULL) }

    var activeDragType by remember { mutableStateOf<SwipeGestureType?>(null) }
    var currentDragDeltaPx by remember { mutableFloatStateOf(0f) }

    val (
        edgeZonePx,
        swipeThresholdPx,
        quickMenuBarZoneWidthPx,
        kbBarMinX,
        kbBarMaxX,
        tpBarWidthPx,
        tpBarEndPaddingPx,
        tpBarZoneWidthPx,
    ) = rememberQuickMenuGestureMetrics()

    fun onGestureReleased(
        type: SwipeGestureType,
        finalDeltaPx: Float,
    ) {
        val visualDp = computeSwipeVisualOffsetDp(finalDeltaPx, swipeThresholdPx, density)
        AppLog.d(TAG, "Quick Menu gesture trial completed for $type")

        triggerHapticFeedback(context, HapticStrength.MEDIUM)

        activePostRelease = PostReleasePillState(type, visualDp)
        postReleaseJob?.cancel()
        postReleaseJob =
            coroutineScope.launch {
                postReleaseAlpha.snapTo(QM_ALPHA_FULL)
                postReleaseAlpha.animateTo(QM_ALPHA_DIM, tween(QM_BLINK_STEP_MS))
                postReleaseAlpha.animateTo(QM_ALPHA_FULL, tween(QM_BLINK_STEP_MS))
                postReleaseAlpha.animateTo(QM_ALPHA_DIM, tween(QM_BLINK_STEP_MS))
                postReleaseAlpha.animateTo(QM_ALPHA_FULL, tween(QM_BLINK_STEP_MS))
                delay(QM_HOLD_DURATION_MS)
                postReleaseAlpha.animateTo(QM_ALPHA_HIDDEN, tween(QM_FADE_OUT_DURATION_MS))
                activePostRelease = null
            }
    }

    val bounceTransition = rememberInfiniteTransition(label = "quick-menu-arrow-bounce")
    val bounceOffset by bounceTransition.animateFloat(
        initialValue = QM_BOUNCE_MIN_PX,
        targetValue = QM_BOUNCE_MAX_PX,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = QM_BOUNCE_DURATION_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "quick-menu-arrow-y",
    )

    fun getTargetOffset(type: SwipeGestureType): Dp =
        if (activeDragType == type && currentDragDeltaPx > 0f) {
            computeSwipeVisualOffsetDp(currentDragDeltaPx, swipeThresholdPx, density)
        } else {
            0.dp
        }

    val kbAnimatedOffsetDp by animateDpAsState(
        targetValue = getTargetOffset(SwipeGestureType.KEYBOARD),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "kbAnimatedOffset",
    )
    val menuAnimatedOffsetDp by animateDpAsState(
        targetValue = getTargetOffset(SwipeGestureType.MENU),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "menuAnimatedOffset",
    )
    val mouseAnimatedOffsetDp by animateDpAsState(
        targetValue = getTargetOffset(SwipeGestureType.TOUCHPAD),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mouseAnimatedOffset",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = QM_SCRIM_ALPHA))
                .pointerInput(overlayAtBottom, kbBarMinX, kbBarMaxX) {
                    fun makeProgressHandler(type: SwipeGestureType) =
                        { delta: Float, _: Boolean ->
                            activeDragType = type
                            currentDragDeltaPx = delta
                        }

                    val clearDragState = {
                        activeDragType = null
                        currentDragDeltaPx = 0f
                    }

                    fun makeEdgeSwipeHandler(type: SwipeGestureType) =
                        {
                            val finalDelta = currentDragDeltaPx.coerceAtLeast(swipeThresholdPx)
                            clearDragState()
                            onGestureReleased(type, finalDelta)
                        }

                    val qmSwipe =
                        SwipeGestureProcessor(
                            edgeZonePx = edgeZonePx,
                            swipeThresholdPx = swipeThresholdPx,
                            overlayAtBottom = overlayAtBottom,
                            quickMenuBarZoneWidthPx = quickMenuBarZoneWidthPx,
                            onSwipeProgress = makeProgressHandler(SwipeGestureType.MENU),
                            onSwipeCancel = clearDragState,
                            onHapticTick = { triggerHapticFeedback(context, HapticStrength.LIGHT) },
                            onEdgeSwipe = makeEdgeSwipeHandler(SwipeGestureType.MENU),
                        )
                    val kbSwipe =
                        SwipeGestureProcessor(
                            edgeZonePx = edgeZonePx,
                            swipeThresholdPx = swipeThresholdPx,
                            overlayAtBottom = overlayAtBottom,
                            customZoneCheck = { x, _ -> x >= kbBarMinX && x <= kbBarMaxX },
                            onSwipeProgress = makeProgressHandler(SwipeGestureType.KEYBOARD),
                            onSwipeCancel = clearDragState,
                            onHapticTick = { triggerHapticFeedback(context, HapticStrength.LIGHT) },
                            onEdgeSwipe = makeEdgeSwipeHandler(SwipeGestureType.KEYBOARD),
                        )
                    val tpSwipe =
                        SwipeGestureProcessor(
                            edgeZonePx = edgeZonePx,
                            swipeThresholdPx = swipeThresholdPx,
                            overlayAtBottom = overlayAtBottom,
                            customZoneCheck = { x, width ->
                                val tpBarCenter = width - tpBarEndPaddingPx - (tpBarWidthPx / 2f)
                                val tpBarMinX = tpBarCenter - (tpBarZoneWidthPx / 2f)
                                val tpBarMaxX = tpBarCenter + (tpBarZoneWidthPx / 2f)
                                x >= tpBarMinX && x <= tpBarMaxX
                            },
                            onSwipeProgress = makeProgressHandler(SwipeGestureType.TOUCHPAD),
                            onSwipeCancel = clearDragState,
                            onHapticTick = { triggerHapticFeedback(context, HapticStrength.LIGHT) },
                            onEdgeSwipe = makeEdgeSwipeHandler(SwipeGestureType.TOUCHPAD),
                        )

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val firstChange = event.changes.firstOrNull()
                            val x = firstChange?.position?.x ?: 0f
                            val y = firstChange?.position?.y ?: 0f
                            when (event.type) {
                                PointerEventType.Press -> {
                                    qmSwipe.onPress(y, size.height.toFloat(), x, size.width.toFloat())
                                    kbSwipe.onPress(y, size.height.toFloat(), x, size.width.toFloat())
                                    tpSwipe.onPress(y, size.height.toFloat(), x, size.width.toFloat())
                                }

                                PointerEventType.Move -> {
                                    qmSwipe.onMove(y)
                                    kbSwipe.onMove(y)
                                    tpSwipe.onMove(y)
                                    if (qmSwipe.isNearEdge || kbSwipe.isNearEdge || tpSwipe.isNearEdge) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }

                                PointerEventType.Release -> {
                                    val allPointersUp = !event.changes.any { it.pressed }
                                    val wasNearEdge = qmSwipe.isNearEdge || kbSwipe.isNearEdge || tpSwipe.isNearEdge
                                    qmSwipe.onRelease(allPointersUp)
                                    kbSwipe.onRelease(allPointersUp)
                                    tpSwipe.onRelease(allPointersUp)
                                    if (wasNearEdge) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            QuickMenuBarTab(
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                modifier =
                    Modifier
                        .align(
                            if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart,
                        ).padding(start = QuickMenuBarLayout.TAB_PADDING),
            )
            QuickMenuBarTab(
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                modifier =
                    Modifier.align(
                        if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter,
                    ),
            )
            QuickMenuBarTab(
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                modifier =
                    Modifier
                        .align(
                            if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd,
                        ).padding(end = QuickMenuBarLayout.TAB_PADDING),
            )
        }

        content?.invoke()

        if (enabled) {
            QuickMenuArrowHint(
                text = stringResource(R.string.quick_menu_label_keyboard),
                overlayAtBottom = overlayAtBottom,
                bounceOffset = bounceOffset,
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                        .padding(start = QuickMenuBarLayout.TAB_PADDING),
            )

            QuickMenuArrowHint(
                text = stringResource(R.string.quick_menu_label_menu),
                overlayAtBottom = overlayAtBottom,
                bounceOffset = bounceOffset,
                modifier =
                    Modifier.align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter),
            )

            QuickMenuArrowHint(
                text = stringResource(R.string.quick_menu_label_mouse),
                overlayAtBottom = overlayAtBottom,
                bounceOffset = bounceOffset,
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                        .padding(end = QuickMenuBarLayout.TAB_PADDING),
            )

            val isKbPostRelease = activePostRelease?.type == SwipeGestureType.KEYBOARD
            QuickMenuSlidingPill(
                icon = if (isKbPostRelease) Icons.Rounded.Check else Icons.Rounded.Keyboard,
                animatedOffsetDp = if (isKbPostRelease) activePostRelease?.offsetDp ?: 0.dp else kbAnimatedOffsetDp,
                isPastThreshold = isKbPostRelease || (activeDragType == SwipeGestureType.KEYBOARD && currentDragDeltaPx >= swipeThresholdPx),
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                alpha = if (isKbPostRelease) postReleaseAlpha.value else QM_ALPHA_FULL,
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                        .padding(start = QuickMenuBarLayout.SLIDING_PILL_PADDING),
            )

            val isMenuPostRelease = activePostRelease?.type == SwipeGestureType.MENU
            QuickMenuSlidingPill(
                icon = if (isMenuPostRelease) Icons.Rounded.Check else Icons.Rounded.Menu,
                animatedOffsetDp = if (isMenuPostRelease) activePostRelease?.offsetDp ?: 0.dp else menuAnimatedOffsetDp,
                isPastThreshold = isMenuPostRelease || (activeDragType == SwipeGestureType.MENU && currentDragDeltaPx >= swipeThresholdPx),
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                alpha = if (isMenuPostRelease) postReleaseAlpha.value else QM_ALPHA_FULL,
                modifier =
                    Modifier.align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter),
            )

            val isTpPostRelease = activePostRelease?.type == SwipeGestureType.TOUCHPAD
            QuickMenuSlidingPill(
                icon = if (isTpPostRelease) Icons.Rounded.Check else Icons.Rounded.Mouse,
                animatedOffsetDp = if (isTpPostRelease) activePostRelease?.offsetDp ?: 0.dp else mouseAnimatedOffsetDp,
                isPastThreshold = isTpPostRelease || (activeDragType == SwipeGestureType.TOUCHPAD && currentDragDeltaPx >= swipeThresholdPx),
                overlayAtBottom = overlayAtBottom,
                colors = colors,
                alpha = if (isTpPostRelease) postReleaseAlpha.value else QM_ALPHA_FULL,
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                        .padding(end = QuickMenuBarLayout.SLIDING_PILL_PADDING),
            )
        }
    }
}

@Composable
private fun QuickMenuArrowHint(
    text: String,
    overlayAtBottom: Boolean,
    bounceOffset: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val arrowIcon = if (overlayAtBottom) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward
    val topPad = if (overlayAtBottom) 0.dp else QM_ARROW_EDGE_PADDING
    val botPad = if (overlayAtBottom) QM_ARROW_EDGE_PADDING else 0.dp

    Box(
        modifier =
            modifier
                .padding(top = topPad, bottom = botPad)
                .width(QuickMenuBarLayout.TAB_WIDTH),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val label: @Composable () -> Unit = {
                Text(
                    text = text,
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (!overlayAtBottom) {
                label()
                Spacer(Modifier.height(QM_SUB_ROW_SPACING))
            }
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = colors.accent,
                modifier =
                    Modifier
                        .offset(y = bounceOffset.dp)
                        .size(QM_ARROW_SIZE),
            )
            if (overlayAtBottom) {
                Spacer(Modifier.height(QM_SUB_ROW_SPACING))
                label()
            }
        }
    }
}
