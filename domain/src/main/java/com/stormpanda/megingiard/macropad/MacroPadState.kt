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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val TAG = "MacroPadState"

/**
 * Recomputes [PadProfile.enableKeyboard], [PadProfile.enableGamepad], and [PadProfile.enableMouse]
 * from button actions across **all layouts** so the injectors are started only when needed.
 */
private fun PadProfile.withSyncedDeviceFlags(): PadProfile {
    val allButtons = layouts.flatMap { it.buttons }
    val hasMacro = allButtons.any { it.action is PadAction.Macro }
    val kb = hasMacro || allButtons.any {
        it.action is PadAction.KeyboardKey ||
        it.action is PadAction.FullScreenKeyboard
    }
    val gp = hasMacro || allButtons.any { it.action is PadAction.GamepadButton }
    val ms = hasMacro || allButtons.any {
        it.action is PadAction.MouseButton    ||
        it.action is PadAction.ScrollWheel    ||
        it.action is PadAction.TrackpointMove ||
        it.action is PadAction.FullScreenMouse ||
        it.action is PadAction.MirrorTouchProjection ||
        it.action is PadAction.MouseLeftClick ||
        it.action is PadAction.MouseRightClick
    }
    return if (enableKeyboard == kb && enableGamepad == gp && enableMouse == ms) this
    else copy(enableKeyboard = kb, enableGamepad = gp, enableMouse = ms)
}

/**
 * Runtime state holder for the MacroPad-centric UI.
 *
 * Manages profiles (each containing multiple [PadLayout]s and [Macro]s)
 * and the currently active profile + layout.
 *
 * Persistence is delegated to [SettingsManager] — all mutators trigger
 * [SettingsManager.saveMacroPadData] after updating the in-memory state.
 *
 * [SettingsManager] calls [loadFrom] once during its init phase to restore
 * previously persisted profiles.
 */
