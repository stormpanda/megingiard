package com.stormpanda.megingiard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppMode { MIRROR, MEDIA }

object AppStateManager {
    private val _currentMode = MutableStateFlow(AppMode.MIRROR)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _isActivityResumed = MutableStateFlow(true)
    val isActivityResumed: StateFlow<Boolean> = _isActivityResumed.asStateFlow()

    private val _isOnValidScreen = MutableStateFlow(true)
    val isOnValidScreen: StateFlow<Boolean> = _isOnValidScreen.asStateFlow()

    private val _userDeclinedCapture = MutableStateFlow(false)
    val userDeclinedCapture: StateFlow<Boolean> = _userDeclinedCapture.asStateFlow()

    private val _promptInFlight = MutableStateFlow(false)
    val promptInFlight: StateFlow<Boolean> = _promptInFlight.asStateFlow()

    fun nextMode() {
        val values = AppMode.entries
        _currentMode.value = values[(_currentMode.value.ordinal + 1) % values.size]
    }

    fun prevMode() {
        val values = AppMode.entries
        _currentMode.value = values[(_currentMode.value.ordinal - 1 + values.size) % values.size]
    }

    fun setMode(mode: AppMode) { _currentMode.value = mode }
    fun setActivityResumed(resumed: Boolean) { _isActivityResumed.value = resumed }
    fun setOnValidScreen(valid: Boolean) { _isOnValidScreen.value = valid }
    fun setUserDeclinedCapture(declined: Boolean) { _userDeclinedCapture.value = declined }
    fun setPromptInFlight(inFlight: Boolean) { _promptInFlight.value = inFlight }
}
