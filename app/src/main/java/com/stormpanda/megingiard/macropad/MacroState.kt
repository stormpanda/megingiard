package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global macro library — macros are independent of profiles and shared across all
 * [PadProfile] instances. Any profile can reference any macro via [PadAction.Macro].
 *
 * Follows the project-wide singleton-state pattern: private [MutableStateFlow] backing
 * fields, read-only [StateFlow] exposed to UI. Every mutation triggers
 * [SettingsManager.saveMacroData] (or [SettingsManager.saveMacroFolderData]) for
 * DataStore persistence.
 */
object MacroState {

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _folders = MutableStateFlow<List<MacroFolder>>(emptyList())
    val folders: StateFlow<List<MacroFolder>> = _folders.asStateFlow()

    // -------------------------------------------------------------------------
    // Initialization (called by SettingsManager.init)
    // -------------------------------------------------------------------------

    internal fun loadFrom(macros: List<Macro>) {
        _macros.value = macros
    }

    internal fun loadFoldersFrom(folders: List<MacroFolder>) {
        _folders.value = folders
    }

    // -------------------------------------------------------------------------
    // Macro CRUD
    // -------------------------------------------------------------------------

    fun addMacro(macro: Macro) {
        _macros.value = _macros.value + macro
        SettingsManager.saveMacroData()
    }

    fun updateMacro(macro: Macro) {
        _macros.value = _macros.value.map { if (it.id == macro.id) macro else it }
        SettingsManager.saveMacroData()
    }

    fun deleteMacro(macroId: String) {
        _macros.value = _macros.value.filter { it.id != macroId }
        SettingsManager.saveMacroData()
    }

    fun renameMacro(macroId: String, newName: String) {
        _macros.value = _macros.value.map {
            if (it.id == macroId) it.copy(name = newName) else it
        }
        SettingsManager.saveMacroData()
    }

    /** Replaces the entire macro list (used for flat full-list reorder — legacy callers). */
    fun reorderMacros(newList: List<Macro>) {
        _macros.value = newList
        SettingsManager.saveMacroData()
    }

    /**
     * Reorders macros within a single folder/section without disturbing macros in
     * other sections. [reorderedSection] must contain the same macros that currently
     * belong to [folderId], just in a different order.
     */
    fun reorderMacrosInFolder(folderId: String?, reorderedSection: List<Macro>) {
        val current = _macros.value.toMutableList()
        val indices = current.mapIndexedNotNull { i, m -> if (m.folderId == folderId) i else null }
        indices.zip(reorderedSection).forEach { (idx, macro) -> current[idx] = macro }
        _macros.value = current
        SettingsManager.saveMacroData()
    }

    /** Moves [macroId] to [folderId] (pass `null` for the "Unassigned" section). */
    fun moveMacroToFolder(macroId: String, folderId: String?) {
        _macros.value = _macros.value.map { if (it.id == macroId) it.copy(folderId = folderId) else it }
        SettingsManager.saveMacroData()
    }

    // -------------------------------------------------------------------------
    // Folder CRUD
    // -------------------------------------------------------------------------

    fun addFolder(folder: MacroFolder) {
        _folders.value = _folders.value + folder
        SettingsManager.saveMacroFolderData()
    }

    fun renameFolder(id: String, newName: String) {
        _folders.value = _folders.value.map { if (it.id == id) it.copy(name = newName) else it }
        SettingsManager.saveMacroFolderData()
    }

    /**
     * Deletes the folder [folderId] and moves all macros that belonged to it to the
     * "Unassigned" section (sets their [Macro.folderId] to `null`).
     */
    fun deleteFolder(folderId: String) {
        _macros.value = _macros.value.map { if (it.folderId == folderId) it.copy(folderId = null) else it }
        _folders.value = _folders.value.filter { it.id != folderId }
        SettingsManager.saveMacroData()
        SettingsManager.saveMacroFolderData()
    }

    fun reorderFolders(newList: List<MacroFolder>) {
        _folders.value = newList
        SettingsManager.saveMacroFolderData()
    }
}

