package com.stormpanda.megingiard

import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.macropad.MacroPadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TAG = "AppStateManager"

object AppStateManager {
    // App-lifetime scope: intentionally never cancelled — this singleton lives for the
    // duration of the process. Cancellation is handled by process termination.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private val _isActivityResumed = MutableStateFlow(true)
    val isActivityResumed: StateFlow<Boolean> = _isActivityResumed.asStateFlow()

    private val _isOnValidScreen = MutableStateFlow(true)
    val isOnValidScreen: StateFlow<Boolean> = _isOnValidScreen.asStateFlow()

    // Defaults to true so mirror capture requires an explicit user action (button press).
    private val _userDeclinedCapture = MutableStateFlow(true)
    val userDeclinedCapture: StateFlow<Boolean> = _userDeclinedCapture.asStateFlow()

    private val _promptInFlight = MutableStateFlow(false)
    val promptInFlight: StateFlow<Boolean> = _promptInFlight.asStateFlow()

    // ── Mirror control signals ────────────────────────────────────────────────
    // One-shot fire-and-forget flags: MainActivity resets them after handling.

    /** Set to true by MirrorPlayStop when mirror is not yet capturing; MainActivity launches
     * CaptureRequestActivity and resets. Distinct from userDeclinedCapture so that a "declined"
     * state within the session can be overridden by an explicit button press. */
    private val _mirrorStartRequested = MutableStateFlow(false)
    val mirrorStartRequested: StateFlow<Boolean> = _mirrorStartRequested.asStateFlow()

    /** Set to true by MirrorPlayStop when mirror is currently capturing; MainActivity sends
     * a STOP intent to ScreenCaptureService and resets. */
    private val _mirrorStopRequested = MutableStateFlow(false)
    val mirrorStopRequested: StateFlow<Boolean> = _mirrorStopRequested.asStateFlow()

    fun requestMirrorStart() {
        AppLog.i(TAG, "requestMirrorStart")
        _mirrorStartRequested.value = true
    }

    fun requestMirrorStop() {
        AppLog.i(TAG, "requestMirrorStop")
        _mirrorStopRequested.value = true
    }

    fun consumeMirrorStartRequest() {
        AppLog.d(TAG, "consumeMirrorStartRequest")
        _mirrorStartRequested.value = false
    }

    fun consumeMirrorStopRequest() {
        AppLog.d(TAG, "consumeMirrorStopRequest")
        _mirrorStopRequested.value = false
    }

    fun setActivityResumed(resumed: Boolean) {
        AppLog.d(TAG, "setActivityResumed($resumed)")
        _isActivityResumed.value = resumed
    }
    fun setOnValidScreen(valid: Boolean) {
        AppLog.i(TAG, "setOnValidScreen($valid)")
        _isOnValidScreen.value = valid
    }
    fun setUserDeclinedCapture(declined: Boolean) {
        AppLog.d(TAG, "setUserDeclinedCapture($declined)")
        _userDeclinedCapture.value = declined
    }
    fun setPromptInFlight(inFlight: Boolean) {
        AppLog.d(TAG, "setPromptInFlight($inFlight)")
        _promptInFlight.value = inFlight
    }

    // ── Touch / gesture state ─────────────────────────────────────────────────

    /** True while any finger is pressing the screen. Used by SwipeGestureProcessor. */
    private val _isTouching = MutableStateFlow(false)
    val isTouching: StateFlow<Boolean> = _isTouching.asStateFlow()

    fun setTouching(touching: Boolean) { _isTouching.value = touching }

    // ── SAF file picker ───────────────────────────────────────────────────────

    // Whether the system file picker (SAF) is currently open. While true,
    // MirrorPresentation hides itself so DocumentsUI is visible to the user on the
    // secondary display. Without this, the Presentation (TYPE_PRIVATE_PRESENTATION)
    // sits above the file-picker Activity and the user cannot see or interact with it.
    private val _isFilePickerOpen = MutableStateFlow(false)
    val isFilePickerOpen: StateFlow<Boolean> = _isFilePickerOpen.asStateFlow()

    fun setFilePickerOpen(open: Boolean) {
        AppLog.d(TAG, "setFilePickerOpen($open)")
        _isFilePickerOpen.value = open
    }

    // ── Pill Menu ─────────────────────────────────────────────────────────────

    private val _isPillMenuOpen = MutableStateFlow(false)
    val isPillMenuOpen: StateFlow<Boolean> = _isPillMenuOpen.asStateFlow()

    fun openPillMenu() {
        AppLog.i(TAG, "openPillMenu")
        _isPillMenuOpen.value = true
    }

    fun closePillMenu() {
        AppLog.i(TAG, "closePillMenu")
        _isPillMenuOpen.value = false
    }

    // ── Modal overlay states ──────────────────────────────────────────────────

