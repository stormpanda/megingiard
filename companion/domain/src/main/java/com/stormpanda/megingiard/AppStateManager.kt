package com.stormpanda.megingiard

import com.stormpanda.megingiard.catalog.SystemRoleClassifier
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.macropad.AutoSwitchCoordinator
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.onboarding.OnboardingWizardManager
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.session.ActiveGameSession
import com.stormpanda.megingiard.session.EmulatorDetectionFunnel
import com.stormpanda.megingiard.settings.KeyboardSettings
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.ui.PrimaryModalConfig
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import com.stormpanda.megingiard.ui.PrimaryModalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
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
    foregroundApp: String? = AutoSwitchCoordinator.foregroundApp.value,
    focusedRomIdentifier: String? = null,
    activeSession: ActiveGameSession? = null,
): Boolean =
    when (this) {
        CompanionViewMode.MACROPAD -> {
            false
        }

        CompanionViewMode.DASHBOARD -> {
            true
        }

        CompanionViewMode.AUTO -> {
            val isForegroundLauncher = SystemRoleClassifier.isLauncherOrSystemUi(foregroundApp)

            if ((focusedAppPackageName == null && activeSession == null) || isForegroundLauncher) {
                true
            } else {
                val matchesPath = activeProfile?.matches(focusedAppPackageName, focusedRomPath, isActiveProfile = true) == true
                val matchesIdentifier =
                    focusedRomIdentifier != null &&
                        activeProfile?.matches(focusedAppPackageName, focusedRomIdentifier, isActiveProfile = true) == true
                val matchesActiveSession =
                    activeSession != null && (
                        activeProfile?.matches(
                            activeSession.packageName,
                            activeSession.romIdentifier,
                            activeSession.systemId,
                            isActiveProfile = true,
                        ) ==
                            true ||
                            activeProfile?.matches(
                                activeSession.packageName,
                                activeSession.romPath,
                                activeSession.systemId,
                                isActiveProfile = true,
                            ) ==
                            true
                    )
                !(matchesPath || matchesIdentifier || matchesActiveSession)
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

    // ── Mirror control signals ────────────────────────────────────────────────
    // One-shot fire-and-forget flags: MainActivity resets them after handling.

    /** Set to true by MirrorPlayStop when mirror is not yet capturing; MainActivity starts
     * ScreenCaptureService and resets. */
    private val _mirrorStartRequested = MutableStateFlow(false)
    val mirrorStartRequested: StateFlow<Boolean> = _mirrorStartRequested.asStateFlow()

    /** Set to true by MirrorPlayStop when mirror is currently capturing; MainActivity sends
     * a STOP intent to ScreenCaptureService and resets. */
    private val _mirrorStopRequested = MutableStateFlow(false)
    val mirrorStopRequested: StateFlow<Boolean> = _mirrorStopRequested.asStateFlow()

    /** Set to true when user requests app shut off; MainActivity handles graceful shutdown and resets. */
    private val _shutOffRequested = MutableStateFlow(false)
    val shutOffRequested: StateFlow<Boolean> = _shutOffRequested.asStateFlow()

    private val _showEmptyCutoutsDialog = MutableStateFlow(false)
    val showEmptyCutoutsDialog: StateFlow<Boolean> = _showEmptyCutoutsDialog.asStateFlow()

    fun setShowEmptyCutoutsDialog(show: Boolean) {
        _showEmptyCutoutsDialog.value = show
    }

    data class AppLaunchRequest(
        val packageName: String,
        val touchX: Float = -1f,
        val touchY: Float = -1f,
    )

    private val _pendingAppLaunchRequest = MutableStateFlow<AppLaunchRequest?>(null)
    val pendingAppLaunchRequest: StateFlow<AppLaunchRequest?> = _pendingAppLaunchRequest.asStateFlow()

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

    fun requestMirrorStart() {
        AppLog.i(TAG, "requestMirrorStart")
        val isViewportEditing =
            _activePrimaryModal.value?.type == PrimaryModalType.MIRROR_VIEWPORT_EDITOR
        if (!isViewportEditing && (
                MacroPadState.activeLayout.value
                    ?.mirrorCutouts
                    .isNullOrEmpty()
            )
        ) {
            _showEmptyCutoutsDialog.value = true
        }
        _mirrorStartRequested.value = true
    }

    fun requestMirrorStop() {
        AppLog.i(TAG, "requestMirrorStop")
        _showEmptyCutoutsDialog.value = false
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

    data class ExternalClientState(
        val packageName: String?,
        val focusedApp: String?,
        val focusedRomPath: String? = null,
        val focusedRomIdentifier: String? = null,
        val hoveredPackage: String? = null,
        val hoveredLabel: String? = null,
        val hoveredRomPath: String? = null,
        val hoveredRomIdentifier: String? = null,
        val hoveredSystemId: String? = null,
        val hoveredPrimaryColor: Int? = null,
        val hoveredSecondaryColor: Int? = null,
    )

    data class StandaloneFocusState(
        val focusedApp: String? = null,
        val focusedRomPath: String? = null,
        val focusedRomIdentifier: String? = null,
    )

    data class EffectiveFocusState(
        val isExternalClientActive: Boolean = false,
        val externalClientPackage: String? = null,
        val focusedAppPackageName: String? = null,
        val focusedRomPath: String? = null,
        val focusedRomIdentifier: String? = null,
        val hoveredAppPackageName: String? = null,
        val hoveredAppLabel: String? = null,
        val hoveredRomPath: String? = null,
        val hoveredRomIdentifier: String? = null,
        val hoveredSystemId: String? = null,
        val hoveredAppPrimaryColor: Int? = null,
        val hoveredAppSecondaryColor: Int? = null,
    )

    private val _externalClientState = MutableStateFlow<ExternalClientState?>(null)
    val externalClientState: StateFlow<ExternalClientState?> = _externalClientState.asStateFlow()

    private val _standaloneFocusState = MutableStateFlow(StandaloneFocusState())
    val standaloneFocusState: StateFlow<StandaloneFocusState> = _standaloneFocusState.asStateFlow()

    private val _effectiveFocusState = MutableStateFlow(EffectiveFocusState())
    val effectiveFocusState: StateFlow<EffectiveFocusState> = _effectiveFocusState.asStateFlow()

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

    private fun deriveEffectiveFocusState(
        client: ExternalClientState?,
        standalone: StandaloneFocusState,
    ): EffectiveFocusState =
        if (client != null) {
            EffectiveFocusState(
                isExternalClientActive = true,
                externalClientPackage = client.packageName,
                focusedAppPackageName = client.focusedApp,
                focusedRomPath = client.focusedRomPath,
                focusedRomIdentifier = client.focusedRomIdentifier,
                hoveredAppPackageName = client.hoveredPackage,
                hoveredAppLabel = client.hoveredLabel,
                hoveredRomPath = client.hoveredRomPath,
                hoveredRomIdentifier = client.hoveredRomIdentifier,
                hoveredSystemId = client.hoveredSystemId,
                hoveredAppPrimaryColor = client.hoveredPrimaryColor,
                hoveredAppSecondaryColor = client.hoveredSecondaryColor,
            )
        } else {
            EffectiveFocusState(
                isExternalClientActive = false,
                externalClientPackage = null,
                focusedAppPackageName = standalone.focusedApp,
                focusedRomPath = standalone.focusedRomPath,
                focusedRomIdentifier = standalone.focusedRomIdentifier,
            )
        }

    private fun applyEffectiveFocusState(effective: EffectiveFocusState) {
        _effectiveFocusState.value = effective
        _isExternalClientActive.value = effective.isExternalClientActive
        _externalClientPackage.value = effective.externalClientPackage
        _focusedAppPackageName.value = effective.focusedAppPackageName
        _focusedRomPath.value = effective.focusedRomPath
        _focusedRomIdentifier.value = effective.focusedRomIdentifier
        _hoveredAppPackageName.value = effective.hoveredAppPackageName
        _hoveredAppLabel.value = effective.hoveredAppLabel
        _hoveredRomPath.value = effective.hoveredRomPath
        _hoveredRomIdentifier.value = effective.hoveredRomIdentifier
        _hoveredSystemId.value = effective.hoveredSystemId
        _hoveredAppPrimaryColor.value = effective.hoveredAppPrimaryColor
        _hoveredAppSecondaryColor.value = effective.hoveredAppSecondaryColor
        EmulatorDetectionFunnel.setFocusedRomContext(effective.focusedRomPath, effective.focusedRomIdentifier)
    }

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
        if (isActive) {
            _externalClientState.value =
                ExternalClientState(
                    packageName = packageName,
                    focusedApp = focusedApp,
                    focusedRomPath = focusedRomPath,
                    focusedRomIdentifier = focusedRomIdentifier,
                    hoveredPackage = hoveredPackage,
                    hoveredLabel = hoveredLabel,
                    hoveredRomPath = hoveredRomPath,
                    hoveredRomIdentifier = hoveredRomIdentifier,
                    hoveredSystemId = hoveredSystemId,
                    hoveredPrimaryColor = hoveredPrimaryColor,
                    hoveredSecondaryColor = hoveredSecondaryColor,
                )
        } else {
            _externalClientState.value = null
            if (focusedApp != null) {
                _standaloneFocusState.value =
                    StandaloneFocusState(
                        focusedApp = focusedApp,
                        focusedRomPath = focusedRomPath,
                        focusedRomIdentifier = focusedRomIdentifier,
                    )
            }
        }
        applyEffectiveFocusState(deriveEffectiveFocusState(_externalClientState.value, _standaloneFocusState.value))
    }

    fun setStandaloneForegroundState(
        focusedApp: String?,
        focusedRomPath: String? = null,
        focusedRomIdentifier: String? = null,
    ) {
        val next = StandaloneFocusState(focusedApp, focusedRomPath, focusedRomIdentifier)
        if (_standaloneFocusState.value != next) {
            AppLog.d(TAG, "setStandaloneForegroundState: focusedApp=$focusedApp focusedRom=$focusedRomPath focusedId=$focusedRomIdentifier")
            _standaloneFocusState.value = next
        }
        applyEffectiveFocusState(deriveEffectiveFocusState(_externalClientState.value, _standaloneFocusState.value))
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

    // ── Single Source of Truth for Companion Surface & Primary Modals ────────

    private val _companionSurfaceMode = MutableStateFlow(CompanionSurfaceMode.MACROPAD)
    val companionSurfaceMode: StateFlow<CompanionSurfaceMode> = _companionSurfaceMode.asStateFlow()

    private val _activePrimaryModal = MutableStateFlow<PrimaryModalConfig?>(null)
    val activePrimaryModal: StateFlow<PrimaryModalConfig?> = _activePrimaryModal.asStateFlow()

    private val _suspendedPrimaryModal = MutableStateFlow<PrimaryModalConfig?>(null)
    val suspendedPrimaryModal: StateFlow<PrimaryModalConfig?> = _suspendedPrimaryModal.asStateFlow()

    private val _isQuickMenuOpen = MutableStateFlow(false)
    val isQuickMenuOpen: StateFlow<Boolean> = _isQuickMenuOpen.asStateFlow()

    private fun isPrimaryModalActive(type: PrimaryModalType): StateFlow<Boolean> =
        _activePrimaryModal
            .map { it?.type == type }
            .stateIn(scope, SharingStarted.Eagerly, false)

    private fun isCompanionSurfaceActive(mode: CompanionSurfaceMode): StateFlow<Boolean> =
        _companionSurfaceMode
            .map { it == mode }
            .stateIn(scope, SharingStarted.Eagerly, false)

    val isGlobalSettingsOpen: StateFlow<Boolean> = isPrimaryModalActive(PrimaryModalType.GLOBAL_SETTINGS)
    val isKeyboardSettingsOpen: StateFlow<Boolean> = isPrimaryModalActive(PrimaryModalType.KEYBOARD_SETTINGS)
    val isTouchpadSettingsOpen: StateFlow<Boolean> = isPrimaryModalActive(PrimaryModalType.TOUCHPAD_SETTINGS)
    val isEditorActive: StateFlow<Boolean> = isPrimaryModalActive(PrimaryModalType.MACROPAD_EDITOR)

    val isViewportEditActive: StateFlow<Boolean> = isPrimaryModalActive(PrimaryModalType.MIRROR_VIEWPORT_EDITOR)
    val isFullscreenKeyboardActive: StateFlow<Boolean> = isCompanionSurfaceActive(CompanionSurfaceMode.KEYBOARD)
    val isFullscreenMouseActive: StateFlow<Boolean> = isCompanionSurfaceActive(CompanionSurfaceMode.TOUCHPAD)

    fun openQuickMenu() {
        if (OnboardingWizardManager.isWizardActive.value || _isPrivdSetupWizardActive.value) {
            AppLog.w(TAG, "openQuickMenu suppressed while wizard is active")
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

    private val _companionViewMode = MutableStateFlow(CompanionViewMode.AUTO)
    val companionViewMode: StateFlow<CompanionViewMode> = _companionViewMode.asStateFlow()

    private val _wasAutoSwitchDeactivatedInEditor = MutableStateFlow(false)
    val wasAutoSwitchDeactivatedInEditor: StateFlow<Boolean> = _wasAutoSwitchDeactivatedInEditor.asStateFlow()

    private var wasAutoSwitchActiveWhenEditorOpened = false

    fun resetAutoSwitchDeactivatedInEditor() {
        AppLog.d(TAG, "resetAutoSwitchDeactivatedInEditor")
        _wasAutoSwitchDeactivatedInEditor.value = false
    }

    private val _autoSwitchOffToastEvent =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val autoSwitchOffToastEvent: SharedFlow<Unit> = _autoSwitchOffToastEvent.asSharedFlow()

    private data class FocusContext(
        val packageName: String?,
        val romPath: String?,
        val romIdentifier: String?,
        val activeSession: ActiveGameSession?,
    )

    private val focusContextFlow: Flow<FocusContext> =
        combine(
            _effectiveFocusState,
            EmulatorDetectionFunnel.activeSession,
        ) { effective, session ->
            FocusContext(
                packageName = effective.focusedAppPackageName,
                romPath = effective.focusedRomPath,
                romIdentifier = effective.focusedRomIdentifier,
                activeSession = session,
            )
        }

    val showIntegrationHome: StateFlow<Boolean> =
        combine(
            focusContextFlow,
            MacroPadState.activeProfile,
            _companionViewMode,
            AutoSwitchCoordinator.foregroundApp,
        ) { focus, profile, viewMode, foreground ->
            viewMode.shouldShowIntegrationHome(
                focusedAppPackageName = focus.packageName,
                focusedRomPath = focus.romPath,
                focusedRomIdentifier = focus.romIdentifier,
                activeSession = focus.activeSession,
                activeProfile = profile,
                foregroundApp = foreground,
            )
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
        if (isEditorActive.value) {
            if (mode == CompanionViewMode.AUTO) {
                wasAutoSwitchActiveWhenEditorOpened = true
                _wasAutoSwitchDeactivatedInEditor.value = false
            } else if (wasAutoSwitchActiveWhenEditorOpened) {
                _wasAutoSwitchDeactivatedInEditor.value = true
            }
        }
        if (mode == CompanionViewMode.AUTO) {
            AutoSwitchCoordinator.reevaluateAutoState()
        }
    }

    val isPrivdPromptDismissed: StateFlow<Boolean> = MacroPadSettings.privdPromptDismissed

    fun setPrivdPromptDismissed(dismissed: Boolean) {
        AppLog.d(TAG, "setPrivdPromptDismissed($dismissed)")
        if (!_isAccessibilityActive.value) {
            MacroPadSettings.setPrivdPromptDismissed(false)
            return
        }
        if (dismissed) {
            privdPromptLatched.value = false
        }
        val effectiveDismissed = dismissed && PrivdManager.state.value != PrivdState.RUNNING
        MacroPadSettings.setPrivdPromptDismissed(effectiveDismissed)
    }

    private val _isAccessibilityActive = MutableStateFlow(true)
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    fun setAccessibilityActive(active: Boolean) {
        AppLog.d(TAG, "setAccessibilityActive($active)")
        _isAccessibilityActive.value = active
        if (!active) {
            privdPromptLatched.value = true
            MacroPadSettings.setPrivdPromptDismissed(false)
        }
    }

    fun resetPrivdPromptState() {
        AppLog.d(TAG, "resetPrivdPromptState")
        privdPromptLatched.value = false
        MacroPadSettings.setPrivdPromptDismissed(false)
    }

    private val privdPromptLatched = MutableStateFlow(false)

    val isPrivdPromptActive: StateFlow<Boolean> =
        combine(
            PrivdManager.state,
            MacroPadSettings.privdPromptDismissed,
            _isAccessibilityActive,
            privdPromptLatched,
        ) { state, dismissed, accessibilityActive, latched ->
            when {
                !accessibilityActive -> true
                dismissed -> false
                state == PrivdState.FAILED -> true
                else -> latched
            }
        }.stateIn(scope, SharingStarted.Eagerly, false)

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

    private val _isMirrorEditorBackgroundHidden = MutableStateFlow(false)
    val isMirrorEditorBackgroundHidden: StateFlow<Boolean> = _isMirrorEditorBackgroundHidden.asStateFlow()

    fun setMirrorEditorBackgroundHidden(hidden: Boolean) {
        AppLog.d(TAG, "setMirrorEditorBackgroundHidden($hidden)")
        _isMirrorEditorBackgroundHidden.value = hidden
    }

    fun toggleMirrorEditorBackgroundHidden() {
        setMirrorEditorBackgroundHidden(!_isMirrorEditorBackgroundHidden.value)
    }

    fun openPrimaryModal(config: PrimaryModalConfig) {
        val previousType = _activePrimaryModal.value?.type
        AppLog.i(TAG, "openPrimaryModal: type=${config.type} payload=${config.payload}")
        _activePrimaryModal.value = config
        val payload = config.payload
        if (payload is PrimaryModalPayload.CropSelector) {
            _activeCropCutoutId.value = payload.cutoutId
        }
        if (config.type == PrimaryModalType.MIRROR_VIEWPORT_EDITOR) {
            _showEmptyCutoutsDialog.value = false
            ScreenCaptureManager.setFollowActive(false, persist = true)
            _activeCropCutoutId.value = _selectedCutoutId.value
            _isMirrorEditorBackgroundHidden.value = false
            MacroPadState.activeLayout.value?.let { layout ->
                MacroPadState.setLayoutMirrorAutoStart(layout.id, true)
            }
            requestMirrorStart()
        } else if (previousType == PrimaryModalType.MIRROR_VIEWPORT_EDITOR) {
            _selectedCutoutId.value = null
            _isMirrorEditorBackgroundHidden.value = false
            if (payload !is PrimaryModalPayload.CropSelector) {
                _activeCropCutoutId.value = null
            }
        }
    }

    fun openPrimaryModal(type: PrimaryModalType) {
        openPrimaryModal(PrimaryModalConfig(type))
    }

    /**
     * Atomically suspends the current modal state and transitions directly to [newConfig]
     * without emitting an intermediate null state or causing overlay window teardown.
     */
    fun suspendCurrentAndOpen(
        newConfig: PrimaryModalConfig,
        overrideConfig: PrimaryModalConfig? = null,
    ) {
        val configToSuspend = overrideConfig ?: _activePrimaryModal.value
        AppLog.i(TAG, "suspendCurrentAndOpen: saving config=$configToSuspend, opening newConfig=$newConfig")
        _suspendedPrimaryModal.value = configToSuspend
        openPrimaryModal(newConfig)
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
        val currentType = _activePrimaryModal.value?.type
        AppLog.i(TAG, "closePrimaryModal: currentModal=$currentType")
        _activePrimaryModal.value = null
        _activeCropCutoutId.value = null
        _selectedCutoutId.value = null
        _isMirrorEditorBackgroundHidden.value = false
    }

    fun setActiveCropCutoutId(id: String?) {
        AppLog.d(TAG, "setActiveCropCutoutId($id)")
        _activeCropCutoutId.value = id
    }

    fun setSelectedCutoutId(id: String?) {
        AppLog.d(TAG, "setSelectedCutoutId($id)")
        _selectedCutoutId.value = id
        if (isViewportEditActive.value) {
            _activeCropCutoutId.value = id
        }
    }

    private fun setPrimaryModalActive(
        type: PrimaryModalType,
        active: Boolean,
    ) {
        if (active) {
            openPrimaryModal(PrimaryModalConfig(type))
        } else if (_activePrimaryModal.value?.type == type) {
            closePrimaryModal()
        }
    }

    fun setGlobalSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setGlobalSettingsOpen($open)")
        setPrimaryModalActive(PrimaryModalType.GLOBAL_SETTINGS, open)
    }

    fun setKeyboardSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setKeyboardSettingsOpen($open)")
        setPrimaryModalActive(PrimaryModalType.KEYBOARD_SETTINGS, open)
    }

    fun setTouchpadSettingsOpen(open: Boolean) {
        AppLog.d(TAG, "setTouchpadSettingsOpen($open)")
        setPrimaryModalActive(PrimaryModalType.TOUCHPAD_SETTINGS, open)
    }

    private val _fullscreenMouseSensitivity = MutableStateFlow(1.0f)
    val fullscreenMouseSensitivity: StateFlow<Float> = _fullscreenMouseSensitivity.asStateFlow()

    val fullscreenKeyboardLayout: StateFlow<KbLayout> = KeyboardSettings.kbLayout

    /**
     * True whenever any modal dialog, peek overlay, or non-macropad fullscreen surface is showing.
     * Used by [handleEdgeSwipe] to determine if an edge swipe should close an active modal or overlay.
     */
    val isAnyModalActive: StateFlow<Boolean> =
        combine(activePrimaryModal, MacroPadState.isPeekActive, _companionSurfaceMode) { modal, peek, surface ->
            modal != null || peek || surface != CompanionSurfaceMode.MACROPAD
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * True whenever any modal dialog or Quick Menu is open.
     * Used by swipe gesture processors to disable edge gesture handling when menus are open.
     */
    val isAnyMenuOpen: StateFlow<Boolean> =
        combine(activePrimaryModal, _isQuickMenuOpen) { modal, quickMenuOpen ->
            modal != null || quickMenuOpen
        }.stateIn(scope, SharingStarted.Eagerly, false)

    fun setFullscreenKeyboardActive(active: Boolean) {
        if (active && (OnboardingWizardManager.isWizardActive.value || _isPrivdSetupWizardActive.value)) {
            AppLog.w(TAG, "setFullscreenKeyboardActive suppressed while wizard is active")
            return
        }
        AppLog.i(TAG, "setFullscreenKeyboardActive($active)")
        if (active) {
            _companionSurfaceMode.value = CompanionSurfaceMode.KEYBOARD
        } else {
            if (_companionSurfaceMode.value == CompanionSurfaceMode.KEYBOARD) {
                _companionSurfaceMode.value = CompanionSurfaceMode.MACROPAD
            }
            if (isKeyboardSettingsOpen.value) {
                setKeyboardSettingsOpen(false)
            }
        }
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
            _companionSurfaceMode.value = CompanionSurfaceMode.TOUCHPAD
        } else {
            _fullscreenMouseSensitivity.value = 1.0f
            if (_companionSurfaceMode.value == CompanionSurfaceMode.TOUCHPAD) {
                _companionSurfaceMode.value = CompanionSurfaceMode.MACROPAD
            }
        }
    }

    fun setViewportEditActive(active: Boolean) {
        AppLog.i(TAG, "setViewportEditActive($active)")
        _showEmptyCutoutsDialog.value = false
        if (active) {
            openPrimaryModal(PrimaryModalConfig(PrimaryModalType.MIRROR_VIEWPORT_EDITOR))
        } else {
            _selectedCutoutId.value = null
            _activeCropCutoutId.value = null
            _isMirrorEditorBackgroundHidden.value = false
            if (_activePrimaryModal.value?.type == PrimaryModalType.MIRROR_VIEWPORT_EDITOR) {
                closePrimaryModal()
            }
        }
    }

    fun setEditorActive(active: Boolean) {
        AppLog.i(TAG, "setEditorActive($active)")
        if (active) {
            openPrimaryModal(PrimaryModalConfig(PrimaryModalType.MACROPAD_EDITOR))
        } else if (isEditorActive.value) {
            closePrimaryModal()
        }
    }

    /** Closes whichever modal dialog or non-macropad fullscreen surface is currently active. */
    fun closeActiveModal() {
        AppLog.i(
            TAG,
            "closeActiveModal: surface=${_companionSurfaceMode.value} peek=${MacroPadState.isPeekActive.value} primaryModal=${_activePrimaryModal.value?.type}",
        )
        closePrimaryModal()
        _companionSurfaceMode.value = CompanionSurfaceMode.MACROPAD
        _fullscreenMouseSensitivity.value = 1.0f
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
            var wasActive = false
            isEditorActive.collect { active ->
                if (active && !wasActive) {
                    wasAutoSwitchActiveWhenEditorOpened = (_companionViewMode.value == CompanionViewMode.AUTO)
                    _wasAutoSwitchDeactivatedInEditor.value = false
                    AppLog.d(TAG, "isEditorActive=true, wasAutoSwitchActiveWhenEditorOpened=$wasAutoSwitchActiveWhenEditorOpened")
                } else if (!active && wasActive) {
                    _wasAutoSwitchDeactivatedInEditor.value = false
                    wasAutoSwitchActiveWhenEditorOpened = false
                    AppLog.d(TAG, "isEditorActive=false, reset auto switch editor state")
                }
                wasActive = active
            }
        }
        scope.launch {
            _companionSurfaceMode.collect { mode ->
                if (mode != CompanionSurfaceMode.KEYBOARD && isKeyboardSettingsOpen.value) {
                    setKeyboardSettingsOpen(false)
                }
                if (mode != CompanionSurfaceMode.TOUCHPAD) {
                    _fullscreenMouseSensitivity.value = 1.0f
                    if (isTouchpadSettingsOpen.value) {
                        setTouchpadSettingsOpen(false)
                    }
                }
            }
        }
        scope.launch {
            var lastActiveLayoutId: String? = null
            MacroPadState.activeLayout.collect { layout ->
                val newId = layout?.id
                if (lastActiveLayoutId != null && newId != lastActiveLayoutId) {
                    AppLog.d(TAG, "activeLayout changed from $lastActiveLayoutId to $newId; checking modal dismissal")
                    if (_activePrimaryModal.value?.type == PrimaryModalType.MIRROR_VIEWPORT_EDITOR) {
                        setViewportEditActive(false)
                    } else if (_activePrimaryModal.value == null && !_isQuickMenuOpen.value) {
                        closeActiveModal()
                    }
                }
                lastActiveLayoutId = newId
            }
        }
        scope.launch {
            PrivdManager.state.collect { state ->
                if (state == PrivdState.FAILED &&
                    !MacroPadSettings.privdPromptDismissed.value
                ) {
                    privdPromptLatched.value = true
                } else if (state == PrivdState.RUNNING &&
                    _isAccessibilityActive.value &&
                    MacroPadSettings.privdPromptDismissed.value
                ) {
                    MacroPadSettings.setPrivdPromptDismissed(false)
                }
            }
        }
    }
}
