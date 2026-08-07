package com.stormpanda.megingiard.ui

import android.content.Context
import android.os.Vibrator
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.SwipeGestureType
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.triggerHapticFeedback
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "QuickMenuTutorialDialog"

// ── Animation ────────────────────────────────────────────────────────────────
private const val QM_BOUNCE_MIN_PX = -10f
private const val QM_BOUNCE_MAX_PX = 10f
private const val QM_BOUNCE_DURATION_MS = 1000

// ── Appearance ───────────────────────────────────────────────────────────────
private const val QM_SCRIM_ALPHA = 0.6f
private val QM_DIALOG_MAX_WIDTH = 340.dp
private val QM_DIALOG_PADDING_HORIZONTAL = 24.dp
private val QM_DIALOG_PADDING_TOP = 20.dp
private val QM_DIALOG_PADDING_BOTTOM = 16.dp
private val QM_DIALOG_SHADOW_ELEVATION = 8.dp
private val QM_DIALOG_CORNER_RADIUS = 28.dp
private val QM_DIALOG_BORDER_WIDTH = 1.dp
private val QM_TITLE_BODY_SPACING = 12.dp
private val QM_BODY_BUTTON_SPACING = 12.dp
private val QM_ARROW_EDGE_PADDING = 16.dp
private val QM_ARROW_SIZE = 32.dp

private const val GESTURE_VISUAL_MULTIPLIER = 1.8f
private const val GESTURE_RESISTANCE_FACTOR = 0.3f
private val GESTURE_MAX_OFFSET = 80.dp
private val PILL_OFFSCREEN_PADDING = 10.dp
private val PILL_SHADOW_ELEVATION = 6.dp
private val PILL_BORDER_WIDTH = 1.dp
private val PILL_ICON_SIZE = 24.dp

private data class PostReleasePillState(
    val type: SwipeGestureType,
    val offsetDp: Dp,
)

