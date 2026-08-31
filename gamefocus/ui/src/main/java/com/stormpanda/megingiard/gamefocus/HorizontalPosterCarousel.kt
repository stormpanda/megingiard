package com.stormpanda.megingiard.gamefocus

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.stormpanda.megingiard.math.floorMod
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import android.graphics.Paint as NativePaint

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
    cardBackgroundColor: ((actualIndex: Int, isSelected: Boolean) -> Color)? = null,
    isHidden: ((actualIndex: Int) -> Boolean)? = null,
    itemContent: @Composable (actualIndex: Int, isSelected: Boolean) -> Unit,
) {
    if (itemCount <= 0) return

    val appColors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
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

            val shadowAlpha by animateFloatAsState(
                targetValue = if (isSettledAndSelected) 1f else 0f,
                animationSpec = tween(durationMillis = HPC_SHADOW_FADE_DURATION_MS, easing = FastOutSlowInEasing),
                label = "PosterShadowFadeIn",
            )

            val isCardHidden = isHidden?.invoke(actualIndex) ?: false
            val cardHiddenAlpha by animateFloatAsState(
                targetValue = if (isCardHidden) HPC_HIDDEN_ALPHA else HPC_VISIBLE_ALPHA,
                animationSpec = tween(durationMillis = HPC_HIDE_ANIMATION_DURATION_MS),
                label = "CarouselCardHiddenAlpha",
            )

            val resolvedCardBg =
                cardBackgroundColor?.invoke(actualIndex, isSelected)
                    ?: if (isSelected) appColors.surfaceVariant else appColors.surface

            Box(
                modifier =
                    Modifier
                        .size(posterWidth, posterHeight)
                        .graphicsLayer {
                            val pageCount = pagerState.pageCount
                            val rawOffset =
                                try {
                                    if (pageCount > 0 && page in 0 until pageCount) {
                                        pagerState.getOffsetDistanceInPages(page)
                                    } else {
                                        0f
                                    }
                                } catch (_: IllegalArgumentException) {
                                    0f
                                }
                            val pageOffset = rawOffset.absoluteValue
                            val s = (1.18f - (pageOffset * 0.33f)).coerceIn(0.85f, 1.18f)
                            val a = (1.0f - (pageOffset * 0.45f)).coerceIn(0.55f, 1.0f)
                            val sign = kotlin.math.sign(rawOffset)
                            val neighborFactor = (1.0f - (rawOffset.absoluteValue - 1.0f).absoluteValue).coerceIn(0.0f, 1.0f)

                            scaleX = s
                            scaleY = s
                            alpha = a
                            translationX = sign * neighborFactor * extraPushPx
                        }.drawBehind {
                            if (shadowAlpha > 0f) {
                                val cornerRadiusPx = posterCornerRadius.toPx()
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
                            val cornerRadiusPx = posterCornerRadius.toPx()
                            drawRoundRect(
                                color = resolvedCardBg.copy(alpha = cardHiddenAlpha),
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            )
                        }.clip(posterShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) appColors.accent else appColors.divider,
                            shape = posterShape,
                        ).noFocusClickable {
                            if (isSelected) {
                                onItemClick(actualIndex)
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(page)
                                }
                            }
                        },
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
    }
}
