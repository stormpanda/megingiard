package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        // TRIANGLE_UP: apex at (0.5, 0), base from (0, 1) to (1, 1)
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.05f, 0.05f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.95f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.05f, 0.95f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_UP, 0.95f, 0.95f, 60f, 60f))

        // TRIANGLE_DOWN: base from (0, 0) to (1, 0), apex at (0.5, 1)
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.05f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.95f, 0.05f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.05f, 0.95f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_DOWN, 0.95f, 0.95f, 60f, 60f))

        // TRIANGLE_LEFT: apex at (0, 0.5), base from (1, 0) to (1, 1)
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_LEFT, 0.05f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_LEFT, 0.95f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_LEFT, 0.95f, 0.95f, 60f, 60f))

        // TRIANGLE_RIGHT: base from (0, 0) to (0, 1), apex at (1, 0.5)
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_RIGHT, 0.05f, 0.05f, 60f, 60f))
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_RIGHT, 0.05f, 0.95f, 60f, 60f))
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.TRIANGLE_RIGHT, 0.95f, 0.05f, 60f, 60f))
    }

    @Test
    fun `hexagon and pentagon variants differ in top-center vs top-edge containment`() {
        // HEXAGON_1 (edge up) has flat top from x=0.25..0.75 at y=0 -> (0.26, 0.02) is inside
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.HEXAGON_1, 0.26f, 0.02f, 60f, 60f))
        // HEXAGON_2 (corner up) slopes from (0.5, 0) to (0, 0.25) -> at x=0.26, y_edge = 0.12 > 0.02 -> outside
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.HEXAGON_2, 0.26f, 0.02f, 60f, 60f))

        // PENTAGON_1 (edge up) has flat top from x=0.191..0.809 at y=0 -> (0.25, 0.02) is inside
        assertTrue(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.PENTAGON_1, 0.25f, 0.02f, 60f, 60f))
        // PENTAGON_2 (corner up) slopes from (0.5, 0) to (0, 0.382) -> (0.25, 0.02) is outside
        assertFalse(ButtonShapeGeometry.isNormalizedPointInside(ButtonShape.PENTAGON_2, 0.25f, 0.02f, 60f, 60f))
    }
}
