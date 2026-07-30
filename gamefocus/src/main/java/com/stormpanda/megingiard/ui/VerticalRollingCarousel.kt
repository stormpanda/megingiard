package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.math.floorMod
import kotlin.math.abs

private const val CAROUSEL_ROLL_ANGLE_DEG = 35f

@Composable
fun <T> VerticalRollingCarousel(
    selectedIndex: Int,
    items: List<T>,
    onSelectedIndexChange: (Int) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3,
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

    val targetOffsetState = remember { mutableStateOf(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex) {
        val currentTarget = targetOffsetState.value
        var delta = (selectedIndex - currentTarget) % items.size
        if (delta > items.size / 2f) {
            delta -= items.size
        } else if (delta < -items.size / 2f) {
            delta += items.size
        }
        targetOffsetState.value = currentTarget + delta
    }

    val animatedOffset by animateFloatAsState(
        targetValue = targetOffsetState.value,
        animationSpec = tween(durationMillis = 200),
        label = "CarouselOffsetAnimation",
    )

    val itemHeight = 32.dp
    val totalHeight = itemHeight * visibleItemsCount

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

        Box(
            modifier =
                Modifier
                    .height(totalHeight)
                    .weight(1f),
            contentAlignment = Alignment.TopStart,
        ) {
            val itemHeightPx = with(density) { itemHeight.toPx() }
            val centerY = itemHeightPx * (visibleItemsCount / 2)

            items.forEachIndexed { i, item ->
                var diff = (i - animatedOffset) % items.size
                if (diff > items.size / 2f) {
                    diff -= items.size
                } else if (diff < -items.size / 2f) {
                    diff += items.size
                }

                if (abs(diff) < (visibleItemsCount / 2f) + 1.2f) {
                    val scaleVal = 1f - abs(diff) * 0.075f
                    val rotationXVal = diff * 22.5f
                    val alphaVal = (1f - abs(diff) * 0.35f).coerceIn(0f, 1f)
                    val pivotY = (0.5f - diff * 0.5f).coerceIn(0f, 1f)

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .graphicsLayer {
                                    translationY = centerY + (diff * itemHeightPx)
                                    scaleX = scaleVal
                                    scaleY = scaleVal
                                    rotationX = rotationXVal
                                    alpha = alphaVal
                                    transformOrigin = TransformOrigin(0f, pivotY)
                                    cameraDistance = 16 * density.density
                                }.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = enabled,
                                    onClick = {
                                        onSelectedIndexChange(i)
                                    },
                                ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = labelProvider(item),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (i == selectedIndex) appColors.onSurface else appColors.onSurfaceSecondary,
                                ),
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}
