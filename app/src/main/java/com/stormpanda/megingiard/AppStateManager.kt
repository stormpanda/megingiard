package com.stormpanda.megingiard

import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppMode { MIRROR, MEDIA, TOUCHPAD }

object AppStateManager {
    private val _currentMode = MutableStateFlow(AppMode.MIRROR)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _isActivityResumed = MutableStateFlow(true)
    val isActivityResumed: StateFlow<Boolean> = _isActivityResumed.asStateFlow()

    private val _isOnValidScreen = MutableStateFlow(true)
    val isOnValidScreen: StateFlow<Boolean> = _isOnValidScreen.asStateFlow()

    // Defaults to true so the mirror tool starts in idle state (no auto-capture on launch)
    private val _userDeclinedCapture = MutableStateFlow(true)
    val userDeclinedCapture: StateFlow<Boolean> = _userDeclinedCapture.asStateFlow()

    private val _promptInFlight = MutableStateFlow(false)
    val promptInFlight: StateFlow<Boolean> = _promptInFlight.asStateFlow()

    fun nextMode() {
        val active = SettingsManager.activeTools.value
        if (active.size <= 1) return
        val currentIndex = active.indexOf(_currentMode.value).coerceAtLeast(0)
        _currentMode.value = active[(currentIndex + 1) % active.size]
    }

    fun prevMode() {
        val active = SettingsManager.activeTools.value
        if (active.size <= 1) return
        val currentIndex = active.indexOf(_currentMode.value).coerceAtLeast(0)
        _currentMode.value = active[(currentIndex - 1 + active.size) % active.size]
    }

    fun setMode(mode: AppMode) { _currentMode.value = mode }
    fun setActivityResumed(resumed: Boolean) { _isActivityResumed.value = resumed }
    fun setOnValidScreen(valid: Boolean) { _isOnValidScreen.value = valid }
    fun setUserDeclinedCapture(declined: Boolean) { _userDeclinedCapture.value = declined }
    fun setPromptInFlight(inFlight: Boolean) { _promptInFlight.value = inFlight }
}
