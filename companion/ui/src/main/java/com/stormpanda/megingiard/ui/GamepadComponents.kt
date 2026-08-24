package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

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
private val GC_SLIDER_TRACK_HEIGHT = 8.dp
private val GC_ROW_CONTENT_SPACING = 12.dp
private val GC_TEXT_SIZE_PILL = 12.sp
private val GC_TEXT_SIZE_CAPSULE = 13.sp
private val GC_CAPSULE_TEXT_WIDTH = 140.dp
private val GC_SPACING_2 = 2.dp
private val GC_SPACING_4 = 4.dp
private val GC_SPACING_6 = 6.dp
private val GC_SPACING_8 = 8.dp
private val GC_SPACING_10 = 10.dp
private val GC_CORNER_4 = 4.dp
private val GC_CORNER_8 = 8.dp

private const val GC_CARD_FOCUSED_BG_ALPHA = 0.95f
private const val GC_CARD_UNFOCUSED_BG_ALPHA = 0.55f
private const val GC_ACCENT_TINT_ALPHA = 0.2f
private const val GC_DESTRUCTIVE_BG_ALPHA = 0.15f
private const val GC_DESTRUCTIVE_BORDER_ALPHA = 0.6f
private const val GC_SWATCH_BORDER_ALPHA = 0.3f
private const val GC_SLIDER_TRACK_BORDER_ALPHA = 0.25f
private const val GC_ANIM_DURATION_MS = 150
private const val GC_SPLIT_ANIM_DURATION_MS = 220
private const val GC_UNFOCUSED_MAX_LINES = 2
private const val GC_DEFAULT_SLIDER_STEP = 0.05f
private const val GC_DISABLED_CARD_ALPHA = 0.38f

private val GC_INFO_BOX_RADIUS = 8.dp
private const val GC_INFO_BOX_BG_ALPHA = 0.5f
private const val GC_INFO_BOX_BORDER_ALPHA = 0.2f
private val GC_INFO_BOX_BORDER_WIDTH = 1.dp
private val GC_INFO_BOX_PADDING_H = 14.dp
private val GC_INFO_BOX_PADDING_V = 12.dp
private val GC_INFO_BOX_SPACING = 10.dp
private val GC_INFO_BOX_ICON_SIZE = 20.dp
private val GC_INFO_BOX_TEXT_SPACING = 2.dp

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
    shape: Shape = RoundedCornerShape(GC_CARD_CORNER),
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
 * Handles standardized gamepad adjustment key events (D-pad Left/Right adjustment, A/B/Enter dismiss).
 */