    private val _isFullscreenKeyboardActive = MutableStateFlow(false)
    val isFullscreenKeyboardActive: StateFlow<Boolean> = _isFullscreenKeyboardActive.asStateFlow()

    private val _isFullscreenMouseActive = MutableStateFlow(false)
    val isFullscreenMouseActive: StateFlow<Boolean> = _isFullscreenMouseActive.asStateFlow()

    private val _isViewportEditActive = MutableStateFlow(false)
    val isViewportEditActive: StateFlow<Boolean> = _isViewportEditActive.asStateFlow()

    private val _isAmbientSettingsActive = MutableStateFlow(false)
    val isAmbientSettingsActive: StateFlow<Boolean> = _isAmbientSettingsActive.asStateFlow()

    private val _fullscreenMouseSensitivity = MutableStateFlow(1.0f)
    val fullscreenMouseSensitivity: StateFlow<Float> = _fullscreenMouseSensitivity.asStateFlow()

    private val _fullscreenKeyboardLayout = MutableStateFlow(KbLayout.QWERTZ)
    val fullscreenKeyboardLayout: StateFlow<KbLayout> = _fullscreenKeyboardLayout.asStateFlow()

    /** Whether the MacroPad layout editor is currently open. */
    private val _isEditorActive = MutableStateFlow(false)
    val isEditorActive: StateFlow<Boolean> = _isEditorActive.asStateFlow()

    /**
     * True whenever any fullscreen modal overlay is showing.
     * Drives "× close" label on [IdlePill][com.stormpanda.megingiard.ui.IdlePill].
     */
    val isAnyModalActive: StateFlow<Boolean> = combine(
        _isFullscreenKeyboardActive,
        _isFullscreenMouseActive,
        _isViewportEditActive,
        _isAmbientSettingsActive,
        MacroPadState.isPeekActive,
    ) { kb, ms, vp, amb, peek -> kb || ms || vp || amb || peek }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun setFullscreenKeyboardActive(active: Boolean, layout: KbLayout = KbLayout.QWERTZ) {
        AppLog.i(TAG, "setFullscreenKeyboardActive($active, layout=$layout)")
        if (active) {
            _fullscreenKeyboardLayout.value = layout
            _isFullscreenMouseActive.value = false
            _isViewportEditActive.value = false
            _isAmbientSettingsActive.value = false
        }
        _isFullscreenKeyboardActive.value = active
    }

    fun setFullscreenMouseActive(active: Boolean, sensitivity: Float = 1.0f) {
        AppLog.i(TAG, "setFullscreenMouseActive($active, sensitivity=$sensitivity)")
        if (active) {
            _fullscreenMouseSensitivity.value = sensitivity
            _isFullscreenKeyboardActive.value = false
            _isViewportEditActive.value = false
            _isAmbientSettingsActive.value = false
        }
        _isFullscreenMouseActive.value = active
    }

    fun setViewportEditActive(active: Boolean) {
        AppLog.i(TAG, "setViewportEditActive($active)")
        if (active) {
            _isFullscreenKeyboardActive.value = false
            _isFullscreenMouseActive.value = false
            _isAmbientSettingsActive.value = false
        }
        _isViewportEditActive.value = active
    }

    fun setAmbientSettingsActive(active: Boolean) {
        AppLog.i(TAG, "setAmbientSettingsActive($active)")
        if (active) {
            _isFullscreenKeyboardActive.value = false
            _isFullscreenMouseActive.value = false
            _isViewportEditActive.value = false
        }
        _isAmbientSettingsActive.value = active
    }

    fun setEditorActive(active: Boolean) {
        AppLog.i(TAG, "setEditorActive($active)")
        _isEditorActive.value = active
    }

    /** Closes whichever fullscreen modal overlay is currently active. */
    fun closeActiveModal() {
        AppLog.i(TAG, "closeActiveModal: kb=${_isFullscreenKeyboardActive.value} ms=${_isFullscreenMouseActive.value} vp=${_isViewportEditActive.value} amb=${_isAmbientSettingsActive.value} peek=${MacroPadState.isPeekActive.value}")
        _isFullscreenKeyboardActive.value = false
        _isFullscreenMouseActive.value = false
        _isViewportEditActive.value = false
        _isAmbientSettingsActive.value = false
        MacroPadState.resetPeek()
    }

    /**
     * Called by [SwipeGestureProcessor] on edge-swipe detection.
     * Dispatches to the correct action based on current navigation state.
     */
    fun handleEdgeSwipe() {
        AppLog.d(TAG, "handleEdgeSwipe: modal=${isAnyModalActive.value} pillMenu=${_isPillMenuOpen.value}")
        when {
            isAnyModalActive.value  -> closeActiveModal()
            _isPillMenuOpen.value   -> closePillMenu()
            else                    -> openPillMenu()
        }
    }

}
