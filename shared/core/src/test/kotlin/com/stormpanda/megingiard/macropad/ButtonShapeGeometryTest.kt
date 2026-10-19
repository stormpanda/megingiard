package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.hypot

class ButtonShapeGeometryTest {
    @Test
    fun `verticesFor returns expected vertex counts and normalized bounds for all shapes`() {
        val expectedCounts =
            mapOf(
                ButtonShape.SQUARE to null,
                ButtonShape.ROUNDED_SQUARE to null,
                ButtonShape.CIRCLE to null,
                ButtonShape.HEXAGON_1 to 6,
                ButtonShape.HEXAGON_2 to 6,
                ButtonShape.PENTAGON_1 to 5,
                ButtonShape.PENTAGON_2 to 5,
                ButtonShape.OCTAGON to 8,
                ButtonShape.DIAMOND to 4,
                ButtonShape.TRIANGLE_UP to 3,
                ButtonShape.TRIANGLE_DOWN to 3,
                ButtonShape.TRIANGLE_LEFT to 3,
                ButtonShape.TRIANGLE_RIGHT to 3,
                ButtonShape.ICON_ONLY to null,
            )

        for (shape in ButtonShape.entries) {
            val vertices = ButtonShapeGeometry.verticesFor(shape)
            val expected = expectedCounts[shape]
            if (expected == null) {
                assertNull("Expected null vertices for $shape", vertices)
            } else {
                assertNotNull("Expected non-null vertices for $shape", vertices)
                assertEquals("Vertex count mismatch for $shape", expected, vertices!!.size)
                for (pt in vertices) {
                    assertTrue("x out of [0,1] for $shape: ${pt.x}", pt.x in 0f..1f)
                    assertTrue("y out of [0,1] for $shape: ${pt.y}", pt.y in 0f..1f)
                }
            }
        }
    }

    @Test
    fun `all polygon shapes are regular with equal edge lengths and equal interior angles`() {
        val polygonShapes =
            listOf(
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
            )

        for (shape in polygonShapes) {
            val vertices = ButtonShapeGeometry.verticesFor(shape)!!
            val count = vertices.size
            val edgeLengths =
                (0 until count).map { i ->
                    val a = vertices[i]
                    val b = vertices[(i + 1) % count]
                    hypot(b.x - a.x, b.y - a.y)
                }
            val firstEdge = edgeLengths.first()
            for ((index, length) in edgeLengths.withIndex()) {
                assertEquals(
                    "Edge $index length mismatch for $shape",
                    firstEdge,
                    length,
                    1e-4f,
                )
            }

            val expectedInteriorAngleDeg = (count - 2) * 180.0 / count
            for (i in 0 until count) {
                val prev = vertices[(i - 1 + count) % count]
                val curr = vertices[i]
                val next = vertices[(i + 1) % count]
                val ux = (prev.x - curr.x).toDouble()
                val uy = (prev.y - curr.y).toDouble()
                val vx = (next.x - curr.x).toDouble()
                val vy = (next.y - curr.y).toDouble()
                val dot = ux * vx + uy * vy
                val mag = hypot(ux, uy) * hypot(vx, vy)
                val cosTheta = (dot / mag).coerceIn(-1.0, 1.0)
                val angleDeg = acos(cosTheta) * 180.0 / PI
                assertEquals(
                    "Interior angle at vertex $i mismatch for $shape",
                    expectedInteriorAngleDeg,
                    angleDeg,
                    0.05,
                )
            }
        }
    }

    @Test
    fun `center point (0_5, 0_5) is inside every ButtonShape`() {
        for (shape in ButtonShape.entries) {
            assertTrue(
                "Center should be inside $shape",
                ButtonShapeGeometry.isNormalizedPointInside(
                    shape = shape,
                    nx = 0.5f,
                    ny = 0.5f,
                    widthDp = 60f,
                    heightDp = 60f,
                ),
            )
        }
    }

    @Test
    fun `extreme corners are inside SQUARE and ICON_ONLY but outside rounded and polygon shapes`() {
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.SQUARE, 0.02f, 0.02f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.ICON_ONLY, 0.02f, 0.02f, 60f, 60f))

        val cornerRejectedShapes =
            listOf(
                ButtonShape.ROUNDED_SQUARE,
                ButtonShape.CIRCLE,
                ButtonShape.HEXAGON_1,
                ButtonShape.HEXAGON_2,
                ButtonShape.PENTAGON_1,
                ButtonShape.PENTAGON_2,
                ButtonShape.OCTAGON,
                ButtonShape.DIAMOND,
            )
        for (shape in cornerRejectedShapes) {
            assertFalse(
                "Top-left corner (0.02, 0.02) should be outside $shape",
                ButtonShapeGeometry.isNormalizedPointInside(shape, 0.02f, 0.02f, 60f, 60f),
            )
            assertFalse(
                "Bottom-right corner (0.98, 0.98) should be outside $shape",
                ButtonShapeGeometry.isNormalizedPointInside(shape, 0.98f, 0.98f, 60f, 60f),
            )
        }
    }

    @Test
    fun `directional triangles accept base corners and reject apex-side corners`() {
        // TRIANGLE_UP: apex at (0.5, 0.067), base from (0, 0.933) to (1, 0.933)
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.05f, 0.08f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.95f, 0.08f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.05f, 0.92f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.95f, 0.92f, 60f, 60f))

        // TRIANGLE_DOWN: base from (0, 0.067) to (1, 0.067), apex at (0.5, 0.933)
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.05f, 0.08f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.95f, 0.08f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.05f, 0.92f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.95f, 0.92f, 60f, 60f))

        // TRIANGLE_LEFT: apex at (0.067, 0.5), base from (0.933, 0) to (0.933, 1)
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_LEFT, 0.08f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_LEFT, 0.92f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_LEFT, 0.92f, 0.95f, 60f, 60f))

        // TRIANGLE_RIGHT: base from (0.067, 0) to (0.067, 1), apex at (0.933, 0.5)
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_RIGHT, 0.08f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_RIGHT, 0.08f, 0.95f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_RIGHT, 0.92f, 0.05f, 60f, 60f))
    }

    @Test
    fun `hexagon and pentagon variants differ in top-center vs top-edge containment`() {
        // HEXAGON_1 (edge up) has flat top from x=0.25..0.75 at y=0.067 -> (0.26, 0.08) is inside
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.HEXAGON_1, 0.26f, 0.08f, 60f, 60f))
        // HEXAGON_2 (corner up) slopes from (0.5, 0) to (0.067, 0.25) -> at x=0.26, y_edge = 0.139 > 0.08 -> outside
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.HEXAGON_2, 0.26f, 0.08f, 60f, 60f))

        // PENTAGON_1 (edge up) has flat top from x=0.191..0.809 at y=0.024 -> (0.25, 0.05) is inside
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.PENTAGON_1, 0.25f, 0.05f, 60f, 60f))
        // PENTAGON_2 (corner up) slopes from (0.5, 0.024) to (0, 0.388) -> at x=0.25, y_edge = 0.206 > 0.05 -> outside
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.PENTAGON_2, 0.25f, 0.05f, 60f, 60f))
    }
}
