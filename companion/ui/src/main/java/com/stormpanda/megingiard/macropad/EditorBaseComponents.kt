package com.stormpanda.megingiard.macropad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.ui.LocalAppColors

internal val EBC_PREVIEW_DEFAULT_SIZE = 36.dp
private val EBC_PREVIEW_ICON_SIZE = 20.dp

private val EBC_INFO_BOX_RADIUS = 12.dp
private val EBC_INFO_BOX_SHAPE = RoundedCornerShape(EBC_INFO_BOX_RADIUS)
private val EBC_INFO_BOX_BORDER_WIDTH = 1.dp
private const val EBC_INFO_BOX_BG_ALPHA = 0.45f
private const val EBC_INFO_BOX_BORDER_ALPHA = 0.25f
private val EBC_INFO_BOX_PADDING_H = 16.dp
private val EBC_INFO_BOX_PADDING_V = 12.dp
private val EBC_ARROW_SIZE = 14.dp
private const val EBC_ARROW_ALPHA = 0.6f

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

/**
 * Gamepad-first themed info banner displaying saved style vs in-flight changes above save buttons.
 */
@Composable
internal fun ColorPreviewInfoBox(
    title: String,
    description: String,
    savedPreview: @Composable () -> Unit,
    currentPreview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = colors.surface.copy(alpha = EBC_INFO_BOX_BG_ALPHA),
                    shape = EBC_INFO_BOX_SHAPE,
                ).border(
                    width = EBC_INFO_BOX_BORDER_WIDTH,
                    color = colors.onSurfaceSecondary.copy(alpha = EBC_INFO_BOX_BORDER_ALPHA),
                    shape = EBC_INFO_BOX_SHAPE,
                ).padding(horizontal = EBC_INFO_BOX_PADDING_H, vertical = EBC_INFO_BOX_PADDING_V),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
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
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                savedPreview()
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary.copy(alpha = EBC_ARROW_ALPHA),
                    modifier = Modifier.size(EBC_ARROW_SIZE),
                )
                currentPreview()
            }
        }
    }
}

private const val PULSE_ANIM_MS = 900

internal fun formatElapsedTime(elapsedMs: Long): String {
    val totalSec = elapsedMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val tenth = (elapsedMs % 1000) / 100
    return "%02d:%02d.%01d".format(min, sec, tenth)
}

@Composable
internal fun PulsingRecordingDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val pulseTransition = rememberInfiniteTransition(label = "recordingPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = PULSE_ANIM_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "recordingDotPulse",
    )

    Box(
        modifier =
            modifier
                .drawBehind {
                    drawCircle(color = color.copy(alpha = pulseAlpha))
                },
    )
}
