package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import java.util.Locale

private const val TAG = "GamepadComponents"

private val GC_CARD_CORNER = 12.dp
private val GC_CARD_MIN_HEIGHT = 56.dp
private val GC_CARD_H_PADDING = 16.dp
private val GC_CARD_V_PADDING = 12.dp
private val GC_FOCUS_BORDER_WIDTH = 2.5.dp
private val GC_DEFAULT_BORDER_WIDTH = 1.dp
private val GC_FOCUS_ELEVATION = 6.dp
private val GC_DEFAULT_ELEVATION = 0.dp
private val GC_ICON_BOX_SIZE = 36.dp
private val GC_ICON_SIZE = 22.dp
private val GC_STATUS_PILL_CORNER = 16.dp
private val GC_STATUS_PILL_H_PADDING = 10.dp
private val GC_STATUS_PILL_V_PADDING = 4.dp
private val GC_STEPPER_BTN_SIZE = 32.dp
private val GC_SWATCH_SIZE = 28.dp
private val GC_SWATCH_CHECK_ICON_SIZE = 16.dp
private val GC_SWATCH_BORDER_WIDTH_ADJUSTING = 3.dp
private val GC_SWATCH_BORDER_WIDTH_DEFAULT = 1.dp
private val GC_PALETTE_CONTAINER_H_PADDING = 8.dp
private val GC_PALETTE_CONTAINER_V_PADDING = 6.dp
private val GC_PALETTE_CONTAINER_BORDER_ADJUSTING = 2.dp
private val GC_PALETTE_CONTAINER_BORDER_DEFAULT = 1.dp
private val GC_COLOR_CARD_CONTENT_SPACING = 8.dp
private val GC_SLIDER_HEIGHT = 24.dp
private val GC_ROW_CONTENT_SPACING = 12.dp
private val GC_TEXT_SIZE_PILL = 12.sp
private val GC_TEXT_SIZE_CAPSULE = 13.sp
private val GC_CAPSULE_TEXT_WIDTH = 140.dp

private const val GC_CARD_FOCUSED_BG_ALPHA = 0.95f
private const val GC_CARD_UNFOCUSED_BG_ALPHA = 0.55f
private const val GC_ACCENT_TINT_ALPHA = 0.2f
private const val GC_DESTRUCTIVE_BG_ALPHA = 0.15f
private const val GC_DESTRUCTIVE_BORDER_ALPHA = 0.6f
private const val GC_SWATCH_BORDER_ALPHA = 0.3f
private const val GC_ANIM_DURATION_MS = 150
private const val GC_UNFOCUSED_MAX_LINES = 2
private const val GC_DEFAULT_SLIDER_STEP = 0.05f

/**
 * Base focusable gamepad card container with glowing accent bezel and spring focus transitions.
 */
@Composable
fun GamepadFocusCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(GC_CARD_CORNER),
    onCustomKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
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

    val isEffectivelyFocused = isFocused || isAdjusting

    LaunchedEffect(isFocused) {
        if (isFocused) {
            recordLastFocused?.invoke(cardFocusRequester)
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
    val animatedBgColor by animateColorAsState(
        targetValue =
            if (isEffectivelyFocused) {
                colors.surface.copy(alpha = GC_CARD_FOCUSED_BG_ALPHA)
            } else {
                colors.surface.copy(alpha = GC_CARD_UNFOCUSED_BG_ALPHA)
            },
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardBgColor",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isEffectivelyFocused) GC_FOCUS_ELEVATION else GC_DEFAULT_ELEVATION,
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardElevation",
    )

    val keyModifier =
        Modifier.onKeyEvent { keyEvent ->
            if (onCustomKeyEvent != null && onCustomKeyEvent(keyEvent)) {
                return@onKeyEvent true
            }
            when (keyEvent.nativeKeyEvent.keyCode) {
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
            Modifier.clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier.focusable(enabled = enabled, interactionSource = interactionSource)
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
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GC_CARD_H_PADDING, vertical = GC_CARD_V_PADDING),
            contentAlignment = Alignment.CenterStart,
        ) {
            content(isEffectivelyFocused)
        }
    }
}

