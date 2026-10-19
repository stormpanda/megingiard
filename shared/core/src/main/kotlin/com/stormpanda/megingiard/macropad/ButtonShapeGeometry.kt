package com.stormpanda.megingiard.macropad

import kotlin.math.max
import kotlin.math.min

/**
 * Normalized 2D coordinate within a button's bounding box `[0f, 1f] × [0f, 1f]`,
 * where `(0f, 0f)` is top-left and `(1f, 1f)` is bottom-right.
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

/** Corner radius fraction (25% of the shorter side) used by [ButtonShape.ROUNDED_SQUARE]. */
const val MP_ROUNDED_SQUARE_CORNER_FRACTION = 0.25f

/** Corner radius fraction (50% of the shorter side) used by [ButtonShape.CIRCLE] (circle / pill). */
private const val BSG_CIRCLE_CORNER_FRACTION = 0.5f

private const val BSG_HALF = 0.5f
private const val BSG_HEX_INSET = 0.25f
private const val BSG_HEX_FAR = 0.75f

// Regular pentagon normalized to [0f, 1f] × [0f, 1f]
private const val BSG_PENT_BASE_INSET_X = 0.190983f
private const val BSG_PENT_BASE_FAR_X = 0.809017f
private const val BSG_PENT_CORNER_UP_SHOULDER_Y = 0.381966f
private const val BSG_PENT_EDGE_UP_SHOULDER_Y = 0.618034f

// Regular octagon corner cut fraction: (2 - sqrt(2)) / 2
private const val BSG_OCT_INSET = 0.2928932f
private const val BSG_OCT_FAR = 0.7071068f

private const val BSG_EDGE_EPSILON = 1e-5f

/**
 * Pure geometry definitions and hit-testing math for [ButtonShape].
 */
object ButtonShapeGeometry {
    private val HEXAGON_1_VERTICES =
        listOf(
            NormalizedPoint(BSG_HEX_INSET, 0f),
            NormalizedPoint(BSG_HEX_FAR, 0f),
            NormalizedPoint(1f, BSG_HALF),
            NormalizedPoint(BSG_HEX_FAR, 1f),
            NormalizedPoint(BSG_HEX_INSET, 1f),
            NormalizedPoint(0f, BSG_HALF),
        )

    private val HEXAGON_2_VERTICES =
        listOf(
            NormalizedPoint(BSG_HALF, 0f),
            NormalizedPoint(1f, BSG_HEX_INSET),
            NormalizedPoint(1f, BSG_HEX_FAR),
            NormalizedPoint(BSG_HALF, 1f),
            NormalizedPoint(0f, BSG_HEX_FAR),
            NormalizedPoint(0f, BSG_HEX_INSET),
        )

    private val PENTAGON_1_VERTICES =
        listOf(
            NormalizedPoint(BSG_PENT_BASE_INSET_X, 0f),
            NormalizedPoint(BSG_PENT_BASE_FAR_X, 0f),
            NormalizedPoint(1f, BSG_PENT_EDGE_UP_SHOULDER_Y),
            NormalizedPoint(BSG_HALF, 1f),
            NormalizedPoint(0f, BSG_PENT_EDGE_UP_SHOULDER_Y),
        )

    private val PENTAGON_2_VERTICES =
        listOf(
            NormalizedPoint(BSG_HALF, 0f),
            NormalizedPoint(1f, BSG_PENT_CORNER_UP_SHOULDER_Y),
            NormalizedPoint(BSG_PENT_BASE_FAR_X, 1f),
            NormalizedPoint(BSG_PENT_BASE_INSET_X, 1f),
            NormalizedPoint(0f, BSG_PENT_CORNER_UP_SHOULDER_Y),
        )

    private val OCTAGON_VERTICES =
        listOf(
            NormalizedPoint(BSG_OCT_INSET, 0f),
            NormalizedPoint(BSG_OCT_FAR, 0f),
            NormalizedPoint(1f, BSG_OCT_INSET),
            NormalizedPoint(1f, BSG_OCT_FAR),
            NormalizedPoint(BSG_OCT_FAR, 1f),
            NormalizedPoint(BSG_OCT_INSET, 1f),
            NormalizedPoint(0f, BSG_OCT_FAR),
            NormalizedPoint(0f, BSG_OCT_INSET),
        )

    private val DIAMOND_VERTICES =
        listOf(
            NormalizedPoint(BSG_HALF, 0f),
            NormalizedPoint(1f, BSG_HALF),
            NormalizedPoint(BSG_HALF, 1f),
            NormalizedPoint(0f, BSG_HALF),
        )

    private val TRIANGLE_UP_VERTICES =
        listOf(
            NormalizedPoint(BSG_HALF, 0f),
            NormalizedPoint(1f, 1f),
            NormalizedPoint(0f, 1f),
        )

