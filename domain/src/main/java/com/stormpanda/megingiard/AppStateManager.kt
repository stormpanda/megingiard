package com.stormpanda.megingiard

import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
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

enum class CompanionViewMode {
    AUTO,
    MACROPAD,
    DASHBOARD,
}

fun CompanionViewMode.shouldShowIntegrationHome(
    focusedAppPackageName: String?,
    focusedRomPath: String?,
    activeProfile: PadProfile?,
): Boolean =
    when (this) {
        CompanionViewMode.MACROPAD -> {
            false
        }

        CompanionViewMode.DASHBOARD -> {
            true
        }

        CompanionViewMode.AUTO -> {
            val foreground = AutoSwitchCoordinator.foregroundApp.value
            val isForegroundLauncher =
                foreground != null &&
                    (
                        foreground.startsWith(MegingiardIpcContract.GAMEFOCUS_PACKAGE) ||
                            foreground.contains("launcher") ||
                            foreground.contains("home") ||
                            foreground == "com.android.systemui"
                    )

            if (focusedAppPackageName == null || isForegroundLauncher) {
                true
            } else {
                activeProfile?.matches(focusedAppPackageName, focusedRomPath, isActiveProfile = true) != true
            }
        }
    }

object AppStateManager {
    // App-lifetime scope: intentionally never cancelled — this singleton lives for the
    // duration of the process. Cancellation is handled by process termination.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private val _isActivityResumed = MutableStateFlow(true)
    val isActivityResumed: StateFlow<Boolean> = _isActivityResumed.asStateFlow()

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

    private val _focusedRomPath = MutableStateFlow<String?>(null)
    val focusedRomPath: StateFlow<String?> = _focusedRomPath.asStateFlow()

    private val _hoveredAppPackageName = MutableStateFlow<String?>(null)
    val hoveredAppPackageName: StateFlow<String?> = _hoveredAppPackageName.asStateFlow()

    private val _hoveredAppLabel = MutableStateFlow<String?>(null)
    val hoveredAppLabel: StateFlow<String?> = _hoveredAppLabel.asStateFlow()

    private val _hoveredRomPath = MutableStateFlow<String?>(null)
    val hoveredRomPath: StateFlow<String?> = _hoveredRomPath.asStateFlow()

    private val _hoveredSystemId = MutableStateFlow<String?>(null)
    val hoveredSystemId: StateFlow<String?> = _hoveredSystemId.asStateFlow()

    private val _hoveredAppPrimaryColor = MutableStateFlow<Int?>(null)
    val hoveredAppPrimaryColor: StateFlow<Int?> = _hoveredAppPrimaryColor.asStateFlow()

    private val _hoveredAppSecondaryColor = MutableStateFlow<Int?>(null)
    val hoveredAppSecondaryColor: StateFlow<Int?> = _hoveredAppSecondaryColor.asStateFlow()

    fun setExternalClientState(
        isActive: Boolean,
        packageName: String?,
        focusedApp: String?,
        focusedRomPath: String? = null,
        hoveredPackage: String? = null,
        hoveredLabel: String? = null,
        hoveredRomPath: String? = null,
        hoveredSystemId: String? = null,
        hoveredPrimaryColor: Int? = null,
        hoveredSecondaryColor: Int? = null,
    ) {
        AppLog.d(
            TAG,
            "setExternalClientState: active=$isActive package=$packageName focused=$focusedApp focusedRom=$focusedRomPath hovered=$hoveredLabel ($hoveredPackage) romPath=$hoveredRomPath systemId=$hoveredSystemId primary=$hoveredPrimaryColor secondary=$hoveredSecondaryColor",
        )
        if (_isExternalClientActive.value != isActive ||
            _focusedAppPackageName.value != focusedApp ||
            _focusedRomPath.value != focusedRomPath
        ) {
            AppLog.d(TAG, "setExternalClientState focus updated: active=$isActive focusedApp=$focusedApp focusedRom=$focusedRomPath")
        }
        _isExternalClientActive.value = isActive
        _externalClientPackage.value = packageName
        _focusedAppPackageName.value = focusedApp
        _focusedRomPath.value = focusedRomPath
        _hoveredAppPackageName.value = hoveredPackage
        _hoveredAppLabel.value = hoveredLabel
        _hoveredRomPath.value = hoveredRomPath
        _hoveredSystemId.value = hoveredSystemId
        _hoveredAppPrimaryColor.value = hoveredPrimaryColor
        _hoveredAppSecondaryColor.value = hoveredSecondaryColor
    }

