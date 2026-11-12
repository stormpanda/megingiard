package com.stormpanda.megingiard.gamefocus

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.floorMod
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign
import android.graphics.Paint as NativePaint
import androidx.compose.animation.Animatable as ColorAnimatable

private const val TAG = "HorizPosterCarousel"

private val HPC_DEFAULT_POSTER_WIDTH = 175.dp
private val HPC_DEFAULT_POSTER_HEIGHT = 262.dp
private val HPC_DEFAULT_POSTER_SPACING = 13.5.dp
private val HPC_DEFAULT_CAROUSEL_HEIGHT = 310.dp
private val HPC_DEFAULT_CORNER_RADIUS = 16.dp
private val HPC_EXTRA_PUSH_DP = 16.dp
private val HPC_VIBRANT_SHADOW_BLUR = 16.dp
private val HPC_VIBRANT_SHADOW_SPREAD = 2.dp
private const val HPC_VIBRANT_SHADOW_ALPHA = 0.45f
private const val HPC_SHADOW_FADE_DURATION_MS = 300
private const val HPC_HIDDEN_ALPHA = 0.4f
private const val HPC_VISIBLE_ALPHA = 1.0f
private const val HPC_HIDE_ANIMATION_DURATION_MS = 300
private const val HPC_CARD_BG_ANIMATION_DURATION_MS = 200
private val HPC_BORDER_SELECTED_WIDTH = 3.dp
private val HPC_BORDER_UNSELECTED_WIDTH = 1.dp
private const val HPC_STROKE_INSET_DIVISOR = 2f
private const val HPC_CENTER_PADDING_DIVISOR = 2
private val HPC_MIN_HORIZONTAL_PADDING = 0.dp
private const val HPC_SCALE_FOCUSED = 1.18f
private const val HPC_SCALE_UNFOCUSED = 0.85f
private const val HPC_SCALE_FALLOFF = 0.33f
private const val HPC_ALPHA_FOCUSED = 1.0f
private const val HPC_ALPHA_UNFOCUSED = 0.55f
private const val HPC_ALPHA_FALLOFF = 0.45f
private const val HPC_NEIGHBOR_UNIT_OFFSET = 1.0f
private const val HPC_MAX_PAINT_ALPHA = 255
private const val HPC_FULL_COLOR_ALPHA = 1.0f

