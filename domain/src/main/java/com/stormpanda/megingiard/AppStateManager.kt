package com.stormpanda.megingiard

import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.KeyboardSettings
import com.stormpanda.megingiard.settings.MacroPadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AppStateManager"

object AppStateManager {
    // App-lifetime scope: intentionally never cancelled — this singleton lives for the
    // duration of the process. Cancellation is handled by process termination.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private val _isActivityResumed = MutableStateFlow(true)
    val isActivityResumed: StateFlow<Boolean> = _isActivityResumed.asStateFlow()

    /**
     * True from the moment [onUserLeaveHint][android.app.Activity.onUserLeaveHint] fires
     * (Home button, Recents navigation) until the next [ON_RESUME][androidx.lifecycle.Lifecycle.Event.ON_RESUME].
     *
     * Unlike [isActivityResumed], this flag is NOT set when a [android.app.Presentation]
     * or other window owned by the same process covers the Activity — only genuine
     * user-initiated navigation away sets it. This makes it safe to use for
     * hiding/showing mirror presentations without creating a feedback loop.
     */
    private val _isUserLeaving = MutableStateFlow(false)
    val isUserLeaving: StateFlow<Boolean> = _isUserLeaving.asStateFlow()

    private val _isOnValidScreen = MutableStateFlow(true)
    val isOnValidScreen: StateFlow<Boolean> = _isOnValidScreen.asStateFlow()

    private val _promptInFlight = MutableStateFlow(false)
    val promptInFlight: StateFlow<Boolean> = _promptInFlight.asStateFlow()

    private val _mirrorAutoStartSuppressedLayoutId = MutableStateFlow<String?>(null)
    val mirrorAutoStartSuppressedLayoutId: StateFlow<String?> = _mirrorAutoStartSuppressedLayoutId.asStateFlow()

    // ── Mirror control signals ────────────────────────────────────────────────
    // One-shot fire-and-forget flags: MainActivity resets them after handling.

    /** Set to true by MirrorPlayStop when mirror is not yet capturing; MainActivity launches
     * CaptureRequestActivity and resets. */
    private val _mirrorStartRequested = MutableStateFlow(false)
    val mirrorStartRequested: StateFlow<Boolean> = _mirrorStartRequested.asStateFlow()

    /** Set to true by MirrorPlayStop when mirror is currently capturing; MainActivity sends
     * a STOP intent to ScreenCaptureService and resets. */
    private val _mirrorStopRequested = MutableStateFlow(false)
    val mirrorStopRequested: StateFlow<Boolean> = _mirrorStopRequested.asStateFlow()

    /** Set to true when user requests app shut off; MainActivity handles graceful shutdown and resets. */
    private val _shutOffRequested = MutableStateFlow(false)
    val shutOffRequested: StateFlow<Boolean> = _shutOffRequested.asStateFlow()

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

    fun requestShutOff() {
        AppLog.i(TAG, "requestShutOff")
        _shutOffRequested.value = true
        resetPrivdPromptState()
    }

    fun consumeShutOffRequest() {
        AppLog.d(TAG, "consumeShutOffRequest")
        _shutOffRequested.value = false
    }

    // ── Integration Client State ──────────────────────────────────────────────

    private val _isExternalClientActive = MutableStateFlow(false)
    val isExternalClientActive: StateFlow<Boolean> = _isExternalClientActive.asStateFlow()

    private val _externalClientPackage = MutableStateFlow<String?>(null)
    val externalClientPackage: StateFlow<String?> = _externalClientPackage.asStateFlow()

    private val _focusedAppPackageName = MutableStateFlow<String?>(null)
    val focusedAppPackageName: StateFlow<String?> = _focusedAppPackageName.asStateFlow()

    private val _hoveredAppPackageName = MutableStateFlow<String?>(null)
    val hoveredAppPackageName: StateFlow<String?> = _hoveredAppPackageName.asStateFlow()

    private val _hoveredAppLabel = MutableStateFlow<String?>(null)
    val hoveredAppLabel: StateFlow<String?> = _hoveredAppLabel.asStateFlow()

