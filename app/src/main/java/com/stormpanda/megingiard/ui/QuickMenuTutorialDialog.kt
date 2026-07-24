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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch

private const val TAG = "QuickMenuTutorialDialog"

// ── Animation ────────────────────────────────────────────────────────────────
private const val QM_BOUNCE_MIN_PX = -12f
private const val QM_BOUNCE_MAX_PX = 12f
private const val QM_BOUNCE_DURATION_MS = 1000

// ── Appearance ───────────────────────────────────────────────────────────────
private const val QM_SCRIM_ALPHA = 0.6f
private val QM_DIALOG_MAX_WIDTH = 340.dp
private val QM_DIALOG_PADDING = 24.dp
private val QM_DIALOG_SHADOW_ELEVATION = 8.dp
private val QM_DIALOG_CORNER_RADIUS = 28.dp
private val QM_DIALOG_BORDER_WIDTH = 1.dp
private val QM_TITLE_BODY_SPACING = 16.dp
private val QM_BODY_BUTTON_SPACING = 16.dp
private val QM_ARROW_EDGE_PADDING = 12.dp
private val QM_ARROW_SIZE = 36.dp

private const val GESTURE_VISUAL_MULTIPLIER = 1.8f
private const val GESTURE_RESISTANCE_FACTOR = 0.3f
private val GESTURE_MAX_OFFSET = 80.dp
private val PILL_OFFSCREEN_PADDING = 10.dp
private val PILL_SHADOW_ELEVATION = 6.dp
private val PILL_BORDER_WIDTH = 1.dp
private val PILL_ICON_SIZE = 24.dp

