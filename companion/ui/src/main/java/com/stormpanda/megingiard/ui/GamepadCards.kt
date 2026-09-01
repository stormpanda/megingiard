package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

private const val TAG = "GamepadCards"

/**
 * Base focusable gamepad card container with glowing accent bezel and spring focus transitions.
 */
@Composable
fun GamepadFocusCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    itemKey: Any? = null,
    enabled: Boolean = true,
    shape: Shape = GC_CARD_SHAPE,
    cardBgColor: Color? = null,
    onCustomKeyEvent: ((ComposeKeyEvent) -> Boolean)? = null,
    onLeftKey: (() -> Unit)? = null,
    onRightKey: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    isAdjusting: Boolean = false,
    content: @Composable (isFocused: Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val recordLastFocused = LocalLastFocusedDeckTracker.current
    val cardRegistry = LocalDeckCardRegistry.current
    val effectiveKey = itemKey ?: cardFocusRequester

    DisposableEffect(effectiveKey, cardFocusRequester) {
        cardRegistry?.invoke(effectiveKey, cardFocusRequester)
        onDispose {
            cardRegistry?.invoke(effectiveKey, null)
        }
    }

    val isEffectivelyFocused = isFocused || isAdjusting

    LaunchedEffect(isFocused) {
        if (isFocused) {
            recordLastFocused?.invoke(effectiveKey, cardFocusRequester)
        }
        onFocusChanged?.invoke(isFocused)
    }

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isEffectivelyFocused) GC_FOCUS_BORDER_WIDTH else GC_DEFAULT_BORDER_WIDTH,
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardBorderWidth",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isEffectivelyFocused) colors.accent else colors.subduedBorder,
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardBorderColor",
    )
    val animatedBgColor =
        if (cardBgColor != null) {
            cardBgColor
        } else {
            val targetBg =
                if (isEffectivelyFocused) {
                    colors.surface.copy(alpha = GC_CARD_FOCUSED_BG_ALPHA)
                } else {
                    colors.surface.copy(alpha = GC_CARD_UNFOCUSED_BG_ALPHA)
                }
            val bg by animateColorAsState(
                targetValue = targetBg,
                animationSpec = tween(GC_ANIM_DURATION_MS),
                label = "cardBgColor",
            )
            bg
        }
    val animatedElevation by animateDpAsState(
        targetValue = if (isEffectivelyFocused) GC_FOCUS_ELEVATION else GC_DEFAULT_ELEVATION,
        animationSpec = tween(GC_ANIM_DURATION_MS),
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
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        if (enabled && onClick != null) {
                            onClick()
                        }
                    }
                    // Consume both KeyDown and KeyUp so clickable does not double-fire
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

    val focusableOrClickModifier =
        if (onClick != null) {
            Modifier
                .focusable(interactionSource = interactionSource)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
        } else {
            Modifier.focusable(interactionSource = interactionSource)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = GC_CARD_MIN_HEIGHT)
                .graphicsLayer {
                    this.shadowElevation = animatedElevation.toPx()
                    this.shape = shape
                    this.clip = false
                }.drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
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
                .then(keyModifier)
                .then(focusableOrClickModifier),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else GC_DISABLED_CARD_ALPHA)
                    .padding(horizontal = GC_CARD_H_PADDING, vertical = GC_CARD_V_PADDING),
            contentAlignment = Alignment.TopStart,
        ) {
            content(isEffectivelyFocused)
        }
    }
}

/**
 * Shared icon container with focus-tinted background for gamepad settings cards.
 */
@Composable
fun GamepadCardIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    isDestructive: Boolean = false,
) {
    val colors = LocalAppColors.current
    val bg =
        when {
            isDestructive -> colors.error.copy(alpha = GC_ACCENT_TINT_ALPHA)
            isFocused -> colors.accent.copy(alpha = GC_ACCENT_TINT_ALPHA)
            else -> colors.surfaceVariant
        }
    val tint =
        when {
            isDestructive -> colors.error
            isFocused -> colors.accent
            else -> colors.onSurfaceSecondary
        }

    Box(
        modifier =
            modifier
                .size(GC_ICON_BOX_SIZE)
                .background(bg, GC_CORNER_8_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(GC_ICON_SIZE),
        )
    }
}

/**
 * Shared position number badge with rounded square background matching [GamepadCardIcon] geometry.
 */
