package com.stormpanda.megingiard

import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.navigation.NavDestination
import com.stormpanda.megingiard.navigation.toPrimaryModalConfig
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.KeyboardSettings
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryModalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    data class AppLaunchRequest(
        val packageName: String,
        val touchX: Float = -1f,
        val touchY: Float = -1f,
    )

    private val _pendingAppLaunchRequest = MutableStateFlow<AppLaunchRequest?>(null)
    val pendingAppLaunchRequest: StateFlow<AppLaunchRequest?> = _pendingAppLaunchRequest.asStateFlow()

    private val _isFloatingBubbleActive = MutableStateFlow(false)
    val isFloatingBubbleActive: StateFlow<Boolean> = _isFloatingBubbleActive.asStateFlow()

    fun requestAppLaunch(
        packageName: String,
        touchX: Float = -1f,
        touchY: Float = -1f,
    ) {
        AppLog.i(TAG, "requestAppLaunch: pkg=$packageName touch=($touchX, $touchY)")
        _pendingAppLaunchRequest.value = AppLaunchRequest(packageName, touchX, touchY)
    }

    fun consumeAppLaunchRequest() {
        AppLog.d(TAG, "consumeAppLaunchRequest")
        _pendingAppLaunchRequest.value = null
    }

    fun setFloatingBubbleActive(active: Boolean) {
        AppLog.d(TAG, "setFloatingBubbleActive: $active")
        _isFloatingBubbleActive.value = active
    }

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

    private val _focusedRomIdentifier = MutableStateFlow<String?>(null)
    val focusedRomIdentifier: StateFlow<String?> = _focusedRomIdentifier.asStateFlow()

    private val _hoveredAppPackageName = MutableStateFlow<String?>(null)
    val hoveredAppPackageName: StateFlow<String?> = _hoveredAppPackageName.asStateFlow()

    private val _hoveredAppLabel = MutableStateFlow<String?>(null)
    val hoveredAppLabel: StateFlow<String?> = _hoveredAppLabel.asStateFlow()

    private val _hoveredRomPath = MutableStateFlow<String?>(null)
    val hoveredRomPath: StateFlow<String?> = _hoveredRomPath.asStateFlow()

    private val _hoveredRomIdentifier = MutableStateFlow<String?>(null)
    val hoveredRomIdentifier: StateFlow<String?> = _hoveredRomIdentifier.asStateFlow()

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
        focusedRomIdentifier: String? = null,
        hoveredPackage: String? = null,
        hoveredLabel: String? = null,
        hoveredRomPath: String? = null,
        hoveredRomIdentifier: String? = null,
        hoveredSystemId: String? = null,
        hoveredPrimaryColor: Int? = null,
        hoveredSecondaryColor: Int? = null,
    ) {
        AppLog.d(
            TAG,
            "setExternalClientState: active=$isActive package=$packageName focused=$focusedApp focusedRom=$focusedRomPath focusedId=$focusedRomIdentifier hovered=$hoveredLabel ($hoveredPackage) romPath=$hoveredRomPath romId=$hoveredRomIdentifier systemId=$hoveredSystemId primary=$hoveredPrimaryColor secondary=$hoveredSecondaryColor",
        )
        if (_isExternalClientActive.value != isActive ||
            _focusedAppPackageName.value != focusedApp ||
            _focusedRomPath.value != focusedRomPath ||
            _focusedRomIdentifier.value != focusedRomIdentifier
        ) {
            AppLog.d(
                TAG,
                "setExternalClientState focus updated: active=$isActive focusedApp=$focusedApp focusedRom=$focusedRomPath focusedId=$focusedRomIdentifier",
            )
        }
        _isExternalClientActive.value = isActive
        _externalClientPackage.value = packageName
        _focusedAppPackageName.value = focusedApp
        _focusedRomPath.value = focusedRomPath
        _focusedRomIdentifier.value = focusedRomIdentifier
        _hoveredAppPackageName.value = hoveredPackage
        _hoveredAppLabel.value = hoveredLabel
        _hoveredRomPath.value = hoveredRomPath
        _hoveredRomIdentifier.value = hoveredRomIdentifier
        _hoveredSystemId.value = hoveredSystemId
        _hoveredAppPrimaryColor.value = hoveredPrimaryColor
        _hoveredAppSecondaryColor.value = hoveredSecondaryColor
    }

    fun setStandaloneForegroundState(
        focusedApp: String?,
        focusedRomPath: String? = null,
        focusedRomIdentifier: String? = null,
    ) {
        if (_isExternalClientActive.value) return
        if (_focusedAppPackageName.value != focusedApp ||
            _focusedRomPath.value != focusedRomPath ||
            _focusedRomIdentifier.value != focusedRomIdentifier
        ) {
            AppLog.d(TAG, "setStandaloneForegroundState: focusedApp=$focusedApp focusedRom=$focusedRomPath focusedId=$focusedRomIdentifier")
        }
        _focusedAppPackageName.value = focusedApp
        _focusedRomPath.value = focusedRomPath
        _focusedRomIdentifier.value = focusedRomIdentifier
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

    // ── Quick Menu ────────────────────────────────────────────────────────────

    // ── Single Source of Truth for Active UI Overlay / Screen Mode ────────────

    private val _uiMode = MutableStateFlow(UiMode.MACROPAD_USE)
    val uiMode: StateFlow<UiMode> = _uiMode.asStateFlow()

    private val _activePrimaryModal = MutableStateFlow<PrimaryModalConfig?>(null)
    val activePrimaryModal: StateFlow<PrimaryModalConfig?> = _activePrimaryModal.asStateFlow()

    private val _currentNavDestination = MutableStateFlow<NavDestination?>(null)
    val currentNavDestination: StateFlow<NavDestination?> = _currentNavDestination.asStateFlow()

    private val _suspendedPrimaryModal = MutableStateFlow<PrimaryModalConfig?>(null)
    val suspendedPrimaryModal: StateFlow<PrimaryModalConfig?> = _suspendedPrimaryModal.asStateFlow()

    val hasSuspendedPrimaryModal: StateFlow<Boolean> =
        _suspendedPrimaryModal.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    val isGlobalSettingsOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.GLOBAL_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isKeyboardSettingsOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.KEYBOARD_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isTouchpadSettingsOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.TOUCHPAD_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isBackgroundSettingsActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.BACKGROUND_SETTINGS }.stateIn(scope, SharingStarted.Eagerly, false)

    val isEditorActive: StateFlow<Boolean> =
        combine(_uiMode, _activePrimaryModal) { mode, modal ->
            mode == UiMode.LAYOUT_EDITOR ||
                modal?.type == PrimaryModalType.MACROPAD_EDITOR ||
                modal?.type == PrimaryModalType.MACROPAD_INSPECTOR ||
                modal?.type == PrimaryModalType.LAYOUT_SETTINGS ||
                modal?.type == PrimaryModalType.PROFILE_SETTINGS ||
                modal?.type == PrimaryModalType.MACRO_TIMELINE_EDITOR
        }.stateIn(scope, SharingStarted.Eagerly, false)

    val isQuickMenuOpen: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.QUICK_MENU }.stateIn(scope, SharingStarted.Eagerly, false)

    val isViewportEditActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.VIEWPORT_EDIT }.stateIn(scope, SharingStarted.Eagerly, false)

    val isFullscreenKeyboardActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.FULLSCREEN_KEYBOARD }.stateIn(scope, SharingStarted.Eagerly, false)

    val isFullscreenMouseActive: StateFlow<Boolean> =
        _uiMode.map { it == UiMode.FULLSCREEN_MOUSE }.stateIn(scope, SharingStarted.Eagerly, false)

    fun openQuickMenu() {
        if (OnboardingWizardManager.isWizardActive.value || _isPrivdSetupWizardActive.value) {
            AppLog.w(TAG, "openQuickMenu suppressed while wizard is active")
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

    private val _autoSwitchOffToastEvent =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val autoSwitchOffToastEvent: SharedFlow<Unit> = _autoSwitchOffToastEvent.asSharedFlow()

    val showIntegrationHome: StateFlow<Boolean> =
        combine(
            _focusedAppPackageName,
            _focusedRomPath,
            MacroPadState.activeProfile,
            _companionViewMode,
        ) { focusedPackage, focusedRom, profile, viewMode ->
            viewMode.shouldShowIntegrationHome(focusedPackage, focusedRom, profile)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    fun setCompanionViewMode(
        mode: CompanionViewMode,
        isAutoSwitchButton: Boolean = false,
    ) {
        val previousMode = _companionViewMode.value
        AppLog.d(TAG, "setCompanionViewMode($mode, isAutoSwitchButton=$isAutoSwitchButton)")
        _companionViewMode.value = mode
        if (previousMode == CompanionViewMode.AUTO && mode != CompanionViewMode.AUTO && !isAutoSwitchButton) {
            AppLog.i(TAG, "Auto Switch turned off by non-button -> emitting toast signal")
            _autoSwitchOffToastEvent.tryEmit(Unit)
        }
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

    private val _isPrivdSetupWizardActive = MutableStateFlow(false)
    val isPrivdSetupWizardActive: StateFlow<Boolean> = _isPrivdSetupWizardActive.asStateFlow()

    fun setPrivdSetupWizardOpen(open: Boolean) {
        AppLog.d(TAG, "setPrivdSetupWizardOpen($open)")
        _isPrivdSetupWizardActive.value = open
    }

    private val _activeCropCutoutId = MutableStateFlow<String?>(null)
    val activeCropCutoutId: StateFlow<String?> = _activeCropCutoutId.asStateFlow()

    private val _selectedCutoutId = MutableStateFlow<String?>(null)
    val selectedCutoutId: StateFlow<String?> = _selectedCutoutId.asStateFlow()

    fun openPrimaryModal(config: PrimaryModalConfig) {
        AppLog.i(TAG, "openPrimaryModal: type=${config.type} payload=${config.payload}")
        _activePrimaryModal.value = config
        when (val payload = config.payload) {
            is PrimaryModalPayload.CropSelector -> {
                _activeCropCutoutId.value = payload.cutoutId
            }

            is PrimaryModalPayload.CutoutInspector -> {
                _selectedCutoutId.value = payload.cutoutId
            }

            else -> {}
        }
        when (config.type) {
            PrimaryModalType.GLOBAL_SETTINGS -> {
                _uiMode.value = UiMode.GLOBAL_SETTINGS
            }

            PrimaryModalType.KEYBOARD_SETTINGS -> {
                _uiMode.value = UiMode.KEYBOARD_SETTINGS
            }

            PrimaryModalType.TOUCHPAD_SETTINGS -> {
                _uiMode.value = UiMode.TOUCHPAD_SETTINGS
            }

            PrimaryModalType.BACKGROUND_SETTINGS -> {
                _uiMode.value = UiMode.BACKGROUND_SETTINGS
            }

            PrimaryModalType.MACROPAD_EDITOR,
            PrimaryModalType.MACROPAD_INSPECTOR,
            PrimaryModalType.LAYOUT_SETTINGS,
            PrimaryModalType.PROFILE_SETTINGS,
            PrimaryModalType.MACRO_TIMELINE_EDITOR,
            -> {
                _uiMode.value = UiMode.LAYOUT_EDITOR
            }

            else -> {}
        }
    }

    fun openPrimaryModal(type: PrimaryModalType) {
        openPrimaryModal(PrimaryModalConfig(type))
    }

    /**
     * Deep-links directly to any destination across single-screen and dual-screen modes.
     */
    fun navigateTo(destination: NavDestination) {
        AppLog.i(TAG, "navigateTo: $destination")
        _currentNavDestination.value = destination
        when (destination) {
            is NavDestination.CutoutLayoutEditor -> {
                _selectedCutoutId.value = destination.cutoutId
                setViewportEditActive(true)
            }

            else -> {
                openPrimaryModal(destination.toPrimaryModalConfig())
            }
        }
    }

    /**
     * Temporarily suspends current navigation/modal state (e.g. before recording touch gestures)
     * and dismisses open modal overlays so the user can interact with the screen.
     */
    fun suspendCurrentAndDismiss(overrideConfig: PrimaryModalConfig? = null) {
        val configToSuspend = overrideConfig ?: _activePrimaryModal.value
        AppLog.i(TAG, "suspendCurrentAndDismiss: saving config=$configToSuspend")
        _suspendedPrimaryModal.value = configToSuspend
        closePrimaryModal()
    }

    /**
     * Resumes the suspended modal destination (if any) back to the exact sub-menu / stack.
     */
    fun resumeSuspended() {
        val suspended = _suspendedPrimaryModal.value
        AppLog.i(TAG, "resumeSuspended: resuming config=$suspended")
        _suspendedPrimaryModal.value = null
        if (suspended != null) {
            openPrimaryModal(suspended)
        }
    }

    /**
     * Clears any currently suspended modal state without reopening.
     */
    fun clearSuspended() {
        AppLog.d(TAG, "clearSuspended")
        _suspendedPrimaryModal.value = null
    }

    fun closePrimaryModal() {
        AppLog.i(TAG, "closePrimaryModal: currentModal=${_activePrimaryModal.value?.type} currentUiMode=${_uiMode.value}")
        _activePrimaryModal.value = null
        _activeCropCutoutId.value = null
        _selectedCutoutId.value = null
        _currentNavDestination.value = null
        wasViewportEditActiveBeforeSettings = false
        if (_uiMode.value == UiMode.LAYOUT_EDITOR ||
            _uiMode.value == UiMode.GLOBAL_SETTINGS ||
            _uiMode.value == UiMode.KEYBOARD_SETTINGS ||
            _uiMode.value == UiMode.TOUCHPAD_SETTINGS ||
            _uiMode.value == UiMode.BACKGROUND_SETTINGS
        ) {
            _uiMode.value = UiMode.MACROPAD_USE
        }
    }

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
        if (open) {
            _activePrimaryModal.value = PrimaryModalConfig(PrimaryModalType.GLOBAL_SETTINGS)
        } else if (_activePrimaryModal.value?.type == PrimaryModalType.GLOBAL_SETTINGS) {
            _activePrimaryModal.value = null
        }
    }

    fun setKeyboardSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setKeyboardSettingsOpen($open)")
        _uiMode.value = if (open) UiMode.KEYBOARD_SETTINGS else UiMode.MACROPAD_USE
        if (open) {
            _activePrimaryModal.value = PrimaryModalConfig(PrimaryModalType.KEYBOARD_SETTINGS)
        } else if (_activePrimaryModal.value?.type == PrimaryModalType.KEYBOARD_SETTINGS) {
            _activePrimaryModal.value = null
        }
    }

    fun setTouchpadSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setTouchpadSettingsOpen($open)")
        _uiMode.value = if (open) UiMode.TOUCHPAD_SETTINGS else UiMode.MACROPAD_USE
        if (open) {
            _activePrimaryModal.value = PrimaryModalConfig(PrimaryModalType.TOUCHPAD_SETTINGS)
        } else if (_activePrimaryModal.value?.type == PrimaryModalType.TOUCHPAD_SETTINGS) {
            _activePrimaryModal.value = null
        }
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
        combine(uiMode, MacroPadState.isPeekActive, activePrimaryModal) { mode, peek, primaryModal ->
            peek || primaryModal != null || mode == UiMode.GLOBAL_SETTINGS || mode == UiMode.KEYBOARD_SETTINGS ||
                mode == UiMode.TOUCHPAD_SETTINGS || mode == UiMode.BACKGROUND_SETTINGS ||
                mode == UiMode.FULLSCREEN_KEYBOARD || mode == UiMode.FULLSCREEN_MOUSE ||
                mode == UiMode.VIEWPORT_EDIT
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * True whenever any settings menu, Quick Menu, or editor modal is active/open.
     * Used by swipe gesture processors to disable edge gesture handling when menus are open.
     */
    val isAnyMenuOpen: StateFlow<Boolean> =
        combine(uiMode, activePrimaryModal) { mode, primaryModal ->
            primaryModal != null || mode == UiMode.GLOBAL_SETTINGS || mode == UiMode.KEYBOARD_SETTINGS ||
                mode == UiMode.TOUCHPAD_SETTINGS || mode == UiMode.BACKGROUND_SETTINGS ||
                mode == UiMode.LAYOUT_EDITOR || mode == UiMode.QUICK_MENU
        }.stateIn(scope, SharingStarted.Eagerly, false)

    fun setFullscreenKeyboardActive(
        active: Boolean,
        layout: KbLayout? = null,
    ) {
        if (active && (OnboardingWizardManager.isWizardActive.value || _isPrivdSetupWizardActive.value)) {
            AppLog.w(TAG, "setFullscreenKeyboardActive suppressed while wizard is active")
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
        if (active && (OnboardingWizardManager.isWizardActive.value || _isPrivdSetupWizardActive.value)) {
            AppLog.w(TAG, "setFullscreenMouseActive suppressed while wizard is active")
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
            _activePrimaryModal.value = PrimaryModalConfig(PrimaryModalType.BACKGROUND_SETTINGS)
        } else {
            _uiMode.value = if (wasViewportEditActiveBeforeSettings) UiMode.VIEWPORT_EDIT else UiMode.MACROPAD_USE
            if (_activePrimaryModal.value?.type == PrimaryModalType.BACKGROUND_SETTINGS) {
                _activePrimaryModal.value = null
            }
        }
    }

    fun setEditorActive(active: Boolean) {
        AppLog.i(TAG, "setEditorActive($active)")
        _uiMode.value = if (active) UiMode.LAYOUT_EDITOR else UiMode.MACROPAD_USE
        if (active) {
            _activePrimaryModal.value = PrimaryModalConfig(PrimaryModalType.MACROPAD_EDITOR)
        } else if (_activePrimaryModal.value?.type == PrimaryModalType.MACROPAD_EDITOR ||
            _activePrimaryModal.value?.type == PrimaryModalType.MACROPAD_INSPECTOR ||
            _activePrimaryModal.value?.type == PrimaryModalType.LAYOUT_SETTINGS ||
            _activePrimaryModal.value?.type == PrimaryModalType.PROFILE_SETTINGS ||
            _activePrimaryModal.value?.type == PrimaryModalType.MACRO_TIMELINE_EDITOR
        ) {
            _activePrimaryModal.value = null
        }
    }

    /** Closes whichever fullscreen modal overlay is currently active. */
    fun closeActiveModal() {
        AppLog.i(
            TAG,
            "closeActiveModal: mode=${_uiMode.value} peek=${MacroPadState.isPeekActive.value} primaryModal=${_activePrimaryModal.value?.type}",
        )
        _uiMode.value = UiMode.MACROPAD_USE
        _activeCropCutoutId.value = null
        _selectedCutoutId.value = null
        _activePrimaryModal.value = null
        wasViewportEditActiveBeforeSettings = false
        MacroPadState.resetPeek()
    }

    /**
     * Called by [SwipeGestureProcessor] on edge-swipe detection.
     * Dispatches to the correct action based on current navigation state.
     */
    fun handleEdgeSwipe() {
        if (OnboardingWizardManager.isWizardActive.value || _isPrivdSetupWizardActive.value) {
            AppLog.w(TAG, "handleEdgeSwipe suppressed while wizard is active")
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
