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
    // Compute device requirements from all buttons across all layouts.
    val allButtons = layouts.flatMap { it.buttons }
    val hasMacro = allButtons.any { it.action is PadAction.Macro }
    val kb = hasMacro || allButtons.any { it.action is PadAction.KeyboardKey }
    val gp = hasMacro || allButtons.any { it.action is PadAction.GamepadButton }
    val ms = hasMacro || allButtons.any {
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

    /**
     * Derived read-only view of the currently active layout within the active profile.
     * Falls back to the first layout if the stored layout ID is not found.
     */
    val activeLayout: StateFlow<PadLayout?> =
        combine(_profiles, _activeProfileId) { ps, profileId ->
            val profile = ps.firstOrNull { it.id == profileId } ?: ps.firstOrNull()
            val layoutId = profile?.activeLayoutId
            profile?.layouts?.firstOrNull { it.id == layoutId } ?: profile?.layouts?.firstOrNull()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // -------------------------------------------------------------------------
    // Internal load hook (called by SettingsManager.init)
    // -------------------------------------------------------------------------

    internal fun loadFrom(profiles: List<PadProfile>, activeProfileId: String?) {
        // Multi-step migration: bring old profile formats up to the current data model.
        var needsSave = false
        val migrated = profiles.map { profile ->
            var p = profile
            // Step 1: Migrate legacy hasTrackpoint → PadButton in the buttons list.
            if (p.hasTrackpoint) {
                needsSave = true
                val tpSize = when {
                    p.trackpointSize <= 1.5f -> TrackpointSize.SMALL
                    p.trackpointSize <= 2.5f -> TrackpointSize.MEDIUM
                    else                     -> TrackpointSize.LARGE
                }
                val tpButton = PadButton(
                    id          = UUID.randomUUID().toString(),
                    label       = "",
                    posX        = p.trackpointPosX,
                    posY        = p.trackpointPosY,
                    buttonSize  = ButtonSize.SIZE_1X1,
                    buttonShape = ButtonShape.CIRCLE,
                    action      = PadAction.TrackpointMove(tpSize),
                )
                p = p.copy(buttons = p.buttons + tpButton, hasTrackpoint = false)
            }
            // Step 2: Migrate legacy profile.buttons → first PadLayout.
            if (p.layouts.isEmpty()) {
                needsSave = true
                val layoutId = UUID.randomUUID().toString()
                p = p.copy(
                    layouts        = listOf(PadLayout(id = layoutId, name = p.name, buttons = p.buttons)),
                    activeLayoutId = layoutId,
                )
            }
            p
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

    // -------------------------------------------------------------------------
    // Layout CRUD (operates on the active profile's layout list)
    // -------------------------------------------------------------------------

    fun setActiveLayoutId(layoutId: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "setActiveLayoutId: $layoutId in profile ${profile.id}")
        updateProfile(profile.copy(activeLayoutId = layoutId))
    }

    fun addLayout(layout: PadLayout) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "addLayout: id=${layout.id} name='${layout.name}'")
        updateProfile(profile.copy(
            layouts        = profile.layouts + layout,
            activeLayoutId = layout.id,
        ))
    }

    fun updateLayout(layout: PadLayout) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "updateLayout: id=${layout.id}")
        updateProfile(profile.copy(
            layouts = profile.layouts.map { if (it.id == layout.id) layout else it }
        ))
    }

    fun deleteLayout(layoutId: String) {
        val profile = activeProfile.value ?: return
        val remaining = profile.layouts.filter { it.id != layoutId }
        if (remaining.isEmpty()) {
            AppLog.w(TAG, "deleteLayout: refused — cannot delete the last layout")
            return
        }
        val newActiveId = if (profile.activeLayoutId == layoutId) remaining.firstOrNull()?.id
                          else profile.activeLayoutId
        AppLog.d(TAG, "deleteLayout: $layoutId → newActive=$newActiveId")
        updateProfile(profile.copy(layouts = remaining, activeLayoutId = newActiveId))
    }

    fun reorderLayouts(newLayouts: List<PadLayout>) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "reorderLayouts: ${newLayouts.size} layouts")
        updateProfile(profile.copy(layouts = newLayouts))
    }

    fun setLayoutEnabled(layoutId: String, enabled: Boolean) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "setLayoutEnabled: $layoutId → $enabled")
        updateProfile(profile.copy(
            layouts = profile.layouts.map { if (it.id == layoutId) it.copy(enabled = enabled) else it }
        ))
    }

    fun previousLayout() {
        val profile = activeProfile.value ?: return
        val enabled = profile.layouts.filter { it.enabled }
        if (enabled.size <= 1) return
        val currentId = profile.activeLayoutId ?: profile.layouts.firstOrNull()?.id
        val currentIdx = enabled.indexOfFirst { it.id == currentId }
        val prevId = enabled[(currentIdx - 1 + enabled.size) % enabled.size].id
        AppLog.d(TAG, "previousLayout → $prevId")
        updateProfile(profile.copy(activeLayoutId = prevId))
    }

    fun nextLayout() {
        val profile = activeProfile.value ?: return
        val enabled = profile.layouts.filter { it.enabled }
        if (enabled.size <= 1) return
        val currentId = profile.activeLayoutId ?: profile.layouts.firstOrNull()?.id
        val currentIdx = enabled.indexOfFirst { it.id == currentId }
        val nextId = enabled[(currentIdx + 1) % enabled.size].id
        AppLog.d(TAG, "nextLayout → $nextId")
        updateProfile(profile.copy(activeLayoutId = nextId))
    }

    // -------------------------------------------------------------------------
    // Per-profile Macro CRUD
    // -------------------------------------------------------------------------

    fun addMacro(macro: Macro) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "addMacro: id=${macro.id} name='${macro.name}'")
        updateProfile(profile.copy(macros = profile.macros + macro))
    }

    fun updateMacro(macro: Macro) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "updateMacro: id=${macro.id}")
        updateProfile(profile.copy(
            macros = profile.macros.map { if (it.id == macro.id) macro else it }
        ))
    }

    fun deleteMacro(macroId: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "deleteMacro: $macroId")
        updateProfile(profile.copy(macros = profile.macros.filter { it.id != macroId }))
    }

    fun renameMacro(macroId: String, newName: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "renameMacro: $macroId → '$newName'")
        updateProfile(profile.copy(
            macros = profile.macros.map { if (it.id == macroId) it.copy(name = newName) else it }
        ))
    }

    fun reorderMacros(newList: List<Macro>) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "reorderMacros: ${newList.size} macros")
        updateProfile(profile.copy(macros = newList))
    }
}