    fun setExternalClientState(
        isActive: Boolean,
        packageName: String?,
        focusedApp: String?,
        hoveredPackage: String? = null,
        hoveredLabel: String? = null,
    ) {
        AppLog.d(
            TAG,
            "setExternalClientState: active=$isActive package=$packageName focused=$focusedApp hovered=$hoveredLabel ($hoveredPackage)",
        )
        _isExternalClientActive.value = isActive
        _externalClientPackage.value = packageName
        _focusedAppPackageName.value = focusedApp
        _hoveredAppPackageName.value = hoveredPackage
        _hoveredAppLabel.value = hoveredLabel
    }

    fun setActivityResumed(resumed: Boolean) {
        AppLog.d(TAG, "setActivityResumed($resumed)")
        _isActivityResumed.value = resumed
    }

    fun setUserLeaving(leaving: Boolean) {
        AppLog.d(TAG, "setUserLeaving($leaving)")
        _isUserLeaving.value = leaving
    }

    fun setOnValidScreen(valid: Boolean) {
        AppLog.i(TAG, "setOnValidScreen($valid)")
        _isOnValidScreen.value = valid
    }

    fun setPromptInFlight(inFlight: Boolean) {
        AppLog.d(TAG, "setPromptInFlight($inFlight)")
        _promptInFlight.value = inFlight
    }

    fun suppressMirrorAutoStart(layoutId: String) {
        AppLog.d(TAG, "suppressMirrorAutoStart($layoutId)")
        _mirrorAutoStartSuppressedLayoutId.value = layoutId
    }

    fun clearMirrorAutoStartSuppression(layoutId: String) {
        if (_mirrorAutoStartSuppressedLayoutId.value == layoutId) {
            AppLog.d(TAG, "clearMirrorAutoStartSuppression($layoutId)")
            _mirrorAutoStartSuppressedLayoutId.value = null
        }
    }

    // ── Touch / gesture state ─────────────────────────────────────────────────

    /** True while any finger is pressing the screen. Used by SwipeGestureProcessor. */
    private val _isTouching = MutableStateFlow(false)
    val isTouching: StateFlow<Boolean> = _isTouching.asStateFlow()

    fun setTouching(touching: Boolean) {
        _isTouching.value = touching
    }

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

    // ── Quick Menu ────────────────────────────────────────────────────────────

    private val _isQuickMenuOpen = MutableStateFlow(false)
    val isQuickMenuOpen: StateFlow<Boolean> = _isQuickMenuOpen.asStateFlow()

    fun openQuickMenu() {
        if (OnboardingWizardManager.isWizardActive.value) {
            AppLog.w(TAG, "openQuickMenu suppressed while onboarding wizard is active")
            return
        }
        AppLog.i(TAG, "openQuickMenu")
        _isQuickMenuOpen.value = true
    }

    fun closeQuickMenu() {
        AppLog.i(TAG, "closeQuickMenu")
        _isQuickMenuOpen.value = false
    }

    private val _activeSwipe = MutableStateFlow<SwipeGestureProgress?>(null)
    val activeSwipe: StateFlow<SwipeGestureProgress?> = _activeSwipe.asStateFlow()

    fun updateActiveSwipe(progress: SwipeGestureProgress?) {
        if (progress == null && _activeSwipe.value != null) {
            AppLog.d(TAG, "swipe gesture completed or cancelled")
        } else if (progress != null && _activeSwipe.value == null) {
            AppLog.d(TAG, "swipe gesture started: type=${progress.type}")
        }
        _activeSwipe.value = progress
    }

    // ── Modal overlay states ──────────────────────────────────────────────────

    private val _isFullscreenKeyboardActive = MutableStateFlow(false)
    val isFullscreenKeyboardActive: StateFlow<Boolean> = _isFullscreenKeyboardActive.asStateFlow()

    private val _isFullscreenMouseActive = MutableStateFlow(false)
    val isFullscreenMouseActive: StateFlow<Boolean> = _isFullscreenMouseActive.asStateFlow()

    private val _hasAdbCredentials = MutableStateFlow(false)
    val hasAdbCredentials: StateFlow<Boolean> = _hasAdbCredentials.asStateFlow()

    fun setHasAdbCredentials(has: Boolean) {
        AppLog.d(TAG, "setHasAdbCredentials($has)")
        _hasAdbCredentials.value = has
    }

    private val _wasMirroringStartedByTouchpad = MutableStateFlow(false)
    val wasMirroringStartedByTouchpad: StateFlow<Boolean> = _wasMirroringStartedByTouchpad.asStateFlow()