internal fun handleAdjustmentKeyEvent(
    keyEvent: ComposeKeyEvent,
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
                .background(bg, RoundedCornerShape(GC_CORNER_8)),
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
                .background(bg, RoundedCornerShape(GC_CORNER_8)),
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
                ).padding(horizontal = GC_SPACING_4, vertical = GC_SPACING_2),
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
                    .padding(horizontal = GC_SPACING_8),
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
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onLeftKey: (() -> Unit)? = null,
    itemKey: Any? = title,
) {
    val colors = LocalAppColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isEditing by remember { mutableStateOf(false) }
    var hasBeenEditing by remember { mutableStateOf(false) }
    var draftValue by remember {
        mutableStateOf(TextFieldValue(text = value))
    }
    val cardFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(value) {
        if (!isEditing && draftValue.text != value) {
            draftValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

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
            hasBeenEditing = true
            draftValue = TextFieldValue(text = value, selection = TextRange(0, value.length))
            keyboardController?.hide()
            try {
                textFieldFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus requester unattached
            }
        } else if (hasBeenEditing) {
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
        onValueChange(draftValue.text.trim())
        isEditing = false
    }

    GamepadFocusCard(
        onClick = {
            if (enabled) {
                if (!isEditing) {
                    draftValue = TextFieldValue(text = value, selection = TextRange(0, value.length))
                    isEditing = true
                } else {
                    onValueChange(draftValue.text.trim())
                    isEditing = false
                }
            }
        },
        cardFocusRequester = cardFocusRequester,
        itemKey = itemKey,
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
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    -> {
                        onValueChange(draftValue.text.trim())
                        isEditing = false
                        true
                    }

                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (singleLine) {
                            onValueChange(draftValue.text.trim())
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
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    -> true

                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> singleLine

                    else -> false
                }
            } else {
                false
            }
        },
    ) { isFocused ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(GC_ANIM_DURATION_MS)),
        ) {
            if (!isEditing) {
                val (displayTitle, displayDescription) =
                    when {
                        isError -> {
                            val headline = if (value.isNotBlank()) "\"$value\"" else title
                            headline to description
                        }

                        value.isNotBlank() -> {
                            val headline = "\"$value\""
                            val sub =
                                if (description != null) {
                                    if (isFocused) "$title — $description" else title
                                } else {
                                    title
                                }
                            headline to sub
                        }

                        else -> {
                            title to (description ?: placeholder)
                        }
                    }

                GamepadCardRow(
                    title = displayTitle,
                    description = displayDescription,
                    icon = icon,
                    isFocused = isFocused,
                    isError = isError,
                    trailingContent = {
                        GamepadPill(
                            text = stringResource(R.string.gamepad_action_edit),
                            isHighlighted = isFocused,
                        )
                    },
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
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
                            isError = isError,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.width(GC_ROW_CONTENT_SPACING))

                        Box(
                            modifier = Modifier.height(GC_ICON_BOX_SIZE),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            GamepadPill(
                                text = stringResource(R.string.gamepad_action_save),
                                isAccent = true,
                                modifier =
                                    Modifier.clickable {
                                        onValueChange(draftValue.text.trim())
                                        isEditing = false
                                    },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(GC_SPACING_8))

                    AppTextField(
                        value = draftValue,
                        onValueChange = {
                            draftValue = it
                            onValueChange(it.text)
                        },
                        placeholder = placeholder?.let { { Text(it) } },
                        isError = isError,
                        singleLine = singleLine,
                        enabled = enabled,
                        readOnly = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                onValueChange(draftValue.text.trim())
                                isEditing = false
                            }),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusProperties {
                                    canFocus = true
                                }.focusRequester(textFieldFocusRequester),
                    )
                }
            }
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
    leadingContent: (@Composable () -> Unit)? = null,
    actionText: String? = null,
    actionGlyph: GamePadGlyph? = null,
    actionLeadingContent: (@Composable () -> Unit)? = null,
    cardBgColor: Color? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    alwaysShowFullDescription: Boolean = false,
    itemKey: Any? = title,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    GamepadFocusCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        itemKey = itemKey,
        cardBgColor = cardBgColor,
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
                if (actionText != null || actionGlyph != null || actionLeadingContent != null) {
                    {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GC_SPACING_8),
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
                                                GC_DEFAULT_BORDER_WIDTH,
                                                if (isDestructive) {
                                                    colors.error.copy(alpha = GC_DESTRUCTIVE_BORDER_ALPHA)
                                                } else {
                                                    colors.subduedBorder
                                                },
                                                RoundedCornerShape(GC_STATUS_PILL_CORNER),
                                            ).padding(horizontal = GC_STATUS_PILL_H_PADDING, vertical = GC_STATUS_PILL_V_PADDING),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(GC_SPACING_6),
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
 * State holder for in-flight changes exit confirmation prompt.
 */
@OptIn(ExperimentalFoundationApi::class)
class SaveExitPromptState(
    val showExitPrompt: Boolean,
    val focusRequester: FocusRequester,
    val bringIntoViewRequester: BringIntoViewRequester,
    val onSave: () -> Unit,
    val onDiscard: () -> Unit,
    val dismissPrompt: () -> Unit,
)

