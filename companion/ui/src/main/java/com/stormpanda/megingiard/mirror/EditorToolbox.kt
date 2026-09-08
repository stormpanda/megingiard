package com.stormpanda.megingiard.mirror

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.handle2DAdjustmentKeyEvent
import com.stormpanda.megingiard.ui.launchDirectionalRepeat
import com.stormpanda.megingiard.ui.rememberBezelBrush
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

private const val TAG = "EditorToolbox"

val TOOLBOX_WIDTH = 220.dp
val TOOLBOX_CONTAINER_CORNER = 12.dp
val TOOLBOX_CONTAINER_SHAPE = RoundedCornerShape(TOOLBOX_CONTAINER_CORNER)
val TOOLBOX_CONTAINER_ELEVATION = 16.dp
val TOOLBOX_PADDING_START = 24.dp
val TOOLBOX_PADDING_VERTICAL = 20.dp
val TOOLBOX_INNER_PADDING_H = 8.dp
val TOOLBOX_INNER_PADDING_V = 8.dp
val TOOLBOX_ITEM_SPACING = 6.dp

val TOOLBOX_CARD_CORNER = 8.dp
val TOOLBOX_CARD_SHAPE = RoundedCornerShape(TOOLBOX_CARD_CORNER)
val TOOLBOX_CARD_MIN_HEIGHT = 38.dp
val TOOLBOX_CARD_PADDING_H = 8.dp
val TOOLBOX_CARD_PADDING_V = 5.dp

val TOOLBOX_ICON_BOX_SIZE = 26.dp
val TOOLBOX_ICON_SIZE = 16.dp
val TOOLBOX_ICON_BOX_CORNER = 6.dp
val TOOLBOX_ICON_BOX_SHAPE = RoundedCornerShape(TOOLBOX_ICON_BOX_CORNER)
val TOOLBOX_ROW_SPACING = 8.dp

val TOOLBOX_TEXT_SIZE_TITLE = 11.sp

val TOOLBOX_HANDLE_WIDTH = 40.dp
val TOOLBOX_HANDLE_HEIGHT = 4.dp
val TOOLBOX_HANDLE_V_PADDING_BOTTOM = 8.dp
val TOOLBOX_HANDLE_V_PADDING_TOP = 4.dp

val TOOLBOX_TOGGLE_BUTTON_SIZE = 20.dp
val TOOLBOX_TOGGLE_ICON_SIZE = 14.dp

val TOOLBOX_FOCUS_BORDER_WIDTH = 2.dp
val TOOLBOX_DEFAULT_BORDER_WIDTH = 1.dp
val TOOLBOX_FOCUS_ELEVATION = 4.dp

const val TOOLBOX_SURFACE_ALPHA = 0.70f
const val TOOLBOX_NORMAL_STEP_PX = 10
const val TOOLBOX_FINE_STEP_PX = 1
const val TOOLBOX_ADJUST_TOAST_DURATION_MS = 5000L

/**
 * Reusable vertical docked controller toolbox container with 2D touch drag positioning,
 * boundary clamping, minimize/expand toggle, and smooth spring animations.
 */
@Composable
fun ToolboxContainer(
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    toggleButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    firstItemFocusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    top = TOOLBOX_PADDING_VERTICAL,
                    bottom = TOOLBOX_PADDING_VERTICAL,
                ),
    ) {
        val boxMaxHeight = constraints.maxHeight
        val boxMaxWidth = constraints.maxWidth
        var surfaceHeightPx by remember { mutableIntStateOf(0) }
        val maxOffsetX = (boxMaxWidth - with(density) { TOOLBOX_WIDTH.toPx() }).coerceAtLeast(0f)

        var offsetX by remember {
            mutableFloatStateOf(with(density) { TOOLBOX_PADDING_START.toPx() })
        }
        var offsetY by remember {
            mutableFloatStateOf(0f)
        }

        Surface(
            modifier =
                Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .width(TOOLBOX_WIDTH)
                    .shadow(TOOLBOX_CONTAINER_ELEVATION, TOOLBOX_CONTAINER_SHAPE)
                    .clip(TOOLBOX_CONTAINER_SHAPE)
                    .border(
                        width = TOOLBOX_DEFAULT_BORDER_WIDTH,
                        brush = rememberBezelBrush(),
                        shape = TOOLBOX_CONTAINER_SHAPE,
                    ).animateContentSize(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                    ).onSizeChanged { size ->
                        surfaceHeightPx = size.height
                        val currentMaxOffsetY = (boxMaxHeight - size.height).coerceAtLeast(0).toFloat()
                        if (offsetY > currentMaxOffsetY) {
                            offsetY = currentMaxOffsetY
                        }
                    },
            color = colors.surface.copy(alpha = TOOLBOX_SURFACE_ALPHA),
            shape = TOOLBOX_CONTAINER_SHAPE,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = TOOLBOX_INNER_PADDING_V),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Scrollable content area (collapsed when minimized)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (isMinimized) {
                                    Modifier.height(TOOLBOX_CARD_MIN_HEIGHT)
                                } else {
                                    Modifier
                                },
                            ).verticalScroll(rememberScrollState())
                            .padding(horizontal = TOOLBOX_INNER_PADDING_H),
                    verticalArrangement = Arrangement.spacedBy(TOOLBOX_ITEM_SPACING),
                ) {
                    content()
                }

                // Bottom Drag Handle (Outside Scroll Container) with 2D Drag & Collapse Toggle
                ToolboxDragHandle(
                    isMinimized = isMinimized,
                    onToggleMinimize = onToggleMinimize,
                    onDrag = { dx, dy ->
                        val currentMaxOffsetY = (boxMaxHeight - surfaceHeightPx).coerceAtLeast(0).toFloat()
                        offsetX = (offsetX + dx).coerceIn(0f, maxOffsetX)
                        offsetY = (offsetY + dy).coerceIn(0f, currentMaxOffsetY)
                    },
                    toggleButtonFocusRequester = toggleButtonFocusRequester,
                    firstItemFocusRequester = firstItemFocusRequester,
                )
            }
        }
    }
}

