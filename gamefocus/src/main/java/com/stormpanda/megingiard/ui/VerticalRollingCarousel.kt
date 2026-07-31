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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.floorMod
import kotlin.math.abs

private const val TAG = "VerticalRollingCarousel"
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
        AppLog.d(TAG, "Selected index changed to $selectedIndex")
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

    val itemHeight = 26.dp
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

            val scaleDecay = if (visibleItemsCount == 3) 0.05f else 0.075f
            val rotationMax = if (visibleItemsCount == 3) 35f else 22.5f
            val alphaDecay = if (visibleItemsCount == 3) 0.65f else 0.375f

            val halfVisible = visibleItemsCount / 2
            val integerOffsetState =
                remember {
                    derivedStateOf { kotlin.math.floor(animatedOffset).toInt() }
                }
            val integerOffset = integerOffsetState.value

            val fractionalOffsetState =
                remember {
                    derivedStateOf { animatedOffset - kotlin.math.floor(animatedOffset) }
                }

            for (s in -halfVisible - 1..halfVisible + 1) {
                androidx.compose.runtime.key(s) {
                    val itemIndex = (integerOffset + s).floorMod(items.size)
                    val item = items[itemIndex]

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .graphicsLayer {
                                    val currentFraction = fractionalOffsetState.value
                                    val diff = s.toFloat() - currentFraction
                                    val scaleVal = 1f - abs(diff) * scaleDecay
                                    val rotationXVal = diff * rotationMax
                                    val alphaVal = (1f - abs(diff) * alphaDecay).coerceIn(0f, 1f)
                                    val pivotY = (0.5f - diff * 0.5f).coerceIn(0f, 1f)

                                    translationY = centerY + (diff * itemHeightPx)
                                    scaleX = scaleVal
                                    scaleY = scaleVal
                                    rotationX = rotationXVal
                                    alpha = alphaVal
                                    transformOrigin = TransformOrigin(0f, pivotY)
                                    cameraDistance = 16 * density.density
                                }.focusProperties { canFocus = false }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = enabled,
                                    onClick = {
                                        onSelectedIndexChange(itemIndex)
                                    },
                                ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val currentFraction = fractionalOffsetState.value
                        val diff = s.toFloat() - currentFraction
                        val colorFraction = (1f - abs(diff)).coerceIn(0f, 1f)
                        val textColor = lerp(appColors.onSurfaceSecondary, appColors.onSurface, colorFraction)

                        Text(
                            text = labelProvider(item),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
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