/**
 * Handles standardized gamepad adjustment key events (D-pad Left/Right adjustment, A/B/Enter dismiss).
 */
internal fun handleAdjustmentKeyEvent(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    isAdjusting: Boolean,
    onAdjustLeft: () -> Unit,
    onAdjustRight: () -> Unit,
    onDismissAdjustment: () -> Unit,
): Boolean {
    if (!isAdjusting) return false
    val keyCode = keyEvent.nativeKeyEvent.keyCode
    return if (keyEvent.type == KeyEventType.KeyDown) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                onAdjustLeft()
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                onAdjustRight()
                true
            }

            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                AppLog.d(TAG, "handleAdjustmentKeyEvent: dismissing adjustment mode on keyCode=$keyCode")
                onDismissAdjustment()
                true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                AppLog.d(TAG, "handleAdjustmentKeyEvent: navigating away from adjustment mode on keyCode=$keyCode")
                onDismissAdjustment()
                false
            }

            else -> {
                false
            }
        }
    } else if (keyEvent.type == KeyEventType.KeyUp) {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            -> true

            else -> false
        }
    } else {
        false
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
                .background(bg, RoundedCornerShape(8.dp)),
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
 * Shared title and adaptive animated description block for gamepad settings cards.
 */
@Composable
fun GamepadCardText(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    isFocused: Boolean = false,
    isDestructive: Boolean = false,
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (isFocused) Int.MAX_VALUE else GC_UNFOCUSED_MAX_LINES,
                overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
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
    isHighlighted: Boolean = false,
    isAccent: Boolean = false,
    isDestructive: Boolean = false,
) {
    val colors = LocalAppColors.current
    val pillBg =
        when {
            isDestructive -> colors.error.copy(alpha = GC_DESTRUCTIVE_BG_ALPHA)
            isAccent -> colors.accent
            isHighlighted -> colors.accent.copy(alpha = GC_ACCENT_TINT_ALPHA)
            else -> colors.surfaceVariant
        }
    val pillTextColor =
        when {
            isDestructive -> colors.error
            isAccent -> colors.onAccent
            isHighlighted -> colors.accent
            else -> colors.onSurfaceSecondary
        }
    val pillBorderColor =
        when {
            isDestructive -> colors.error.copy(alpha = GC_DESTRUCTIVE_BORDER_ALPHA)
            isHighlighted -> colors.accent
            else -> colors.subduedBorder
        }
    val pillBorderWidth = if (isHighlighted) 2.dp else 1.dp

    Box(
        modifier =
            modifier
                .background(pillBg, RoundedCornerShape(GC_STATUS_PILL_CORNER))
                .border(pillBorderWidth, pillBorderColor, RoundedCornerShape(GC_STATUS_PILL_CORNER))
                .padding(horizontal = GC_STATUS_PILL_H_PADDING, vertical = GC_STATUS_PILL_V_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = pillTextColor,
            fontSize = GC_TEXT_SIZE_PILL,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CapsuleArrowButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String? = null,
    size: Dp = GC_STEPPER_BTN_SIZE,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/**
 * Shared interactive stepper / choice capsule with left & right navigation buttons.
 */
@Composable
fun GamepadAdjustableCapsule(
    valueText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isAdjusting: Boolean,
    isFocused: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val capsuleBorderColor = if (isAdjusting) colors.accent else colors.subduedBorder
    val capsuleBorderWidth = if (isAdjusting) 2.dp else 1.dp
    val capsuleBg = if (isAdjusting) colors.accent.copy(alpha = GC_ACCENT_TINT_ALPHA) else colors.surfaceVariant
    val arrowTint = if (isAdjusting || isFocused) colors.accent else colors.onSurfaceSecondary

    Row(
        modifier =
            modifier
                .background(capsuleBg, RoundedCornerShape(GC_STATUS_PILL_CORNER))
                .border(
                    capsuleBorderWidth,
                    capsuleBorderColor,
                    RoundedCornerShape(GC_STATUS_PILL_CORNER),
                ).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleArrowButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.gamepad_previous),
            tint = arrowTint,
            onClick = onPrevious,
            enabled = enabled,
        )

        Text(
            text = valueText,
            color = if (isAdjusting) colors.accent else colors.onSurface,
            fontSize = GC_TEXT_SIZE_CAPSULE,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier =
                Modifier
                    .width(GC_CAPSULE_TEXT_WIDTH)
                    .appMarquee()
                    .padding(horizontal = 8.dp),
        )

        CapsuleArrowButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(R.string.gamepad_next),
            tint = arrowTint,
            onClick = onNext,
            enabled = enabled,
        )
    }
}

/**
 * Shared top-anchored 3-slot row layout ([Icon] -> [Title + Description] -> [Trailing Control]) for settings cards.
 */
@Composable
fun GamepadCardRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    isFocused: Boolean = false,
    isDestructive: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
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
            modifier = Modifier.weight(1f),
        )

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))
            trailingContent()
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
 *
 * In Tier 1 (Row Navigation):
 * - D-Pad Left: passes through to navigate back to the sidebar
 * - D-Pad Up / Down: moves strictly to adjacent card
 * - Button A: enters Tier 2 (Value Adjustment Mode)
 *
 * In Tier 2 (Value Adjustment Mode):
 * - Stepper capsule illuminates with glowing accent border
 * - D-Pad Left: calls onDecrement()
 * - D-Pad Right: calls onIncrement()
 * - Button A: confirms value and exits adjustment
 * - Button B / Back: cancels/exits adjustment without dismissing overlay
 * - D-Pad Up / Down: exits adjustment and moves to adjacent card
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
    )
}

