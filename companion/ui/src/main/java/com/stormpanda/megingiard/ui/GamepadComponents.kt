package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog

private const val TAG = "GamepadComponents"

private val GC_CARD_CORNER = 12.dp
private val GC_CARD_MIN_HEIGHT = 56.dp
private val GC_CARD_H_PADDING = 16.dp
private val GC_CARD_V_PADDING = 12.dp
private val GC_CARD_SPACING = 8.dp
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
private val GC_ANIM_DURATION_MS = 150

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
    val shape: Shape = if (glyph.isPill) RoundedCornerShape(4.dp) else CircleShape
    val horizontalPadding = if (glyph.isPill) 6.dp else 0.dp

    Box(
        modifier =
            modifier
                .height(size)
                .defaultMinSize(minWidth = size)
                .background(backgroundColor, shape)
                .border(1.dp, tint.copy(alpha = 0.5f), shape)
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
                .border(GC_DEFAULT_BORDER_WIDTH, colors.controlOverlayBorder)
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
    onLeftKey: (() -> Unit)? = null,
    onRightKey: (() -> Unit)? = null,
    content: @Composable (isFocused: Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isFocused) GC_FOCUS_BORDER_WIDTH else GC_DEFAULT_BORDER_WIDTH,
        animationSpec = tween(GC_ANIM_DURATION_MS),
        label = "cardBorderWidth",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else colors.controlOverlayBorder.copy(alpha = 0.6f),
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

    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier.primaryOverlayFocusable(interactionSource = interactionSource, shape = shape)
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = GC_CARD_MIN_HEIGHT)
                .shadow(animatedElevation, shape)
                .background(animatedBgColor, shape)
                .border(animatedBorderWidth, animatedBorderColor, shape)
                .then(keyModifier)
                .then(clickModifier),
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
    val colors = LocalAppColors.current

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(GC_ICON_BOX_SIZE)
                            .background(
                                if (isFocused) colors.accent.copy(alpha = 0.2f) else colors.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) colors.accent else colors.onSurfaceSecondary,
                        modifier = Modifier.size(GC_ICON_SIZE),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Illuminated Status Pill
            val pillBg = if (checked) colors.accent else colors.surfaceVariant
            val pillTextColor = if (checked) colors.onAccent else colors.onSurfaceSecondary
            val pillText = if (checked) "ON ●" else "OFF ○"

            Box(
                modifier =
                    Modifier
                        .background(pillBg, RoundedCornerShape(GC_STATUS_PILL_CORNER))
                        .border(
                            1.dp,
                            if (checked) colors.accent else colors.controlOverlayBorder,
                            RoundedCornerShape(GC_STATUS_PILL_CORNER),
                        ).padding(horizontal = GC_STATUS_PILL_H_PADDING, vertical = GC_STATUS_PILL_V_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pillText,
                    color = pillTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
) {
    val colors = LocalAppColors.current

    GamepadFocusCard(
        onClick = onValueClick,
        enabled = enabled,
        onLeftKey = onDecrement,
        onRightKey = onIncrement,
        modifier = modifier,
    ) { isFocused ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(GC_ICON_BOX_SIZE)
                            .background(
                                if (isFocused) colors.accent.copy(alpha = 0.2f) else colors.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) colors.accent else colors.onSurfaceSecondary,
                        modifier = Modifier.size(GC_ICON_SIZE),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Stepper Pill ◀ Value ▶
            Row(
                modifier =
                    Modifier
                        .background(colors.surfaceVariant, RoundedCornerShape(GC_STATUS_PILL_CORNER))
                        .border(
                            1.dp,
                            if (isFocused) colors.accent else colors.controlOverlayBorder,
                            RoundedCornerShape(GC_STATUS_PILL_CORNER),
                        ).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(GC_STEPPER_BTN_SIZE),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (isFocused) colors.accent else colors.onSurfaceSecondary,
                    )
                }

                Text(
                    text = valueText,
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(GC_STEPPER_BTN_SIZE),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (isFocused) colors.accent else colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
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
) {
    val colors = LocalAppColors.current

    GamepadFocusCard(
        onClick = onClick,
        enabled = enabled,
        onLeftKey = onPrevious,
        onRightKey = onNext,
        modifier = modifier,
    ) { isFocused ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(GC_ICON_BOX_SIZE)
                            .background(
                                if (isFocused) colors.accent.copy(alpha = 0.2f) else colors.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) colors.accent else colors.onSurfaceSecondary,
                        modifier = Modifier.size(GC_ICON_SIZE),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Choice Capsule ◀ Option ▶
            Row(
                modifier =
                    Modifier
                        .background(colors.surfaceVariant, RoundedCornerShape(GC_STATUS_PILL_CORNER))
                        .border(
                            1.dp,
                            if (isFocused) colors.accent else colors.controlOverlayBorder,
                            RoundedCornerShape(GC_STATUS_PILL_CORNER),
                        ).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(GC_STEPPER_BTN_SIZE),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (isFocused) colors.accent else colors.onSurfaceSecondary,
                    )
                }

                Text(
                    text = selectedText,
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(GC_STEPPER_BTN_SIZE),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (isFocused) colors.accent else colors.onSurfaceSecondary,
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
    actionText: String? = null,
    actionGlyph: GamePadGlyph = GamePadGlyph.BTN_A,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    val colors = LocalAppColors.current

    GamepadFocusCard(
        onClick = if (enabled) onClick else null,
        enabled = enabled,
        modifier = modifier,
    ) { isFocused ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(GC_ICON_BOX_SIZE)
                            .background(
                                if (isDestructive) {
                                    colors.error.copy(alpha = 0.2f)
                                } else if (isFocused) {
                                    colors.accent.copy(alpha = 0.2f)
                                } else {
                                    colors.surfaceVariant
                                },
                                RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                            if (isDestructive) {
                                colors.error
                            } else if (isFocused) {
                                colors.accent
                            } else {
                                colors.onSurfaceSecondary
                            },
                        modifier = Modifier.size(GC_ICON_SIZE),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action Badge (A) Action
            Row(
                modifier =
                    Modifier
                        .background(
                            if (isDestructive) colors.error.copy(alpha = 0.15f) else colors.surfaceVariant,
                            RoundedCornerShape(GC_STATUS_PILL_CORNER),
                        ).border(
                            1.dp,
                            if (isDestructive) {
                                colors.error
                            } else if (isFocused) {
                                colors.accent
                            } else {
                                colors.controlOverlayBorder
                            },
                            RoundedCornerShape(GC_STATUS_PILL_CORNER),
                        ).padding(horizontal = GC_STATUS_PILL_H_PADDING, vertical = GC_STATUS_PILL_V_PADDING),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GamePadGlyphBadge(
                    glyph = actionGlyph,
                    tint = if (isDestructive) colors.error else colors.accent,
                )
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