@Composable
fun QuickMenuStepContent(overlayAtBottom: Boolean) {
    val colors = LocalAppColors.current
    val overlayFadeOut by SettingsManager.overlayFadeOut.collectAsState()

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
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_overlay_position),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Switch(
                checked = overlayAtBottom,
                onCheckedChange = { SettingsManager.setOverlayAtBottom(it) },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_overlay_fade_out),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(2.dp))
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
    onDismiss: () -> Unit = {},
    showScrim: Boolean = true,
    enabled: Boolean = true,
    content: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    var activePostRelease by remember { mutableStateOf<PostReleasePillState?>(null) }
    val postReleaseAlpha = remember { Animatable(1f) }

    var activeDragType by remember { mutableStateOf<SwipeGestureType?>(null) }
    var currentDragDeltaPx by remember { mutableFloatStateOf(0f) }

    fun onGestureReleased(
        type: SwipeGestureType,
        finalDeltaPx: Float,
    ) {
        val thresholdPx = with(density) { QuickMenuBarLayout.SWIPE_THRESHOLD.toPx() }
        if (finalDeltaPx >= thresholdPx) {
            val visualDelta = finalDeltaPx * GESTURE_VISUAL_MULTIPLIER
            val visualThreshold = thresholdPx * GESTURE_VISUAL_MULTIPLIER
            val visualPx =
                if (visualDelta < visualThreshold) {
                    visualDelta
                } else {
                    visualThreshold + (visualDelta - visualThreshold) * GESTURE_RESISTANCE_FACTOR
                }
            val visualDp = with(density) { visualPx.toDp() }.coerceAtMost(GESTURE_MAX_OFFSET)

            triggerHapticFeedback(context, HapticStrength.MEDIUM)

            activePostRelease = PostReleasePillState(type, visualDp)
            coroutineScope.launch {
                postReleaseAlpha.snapTo(1f)
                // Blink twice
                postReleaseAlpha.animateTo(0.2f, tween(100))
                postReleaseAlpha.animateTo(1f, tween(100))
                postReleaseAlpha.animateTo(0.2f, tween(100))
                postReleaseAlpha.animateTo(1f, tween(100))
                // Reside there for a moment (1.2 seconds)
                delay(1200L)
                // Disappear smoothly (300ms)
                postReleaseAlpha.animateTo(0f, tween(300))
                activePostRelease = null
            }
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
            val threshold = with(density) { QuickMenuBarLayout.SWIPE_THRESHOLD.toPx() }
            val visualDelta = currentDragDeltaPx * GESTURE_VISUAL_MULTIPLIER
            val visualThreshold = threshold * GESTURE_VISUAL_MULTIPLIER
            val visualPx =
                if (visualDelta < visualThreshold) {
                    visualDelta
                } else {
                    visualThreshold + (visualDelta - visualThreshold) * GESTURE_RESISTANCE_FACTOR
                }
            val visualDp = with(density) { visualPx.toDp() }
            visualDp.coerceAtMost(GESTURE_MAX_OFFSET)
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
                .then(
                    if (showScrim) {
                        Modifier.background(Color.Black.copy(alpha = QM_SCRIM_ALPHA))
                    } else {
                        Modifier
                    },
                ).pointerInput(overlayAtBottom) {
                    val swipeThresholdPx = QuickMenuBarLayout.SWIPE_THRESHOLD.toPx()
                    val edgeZonePx = QuickMenuBarLayout.SWIPE_EDGE_ZONE.toPx()
                    val kbZoneWidthPx = QuickMenuBarLayout.TAB_ZONE_WIDTH.toPx()
                    val qmZoneWidthPx = QuickMenuBarLayout.SWIPE_QM_BAR_ZONE_WIDTH.toPx()
                    val tpZoneWidthPx = QuickMenuBarLayout.TAB_ZONE_WIDTH.toPx()

                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val screenWidth = size.width.toFloat()
                            val screenHeight = size.height.toFloat()

                            val inEdgeZone =
                                if (overlayAtBottom) {
                                    down.position.y >= screenHeight - edgeZonePx
                                } else {
                                    down.position.y <= edgeZonePx
                                }

                            if (!inEdgeZone) return@awaitPointerEventScope

                            val type =
                                when {
                                    down.position.x <= kbZoneWidthPx -> SwipeGestureType.KEYBOARD
                                    kotlin.math.abs(down.position.x - screenWidth / 2f) <= qmZoneWidthPx / 2f -> SwipeGestureType.MENU
                                    down.position.x >= screenWidth - tpZoneWidthPx -> SwipeGestureType.TOUCHPAD
                                    else -> null
                                } ?: return@awaitPointerEventScope

                            activeDragType = type
                            currentDragDeltaPx = 0f
                            var accumulated = 0f
                            val pointerId = down.id

                            while (true) {
                                val e = awaitPointerEvent()
                                val change = e.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break
                                val deltaY = if (overlayAtBottom) -change.positionChange().y else change.positionChange().y
                                accumulated = (accumulated + deltaY).coerceAtLeast(0f)
                                currentDragDeltaPx = accumulated
                                change.consume()
                            }

                            onGestureReleased(type, accumulated)
                            activeDragType = null
                            currentDragDeltaPx = 0f
                        }
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        AppLog.d(TAG, "Quick Menu tutorial gesture overlay dismissed via background click")
                        onDismiss()
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Always-visible edge bar controls (when enabled)
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
            // ── 3 Static Edge Labels & Bouncing Arrows ───────────────────────────
            val arrowIcon = if (overlayAtBottom) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward
            val topPad = if (overlayAtBottom) 0.dp else QM_ARROW_EDGE_PADDING
            val botPad = if (overlayAtBottom) QM_ARROW_EDGE_PADDING else 0.dp

            // Keyboard Arrow & Static Label (Left)
            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                        .padding(
                            start = QuickMenuBarLayout.TAB_PADDING,
                            top = topPad,
                            bottom = botPad,
                        ).width(QuickMenuBarLayout.TAB_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!overlayAtBottom) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_keyboard),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier =
                                Modifier
                                    .offset(y = bounceOffset.dp)
                                    .size(QM_ARROW_SIZE),
                        )
                    } else {
                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier =
                                Modifier
                                    .offset(y = bounceOffset.dp)
                                    .size(QM_ARROW_SIZE),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.quick_menu_label_keyboard),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // Menu Arrow & Static Label (Center)
            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                        .padding(
                            top = topPad,
                            bottom = botPad,
                        ).width(QuickMenuBarLayout.TAB_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!overlayAtBottom) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_menu),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier =
                                Modifier
                                    .offset(y = bounceOffset.dp)
                                    .size(QM_ARROW_SIZE),
                        )
                    } else {
                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier =
                                Modifier
                                    .offset(y = bounceOffset.dp)
                                    .size(QM_ARROW_SIZE),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.quick_menu_label_menu),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // Mouse Arrow & Static Label (Right)
            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                        .padding(
                            end = QuickMenuBarLayout.TAB_PADDING,
                            top = topPad,
                            bottom = botPad,
                        ).width(QuickMenuBarLayout.TAB_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!overlayAtBottom) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_mouse),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier =
                                Modifier
                                    .offset(y = bounceOffset.dp)
                                    .size(QM_ARROW_SIZE),
                        )
                    } else {
                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier =
                                Modifier
                                    .offset(y = bounceOffset.dp)
                                    .size(QM_ARROW_SIZE),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.quick_menu_label_mouse),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // ─── Sliding gesture pills on trial drag ─────────────────────────────
            val thresholdPx = with(density) { QuickMenuBarLayout.SWIPE_THRESHOLD.toPx() }

            // Keyboard Pill
            val isKbDragging = activeDragType == SwipeGestureType.KEYBOARD
            val isKbPostRelease = activePostRelease?.type == SwipeGestureType.KEYBOARD
            if (isKbDragging || isKbPostRelease) {
                val isPastThreshold = if (isKbDragging) currentDragDeltaPx >= thresholdPx else true
                val showCheckmark = isKbPostRelease
                val animatedOffset = if (isKbDragging) kbAnimatedOffsetDp else (activePostRelease?.offsetDp ?: 0.dp)
                val alphaVal = if (isKbPostRelease) postReleaseAlpha.value else 1f

                if (animatedOffset > 0.dp && alphaVal > 0f) {
                    val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
                    val yOffset = if (overlayAtBottom) initialOffscreenOffset - animatedOffset else -initialOffscreenOffset + animatedOffset

                    Box(
                        modifier =
                            Modifier
                                .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                                .offset(y = yOffset)
                                .padding(start = QuickMenuBarLayout.SLIDING_PILL_PADDING)
                                .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                                .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                                .graphicsLayer(alpha = alphaVal)
                                .background(
                                    color =
                                        if (isPastThreshold ||
                                            showCheckmark
                                        ) {
                                            colors.accent
                                        } else {
                                            colors.controlOverlay
                                        },
                                    shape = CircleShape,
                                ).border(
                                    width = PILL_BORDER_WIDTH,
                                    color = if (isPastThreshold || showCheckmark) colors.accent else colors.controlOverlayBorder,
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showCheckmark) Icons.Rounded.Check else Icons.Rounded.Keyboard,
                            contentDescription = null,
                            tint = if (isPastThreshold || showCheckmark) colors.onAccent else colors.onSurface,
                            modifier = Modifier.size(PILL_ICON_SIZE),
                        )
                    }
                }
            }

            // Menu Pill
            val isMenuDragging = activeDragType == SwipeGestureType.MENU
            val isMenuPostRelease = activePostRelease?.type == SwipeGestureType.MENU
            if (isMenuDragging || isMenuPostRelease) {
                val isPastThreshold = if (isMenuDragging) currentDragDeltaPx >= thresholdPx else true
                val showCheckmark = isMenuPostRelease
                val animatedOffset = if (isMenuDragging) menuAnimatedOffsetDp else (activePostRelease?.offsetDp ?: 0.dp)
                val alphaVal = if (isMenuPostRelease) postReleaseAlpha.value else 1f

                if (animatedOffset > 0.dp && alphaVal > 0f) {
                    val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
                    val yOffset = if (overlayAtBottom) initialOffscreenOffset - animatedOffset else -initialOffscreenOffset + animatedOffset

                    Box(
                        modifier =
                            Modifier
                                .align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                                .offset(y = yOffset)
                                .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                                .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                                .graphicsLayer(alpha = alphaVal)
                                .background(
                                    color =
                                        if (isPastThreshold ||
                                            showCheckmark
                                        ) {
                                            colors.accent
                                        } else {
                                            colors.controlOverlay
                                        },
                                    shape = CircleShape,
                                ).border(
                                    width = PILL_BORDER_WIDTH,
                                    color = if (isPastThreshold || showCheckmark) colors.accent else colors.controlOverlayBorder,
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showCheckmark) Icons.Rounded.Check else Icons.Rounded.Menu,
                            contentDescription = null,
                            tint = if (isPastThreshold || showCheckmark) colors.onAccent else colors.onSurface,
                            modifier = Modifier.size(PILL_ICON_SIZE),
                        )
                    }
                }
            }

            // Mouse Pill
            val isMouseDragging = activeDragType == SwipeGestureType.TOUCHPAD
            val isMousePostRelease = activePostRelease?.type == SwipeGestureType.TOUCHPAD
            if (isMouseDragging || isMousePostRelease) {
                val isPastThreshold = if (isMouseDragging) currentDragDeltaPx >= thresholdPx else true
                val showCheckmark = isMousePostRelease
                val animatedOffset = if (isMouseDragging) mouseAnimatedOffsetDp else (activePostRelease?.offsetDp ?: 0.dp)
                val alphaVal = if (isMousePostRelease) postReleaseAlpha.value else 1f

                if (animatedOffset > 0.dp && alphaVal > 0f) {
                    val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
                    val yOffset = if (overlayAtBottom) initialOffscreenOffset - animatedOffset else -initialOffscreenOffset + animatedOffset

                    Box(
                        modifier =
                            Modifier
                                .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                                .offset(y = yOffset)
                                .padding(end = QuickMenuBarLayout.SLIDING_PILL_PADDING)
                                .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                                .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                                .graphicsLayer(alpha = alphaVal)
                                .background(
                                    color =
                                        if (isPastThreshold ||
                                            showCheckmark
                                        ) {
                                            colors.accent
                                        } else {
                                            colors.controlOverlay
                                        },
                                    shape = CircleShape,
                                ).border(
                                    width = PILL_BORDER_WIDTH,
                                    color = if (isPastThreshold || showCheckmark) colors.accent else colors.controlOverlayBorder,
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showCheckmark) Icons.Rounded.Check else Icons.Rounded.Mouse,
                            contentDescription = null,
                            tint = if (isPastThreshold || showCheckmark) colors.onAccent else colors.onSurface,
                            modifier = Modifier.size(PILL_ICON_SIZE),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickMenuTutorialDialog(
    overlayAtBottom: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "Quick Menu tutorial dialog shown")
    }

    QuickMenuGestureTrialOverlay(
        overlayAtBottom = overlayAtBottom,
        onDismiss = onDismiss,
        showScrim = true,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = QM_DIALOG_MAX_WIDTH)
                    .padding(horizontal = 16.dp)
                    .shadow(QM_DIALOG_SHADOW_ELEVATION, RoundedCornerShape(QM_DIALOG_CORNER_RADIUS))
                    .clip(RoundedCornerShape(QM_DIALOG_CORNER_RADIUS))
                    .background(colors.surface)
                    .border(
                        QM_DIALOG_BORDER_WIDTH,
                        brush = rememberBezelBrush(),
                        shape = RoundedCornerShape(QM_DIALOG_CORNER_RADIUS),
                    ).padding(
                        start = QM_DIALOG_PADDING_HORIZONTAL,
                        end = QM_DIALOG_PADDING_HORIZONTAL,
                        top = QM_DIALOG_PADDING_TOP,
                        bottom = QM_DIALOG_PADDING_BOTTOM,
                    ).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // absorb clicks so dialog itself doesn't dismiss
                    ),
        ) {
            QuickMenuStepContent(overlayAtBottom = overlayAtBottom)
            Spacer(modifier = Modifier.height(QM_BODY_BUTTON_SPACING))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = {
                        AppLog.d(TAG, "Quick Menu tutorial dialog confirmed")
                        onDismiss()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.welcome_btn_got_it),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