/**
 * Gamepad-first inline carousel choice card.
 *
 * In Tier 1 (Row Navigation):
 * - D-Pad Left: passes through to navigate back to the sidebar
 * - D-Pad Up / Down: moves strictly to adjacent card
 * - Button A: enters Tier 2 (Value Adjustment Mode)
 *
 * In Tier 2 (Value Adjustment Mode):
 * - Choice capsule illuminates with glowing accent border
 * - D-Pad Left: calls onPrevious()
 * - D-Pad Right: calls onNext()
 * - Button A: confirms value and exits adjustment
 * - Button B / Back: cancels/exits adjustment without dismissing overlay
 * - D-Pad Up / Down: exits adjustment and moves to adjacent card
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
    )
}

/**
 * Gamepad-first text input card supporting 2-tier D-pad navigation.
 *
 * In Tier 1 (Row Navigation):
 * - D-Pad Left: passes through to navigate back to the sidebar
 * - D-Pad Up / Down: moves strictly to adjacent card
 * - Button A / Click: enters Tier 2 (Text Editing Mode), requests text field focus
 *
 * In Tier 2 (Text Editing Mode):
 * - Text field is focused and receives software/hardware keyboard input
 * - Enter / Dpad Center: confirms value, exits editing mode
 * - Button B / Back / Escape: cancels/exits editing mode without dismissing overlay
 */
