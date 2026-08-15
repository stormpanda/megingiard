package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.delay
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
private val GC_FOOTER_HEIGHT = 44.dp
private val GC_FOOTER_H_PADDING = 16.dp
private val GC_GLYPH_SIZE = 20.dp
private val GC_STEPPER_BTN_SIZE = 32.dp
private val GC_SWATCH_SIZE = 36.dp
private val GC_SIDEBAR_WIDTH = 210.dp
private val GC_SIDEBAR_ITEM_HEIGHT = 40.dp
private val GC_SIDEBAR_CORNER = 10.dp
private val GC_SIDEBAR_ICON_SIZE = 20.dp
private const val GC_ANIM_DURATION_MS = 150
private const val GC_INITIAL_FOCUS_DELAY_MS = 50L
private const val GC_UNFOCUSED_MAX_LINES = 2

/**
 * Standard gamepad button glyph descriptors.
 */
enum class GamePadGlyph(
    val label: String,
    val isPill: Boolean = false,
) {
    BTN_A("A"),
    BTN_B("B"),
    BTN_X("X"),
    BTN_Y("Y"),
    BTN_L1("L1", isPill = true),
    BTN_R1("R1", isPill = true),
    BTN_L2("L2", isPill = true),
    BTN_R2("R2", isPill = true),
    DPAD("D-Pad", isPill = true),
    STICK("Stick", isPill = true),
    NAV("Stick / D-Pad", isPill = true),
    BUMPERS("L1 / R1", isPill = true),
}

/**
 * Renders a circular or pill console controller glyph badge.
 */
