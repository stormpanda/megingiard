package com.stormpanda.megingiard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.floorMod
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private const val TAG = "VerticalRollingCarousel"
private const val VRC_ROLL_ANGLE_DEG = 35f
private const val VRC_ROLL_ANGLE_COMPACT_DEG = 22.5f
private const val VRC_SCALE_DECAY = 0.05f
private const val VRC_SCALE_DECAY_COMPACT = 0.075f
private const val VRC_ALPHA_DECAY = 0.65f
private const val VRC_ALPHA_DECAY_COMPACT = 0.375f
private const val VRC_ANIM_DURATION_MS = 200
private val VRC_BUTTON_SLIDE_OFFSET = 10.dp
private const val VRC_CAMERA_DISTANCE_FACTOR = 16
private const val VRC_DEFAULT_VISIBLE_COUNT = 3
private const val VRC_HALF_DIVISOR = 2f
private const val VRC_CENTER_DIVISOR = 2
private const val VRC_PIVOT_CENTER = 0.5f
private const val VRC_FULL_ALPHA = 1f
private const val VRC_MAX_RING_ITEMS = 32
private const val VRC_RING_PADDING_SLOTS = 3
private const val VRC_NEIGHBOR_GUARD_SLOTS = 1
private const val VRC_WINDOW_SYMMETRY_MULTIPLIER = 2
private const val VRC_OFFSCREEN_PARK_MULTIPLIER = 4f
private const val VRC_WEIGHT_FILL = 1f
private val VRC_ITEM_HEIGHT = 26.dp
private val VRC_BUTTON_COL_PADDING_END = 6.dp
private val VRC_BUTTON_SPACER_HEIGHT = 2.dp
private val VRC_BUTTON_PADDING = 2.dp

internal class RollingOffsetTracker(
    initialIndex: Int,
) {
    private var lastIndex: Int = initialIndex
    private var lastItemCount: Int = 0
    var targetOffset: Float = initialIndex.toFloat()
        private set

    fun resolveTarget(
        selectedIndex: Int,
        itemCount: Int,
        logChanges: Boolean = false,
    ): Float {
        if (itemCount <= 0) return targetOffset
        if (lastItemCount == 0 || itemCount != lastItemCount) {
            val shouldLog = lastItemCount != 0 && selectedIndex != lastIndex
            lastItemCount = itemCount
            lastIndex = selectedIndex
            targetOffset = selectedIndex.toFloat()
            if (logChanges && shouldLog) {
                AppLog.d(TAG, "Selected index changed to $selectedIndex (itemCount=$itemCount)")
            }
            return targetOffset
        }
        if (selectedIndex == lastIndex) {
            return targetOffset
        }
        lastIndex = selectedIndex
        var delta = (selectedIndex - targetOffset) % itemCount
        val halfCount = itemCount / VRC_HALF_DIVISOR
        if (delta > halfCount) {
            delta -= itemCount
        } else if (delta < -halfCount) {
            delta += itemCount
        }
        targetOffset += delta
        if (logChanges) {
            AppLog.d(TAG, "Selected index changed to $selectedIndex")
        }
        return targetOffset
    }
}

internal fun computeRollingRingSize(
    itemCount: Int,
    visibleItemsCount: Int,
): Int {
    if (itemCount <= 0) return 0
    val halfVisible = visibleItemsCount / VRC_CENTER_DIVISOR
    val minWindowSlots = VRC_WINDOW_SYMMETRY_MULTIPLIER * halfVisible + VRC_RING_PADDING_SLOTS
    val copiesPerItem = ((minWindowSlots + itemCount - 1) / itemCount).coerceAtLeast(1)
    return itemCount * copiesPerItem
}

internal fun computeRollingRingDiff(
    ringSlot: Int,
    animatedOffset: Float,
    ringSize: Int,
): Float {
    val ringSizeFloat = ringSize.toFloat()
    var diff = (ringSlot.toFloat() - animatedOffset).mod(ringSizeFloat)
    val halfRing = ringSizeFloat / VRC_HALF_DIVISOR
    if (diff > halfRing) {
        diff -= ringSizeFloat
    }
    return diff
}

