package com.stormpanda.megingiard.gamefocus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import kotlin.math.abs

private const val TAG = "HorizontalLetterCarousel"
private val HLC_ITEM_WIDTH = 26.dp
private val HLC_SPACING = 4.dp
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
    val itemShiftPx = with(density) { (HLC_ITEM_WIDTH + HLC_SPACING).roundToPx() }

    AnimatedContent(
        targetState = safeIndex,
        transitionSpec = {
            val isMovingForward =
                (targetState > initialState && !(initialState == 0 && targetState == letters.size - 1)) ||
                    (initialState == letters.size - 1 && targetState == 0)
            if (isMovingForward) {
                (
                    slideInHorizontally(animationSpec = tween(160)) { itemShiftPx } +
                        fadeIn(animationSpec = tween(160))
                ).togetherWith(
                    slideOutHorizontally(animationSpec = tween(140)) { -itemShiftPx } +
                        fadeOut(animationSpec = tween(140)),
                )
            } else {
                (
                    slideInHorizontally(animationSpec = tween(160)) { -itemShiftPx } +
                        fadeIn(animationSpec = tween(160))
                ).togetherWith(
                    slideOutHorizontally(animationSpec = tween(140)) { itemShiftPx } +
                        fadeOut(animationSpec = tween(140)),
                )
            }
        },
        label = "LetterCarouselHorizontalRollTransition",
        modifier = modifier,
    ) { activeIndex ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(HLC_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val offsets = listOf(-4, -3, -2, -1, 0, 1, 2, 3, 4)
            for (offset in offsets) {
                val letterIndex = Math.floorMod(activeIndex + offset, letters.size)
                val letterChar = letters[letterIndex]

                val isSelected = (offset == 0)
                val absOffset = abs(offset)

                val targetAlpha =
                    when (absOffset) {
                        0 -> 1.0f
                        1 -> 0.70f
                        2 -> 0.50f
                        3 -> 0.32f
                        else -> 0.18f
                    }

                val targetScale =
                    when (absOffset) {
                        0 -> 1.25f
                        1 -> 1.05f
                        2 -> 0.88f
                        3 -> 0.72f
                        else -> 0.58f
                    }

                val targetRotationY =
                    when (offset) {
                        -4 -> -50f
                        -3 -> -40f
                        -2 -> -28f
                        -1 -> -15f
                        0 -> 0f
                        1 -> 15f
                        2 -> 28f
                        3 -> 40f
                        4 -> 50f
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
}
