package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient

private const val TAG = "TouchInjector"
private const val TOUCH_SLOT_MIN = 0
private const val TOUCH_SLOT_MAX = 9

/**
 * Shared touch injection facade used by both Touchpad and Mirror Touch Projection.
 *
 * Converts normalised logical-display coordinates to the physical portrait space of
 * the AYN Thor's primary touchscreen (`fts_ts`, `/dev/input/event6`) and routes them
 * to the running Megingiard System Service (Privileged Mode).
 *
 * Display 0 runs at ROTATION_270 (sensor mounted inverted relative to the logical
 * landscape orientation). The sensor's portrait X/Y map as:
 *   sensor_x = (1 − normalizedY) * PHYS_W
 *   sensor_y = normalizedX * PHYS_H
 *
 * @param normalizedX  0.0 (left edge) … 1.0 (right edge) of the logical touch surface
 * @param normalizedY  0.0 (top edge)  … 1.0 (bottom edge) of the logical touch surface
 */
object TouchInjector {
    // Physical dimensions of fts_ts (event6) in portrait orientation.
    // These are fixed hardware constants; they do not change with display rotation.
    const val THOR_SENSOR_W = 1080f
    const val THOR_SENSOR_H = 1920f

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
        val cx = normalizedX.coerceIn(-0.5f, 1.5f)
        val cy = normalizedY.coerceIn(-0.5f, 1.5f)
        val px = ((1f - cy) * THOR_SENSOR_W).toInt()
        val py = (cx * THOR_SENSOR_H).toInt()

        if (action == TouchAction.UP) {
            PrivdClient.send("U $slot\n")
        } else {
            val char = if (action == TouchAction.DOWN) "D" else "M"
            PrivdClient.send("$char $slot $px $py\n")
        }
    }

    fun releaseAllSlots() {
        AppLog.i(TAG, "releaseAllSlots()")
        for (slot in TOUCH_SLOT_MIN..TOUCH_SLOT_MAX) {
            PrivdClient.send("U $slot\n")
        }
    }
}
