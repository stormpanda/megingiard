package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

private const val TAG = "PadButtonFace"

private const val PBF_DISABLED_ALPHA = 0.38f
private val PBF_BACKING_COLOR = Color(0x80121212)
private val PBF_BORDER_WIDTH = 1.dp

@Composable
internal fun PadButtonFace(
    width: Dp,
    height: Dp,
    shape: Shape,
    isIconOnly: Boolean,
    isDeviceDisabled: Boolean,
    borderColor: Color,
    bgColor: Color,
    bgAlpha: Float,
    gradientScale: Float = 2.8f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current

    val bgBrush =
        remember(bgColor, bgAlpha, gradientScale, width, height, density) {
            val wPx = with(density) { width.toPx() }
            val hPx = with(density) { height.toPx() }
            val halfDiag = sqrt(wPx * wPx + hPx * hPx) / 2f
            val maxAlpha = (bgAlpha * gradientScale).coerceIn(0f, 1f)
            Brush.radialGradient(
                0.00f to bgColor.copy(alpha = 0f),
                0.50f to bgColor.copy(alpha = maxAlpha * 0.25f),
                0.75f to bgColor.copy(alpha = maxAlpha * 0.5625f),
                1.00f to bgColor.copy(alpha = maxAlpha),
                center = Offset(wPx / 2f, hPx / 2f),
                radius = halfDiag,
            )
        }

    val disabledPaint =
        remember {
            Paint().apply {
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                this.alpha = PBF_DISABLED_ALPHA
            }
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(width, height)
                .clip(shape)
                .drawWithContent {
                    if (isIconOnly) {
                        drawContent()
                    } else {
                        if (isDeviceDisabled) {
                            drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), disabledPaint)
                            drawRect(color = PBF_BACKING_COLOR)
                            drawRect(brush = bgBrush)
                            drawContent()
                            drawContext.canvas.restore()
                        } else {
                            drawRect(color = PBF_BACKING_COLOR)
                            drawRect(brush = bgBrush)
                            drawContent()
                        }
                    }
                }.then(
                    if (isIconOnly) {
                        Modifier
                    } else {
                        Modifier.border(PBF_BORDER_WIDTH, borderColor, shape)
                    },
                ),
    ) {
        content()
    }
}
