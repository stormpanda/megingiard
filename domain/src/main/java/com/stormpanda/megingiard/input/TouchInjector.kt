package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog

@Suppress("unused")
private const val TAG = "TouchInjector"

/**
 * Shared touch injection facade used by both Touchpad and Mirror Touch Projection.
 *
 * Converts normalised logical-display coordinates to the physical portrait space of
 * the AYN Thor's primary touchscreen (`fts_ts`, `/dev/input/event6`) and pipes the
 * command to [ShellInputInjector].
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
    private const val PHYS_W = 1080
    private const val PHYS_H = 1920

    /**
     * Starts the native touch injector. Safe to call if already running (no-op).
     */
    fun start(context: Context) {
        AppLog.i(TAG, "start()")
        if (!ShellInputInjector.isRunning) ShellInputInjector.start(context)
    }

    fun stop() {
        AppLog.i(TAG, "stop()")
        ShellInputInjector.stop()
    }

    val isRunning: Boolean
        get() = ShellInputInjector.isRunning

    /**
     * Injects a touch event using normalised coordinates.
     *
     * @param action       DOWN / MOVE / UP
     * @param normalizedX  0.0 (left) … 1.0 (right) of the logical display
     * @param normalizedY  0.0 (top)  … 1.0 (bottom) of the logical display
     */
    fun injectTouch(action: TouchAction, normalizedX: Float, normalizedY: Float) {
        val px = ((1f - normalizedY) * PHYS_W).toInt()
        val py = (normalizedX * PHYS_H).toInt()
        ShellInputInjector.injectTouch(action, px, py)
    }
}