@Composable
fun GamepadTextFieldCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    placeholder: String? = null,
    icon: ImageVector? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onLeftKey: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isEditing by remember { mutableStateOf(false) }
    var draftText by remember(value) { mutableStateOf(value) }
    val cardFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }

    DisposableEffect(isEditing) {
        if (isEditing) {
            AppStateManager.setFullscreenKeyboardActive(true)
        }
        onDispose {
            if (isEditing) {
                AppStateManager.setFullscreenKeyboardActive(false)
            }
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            keyboardController?.hide()
            try {
                textFieldFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus requester unattached
            }
        } else {
            keyboardController?.hide()
            AppStateManager.setFullscreenKeyboardActive(false)
            try {
                cardFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus requester unattached
            }
        }
    }

    BackHandler(enabled = isEditing) {
        onValueChange(draftText.trim())
        isEditing = false
    }

    GamepadFocusCard(
        onClick = {
            if (enabled && !isEditing) {
                isEditing = true
            }
        },
        cardFocusRequester = cardFocusRequester,
        enabled = enabled,
        modifier = modifier,
        isAdjusting = isEditing,
        onLeftKey = onLeftKey,
        onCustomKeyEvent = { keyEvent ->
            if (!isEditing) return@GamepadFocusCard false
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    -> {
                        onValueChange(draftText.trim())
                        isEditing = false
                        true
                    }

                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    -> {
                        if (singleLine) {
                            onValueChange(draftText.trim())
                            isEditing = false
                            true
                        } else {
                            false
                        }
                    }

                    else -> {
                        false
                    }
                }
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    -> singleLine

                    else -> false
                }
            } else {
                false
            }
        },
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    GamepadCardIcon(
                        icon = icon,
                        isFocused = isFocused,
                    )
                    Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))
                }

                GamepadCardText(
                    title = title,
                    description = description,
                    isFocused = isFocused,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))

                GamepadPill(
                    text = if (isEditing) stringResource(R.string.gamepad_action_save) else stringResource(R.string.gamepad_action_edit),
                    isAccent = isEditing,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AppTextField(
                value = if (isEditing) draftText else value,
                onValueChange = {
                    if (isEditing) {
                        draftText = it
                        onValueChange(it)
                    }
                },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = singleLine,
                enabled = enabled,
                readOnly = !isEditing,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        onValueChange(draftText.trim())
                        isEditing = false
                    }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            canFocus = isEditing
                        }.focusRequester(textFieldFocusRequester),
            )
        }
    }
}

/**
 * Gamepad-first action button card.
 */
@Composable
fun GamepadActionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    actionGlyph: GamePadGlyph? = null,
    actionLeadingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    val colors = LocalAppColors.current

    GamepadFocusCard(
        onClick = if (enabled) onClick else null,
        enabled = enabled,
        modifier = modifier,
    ) { isFocused ->
        GamepadCardRow(
            title = title,
            description = description,
            icon = icon,
            isFocused = isFocused,
            isDestructive = isDestructive,
            trailingContent =
                if (actionText != null || actionGlyph != null || actionLeadingContent != null) {
                    {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (actionLeadingContent != null) {
                                actionLeadingContent()
                            }
                            if (actionText != null || actionGlyph != null) {
                                Row(
                                    modifier =
                                        Modifier
                                            .background(
                                                if (isDestructive) {
                                                    colors.error.copy(
                                                        alpha = GC_DESTRUCTIVE_BG_ALPHA,
                                                    )
                                                } else {
                                                    colors.surfaceVariant
                                                },
                                                RoundedCornerShape(GC_STATUS_PILL_CORNER),
                                            ).border(
                                                1.dp,
                                                if (isDestructive) {
                                                    colors.error.copy(alpha = GC_DESTRUCTIVE_BORDER_ALPHA)
                                                } else {
                                                    colors.subduedBorder
                                                },
                                                RoundedCornerShape(GC_STATUS_PILL_CORNER),
                                            ).padding(horizontal = GC_STATUS_PILL_H_PADDING, vertical = GC_STATUS_PILL_V_PADDING),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (actionGlyph != null) {
                                        GamePadGlyphBadge(
                                            glyph = actionGlyph,
                                            tint = if (isDestructive) colors.error else colors.accent,
                                        )
                                    }
                                    if (actionText != null) {
                                        Text(
                                            text = actionText,
                                            color = if (isDestructive) colors.error else colors.onSurface,
                                            fontSize = GC_TEXT_SIZE_PILL,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    null
                },
        )
    }
}

/**
 * Standalone color swatch circle with selection checkmark and adjustment highlight outline.
 */
@Composable
fun GamepadColorSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isAdjusting: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    size: Dp = GC_SWATCH_SIZE,
    contentDescription: String? = null,
) {
    val colors = LocalAppColors.current
    val isHighlighted = isSelected && isAdjusting
    val defaultDesc = stringResource(R.string.gamepad_color_selected)
    val effectiveDesc = contentDescription ?: if (isSelected) defaultDesc else null
    val swatchBorderWidth = if (isHighlighted) GC_SWATCH_BORDER_WIDTH_ADJUSTING else GC_SWATCH_BORDER_WIDTH_DEFAULT
    val swatchBorderColor =
        if (isHighlighted) colors.onSurface else Color.White.copy(alpha = GC_SWATCH_BORDER_ALPHA)

    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(enabled = enabled, onClick = onClick)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .border(
                    swatchBorderWidth,
                    swatchBorderColor,
                    CircleShape,
                ).semantics {
                    if (effectiveDesc != null) {
                        this.contentDescription = effectiveDesc
                    }
                    this.selected = isSelected
                }.then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = effectiveDesc,
                tint = Color.White,
                modifier = Modifier.size(GC_SWATCH_CHECK_ICON_SIZE),
            )
        }
    }
}