@Composable
fun <T> VerticalRollingCarousel(
    selectedIndex: Int,
    items: List<T>,
    onSelectedIndexChange: (Int) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = VRC_DEFAULT_VISIBLE_COUNT,
    enabled: Boolean = true,
    showButtonIcons: Boolean = true,
    showNeighboringItems: Boolean = true,
) {
    if (items.isEmpty()) return

    val appColors = LocalAppColors.current
    val density = LocalDensity.current
    val currentSelectedIndexState = rememberUpdatedState(selectedIndex)
    val currentItemsState = rememberUpdatedState(items)
    val currentOnSelectedIndexChangeState = rememberUpdatedState(onSelectedIndexChange)

    val onStepUp: () -> Unit =
        remember {
            {
                val size = currentItemsState.value.size
                if (size > 0) {
                    val nextIndex = (currentSelectedIndexState.value - 1).floorMod(size)
                    currentOnSelectedIndexChangeState.value(nextIndex)
                }
            }
        }

    val onStepDown: () -> Unit =
        remember {
            {
                val size = currentItemsState.value.size
                if (size > 0) {
                    val nextIndex = (currentSelectedIndexState.value + 1).floorMod(size)
                    currentOnSelectedIndexChangeState.value(nextIndex)
                }
            }
        }

    val offsetTracker = remember { RollingOffsetTracker(selectedIndex) }
    val targetOffset = offsetTracker.resolveTarget(selectedIndex, items.size, logChanges = true)

    val animatedOffsetState =
        animateFloatAsState(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = VRC_ANIM_DURATION_MS),
            label = "CarouselOffsetAnimation",
        )

    val onTapSlot: (Int) -> Unit =
        remember(animatedOffsetState) {
            { slotOffset ->
                val size = currentItemsState.value.size
                if (size > 0) {
                    val centerIndex = animatedOffsetState.value.roundToInt()
                    val tappedIndex = (centerIndex + slotOffset).floorMod(size)
                    currentOnSelectedIndexChangeState.value(tappedIndex)
                }
            }
        }

    val totalHeight = VRC_ITEM_HEIGHT * visibleItemsCount
    val baseTitleStyle = MaterialTheme.typography.titleMedium
    val itemTextStyle =
        remember(baseTitleStyle, appColors.onSurfaceSecondary) {
            baseTitleStyle.copy(
                fontWeight = FontWeight.Bold,
                color = appColors.onSurfaceSecondary,
            )
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        RollingStepButtons(
            enabled = enabled,
            showButtonIcons = showButtonIcons,
            density = density,
            onStepUp = onStepUp,
            onStepDown = onStepDown,
        )

        RollingItemsViewport(
            items = items,
            animatedOffsetState = animatedOffsetState,
            itemTextStyle = itemTextStyle,
            visibleItemsCount = visibleItemsCount,
            enabled = enabled,
            showNeighboringItems = showNeighboringItems,
            density = density,
            onTapSlot = onTapSlot,
            labelProvider = labelProvider,
            modifier =
                Modifier
                    .height(totalHeight)
                    .weight(VRC_WEIGHT_FILL),
        )
    }
}

@Composable
private fun RollingStepButtons(
    enabled: Boolean,
    showButtonIcons: Boolean,
    density: Density,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
) {
    val buttonIconsAlphaState =
        animateFloatAsState(
            targetValue = if (showButtonIcons) VRC_FULL_ALPHA else 0f,
            animationSpec = tween(durationMillis = VRC_ANIM_DURATION_MS),
            label = "CarouselButtonIconsAlpha",
        )
    val slideOffsetPx = with(density) { VRC_BUTTON_SLIDE_OFFSET.toPx() }
    val buttonIconsTranslationXState =
        animateFloatAsState(
            targetValue = if (showButtonIcons) 0f else -slideOffsetPx,
            animationSpec = tween(durationMillis = VRC_ANIM_DURATION_MS),
            label = "CarouselButtonIconsTranslationX",
        )
    val buttonPadding = remember { PaddingValues(horizontal = VRC_BUTTON_PADDING, vertical = VRC_BUTTON_PADDING) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .padding(end = VRC_BUTTON_COL_PADDING_END)
                .graphicsLayer {
                    alpha = buttonIconsAlphaState.value
                    translationX = buttonIconsTranslationXState.value
                },
    ) {
        GamePadButtonAction(
            button = GamePadButton.DPAD_UP,
            text = "",
            enabled = enabled && showButtonIcons,
            onClick = onStepUp,
            contentPadding = buttonPadding,
        )
        Spacer(modifier = Modifier.height(VRC_BUTTON_SPACER_HEIGHT))
        GamePadButtonAction(
            button = GamePadButton.DPAD_DOWN,
            text = "",
            enabled = enabled && showButtonIcons,
            onClick = onStepDown,
            contentPadding = buttonPadding,
        )
    }
}

