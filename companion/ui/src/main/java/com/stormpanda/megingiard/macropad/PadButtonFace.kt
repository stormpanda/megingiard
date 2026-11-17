package com.stormpanda.megingiard.macropad

import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ui.AppPalettes
import kotlin.math.sqrt

private const val TAG = "PadButtonFace"
private val PBF_BACKING_COLOR = AppPalettes.padButtonBacking
private val PBF_BORDER_WIDTH = 1.dp
private const val PBF_CENTER_NORM = 0.5f
private const val PBF_GRADIENT_MID_STOP_1 = 0.50f
private const val PBF_GRADIENT_MID_ALPHA_1 = 0.25f
private const val PBF_GRADIENT_MID_STOP_2 = 0.75f
private const val PBF_GRADIENT_MID_ALPHA_2 = 0.5625f
private val PBF_ZERO_OFFSET = NormalizedPoint(0f, 0f)

internal val PBF_SQUARE_RADIUS = 4.dp
internal val PBF_SQUARE_SHAPE = RoundedCornerShape(PBF_SQUARE_RADIUS)
private const val PBF_ROUNDED_SQUARE_PERCENT = 25
internal val PBF_ROUNDED_SQUARE_SHAPE = RoundedCornerShape(percent = PBF_ROUNDED_SQUARE_PERCENT)
private const val PBF_PILL_PERCENT = 50
internal val PBF_PILL_SHAPE = RoundedCornerShape(percent = PBF_PILL_PERCENT)
private const val PBF_OUTLINE_CACHE_SIZE = 16

private class PolygonButtonShape(
    private val vertices: List<NormalizedPoint>,
    val contentCenterOffset: NormalizedPoint,
) : Shape {
    private val outlineCache = LruCache<Size, Outline.Generic>(PBF_OUTLINE_CACHE_SIZE)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        synchronized(outlineCache) {
            outlineCache.get(size)?.let { return it }
        }
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
        return Outline.Generic(path).also { outline ->
            synchronized(outlineCache) {
                outlineCache.put(size, outline)
            }
        }
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
 * Resolves the Compose [Shape] for a [PadButton] given its [buttonShape], pixel dimensions, and trackpoint state.
 */
internal fun resolvePadButtonShape(
    buttonShape: ButtonShape,
    widthPx: Float,
    heightPx: Float,
    isTrackpoint: Boolean = false,
): Shape {
    if (isTrackpoint) return CircleShape
    return when (buttonShape) {
        ButtonShape.SQUARE,
        ButtonShape.ICON_ONLY,
        -> {
            PBF_SQUARE_SHAPE
        }

        ButtonShape.ROUNDED_SQUARE -> {
            PBF_ROUNDED_SQUARE_SHAPE
        }

        ButtonShape.CIRCLE -> {
            if (widthPx == heightPx) CircleShape else PBF_PILL_SHAPE
        }

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
        -> {
            PBF_POLYGON_SHAPES[buttonShape] ?: run {
                AppLog.w(TAG, "Missing polygon vertices for shape=$buttonShape; falling back to square")
                PBF_SQUARE_SHAPE
            }
        }
    }
}

/**
 * Resolves the 1:1 preview [Shape] for a [ButtonShape] in editor preview badges.
 */
internal fun resolvePadButtonPreviewShape(buttonShape: ButtonShape): Shape =
    resolvePadButtonShape(
        buttonShape = buttonShape,
        widthPx = MP_THOR_DEFAULT_BUTTON_SIZE_PX,
        heightPx = MP_THOR_DEFAULT_BUTTON_SIZE_PX,
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
    val contentOffset = (shape as? PolygonButtonShape)?.contentCenterOffset ?: PBF_ZERO_OFFSET

    val faceModifier =
        if (isIconOnly) {
            modifier.size(width, height)
        } else {
            modifier
                .size(width, height)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val borderStroke = Stroke(width = PBF_BORDER_WIDTH.toPx())
                    val halfDiag = (sqrt(size.width * size.width + size.height * size.height) / 2f).coerceAtLeast(1f)
                    val maxAlpha = bgColor.alpha.coerceIn(0f, 1f)
                    val radialBrush =
                        Brush.radialGradient(
                            0.00f to bgColor.copy(alpha = 0f),
                            PBF_GRADIENT_MID_STOP_1 to bgColor.copy(alpha = maxAlpha * PBF_GRADIENT_MID_ALPHA_1),
                            PBF_GRADIENT_MID_STOP_2 to bgColor.copy(alpha = maxAlpha * PBF_GRADIENT_MID_ALPHA_2),
                            1.00f to bgColor.copy(alpha = maxAlpha),
                            center =
                                Offset(
                                    size.width * (PBF_CENTER_NORM + contentOffset.x),
                                    size.height * (PBF_CENTER_NORM + contentOffset.y),
                                ),
                            radius = halfDiag,
                        )
                    val effectiveBackingColor = PBF_BACKING_COLOR.copy(alpha = PBF_BACKING_COLOR.alpha * bgColor.alpha)
                    onDrawWithContent {
                        drawOutline(outline = outline, color = effectiveBackingColor)
                        drawOutline(outline = outline, brush = radialBrush)
                        drawContent()
                        if (borderColor.alpha > 0f) {
                            drawOutline(outline = outline, color = borderColor, style = borderStroke)
                        }
                    }
                }
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier = faceModifier,
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