/**
 * Gamepad-first color palette card for selecting preset colors.
 *
 * In Tier 1 (Row Navigation):
 * - D-Pad Left: passes through to navigate back to the sidebar
 * - D-Pad Up / Down: moves strictly to adjacent card
 * - Button A: enters Tier 2 (Color Selection Mode)
 *
 * In Tier 2 (Color Selection Mode):
 * - D-Pad Left: selects previous preset color
 * - D-Pad Right: selects next preset color
 * - Button A: confirms selection and exits adjustment
 * - Button B / Back: cancels/exits adjustment without dismissing overlay
 * - D-Pad Up / Down: exits adjustment and moves to adjacent card
 */
@Composable
fun GamepadColorPaletteCard(
    title: String,
    paletteColors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    var isAdjusting by remember { mutableStateOf(false) }

    val onPreviousColor = {
        if (paletteColors.isNotEmpty()) {
            val currentIndex = paletteColors.indexOf(selectedColor)
            val prevIndex =
                if (currentIndex <= 0) {
                    paletteColors.size - 1
                } else {
                    currentIndex - 1
                }
            onColorSelected(paletteColors[prevIndex])
        }
    }

    val onNextColor = {
        if (paletteColors.isNotEmpty()) {
            val currentIndex = paletteColors.indexOf(selectedColor)
            val nextIndex =
                if (currentIndex == -1 || currentIndex >= paletteColors.size - 1) {
                    0
                } else {
                    currentIndex + 1
                }
            onColorSelected(paletteColors[nextIndex])
        }
    }

    GamepadFocusCard(
        onClick = {
            if (enabled) {
                val nextState = !isAdjusting
                AppLog.d(TAG, "GamepadColorPaletteCard: '$title' adjustment mode=$nextState")
                isAdjusting = nextState
            }
        },
        enabled = enabled,
        modifier = modifier,
        isAdjusting = isAdjusting,
        onCustomKeyEvent = { keyEvent ->
            handleAdjustmentKeyEvent(
                keyEvent = keyEvent,
                isAdjusting = isAdjusting,
                onAdjustLeft = onPreviousColor,
                onAdjustRight = onNextColor,
                onDismissAdjustment = { isAdjusting = false },
            )
        },
        onFocusChanged = { focused ->
            if (!focused) {
                isAdjusting = false
            }
        },
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GC_COLOR_CARD_CONTENT_SPACING),
        ) {
            if (icon != null || description != null) {
                GamepadCardRow(
                    title = title,
                    description = description,
                    icon = icon,
                    isFocused = isFocused,
                )
            } else {
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            GamepadColorPaletteGrid(
                paletteColors = paletteColors,
                selectedColor = selectedColor,
                onColorSelected = onColorSelected,
                onPrevious = onPreviousColor,
                onNext = onNextColor,
                isAdjusting = isAdjusting,
                isFocused = isFocused,
                enabled = enabled,
            )
        }
    }
}

/**
 * 2D Gamepad Color Palette Grid with navigation chevrons.
 */
