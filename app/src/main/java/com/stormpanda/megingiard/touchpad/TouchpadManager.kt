package com.stormpanda.megingiard.touchpad

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TouchAction { DOWN, MOVE, UP }

object TouchpadManager {
    private val _primaryDisplayWidth = MutableStateFlow(0)
    val primaryDisplayWidth: StateFlow<Int> = _primaryDisplayWidth.asStateFlow()

    private val _primaryDisplayHeight = MutableStateFlow(0)
    val primaryDisplayHeight: StateFlow<Int> = _primaryDisplayHeight.asStateFlow()

    /**
     * Call once the primary display dimensions are known (and whenever they
     * change). Restarts the shell bridge targeted at [displayId].
     */
    fun setPrimaryDisplaySize(width: Int, height: Int, displayId: Int = 0) {
        _primaryDisplayWidth.value = width
        _primaryDisplayHeight.value = height
        if (ShellInputInjector.isRunning) ShellInputInjector.stop()
        ShellInputInjector.start(displayId)
    }

    /**
     * Injects a touch event onto the primary display via a persistent shell
     * process (`input -d <id> motionevent DOWN/MOVE/UP x y`). The shell runs
     * as uid 2000 which holds INJECT_EVENTS, so the event reaches every
     * window without any accessibility-service restriction.
     *
     * @param normalizedX  0.0 (left edge) … 1.0 (right edge) of the touch area
     * @param normalizedY  0.0 (top edge) … 1.0 (bottom edge) of the touch area
     */
    fun injectTouch(action: TouchAction, normalizedX: Float, normalizedY: Float) {
        val absX = normalizedX * _primaryDisplayWidth.value
        val absY = normalizedY * _primaryDisplayHeight.value
        ShellInputInjector.injectTouch(action, absX, absY)
    }
}
