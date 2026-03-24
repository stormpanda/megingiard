package com.stormpanda.megingiard.touchpad

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TouchAction { DOWN, MOVE, UP }

object TouchpadManager {
    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _primaryDisplayWidth = MutableStateFlow(0)
    val primaryDisplayWidth: StateFlow<Int> = _primaryDisplayWidth.asStateFlow()

    private val _primaryDisplayHeight = MutableStateFlow(0)
    val primaryDisplayHeight: StateFlow<Int> = _primaryDisplayHeight.asStateFlow()

    fun setAccessibilityEnabled(enabled: Boolean) { _isAccessibilityEnabled.value = enabled }
    fun setPrimaryDisplaySize(width: Int, height: Int) {
        _primaryDisplayWidth.value = width
        _primaryDisplayHeight.value = height
    }

    /**
     * Injects a touch event onto the primary display.
     * @param action  DOWN, MOVE, or UP
     * @param normalizedX  0.0 (left edge) … 1.0 (right edge) relative to the 16:9 touch area
     * @param normalizedY  0.0 (top edge) … 1.0 (bottom edge) relative to the 16:9 touch area
     */
    fun injectTouch(action: TouchAction, normalizedX: Float, normalizedY: Float) {
        val service = MegingiardAccessibilityService.instance ?: return
        val absX = normalizedX * _primaryDisplayWidth.value
        val absY = normalizedY * _primaryDisplayHeight.value
        service.handleTouch(action, absX, absY)
    }
}