@Composable
fun GamePadGlyphBadge(
    glyph: GamePadGlyph,
    modifier: Modifier = Modifier,
    size: Dp = GC_GLYPH_SIZE,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    backgroundColor: Color = LocalAppColors.current.surfaceVariant,
) {
    val colors = LocalAppColors.current
    val shape: Shape = if (glyph.isPill) RoundedCornerShape(4.dp) else CircleShape
    val horizontalPadding = if (glyph.isPill) 6.dp else 0.dp

    Box(
        modifier =
            modifier
                .height(size)
                .defaultMinSize(minWidth = size)
                .background(backgroundColor, shape)
                .border(1.dp, colors.subduedBorder, shape)
                .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Controller action prompt item displaying `[Glyph] Action Label`.
 */
@Composable
fun GamePadActionPrompt(
    glyph: GamePadGlyph,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Row(
        modifier = modifier.then(clickModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GamePadGlyphBadge(glyph = glyph)
        Text(
            text = text,
            color = colors.onSurfaceSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Unified bottom footer bar displaying contextual gamepad button action prompts.
 */
@Composable
fun PrimaryOverlayFooter(
    actions: List<Pair<GamePadGlyph, String>>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GC_FOOTER_HEIGHT)
                .background(colors.surfaceVariant.copy(alpha = 0.85f))
                .border(GC_DEFAULT_BORDER_WIDTH, colors.subduedBorder)
                .padding(horizontal = GC_FOOTER_H_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        actions.forEach { (glyph, label) ->
            GamePadActionPrompt(glyph = glyph, text = label)
        }
    }
}

/**
 * Base focusable gamepad card container with glowing accent bezel and spring focus transitions.
 */
@Composable
fun GamepadFocusCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(GC_CARD_CORNER),
    onCustomKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onLeftKey: (() -> Unit)? = null,
    onRightKey: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    content: @Composable (isFocused: Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val cardFocusRequester = remember { FocusRequester() }
    val recordLastFocused = LocalLastFocusedDeckTracker.current

    LaunchedEffect(isFocused) {
        if (isFocused) {
            recordLastFocused?.invoke(cardFocusRequester)
        }
        onFocusChanged?.invoke(isFocused)
    }

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isFocused) GC_FOCUS_BORDER_WIDTH else GC_DEFAULT_BORDER_WIDTH,
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardBorderWidth",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else colors.subduedBorder,
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardBorderColor",
    )
    val animatedBgColor by animateColorAsState(
        targetValue =
            if (isFocused) {
                colors.surface.copy(alpha = 0.95f)
            } else {
                colors.surface.copy(alpha = 0.55f)
            },
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardBgColor",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isFocused) GC_FOCUS_ELEVATION else GC_DEFAULT_ELEVATION,
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardElevation",
    )

    val keyModifier =
        Modifier.onKeyEvent { keyEvent ->
            if (onCustomKeyEvent != null && onCustomKeyEvent(keyEvent)) {
                return@onKeyEvent true
            }
            if (keyEvent.type == KeyEventType.KeyUp) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (enabled && onClick != null) {
                            onClick()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (enabled && onLeftKey != null) {
                            onLeftKey()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (enabled && onRightKey != null) {
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
            } else {
                false
            }
        }

    val focusableOrClickModifier =
        if (onClick != null) {
            Modifier.clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    try {
                        cardFocusRequester.requestFocus()
                    } catch (_: Exception) {
                        // Focus sync fallback
                    }
                    onClick()
                },
            )
        } else {
            Modifier.focusable(enabled = enabled, interactionSource = interactionSource)
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = GC_CARD_MIN_HEIGHT)
                .shadow(animatedElevation, shape)
                .background(animatedBgColor, shape)
                .border(animatedBorderWidth, animatedBorderColor, shape)
                .focusRequester(cardFocusRequester)
                .then(keyModifier)
                .then(focusableOrClickModifier),
        shape = shape,
        color = Color.Transparent,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GC_CARD_H_PADDING, vertical = GC_CARD_V_PADDING),
            contentAlignment = Alignment.CenterStart,
        ) {
            content(isFocused)
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
            isDestructive -> colors.error.copy(alpha = 0.2f)
            isFocused -> colors.accent.copy(alpha = 0.2f)
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
            isDestructive -> colors.error.copy(alpha = 0.15f)
            isAccent -> colors.accent
            isHighlighted -> colors.accent.copy(alpha = 0.2f)
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
            isDestructive -> colors.error.copy(alpha = 0.6f)
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
            fontSize = 12.sp,
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(GC_STEPPER_BTN_SIZE)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
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
    val capsuleBg = if (isAdjusting) colors.accent.copy(alpha = 0.2f) else colors.surfaceVariant
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
            tint = arrowTint,
            onClick = onPrevious,
            enabled = enabled,
        )

        Text(
            text = valueText,
            color = if (isAdjusting) colors.accent else colors.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        CapsuleArrowButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
            Spacer(modifier = Modifier.width(12.dp))
        }

        GamepadCardText(
            title = title,
            description = description,
            isFocused = isFocused,
            isDestructive = isDestructive,
            modifier = Modifier.weight(1f),
        )

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
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
                    text = if (checked) "ON ●" else "OFF ○",
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
                if (actionText != null || actionGlyph != null) {
                    {
                        Row(
                            modifier =
                                Modifier
                                    .background(
                                        if (isDestructive) colors.error.copy(alpha = 0.15f) else colors.surfaceVariant,
                                        RoundedCornerShape(GC_STATUS_PILL_CORNER),
                                    ).border(
                                        1.dp,
                                        if (isDestructive) {
                                            colors.error.copy(alpha = 0.6f)
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
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

/**
 * 2D Gamepad Color Palette Grid.
 */
@Composable
fun GamepadColorPaletteGrid(
    paletteColors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        paletteColors.forEach { color ->
            val isSelected = color == selectedColor
            Box(
                modifier =
                    Modifier
                        .size(GC_SWATCH_SIZE)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (isSelected) 3.dp else 1.5.dp,
                            if (isSelected) colors.onSurface else Color.White.copy(alpha = 0.3f),
                            CircleShape,
                        ).primaryOverlayFocusable(
                            onClick = { onColorSelected(color) },
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

val LocalActiveCategoryRequester = compositionLocalOf<FocusRequester?> { null }
val LocalFirstContentRequester = compositionLocalOf<FocusRequester?> { null }
val LocalLastFocusedDeckTracker = compositionLocalOf<((FocusRequester) -> Unit)?> { null }
val LocalResetLastFocusedTracker = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Modifier extension to mark a composable card as the primary focus target when entering the right deck from the sidebar.
 */
@Composable
fun Modifier.firstDeckItem(isFirst: Boolean = true): Modifier {
    val requester = LocalFirstContentRequester.current
    return if (requester != null && isFirst) this.focusRequester(requester) else this
}

/**
 * Unified gamepad-first category sidebar item tile used across split-screen dialogs and editors.
 */
@Composable
fun GamepadCategoryTile(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val activeCategoryRequester = LocalActiveCategoryRequester.current
    val resetLastFocused = LocalResetLastFocusedTracker.current

    val animatedBg by animateColorAsState(
        targetValue =
            when {
                selected && isFocused -> colors.accent.copy(alpha = 0.35f)
                selected -> colors.accent.copy(alpha = 0.2f)
                isFocused -> colors.surface.copy(alpha = 0.95f)
                else -> Color.Transparent
            },
        label = "catBg",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else Color.Transparent,
        label = "catBorder",
    )

    val requesterModifier =
        if (activeCategoryRequester != null && selected) {
            Modifier.focusRequester(activeCategoryRequester)
        } else {
            Modifier
        }

    val wrappedOnClick: () -> Unit = {
        try {
            activeCategoryRequester?.requestFocus()
        } catch (_: Exception) {
            // Focus sync fallback
        }
        onClick()
    }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GC_SIDEBAR_ITEM_HEIGHT)
                .background(animatedBg, RoundedCornerShape(GC_SIDEBAR_CORNER))
                .border(if (isFocused) 1.5.dp else 0.dp, animatedBorderColor, RoundedCornerShape(GC_SIDEBAR_CORNER))
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        if (!selected) {
                            resetLastFocused?.invoke()
                        }
                        onSelect?.invoke() ?: onClick()
                    }
                }.then(requesterModifier)
                .primaryOverlayFocusable(
                    onClick = wrappedOnClick,
                    shape = RoundedCornerShape(GC_SIDEBAR_CORNER),
                    borderWidth = 0.dp,
                    interactionSource = interactionSource,
                ),
        shape = RoundedCornerShape(GC_SIDEBAR_CORNER),
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected || isFocused) colors.accent else colors.onSurfaceSecondary,
                modifier = Modifier.size(GC_SIDEBAR_ICON_SIZE),
            )
            Text(
                text = title,
                color = if (selected || isFocused) colors.onSurface else colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Standardized split-screen two-pane scaffold for primary screen settings and editors.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadTwoPaneScaffold(
    sidebarContent: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    footerContent: (@Composable () -> Unit)? = null,
    sidebarFooter: (@Composable () -> Unit)? = null,
    sidebarWidth: Dp = GC_SIDEBAR_WIDTH,
) {
    val colors = LocalAppColors.current
    val inputModeManager = LocalInputModeManager.current
    val activeCategoryRequester = remember { FocusRequester() }
    val firstContentRequester = remember { FocusRequester() }
    var lastFocusedContentRequester by remember { mutableStateOf<FocusRequester?>(null) }

    val transferFocusToDeck: () -> Unit = {
        var handled = false
        val target = lastFocusedContentRequester
        if (target != null) {
            try {
                target.requestFocus()
                AppLog.d(TAG, "transferFocusToDeck: restored focus to last focused content deck item")
                handled = true
            } catch (_: IllegalStateException) {
                lastFocusedContentRequester = null
            }
        }
        if (!handled) {
            try {
                firstContentRequester.requestFocus()
                AppLog.d(TAG, "transferFocusToDeck: focused first content deck item")
            } catch (_: IllegalStateException) {
                try {
                    activeCategoryRequester.requestFocus()
                } catch (_: IllegalStateException) {
                    // Focus fallback
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalActiveCategoryRequester provides activeCategoryRequester,
        LocalFirstContentRequester provides firstContentRequester,
        LocalLastFocusedDeckTracker provides { req -> lastFocusedContentRequester = req },
        LocalResetLastFocusedTracker provides { lastFocusedContentRequester = null },
    ) {
        LaunchedEffect(Unit) {
            delay(GC_INITIAL_FOCUS_DELAY_MS)
            try {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                activeCategoryRequester.requestFocus()
                AppLog.d(TAG, "GamepadTwoPaneScaffold: initial focus requested on active category")
            } catch (_: Exception) {
                // Initial focus fallback
            }
        }

        LaunchedEffect(Unit) {
            PrimaryOverlayInputBridge.focusRecoveryEvents.collect { keyCode ->
                AppLog.d(TAG, "GamepadTwoPaneScaffold: focusRecoveryEvent keyCode=$keyCode")
                inputModeManager.requestInputMode(InputMode.Keyboard)
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        transferFocusToDeck()
                    }

                    else -> {
                        try {
                            activeCategoryRequester.requestFocus()
                        } catch (_: Exception) {
                            // Focus recovery fallback
                        }
                    }
                }
            }
        }

        Column(modifier = modifier.fillMaxSize().background(colors.appBackground)) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // Left Category Sidebar Rail
                Column(
                    modifier =
                        Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                                ) {
                                    transferFocusToDeck()
                                    true
                                } else {
                                    false
                                }
                            },
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        sidebarContent()
                    }
                    if (sidebarFooter != null) {
                        sidebarFooter()
                    }
                }

                // Right Content Deck
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                ) {
                                    try {
                                        activeCategoryRequester.requestFocus()
                                    } catch (_: Exception) {
                                        // Focus fallback
                                    }
                                    true
                                } else {
                                    false
                                }
                            }.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content()
                }
            }
            if (footerContent != null) {
                footerContent()
            }
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
    step: Float = 0.05f,
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

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                colors =
                    SliderDefaults.colors(
                        thumbColor = if (isAdjusting) colors.accent else colors.onSurface,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.subduedBorder,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp),
            )
        }
    }
}

