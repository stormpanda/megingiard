package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.ui.AppPalettes
import kotlin.math.sqrt

private val PBF_BACKING_COLOR = AppPalettes.padButtonBacking
private val PBF_BORDER_WIDTH = 1.dp
private const val PBF_CENTER_NORM = 0.5f
private val PBF_ZERO_OFFSET = NormalizedPoint(0f, 0f)

internal val PBF_SQUARE_RADIUS = 4.dp
internal val PBF_SQUARE_SHAPE = RoundedCornerShape(PBF_SQUARE_RADIUS)
private const val PBF_ROUNDED_SQUARE_PERCENT = 25
internal val PBF_ROUNDED_SQUARE_SHAPE = RoundedCornerShape(percent = PBF_ROUNDED_SQUARE_PERCENT)
private const val PBF_PILL_PERCENT = 50
internal val PBF_PILL_SHAPE = RoundedCornerShape(percent = PBF_PILL_PERCENT)

private class PolygonButtonShape(
    private val vertices: List<NormalizedPoint>,
    val contentCenterOffset: NormalizedPoint,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path =
            Path().apply {
                if (vertices.isNotEmpty()) {
                    moveTo(vertices[0].x * size.width, vertices[0].y * size.height)
                    for (i in 1 until vertices.size) {
                        lineTo(vertices[i].x * size.width, vertices[i].y * size.height)
                    }
                    close()
                }
            }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PolygonButtonShape &&
                    vertices == other.vertices &&
                    contentCenterOffset == other.contentCenterOffset
            )

    override fun hashCode(): Int = 31 * vertices.hashCode() + contentCenterOffset.hashCode()
}

private val PBF_POLYGON_SHAPES: Map<ButtonShape, Shape> =
    ButtonShape.entries
        .mapNotNull { shape ->
            ButtonShapeGeometry.verticesFor(shape)?.let { vertices ->
                shape to
                    PolygonButtonShape(
                        vertices = vertices,
                        contentCenterOffset = ButtonShapeGeometry.contentCenterOffsetFor(shape),
                    )
            }
        }.toMap()

/**
 * Resolves the Compose [Shape] for a [PadButton] given its [buttonShape], dimensions, and trackpoint state.
 */
internal fun resolvePadButtonShape(
    buttonShape: ButtonShape,
    widthDp: Float,
    heightDp: Float,
    isTrackpoint: Boolean = false,
): Shape {
    if (isTrackpoint) return CircleShape
    return when (buttonShape) {
        ButtonShape.SQUARE,
        ButtonShape.ICON_ONLY,
        -> PBF_SQUARE_SHAPE

        ButtonShape.ROUNDED_SQUARE -> PBF_ROUNDED_SQUARE_SHAPE

        ButtonShape.CIRCLE -> if (widthDp == heightDp) CircleShape else PBF_PILL_SHAPE

        ButtonShape.HEXAGON_1,
        ButtonShape.HEXAGON_2,
        ButtonShape.PENTAGON_1,
        ButtonShape.PENTAGON_2,
        ButtonShape.OCTAGON,
        ButtonShape.DIAMOND,
        ButtonShape.TRIANGLE_UP,
        ButtonShape.TRIANGLE_DOWN,
        ButtonShape.TRIANGLE_LEFT,
        ButtonShape.TRIANGLE_RIGHT,
        -> PBF_POLYGON_SHAPES[buttonShape] ?: PBF_SQUARE_SHAPE
    }
}

/**
 * Resolves the 1:1 preview [Shape] for a [ButtonShape] in editor preview badges.
 */
internal fun resolvePadButtonPreviewShape(buttonShape: ButtonShape): Shape =
    resolvePadButtonShape(
        buttonShape = buttonShape,
        widthDp = MP_BUTTON_BASE_UNIT_DP,
        heightDp = MP_BUTTON_BASE_UNIT_DP,
        isTrackpoint = false,
    )

@Composable
internal fun PadButtonFace(
    width: Dp,
    height: Dp,
    shape: Shape,
    isIconOnly: Boolean,
    borderColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val contentOffset = (shape as? PolygonButtonShape)?.contentCenterOffset ?: PBF_ZERO_OFFSET

    val bgBrush =
        remember(bgColor, width, height, density, contentOffset) {
            val wPx = with(density) { width.toPx() }
            val hPx = with(density) { height.toPx() }
            val halfDiag = sqrt(wPx * wPx + hPx * hPx) / 2f
            val maxAlpha = bgColor.alpha.coerceIn(0f, 1f)
            Brush.radialGradient(
                0.00f to bgColor.copy(alpha = 0f),
                0.50f to bgColor.copy(alpha = maxAlpha * 0.25f),
                0.75f to bgColor.copy(alpha = maxAlpha * 0.5625f),
                1.00f to bgColor.copy(alpha = maxAlpha),
                center =
                    Offset(
                        wPx * (PBF_CENTER_NORM + contentOffset.x),
                        hPx * (PBF_CENTER_NORM + contentOffset.y),
                    ),
                radius = halfDiag,
            )
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(width, height)
                .clip(shape)
                .drawWithContent {
                    if (isIconOnly) {
                        drawContent()
                    } else {
                        val effectiveBackingColor = PBF_BACKING_COLOR.copy(alpha = PBF_BACKING_COLOR.alpha * bgColor.alpha)
                        drawRect(color = effectiveBackingColor)
                        drawRect(brush = bgBrush)
                        drawContent()
                    }
                }.then(
                    if (isIconOnly) {
                        Modifier
                    } else {
                        Modifier.border(PBF_BORDER_WIDTH, borderColor, shape)
                    },
                ),
    ) {
        if (contentOffset.x != 0f || contentOffset.y != 0f) {
            Box(
                modifier = Modifier.offset(x = width * contentOffset.x, y = height * contentOffset.y),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        } else {
            content()
        }
    }
}
