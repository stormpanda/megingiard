package com.stormpanda.megingiard.gamefocus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.ui.LocalAppColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val HPC_DEFAULT_POSTER_WIDTH = 175.dp
private val HPC_DEFAULT_POSTER_HEIGHT = 262.dp
private val HPC_DEFAULT_POSTER_SPACING = 13.5.dp
private val HPC_DEFAULT_CAROUSEL_HEIGHT = 310.dp
private val HPC_DEFAULT_CORNER_RADIUS = 16.dp

@Composable
fun HorizontalPosterCarousel(
    itemCount: Int,
    pagerState: PagerState,
    onItemClick: (actualIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    targetPage: Int = pagerState.targetPage,
    posterWidth: Dp = HPC_DEFAULT_POSTER_WIDTH,
    posterHeight: Dp = HPC_DEFAULT_POSTER_HEIGHT,
    posterSpacing: Dp = HPC_DEFAULT_POSTER_SPACING,
    carouselHeight: Dp = HPC_DEFAULT_CAROUSEL_HEIGHT,
    posterCornerRadius: Dp = HPC_DEFAULT_CORNER_RADIUS,
    ambientGlowColor: Color = Color.Unspecified,
    itemContent: @Composable (actualIndex: Int, isSelected: Boolean) -> Unit,
) {
    if (itemCount <= 0) return

    val appColors = LocalAppColors.current
    val activeGlowColor = if (ambientGlowColor != Color.Unspecified) ambientGlowColor else appColors.accent
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(carouselHeight),
    ) {
        val horizontalPadding = ((maxWidth - posterWidth) / 2).coerceAtLeast(0.dp)

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(posterWidth),
            pageSpacing = posterSpacing,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val actualIndex = Math.floorMod(page, itemCount)
            val isSelected = page == targetPage

            // Synchronized continuous page offset calculation using getOffsetDistanceInPages
            val rawOffset = pagerState.getOffsetDistanceInPages(page)
            val pageOffset = rawOffset.absoluteValue

            val scale = (1.18f - (pageOffset * 0.33f)).coerceIn(0.85f, 1.18f)
            val alpha = (1.0f - (pageOffset * 0.45f)).coerceIn(0.55f, 1.0f)

            // Compensate for center card scale expansion (1.18x) by pushing direct left/right neighbors outwards
            val sign = kotlin.math.sign(rawOffset)
            val neighborFactor = (1.0f - (rawOffset.absoluteValue - 1.0f).absoluteValue).coerceIn(0.0f, 1.0f)
            val extraPushPx =
                remember(density) {
                    with(density) { 16.dp.toPx() }
                }
            val translationXShift = sign * neighborFactor * extraPushPx

            Box(
                modifier =
                    Modifier
                        .size(posterWidth, posterHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = translationXShift
                            this.alpha = alpha
                        }.shadow(
                            elevation = if (isSelected) 20.dp else 4.dp,
                            shape = RoundedCornerShape(posterCornerRadius),
                            ambientColor = if (isSelected) activeGlowColor else Color.Black,
                            spotColor = if (isSelected) activeGlowColor else Color.Black,
                        ).clip(RoundedCornerShape(posterCornerRadius))
                        .background(
                            if (isSelected) appColors.surfaceVariant else appColors.surface,
                        ).border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) appColors.accent else appColors.divider,
                            shape = RoundedCornerShape(posterCornerRadius),
                        ).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
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
                itemContent(actualIndex, isSelected)
            }
        }
    }
}