@Composable
fun QuickMenuTutorialDialog(
    overlayAtBottom: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    var kbSuccess by remember { mutableStateOf(false) }
    var menuSuccess by remember { mutableStateOf(false) }
    var mouseSuccess by remember { mutableStateOf(false) }

    val kbBlinkAlpha = remember { Animatable(1f) }
    val menuBlinkAlpha = remember { Animatable(1f) }
    val mouseBlinkAlpha = remember { Animatable(1f) }

    var activeDragType by remember { mutableStateOf<SwipeGestureType?>(null) }
    var currentDragDeltaPx by remember { mutableFloatStateOf(0f) }

    fun triggerSuccessAnimation(type: SwipeGestureType) {
        triggerHapticFeedback(context, HapticStrength.MEDIUM)
        coroutineScope.launch {
            val anim =
                when (type) {
                    SwipeGestureType.KEYBOARD -> {
                        kbSuccess = true
                        kbBlinkAlpha
                    }

                    SwipeGestureType.MENU -> {
                        menuSuccess = true
                        menuBlinkAlpha
                    }

                    SwipeGestureType.TOUCHPAD -> {
                        mouseSuccess = true
                        mouseBlinkAlpha
                    }
                }
            anim.snapTo(1f)
            anim.animateTo(0.2f, tween(120))
            anim.animateTo(1f, tween(120))
            anim.animateTo(0.2f, tween(120))
            anim.animateTo(1f, tween(120))
        }
    }

    LaunchedEffect(Unit) {
        AppLog.d(TAG, "Quick Menu tutorial dialog shown")
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
                .background(Color.Black.copy(alpha = QM_SCRIM_ALPHA))
                .pointerInput(overlayAtBottom) {
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

                            if (accumulated >= swipeThresholdPx) {
                                triggerSuccessAnimation(type)
                            }
                            activeDragType = null
                            currentDragDeltaPx = 0f
                        }
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        AppLog.d(TAG, "Quick Menu tutorial dialog dismissed via background click")
                        onDismiss()
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Centered dialog card
        Column(
            modifier =
                Modifier
                    .widthIn(max = QM_DIALOG_MAX_WIDTH)
                    .padding(QM_DIALOG_PADDING)
                    .shadow(QM_DIALOG_SHADOW_ELEVATION, RoundedCornerShape(QM_DIALOG_CORNER_RADIUS))
                    .clip(RoundedCornerShape(QM_DIALOG_CORNER_RADIUS))
                    .background(colors.surface)
                    .border(
                        QM_DIALOG_BORDER_WIDTH,
                        brush = rememberQuickMenuBezelBrush(),
                        shape = RoundedCornerShape(QM_DIALOG_CORNER_RADIUS),
                    ).padding(QM_DIALOG_PADDING)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // absorb clicks so dialog itself doesn't dismiss
                    ),
        ) {
            Text(
                text = stringResource(R.string.quick_menu_tutorial_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(QM_TITLE_BODY_SPACING))
            Column(
                modifier =
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.quick_menu_tutorial_body),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_overlay_position),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = overlayAtBottom,
                        onCheckedChange = { SettingsManager.setOverlayAtBottom(it) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(QM_BODY_BUTTON_SPACING))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        AppLog.d(TAG, "Quick Menu tutorial dialog confirmed")
                        onDismiss()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.welcome_btn_got_it),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // ── 3 Arrow Indicators & Labels ──────────────────────────────────────
        val arrowAlignBase = if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter
        val arrowIcon = if (overlayAtBottom) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward
        val yPad = if (overlayAtBottom) QM_ARROW_EDGE_PADDING else 0.dp
        val topPad = if (overlayAtBottom) 0.dp else QM_ARROW_EDGE_PADDING

        // Keyboard Arrow (Left)
        Box(
            modifier =
                Modifier
                    .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                    .padding(
                        start = QuickMenuBarLayout.TAB_PADDING,
                        top = topPad,
                        bottom = yPad,
                    ).offset(y = bounceOffset.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(alpha = kbBlinkAlpha.value),
            ) {
                if (!overlayAtBottom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_keyboard),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (kbSuccess) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.actionColorSystem,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(QM_ARROW_SIZE),
                )
                if (overlayAtBottom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_keyboard),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (kbSuccess) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.actionColorSystem,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Menu Arrow (Center)
        Box(
            modifier =
                Modifier
                    .align(arrowAlignBase)
                    .padding(
                        top = topPad,
                        bottom = yPad,
                    ).offset(y = bounceOffset.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(alpha = menuBlinkAlpha.value),
            ) {
                if (!overlayAtBottom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_menu),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (menuSuccess) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.actionColorSystem,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(QM_ARROW_SIZE),
                )
                if (overlayAtBottom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_menu),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (menuSuccess) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.actionColorSystem,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Mouse Arrow (Right)
        Box(
            modifier =
                Modifier
                    .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                    .padding(
                        end = QuickMenuBarLayout.TAB_PADDING,
                        top = topPad,
                        bottom = yPad,
                    ).offset(y = bounceOffset.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(alpha = mouseBlinkAlpha.value),
            ) {
                if (!overlayAtBottom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_mouse),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (mouseSuccess) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.actionColorSystem,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(QM_ARROW_SIZE),
                )
                if (overlayAtBottom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.quick_menu_label_mouse),
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (mouseSuccess) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.actionColorSystem,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // ─── Sliding gesture pills on trial drag ─────────────────────────────
        val thresholdPx = with(density) { QuickMenuBarLayout.SWIPE_THRESHOLD.toPx() }

        // Keyboard Pill
        if (kbAnimatedOffsetDp > 0.dp) {
            val isPastThreshold = currentDragDeltaPx >= thresholdPx
            val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
            val yOffset = if (overlayAtBottom) initialOffscreenOffset - kbAnimatedOffsetDp else -initialOffscreenOffset + kbAnimatedOffsetDp

            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomStart else Alignment.TopStart)
                        .offset(y = yOffset)
                        .padding(start = QuickMenuBarLayout.SLIDING_PILL_PADDING)
                        .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                        .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                        .background(color = if (isPastThreshold || kbSuccess) colors.accent else colors.controlOverlay, shape = CircleShape)
                        .border(
                            width = PILL_BORDER_WIDTH,
                            color = if (isPastThreshold || kbSuccess) colors.accent else colors.controlOverlayBorder,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPastThreshold || kbSuccess) Icons.Rounded.Check else Icons.Rounded.Keyboard,
                    contentDescription = null,
                    tint = if (isPastThreshold || kbSuccess) colors.onAccent else colors.onSurface,
                    modifier = Modifier.size(PILL_ICON_SIZE),
                )
            }
        }

        // Menu Pill
        if (menuAnimatedOffsetDp > 0.dp) {
            val isPastThreshold = currentDragDeltaPx >= thresholdPx
            val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
            val yOffset =
                if (overlayAtBottom) {
                    initialOffscreenOffset - menuAnimatedOffsetDp
                } else {
                    -initialOffscreenOffset +
                        menuAnimatedOffsetDp
                }

            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                        .offset(y = yOffset)
                        .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                        .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                        .background(
                            color =
                                if (isPastThreshold ||
                                    menuSuccess
                                ) {
                                    colors.accent
                                } else {
                                    colors.controlOverlay
                                },
                            shape = CircleShape,
                        ).border(
                            width = PILL_BORDER_WIDTH,
                            color = if (isPastThreshold || menuSuccess) colors.accent else colors.controlOverlayBorder,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPastThreshold || menuSuccess) Icons.Rounded.Check else Icons.Rounded.Menu,
                    contentDescription = null,
                    tint = if (isPastThreshold || menuSuccess) colors.onAccent else colors.onSurface,
                    modifier = Modifier.size(PILL_ICON_SIZE),
                )
            }
        }

        // Mouse Pill
        if (mouseAnimatedOffsetDp > 0.dp) {
            val isPastThreshold = currentDragDeltaPx >= thresholdPx
            val initialOffscreenOffset = QuickMenuBarLayout.SLIDING_PILL_SIZE + PILL_OFFSCREEN_PADDING
            val yOffset =
                if (overlayAtBottom) {
                    initialOffscreenOffset - mouseAnimatedOffsetDp
                } else {
                    -initialOffscreenOffset +
                        mouseAnimatedOffsetDp
                }

            Box(
                modifier =
                    Modifier
                        .align(if (overlayAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                        .offset(y = yOffset)
                        .padding(end = QuickMenuBarLayout.SLIDING_PILL_PADDING)
                        .shadow(elevation = PILL_SHADOW_ELEVATION, shape = CircleShape, clip = false)
                        .size(QuickMenuBarLayout.SLIDING_PILL_SIZE)
                        .background(
                            color =
                                if (isPastThreshold ||
                                    mouseSuccess
                                ) {
                                    colors.accent
                                } else {
                                    colors.controlOverlay
                                },
                            shape = CircleShape,
                        ).border(
                            width = PILL_BORDER_WIDTH,
                            color = if (isPastThreshold || mouseSuccess) colors.accent else colors.controlOverlayBorder,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPastThreshold || mouseSuccess) Icons.Rounded.Check else Icons.Rounded.Mouse,
                    contentDescription = null,
                    tint = if (isPastThreshold || mouseSuccess) colors.onAccent else colors.onSurface,
                    modifier = Modifier.size(PILL_ICON_SIZE),
                )
            }
        }
    }
}
