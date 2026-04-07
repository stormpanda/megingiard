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
 * [SettingsManager.saveMacroData] for DataStore persistence.
 */
object MacroState {

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    // -------------------------------------------------------------------------
    // Initialization (called by SettingsManager.init)
    // -------------------------------------------------------------------------

    internal fun loadFrom(macros: List<Macro>) {
        _macros.value = macros
    }

    // -------------------------------------------------------------------------
    // CRUD
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
}