    fun setWasMirroringStartedByTouchpad(started: Boolean) {
        _wasMirroringStartedByTouchpad.value = started
    }

    private val _isBackgroundSettingsActive = MutableStateFlow(false)
    val isBackgroundSettingsActive: StateFlow<Boolean> = _isBackgroundSettingsActive.asStateFlow()

    val isPrivdPromptDismissed: StateFlow<Boolean> = MacroPadSettings.privdPromptDismissed

    fun setPrivdPromptDismissed(dismissed: Boolean) {
        AppLog.d(TAG, "setPrivdPromptDismissed($dismissed)")
        MacroPadSettings.setPrivdPromptDismissed(dismissed)
    }

    private val _isAccessibilityActive = MutableStateFlow(true)
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    fun setAccessibilityActive(active: Boolean) {
        AppLog.d(TAG, "setAccessibilityActive($active)")
        _isAccessibilityActive.value = active
    }

    fun resetPrivdPromptState() {
        AppLog.d(TAG, "resetPrivdPromptState")
        _isPrivdPromptShowing.value = false
    }

    private val _isPrivdPromptShowing = MutableStateFlow(false)
    val isPrivdPromptActive: StateFlow<Boolean> = _isPrivdPromptShowing.asStateFlow()

    private val _isViewportEditActive = MutableStateFlow(false)
    val isViewportEditActive: StateFlow<Boolean> = _isViewportEditActive.asStateFlow()

    private val _activeCropCutoutId = MutableStateFlow<String?>(null)
    val activeCropCutoutId: StateFlow<String?> = _activeCropCutoutId.asStateFlow()

    private val _selectedCutoutId = MutableStateFlow<String?>(null)
    val selectedCutoutId: StateFlow<String?> = _selectedCutoutId.asStateFlow()

    fun setActiveCropCutoutId(id: String?) {
        AppLog.d(TAG, "setActiveCropCutoutId($id)")
        _activeCropCutoutId.value = id
    }

    fun setSelectedCutoutId(id: String?) {
        AppLog.d(TAG, "setSelectedCutoutId($id)")
        _selectedCutoutId.value = id
    }

    private val _isGlobalSettingsOpen = MutableStateFlow(false)
    val isGlobalSettingsOpen: StateFlow<Boolean> = _isGlobalSettingsOpen.asStateFlow()

