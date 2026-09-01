package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "GamepadColorPickers"

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
                .background(containerBg, GC_STATUS_PILL_SHAPE)
                .border(
                    containerBorderWidth,
                    containerBorderColor,
                    GC_STATUS_PILL_SHAPE,
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

/** Formats a Compose [Color] into a hex string with optional alpha percentage. */
fun Color.toHexLabel(includeAlpha: Boolean = true): String =
    if (includeAlpha && alpha < 0.99f) {
        String.format(Locale.US, "#%06X (%d%%)", 0xFFFFFF and toArgb(), (alpha * 100).roundToInt())
    } else {
        String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())
    }

fun dimColorFilter(dim: Float): ColorFilter? {
    if (dim <= 0f) return null
    val brightness = (1f - dim).coerceIn(0f, 1f)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                brightness,
                0f,
                0f,
                0f,
                0f,
                0f,
                brightness,
                0f,
                0f,
                0f,
                0f,
                0f,
                brightness,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        ),
    )
}