@Composable
fun GamepadPositionBadge(
    index: Int,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
) {
    val colors = LocalAppColors.current
    val bg =
        if (isFocused) {
            colors.accent.copy(alpha = GC_ACCENT_TINT_ALPHA)
        } else {
            colors.surfaceVariant
        }
    val textColor = if (isFocused) colors.accent else colors.onSurfaceSecondary

    Box(
        modifier =
            modifier
                .size(GC_ICON_BOX_SIZE)
                .background(bg, GC_CORNER_8_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "#${index + 1}",
            color = textColor,
            fontSize = GC_TEXT_SIZE_PILL,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Shared title and adaptive animated description block for gamepad settings cards.
 */
@Composable
fun GamepadCardText(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    isFocused: Boolean = false,
    isDestructive: Boolean = false,
    isError: Boolean = false,
    alwaysShowFullDescription: Boolean = false,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier.animateContentSize(),
    ) {
        Text(
            text = title,
            color = if (isDestructive) colors.error else colors.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(GC_SPACING_2))
            Text(
                text = description,
                color = if (isError) colors.error else colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (isFocused || alwaysShowFullDescription) Int.MAX_VALUE else GC_UNFOCUSED_MAX_LINES,
                overflow = if (isFocused || alwaysShowFullDescription) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Shared status, badge, or readout pill for gamepad settings cards.
 */
@Composable
fun GamepadPill(
    text: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    isHighlighted: Boolean = false,
    isAccent: Boolean = false,
    isDestructive: Boolean = false,
    isConfirming: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val pillBg =
        when {
            isConfirming -> colors.error
            isDestructive -> colors.error.copy(alpha = GC_DESTRUCTIVE_BG_ALPHA)
            isAccent -> colors.accent
            isHighlighted -> colors.accent.copy(alpha = GC_ACCENT_TINT_ALPHA)
            !enabled -> colors.surfaceVariant.copy(alpha = GC_DISABLED_CARD_ALPHA)
            else -> colors.surfaceVariant
        }
    val pillTextColor =
        when {
            isConfirming -> colors.surface
            isDestructive -> colors.error
            isAccent -> colors.onAccent
            isHighlighted -> colors.accent
            !enabled -> colors.onSurfaceSecondary
            else -> colors.onSurfaceSecondary
        }
    val pillBorderColor =
        when {
            isConfirming -> colors.error
            isDestructive -> colors.error.copy(alpha = GC_DESTRUCTIVE_BORDER_ALPHA)
            isHighlighted -> colors.accent
            !enabled -> colors.subduedBorder.copy(alpha = GC_DISABLED_CARD_ALPHA)
            else -> colors.subduedBorder
        }
    val pillBorderWidth = if (isHighlighted) 2.dp else 1.dp

    val shape = GC_STATUS_PILL_SHAPE
    Row(
        modifier =
            modifier
                .background(pillBg, shape)
                .border(pillBorderWidth, pillBorderColor, shape)
                .padding(horizontal = GC_STATUS_PILL_H_PADDING, vertical = GC_STATUS_PILL_V_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GC_SPACING_6),
    ) {
        leadingContent?.invoke()
        if (text.isNotEmpty()) {
            Text(
                text = text,
                color = pillTextColor,
                fontSize = GC_TEXT_SIZE_PILL,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Shared layout structure with leading icon, title/description, and trailing action area.
 */
@Composable
fun GamepadCardRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    isFocused: Boolean = false,
    isDestructive: Boolean = false,
    isError: Boolean = false,
    alwaysShowFullDescription: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))
        } else if (icon != null) {
            GamepadCardIcon(
                icon = icon,
                isFocused = isFocused,
                isDestructive = isDestructive,
            )
            Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))
        }

        GamepadCardText(
            title = title,
            description = description,
            isFocused = isFocused,
            isDestructive = isDestructive,
            isError = isError,
            alwaysShowFullDescription = alwaysShowFullDescription,
            modifier = Modifier.weight(1f),
        )

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))
            Box(
                modifier = Modifier.height(GC_ICON_BOX_SIZE),
                contentAlignment = Alignment.CenterEnd,
            ) {
                trailingContent()
            }
        }
    }
}

/**
 * Gamepad-first switch card with illuminated ON/OFF status badge.
 */
@Composable
fun GamepadToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    itemKey: Any? = title,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    GamepadFocusCard(
        onClick =
            if (enabled) {
                { onCheckedChange(!checked) }
            } else {
                null
            },
        enabled = enabled,
        modifier = modifier,
        itemKey = itemKey,
        cardFocusRequester = cardFocusRequester,
        onFocusChanged = onFocusChanged,
    ) { isFocused ->
        GamepadCardRow(
            title = title,
            description = description,
            icon = icon,
            isFocused = isFocused,
            trailingContent = {
                GamepadPill(
                    text = if (checked) stringResource(R.string.gamepad_toggle_on) else stringResource(R.string.gamepad_toggle_off),
                    isAccent = checked,
                )
            },
        )
    }
}

