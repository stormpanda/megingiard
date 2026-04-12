package com.stormpanda.megingiard.config

import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroFolder
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroState
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.VignetteShape
import com.stormpanda.megingiard.ui.ThemeMode
import java.util.UUID
import kotlinx.serialization.json.Json

private const val TAG = "ConfigImporter"

private val importJson = Json { ignoreUnknownKeys = true }

/**
 * Parses and applies [MegingiardExport] files.
 *
 * **Conflict resolution:** MacroPad profiles, macros, and folders are always imported
 * side-by-side with fresh UUIDs and an `(Imported)` name suffix. Existing data is never
 * overwritten or deleted. Non-MacroPad settings (global, tool) are applied directly.
 *
 * **Partial import:** Sections that are `null` in [ExportSections] are silently skipped —
 * the current app state for those sections is left untouched.
 */
object ConfigImporter {

    /**
     * Deserializes a JSON string and verifies its [MegingiardExport.checksum].
     *
     * @return [Result.success] with the verified export, or [Result.failure] on
     *         parse error or checksum mismatch.
     */
    fun parseExport(json: String): Result<MegingiardExport> = runCatching {
        val export = importJson.decodeFromString<MegingiardExport>(json)
        if (!ChecksumUtil.verifyChecksum(export)) {
            error("Checksum mismatch — the file may be corrupted or tampered")
        }
        AppLog.i(TAG, "parseExport: OK type=${export.type} schema=${export.schemaVersion}")
        export
    }

    /**
     * Applies all non-null sections from [export] to the running app state.
     * Safe to call on the main thread — all SettingsManager setters are non-blocking.
     */
    fun applyImport(export: MegingiardExport) {
        AppLog.i(TAG, "applyImport: type=${export.type} schema=${export.schemaVersion}")
        val sections = export.sections
        sections.global?.let { applyGlobal(it) }
        sections.mirror?.let { applyMirror(it) }
        sections.touchpad?.let { applyTouchpad(it) }
        sections.keyboard?.let { applyKeyboard(it) }
        sections.macropad?.let { applyMacroPad(it) }
    }

    // ── Section appliers ─────────────────────────────────────────────────────

    private fun applyGlobal(section: GlobalSettingsSection) {
        AppLog.d(TAG, "applyGlobal")
        val sm = SettingsManager

        val parsedEnabled = section.enabledTools
            .mapNotNull { runCatching { AppMode.valueOf(it) }.getOrNull() }
            .toSet()
            .ifEmpty { AppMode.entries.toSet() }
        sm.setEnabledTools(parsedEnabled)

        val parsedOrder = section.toolOrder
            .mapNotNull { runCatching { AppMode.valueOf(it) }.getOrNull() }
        if (parsedOrder.containsAll(AppMode.entries)) {
            sm.setToolOrder(parsedOrder)
        }

        sm.setOverlayTimeoutMs(section.overlayTimeoutMs)
        sm.setOverlayAtBottom(section.overlayAtBottom)

        runCatching { hexToArgb(section.accentColor) }.getOrNull()?.let { argb ->
            sm.setAccentColor(Color(argb))
        }
        ThemeMode.entries.firstOrNull { it.name == section.themeMode }
            ?.let { sm.setThemeMode(it) }
        AppLanguage.entries.firstOrNull { it.name == section.appLanguage }
            ?.let { sm.setAppLanguage(it) }
        AppLog.Level.entries.firstOrNull { it.name == section.logLevel }
            ?.let { sm.setLogLevel(it) }

        sm.setRememberLastTool(section.rememberLastTool)
    }

    private fun applyMirror(section: MirrorSettingsSection) {
        AppLog.d(TAG, "applyMirror")
        val sm = SettingsManager
        sm.setAutoStartCapture(section.autoStartCapture)
        sm.setRememberViewport(section.rememberViewport)
        sm.setRememberLock(section.rememberLock)
        sm.setRememberProjection(section.rememberProjection)
        sm.setPinchWhileProjecting(section.pinchWhileProjecting)
    }

    private fun applyTouchpad(section: TouchpadSettingsSection) {
        AppLog.d(TAG, "applyTouchpad")
        val sm = SettingsManager
        sm.setTouchpadUseMouse(section.useMouse)
        sm.setTouchpadTapToClick(section.tapToClick)
        sm.setTouchpadTwoFingerTap(section.twoFingerTap)
    }