@Composable
fun HorizontalPosterCarousel(
    itemCount: Int,
    pagerState: PagerState,
    onItemClick: (actualIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    key: ((page: Int) -> Any)? = null,
    containerWidth: Dp? = null,
    posterWidth: Dp = HPC_DEFAULT_POSTER_WIDTH,
    posterHeight: Dp = HPC_DEFAULT_POSTER_HEIGHT,
    posterSpacing: Dp = HPC_DEFAULT_POSTER_SPACING,
    carouselHeight: Dp = HPC_DEFAULT_CAROUSEL_HEIGHT,
    posterCornerRadius: Dp = HPC_DEFAULT_CORNER_RADIUS,
    cardBackgroundColor: (@Composable (actualIndex: Int, isSelected: Boolean) -> Color)? = null,
    isHidden: ((actualIndex: Int) -> Boolean)? = null,
    itemContent: @Composable (actualIndex: Int, isSelected: Boolean) -> Unit,
) {
    if (itemCount <= 0) return

    val appColors = LocalAppColors.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var measuredWidthPx by remember { mutableIntStateOf(0) }
    val effectiveContainerWidth =
        containerWidth
            ?: if (measuredWidthPx > 0) {
                with(density) { measuredWidthPx.toDp() }
            } else {
                configuration.screenWidthDp.dp
            }
    val horizontalPadding =
        ((effectiveContainerWidth - posterWidth) / HPC_CENTER_PADDING_DIVISOR).coerceAtLeast(HPC_MIN_HORIZONTAL_PADDING)

    val extraPushPx =
        remember(density) {
            with(density) { HPC_EXTRA_PUSH_DP.toPx() }
        }
    val posterShape = remember(posterCornerRadius) { RoundedCornerShape(posterCornerRadius) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(carouselHeight)
                .then(
                    if (containerWidth == null) {
                        Modifier.onSizeChanged { size ->
                            if (measuredWidthPx != size.width) {
                                measuredWidthPx = size.width
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        HorizontalPager(
            state = pagerState,
            key = key,
            pageSize = PageSize.Fixed(posterWidth),
            pageSpacing = posterSpacing,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val actualIndex = page.floorMod(itemCount)
            val isSelected by remember(pagerState, page) {
                derivedStateOf { pagerState.targetPage == page }
            }
            val isSettledAndSelected by remember(pagerState, page) {
                derivedStateOf {
                    !pagerState.isScrollInProgress &&
                        pagerState.targetPage == page &&
                        pagerState.settledPage == page
                }
            }
            val isCardHidden = isHidden?.invoke(actualIndex) ?: false

            CarouselPosterCard(
                page = page,
                actualIndex = actualIndex,
                isSelected = isSelected,
                isSettledAndSelected = isSettledAndSelected,
                isCardHidden = isCardHidden,
                posterWidth = posterWidth,
                posterHeight = posterHeight,
                posterCornerRadius = posterCornerRadius,
                posterShape = posterShape,
                extraPushPx = extraPushPx,
                pagerState = pagerState,
                appColors = appColors,
                cardBackgroundColor = cardBackgroundColor,
                onItemClick = onItemClick,
                itemContent = itemContent,
            )
        }
    }
}

@Composable
private fun CarouselPosterCard(
    page: Int,
    actualIndex: Int,
    isSelected: Boolean,
    isSettledAndSelected: Boolean,
    isCardHidden: Boolean,
    posterWidth: Dp,
    posterHeight: Dp,
    posterCornerRadius: Dp,
    posterShape: RoundedCornerShape,
    extraPushPx: Float,
    pagerState: PagerState,
    appColors: AppColors,
    cardBackgroundColor: (@Composable (actualIndex: Int, isSelected: Boolean) -> Color)?,
    onItemClick: (actualIndex: Int) -> Unit,
    itemContent: @Composable (actualIndex: Int, isSelected: Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentIsSelected by rememberUpdatedState(isSelected)

    val initiallySettledSelected = remember { isSettledAndSelected }
    var wasEverSettledSelected by remember { mutableStateOf(isSettledAndSelected) }
    if (isSettledAndSelected && !wasEverSettledSelected) {
        wasEverSettledSelected = true
    }

    val shadowAlphaAnim =
        if (wasEverSettledSelected) {
            val anim = remember { Animatable(if (initiallySettledSelected) HPC_FULL_COLOR_ALPHA else 0f) }
            LaunchedEffect(isSettledAndSelected) {
                val target = if (isSettledAndSelected) HPC_FULL_COLOR_ALPHA else 0f
                anim.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = HPC_SHADOW_FADE_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
            anim
        } else {
            null
        }

    val initiallyHidden = remember { isCardHidden }
    var wasEverHidden by remember { mutableStateOf(isCardHidden) }
    if (isCardHidden && !wasEverHidden) {
        wasEverHidden = true
    }

    val cardHiddenAlphaAnim =
        if (wasEverHidden) {
            val anim = remember { Animatable(if (initiallyHidden) HPC_HIDDEN_ALPHA else HPC_VISIBLE_ALPHA) }
            LaunchedEffect(isCardHidden) {
                val target = if (isCardHidden) HPC_HIDDEN_ALPHA else HPC_VISIBLE_ALPHA
                anim.animateTo(target, tween(durationMillis = HPC_HIDE_ANIMATION_DURATION_MS))
            }
            anim
        } else {
            null
        }

    val resolvedCardBg =
        cardBackgroundColor?.invoke(actualIndex, isSelected)
            ?: if (isSelected) appColors.surfaceVariant else appColors.surface

    val initialCardBg = remember { resolvedCardBg }
    var hasCardBgChanged by remember { mutableStateOf(false) }
    if (!hasCardBgChanged && resolvedCardBg != initialCardBg) {
        hasCardBgChanged = true
    }

    val cardBgAnim =
        if (hasCardBgChanged) {
            val anim = remember { ColorAnimatable(initialCardBg) }
            LaunchedEffect(resolvedCardBg) {
                anim.animateTo(resolvedCardBg, tween(durationMillis = HPC_CARD_BG_ANIMATION_DURATION_MS))
            }
            anim
        } else {
            null
        }

    val handleCardClick: () -> Unit =
        remember(page, actualIndex) {
            {
                if (currentIsSelected) {
                    AppLog.d(TAG, "Carousel card activated: index=$actualIndex")
                    currentOnItemClick(actualIndex)
                } else {
                    AppLog.d(TAG, "Carousel card tapped, scrolling to page=$page (index=$actualIndex)")
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                }
            }
        }

    Box(
        modifier =
            Modifier
                .size(posterWidth, posterHeight)
                .graphicsLayer {
                    val pageCount = pagerState.pageCount
                    val rawOffset =
                        if (pageCount > 0 && page in 0 until pageCount) {
                            pagerState.getOffsetDistanceInPages(page)
                        } else {
                            0f
                        }
                    val pageOffset = rawOffset.absoluteValue
                    val s = (HPC_SCALE_FOCUSED - (pageOffset * HPC_SCALE_FALLOFF)).coerceIn(HPC_SCALE_UNFOCUSED, HPC_SCALE_FOCUSED)
                    val a = (HPC_ALPHA_FOCUSED - (pageOffset * HPC_ALPHA_FALLOFF)).coerceIn(HPC_ALPHA_UNFOCUSED, HPC_ALPHA_FOCUSED)
                    val directionSign = sign(rawOffset)
                    val neighborFactor =
                        (HPC_NEIGHBOR_UNIT_OFFSET - (pageOffset - HPC_NEIGHBOR_UNIT_OFFSET).absoluteValue)
                            .coerceIn(0.0f, HPC_NEIGHBOR_UNIT_OFFSET)

                    scaleX = s
                    scaleY = s
                    alpha = a
                    translationX = directionSign * neighborFactor * extraPushPx
                }.drawWithCache {
                    val cornerRadiusPx = posterCornerRadius.toPx()
                    val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    val blurRadiusPx = HPC_VIBRANT_SHADOW_BLUR.toPx()
                    val spreadPx = HPC_VIBRANT_SHADOW_SPREAD.toPx()
                    val baseAccentAlpha = appColors.accent.alpha
                    val opaqueAccentArgb = appColors.accent.copy(alpha = HPC_FULL_COLOR_ALPHA).toArgb()
                    val glowPaint =
                        NativePaint().apply {
                            isAntiAlias = true
                            color = opaqueAccentArgb
                            style = NativePaint.Style.FILL
                            maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                        }

                    onDrawBehind {
                        val currentShadowAlpha = shadowAlphaAnim?.value ?: 0f
                        if (currentShadowAlpha > 0f) {
                            val paintAlpha =
                                (baseAccentAlpha * HPC_VIBRANT_SHADOW_ALPHA * currentShadowAlpha * HPC_MAX_PAINT_ALPHA)
                                    .roundToInt()
                                    .coerceIn(0, HPC_MAX_PAINT_ALPHA)
                            if (paintAlpha > 0) {
                                glowPaint.alpha = paintAlpha
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawRoundRect(
                                        -spreadPx,
                                        -spreadPx,
                                        size.width + spreadPx,
                                        size.height + spreadPx,
                                        cornerRadiusPx + spreadPx,
                                        cornerRadiusPx + spreadPx,
                                        glowPaint,
                                    )
                                }
                            }
                        }
                        val currentHiddenAlpha = cardHiddenAlphaAnim?.value ?: HPC_VISIBLE_ALPHA
                        val currentBgColor = cardBgAnim?.value ?: resolvedCardBg
                        drawRoundRect(
                            color = currentBgColor.copy(alpha = currentHiddenAlpha),
                            cornerRadius = cornerRadius,
                        )
                    }
                }.clip(posterShape)
                .drawWithContent {
                    drawContent()

                    val cornerRadiusPx = posterCornerRadius.toPx()
                    val borderWidthDp = if (isSelected) HPC_BORDER_SELECTED_WIDTH else HPC_BORDER_UNSELECTED_WIDTH
                    val borderWidthPx = borderWidthDp.toPx()
                    val borderColor = if (isSelected) appColors.accent else appColors.divider
                    val halfBorder = borderWidthPx / HPC_STROKE_INSET_DIVISOR
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(halfBorder, halfBorder),
                        size = Size(size.width - borderWidthPx, size.height - borderWidthPx),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = borderWidthPx),
                    )
                }.noFocusClickable(onClick = handleCardClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                if (cardHiddenAlphaAnim != null) {
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = cardHiddenAlphaAnim.value }
                } else {
                    Modifier.fillMaxSize()
                },
        ) {
            itemContent(actualIndex, isSelected)
        }
    }
}