/**
 * Bottom drag handle anchored at the bottom of the toolbox container for dragging the menu
 * in 2D across Display 0, housing the minimize / expand toggle button on its right flank.
 */
@Composable
fun ToolboxDragHandle(
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    toggleButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    firstItemFocusRequester: FocusRequester? = null,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val buttonBg by animateColorAsState(
        targetValue = if (isFocused) colors.accent.copy(alpha = 0.25f) else colors.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(150),
        label = "toggleButtonBg",
    )
    val buttonBorderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else Color.Transparent,
        animationSpec = tween(150),
        label = "toggleButtonBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = if (isFocused) colors.accent else colors.onSurfaceSecondary,
        animationSpec = tween(150),
        label = "toggleButtonIconTint",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }.padding(
                    start = TOOLBOX_INNER_PADDING_H,
                    end = TOOLBOX_INNER_PADDING_H,
                    top = TOOLBOX_HANDLE_V_PADDING_TOP,
                    bottom = TOOLBOX_HANDLE_V_PADDING_BOTTOM,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Centered Capsule Drag Handle
        Box(
            modifier =
                Modifier
                    .width(TOOLBOX_HANDLE_WIDTH)
                    .height(TOOLBOX_HANDLE_HEIGHT)
                    .clip(CircleShape)
                    .background(colors.onSurfaceSecondary.copy(alpha = 0.4f)),
        )

        // Minimize / Expand Toggle Button anchored to the right
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(TOOLBOX_TOGGLE_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(TOOLBOX_DEFAULT_BORDER_WIDTH, buttonBorderColor, CircleShape)
                    .focusRequester(toggleButtonFocusRequester)
                    .then(
                        if (firstItemFocusRequester != null) {
                            Modifier.focusProperties {
                                down = firstItemFocusRequester
                            }
                        } else {
                            Modifier
                        },
                    ).onKeyEvent { keyEvent ->
                        val keyCode = keyEvent.nativeKeyEvent.keyCode
                        if (keyEvent.type == KeyEventType.KeyUp &&
                            (
                                keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == KeyEvent.KEYCODE_ENTER
                            )
                        ) {
                            onToggleMinimize()
                            true
                        } else {
                            false
                        }
                    }.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggleMinimize,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isMinimized) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                contentDescription = stringResource(if (isMinimized) R.string.mirror_editor_expand else R.string.mirror_editor_minimize),
                tint = iconTint,
                modifier = Modifier.size(TOOLBOX_TOGGLE_ICON_SIZE),
            )
        }
    }
}

/**
 * Base focusable card container scaled proportionally for Display 0 editor toolboxes.
 */
