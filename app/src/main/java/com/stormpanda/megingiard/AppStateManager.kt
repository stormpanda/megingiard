package com.stormpanda.megingiard

import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class AppMode { MIRROR, MEDIA, TOUCHPAD }

object AppStateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    // ── Global overlay visibility (shared between Activity window and MirrorPresentation)
    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    // Timestamp updated on every interaction to restart the auto-hide timer.
    private val _overlayInteractionTime = MutableStateFlow(0L)

    init {
        // Auto-hide coroutine: hides the overlay after the user-configured timeout.
        // collectLatest restarts whenever visibility, interaction time, or timeout changes.
        scope.launch {
            combine(
                _overlayVisible,
                _overlayInteractionTime,
                SettingsManager.overlayTimeoutMs
            ) { visible, _, timeout -> visible to timeout }
                .collectLatest { (visible, timeout) ->
                    if (visible) {
                        delay(timeout)
                        _overlayVisible.value = false
                    }
                }
        }
    }

    fun triggerOverlay() {
        _overlayVisible.value = true
        _overlayInteractionTime.value = System.currentTimeMillis()
    }

    fun nextMode() {
        val active = SettingsManager.activeTools.value
        if (active.size <= 1) return
        val currentIndex = active.indexOf(_currentMode.value)
        if (currentIndex == -1) { _currentMode.value = active.first(); return }
        _currentMode.value = active[(currentIndex + 1) % active.size]
    }

    fun prevMode() {
        val active = SettingsManager.activeTools.value
        if (active.size <= 1) return
        val currentIndex = active.indexOf(_currentMode.value)
        if (currentIndex == -1) { _currentMode.value = active.first(); return }
        _currentMode.value = active[(currentIndex - 1 + active.size) % active.size]
    }

    fun setMode(mode: AppMode) { _currentMode.value = mode }
    fun setActivityResumed(resumed: Boolean) { _isActivityResumed.value = resumed }
    fun setOnValidScreen(valid: Boolean) { _isOnValidScreen.value = valid }
    fun setUserDeclinedCapture(declined: Boolean) { _userDeclinedCapture.value = declined }
    fun setPromptInFlight(inFlight: Boolean) { _promptInFlight.value = inFlight }
}
