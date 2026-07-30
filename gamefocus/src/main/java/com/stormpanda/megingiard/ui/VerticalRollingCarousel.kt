package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.math.floorMod

private const val CAROUSEL_ROLL_ANGLE_DEG = 35f

@Composable
fun <T> VerticalRollingCarousel(
    selectedIndex: Int,
    items: List<T>,
    onSelectedIndexChange: (Int) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (items.isEmpty()) return

    val appColors = LocalAppColors.current
    val density = LocalDensity.current

    val onStepUp = {
        val nextIndex = (selectedIndex - 1).floorMod(items.size)
        onSelectedIndexChange(nextIndex)
    }

    val onStepDown = {
        val nextIndex = (selectedIndex + 1).floorMod(items.size)
        onSelectedIndexChange(nextIndex)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // Up & Down Gamepad Action Buttons on the left
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(end = 6.dp),
        ) {
            GamePadButtonAction(
                button = GamePadButton.DPAD_UP,
                text = "",
                enabled = enabled,
                onClick = onStepUp,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            GamePadButtonAction(
                button = GamePadButton.DPAD_DOWN,
                text = "",
                enabled = enabled,
                onClick = onStepDown,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            )
        }

        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                val isMovingDown = (initialState + 1).floorMod(items.size) == targetState
                if (isMovingDown) {
                    (slideInVertically { height -> height / 3 } + fadeIn())
                        .togetherWith(slideOutVertically { height -> -height / 3 } + fadeOut())
                } else {
                    (slideInVertically { height -> -height / 3 } + fadeIn())
                        .togetherWith(slideOutVertically { height -> height / 3 } + fadeOut())
                }
            },
            label = "VerticalRollingCarouselTransition",
        ) { currentIndex ->
            val prevIndex = (currentIndex - 1).floorMod(items.size)
            val nextIndex = (currentIndex + 1).floorMod(items.size)

            val currentItem = items[currentIndex]
            val prevItem = items[prevIndex]
            val nextItem = items[nextIndex]

            Column(
                horizontalAlignment = Alignment.Start,
            ) {
                // Previous item (curved backward top for 3D roll illusion)
                Text(
                    text = labelProvider(prevItem),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = appColors.onSurfaceSecondary.copy(alpha = 0.35f),
                        ),
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .graphicsLayer {
                                rotationX = -CAROUSEL_ROLL_ANGLE_DEG
                                cameraDistance = 16 * density.density
                            }.focusProperties { canFocus = false }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = enabled,
                                onClick = onStepUp,
                            ),
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Active item (highlighted, center-front)
                Text(
                    text = labelProvider(currentItem),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = appColors.onSurface,
                        ),
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Next item (curved backward bottom for 3D roll illusion)
                Text(
                    text = labelProvider(nextItem),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = appColors.onSurfaceSecondary.copy(alpha = 0.35f),
                        ),
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .graphicsLayer {
                                rotationX = CAROUSEL_ROLL_ANGLE_DEG
                                cameraDistance = 16 * density.density
                            }.focusProperties { canFocus = false }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = enabled,
                                onClick = onStepDown,
                            ),
                )
            }
        }
    }
}