    fun setStandaloneForegroundState(
        focusedApp: String?,
        focusedRomPath: String? = null,
    ) {
        if (_isExternalClientActive.value) return
        if (_focusedAppPackageName.value != focusedApp || _focusedRomPath.value != focusedRomPath) {
            AppLog.d(TAG, "setStandaloneForegroundState: focusedApp=$focusedApp focusedRom=$focusedRomPath")
            _focusedAppPackageName.value = focusedApp
            _focusedRomPath.value = focusedRomPath
        }
    }

    fun setActivityResumed(resumed: Boolean) {
        AppLog.d(TAG, "setActivityResumed($resumed)")
        _isActivityResumed.value = resumed
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

    // ── Single Source of Truth for Active UI Overlay / Screen Mode ────────────

    private val _uiMode = MutableStateFlow(UiMode.MACROPAD_USE)
    val uiMode: StateFlow<UiMode> = _uiMode.asStateFlow()

    val isGlobalSettingsOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.GLOBAL_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isKeyboardSettingsOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.KEYBOARD_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isTouchpadSettingsOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.TOUCHPAD_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isBackgroundSettingsActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.BACKGROUND_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isEditorActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.LAYOUT_EDITOR }.stateIn(scope, SharingStarted.Eagerly, false)

    val isQuickMenuOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.QUICK_MENU }.stateIn(scope, SharingStarted.Eagerly, false)

    val isViewportEditActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.VIEWPORT_EDIT }.stateIn(scope, SharingStarted.Eagerly, false)

    val isFullscreenKeyboardActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.FULLSCREEN_KEYBOARD }.stateIn(scope, SharingStarted.Eagerly, false)

    val isFullscreenMouseActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.FULLSCREEN_MOUSE }.stateIn(scope, SharingStarted.Eagerly, false)

    fun openQuickMenu() {
        if (OnboardingWizardManager.isWizardActive.value) {
            AppLog.w(TAG, "openQuickMenu suppressed while onboarding wizard is active")
            return
        }
        AppLog.i(TAG, "openQuickMenu")
        _uiMode.value = UiMode.QUICK_MENU
    }

    fun closeQuickMenu() {
        AppLog.i(TAG, "closeQuickMenu")
        if (_uiMode.value == UiMode.QUICK_MENU) {
            _uiMode.value = UiMode.MACROPAD_USE
        }
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

    private val _companionViewMode = MutableStateFlow(CompanionViewMode.AUTO)
    val companionViewMode: StateFlow<CompanionViewMode> = _companionViewMode.asStateFlow()

    val showIntegrationHome: StateFlow<Boolean> =
        combine(
            _focusedAppPackageName,
            _focusedRomPath,
            MacroPadState.activeProfile,
            _companionViewMode,
        ) { focusedPackage, focusedRom, profile, viewMode ->
            viewMode.shouldShowIntegrationHome(focusedPackage, focusedRom, profile)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    fun setCompanionViewMode(mode: CompanionViewMode) {
        AppLog.d(TAG, "setCompanionViewMode($mode)")
        _companionViewMode.value = mode
        if (mode == CompanionViewMode.AUTO) {
            AutoSwitchCoordinator.reevaluateAutoState()
        }
    }

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

    fun setGlobalSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setGlobalSettingsOpen($open)")
        _uiMode.value = if (open) UiMode.GLOBAL_SETTINGS else UiMode.MACROPAD_USE
    }

    fun setKeyboardSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setKeyboardSettingsOpen($open)")
        _uiMode.value = if (open) UiMode.KEYBOARD_SETTINGS else UiMode.MACROPAD_USE
    }

    fun setTouchpadSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setTouchpadSettingsOpen($open)")
        _uiMode.value = if (open) UiMode.TOUCHPAD_SETTINGS else UiMode.MACROPAD_USE
    }

    private var wasViewportEditActiveBeforeSettings = false

    private val _fullscreenMouseSensitivity = MutableStateFlow(1.0f)
    val fullscreenMouseSensitivity: StateFlow<Float> = _fullscreenMouseSensitivity.asStateFlow()

    private val _forcedKeyboardLayout = MutableStateFlow<KbLayout?>(null)
    val fullscreenKeyboardLayout: StateFlow<KbLayout> =
        combine(_forcedKeyboardLayout, KeyboardSettings.kbLayout) { forced, settings ->
            forced ?: settings
        }.stateIn(scope, SharingStarted.Eagerly, KeyboardSettings.kbLayout.value)

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
        } else {
            _forcedKeyboardLayout.value = null
        }
        _uiMode.value = if (active) UiMode.FULLSCREEN_KEYBOARD else UiMode.MACROPAD_USE
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
        }
        _uiMode.value = if (active) UiMode.FULLSCREEN_MOUSE else UiMode.MACROPAD_USE
    }

    fun setViewportEditActive(active: Boolean) {
        AppLog.i(TAG, "setViewportEditActive($active)")
        if (active) {
            ScreenCaptureManager.setFollowActive(false, persist = true)
        }
        _uiMode.value = if (active) UiMode.VIEWPORT_EDIT else UiMode.MACROPAD_USE
    }

    fun setBackgroundSettingsActive(active: Boolean) {
        AppLog.i(TAG, "setBackgroundSettingsActive($active)")
        if (active) {
            wasViewportEditActiveBeforeSettings = (_uiMode.value == UiMode.VIEWPORT_EDIT)
            setPrivdPromptDismissed(true)
            _uiMode.value = UiMode.BACKGROUND_SETTINGS
        } else {
            _uiMode.value = if (wasViewportEditActiveBeforeSettings) UiMode.VIEWPORT_EDIT else UiMode.MACROPAD_USE
        }
    }

    fun setEditorActive(active: Boolean) {
        AppLog.i(TAG, "setEditorActive($active)")
        _uiMode.value = if (active) UiMode.LAYOUT_EDITOR else UiMode.MACROPAD_USE
    }

    /** Closes whichever fullscreen modal overlay is currently active. */
    fun closeActiveModal() {
        AppLog.i(
            TAG,
            "closeActiveModal: mode=${_uiMode.value} peek=${MacroPadState.isPeekActive.value}",
        )
        _uiMode.value = UiMode.MACROPAD_USE
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
        AppLog.d(TAG, "handleEdgeSwipe: modal=${isAnyModalActive.value} quickMenu=${isQuickMenuOpen.value}")
        when {
            isAnyModalActive.value -> closeActiveModal()
            isQuickMenuOpen.value -> closeQuickMenu()
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
                    if (_uiMode.value != UiMode.QUICK_MENU &&
                        _uiMode.value != UiMode.LAYOUT_EDITOR &&
                        _uiMode.value != UiMode.BACKGROUND_SETTINGS
                    ) {
                        closeActiveModal()
                    }
                }
                lastActiveLayoutId = newId
            }
        }
        scope.launch {
            combine(
                PrivdManager.state,
                _hasAdbCredentials,
                MacroPadSettings.privdPromptDismissed,
                isBackgroundSettingsActive,
                _isAccessibilityActive,
            ) { array ->
                val state = array[0] as PrivdState
                val hasCreds = array[1] as Boolean
                val dismissed = array[2] as Boolean
                val bgSettingsActive = array[3] as Boolean
                val accessibilityActive = array[4] as Boolean

                if (state == PrivdState.RUNNING && accessibilityActive && dismissed) {
                    MacroPadSettings.setPrivdPromptDismissed(false)
                }
                if (!accessibilityActive) {
                    MacroPadSettings.setPrivdPromptDismissed(false)
                    _isPrivdPromptShowing.value = true
                } else if (dismissed || bgSettingsActive) {
                    _isPrivdPromptShowing.value = false
                } else if (state == PrivdState.FAILED && hasCreds) {
                    _isPrivdPromptShowing.value = true
                }
            }.collect {}
        }
    }
}
