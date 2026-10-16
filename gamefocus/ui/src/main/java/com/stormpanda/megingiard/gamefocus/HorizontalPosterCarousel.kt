package com.stormpanda.megingiard.gamefocus

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.floorMod
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sign
import android.graphics.Paint as NativePaint

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

@Composable
fun HorizontalPosterCarousel(
    itemCount: Int,
    pagerState: PagerState,
    onItemClick: (actualIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    key: ((page: Int) -> Any)? = null,
    targetPage: Int = pagerState.targetPage,
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
    val extraPushPx =
        remember(density) {
            with(density) { HPC_EXTRA_PUSH_DP.toPx() }
        }
    val posterShape = remember(posterCornerRadius) { RoundedCornerShape(posterCornerRadius) }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(carouselHeight),
    ) {
        val horizontalPadding = ((maxWidth - posterWidth) / 2).coerceAtLeast(0.dp)

        HorizontalPager(
            state = pagerState,
            key = key,
            pageSize = PageSize.Fixed(posterWidth),
            pageSpacing = posterSpacing,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val actualIndex = page.floorMod(itemCount)
            val isSelected = page == targetPage
            val isSettledAndSelected = isSelected && !pagerState.isScrollInProgress && page == pagerState.settledPage
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

    val shadowAlpha by animateFloatAsState(
        targetValue = if (isSettledAndSelected) 1f else 0f,
        animationSpec = tween(durationMillis = HPC_SHADOW_FADE_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PosterShadowFadeIn",
    )

    val cardHiddenAlpha by animateFloatAsState(
        targetValue = if (isCardHidden) HPC_HIDDEN_ALPHA else HPC_VISIBLE_ALPHA,
        animationSpec = tween(durationMillis = HPC_HIDE_ANIMATION_DURATION_MS),
        label = "CarouselCardHiddenAlpha",
    )

    val resolvedCardBg =
        cardBackgroundColor?.invoke(actualIndex, isSelected)
            ?: if (isSelected) appColors.surfaceVariant else appColors.surface

    val animatedCardBg by animateColorAsState(
        targetValue = resolvedCardBg,
        animationSpec = tween(durationMillis = HPC_CARD_BG_ANIMATION_DURATION_MS),
        label = "CarouselCardBgAnim",
    )

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
                    val s = (1.18f - (pageOffset * 0.33f)).coerceIn(0.85f, 1.18f)
                    val a = (1.0f - (pageOffset * 0.45f)).coerceIn(0.55f, 1.0f)
                    val sign = sign(rawOffset)
                    val neighborFactor = (1.0f - (rawOffset.absoluteValue - 1.0f).absoluteValue).coerceIn(0.0f, 1.0f)

                    scaleX = s
                    scaleY = s
                    alpha = a
                    translationX = sign * neighborFactor * extraPushPx
                }.drawBehind {
                    val cornerRadiusPx = posterCornerRadius.toPx()
                    if (shadowAlpha > 0f) {
                        val blurRadiusPx = HPC_VIBRANT_SHADOW_BLUR.toPx()
                        val spreadPx = HPC_VIBRANT_SHADOW_SPREAD.toPx()
                        val shadowColorArgb = appColors.accent.copy(alpha = HPC_VIBRANT_SHADOW_ALPHA * shadowAlpha).toArgb()

                        drawIntoCanvas { canvas ->
                            val nativePaint =
                                NativePaint().apply {
                                    isAntiAlias = true
                                    color = shadowColorArgb
                                    style = NativePaint.Style.FILL
                                    maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                                }
                            canvas.nativeCanvas.drawRoundRect(
                                -spreadPx,
                                -spreadPx,
                                size.width + spreadPx,
                                size.height + spreadPx,
                                cornerRadiusPx + spreadPx,
                                cornerRadiusPx + spreadPx,
                                nativePaint,
                            )
                        }
                    }
                    // Draw background here to avoid recomposing on cardHiddenAlpha changes
                    drawRoundRect(
                        color = animatedCardBg.copy(alpha = cardHiddenAlpha),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )
                }.clip(posterShape)
                .drawWithContent {
                    drawContent()

                    // Draw border overlay directly on canvas on top of artwork
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
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cardHiddenAlpha },
        ) {
            itemContent(actualIndex, isSelected)
        }
    }
}
