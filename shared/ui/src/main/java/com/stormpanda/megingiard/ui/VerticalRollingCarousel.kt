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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.floorMod
import kotlin.math.abs

private const val TAG = "VerticalRollingCarousel"
private const val CAROUSEL_ROLL_ANGLE_DEG = 35f
private const val CAROUSEL_ROLL_ANGLE_COMPACT_DEG = 22.5f
private const val CAROUSEL_SCALE_DECAY = 0.05f
private const val CAROUSEL_SCALE_DECAY_COMPACT = 0.075f
private const val CAROUSEL_ALPHA_DECAY = 0.65f
private const val CAROUSEL_ALPHA_DECAY_COMPACT = 0.375f
private const val CAROUSEL_ANIM_DURATION_MS = 200
private const val BUTTON_SLIDE_OFFSET_DP = 10f
private const val CAROUSEL_CAMERA_DISTANCE_FACTOR = 16
private val CAROUSEL_ITEM_HEIGHT = 26.dp
private val CAROUSEL_BUTTON_COL_PADDING_END = 6.dp
private val CAROUSEL_BUTTON_SPACER_HEIGHT = 2.dp
private val CAROUSEL_BUTTON_PADDING = 2.dp

@Composable
fun <T> VerticalRollingCarousel(
    selectedIndex: Int,
    items: List<T>,
    onSelectedIndexChange: (Int) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3,
    enabled: Boolean = true,
    showButtonIcons: Boolean = true,
    showNeighboringItems: Boolean = true,
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
        animationSpec = tween(durationMillis = CAROUSEL_ANIM_DURATION_MS),
        label = "CarouselOffsetAnimation",
    )

    val buttonIconsAlpha by animateFloatAsState(
        targetValue = if (showButtonIcons) 1f else 0f,
        animationSpec = tween(durationMillis = CAROUSEL_ANIM_DURATION_MS),
        label = "CarouselButtonIconsAlpha",
    )
    val buttonIconsTranslationX by animateFloatAsState(
        targetValue = if (showButtonIcons) 0f else -with(density) { BUTTON_SLIDE_OFFSET_DP.dp.toPx() },
        animationSpec = tween(durationMillis = CAROUSEL_ANIM_DURATION_MS),
        label = "CarouselButtonIconsTranslationX",
    )

    val neighborAlphaProgress by animateFloatAsState(
        targetValue = if (showNeighboringItems) 1f else 0f,
        animationSpec = tween(durationMillis = CAROUSEL_ANIM_DURATION_MS),
        label = "CarouselNeighborAlphaProgress",
    )

    val totalHeight = CAROUSEL_ITEM_HEIGHT * visibleItemsCount

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // Up & Down Gamepad Action Buttons on the left
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .padding(end = CAROUSEL_BUTTON_COL_PADDING_END)
                    .graphicsLayer {
                        alpha = buttonIconsAlpha
                        translationX = buttonIconsTranslationX
                    },
        ) {
            GamePadButtonAction(
                button = GamePadButton.DPAD_UP,
                text = "",
                enabled = enabled && showButtonIcons,
                onClick = onStepUp,
                contentPadding = PaddingValues(horizontal = CAROUSEL_BUTTON_PADDING, vertical = CAROUSEL_BUTTON_PADDING),
            )
            Spacer(modifier = Modifier.height(CAROUSEL_BUTTON_SPACER_HEIGHT))
            GamePadButtonAction(
                button = GamePadButton.DPAD_DOWN,
                text = "",
                enabled = enabled && showButtonIcons,
                onClick = onStepDown,
                contentPadding = PaddingValues(horizontal = CAROUSEL_BUTTON_PADDING, vertical = CAROUSEL_BUTTON_PADDING),
            )
        }

        Box(
            modifier =
                Modifier
                    .height(totalHeight)
                    .weight(1f),
            contentAlignment = Alignment.TopStart,
        ) {
            val itemHeightPx = with(density) { CAROUSEL_ITEM_HEIGHT.toPx() }
            val centerY = itemHeightPx * (visibleItemsCount / 2)

            val scaleDecay = if (visibleItemsCount == 3) CAROUSEL_SCALE_DECAY else CAROUSEL_SCALE_DECAY_COMPACT
            val rotationMax = if (visibleItemsCount == 3) CAROUSEL_ROLL_ANGLE_DEG else CAROUSEL_ROLL_ANGLE_COMPACT_DEG
            val alphaDecay = if (visibleItemsCount == 3) CAROUSEL_ALPHA_DECAY else CAROUSEL_ALPHA_DECAY_COMPACT

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
                key(s) {
                    val itemIndex = (integerOffset + s).floorMod(items.size)
                    val item = items[itemIndex]

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(CAROUSEL_ITEM_HEIGHT)
                                .graphicsLayer {
                                    val currentFraction = fractionalOffsetState.value
                                    val diff = s.toFloat() - currentFraction
                                    val scaleVal = 1f - abs(diff) * scaleDecay
                                    val rotationXVal = diff * rotationMax
                                    val effectiveAlphaDecay = 1.0f - (1.0f - alphaDecay) * neighborAlphaProgress
                                    val alphaVal = (1f - abs(diff) * effectiveAlphaDecay).coerceIn(0f, 1f)
                                    val pivotY = (0.5f - diff * 0.5f).coerceIn(0f, 1f)

                                    translationY = centerY + (diff * itemHeightPx)
                                    scaleX = scaleVal
                                    scaleY = scaleVal
                                    rotationX = rotationXVal
                                    alpha = alphaVal
                                    transformOrigin = TransformOrigin(0f, pivotY)
                                    cameraDistance = CAROUSEL_CAMERA_DISTANCE_FACTOR * density.density
                                }.focusProperties { canFocus = false }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = enabled && (showNeighboringItems || s == 0),
                                    onClick = {
                                        onSelectedIndexChange(itemIndex)
                                    },
                                ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = labelProvider(item),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.onSurfaceSecondary,
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