/**
 * Remembers a [SaveExitPromptState] that intercepts back navigation (gamepad B button, Escape,
 * or Android Back gesture) when [hasChanges] is true, transitioning the save action row into a
 * split "Save & Exit" / "Discard & Exit" confirmation and automatically scrolling & focusing it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun rememberSaveExitPromptState(
    hasChanges: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
): SaveExitPromptState {
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val inputModeManager = LocalInputModeManager.current
    var showExitPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(hasChanges) {
        if (!hasChanges) {
            showExitPrompt = false
        }
    }

    val registerBackInterceptor = LocalDeckBackInterceptor.current
    val currentHasChanges by rememberUpdatedState(hasChanges)
    val currentShowExitPrompt by rememberUpdatedState(showExitPrompt)
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDiscard by rememberUpdatedState(onDiscard)

    val refocusSaveAction: () -> Unit = {
        try {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
        coroutineScope.launch {
            try {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                bringIntoViewRequester.bringIntoView()
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val handleBack: () -> Boolean = {
        if (currentHasChanges) {
            if (!currentShowExitPrompt) {
                showExitPrompt = true
                refocusSaveAction()
                true
            } else {
                showExitPrompt = false
                refocusSaveAction()
                true
            }
        } else {
            false
        }
    }

    DisposableEffect(registerBackInterceptor) {
        registerBackInterceptor(handleBack)
        onDispose {
            registerBackInterceptor(null)
        }
    }

    BackHandler(enabled = hasChanges) {
        handleBack()
    }

    return remember(showExitPrompt, focusRequester, bringIntoViewRequester) {
        SaveExitPromptState(
            showExitPrompt = showExitPrompt,
            focusRequester = focusRequester,
            bringIntoViewRequester = bringIntoViewRequester,
            onSave = { currentOnSave() },
            onDiscard = { currentOnDiscard() },
            dismissPrompt = {
                showExitPrompt = false
                refocusSaveAction()
            },
        )
    }
}

/**
 * Unified gamepad-first action row for menus and subpages with in-flight changes.
 *
 * Maintains a stable [GamepadActionCard] node in the composition across both normal and exit
 * prompt states to prevent focus detachment and eliminate UI flicker during transitions.
 *
 * In normal mode ([showExitPrompt] = false):
 * - The primary card expands to full width (weight 1f).
 *
 * In exit confirmation mode ([showExitPrompt] = true):
 * - The primary card transitions to "Save & Exit" while preserving active focus.
 * - The secondary "Discard & Exit" card appears beside it sharing the row (weight 1f each).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamepadSaveExitActionRow(
    title: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    saveIcon: ImageVector = Icons.Rounded.Save,
    saveActionText: String = stringResource(R.string.gamepad_action_save),
    saveActionLeadingContent: (@Composable () -> Unit)? = null,
    cardBgColor: Color? = null,
    enabled: Boolean = true,
    showExitPrompt: Boolean = false,
    saveFocusRequester: FocusRequester? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    itemKey: Any? = title,
) {
    val bivrModifier =
        if (bringIntoViewRequester != null) {
            Modifier.bringIntoViewRequester(bringIntoViewRequester)
        } else {
            Modifier
        }

    val saveReqModifier =
        if (saveFocusRequester != null) {
            Modifier.focusRequester(saveFocusRequester)
        } else {
            Modifier
        }

    val splitFraction by animateFloatAsState(
        targetValue = if (showExitPrompt) 1f else 0f,
        animationSpec = tween(durationMillis = GC_SPLIT_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        label = "saveExitSplitFraction",
    )

    val isPromptActive = showExitPrompt || splitFraction > 0.05f
    val effectiveTitle =
        if (isPromptActive) {
            stringResource(R.string.gamepad_action_save_and_exit)
        } else {
            title
        }
    val effectiveDescription =
        if (isPromptActive) {
            stringResource(R.string.gamepad_action_save_and_exit_desc)
        } else {
            description
        }

    BoxWithConstraints(
        modifier = modifier.then(bivrModifier).fillMaxWidth().animateContentSize(),
    ) {
        val totalWidth = maxWidth
        val targetCardWidth = ((totalWidth - GC_SPACING_10) / 2f).coerceAtLeast(0.dp)
        val currentSpacing = GC_SPACING_10 * splitFraction
        val card2VisibleWidth = targetCardWidth * splitFraction
        val card1VisibleWidth =
            (
                totalWidth - (
                    if (splitFraction >
                        0.001f
                    ) {
                        card2VisibleWidth + currentSpacing
                    } else {
                        0.dp
                    }
                )
            ).coerceAtLeast(0.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GamepadActionCard(
                title = effectiveTitle,
                description = effectiveDescription,
                actionText = saveActionText,
                icon = saveIcon,
                cardBgColor = cardBgColor,
                actionLeadingContent = if (!showExitPrompt) saveActionLeadingContent else null,
                enabled = enabled,
                onClick = onSave,
                itemKey = itemKey,
                modifier = Modifier.width(card1VisibleWidth).then(saveReqModifier),
            )

            if (splitFraction > 0.001f) {
                Spacer(modifier = Modifier.width(currentSpacing))

                Box(
                    modifier =
                        Modifier
                            .width(card2VisibleWidth)
                            .clipToBounds()
                            .graphicsLayer {
                                alpha = splitFraction
                            },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    GamepadActionCard(
                        title = stringResource(R.string.gamepad_action_discard_and_exit),
                        description = stringResource(R.string.gamepad_action_discard_and_exit_desc),
                        actionText = stringResource(R.string.gamepad_action_discard),
                        icon = Icons.Rounded.Close,
                        isDestructive = true,
                        onClick = onDiscard,
                        itemKey = "discard_and_exit",
                        modifier = Modifier.requiredWidth(targetCardWidth),
                    )
                }
            }
        }
    }
}

/**
 * Gamepad-first destructive two-step confirmation card.
 *
 * Step 1: Displays initial title/description with action badge (e.g. "Delete").
 * When activated (Button A / click), enters confirming state:
 * - Title changes to [confirmTitle] (e.g. "Really delete 'Profile'?")
 * - Action badge changes to [confirmActionText] (e.g. "Confirm") in high-contrast destructive styling
 *
 * Step 2: Activating again invokes [onConfirm].
 *
 * Cancellation:
 * - Pressing Button B / Back cancels confirmation and consumes the key.
 * - Navigating away / losing focus automatically resets the card to Step 1.
 */