/**
 * Shared base card for adjustable components (steppers, choices) supporting 2-tier D-pad navigation.
 */
@Composable
fun GamepadAdjustableCard(
    title: String,
    valueText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    itemKey: Any? = title,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    var isAdjusting by remember { mutableStateOf(false) }

    GamepadFocusCard(
        onClick = {
            if (onClick != null) {
                onClick()
            } else {
                val nextState = !isAdjusting
                AppLog.d(TAG, "GamepadAdjustableCard: '$title' adjustment mode=$nextState")
                isAdjusting = nextState
            }
        },
        enabled = enabled,
        modifier = modifier,
        itemKey = itemKey,
        isAdjusting = isAdjusting,
        onCustomKeyEvent = { keyEvent ->
            handleAdjustmentKeyEvent(
                keyEvent = keyEvent,
                isAdjusting = isAdjusting,
                onAdjustLeft = onPrevious,
                onAdjustRight = onNext,
                onDismissAdjustment = { isAdjusting = false },
            )
        },
        onFocusChanged = { focused ->
            if (!focused) {
                isAdjusting = false
            }
            onFocusChanged?.invoke(focused)
        },
    ) { isFocused ->
        GamepadCardRow(
            title = title,
            description = description,
            icon = icon,
            isFocused = isFocused,
            trailingContent = {
                GamepadAdjustableCapsule(
                    valueText = valueText,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    isAdjusting = isAdjusting,
                    isFocused = isFocused,
                    enabled = enabled,
                )
            },
        )
    }
}

/**
 * Gamepad-first stepper card for adjusting numeric values directly with D-pad Left/Right.
 */
@Composable
fun GamepadStepperCard(
    title: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    onValueClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    itemKey: Any? = title,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    GamepadAdjustableCard(
        title = title,
        valueText = valueText,
        onPrevious = onDecrement,
        onNext = onIncrement,
        modifier = modifier,
        description = description,
        icon = icon,
        onClick = onValueClick,
        enabled = enabled,
        itemKey = itemKey,
        onFocusChanged = onFocusChanged,
    )
}

/**
 * Gamepad-first inline carousel choice card.
 */
@Composable
fun GamepadChoiceCard(
    title: String,
    selectedText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    itemKey: Any? = title,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    GamepadAdjustableCard(
        title = title,
        valueText = selectedText,
        onPrevious = onPrevious,
        onNext = onNext,
        modifier = modifier,
        description = description,
        icon = icon,
        onClick = onClick,
        enabled = enabled,
        itemKey = itemKey,
        onFocusChanged = onFocusChanged,
    )
}

/**
 * Gamepad-first action button card with trailing action badge or glyph.
 */
@Composable
fun GamepadActionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    actionText: String? = null,
    actionGlyph: GamePadGlyph? = null,
    actionLeadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    cardBgColor: Color? = null,
    pulseOnChanges: Boolean = false,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    alwaysShowFullDescription: Boolean = false,
    itemKey: Any? = title,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    val effectiveBg =
        if (cardBgColor != null) {
            cardBgColor
        } else if (pulseOnChanges) {
            val pulseTransition = rememberInfiniteTransition(label = "actionPulse")
            val pulseFraction by pulseTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = GC_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "actionPulseFraction",
            )
            lerp(
                colors.surface.copy(alpha = GC_PULSE_SURFACE_ALPHA),
                colors.accent.copy(alpha = GC_PULSE_ACCENT_ALPHA),
                pulseFraction,
            )
        } else {
            null
        }

    GamepadFocusCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        itemKey = itemKey,
        cardBgColor = effectiveBg,
        onFocusChanged = onFocusChanged,
    ) { isFocused ->
        GamepadCardRow(
            title = title,
            description = description,
            icon = icon,
            leadingContent = leadingContent,
            isFocused = isFocused,
            isDestructive = isDestructive,
            alwaysShowFullDescription = alwaysShowFullDescription,
            trailingContent =
                trailingContent ?: if (actionText != null || actionGlyph != null || actionLeadingContent != null) {
                    {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GC_SPACING_8),
                        ) {
                            actionLeadingContent?.invoke()
                            if (actionText != null || actionGlyph != null) {
                                GamepadPill(
                                    text = actionText ?: "",
                                    leadingContent =
                                        actionGlyph?.let { glyph ->
                                            {
                                                GamePadGlyphBadge(
                                                    glyph = glyph,
                                                    tint = if (isDestructive) colors.error else colors.accent,
                                                )
                                            }
                                        },
                                    isDestructive = isDestructive,
                                )
                            }
                        }
                    }
                } else {
                    null
                },
        )
    }
}