@Composable
private fun <T> RollingItemsViewport(
    items: List<T>,
    animatedOffsetState: State<Float>,
    itemTextStyle: TextStyle,
    visibleItemsCount: Int,
    enabled: Boolean,
    showNeighboringItems: Boolean,
    density: Density,
    onTapSlot: (Int) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val neighborAlphaProgressState =
        animateFloatAsState(
            targetValue = if (showNeighboringItems) VRC_FULL_ALPHA else 0f,
            animationSpec = tween(durationMillis = VRC_ANIM_DURATION_MS),
            label = "CarouselNeighborAlphaProgress",
        )

    val itemHeightPx = with(density) { VRC_ITEM_HEIGHT.toPx() }
    val halfVisible = visibleItemsCount / VRC_CENTER_DIVISOR
    val centerY = itemHeightPx * halfVisible
    val scaleDecay = if (visibleItemsCount == VRC_DEFAULT_VISIBLE_COUNT) VRC_SCALE_DECAY else VRC_SCALE_DECAY_COMPACT
    val rotationMax = if (visibleItemsCount == VRC_DEFAULT_VISIBLE_COUNT) VRC_ROLL_ANGLE_DEG else VRC_ROLL_ANGLE_COMPACT_DEG
    val alphaDecay = if (visibleItemsCount == VRC_DEFAULT_VISIBLE_COUNT) VRC_ALPHA_DECAY else VRC_ALPHA_DECAY_COMPACT
    val maxVisibleDiff = (halfVisible + VRC_NEIGHBOR_GUARD_SLOTS).toFloat()

    Box(
        modifier =
            modifier.then(
                if (enabled) {
                    Modifier.pointerInput(showNeighboringItems, halfVisible, itemHeightPx) {
                        detectTapGestures { tapOffset ->
                            val slotOffset = floor(tapOffset.y / itemHeightPx).toInt() - halfVisible
                            if (slotOffset in -halfVisible..halfVisible && (showNeighboringItems || slotOffset == 0)) {
                                onTapSlot(slotOffset)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        if (items.size <= VRC_MAX_RING_ITEMS) {
            // Retain a static modular ring of Text nodes so rolling never mutates text strings,
            // never recomposes on integer crossings, and never rebuilds AndroidParagraph layouts.
            val ringSize = computeRollingRingSize(items.size, visibleItemsCount)
            for (ringSlot in 0 until ringSize) {
                key(ringSlot) {
                    val item = items[ringSlot % items.size]
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(VRC_ITEM_HEIGHT)
                                .graphicsLayer {
                                    val diff = computeRollingRingDiff(ringSlot, animatedOffsetState.value, ringSize)
                                    val absDiff = abs(diff)
                                    if (absDiff >= maxVisibleDiff) {
                                        alpha = 0f
                                        translationY = -VRC_OFFSCREEN_PARK_MULTIPLIER * itemHeightPx
                                    } else {
                                        val scaleVal = VRC_FULL_ALPHA - absDiff * scaleDecay
                                        val rotationXVal = diff * rotationMax
                                        val effectiveAlphaDecay =
                                            VRC_FULL_ALPHA - (VRC_FULL_ALPHA - alphaDecay) * neighborAlphaProgressState.value
                                        val alphaVal = (VRC_FULL_ALPHA - absDiff * effectiveAlphaDecay).coerceIn(0f, VRC_FULL_ALPHA)
                                        val pivotY = (VRC_PIVOT_CENTER - diff * VRC_PIVOT_CENTER).coerceIn(0f, VRC_FULL_ALPHA)

                                        translationY = centerY + (diff * itemHeightPx)
                                        scaleX = scaleVal
                                        scaleY = scaleVal
                                        rotationX = rotationXVal
                                        alpha = alphaVal
                                        transformOrigin = TransformOrigin(0f, pivotY)
                                        cameraDistance = VRC_CAMERA_DISTANCE_FACTOR * density.density
                                    }
                                },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = labelProvider(item),
                            style = itemTextStyle,
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        } else {
            val integerOffsetState =
                remember(animatedOffsetState) {
                    derivedStateOf { floor(animatedOffsetState.value).toInt() }
                }
            val integerOffset = integerOffsetState.value
            val fractionalOffsetState =
                remember(animatedOffsetState) {
                    derivedStateOf {
                        val offset = animatedOffsetState.value
                        offset - floor(offset)
                    }
                }

            for (s in -halfVisible - VRC_NEIGHBOR_GUARD_SLOTS..halfVisible + VRC_NEIGHBOR_GUARD_SLOTS) {
                val itemIndex = (integerOffset + s).floorMod(items.size)
                key(itemIndex) {
                    val item = items[itemIndex]
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(VRC_ITEM_HEIGHT)
                                .graphicsLayer {
                                    val diff = s.toFloat() - fractionalOffsetState.value
                                    val absDiff = abs(diff)
                                    val scaleVal = VRC_FULL_ALPHA - absDiff * scaleDecay
                                    val rotationXVal = diff * rotationMax
                                    val effectiveAlphaDecay =
                                        VRC_FULL_ALPHA - (VRC_FULL_ALPHA - alphaDecay) * neighborAlphaProgressState.value
                                    val alphaVal = (VRC_FULL_ALPHA - absDiff * effectiveAlphaDecay).coerceIn(0f, VRC_FULL_ALPHA)
                                    val pivotY = (VRC_PIVOT_CENTER - diff * VRC_PIVOT_CENTER).coerceIn(0f, VRC_FULL_ALPHA)

                                    translationY = centerY + (diff * itemHeightPx)
                                    scaleX = scaleVal
                                    scaleY = scaleVal
                                    rotationX = rotationXVal
                                    alpha = alphaVal
                                    transformOrigin = TransformOrigin(0f, pivotY)
                                    cameraDistance = VRC_CAMERA_DISTANCE_FACTOR * density.density
                                },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = labelProvider(item),
                            style = itemTextStyle,
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}
