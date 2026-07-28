package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val ICON_DEFAULT_SIZE_DP = 18

/**
 * Reusable Composable rendering a solid filled circle with a bold letter cutout (A, X, etc.)
 */
@Composable
fun CutoutLetterCircleIcon(
    letter: String,
    modifier: Modifier = Modifier,
    size: Dp = ICON_DEFAULT_SIZE_DP.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = (size.value * 0.65f).sp,
                    fontWeight = FontWeight.Black,
                    color = cutoutColor,
                    lineHeight = (size.value * 0.65f).sp,
                ),
            textAlign = TextAlign.Center,
        )
    }
}
