package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.DisplayRotationDegrees
import com.stormpanda.megingiard.math.TouchSensorMath
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "TouchInjector"
private const val TOUCH_SLOT_MIN = 0
private const val TOUCH_SLOT_MAX = 9

/**
 * Shared touch injection facade used by both Touchpad and Mirror Touch Projection.
 *
 * Converts normalised logical-display coordinates to the physical sensor pixel space of
 * the handheld's primary touchscreen (reported by `megingiard_privd` via `EVIOCGABS` during
 * handshake) and routes them to the running Megingiard System Service (Privileged Mode).
 *
 * Supports all 4 primary display rotations ([DisplayRotationDegrees]) via [TouchSensorMath].
 * On portrait-mounted `1080 × 1920` panels running at `ROTATION_90` (AYN Thor, Retroid Pocket Duo),
 * sensor coordinates map as:
 *   `sensor_x = (1 − normalizedY) * sensorWidth`
 *   `sensor_y = normalizedX * sensorHeight`
 *
 * @param normalizedX  0.0 (left edge) … 1.0 (right edge) of the logical touch surface
 * @param normalizedY  0.0 (top edge)  … 1.0 (bottom edge) of the logical touch surface
 */
object TouchInjector {
    const val DEFAULT_SENSOR_W: Int = TouchSensorMath.DEFAULT_SENSOR_WIDTH
    const val DEFAULT_SENSOR_H: Int = TouchSensorMath.DEFAULT_SENSOR_HEIGHT

    val activeSensorWidth: Int
        get() = PrivdClient.touchSensorWidth

    val activeSensorHeight: Int
        get() = PrivdClient.touchSensorHeight

    @Volatile
    var primaryDisplayRotation: DisplayRotationDegrees = DisplayRotationDegrees.ROT_90
        private set

    fun updatePrimaryDisplayRotation(rotation: DisplayRotationDegrees) {
        val previous = primaryDisplayRotation
        if (previous != rotation) {
            primaryDisplayRotation = rotation
            AppLog.d(TAG, "updatePrimaryDisplayRotation: $previous -> $rotation")
        }
    }

    /**
     * Injects a touch event using normalised coordinates.
     *
     * Coordinates are clamped to the safe overrun range of [-0.5, 1.5] to prevent
     * signed integer overflow wrapping/jumps in target applications.
     */
    fun injectTouch(
        action: TouchAction,
        normalizedX: Float,
        normalizedY: Float,
    ) {
        injectTouch(0, action, normalizedX, normalizedY)
    }

    /**
     * Injects a slot-aware touch event using normalised coordinates.
     *
     * Coordinates are clamped to the safe overrun range of [-0.5, 1.5] to prevent
     * signed integer overflow wrapping/jumps in target applications.
     */
    fun injectTouch(
        slot: Int,
        action: TouchAction,
        normalizedX: Float,
        normalizedY: Float,
    ) {
        if (action == TouchAction.UP) {
            PrivdClient.send("U $slot\n")
            return
        }

        val sensorPoint =
            TouchSensorMath.normalizedToSensor(
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                sensorWidth = activeSensorWidth,
                sensorHeight = activeSensorHeight,
                rotation = primaryDisplayRotation,
            )
        val char = if (action == TouchAction.DOWN) "D" else "M"
        PrivdClient.send("$char $slot ${sensorPoint.x} ${sensorPoint.y}\n")
    }

    fun releaseAllSlots() {
        AppLog.i(TAG, "releaseAllSlots()")
        for (slot in TOUCH_SLOT_MIN..TOUCH_SLOT_MAX) {
            PrivdClient.send("U $slot\n")
        }
    }
}
