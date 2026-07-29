package com.stormpanda.megingiard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * Reusable Composable rendering a solid filled circle (1 char) or pill shape (multi-char)
 * with a bold letter cutout (A, X, SELECT, START, etc.).
 */
@Composable
fun CutoutLetterCircleIcon(
    letter: String,
    modifier: Modifier = Modifier,
    size: Dp = ICON_DEFAULT_SIZE_DP.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
) {
    val isMultiChar = letter.length > 1
    val fontSizeFactor =
        if (letter.length > 3) {
            0.48f
        } else if (isMultiChar) {
            0.54f
        } else {
            0.65f
        }
    val horizontalPadding = if (isMultiChar) (size.value * 0.35f).dp else 0.dp

    Box(
        modifier =
            modifier
                .height(size)
                .widthIn(min = size)
                .clip(CircleShape)
                .background(tint)
                .padding(horizontal = horizontalPadding),
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
            maxLines = 1,
        )
    }
}

/**
 * Reusable Composable rendering a standalone filled Material Symbol icon (replacing the outer cutout circle).
 */
@Composable
fun CutoutSymbolCircleIcon(
    symbolName: String,
    modifier: Modifier = Modifier,
    size: Dp = ICON_DEFAULT_SIZE_DP.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
) {
    MaterialSymbol(
        name = symbolName,
        size = size,
        tint = tint,
        filled = true,
        modifier = modifier,
    )
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
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    iconSize: Dp = ICON_DEFAULT_SIZE_DP.dp,
    tint: Color = LocalAppColors.current.onSurfaceSecondary,
    cutoutColor: Color = LocalAppColors.current.appBackground,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = tint,
        interactionSource = interactionSource,
        modifier = modifier.focusProperties { canFocus = false },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(contentPadding),
        ) {
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
