package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.mirror.ScreenCutout
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
import java.util.Locale
import java.util.UUID

private const val TAG = "MacroPadState"
private const val MP_DEFAULT_PROFILE_NAME = "Profile"
private const val MP_DEFAULT_LAYOUT_NAME = "Layout"
private const val DEFAULT_PROFILE_NAME = "Default"
private const val DUPLICATE_BUTTON_OFFSET = 0.05f
private const val MP_LEGACY_BG_ALPHA_MIGRATION = 0xB3 // 70% opacity in hex (matching previous default resting level)

private fun defaultInitialName(): String =
    when (Locale.getDefault().language.lowercase()) {
        "zh" -> "預設"
        "de" -> "Standard"
        else -> "Default"
    }

private fun List<String>.nextUniqueName(
    baseName: String,
    fallback: String,
): String {
    val normalizedBase = baseName.trim().ifBlank { fallback }
    if (none { it.equals(normalizedBase, ignoreCase = true) }) return normalizedBase
    var index = 2
    while (true) {
        val candidate = "$normalizedBase ($index)"
        if (none { it.equals(candidate, ignoreCase = true) }) return candidate
        index += 1
    }
}

private fun migrateButtonBgColorOption(option: ColorOption?): ColorOption? {
    if (option is ColorOption.Custom) {
        val alpha = (option.argb ushr 24) and 0xFF
        // If alpha is full opacity (0xFF / 1.0f) from pre-opacity versions,
        // migrate it to the documented previous resting default 0.70f (0xB3).
        if (alpha == 0xFF) {
            val migratedArgb = (MP_LEGACY_BG_ALPHA_MIGRATION shl 24) or (option.argb and 0x00FFFFFF)
            return ColorOption.Custom(migratedArgb)
        }
    }
    return option
}

/**
 * Recomputes [PadProfile.enableKeyboard], [PadProfile.enableGamepad], [PadProfile.enableMouse],
 * and [PadProfile.enableTouch] from button actions across **all layouts** so the injectors
 * are started only when needed.
 */
private fun PadProfile.withSyncedDeviceFlags(): PadProfile {
    val allActions = layouts.flatMap { it.buttons }.map { it.action }
    val hasMacro = allActions.any { it is PadAction.Macro }
    val kb = hasMacro || allActions.any { it is PadAction.KeyboardKey }
    val gp = hasMacro || allActions.any { it is PadAction.GamepadButton }
    val ms =
        hasMacro ||
            allActions.any {
                it is PadAction.MouseButton ||
                    it is PadAction.ScrollWheel ||
                    (it is PadAction.TrackpointMove && it.mode == TrackpointMode.PHYSICAL_MOUSE)
            } || layouts.any { it.backgroundTouchpad.enabled }
    val ts =
        hasMacro ||
            allActions.any {
                it is PadAction.TrackpointMove && it.mode == TrackpointMode.VIRTUAL_TOUCH
            }
    return if (enableKeyboard == kb && enableGamepad == gp && enableMouse == ms && enableTouch == ts) {
        this
    } else {
        copy(enableKeyboard = kb, enableGamepad = gp, enableMouse = ms, enableTouch = ts)
    }
}

private fun List<PadButton>.cloneWithMacroMapping(macroMapping: Map<String, String>): List<PadButton> =
    map { btn ->
        val updatedAction =
            when (val action = btn.action) {
                is PadAction.Macro -> {
                    val newMacroId = macroMapping[action.macroId] ?: action.macroId
                    PadAction.Macro(newMacroId)
                }

                else -> {
                    action
                }
            }
        btn.copy(
            id = UUID.randomUUID().toString(),
            action = updatedAction,
        )
    }

