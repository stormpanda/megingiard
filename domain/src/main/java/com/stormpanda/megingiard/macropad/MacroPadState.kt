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

@Suppress("unused")
private const val TAG = "MacroPadState"
private const val MP_DEFAULT_PROFILE_NAME = "Profile"
private const val MP_DEFAULT_LAYOUT_NAME = "Layout"

private fun List<String>.nextUniqueName(baseName: String, fallback: String): String {
    val normalizedBase = baseName.trim().ifBlank { fallback }
    if (none { it.equals(normalizedBase, ignoreCase = true) }) return normalizedBase
    var index = 2
    while (true) {
        val candidate = "$normalizedBase ($index)"
        if (none { it.equals(candidate, ignoreCase = true) }) return candidate
        index += 1
    }
}

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
        it.action is PadAction.MirrorTouchProjection
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
     * Restores profiles from persistence. Ensures every profile has at least
     * one layout and syncs device flags.
     *
     * @param profiles         Deserialized profiles from DataStore.
     * @param activeProfileId  Persisted active profile ID.
     */
    internal fun loadFrom(
        profiles: List<PadProfile>,
        activeProfileId: String?,
    ) {
        var needsSave = false

        val processed = profiles.map { profile ->
            var p = profile

            // Ensure every profile has at least one layout (guard against
            // malformed data that has no layouts).
            if (p.layouts.isEmpty()) {
                needsSave = true
                val layoutId = UUID.randomUUID().toString()
                p = p.copy(
                    layouts        = listOf(PadLayout(id = layoutId, name = p.name)),
                    activeLayoutId = layoutId,
                )
            }

            p
        }

        val withFlags = processed.map { profile ->
            profile.withSyncedDeviceFlags().also { synced ->
                if (synced != profile) needsSave = true
            }
        }

        _profiles.value = withFlags
        _activeProfileId.value = activeProfileId
        AppLog.d(TAG, "loadFrom: ${withFlags.size} profiles, activeId=$activeProfileId, needsSave=$needsSave")
        if (needsSave) SettingsManager.saveMacroPadData()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profile CRUD
    // ─────────────────────────────────────────────────────────────────────────

    fun addProfile(profile: PadProfile) {
        val existingNames = _profiles.value.map { it.name }
        val desiredName = profile.name.trim().ifBlank { MP_DEFAULT_PROFILE_NAME }
        val uniqueName = existingNames.nextUniqueName(desiredName, MP_DEFAULT_PROFILE_NAME)
        if (uniqueName != desiredName) {
            AppLog.w(TAG, "addProfile: duplicate profile name '$desiredName' adjusted to '$uniqueName'")
        }
        val normalizedProfile = profile.copy(name = uniqueName)
        AppLog.d(TAG, "addProfile id=${normalizedProfile.id} name='${normalizedProfile.name}'")
        _profiles.value = _profiles.value + normalizedProfile
        if (_activeProfileId.value == null) _activeProfileId.value = normalizedProfile.id
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
        val existingNames = _profiles.value
            .filter { it.id != profileId }
            .map { it.name }
        val desiredName = newName.trim().ifBlank { MP_DEFAULT_PROFILE_NAME }
        val uniqueName = existingNames.nextUniqueName(desiredName, MP_DEFAULT_PROFILE_NAME)
        if (uniqueName != desiredName) {
            AppLog.w(TAG, "renameProfile: duplicate profile name '$desiredName' adjusted to '$uniqueName'")
        }
        AppLog.d(TAG, "renameProfile id=$profileId name='$uniqueName'")
        _profiles.value = _profiles.value.map {
            if (it.id == profileId) it.copy(name = uniqueName) else it
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
        val existingNames = profile.layouts.map { it.name }
        val desiredName = layout.name.trim().ifBlank { MP_DEFAULT_LAYOUT_NAME }
        val uniqueName = existingNames.nextUniqueName(desiredName, MP_DEFAULT_LAYOUT_NAME)
        if (uniqueName != desiredName) {
            AppLog.w(TAG, "addLayout: duplicate layout name '$desiredName' adjusted to '$uniqueName'")
        }
        val normalizedLayout = layout.copy(name = uniqueName)
        AppLog.d(TAG, "addLayout id=${normalizedLayout.id} name='${normalizedLayout.name}' to profile=${profile.id}")
        updateProfile(profile.copy(
            layouts = profile.layouts + normalizedLayout,
            activeLayoutId = normalizedLayout.id,
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

    fun reorderMacros(newOrder: List<Macro>) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "reorderMacros count=${newOrder.size}")
        updateProfile(profile.copy(macros = newOrder))
    }

    /** Copy a macro from any profile into the active profile with a new UUID. */
    fun copyMacroToActiveProfile(macro: Macro) {
        val profile = activeProfile.value ?: return
        val copied = macro.copy(
            id = UUID.randomUUID().toString(),
            name = "${macro.name} (Copy)",
        )
        AppLog.d(TAG, "copyMacroToActiveProfile originalId=${macro.id} newId=${copied.id}")
        updateProfile(profile.copy(macros = profile.macros + copied))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mirror viewport persistence (per-layout)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Saves the current mirror viewport (scale and offsets) into the active layout's
     * [PadLayout.mirrorSavedScale/X/Y] fields and persists via [SettingsManager.saveMacroPadData].
     *
     * Called by [MirrorViewportController] after a debounce when [SettingsManager.rememberViewport] is enabled.
     * A no-op if no active layout is found or the values are unchanged.
     */
    fun saveMirrorViewport(scale: Float, offsetX: Float, offsetY: Float) {
        val layoutId = activeLayout.value?.id ?: return
        saveMirrorViewport(layoutId, scale, offsetX, offsetY)
    }

    /**
     * Saves mirror viewport values into the specified layout ID.
     *
     * Used by [MirrorViewportController] to persist the viewport that belongs to
     * the layout which produced the gesture, even if the user switches layouts
     * before a debounce window completes.
     */
    fun saveMirrorViewport(layoutId: String, scale: Float, offsetX: Float, offsetY: Float) {
        var changed = false
        val updatedProfiles = _profiles.value.map { profile ->
            var profileChanged = false
            val updatedLayouts = profile.layouts.map { layout ->
                if (layout.id != layoutId) return@map layout
                if (layout.mirrorSavedScale == scale &&
                    layout.mirrorSavedOffsetX == offsetX &&
                    layout.mirrorSavedOffsetY == offsetY
                ) {
                    layout
                } else {
                    changed = true
                    profileChanged = true
                    layout.copy(
                        mirrorSavedScale = scale,
                        mirrorSavedOffsetX = offsetX,
                        mirrorSavedOffsetY = offsetY,
                    )
                }
            }
            if (profileChanged) profile.copy(layouts = updatedLayouts) else profile
        }
        if (!changed) return
        AppLog.d(TAG, "saveMirrorViewport layoutId=$layoutId scale=$scale offset=($offsetX,$offsetY)")
        _profiles.value = updatedProfiles
        SettingsManager.saveMacroPadData()
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