/**
 * Gamepad-first search bar with clear button and optional category filter chips.
 */
@Composable
fun GamepadSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
) {
    GamepadSearchBar<String>(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        categories = emptyList(),
        selectedCategory = null,
        onCategorySelected = null,
        categoryLabel = { it },
    )
}

@Composable
fun <T> GamepadSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    categories: List<T> = emptyList(),
    selectedCategory: T? = null,
    onCategorySelected: ((T?) -> Unit)? = null,
    categoryLabel: (T) -> String = { it.toString() },
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(text = placeholder, color = colors.onSurfaceSecondary)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = colors.onSurfaceSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (categories.isNotEmpty() && onCategorySelected != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSelectableChip(
                    text = "All",
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                )
                categories.forEach { category ->
                    AppSelectableChip(
                        text = categoryLabel(category),
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                    )
                }
            }
        }
    }
}

/**
 * Standard gamepad-focused confirmation dialog with destructive styling support.
 */
@Composable
fun GamepadConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
) {
    val colors = LocalAppColors.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = if (isDestructive) colors.error else colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            GamepadActionCard(
                title = confirmText,
                onClick = onConfirm,
                actionGlyph = GamePadGlyph.BTN_A,
                isDestructive = isDestructive,
            )
        },
        dismissButton = {
            GamepadActionCard(
                title = cancelText,
                onClick = onDismiss,
                actionGlyph = GamePadGlyph.BTN_B,
            )
        },
    )
}

/**
 * Standard empty state display for lists and grids.
 */
@Composable
fun GamepadEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .background(colors.surfaceVariant, CircleShape)
                    .border(1.dp, colors.subduedBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (description != null) {
            Text(
                text = description,
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            GamepadActionCard(
                title = actionText,
                onClick = onAction,
                actionGlyph = GamePadGlyph.BTN_A,
            )
        }
    }
}
