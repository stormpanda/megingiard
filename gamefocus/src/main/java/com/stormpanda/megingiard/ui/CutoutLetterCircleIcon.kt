package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val ICON_DEFAULT_SIZE_DP = 18

/**
 * Reusable Composable rendering a solid filled circle with a bold letter cutout (A, X, L1, R1, etc.)
 */
@Composable
fun CutoutLetterCircleIcon(
    letter: String,
    modifier: Modifier = Modifier,
    size: Dp = ICON_DEFAULT_SIZE_DP.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
) {
    val fontSizeFactor = if (letter.length > 1) 0.48f else 0.65f

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
                    fontSize = (size.value * fontSizeFactor).sp,
                    fontWeight = FontWeight.Black,
                    color = cutoutColor,
                    lineHeight = (size.value * fontSizeFactor).sp,
                ),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Reusable Composable rendering a solid filled circle with a Material Symbol cutout (menu, etc.)
 */
@Composable
fun CutoutSymbolCircleIcon(
    symbolName: String,
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
        MaterialSymbol(
            name = symbolName,
            size = (size.value * 0.7f).dp,
            tint = cutoutColor,
        )
    }
}

/**
 * Reusable subdued TextButton containing a CutoutLetterCircleIcon + label text.
 */
@Composable
fun CutoutLetterButton(
    letter: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = ICON_DEFAULT_SIZE_DP.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.focusProperties { canFocus = false },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CutoutLetterCircleIcon(
                letter = letter,
                size = iconSize,
                tint = tint,
                cutoutColor = cutoutColor,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
    }
}

/**
 * Reusable subdued TextButton containing a CutoutSymbolCircleIcon + label text.
 */
@Composable
fun CutoutSymbolButton(
    symbolName: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = ICON_DEFAULT_SIZE_DP.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.focusProperties { canFocus = false },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CutoutSymbolCircleIcon(
                symbolName = symbolName,
                size = iconSize,
                tint = tint,
                cutoutColor = cutoutColor,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
    }
}