@Composable
fun GamepadColorPaletteGrid(
    paletteColors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    isAdjusting: Boolean = false,
    isFocused: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val containerBorderColor = if (isAdjusting) colors.accent else colors.subduedBorder
    val containerBorderWidth =
        if (isAdjusting) GC_PALETTE_CONTAINER_BORDER_ADJUSTING else GC_PALETTE_CONTAINER_BORDER_DEFAULT
    val containerBg = if (isAdjusting) colors.accent.copy(alpha = GC_ACCENT_TINT_ALPHA) else colors.surfaceVariant
    val arrowTint = if (isAdjusting || isFocused) colors.accent else colors.onSurfaceSecondary

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerBg, RoundedCornerShape(GC_STATUS_PILL_CORNER))
                .border(
                    containerBorderWidth,
                    containerBorderColor,
                    RoundedCornerShape(GC_STATUS_PILL_CORNER),
                ).padding(
                    horizontal = GC_PALETTE_CONTAINER_H_PADDING,
                    vertical = GC_PALETTE_CONTAINER_V_PADDING,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onPrevious != null) {
            CapsuleArrowButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.gamepad_previous),
                tint = arrowTint,
                onClick = onPrevious,
                enabled = enabled,
                size = GC_SWATCH_SIZE,
            )
        }

        paletteColors.forEachIndexed { index, color ->
            val isSelected = color == selectedColor
            val colorDesc = stringResource(R.string.gamepad_color_option, index + 1)
            GamepadColorSwatch(
                color = color,
                isSelected = isSelected,
                isAdjusting = isAdjusting,
                contentDescription = colorDesc,
                onClick = { onColorSelected(color) },
                enabled = enabled,
            )
        }

        if (onNext != null) {
            CapsuleArrowButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.gamepad_next),
                tint = arrowTint,
                onClick = onNext,
                enabled = enabled,
                size = GC_SWATCH_SIZE,
            )
        }
    }
}

/**
 * Uppercase section header label with tracked letter spacing and design token colors.
 */
@Composable
fun GamepadSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.sectionHeaderColor,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        fontWeight = FontWeight.Bold,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * Gamepad-first slider card with continuous value adjustment, LB/RB bumper steps,
 * and glowing focus outline.
 *
 * In Tier 1 (Row Navigation):
 * - D-Pad Left: passes through to navigate back to the sidebar
 * - D-Pad Up / Down: moves strictly to adjacent card
 * - Button A: enters Tier 2 (Value Adjustment Mode)
 *
 * In Tier 2 (Value Adjustment Mode):
 * - Readout pill illuminates with glowing accent border
 * - D-Pad Left: decrements value by step
 * - D-Pad Right: increments value by step
 * - Button A: confirms value and exits adjustment
 * - Button B / Back: cancels/exits adjustment without dismissing overlay
 * - D-Pad Up / Down: exits adjustment and moves to adjacent card
 */
@Composable
fun GamepadSliderCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    valueLabel: String = "%.0f%%".format(value * 100f),
    step: Float = GC_DEFAULT_SLIDER_STEP,
    trackBrush: Brush? = null,
    thumbColor: Color? = null,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    var isAdjusting by remember { mutableStateOf(false) }

    GamepadFocusCard(
        onClick = {
            isAdjusting = !isAdjusting
        },
        modifier = modifier,
        enabled = enabled,
        isAdjusting = isAdjusting,
        onCustomKeyEvent = { keyEvent ->
            handleAdjustmentKeyEvent(
                keyEvent = keyEvent,
                isAdjusting = isAdjusting,
                onAdjustLeft = {
                    val newVal = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(newVal)
                },
                onAdjustRight = {
                    val newVal = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(newVal)
                },
                onDismissAdjustment = { isAdjusting = false },
            )
        },
        onFocusChanged = { focused ->
            if (!focused) {
                isAdjusting = false
            }
        },
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GamepadCardRow(
                title = title,
                description = description,
                icon = icon,
                isFocused = focused,
                trailingContent = {
                    GamepadPill(
                        text = valueLabel,
                        isHighlighted = isAdjusting,
                    )
                },
            )

            if (trackBrush != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(GC_SLIDER_HEIGHT),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(trackBrush)
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                    )

                    Slider(
                        value = value,
                        onValueChange = onValueChange,
                        valueRange = valueRange,
                        enabled = enabled,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = thumbColor ?: if (isAdjusting) colors.accent else colors.onSurface,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    enabled = enabled,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = thumbColor ?: if (isAdjusting) colors.accent else colors.onSurface,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.subduedBorder,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(GC_SLIDER_HEIGHT),
                )
            }
        }
    }
}