@Composable
fun ToolboxCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    enabled: Boolean = true,
    isFocusedOverride: Boolean = false,
    isDestructive: Boolean = false,
    cardBgColor: Color? = null,
    onLeftKey: (() -> Unit)? = null,
    onRightKey: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onCustomKeyEvent: ((ComposeKeyEvent) -> Boolean)? = null,
    icon: ImageVector,
    title: String,
    trailingContent: (@Composable (isFocused: Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val effectivelyFocused = isFocused || isFocusedOverride

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (effectivelyFocused) TOOLBOX_FOCUS_BORDER_WIDTH else TOOLBOX_DEFAULT_BORDER_WIDTH,
        animationSpec = tween(150),
        label = "cardBorderWidth",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (effectivelyFocused) (if (isDestructive) colors.error else colors.accent) else colors.subduedBorder,
        animationSpec = tween(150),
        label = "cardBorderColor",
    )
    val animatedBgColor =
        if (cardBgColor != null) {
            cardBgColor
        } else {
            val targetBg =
                if (effectivelyFocused) {
                    colors.surface.copy(alpha = 0.90f)
                } else {
                    colors.surface.copy(alpha = 0.40f)
                }
            val bg by animateColorAsState(
                targetValue = targetBg,
                animationSpec = tween(150),
                label = "cardBgColor",
            )
            bg
        }
    val animatedElevation by animateDpAsState(
        targetValue = if (effectivelyFocused) TOOLBOX_FOCUS_ELEVATION else 0.dp,
        animationSpec = tween(150),
        label = "cardElevation",
    )

    var lastCustomConsumedDownKeyCode by remember { mutableIntStateOf(0) }

    val keyModifier =
        Modifier.onKeyEvent { keyEvent ->
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyDown) {
                if (onCustomKeyEvent != null && onCustomKeyEvent(keyEvent)) {
                    lastCustomConsumedDownKeyCode = keyCode
                    return@onKeyEvent true
                }
                lastCustomConsumedDownKeyCode = 0
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                if (lastCustomConsumedDownKeyCode == keyCode && keyCode != 0) {
                    lastCustomConsumedDownKeyCode = 0
                    onCustomKeyEvent?.invoke(keyEvent)
                    return@onKeyEvent true
                }
                if (onCustomKeyEvent != null && onCustomKeyEvent(keyEvent)) {
                    return@onKeyEvent true
                }
            }

            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (keyEvent.type == KeyEventType.KeyUp && enabled) {
                        onClick()
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (keyEvent.type == KeyEventType.KeyUp && enabled && onLeftKey != null) {
                        onLeftKey()
                        true
                    } else {
                        false
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (keyEvent.type == KeyEventType.KeyUp && enabled && onRightKey != null) {
                        onRightKey()
                        true
                    } else {
                        false
                    }
                }

                else -> {
                    false
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TOOLBOX_CARD_MIN_HEIGHT)
                .graphicsLayer {
                    this.shadowElevation = animatedElevation.toPx()
                    this.shape = TOOLBOX_CARD_SHAPE
                    this.clip = false
                }.drawBehind {
                    val outline = TOOLBOX_CARD_SHAPE.createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline = outline,
                        brush = SolidColor(animatedBgColor),
                        style = Fill,
                    )
                    drawOutline(
                        outline = outline,
                        brush = SolidColor(animatedBorderColor),
                        style = Stroke(width = animatedBorderWidth.toPx()),
                    )
                }.focusRequester(cardFocusRequester)
                .onFocusChanged { state ->
                    onFocusChanged?.invoke(state.isFocused)
                }.then(keyModifier)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = TOOLBOX_CARD_PADDING_H, vertical = TOOLBOX_CARD_PADDING_V),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconBg =
                when {
                    isDestructive -> colors.error.copy(alpha = 0.15f)
                    effectivelyFocused -> colors.accent.copy(alpha = 0.15f)
                    else -> colors.surfaceVariant
                }
            val iconTint =
                when {
                    isDestructive -> colors.error
                    effectivelyFocused -> colors.accent
                    else -> colors.onSurfaceSecondary
                }

            Box(
                modifier =
                    Modifier
                        .size(TOOLBOX_ICON_BOX_SIZE)
                        .clip(TOOLBOX_ICON_BOX_SHAPE)
                        .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(TOOLBOX_ICON_SIZE),
                )
            }

            Spacer(modifier = Modifier.width(TOOLBOX_ROW_SPACING))

            Text(
                text = title,
                color =
                    if (isDestructive) {
                        colors.error
                    } else if (enabled) {
                        colors.onSurface
                    } else {
                        colors.onSurfaceSecondary.copy(alpha = 0.4f)
                    },
                fontSize = TOOLBOX_TEXT_SIZE_TITLE,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(TOOLBOX_ROW_SPACING))
                trailingContent(effectivelyFocused)
            }
        }
    }
}

/**
 * Action card within a toolbox that executes a discrete action (e.g. Save, Add, Delete).
 */
@Composable
fun ToolboxActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionBadge: String? = null,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    isAccent: Boolean = false,
    isDestructive: Boolean = false,
    cardBgColor: Color? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    ToolboxCard(
        onClick = onClick,
        icon = icon,
        title = title,
        isDestructive = isDestructive,
        cardBgColor = cardBgColor,
        cardFocusRequester = cardFocusRequester,
        onFocusChanged = onFocusChanged,
        modifier = modifier,
        trailingContent =
            if (actionBadge != null) {
                { isFocused ->
                    GamepadPill(
                        text = actionBadge,
                        isAccent = isAccent,
                        isDestructive = isDestructive,
                        isHighlighted = isFocused,
                    )
                }
            } else {
                null
            },
    )
}

