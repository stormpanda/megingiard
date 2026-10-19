package com.stormpanda.megingiard.macropad

import kotlin.math.abs
import kotlin.math.hypot
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

// Regular hexagon & equilateral triangle altitude inset: (1 - sqrt(3) / 2) / 2 and (1 + sqrt(3) / 2) / 2
private const val BSG_SQRT3_INSET = 0.0669873f
private const val BSG_SQRT3_FAR = 0.9330127f

// Regular pentagon (side = (sqrt(5) - 1) / 2, height = sin(72°)) centered in [0f, 1f] × [0f, 1f]
private const val BSG_PENT_BASE_INSET_X = 0.190983f
private const val BSG_PENT_BASE_FAR_X = 0.809017f
private const val BSG_PENT_Y_INSET = 0.02447174f
private const val BSG_PENT_Y_FAR = 0.97552826f
private const val BSG_PENT_CORNER_UP_SHOULDER_Y = 0.387743f
private const val BSG_PENT_EDGE_UP_SHOULDER_Y = 0.612257f

// Regular octagon corner cut fraction: (2 - sqrt(2)) / 2
private const val BSG_OCT_INSET = 0.2928932f
private const val BSG_OCT_FAR = 0.7071068f

private const val BSG_EDGE_EPSILON = 1e-5f
private val BSG_ZERO_OFFSET = NormalizedPoint(0f, 0f)

/**
 * Pure geometry definitions and hit-testing math for [ButtonShape].
 *
 * Every polygonal shape is defined as a true regular polygon (all edge lengths equal and
 * all interior angles equal) maximally fitted and centered within `[0f, 1f] × [0f, 1f]`.
 */
object ButtonShapeGeometry {
    private val HEXAGON_1_VERTICES =
        listOf(
            NormalizedPoint(BSG_HEX_INSET, BSG_SQRT3_INSET),
            NormalizedPoint(BSG_HEX_FAR, BSG_SQRT3_INSET),
            NormalizedPoint(1f, BSG_HALF),
            NormalizedPoint(BSG_HEX_FAR, BSG_SQRT3_FAR),
            NormalizedPoint(BSG_HEX_INSET, BSG_SQRT3_FAR),
            NormalizedPoint(0f, BSG_HALF),
        )

    private val HEXAGON_2_VERTICES =
        listOf(
            NormalizedPoint(BSG_HALF, 0f),
            NormalizedPoint(BSG_SQRT3_FAR, BSG_HEX_INSET),
            NormalizedPoint(BSG_SQRT3_FAR, BSG_HEX_FAR),
            NormalizedPoint(BSG_HALF, 1f),
            NormalizedPoint(BSG_SQRT3_INSET, BSG_HEX_FAR),
            NormalizedPoint(BSG_SQRT3_INSET, BSG_HEX_INSET),
        )

    private val PENTAGON_1_VERTICES =
        listOf(
            NormalizedPoint(BSG_PENT_BASE_INSET_X, BSG_PENT_Y_INSET),
            NormalizedPoint(BSG_PENT_BASE_FAR_X, BSG_PENT_Y_INSET),
            NormalizedPoint(1f, BSG_PENT_EDGE_UP_SHOULDER_Y),
            NormalizedPoint(BSG_HALF, BSG_PENT_Y_FAR),
            NormalizedPoint(0f, BSG_PENT_EDGE_UP_SHOULDER_Y),
        )

    private val PENTAGON_2_VERTICES =
        listOf(
            NormalizedPoint(BSG_HALF, BSG_PENT_Y_INSET),
            NormalizedPoint(1f, BSG_PENT_CORNER_UP_SHOULDER_Y),
            NormalizedPoint(BSG_PENT_BASE_FAR_X, BSG_PENT_Y_FAR),
            NormalizedPoint(BSG_PENT_BASE_INSET_X, BSG_PENT_Y_FAR),
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
            NormalizedPoint(BSG_HALF, BSG_SQRT3_INSET),
            NormalizedPoint(1f, BSG_SQRT3_FAR),
            NormalizedPoint(0f, BSG_SQRT3_FAR),
        )

    private val TRIANGLE_DOWN_VERTICES =
        listOf(
            NormalizedPoint(0f, BSG_SQRT3_INSET),
            NormalizedPoint(1f, BSG_SQRT3_INSET),
            NormalizedPoint(BSG_HALF, BSG_SQRT3_FAR),
        )

