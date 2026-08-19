package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TAG = "EditorBaseComponents"

internal val EBC_PREVIEW_DEFAULT_SIZE = 36.dp
private const val EBC_PREVIEW_BG_ALPHA = 0.25f
private const val EBC_PREVIEW_GRADIENT_SCALE = 2.8f
private val EBC_PREVIEW_ICON_SIZE = 20.dp

internal val EBC_PALETTE_PRESETS =
    listOf(
        Color(0xFFFF5252), // Red
        Color(0xFFFF7043), // Deep Orange
        Color(0xFFFFA726), // Orange
        Color(0xFFFFCA28), // Amber
        Color(0xFF66BB6A), // Green
        Color(0xFF26A69A), // Teal
        Color(0xFF29B6F6), // Light Blue
        Color(0xFF42A5F5), // Blue
        Color(0xFF7E57C2), // Deep Purple
        Color(0xFFEC407A), // Pink
        Color(0xFFFFFFFF), // White
        Color(0xFF212121), // Dark Grey
    )

@Composable
internal fun SwordsButtonPreview(
    textColor: Color,
    borderColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = EBC_PREVIEW_DEFAULT_SIZE,
    isIconOnly: Boolean = false,
) {
    PadButtonFace(
        width = size,
        height = size,
        shape = CircleShape,
        isIconOnly = isIconOnly,
        isDeviceDisabled = false,
        borderColor = borderColor,
        bgColor = bgColor,
        bgAlpha = EBC_PREVIEW_BG_ALPHA,
        gradientScale = EBC_PREVIEW_GRADIENT_SCALE,
        modifier = modifier,
    ) {
        MaterialSymbol(
            name = "swords",
            size = EBC_PREVIEW_ICON_SIZE,
            tint = textColor,
            filled = true,
        )
    }
}
