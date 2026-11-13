package com.stormpanda.megingiard.math

import com.stormpanda.megingiard.AppLog

private const val TAG = "TouchSensorMath"

/**
 * Canonical 90-degree display rotation steps corresponding to Android `Surface.ROTATION_*`.
 */
enum class DisplayRotationDegrees(
    val degrees: Int,
) {
    ROT_0(0),
    ROT_90(90),
    ROT_180(180),
    ROT_270(270),
    ;

    companion object {
        private const val SURFACE_ROTATION_0 = 0
        private const val SURFACE_ROTATION_90 = 1
        private const val SURFACE_ROTATION_180 = 2
        private const val SURFACE_ROTATION_270 = 3

        fun fromSurfaceRotation(surfaceRotation: Int): DisplayRotationDegrees =
            when (surfaceRotation) {
                SURFACE_ROTATION_0 -> {
                    ROT_0
                }

                SURFACE_ROTATION_90 -> {
                    ROT_90
                }

                SURFACE_ROTATION_180 -> {
                    ROT_180
                }

                SURFACE_ROTATION_270 -> {
                    ROT_270
                }

                else -> {
                    AppLog.w(TAG, "fromSurfaceRotation: unexpected surfaceRotation=$surfaceRotation, falling back to ROT_90")
                    ROT_90
                }
            }
    }
}

data class SensorPixelPoint(
    val x: Int,
    val y: Int,
)

data class NormalizedTouchPoint(
    val x: Float,
    val y: Float,
)

/**
 * Pure-JVM coordinate transforms between normalized logical display touch coordinates
 * and unrotated physical Linux `evdev` touchscreen sensor coordinates across all four
 * display mounting orientations.
 */
object TouchSensorMath {
    const val DEFAULT_SENSOR_WIDTH = 1080
    const val DEFAULT_SENSOR_HEIGHT = 1920
    private const val MIN_CLAMP_OVERRUN = -0.5f
    private const val MAX_CLAMP_OVERRUN = 1.5f
    private const val NORM_MIN = 0f
    private const val NORM_MAX = 1f

    /**
     * Maps normalized logical coordinates (`0.0..1.0`, clamped to the safe overrun window
     * `[-0.5, 1.5]`) to physical touchscreen sensor coordinates `(x, y)` for [rotation].
     */
    fun normalizedToSensor(
        normalizedX: Float,
        normalizedY: Float,
        sensorWidth: Int = DEFAULT_SENSOR_WIDTH,
        sensorHeight: Int = DEFAULT_SENSOR_HEIGHT,
        rotation: DisplayRotationDegrees = DisplayRotationDegrees.ROT_90,
    ): SensorPixelPoint {
        val safeX = if (normalizedX.isNaN()) NORM_MIN else normalizedX
        val safeY = if (normalizedY.isNaN()) NORM_MIN else normalizedY
        val cx = safeX.coerceIn(MIN_CLAMP_OVERRUN, MAX_CLAMP_OVERRUN)
        val cy = safeY.coerceIn(MIN_CLAMP_OVERRUN, MAX_CLAMP_OVERRUN)
        val w = (if (sensorWidth > 0) sensorWidth else DEFAULT_SENSOR_WIDTH).toFloat()
        val h = (if (sensorHeight > 0) sensorHeight else DEFAULT_SENSOR_HEIGHT).toFloat()

        return when (rotation) {
            DisplayRotationDegrees.ROT_0 -> {
                SensorPixelPoint(
                    x = (cx * w).toInt(),
                    y = (cy * h).toInt(),
                )
            }

            DisplayRotationDegrees.ROT_90 -> {
                SensorPixelPoint(
                    x = ((NORM_MAX - cy) * w).toInt(),
                    y = (cx * h).toInt(),
                )
            }

            DisplayRotationDegrees.ROT_180 -> {
                SensorPixelPoint(
                    x = ((NORM_MAX - cx) * w).toInt(),
                    y = ((NORM_MAX - cy) * h).toInt(),
                )
            }

            DisplayRotationDegrees.ROT_270 -> {
                SensorPixelPoint(
                    x = (cy * w).toInt(),
                    y = ((NORM_MAX - cx) * h).toInt(),
                )
            }
        }
    }

    /**
     * Maps raw physical `evdev` touchscreen coordinates `(rawX, rawY)` to normalized
     * logical display coordinates `[0.0, 1.0]` for [rotation].
     */
    fun sensorToNormalized(
        rawX: Int,
        rawY: Int,
        sensorWidth: Int = DEFAULT_SENSOR_WIDTH,
        sensorHeight: Int = DEFAULT_SENSOR_HEIGHT,
        rotation: DisplayRotationDegrees = DisplayRotationDegrees.ROT_90,
    ): NormalizedTouchPoint {
        val w = (if (sensorWidth > 0) sensorWidth else DEFAULT_SENSOR_WIDTH).toFloat()
        val h = (if (sensorHeight > 0) sensorHeight else DEFAULT_SENSOR_HEIGHT).toFloat()

        val nx: Float
        val ny: Float
        when (rotation) {
            DisplayRotationDegrees.ROT_0 -> {
                nx = rawX.toFloat() / w
                ny = rawY.toFloat() / h
            }

            DisplayRotationDegrees.ROT_90 -> {
                nx = rawY.toFloat() / h
                ny = NORM_MAX - (rawX.toFloat() / w)
            }

            DisplayRotationDegrees.ROT_180 -> {
                nx = NORM_MAX - (rawX.toFloat() / w)
                ny = NORM_MAX - (rawY.toFloat() / h)
            }

            DisplayRotationDegrees.ROT_270 -> {
                nx = NORM_MAX - (rawY.toFloat() / h)
                ny = rawX.toFloat() / w
            }
        }

        return NormalizedTouchPoint(
            x = nx.coerceIn(NORM_MIN, NORM_MAX),
            y = ny.coerceIn(NORM_MIN, NORM_MAX),
        )
    }
}