    private fun applyKeyboard(section: KeyboardSettingsSection) {
        AppLog.d(TAG, "applyKeyboard")
        val sm = SettingsManager
        KbLayout.entries.firstOrNull { it.name == section.layout }?.let { sm.setKbLayout(it) }
        sm.setKbTrackpointEnabled(section.trackpointEnabled)
        KbMouseBtnPos.entries.firstOrNull { it.name == section.mouseBtnPos }
            ?.let { sm.setKbMouseBtnPos(it) }
        sm.setKbRepeatEnabled(section.repeatEnabled)
        sm.setKbFullscreen(section.fullscreen)
    }

    private fun applyMacroPad(section: MacroPadSection) {
        AppLog.d(TAG, "applyMacroPad: ${section.profiles.size} profiles, ${section.macros.size} macros, ${section.macroFolders.size} folders")
        applyMacroPadSettings(section.settings)

        // Step 1: import folders with new UUIDs and build old→new ID map
        val folderIdMap = mutableMapOf<String, String>()
        for (folder in section.macroFolders) {
            val newId = UUID.randomUUID().toString()
            folderIdMap[folder.id] = newId
            MacroState.addFolder(MacroFolder(id = newId, name = importedName(folder.name)))
            AppLog.d(TAG, "imported folder '${folder.name}' → $newId")
        }

        // Step 2: import macros with new UUIDs and build old→new ID map
        val macroIdMap = mutableMapOf<String, String>()
        for (macro in section.macros) {
            val newId = UUID.randomUUID().toString()
            macroIdMap[macro.id] = newId
            val newFolderId = macro.folderId?.let { folderIdMap[it] }
            MacroState.addMacro(
                macro.copy(id = newId, name = importedName(macro.name), folderId = newFolderId)
            )
            AppLog.d(TAG, "imported macro '${macro.name}' → $newId")
        }

        // Step 3: import profiles with new UUIDs; remap PadAction.Macro references
        for (profile in section.profiles) {
            val newProfileId = UUID.randomUUID().toString()
            val remappedButtons = profile.buttons.map { button ->
                button.copy(
                    id = UUID.randomUUID().toString(),
                    action = remapMacroAction(button.action, macroIdMap),
                )
            }
            val importedProfile = profile.copy(
                id = newProfileId,
                name = importedName(profile.name),
                buttons = remappedButtons,
            )
            MacroPadState.addProfile(importedProfile)
            AppLog.d(TAG, "imported profile '${profile.name}' → $newProfileId")
        }
    }

    private fun applyMacroPadSettings(settings: MacroPadSettingsSection) {
        val sm = SettingsManager
        sm.setMacropadAmbientEnabled(settings.ambientEnabled)
        sm.setMacropadAmbientDim(settings.ambientDim)
        sm.setMacropadAmbientPreview(settings.ambientPreview)
        sm.setMacropadAmbientApplyTheme(settings.ambientApplyTheme)
        sm.setMacropadAmbientVignetteEnabled(settings.vignetteEnabled)
        VignetteShape.entries.firstOrNull { it.name == settings.vignetteShape }
            ?.let { sm.setMacropadAmbientVignetteShape(it) }
        sm.setMacropadAmbientVignetteVisibleArea(settings.vignetteVisibleArea)
        sm.setMacropadAmbientVignetteTransition(settings.vignetteTransition)
        sm.setMacropadAmbientVignetteOpacity(settings.vignetteOpacity)
        runCatching { hexToArgb(settings.vignetteColor) }.getOrNull()
            ?.let { sm.setMacropadAmbientVignetteColor(it) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * If [action] is a [PadAction.Macro], remaps its [PadAction.Macro.macroId] using [macroIdMap].
     * If the old ID is not in the map (e.g., a profile-only import with no macros), the original
     * ID is preserved — the button will silently no-op if the macro doesn't exist.
     */
    private fun remapMacroAction(action: PadAction, macroIdMap: Map<String, String>): PadAction {
        if (action !is PadAction.Macro) return action
        val newMacroId = macroIdMap[action.macroId] ?: action.macroId
        return PadAction.Macro(macroId = newMacroId)
    }

    /** Appends " (Imported)" to [original] to signal the item came from an import. */
    private fun importedName(original: String) = "$original (Imported)"

    /**
     * Parses a `"#AARRGGBB"` hex string to an ARGB Int.
     * @throws NumberFormatException on malformed input (handled by callers via runCatching).
     */
    private fun hexToArgb(hex: String): Int = hex.removePrefix("#").toLong(16).toInt()
}