    private val TRIANGLE_LEFT_VERTICES =
        listOf(
            NormalizedPoint(BSG_SQRT3_INSET, BSG_HALF),
            NormalizedPoint(BSG_SQRT3_FAR, 0f),
            NormalizedPoint(BSG_SQRT3_FAR, 1f),
        )

    private val TRIANGLE_RIGHT_VERTICES =
        listOf(
            NormalizedPoint(BSG_SQRT3_INSET, 0f),
            NormalizedPoint(BSG_SQRT3_FAR, BSG_HALF),
            NormalizedPoint(BSG_SQRT3_INSET, 1f),
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

    private val CONTENT_CENTER_OFFSETS: Map<ButtonShape, NormalizedPoint> =
        ButtonShape.entries.associateWith { shape ->
            val vertices = verticesFor(shape)
            if (vertices.isNullOrEmpty()) {
                BSG_ZERO_OFFSET
            } else {
                var sumX = 0f
                var sumY = 0f
                for (v in vertices) {
                    sumX += v.x
                    sumY += v.y
                }
                val dx = sumX / vertices.size - BSG_HALF
                val dy = sumY / vertices.size - BSG_HALF
                NormalizedPoint(
                    x = if (abs(dx) < BSG_EDGE_EPSILON) 0f else dx,
                    y = if (abs(dy) < BSG_EDGE_EPSILON) 0f else dy,
                )
            }
        }

    /**
     * Returns the normalized offset `(dx, dy)` from the bounding-box midpoint `(0.5f, 0.5f)` to
     * the shape's true geometric center (centroid and incircle center, where perpendicular
     * distance to every polygon edge is equal).
     *
     * Shapes that are symmetric across both the horizontal and vertical axes return `(0f, 0f)`.
     * Shapes whose halves are not congruent across both axes (`PENTAGON_1`, `PENTAGON_2`,
     * `TRIANGLE_UP`, `TRIANGLE_DOWN`, `TRIANGLE_LEFT`, `TRIANGLE_RIGHT`) return a non-zero offset
     * of magnitude `(R - r) / 2` toward the flat base edge (`±0.05020285f` for pentagons,
     * `±sqrt(3)/12 ≈ ±0.14433757f` for equilateral triangles).
     */
    fun contentCenterOffsetFor(
        shape: ButtonShape,
        isTrackpoint: Boolean = false,
    ): NormalizedPoint {
        if (isTrackpoint) return BSG_ZERO_OFFSET
        return CONTENT_CENTER_OFFSETS[shape] ?: BSG_ZERO_OFFSET
    }

    private val CONTENT_SCALES: Map<ButtonShape, Float> =
        ButtonShape.entries.associateWith { shape ->
            val vertices = verticesFor(shape)
            if (vertices == null || vertices.size < 3) {
                1f
            } else {
                val offset = contentCenterOffsetFor(shape)
                val cx = BSG_HALF + offset.x
                val cy = BSG_HALF + offset.y
                val a = vertices[0]
                val b = vertices[1]
                val ex = b.x - a.x
                val ey = b.y - a.y
                val edgeLen = hypot(ex, ey)
                val inradius = if (edgeLen > 0f) abs(ex * (cy - a.y) - ey * (cx - a.x)) / edgeLen else BSG_HALF
                (inradius / BSG_HALF).coerceIn(0f, 1f)
            }
        }

    /**
     * Returns the content scale factor `(0f..1f]` for icons and labels inside [shape], equal to
     * the ratio of the shape's inscribed circle diameter `2r` to the unit circle diameter `1.0`.
     *
     * - `SQUARE`, `ROUNDED_SQUARE`, `CIRCLE`, `ICON_ONLY`, `OCTAGON`: `1.0f`
     * - `HEXAGON_1`, `HEXAGON_2`: `sqrt(3) / 2 ≈ 0.8660254f`
     * - `PENTAGON_1`, `PENTAGON_2`: `1 / (2 * sin(36°)) ≈ 0.8506508f`
     * - `DIAMOND`: `sqrt(2) / 2 ≈ 0.7071068f`
     * - `TRIANGLE_UP`, `TRIANGLE_DOWN`, `TRIANGLE_LEFT`, `TRIANGLE_RIGHT`: `sqrt(3) / 3 ≈ 0.5773503f`
     */
    fun contentScaleFor(
        shape: ButtonShape,
        isTrackpoint: Boolean = false,
    ): Float {
        if (isTrackpoint) return 1f
        return CONTENT_SCALES[shape] ?: 1f
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