    private val TRIANGLE_DOWN_VERTICES =
        listOf(
            NormalizedPoint(0f, 0f),
            NormalizedPoint(1f, 0f),
            NormalizedPoint(BSG_HALF, 1f),
        )

    private val TRIANGLE_LEFT_VERTICES =
        listOf(
            NormalizedPoint(0f, BSG_HALF),
            NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f),
        )

    private val TRIANGLE_RIGHT_VERTICES =
        listOf(
            NormalizedPoint(0f, 0f),
            NormalizedPoint(1f, BSG_HALF),
            NormalizedPoint(0f, 1f),
        )

    /**
     * Returns the clockwise normalized polygon vertices for polygonal [ButtonShape]s,
     * or `null` for non-polygonal shapes ([ButtonShape.SQUARE], [ButtonShape.ROUNDED_SQUARE],
     * [ButtonShape.CIRCLE], [ButtonShape.ICON_ONLY]).
     */
    fun verticesFor(shape: ButtonShape): List<NormalizedPoint>? =
        when (shape) {
            ButtonShape.HEXAGON_1 -> HEXAGON_1_VERTICES

            ButtonShape.HEXAGON_2 -> HEXAGON_2_VERTICES

            ButtonShape.PENTAGON_1 -> PENTAGON_1_VERTICES

            ButtonShape.PENTAGON_2 -> PENTAGON_2_VERTICES

            ButtonShape.OCTAGON -> OCTAGON_VERTICES

            ButtonShape.DIAMOND -> DIAMOND_VERTICES

            ButtonShape.TRIANGLE_UP -> TRIANGLE_UP_VERTICES

            ButtonShape.TRIANGLE_DOWN -> TRIANGLE_DOWN_VERTICES

            ButtonShape.TRIANGLE_LEFT -> TRIANGLE_LEFT_VERTICES

            ButtonShape.TRIANGLE_RIGHT -> TRIANGLE_RIGHT_VERTICES

            ButtonShape.SQUARE,
            ButtonShape.ROUNDED_SQUARE,
            ButtonShape.CIRCLE,
            ButtonShape.ICON_ONLY,
            -> null
        }

    /**
     * Evaluates whether a normalized point `(nx, ny)` in `[0f, 1f] × [0f, 1f]` lies inside
     * the effective visual shape of a button.
     *
     * @param shape        configured [ButtonShape] of the button
     * @param nx           normalized X coordinate relative to the button's bounding box (`0f` = left, `1f` = right)
     * @param ny           normalized Y coordinate relative to the button's bounding box (`0f` = top, `1f` = bottom)
     * @param widthDp      button width in dp
     * @param heightDp     button height in dp
     * @param isTrackpoint true when the button action is [PadAction.TrackpointMove] (always circular)
     */
    fun isNormalizedPointInside(
        shape: ButtonShape,
        nx: Float,
        ny: Float,
        widthDp: Float,
        heightDp: Float,
        isTrackpoint: Boolean = false,
    ): Boolean {
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return false
        val effectiveShape = if (isTrackpoint) ButtonShape.CIRCLE else shape
        return when (effectiveShape) {
            ButtonShape.SQUARE,
            ButtonShape.ICON_ONLY,
            -> {
                true
            }

            ButtonShape.ROUNDED_SQUARE -> {
                isInsideRoundedRect(
                    nx = nx,
                    ny = ny,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    cornerFraction = MP_ROUNDED_SQUARE_CORNER_FRACTION,
                )
            }

            ButtonShape.CIRCLE -> {
                isInsideRoundedRect(
                    nx = nx,
                    ny = ny,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    cornerFraction = BSG_CIRCLE_CORNER_FRACTION,
                )
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
                val vertices = verticesFor(effectiveShape) ?: return true
                isInsideConvexPolygon(nx, ny, vertices)
            }
        }
    }

    private fun isInsideRoundedRect(
        nx: Float,
        ny: Float,
        widthDp: Float,
        heightDp: Float,
        cornerFraction: Float,
    ): Boolean {
        val w = max(1f, widthDp)
        val h = max(1f, heightDp)
        val x = nx * w
        val y = ny * h
        val radius = cornerFraction * min(w, h)
        val dx = max(0f, max(radius - x, x - (w - radius)))
        val dy = max(0f, max(radius - y, y - (h - radius)))
        return dx * dx + dy * dy <= radius * radius + BSG_EDGE_EPSILON
    }

    private fun isInsideConvexPolygon(
        nx: Float,
        ny: Float,
        vertices: List<NormalizedPoint>,
    ): Boolean {
        val count = vertices.size
        for (i in 0 until count) {
            val v1 = vertices[i]
            val v2 = vertices[(i + 1) % count]
            val cross = (v2.x - v1.x) * (ny - v1.y) - (v2.y - v1.y) * (nx - v1.x)
            if (cross < -BSG_EDGE_EPSILON) {
                return false
            }
        }
        return true
    }
}