object MacroPadState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    private val _profiles = MutableStateFlow<List<PadProfile>>(emptyList())
    val profiles: StateFlow<List<PadProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    /** Derived: the currently active profile (falls back to first if ID not found). */
    val activeProfile: StateFlow<PadProfile?> =
        combine(_profiles, _activeProfileId) { ps, id ->
            ps.firstOrNull { it.id == id } ?: ps.firstOrNull()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Derived: the currently active layout within the active profile. */
    val activeLayout: StateFlow<PadLayout?> =
        activeProfile.map { profile ->
            if (profile == null) return@map null
            val layoutId = profile.activeLayoutId
            profile.layouts.firstOrNull { it.id == layoutId }
                ?: profile.layouts.firstOrNull()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // ─────────────────────────────────────────────────────────────────────────
    // Load hook (called by SettingsManager.init)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Restores profiles from persistence. Handles two legacy migration paths:
     * 1. **Trackpoint migration:** Old profiles with `hasTrackpoint=true` → TrackpointMove button.
     * 2. **Layouts migration:** Old profiles with `buttons` but no `layouts` →
     *    single layout wrapping the existing buttons.
     *
     * @param profiles         Deserialized profiles from DataStore.
     * @param activeProfileId  Persisted active profile ID.
     * @param globalMacros     Global macros from the old per-app macro library (migrated into
     *                         each profile that references them). Empty list if already migrated.
     */
    internal fun loadFrom(
        profiles: List<PadProfile>,
        activeProfileId: String?,
        globalMacros: List<Macro> = emptyList(),
    ) {
        var needsSave = false

        val migrated = profiles.map { profile ->
            var p = profile

            // ── Migration 1: trackpoint flags → TrackpointMove button ────────
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
                // Buttons may still be in the legacy `buttons` field at this point.
                p = p.copy(
                    buttons       = p.buttons + tpButton,
                    hasTrackpoint = false,
                )
            }

            // ── Migration 2: flat buttons → single layout ────────────────────
            if (p.layouts.isEmpty() && p.buttons.isNotEmpty()) {
                needsSave = true
                val layoutId = UUID.randomUUID().toString()
                val layout = PadLayout(
                    id      = layoutId,
                    name    = p.name,
                    buttons = p.buttons,
                )
                p = p.copy(
                    layouts       = listOf(layout),
                    activeLayoutId = layoutId,
                    buttons       = emptyList(),
                )
            }

            // ── Migration 3: adopt referenced global macros ──────────────────
            if (globalMacros.isNotEmpty() && p.macros.isEmpty()) {
                val referencedIds = p.layouts
                    .flatMap { it.buttons }
                    .mapNotNull { (it.action as? PadAction.Macro)?.macroId }
                    .toSet()
                val adopted = globalMacros.filter { it.id in referencedIds }
                if (adopted.isNotEmpty()) {
                    needsSave = true
                    p = p.copy(macros = adopted)
                }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Profile CRUD
    // ─────────────────────────────────────────────────────────────────────────

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

    /** Deletes all profiles and creates a single blank default profile. */
    fun restoreDefaults() {
        val defaultId = UUID.randomUUID().toString()
        val defaultLayoutId = UUID.randomUUID().toString()
        val defaultProfile = PadProfile(
            id = defaultId,
            name = "Default",
            layouts = listOf(PadLayout(id = defaultLayoutId, name = "Default")),
            activeLayoutId = defaultLayoutId,
        )
        _profiles.value = listOf(defaultProfile)
        _activeProfileId.value = defaultId
        AppLog.i(TAG, "restoreDefaults: created default profile $defaultId")
        SettingsManager.saveMacroPadData()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout CRUD (within the active profile)
    // ─────────────────────────────────────────────────────────────────────────

    fun setActiveLayoutId(layoutId: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "setActiveLayoutId: profileId=${profile.id} layoutId=$layoutId")
        updateProfile(profile.copy(activeLayoutId = layoutId))
    }

    fun addLayout(layout: PadLayout) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "addLayout id=${layout.id} name='${layout.name}' to profile=${profile.id}")
        updateProfile(profile.copy(
            layouts = profile.layouts + layout,
            activeLayoutId = layout.id,
        ))
    }

    fun updateLayout(layout: PadLayout) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "updateLayout id=${layout.id} name='${layout.name}'")
        updateProfile(profile.copy(
            layouts = profile.layouts.map { if (it.id == layout.id) layout else it },
        ))
    }

    fun deleteLayout(layoutId: String) {
        val profile = activeProfile.value ?: return
        val remaining = profile.layouts.filter { it.id != layoutId }
        if (remaining.isEmpty()) return // must keep at least one layout
        val newActiveId = if (profile.activeLayoutId == layoutId) {
            remaining.firstOrNull()?.id
        } else {
            profile.activeLayoutId
        }
        AppLog.d(TAG, "deleteLayout id=$layoutId → activeLayoutId=$newActiveId")
        updateProfile(profile.copy(layouts = remaining, activeLayoutId = newActiveId))
    }

    fun setLayoutEnabled(layoutId: String, enabled: Boolean) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "setLayoutEnabled id=$layoutId enabled=$enabled")
        updateProfile(profile.copy(
            layouts = profile.layouts.map {
                if (it.id == layoutId) it.copy(enabled = enabled) else it
            },
        ))
    }

    fun reorderLayouts(newOrder: List<PadLayout>) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "reorderLayouts count=${newOrder.size}")
        updateProfile(profile.copy(layouts = newOrder))
    }

    /** Switch to the next enabled layout, wrapping around. */
    fun nextLayout() {
        val profile = activeProfile.value ?: return
        val enabled = profile.layouts.filter { it.enabled }
        if (enabled.size <= 1) return
        val currentIndex = enabled.indexOfFirst { it.id == profile.activeLayoutId }
        val nextIndex = (currentIndex + 1) % enabled.size
        AppLog.d(TAG, "nextLayout: ${enabled[nextIndex].name}")
        setActiveLayoutId(enabled[nextIndex].id)
    }

    /** Switch to the previous enabled layout, wrapping around. */
    fun previousLayout() {
        val profile = activeProfile.value ?: return
        val enabled = profile.layouts.filter { it.enabled }
        if (enabled.size <= 1) return
        val currentIndex = enabled.indexOfFirst { it.id == profile.activeLayoutId }
        val prevIndex = if (currentIndex <= 0) enabled.size - 1 else currentIndex - 1
        AppLog.d(TAG, "previousLayout: ${enabled[prevIndex].name}")
        setActiveLayoutId(enabled[prevIndex].id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-profile Macro CRUD
    // ─────────────────────────────────────────────────────────────────────────

    fun addMacro(macro: Macro) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "addMacro id=${macro.id} name='${macro.name}' to profile=${profile.id}")
        updateProfile(profile.copy(macros = profile.macros + macro))
    }

    fun updateMacro(macro: Macro) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "updateMacro id=${macro.id} name='${macro.name}'")
        updateProfile(profile.copy(
            macros = profile.macros.map { if (it.id == macro.id) macro else it },
        ))
    }

    fun deleteMacro(macroId: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "deleteMacro id=$macroId from profile=${profile.id}")
        updateProfile(profile.copy(macros = profile.macros.filter { it.id != macroId }))
    }

    fun renameMacro(macroId: String, newName: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "renameMacro id=$macroId name='$newName'")
        updateProfile(profile.copy(
            macros = profile.macros.map {
                if (it.id == macroId) it.copy(name = newName) else it
            },
        ))
    }

    /** Copy a macro from any profile into the active profile with a new UUID. */
    fun copyMacroToActiveProfile(macro: Macro) {
        val profile = activeProfile.value ?: return
        val copied = macro.copy(
            id = UUID.randomUUID().toString(),
            name = "${macro.name} (Copy)",
            folderId = null,
        )
        AppLog.d(TAG, "copyMacroToActiveProfile originalId=${macro.id} newId=${copied.id}")
        updateProfile(profile.copy(macros = profile.macros + copied))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient Peek state
    // ─────────────────────────────────────────────────────────────────────────

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