/**
 * Interactive 2D coordinate adjustment card that handles entering/exiting adjustment mode,
 * D-pad moves, R2 resizes, L2 fine-steps, and toast feedback.
 */
@Composable
fun AdjustCoordinatesCard(
    title: String,
    icon: ImageVector,
    onMove: (dx: Int, dy: Int) -> Unit,
    onResize: (dx: Int, dy: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
    resetKey: Any? = null,
    trailingContent: (@Composable (isFocused: Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    var isAdjusting by remember { mutableStateOf(false) }
    var isR2Held by remember { mutableStateOf(false) }
    var isL2Held by remember { mutableStateOf(false) }
    val isR2HeldState = rememberUpdatedState(isR2Held)
    val isL2HeldState = rememberUpdatedState(isL2Held)

    var activeDirectionKey by remember { mutableIntStateOf(0) }
    var activeRepeatJob by remember { mutableStateOf<Job?>(null) }

    fun stopAdjustingImmediate() {
        activeRepeatJob?.cancel()
        activeRepeatJob = null
        activeDirectionKey = 0
        isR2Held = false
        isL2Held = false
    }

    fun dispatchAction(
        dirX: Int,
        dirY: Int,
    ) {
        val stepSize = if (isL2HeldState.value) TOOLBOX_FINE_STEP_PX else TOOLBOX_NORMAL_STEP_PX
        val dx = dirX * stepSize
        val dy = dirY * stepSize
        if (isR2HeldState.value) {
            currentOnResize(dx, dy)
        } else {
            currentOnMove(dx, dy)
        }
    }

    fun startAdjusting(
        keyCode: Int,
        dirX: Int,
        dirY: Int,
    ) {
        if (activeDirectionKey == keyCode && activeRepeatJob?.isActive == true) return
        activeRepeatJob?.cancel()
        activeDirectionKey = keyCode
        dispatchAction(dirX, dirY)
        activeRepeatJob =
            coroutineScope.launchDirectionalRepeat(
                keyCode = keyCode,
                isActiveCheck = { activeDirectionKey == keyCode },
            ) {
                dispatchAction(dirX, dirY)
            }
    }

    fun stopAdjusting(keyCode: Int) {
        if (activeDirectionKey == keyCode) {
            activeRepeatJob?.cancel()
            activeRepeatJob = null
            activeDirectionKey = 0
        }
    }

    LaunchedEffect(resetKey) {
        if (isAdjusting) {
            stopAdjustingImmediate()
            isAdjusting = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAdjustingImmediate()
        }
    }

    ToolboxCard(
        onClick = {
            if (isAdjusting) {
                stopAdjustingImmediate()
                isAdjusting = false
            } else {
                isAdjusting = true
                isR2Held = false
                isL2Held = false
                DialogToastManager.show(
                    message = context.getString(R.string.mirror_editor_adjust_toast),
                    durationMs = TOOLBOX_ADJUST_TOAST_DURATION_MS,
                    icon = icon,
                )
            }
        },
        isFocusedOverride = isAdjusting,
        enabled = enabled,
        cardFocusRequester = cardFocusRequester,
        onFocusChanged = { focused ->
            if (!focused && isAdjusting) {
                stopAdjustingImmediate()
                isAdjusting = false
            }
            onFocusChanged?.invoke(focused)
        },
        cardBgColor = if (isAdjusting) colors.accent.copy(alpha = 0.25f) else null,
        icon = icon,
        title = title,
        onCustomKeyEvent = { event ->
            handle2DAdjustmentKeyEvent(
                keyEvent = event,
                isAdjusting = isAdjusting,
                onStartAdjusting = { keyCode, dirX, dirY -> startAdjusting(keyCode, dirX, dirY) },
                onStopAdjusting = { keyCode -> stopAdjusting(keyCode) },
                onDismissAdjustment = {
                    stopAdjustingImmediate()
                    isAdjusting = false
                },
                onModifierKeyDown = { keyCode ->
                    when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L2 -> {
                            isL2Held = true
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            isR2Held = true
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
                onModifierKeyUp = { keyCode ->
                    when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L2 -> {
                            isL2Held = false
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            isR2Held = false
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
            )
        },
        modifier = modifier,
        trailingContent =
            trailingContent ?: { isFocused ->
                GamepadPill(
                    text = stringResource(if (isAdjusting) R.string.gamepad_action_confirm else R.string.gamepad_action_move),
                    isAccent = isAdjusting,
                    isHighlighted = isFocused,
                )
            },
    )
}
