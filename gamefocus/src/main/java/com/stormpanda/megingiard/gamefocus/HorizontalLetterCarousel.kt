package com.stormpanda.megingiard.gamefocus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.ui.LocalAppColors

private const val TAG = "HorizontalLetterCarousel"
private val HLC_ITEM_WIDTH = 48.dp
private val HLC_SPACING = 8.dp
private const val HLC_CAMERA_DISTANCE_MULTIPLIER = 16

@Composable
fun HorizontalLetterCarousel(
    letters: List<Char>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val density = LocalDensity.current

    if (letters.isEmpty()) return

    val safeIndex = selectedIndex.coerceIn(0, letters.size - 1)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HLC_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val offsets = listOf(-2, -1, 0, 1, 2)
        for (offset in offsets) {
            val letterIndex = Math.floorMod(safeIndex + offset, letters.size)
            val letterChar = letters[letterIndex]

            val isSelected = (offset == 0)
            val isInner = (offset == -1 || offset == 1)

            val targetAlpha =
                when {
                    isSelected -> 1.0f
                    isInner -> 0.55f
                    else -> 0.25f
                }

            val targetScale =
                when {
                    isSelected -> 1.25f
                    isInner -> 0.95f
                    else -> 0.75f
                }

            val targetRotationY =
                when (offset) {
                    -2 -> -35f
                    -1 -> -20f
                    0 -> 0f
                    1 -> 20f
                    2 -> 35f
                    else -> 0f
                }

            val textColor = if (isSelected) appColors.accent else appColors.onSurfaceSecondary

            Text(
                text = letterChar.toString(),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = textColor.copy(alpha = targetAlpha),
                    ),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .width(HLC_ITEM_WIDTH)
                        .graphicsLayer {
                            scaleX = targetScale
                            scaleY = targetScale
                            rotationY = targetRotationY
                            cameraDistance = HLC_CAMERA_DISTANCE_MULTIPLIER * density.density
                        },
            )
        }
    }
}