/**
 * Runtime state holder for the MacroPad-centric UI.
 *
 * Manages profiles (each containing multiple [PadLayout]s and [Macro]s)
 * and the currently active profile + layout.
 *
 * Persistence is delegated to [MacroPadSettings] — all mutators trigger
 * [MacroPadSettings.saveMacroPadData] after updating the in-memory state.
 *
 * [MacroPadSettings] calls [loadFrom] once during its init phase to restore
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

    private val _previewLayout = MutableStateFlow<PadLayout?>(null)
    val previewLayout: StateFlow<PadLayout?> = _previewLayout.asStateFlow()

    private val _activeProfile = MutableStateFlow<PadProfile?>(null)
    val activeProfile: StateFlow<PadProfile?> = _activeProfile.asStateFlow()

    private val _activeLayout = MutableStateFlow<PadLayout?>(null)
    val activeLayout: StateFlow<PadLayout?> = _activeLayout.asStateFlow()

    private fun recomputeActiveState() {
        val ps = _profiles.value
        val profId = _activeProfileId.value
        val prof = ps.firstOrNull { it.id == profId } ?: ps.firstOrNull()
        _activeProfile.value = prof

        val preview = _previewLayout.value
        _activeLayout.value =
            if (preview != null) {
                preview
            } else if (prof == null) {
                null
            } else {
                val layId = prof.activeLayoutId
                prof.layouts.firstOrNull { it.id == layId } ?: prof.layouts.firstOrNull()
            }
    }

    fun setPreviewLayout(layout: PadLayout?) {
        AppLog.d(TAG, "setPreviewLayout(${layout?.id}, name='${layout?.name}')")
        _previewLayout.value = layout
        recomputeActiveState()
    }

    fun clearPreviewLayout() {
        AppLog.d(TAG, "clearPreviewLayout()")
        _previewLayout.value = null
        _isCroppingBackground.value = false
        recomputeActiveState()
    }

    fun setPreviewButton(button: PadButton?) {
        if (button == null) {
            clearPreviewLayout()
            return
        }
        val currentProfile = activeProfile.value ?: return
        val currentActiveLayout =
            currentProfile.layouts.firstOrNull { it.id == currentProfile.activeLayoutId }
                ?: currentProfile.layouts.firstOrNull() ?: return
        val isExisting = currentActiveLayout.buttons.any { it.id == button.id }
        val updatedButtons =
            if (isExisting) {
                currentActiveLayout.buttons.map { if (it.id == button.id) button else it }
            } else {
                currentActiveLayout.buttons + button
            }
        setPreviewLayout(currentActiveLayout.copy(buttons = updatedButtons))
    }

    fun updatePreviewBackgroundCrop(
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val current = _previewLayout.value ?: return
        _previewLayout.value =
            current.copy(
                bgImageScale = scale,
                bgImageOffsetX = offsetX,
                bgImageOffsetY = offsetY,
            )
        recomputeActiveState()
    }

    private val _selectedButtonId = MutableStateFlow<String?>(null)
    val selectedButtonId: StateFlow<String?> = _selectedButtonId.asStateFlow()

    fun setSelectedButtonId(id: String?) {
        AppLog.d(TAG, "setSelectedButtonId($id)")
        _selectedButtonId.value = id
    }

    private val _isEditingButtonPositions = MutableStateFlow(false)
    val isEditingButtonPositions: StateFlow<Boolean> = _isEditingButtonPositions.asStateFlow()

    fun setEditingButtonPositions(editing: Boolean) {
        AppLog.d(TAG, "setEditingButtonPositions($editing)")
        _isEditingButtonPositions.value = editing
        if (!editing) {
            _selectedButtonId.value = null
        }
    }

    private val _isCroppingBackground = MutableStateFlow(false)
    val isCroppingBackground: StateFlow<Boolean> = _isCroppingBackground.asStateFlow()

    fun setCroppingBackground(cropping: Boolean) {
        AppLog.d(TAG, "setCroppingBackground($cropping)")
        _isCroppingBackground.value = cropping
    }

    private val _gridMode = MutableStateFlow(GridMode.OFF)
    val gridMode: StateFlow<GridMode> = _gridMode.asStateFlow()

    fun setGridMode(mode: GridMode) {
        AppLog.d(TAG, "setGridMode($mode)")
        _gridMode.value = mode
    }

    /**
     * Resolves the best matching profile for a given package name, optional ROM path, and system ID.
     * Prefers specific ROM-file profile matches over generic package profiles.
     */
    fun findBestMatchingProfile(
        packageName: String?,
        romPath: String? = null,
        systemId: String? = null,
    ): PadProfile? {
        val pkg = packageName?.trim() ?: return null
        if (pkg.isBlank()) return null
        val currentProfiles = _profiles.value
        return currentProfiles.firstOrNull { profile ->
            profile.association?.romFileName != null &&
                profile.matches(pkg, romPath, systemId)
        } ?: currentProfiles.firstOrNull { profile ->
            profile.association?.romFileName == null &&
                profile.matches(pkg, romPath, systemId)
        }
    }

    /**
     * Returns the designated default profile (`isDefault == true`), or the profile named "Default"
     * (case-insensitive), or the first profile in the list as a fallback.
     */
    fun getDefaultOrFirstProfile(): PadProfile? {
        val currentProfiles = _profiles.value
        return currentProfiles.firstOrNull { it.isDefault }
            ?: currentProfiles.firstOrNull { it.name.equals(DEFAULT_PROFILE_NAME, ignoreCase = true) }
            ?: currentProfiles.firstOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load hook (called by MacroPadSettings.loadFrom)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Restores profiles from persistence. Ensures every profile has at least
     * one layout and syncs device flags.
     *
     * @param profiles         Deserialized profiles from DataStore.
     * @param activeProfileId  Persisted active profile ID.
     */
    fun loadFrom(
        profiles: List<PadProfile>,
        activeProfileId: String?,
    ) {
        _previewLayout.value = null
        var needsSave = false
        val inputProfiles =
            if (profiles.isEmpty()) {
                needsSave = true
                val defaultId = UUID.randomUUID().toString()
                val defaultLayoutId = UUID.randomUUID().toString()
                val initialName = defaultInitialName()
                listOf(
                    PadProfile(
                        id = defaultId,
                        name = initialName,
                        layouts =
                            listOf(
                                PadLayout(id = defaultLayoutId, name = initialName),
                            ),
                        activeLayoutId = defaultLayoutId,
                    ),
                )
            } else {
                profiles
            }

        val processed =
            inputProfiles.map { profile ->
                var p = profile

                // Ensure every profile has at least one layout (guard against
                // malformed data that has no layouts).
                if (p.layouts.isEmpty()) {
                    needsSave = true
                    val layoutId = UUID.randomUUID().toString()
                    p =
                        p.copy(
                            layouts =
                                listOf(
                                    PadLayout(
                                        id = layoutId,
                                        name = p.name,
                                        mirrorConfigured = true,
                                    ),
                                ),
                            activeLayoutId = layoutId,
                        )
                } else {
                    val migratedLayouts =
                        p.layouts.map { layout ->
                            var current = layout
                            var changed = false
                            @Suppress("DEPRECATION")
                            if (current.buttonColorNoMirror != null || current.buttonColorMirror != null) {
                                needsSave = true
                                changed = true
                                current =
                                    current.copy(
                                        buttonTextColor = ColorOption.Neutral,
                                        buttonBorderColor = ColorOption.Neutral,
                                        buttonBgColor = ColorOption.Neutral,
                                        buttonColorNoMirror = null,
                                        buttonColorMirror = null,
                                    )
                            }
                            val migratedBg = migrateButtonBgColorOption(current.buttonBgColor) ?: current.buttonBgColor
                            if (migratedBg != current.buttonBgColor) {
                                needsSave = true
                                changed = true
                                current = current.copy(buttonBgColor = migratedBg)
                            }
                            val migratedButtons =
                                current.buttons.map { btn ->
                                    val btnMigratedBg = migrateButtonBgColorOption(btn.buttonBgColor)
                                    if (btnMigratedBg != btn.buttonBgColor) {
                                        needsSave = true
                                        changed = true
                                        btn.copy(buttonBgColor = btnMigratedBg)
                                    } else {
                                        btn
                                    }
                                }
                            if (migratedButtons != current.buttons) {
                                needsSave = true
                                changed = true
                                current = current.copy(buttons = migratedButtons)
                            }
                            if (!current.mirrorConfigured) {
                                needsSave = true
                                current.copy(mirrorConfigured = true)
                            } else if (changed) {
                                current
                            } else {
                                layout
                            }
                        }
                    p = p.copy(layouts = migratedLayouts)
                }

                p
            }

        val withFlags =
            processed.map { profile ->
                profile.withSyncedDeviceFlags().also { synced ->
                    if (synced != profile) needsSave = true
                }
            }

        _profiles.value = withFlags

        val resolvedActiveId =
            if (activeProfileId == null || withFlags.none { it.id == activeProfileId }) {
                needsSave = true
                withFlags.firstOrNull()?.id
            } else {
                activeProfileId
            }
        _activeProfileId.value = resolvedActiveId
        recomputeActiveState()

        AppLog.d(TAG, "loadFrom: ${withFlags.size} profiles, activeId=$resolvedActiveId, needsSave=$needsSave")
        if (needsSave) MacroPadSettings.saveMacroPadData()
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
        recomputeActiveState()
        MacroPadSettings.saveMacroPadData()
    }

    fun updateProfile(profile: PadProfile) {
        val synced = profile.withSyncedDeviceFlags()
        AppLog.d(TAG, "updateProfile id=${synced.id} (kb=${synced.enableKeyboard} gp=${synced.enableGamepad} ms=${synced.enableMouse})")
        _profiles.value = _profiles.value.map { if (it.id == synced.id) synced else it }
        recomputeActiveState()
        MacroPadSettings.saveMacroPadData()
    }

    fun deleteProfile(profileId: String) {
        val remaining = _profiles.value.filter { it.id != profileId }
        _profiles.value = remaining
        if (_activeProfileId.value == profileId) {
            _activeProfileId.value = remaining.firstOrNull()?.id
        }
        recomputeActiveState()
        AppLog.d(TAG, "deleteProfile id=$profileId → activeId=${_activeProfileId.value}")
        MacroPadSettings.saveMacroPadData()
    }

    fun renameProfile(
        profileId: String,
        newName: String,
    ) {
        val existing = _profiles.value.firstOrNull { it.id == profileId }
        val association = existing?.association
        renameProfile(profileId, newName, association)
    }

    fun renameProfile(
        profileId: String,
        newName: String,
        association: ProfileAssociation?,
    ) {
        val existingNames =
            _profiles.value
                .filter { it.id != profileId }
                .map { it.name }
        val desiredName = newName.trim().ifBlank { MP_DEFAULT_PROFILE_NAME }
        val uniqueName = existingNames.nextUniqueName(desiredName, MP_DEFAULT_PROFILE_NAME)
        if (uniqueName != desiredName) {
            AppLog.w(TAG, "renameProfile: duplicate profile name '$desiredName' adjusted to '$uniqueName'")
        }
        AppLog.d(TAG, "renameProfile id=$profileId name='$uniqueName' association='$association'")
        _profiles.value =
            _profiles.value.map {
                if (it.id == profileId) it.copy(name = uniqueName, association = association) else it
            }
        recomputeActiveState()
        MacroPadSettings.saveMacroPadData()
    }

    fun setActiveProfileId(id: String?) {
        AppLog.i(TAG, "setActiveProfileId: $id")
        _previewLayout.value = null
        _activeProfileId.value = id
        recomputeActiveState()
        MacroPadSettings.saveMacroPadData()
    }

    /** Deletes all profiles and creates a single blank default profile. */
    fun restoreDefaults() {
        val defaultId = UUID.randomUUID().toString()
        val defaultLayoutId = UUID.randomUUID().toString()
        val initialName = defaultInitialName()
        val defaultProfile =
            PadProfile(
                id = defaultId,
                name = initialName,
                layouts = listOf(PadLayout(id = defaultLayoutId, name = initialName)),
                activeLayoutId = defaultLayoutId,
            )
        _profiles.value = listOf(defaultProfile)
        _activeProfileId.value = defaultId
        recomputeActiveState()
        AppLog.i(TAG, "restoreDefaults: created default profile $defaultId")
        MacroPadSettings.saveMacroPadData()
    }

    fun reorderProfiles(newOrder: List<PadProfile>) {
        AppLog.d(TAG, "reorderProfiles count=${newOrder.size}")
        _profiles.value = newOrder
        recomputeActiveState()
        MacroPadSettings.saveMacroPadData()
    }

    fun duplicateProfile(profileId: String): Map<String, String>? {
        val sourceProfile = _profiles.value.firstOrNull { it.id == profileId } ?: return null
        val existingNames = _profiles.value.map { it.name }
        val uniqueName = existingNames.nextUniqueName(sourceProfile.name, MP_DEFAULT_PROFILE_NAME)

        val newProfileId = UUID.randomUUID().toString()
        val macroMapping = mutableMapOf<String, String>() // source macro ID -> target macro ID
        val layoutMapping = mutableMapOf<String, String>() // source layout ID -> target layout ID

        // Copy macros
        val copiedMacros =
            sourceProfile.macros.map { macro ->
                val targetMacroId = UUID.randomUUID().toString()
                macroMapping[macro.id] = targetMacroId
                macro.copy(id = targetMacroId)
            }

        // Copy layouts
        val copiedLayouts =
            sourceProfile.layouts.map { layout ->
                val targetLayoutId = UUID.randomUUID().toString()
                layoutMapping[layout.id] = targetLayoutId
                layout.copy(
                    id = targetLayoutId,
                    buttons = layout.buttons.cloneWithMacroMapping(macroMapping),
                    backgroundImagePath = layout.backgroundImagePath?.let { "backgrounds/bg_$targetLayoutId" },
                    backgroundImageVersion = 0,
                )
            }

        val newActiveLayoutId =
            sourceProfile.activeLayoutId?.let { activeId ->
                val sourceIndex = sourceProfile.layouts.indexOfFirst { it.id == activeId }
                if (sourceIndex != -1) copiedLayouts[sourceIndex].id else copiedLayouts.firstOrNull()?.id
            } ?: copiedLayouts.firstOrNull()?.id

        val duplicated =
            sourceProfile
                .copy(
                    id = newProfileId,
                    name = uniqueName,
                    layouts = copiedLayouts,
                    macros = copiedMacros,
                    activeLayoutId = newActiveLayoutId,
                    isDefault = false,
                ).withSyncedDeviceFlags()

        AppLog.d(TAG, "duplicateProfile originalId=$profileId newId=$newProfileId name='$uniqueName'")
        _profiles.value = _profiles.value + duplicated
        recomputeActiveState()
        MacroPadSettings.saveMacroPadData()
        return layoutMapping
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout CRUD (within the active profile)
    // ─────────────────────────────────────────────────────────────────────────

    fun setActiveLayoutId(layoutId: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "setActiveLayoutId: profileId=${profile.id} layoutId=$layoutId")
        _previewLayout.value = null
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
        val cutouts = layout.mirrorCutouts
        val normalizedLayout = layout.copy(name = uniqueName, mirrorCutouts = cutouts, mirrorConfigured = true)
        AppLog.d(TAG, "addLayout id=${normalizedLayout.id} name='${normalizedLayout.name}' to profile=${profile.id}")
        updateProfile(
            profile.copy(
                layouts = profile.layouts + normalizedLayout,
                activeLayoutId = normalizedLayout.id,
            ),
        )
    }

    fun updateLayout(layout: PadLayout) {
        val profile = activeProfile.value ?: return
        val withConfigured = if (!layout.mirrorConfigured) layout.copy(mirrorConfigured = true) else layout
        updateProfile(
            profile.copy(
                layouts = profile.layouts.map { if (it.id == withConfigured.id) withConfigured else it },
            ),
        )
    }

    fun deleteLayout(layoutId: String): Boolean {
        val profile = activeProfile.value ?: return false
        val layoutExists = profile.layouts.any { it.id == layoutId }
        if (!layoutExists) {
            AppLog.w(TAG, "deleteLayout id=$layoutId not found in profile=${profile.id}")
            return false
        }
        val remaining = profile.layouts.filter { it.id != layoutId }
        if (remaining.isEmpty()) {
            AppLog.w(TAG, "deleteLayout id=$layoutId rejected: profile=${profile.id} must keep at least one layout")
            return false
        }
        val newActiveId =
            if (profile.activeLayoutId == layoutId) {
                remaining.firstOrNull()?.id
            } else {
                profile.activeLayoutId
            }
        AppLog.d(TAG, "deleteLayout id=$layoutId → activeLayoutId=$newActiveId")
        updateProfile(profile.copy(layouts = remaining, activeLayoutId = newActiveId))
        return true
    }

    fun reorderLayouts(newOrder: List<PadLayout>) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "reorderLayouts count=${newOrder.size}")
        updateProfile(profile.copy(layouts = newOrder))
    }

    fun duplicateLayout(layoutId: String): String? {
        val profile = activeProfile.value ?: return null
        val layout = profile.layouts.firstOrNull { it.id == layoutId } ?: return null
        val existingNames = profile.layouts.map { it.name }
        val uniqueName = existingNames.nextUniqueName(layout.name, MP_DEFAULT_LAYOUT_NAME)

        val copiedButtons =
            layout.buttons.map { btn ->
                btn.copy(id = UUID.randomUUID().toString())
            }

        val copiedCutouts =
            layout.mirrorCutouts.map { cutout ->
                cutout.copy(id = UUID.randomUUID().toString())
            }

        val newLayoutId = UUID.randomUUID().toString()
        val duplicatedLayout =
            layout.copy(
                id = newLayoutId,
                name = uniqueName,
                buttons = copiedButtons,
                mirrorCutouts = copiedCutouts,
                backgroundImagePath = layout.backgroundImagePath?.let { "backgrounds/bg_$newLayoutId" },
                backgroundImageVersion = 0,
            )

        AppLog.d(TAG, "duplicateLayout layoutId=$layoutId newId=${duplicatedLayout.id} name='$uniqueName' in profile=${profile.id}")
        updateProfile(
            profile.copy(
                layouts = profile.layouts + duplicatedLayout,
                activeLayoutId = duplicatedLayout.id,
            ),
        )
        return newLayoutId
    }

    /** Switch to the next layout, wrapping around. */
    fun nextLayout() {
        val profile = activeProfile.value ?: return
        val layouts = profile.layouts
        if (layouts.size <= 1) return
        val currentIndex = layouts.indexOfFirst { it.id == profile.activeLayoutId }
        val nextIndex = (currentIndex + 1) % layouts.size
        AppLog.d(TAG, "nextLayout: ${layouts[nextIndex].name}")
        setActiveLayoutId(layouts[nextIndex].id)
    }

    /** Switch to the previous layout, wrapping around. */
    fun previousLayout() {
        val profile = activeProfile.value ?: return
        val layouts = profile.layouts
        if (layouts.size <= 1) return
        val currentIndex = layouts.indexOfFirst { it.id == profile.activeLayoutId }
        val prevIndex = if (currentIndex <= 0) layouts.size - 1 else currentIndex - 1
        AppLog.d(TAG, "previousLayout: ${layouts[prevIndex].name}")
        setActiveLayoutId(layouts[prevIndex].id)
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
        updateProfile(
            profile.copy(
                macros = profile.macros.map { if (it.id == macro.id) macro else it },
            ),
        )
    }

    fun deleteMacro(macroId: String) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "deleteMacro id=$macroId from profile=${profile.id}")
        updateProfile(profile.copy(macros = profile.macros.filter { it.id != macroId }))
    }

    fun renameMacro(
        macroId: String,
        newName: String,
    ) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "renameMacro id=$macroId name='$newName'")
        updateProfile(
            profile.copy(
                macros =
                    profile.macros.map {
                        if (it.id == macroId) it.copy(name = newName) else it
                    },
            ),
        )
    }

    fun reorderMacros(newOrder: List<Macro>) {
        val profile = activeProfile.value ?: return
        AppLog.d(TAG, "reorderMacros count=${newOrder.size}")
        updateProfile(profile.copy(macros = newOrder))
    }

    /** Copy a macro from any profile into the active profile with a new UUID. */
    fun copyMacroToActiveProfile(macro: Macro) {
        val profile = activeProfile.value ?: return
        val existingNames = profile.macros.map { it.name }
        val uniqueName = existingNames.nextUniqueName(macro.name, "Macro")
        val copied =
            macro.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueName,
            )
        AppLog.d(TAG, "copyMacroToActiveProfile originalId=${macro.id} newId=${copied.id} name='$uniqueName'")
        updateProfile(profile.copy(macros = profile.macros + copied))
    }

    /** Copy a macro to a specific profile, renaming on collision. */
    fun copyMacroToProfile(
        macro: Macro,
        targetProfileId: String,
    ) {
        val targetProfile = _profiles.value.firstOrNull { it.id == targetProfileId } ?: return
        val existingNames = targetProfile.macros.map { it.name }
        val desiredName = macro.name
        val uniqueName = existingNames.nextUniqueName(desiredName, "Macro")
        val copied =
            macro.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueName,
            )
        AppLog.d(TAG, "copyMacroToProfile macroId=${macro.id} targetProfileId=$targetProfileId newId=${copied.id} name='$uniqueName'")
        val updatedProfile = targetProfile.copy(macros = targetProfile.macros + copied)
        updateProfile(updatedProfile)
    }

    /** Copy a layout to a specific profile, cloning buttons and copying referenced macros if cross-profile. */
    fun copyLayoutToProfile(
        layout: PadLayout,
        sourceProfileId: String,
        targetProfileId: String,
    ) {
        val sourceProfile = _profiles.value.firstOrNull { it.id == sourceProfileId } ?: return
        val targetProfile = _profiles.value.firstOrNull { it.id == targetProfileId } ?: return

        val existingNames = targetProfile.layouts.map { it.name }
        val desiredName = layout.name
        val uniqueName = existingNames.nextUniqueName(desiredName, "Layout")

        val macroMapping = mutableMapOf<String, String>() // source macro ID -> target macro ID
        var updatedTargetProfile = targetProfile

        if (sourceProfileId != targetProfileId) {
            val referencedMacroIds = layout.buttons.mapNotNull { (it.action as? PadAction.Macro)?.macroId }.distinct()
            for (macroId in referencedMacroIds) {
                val sourceMacro = sourceProfile.macros.firstOrNull { it.id == macroId }
                if (sourceMacro != null) {
                    val targetExistingMacroNames = updatedTargetProfile.macros.map { it.name }
                    val targetMacroName = targetExistingMacroNames.nextUniqueName(sourceMacro.name, "Macro")
                    val targetMacroId = UUID.randomUUID().toString()
                    val copiedMacro =
                        sourceMacro.copy(
                            id = targetMacroId,
                            name = targetMacroName,
                        )
                    macroMapping[macroId] = targetMacroId
                    updatedTargetProfile = updatedTargetProfile.copy(macros = updatedTargetProfile.macros + copiedMacro)
                    AppLog.d(
                        TAG,
                        "copyLayoutToProfile: copied referenced macro sourceId=$macroId targetId=$targetMacroId name='$targetMacroName'",
                    )
                }
            }
        }

        val copiedCutouts =
            layout.mirrorCutouts.map { cutout ->
                cutout.copy(id = UUID.randomUUID().toString())
            }

        val copiedLayout =
            layout.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueName,
                buttons = layout.buttons.cloneWithMacroMapping(macroMapping),
                mirrorCutouts = copiedCutouts,
            )

        AppLog.d(TAG, "copyLayoutToProfile layoutId=${layout.id} name='$uniqueName' to profileId=$targetProfileId")
        updatedTargetProfile = updatedTargetProfile.copy(layouts = updatedTargetProfile.layouts + copiedLayout)
        updateProfile(updatedTargetProfile)
    }

    /** Copy a single button to a layout in any profile, copying its referenced macro if cross-profile. */
    fun copyButtonToLayout(
        button: PadButton,
        sourceProfileId: String,
        targetProfileId: String,
        targetLayoutId: String,
    ) {
        val sourceProfile = _profiles.value.firstOrNull { it.id == sourceProfileId } ?: return
        val targetProfile = _profiles.value.firstOrNull { it.id == targetProfileId } ?: return
        val targetLayout = targetProfile.layouts.firstOrNull { it.id == targetLayoutId } ?: return

        var updatedTargetProfile = targetProfile
        var updatedAction = button.action

        if (button.action is PadAction.Macro && sourceProfileId != targetProfileId) {
            val macroId = (button.action as PadAction.Macro).macroId
            val sourceMacro = sourceProfile.macros.firstOrNull { it.id == macroId }
            if (sourceMacro != null) {
                val targetExistingMacroNames = updatedTargetProfile.macros.map { it.name }
                val targetMacroName = targetExistingMacroNames.nextUniqueName(sourceMacro.name, "Macro")
                val targetMacroId = UUID.randomUUID().toString()
                val copiedMacro =
                    sourceMacro.copy(
                        id = targetMacroId,
                        name = targetMacroName,
                    )
                updatedAction = PadAction.Macro(targetMacroId)
                updatedTargetProfile = updatedTargetProfile.copy(macros = updatedTargetProfile.macros + copiedMacro)
                AppLog.d(
                    TAG,
                    "copyButtonToLayout: copied referenced macro sourceId=$macroId targetId=$targetMacroId name='$targetMacroName'",
                )
            }
        }

        val clonedButton =
            button.copy(
                id = UUID.randomUUID().toString(),
                action = updatedAction,
            )

        val updatedLayouts =
            updatedTargetProfile.layouts.map { layout ->
                if (layout.id == targetLayoutId) {
                    layout.copy(buttons = layout.buttons + clonedButton)
                } else {
                    layout
                }
            }

        AppLog.d(TAG, "copyButtonToLayout buttonId=${button.id} to profileId=$targetProfileId layoutId=$targetLayoutId")
        updateProfile(updatedTargetProfile.copy(layouts = updatedLayouts))
    }

    private inline fun updateLayoutInProfiles(
        layoutId: String,
        transform: (PadLayout) -> PadLayout?,
    ): Boolean {
        var changed = false
        val updatedProfiles =
            _profiles.value.map { profile ->
                var profileChanged = false
                val updatedLayouts =
                    profile.layouts.map { layout ->
                        if (layout.id != layoutId) return@map layout
                        val updated = transform(layout)
                        if (updated != null && updated != layout) {
                            changed = true
                            profileChanged = true
                            updated
                        } else {
                            layout
                        }
                    }
                if (profileChanged) profile.copy(layouts = updatedLayouts) else profile
            }
        if (changed) {
            _profiles.value = updatedProfiles
            recomputeActiveState()
            MacroPadSettings.saveMacroPadData()
        }
        return changed
    }

    /** Duplicate a button in the current layout, shifting its position slightly to prevent perfect overlap. */
    fun duplicateButtonInLayout(
        button: PadButton,
        layoutId: String,
    ) {
        val newPosX = (button.posX + DUPLICATE_BUTTON_OFFSET).coerceIn(0f, 1f)
        val newPosY = (button.posY + DUPLICATE_BUTTON_OFFSET).coerceIn(0f, 1f)
        val clonedButton =
            button.copy(
                id = UUID.randomUUID().toString(),
                posX = newPosX,
                posY = newPosY,
            )

        val changed =
            updateLayoutInProfiles(layoutId) { layout ->
                layout.copy(buttons = layout.buttons + clonedButton)
            }
        if (changed) {
            AppLog.d(TAG, "duplicateButtonInLayout buttonId=${button.id} layoutId=$layoutId offset to ($newPosX, $newPosY)")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mirror viewport persistence (per-layout)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Saves the current mirror viewport (scale and offsets) into the active layout's
     * [PadLayout.mirrorSavedScale/X/Y] fields and persists via [MacroPadSettings.saveMacroPadData].
     *
     * Called by [MirrorViewportController] after a debounce when [com.stormpanda.megingiard.settings.MirrorSettings.rememberViewport] is enabled.
     * A no-op if no active layout is found or the values are unchanged.
     */
    fun saveMirrorViewport(
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
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
    fun saveMirrorViewport(
        layoutId: String,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val changed =
            updateLayoutInProfiles(layoutId) { layout ->
                if (layout.mirrorSavedScale == scale &&
                    layout.mirrorSavedOffsetX == offsetX &&
                    layout.mirrorSavedOffsetY == offsetY
                ) {
                    null
                } else {
                    layout.copy(
                        mirrorSavedScale = scale,
                        mirrorSavedOffsetX = offsetX,
                        mirrorSavedOffsetY = offsetY,
                        mirrorConfigured = true,
                    )
                }
            }
        if (changed) {
            AppLog.d(TAG, "saveMirrorViewport layoutId=$layoutId scale=$scale offset=($offsetX,$offsetY)")
        }
    }

    fun saveMirrorCutouts(
        layoutId: String,
        cutouts: List<ScreenCutout>,
    ) {
        val changed =
            updateLayoutInProfiles(layoutId) { layout ->
                if (layout.mirrorCutouts == cutouts && layout.mirrorConfigured) {
                    null
                } else {
                    layout.copy(mirrorCutouts = cutouts, mirrorConfigured = true)
                }
            }
        if (changed) {
            AppLog.d(TAG, "saveMirrorCutouts layoutId=$layoutId count=${cutouts.size}")
        }
    }

    /**
     * Persists the active mirror preference for the specified layout.
     *
     * This is changed only by explicit user start/stop decisions and MediaProjection
     * consent cancellation. Runtime service start/teardown must not mutate it.
     * A no-op if the value is unchanged.
     */
    fun setLayoutMirrorAutoStart(
        layoutId: String,
        value: Boolean,
    ) {
        val changed =
            updateLayoutInProfiles(layoutId) { layout ->
                if (layout.mirrorAutoStart == value) null else layout.copy(mirrorAutoStart = value)
            }
        if (changed) {
            AppLog.d(TAG, "setLayoutMirrorAutoStart layoutId=$layoutId value=$value")
        }
    }

    /**
     * Persists the current mirror follow preference for the specified layout.
     * A no-op if the value is unchanged.
     */
    fun setLayoutMirrorFollowActive(
        layoutId: String,
        value: Boolean,
    ) {
        val changed =
            updateLayoutInProfiles(layoutId) { layout ->
                if (layout.mirrorFollowActive == value) null else layout.copy(mirrorFollowActive = value)
            }
        if (changed) {
            AppLog.d(TAG, "setLayoutMirrorFollowActive layoutId=$layoutId value=$value")
        }
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
