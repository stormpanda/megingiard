package com.stormpanda.megingiard.touchpad

import android.content.Context

enum class TouchAction { DOWN, MOVE, UP }

object TouchpadManager {

    // Physical dimensions of fts_ts (event6) in portrait orientation.
    // These are fixed hardware constants; they do not change with display rotation.
    private const val PHYS_W = 1080
    private const val PHYS_H = 1920

    /**
     * Starts the native touch injector. Call once from [TouchpadScreen] via
     * LaunchedEffect(Unit). Safe to call multiple times — no-op if already running.
     */
    fun start(context: Context) {
        if (!ShellInputInjector.isRunning) ShellInputInjector.start(context)
    }

    fun stop() {
        ShellInputInjector.stop()
    }

    /**
     * Injects a touch event. Coordinates are normalised to the logical display
     * and remapped to the touchscreen's physical portrait space before dispatch.
     *
     * Display 0 runs at ROTATION_270 (sensor mounted inverted relative to the
     * logical landscape orientation). The sensor's portrait X/Y map as:
     *   sensor_x = (1 − normalizedY) * PHYS_W
     *   sensor_y = normalizedX * PHYS_H
     *
     * @param normalizedX  0.0 (left edge) … 1.0 (right edge) of the touch surface
     * @param normalizedY  0.0 (top edge)  … 1.0 (bottom edge) of the touch surface
     */
    fun injectTouch(action: TouchAction, normalizedX: Float, normalizedY: Float) {
        val px = ((1f - normalizedY) * PHYS_W).toInt()
        val py = (normalizedX * PHYS_H).toInt()
        ShellInputInjector.injectTouch(action, px, py)
    }
}