    fun setGlobalSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setGlobalSettingsOpen($open)")
        _isGlobalSettingsOpen.value = open
    }

    private val _isKeyboardSettingsOpen = MutableStateFlow(false)
    val isKeyboardSettingsOpen: StateFlow<Boolean> = _isKeyboardSettingsOpen.asStateFlow()

    fun setKeyboardSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setKeyboardSettingsOpen($open)")
        _isKeyboardSettingsOpen.value = open
    }

    private val _isTouchpadSettingsOpen = MutableStateFlow(false)
    val isTouchpadSettingsOpen: StateFlow<Boolean> = _isTouchpadSettingsOpen.asStateFlow()

    fun setTouchpadSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setTouchpadSettingsOpen($open)")
        _isTouchpadSettingsOpen.value = open
    }

    private var wasViewportEditActiveBeforeSettings = false

    private val _fullscreenMouseSensitivity = MutableStateFlow(1.0f)
    val fullscreenMouseSensitivity: StateFlow<Float> = _fullscreenMouseSensitivity.asStateFlow()

    private val _forcedKeyboardLayout = MutableStateFlow<KbLayout?>(null)
    val fullscreenKeyboardLayout: StateFlow<KbLayout> =
        combine(_forcedKeyboardLayout, KeyboardSettings.kbLayout) { forced, settings ->
            forced ?: settings
        }.stateIn(scope, SharingStarted.Eagerly, KeyboardSettings.kbLayout.value)

    /** Whether the MacroPad layout editor is currently open. */
    private val _isEditorActive = MutableStateFlow(false)
    val isEditorActive: StateFlow<Boolean> = _isEditorActive.asStateFlow()

    /**
     * The app-wide active UI mode representing the screens, settings panels, or overlays currently active.
     */
    private val uiMode: StateFlow<UiMode> =
        combine(
            _isGlobalSettingsOpen,
            _isKeyboardSettingsOpen,
            _isTouchpadSettingsOpen,
            _isBackgroundSettingsActive,
            _isEditorActive,
            _isQuickMenuOpen,
            _isViewportEditActive,
            _isFullscreenKeyboardActive,
            _isFullscreenMouseActive,
        ) { array: Array<Boolean> ->
            val globalSettings = array[0]
            val keyboardSettings = array[1]
            val touchpadSettings = array[2]
            val backgroundSettings = array[3]
            val editor = array[4]
            val quickMenu = array[5]
            val viewportEdit = array[6]
            val fullscreenKeyboard = array[7]
            val fullscreenMouse = array[8]

            when {
                globalSettings -> UiMode.GLOBAL_SETTINGS
                keyboardSettings -> UiMode.KEYBOARD_SETTINGS
                touchpadSettings -> UiMode.TOUCHPAD_SETTINGS
                backgroundSettings -> UiMode.BACKGROUND_SETTINGS
                editor -> UiMode.LAYOUT_EDITOR
                quickMenu -> UiMode.QUICK_MENU
                viewportEdit -> UiMode.VIEWPORT_EDIT
                fullscreenKeyboard -> UiMode.FULLSCREEN_KEYBOARD
                fullscreenMouse -> UiMode.FULLSCREEN_MOUSE
                else -> UiMode.MACROPAD_USE
            }
        }.stateIn(scope, SharingStarted.Eagerly, UiMode.MACROPAD_USE)

    /**
     * True whenever any fullscreen modal overlay is showing.
     * Used by [handleEdgeSwipe] to determine if an edge swipe should close the active modal.
     */
    val isAnyModalActive: StateFlow<Boolean> =
        combine(uiMode, MacroPadState.isPeekActive) { mode, peek ->
            peek || mode == UiMode.GLOBAL_SETTINGS || mode == UiMode.KEYBOARD_SETTINGS ||
                mode == UiMode.TOUCHPAD_SETTINGS || mode == UiMode.BACKGROUND_SETTINGS ||
                mode == UiMode.FULLSCREEN_KEYBOARD || mode == UiMode.FULLSCREEN_MOUSE ||
                mode == UiMode.VIEWPORT_EDIT
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * True whenever any settings menu, Quick Menu, or editor modal is active/open.
     * Used by swipe gesture processors to disable edge gesture handling when menus are open.
     */
    val isAnyMenuOpen: StateFlow<Boolean> =
        uiMode
            .map { mode ->
                mode == UiMode.GLOBAL_SETTINGS || mode == UiMode.KEYBOARD_SETTINGS ||
                    mode == UiMode.TOUCHPAD_SETTINGS || mode == UiMode.BACKGROUND_SETTINGS ||
                    mode == UiMode.LAYOUT_EDITOR || mode == UiMode.QUICK_MENU
            }.stateIn(scope, SharingStarted.Eagerly, false)

    fun setFullscreenKeyboardActive(
        active: Boolean,
        layout: KbLayout? = null,
    ) {
        if (active && OnboardingWizardManager.isWizardActive.value) {
            AppLog.w(TAG, "setFullscreenKeyboardActive suppressed while onboarding wizard is active")
            return
        }
        AppLog.i(TAG, "setFullscreenKeyboardActive($active, layout=$layout)")
        if (active) {
            _forcedKeyboardLayout.value = layout
            _isFullscreenMouseActive.value = false
            _isViewportEditActive.value = false
            _isBackgroundSettingsActive.value = false
        } else {
            _forcedKeyboardLayout.value = null
        }
        _isFullscreenKeyboardActive.value = active
    }

    fun setFullscreenMouseActive(
        active: Boolean,
        sensitivity: Float = 1.0f,
    ) {
        if (active && OnboardingWizardManager.isWizardActive.value) {
            AppLog.w(TAG, "setFullscreenMouseActive suppressed while onboarding wizard is active")
            return
        }
        AppLog.i(TAG, "setFullscreenMouseActive($active, sensitivity=$sensitivity)")
        if (active) {
            _fullscreenMouseSensitivity.value = sensitivity
            _isFullscreenKeyboardActive.value = false
            _isViewportEditActive.value = false
            _isBackgroundSettingsActive.value = false
        }
        _isFullscreenMouseActive.value = active
    }

    fun setViewportEditActive(active: Boolean) {
        AppLog.i(TAG, "setViewportEditActive($active)")
        if (active) {
            _isFullscreenKeyboardActive.value = false
            _isFullscreenMouseActive.value = false
            _isBackgroundSettingsActive.value = false
            ScreenCaptureManager.setFollowActive(false, persist = true)
        }
        _isViewportEditActive.value = active
    }

    fun setBackgroundSettingsActive(active: Boolean) {
        AppLog.i(TAG, "setBackgroundSettingsActive($active)")
        if (active) {
            wasViewportEditActiveBeforeSettings = _isViewportEditActive.value
            _isFullscreenKeyboardActive.value = false
            _isFullscreenMouseActive.value = false
            _isViewportEditActive.value = false
            setPrivdPromptDismissed(true)
        } else {
            _isViewportEditActive.value = wasViewportEditActiveBeforeSettings
        }
        _isBackgroundSettingsActive.value = active
    }

    fun setEditorActive(active: Boolean) {
        AppLog.i(TAG, "setEditorActive($active)")
        _isEditorActive.value = active
    }

    /** Closes whichever fullscreen modal overlay is currently active. */
    fun closeActiveModal() {
        AppLog.i(
            TAG,
            "closeActiveModal: kb=${_isFullscreenKeyboardActive.value} ms=${_isFullscreenMouseActive.value} vp=${_isViewportEditActive.value} amb=${_isBackgroundSettingsActive.value} peek=${MacroPadState.isPeekActive.value}",
        )
        _isFullscreenKeyboardActive.value = false
        _isFullscreenMouseActive.value = false
        _isViewportEditActive.value = false
        _isBackgroundSettingsActive.value = false
        _isGlobalSettingsOpen.value = false
        _isKeyboardSettingsOpen.value = false
        _isTouchpadSettingsOpen.value = false
        _activeCropCutoutId.value = null
        _selectedCutoutId.value = null
        wasViewportEditActiveBeforeSettings = false
        MacroPadState.resetPeek()
    }

    /**
     * Called by [SwipeGestureProcessor] on edge-swipe detection.
     * Dispatches to the correct action based on current navigation state.
     */
    fun handleEdgeSwipe() {
        if (OnboardingWizardManager.isWizardActive.value) {
            AppLog.w(TAG, "handleEdgeSwipe suppressed while onboarding wizard is active")
            return
        }
        AppLog.d(TAG, "handleEdgeSwipe: modal=${isAnyModalActive.value} quickMenu=${_isQuickMenuOpen.value}")
        when {
            isAnyModalActive.value -> closeActiveModal()
            _isQuickMenuOpen.value -> closeQuickMenu()
            else -> openQuickMenu()
        }
    }

    init {
        scope.launch {
            var lastActiveLayoutId: String? = null
            MacroPadState.activeLayout.collect { layout ->
                val newId = layout?.id
                if (lastActiveLayoutId != null && newId != lastActiveLayoutId) {
                    AppLog.d(TAG, "activeLayout changed from $lastActiveLayoutId to $newId; closing active modals")
                    closeActiveModal()
                }
                lastActiveLayoutId = newId
            }
        }
        scope.launch {
            combine(
                PrivdManager.state,
                MacroPadSettings.privdShowAdbPrompt,
                _hasAdbCredentials,
                MacroPadSettings.privdPromptDismissed,
                _isBackgroundSettingsActive,
                _isAccessibilityActive,
            ) { array ->
                val state = array[0] as PrivdState
                val showPromptPref = array[1] as Boolean
                val hasCreds = array[2] as Boolean
                val dismissed = array[3] as Boolean
                val bgSettingsActive = array[4] as Boolean
                val accessibilityActive = array[5] as Boolean

                if (state == PrivdState.RUNNING && accessibilityActive && dismissed) {
                    MacroPadSettings.setPrivdPromptDismissed(false)
                }
                if (!accessibilityActive) {
                    MacroPadSettings.setPrivdPromptDismissed(false)
                    _isPrivdPromptShowing.value = true
                } else if (dismissed || bgSettingsActive) {
                    _isPrivdPromptShowing.value = false
                } else if (state == PrivdState.FAILED && showPromptPref && hasCreds) {
                    _isPrivdPromptShowing.value = true
                }
            }.collect {}
        }
    }
}
