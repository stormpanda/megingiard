package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MAGICAL_BORDER_STROKE_WIDTH = 1.5.dp
private const val MAGICAL_FEATHER_GLOW_ALPHA = 0.08f
private const val MAGICAL_CORE_GLOW_ALPHA = 0.16f

@Composable
fun rememberMagicalBezelBrush(accentColor: Color = LocalAppColors.current.actionColorSystem): Brush {
    val transition = rememberInfiniteTransition(label = "MagicalBezel")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Angle",
    )

    return remember(angle, accentColor) {
        val rad = Math.toRadians(angle.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()

        val startX = 500f * (1f - cos)
        val startY = 500f * (1f - sin)
        val endX = 500f * (1f + cos)
        val endY = 500f * (1f + sin)

        Brush.linearGradient(
            colorStops =
                arrayOf(
                    0.0f to Color.White.copy(alpha = 0.85f),
                    0.2f to accentColor.copy(alpha = 0.9f),
                    0.45f to Color.White.copy(alpha = 0.25f),
                    0.7f to Color.Transparent,
                    0.85f to accentColor.copy(alpha = 0.5f),
                    1.0f to Color.White.copy(alpha = 0.85f),
                ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
        )
    }
}

/**
 * Reusable OutlinedButton with an animated magical shimmering bezel gradient and dual-layer glow.
 * Shared across Privileged Mode Auto-Setup and Auto-Fill (read screen) action buttons.
 */
@Composable
fun AppMagicalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = LocalAppColors.current.actionColorSystem,
    content: @Composable RowScope.() -> Unit,
) {
    val magicalBrush = rememberMagicalBezelBrush(accentColor)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(MAGICAL_BORDER_STROKE_WIDTH, magicalBrush),
        modifier =
            modifier.drawBehind {
                val dy = MAGICAL_BORDER_STROKE_WIDTH.toPx()
                val dx = 4.5.dp.toPx()
                val pillRadius = (size.height + 2 * dy) / 2f

                // Outer feathered glow layer
                drawRoundRect(
                    color = accentColor.copy(alpha = MAGICAL_FEATHER_GLOW_ALPHA),
                    cornerRadius = CornerRadius(pillRadius + 2.dp.toPx()),
                    size = Size(size.width + 2 * (dx + 2.dp.toPx()), size.height + 2 * dy),
                    topLeft = Offset(-(dx + 2.dp.toPx()), -dy),
                )
                // Inner core glow layer
                drawRoundRect(
                    color = accentColor.copy(alpha = MAGICAL_CORE_GLOW_ALPHA),
                    cornerRadius = CornerRadius(pillRadius),
                    size = Size(size.width + 2 * dx, size.height + 2 * dy),
                    topLeft = Offset(-dx, -dy),
                )
            },
        content = content,
    )
}