@Composable
fun GamepadTwoStepConfirmCard(
    title: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    confirmDescription: String? = description,
    actionText: String? = null,
    confirmActionText: String = stringResource(R.string.gamepad_action_confirm),
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    itemKey: Any? = title,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val colors = LocalAppColors.current
    var isConfirming by remember { mutableStateOf(false) }

    GamepadFocusCard(
        onClick = {
            if (enabled) {
                if (!isConfirming) {
                    isConfirming = true
                } else {
                    onConfirm()
                }
            }
        },
        enabled = enabled,
        modifier = modifier,
        itemKey = itemKey,
        cardFocusRequester = cardFocusRequester,
        onFocusChanged = { isFocused ->
            if (!isFocused && isConfirming) {
                isConfirming = false
            }
        },
        onCustomKeyEvent = { keyEvent ->
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (isConfirming && (
                    keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                        keyCode == KeyEvent.KEYCODE_BACK ||
                        keyCode == KeyEvent.KEYCODE_ESCAPE
                )
            ) {
                if (keyEvent.type == KeyEventType.KeyDown) {
                    isConfirming = false
                }
                true
            } else {
                false
            }
        },
    ) { isFocused ->
        GamepadCardRow(
            title = if (isConfirming) confirmTitle else title,
            description = if (isConfirming) confirmDescription else description,
            icon = icon,
            leadingContent = leadingContent,
            isFocused = isFocused,
            isDestructive = isDestructive,
            trailingContent = {
                Row(
                    modifier =
                        Modifier
                            .background(
                                color =
                                    when {
                                        isConfirming -> colors.error
                                        isDestructive -> colors.error.copy(alpha = GC_DESTRUCTIVE_BG_ALPHA)
                                        !enabled -> colors.surfaceVariant.copy(alpha = GC_DISABLED_CARD_ALPHA)
                                        isFocused -> colors.accent
                                        else -> colors.surfaceVariant
                                    },
                                shape = RoundedCornerShape(GC_STATUS_PILL_CORNER),
                            ).border(
                                width = GC_DEFAULT_BORDER_WIDTH,
                                color =
                                    when {
                                        isConfirming -> colors.error
                                        isDestructive -> colors.error.copy(alpha = GC_DESTRUCTIVE_BORDER_ALPHA)
                                        !enabled -> colors.subduedBorder.copy(alpha = GC_DISABLED_CARD_ALPHA)
                                        isFocused -> colors.accent
                                        else -> colors.subduedBorder
                                    },
                                shape = RoundedCornerShape(GC_STATUS_PILL_CORNER),
                            ).padding(horizontal = GC_STATUS_PILL_H_PADDING, vertical = GC_STATUS_PILL_V_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GC_SPACING_6),
                ) {
                    Text(
                        text = if (isConfirming) confirmActionText else (actionText ?: stringResource(R.string.gamepad_action_delete)),
                        color =
                            when {
                                isConfirming -> colors.surface
                                isDestructive -> colors.error
                                !enabled -> colors.onSurfaceSecondary
                                isFocused -> colors.onAccent
                                else -> colors.onSurfaceSecondary
                            },
                        fontSize = GC_TEXT_SIZE_PILL,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}

/**
 * Gamepad-first reorderable card supporting 2-tier D-pad navigation, touch drag-and-drop,
 * and single-tap Up/Down arrow buttons.
 *
 * In Tier 1 (Row Navigation):
 * - D-Pad Up / Down: moves focus between adjacent cards in the list.
 * - D-Pad Left: passes through to navigate back to the sidebar.
 * - Button A / Click: enters Tier 2 (Moving Mode) on this card.
 *
 * In Tier 2 (Moving Mode):
 * - Card illuminates with glowing accent border and active "Moving" status badge.
 * - D-Pad Up: moves this item UP by 1 position (if not already at top).
 * - D-Pad Down: moves this item DOWN by 1 position (if not already at bottom).
 * - Button A / Enter: confirms position and exits Moving Mode.
 * - Button B / Back: cancels/exits Moving Mode.
 */
@Composable
fun GamepadReorderCard(
    title: String,
    index: Int,
    totalCount: Int,
    isMoving: Boolean,
    onToggleMoving: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    itemKey: Any? = title,
    enabled: Boolean = true,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    BackHandler(enabled = isMoving) {
        AppLog.d(TAG, "GamepadReorderCard: '$title' back pressed while moving, exiting moving mode")
        onToggleMoving()
    }

    GamepadFocusCard(
        onClick = {
            if (enabled) {
                onToggleMoving()
            }
        },
        cardFocusRequester = cardFocusRequester,
        itemKey = itemKey,
        enabled = enabled,
        modifier = modifier,
        isAdjusting = isMoving,
        onCustomKeyEvent = { keyEvent ->
            if (!isMoving) return@GamepadFocusCard false
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (index > 0) {
                            AppLog.d(TAG, "GamepadReorderCard: '$title' move up via D-pad")
                            onMoveUp()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (index < totalCount - 1) {
                            AppLog.d(TAG, "GamepadReorderCard: '$title' move down via D-pad")
                            onMoveDown()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    -> {
                        AppLog.d(TAG, "GamepadReorderCard: '$title' exiting moving mode on keyCode=$keyCode")
                        onToggleMoving()
                        true
                    }

                    else -> {
                        false
                    }
                }
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    -> true

                    else -> false
                }
            } else {
                false
            }
        },
        onFocusChanged = { isFocused ->
            if (!isFocused && isMoving) {
                onToggleMoving()
            }
            onFocusChanged?.invoke(isFocused)
        },
    ) { isFocused ->
        GamepadCardRow(
            title = title,
            description = description,
            icon = icon,
            leadingContent =
                leadingContent ?: {
                    GamepadPositionBadge(
                        index = index,
                        isFocused = isFocused || isMoving || isDragging,
                    )
                },
            isFocused = isFocused || isMoving || isDragging,
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GC_SPACING_8),
                ) {
                    // Drag Handle on the left of badge
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.cd_drag_reorder),
                        tint = if (isFocused || isMoving || isDragging) colors.accent else colors.onSurfaceSecondary,
                        modifier =
                            Modifier
                                .padding(horizontal = GC_SPACING_4)
                                .then(dragHandleModifier),
                    )

                    // Moving / Move badge on the very right
                    GamepadPill(
                        text =
                            if (isMoving) {
                                stringResource(R.string.gamepad_action_moving)
                            } else {
                                stringResource(R.string.gamepad_action_move)
                            },
                        isAccent = isMoving,
                        isHighlighted = isFocused && !isMoving,
                    )
                }
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
    trailingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    itemKey: Any? = title,
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
        itemKey = itemKey,
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
            if (icon != null || description != null || trailingContent != null) {
                GamepadCardRow(
                    title = title,
                    description = description,
                    icon = icon,
                    isFocused = isFocused,
                    trailingContent = trailingContent,
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
                .padding(horizontal = GC_SPACING_8, vertical = GC_SPACING_6),
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
            verticalArrangement = Arrangement.spacedBy(GC_SPACING_8),
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
                                .height(GC_SLIDER_TRACK_HEIGHT)
                                .clip(RoundedCornerShape(GC_CORNER_4))
                                .background(trackBrush)
                                .border(
                                    GC_DEFAULT_BORDER_WIDTH,
                                    Color.White.copy(alpha = GC_SLIDER_TRACK_BORDER_ALPHA),
                                    RoundedCornerShape(GC_CORNER_4),
                                ),
                    )

                    Slider(
                        value = value,
                        onValueChange = onValueChange,
                        onValueChangeFinished = { isAdjusting = false },
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
                    onValueChangeFinished = { isAdjusting = false },
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

/**
 * A reusable 2-column grid layout for [GamepadActionCard] items, ensuring balanced row heights,
 * consistent spacing, and proper focus styling on the first item in the deck.
 */
@Composable
fun <T> GamepadTwoColumnGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    spacing: Dp = GC_SPACING_10,
    cardContent: @Composable (item: T, isFirstItem: Boolean, modifier: Modifier) -> Unit,
) {
    val chunked = remember(items) { items.chunked(2) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        chunked.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val isFirstItem = rowIndex == 0 && colIndex == 0
                    cardContent(
                        item,
                        isFirstItem,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (isFirstItem) Modifier.firstDeckItem() else Modifier),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Gamepad-first themed info banner / notice box matching the design across editor sub-menus.
 */
@Composable
fun GamepadInfoBox(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    iconTint: Color? = null,
) {
    val colors = LocalAppColors.current
    val effectiveTint = iconTint ?: colors.accent
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = colors.surface.copy(alpha = GC_INFO_BOX_BG_ALPHA),
                    shape = RoundedCornerShape(GC_INFO_BOX_RADIUS),
                ).border(
                    width = GC_INFO_BOX_BORDER_WIDTH,
                    color = colors.onSurfaceSecondary.copy(alpha = GC_INFO_BOX_BORDER_ALPHA),
                    shape = RoundedCornerShape(GC_INFO_BOX_RADIUS),
                ).padding(horizontal = GC_INFO_BOX_PADDING_H, vertical = GC_INFO_BOX_PADDING_V),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GC_INFO_BOX_SPACING),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveTint,
                modifier = Modifier.size(GC_INFO_BOX_ICON_SIZE),
            )
            if (description != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(GC_INFO_BOX_TEXT_SPACING),
                ) {
                    Text(
                        text = text,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = description,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    text = text,
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
