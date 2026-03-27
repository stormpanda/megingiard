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

enum class AppMode { MIRROR, MEDIA, TOUCHPAD, KEYBOARD }

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

    // Pill gesture state shared across windows so the finger indicator survives
    // seamlessly when MirrorPresentation shows/hides during a drag.
    private val _pillExpanded = MutableStateFlow(false)
    val pillExpanded: StateFlow<Boolean> = _pillExpanded.asStateFlow()

    private val _pillFingerXFraction = MutableStateFlow(0f)
    val pillFingerXFraction: StateFlow<Float> = _pillFingerXFraction.asStateFlow()

    fun setPillExpanded(expanded: Boolean) { _pillExpanded.value = expanded }
    fun setPillFingerXFraction(fraction: Float) { _pillFingerXFraction.value = fraction }

    // Whether a finger is currently down on the screen. While true the auto-hide
    // timer is paused so the overlay stays visible even if the finger is held still.
    private val _isTouching = MutableStateFlow(false)
    val isTouching: StateFlow<Boolean> = _isTouching.asStateFlow()

    // Timestamp updated on every interaction to restart the auto-hide timer.
    private val _overlayInteractionTime = MutableStateFlow(0L)

    init {
        // Auto-hide coroutine: hides the overlay after the user-configured timeout.
        // collectLatest restarts whenever visibility, interaction time, touch, or timeout changes.
        scope.launch {
            combine(
                _overlayVisible,
                _overlayInteractionTime,
                _isTouching,
                SettingsManager.overlayTimeoutMs
            ) { visible, _, touching, timeout -> Triple(visible, touching, timeout) }
                .collectLatest { (visible, touching, timeout) ->
                    if (visible) {
                        // Wait until finger is lifted before starting the countdown
                        if (touching) return@collectLatest
                        delay(timeout)
                        _overlayVisible.value = false
                        _pillExpanded.value = false
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
    fun setTouching(touching: Boolean) { _isTouching.value = touching }
}
