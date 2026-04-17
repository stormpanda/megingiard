package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.SettingsManager
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TAG = "MacroPadState"

/**
 * Recomputes [PadProfile.enableKeyboard], [PadProfile.enableGamepad], and [PadProfile.enableMouse]
 * from the actual set of button actions so the injectors are started only when needed.
 */
private fun PadProfile.withSyncedDeviceFlags(): PadProfile {
    // Macro buttons can inject any event type — treat them as requiring all three devices.
    val hasMacro = buttons.any { it.action is PadAction.Macro }
    val kb = hasMacro || buttons.any { it.action is PadAction.KeyboardKey }
    val gp = hasMacro || buttons.any { it.action is PadAction.GamepadButton }
    val ms = hasMacro || buttons.any {
        it.action is PadAction.MouseButton    ||
        it.action is PadAction.ScrollWheel    ||
        it.action is PadAction.TrackpointMove ||
        it.action is PadAction.MouseLeftClick ||
        it.action is PadAction.MouseRightClick
    }
    return if (enableKeyboard == kb && enableGamepad == gp && enableMouse == ms) this
    else copy(enableKeyboard = kb, enableGamepad = gp, enableMouse = ms)
}

/**
 * Runtime state holder for the MacroPad tool.
 *
 * Manages the list of [PadProfile]s and the currently active profile.
 * Persistence is delegated to [SettingsManager] — all mutators trigger
 * [SettingsManager.saveMacroPadData] after updating the in-memory state.
 *
 * [SettingsManager] calls [loadFrom] once during its init phase to restore
 * previously persisted profiles.
 */
object MacroPadState {

    // App-lifetime scope: intentionally never cancelled — this singleton lives for the
    // duration of the process. Cancellation is handled by process termination.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val _profiles = MutableStateFlow<List<PadProfile>>(emptyList())
    val profiles: StateFlow<List<PadProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    /**
     * Derived read-only view of the currently active profile.
     * Falls back to the first profile if the stored ID is not found.
     */
    val activeProfile: StateFlow<PadProfile?> =
        combine(_profiles, _activeProfileId) { ps, id ->
            ps.firstOrNull { it.id == id } ?: ps.firstOrNull()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // -------------------------------------------------------------------------
    // Internal load hook (called by SettingsManager.init)
    // -------------------------------------------------------------------------

    internal fun loadFrom(profiles: List<PadProfile>, activeProfileId: String?) {
        // One-time migration: profiles saved before the Trackpoint-as-button change stored the
        // trackpoint as profile-level flags (hasTrackpoint, trackpointPosX/Y, trackpointSize).
        // Convert any such profile to a TrackpointMove PadButton and persist immediately.
        var needsSave = false
        val migrated = profiles.map { profile ->
            if (!profile.hasTrackpoint) return@map profile
            needsSave = true
            val tpSize = when {
                profile.trackpointSize <= 1.5f -> TrackpointSize.SMALL
                profile.trackpointSize <= 2.5f -> TrackpointSize.MEDIUM
                else                           -> TrackpointSize.LARGE
            }
            val tpButton = PadButton(
                id          = UUID.randomUUID().toString(),
                label       = "",
                posX        = profile.trackpointPosX,
                posY        = profile.trackpointPosY,
                buttonSize  = ButtonSize.SIZE_1X1,
                buttonShape = ButtonShape.CIRCLE,
                action      = PadAction.TrackpointMove(tpSize),
            )
            profile.copy(
                buttons      = profile.buttons + tpButton,
                hasTrackpoint = false,
            )
        }
        val withFlags = migrated.map { profile ->
            profile.withSyncedDeviceFlags().also { synced ->
                if (synced != profile) needsSave = true
            }
        }
        _profiles.value = withFlags
        _activeProfileId.value = activeProfileId
        AppLog.d(TAG, "loadFrom: ${withFlags.size} profiles, activeId=$activeProfileId, migrated=$needsSave")
        if (needsSave) SettingsManager.saveMacroPadData()
    }

    // -------------------------------------------------------------------------
    // Profile CRUD
    // -------------------------------------------------------------------------

    fun addProfile(profile: PadProfile) {
        AppLog.d(TAG, "addProfile id=${profile.id} name='${profile.name}'")
        _profiles.value = _profiles.value + profile
        if (_activeProfileId.value == null) _activeProfileId.value = profile.id
        SettingsManager.saveMacroPadData()
    }

    fun updateProfile(profile: PadProfile) {
        val synced = profile.withSyncedDeviceFlags()
        AppLog.d(TAG, "updateProfile id=${synced.id} (kb=${synced.enableKeyboard} gp=${synced.enableGamepad} ms=${synced.enableMouse})")
        _profiles.value = _profiles.value.map { if (it.id == synced.id) synced else it }
        SettingsManager.saveMacroPadData()
    }

    fun deleteProfile(profileId: String) {
        val remaining = _profiles.value.filter { it.id != profileId }
        _profiles.value = remaining
        if (_activeProfileId.value == profileId) {
            _activeProfileId.value = remaining.firstOrNull()?.id
        }
        AppLog.d(TAG, "deleteProfile id=$profileId → activeId=${_activeProfileId.value}")
        SettingsManager.saveMacroPadData()
    }

    fun renameProfile(profileId: String, newName: String) {
        AppLog.d(TAG, "renameProfile id=$profileId name='$newName'")
        _profiles.value = _profiles.value.map {
            if (it.id == profileId) it.copy(name = newName) else it
        }
        SettingsManager.saveMacroPadData()
    }

    fun setActiveProfileId(id: String?) {
        AppLog.i(TAG, "setActiveProfileId: $id")
        _activeProfileId.value = id
        SettingsManager.saveMacroPadData()
    }

    // -------------------------------------------------------------------------
    // Ambient Peek state
    // -------------------------------------------------------------------------

    private val _isPeekActive = MutableStateFlow(false)
    val isPeekActive: StateFlow<Boolean> = _isPeekActive.asStateFlow()

    fun togglePeek() {
        val next = !_isPeekActive.value
        AppLog.d(TAG, "togglePeek → $next")
        _isPeekActive.value = next
    }
    fun resetPeek() {
        AppLog.d(TAG, "resetPeek")
        _isPeekActive.value = false
    }
}
