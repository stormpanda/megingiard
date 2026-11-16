package com.stormpanda.megingiard.math

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchSensorMathTest {
    @Test
    fun `fromSurfaceRotation maps Android Surface rotation indices and falls back to ROT_90`() {
        assertEquals(DisplayRotationDegrees.ROT_0, DisplayRotationDegrees.fromSurfaceRotation(0))
        assertEquals(DisplayRotationDegrees.ROT_90, DisplayRotationDegrees.fromSurfaceRotation(1))
        assertEquals(DisplayRotationDegrees.ROT_180, DisplayRotationDegrees.fromSurfaceRotation(2))
        assertEquals(DisplayRotationDegrees.ROT_270, DisplayRotationDegrees.fromSurfaceRotation(3))
        assertEquals(DisplayRotationDegrees.ROT_90, DisplayRotationDegrees.fromSurfaceRotation(99))
    }

    @Test
    fun `ROT_90 portrait 1080x1920 maps normalized landscape coordinates accurately`() {
        val topLeft = TouchSensorMath.normalizedToSensor(0f, 0f, 1080, 1920, DisplayRotationDegrees.ROT_90)
        assertEquals(1080, topLeft.x)
        assertEquals(0, topLeft.y)

        val center = TouchSensorMath.normalizedToSensor(0.5f, 0.5f, 1080, 1920, DisplayRotationDegrees.ROT_90)
        assertEquals(540, center.x)
        assertEquals(960, center.y)

        val bottomRight = TouchSensorMath.normalizedToSensor(1f, 1f, 1080, 1920, DisplayRotationDegrees.ROT_90)
        assertEquals(0, bottomRight.x)
        assertEquals(1920, bottomRight.y)
    }

    @Test
    fun `round-trip normalized to sensor to normalized across all four rotations`() {
        val testPoints =
            listOf(
                0.0f to 0.0f,
                0.25f to 0.75f,
                0.5f to 0.5f,
                0.8f to 0.2f,
                1.0f to 1.0f,
            )

        for (rotation in DisplayRotationDegrees.entries) {
            for ((nx, ny) in testPoints) {
                val sensor =
                    TouchSensorMath.normalizedToSensor(
                        normalizedX = nx,
                        normalizedY = ny,
                        sensorWidth = 1200,
                        sensorHeight = 2000,
                        rotation = rotation,
                    )
                val restored =
                    TouchSensorMath.sensorToNormalized(
                        rawX = sensor.x,
                        rawY = sensor.y,
                        sensorWidth = 1200,
                        sensorHeight = 2000,
                        rotation = rotation,
                    )
                assertEquals("X mismatch for $rotation", nx, restored.x, 0.002f)
                assertEquals("Y mismatch for $rotation", ny, restored.y, 0.002f)
            }
        }
    }

    @Test
    fun `clamps overrun normalized coordinates and falls back on non-positive sensor dimensions`() {
        val clamped =
            TouchSensorMath.normalizedToSensor(
                normalizedX = -2.0f,
                normalizedY = 3.0f,
                sensorWidth = 0,
                sensorHeight = -1,
                rotation = DisplayRotationDegrees.ROT_90,
            )
        assertEquals(-540, clamped.x)
        assertEquals(-960, clamped.y)

        val normClamped =
            TouchSensorMath.sensorToNormalized(
                rawX = -200,
                rawY = 5000,
                sensorWidth = 0,
                sensorHeight = 0,
                rotation = DisplayRotationDegrees.ROT_0,
            )
        assertEquals(0f, normClamped.x, 0.0001f)
        assertEquals(1f, normClamped.y, 0.0001f)
    }

    @Test
    fun `normalizedToSensor guards NaN and Infinity deterministically`() {
        val nanPoint =
            TouchSensorMath.normalizedToSensor(
                normalizedX = Float.NaN,
                normalizedY = Float.NaN,
                sensorWidth = 1080,
                sensorHeight = 1920,
                rotation = DisplayRotationDegrees.ROT_90,
            )
        // NaN defaults to NORM_MIN (0f), which at ROT_90 maps to top-left (1080, 0)
        assertEquals(1080, nanPoint.x)
        assertEquals(0, nanPoint.y)

        val infPoint =
            TouchSensorMath.normalizedToSensor(
                normalizedX = Float.POSITIVE_INFINITY,
                normalizedY = Float.NEGATIVE_INFINITY,
                sensorWidth = 1080,
                sensorHeight = 1920,
                rotation = DisplayRotationDegrees.ROT_0,
            )
        assertEquals(1620, infPoint.x)
        assertEquals(-960, infPoint.y)
    }
}
