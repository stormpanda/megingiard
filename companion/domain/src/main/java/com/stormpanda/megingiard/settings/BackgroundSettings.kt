package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BackgroundSettings"

/**
 * MacroPad ambient-display global default settings: master enable, dim,
 * preview toggle, and apply-theme flag. Persists to the shared DataStore owned by [SettingsManager].
 *
 * Note: Per-layout ambient overrides live in [com.stormpanda.megingiard.macropad.MacroPadState];
 * this object only owns the **global defaults** that the layout editor reads from.
 *
 * Lifecycle: see [KeyboardSettings] — same `init(dataStore, scope)` + `loadFrom(prefs)` pattern.
 *
 * `updateXxxLive` setters mutate the in-memory [StateFlow] only and skip DataStore — safe to
 * call on every drag frame from a slider. The corresponding `setXxx` is called once on
 * pointer-up to commit.
 */
object BackgroundSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    private val _macropadBackgroundDim = MutableStateFlow(0f)
    val macropadBackgroundDim: StateFlow<Float> = _macropadBackgroundDim.asStateFlow()

    private val _macropadBackgroundPreview = MutableStateFlow(false)
    val macropadBackgroundPreview: StateFlow<Boolean> = _macropadBackgroundPreview.asStateFlow()

    private val _macropadBackgroundApplyTheme = MutableStateFlow(false)
    val macropadBackgroundApplyTheme: StateFlow<Boolean> = _macropadBackgroundApplyTheme.asStateFlow()

    internal fun init(
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _macropadBackgroundDim.value = prefs[KEY_MACROPAD_AMBIENT_DIM] ?: 0f
        _macropadBackgroundPreview.value = prefs[KEY_MACROPAD_AMBIENT_PREVIEW] ?: false
        _macropadBackgroundApplyTheme.value = prefs[KEY_MACROPAD_AMBIENT_APPLY_THEME] ?: false
    }

    fun setMacropadBackgroundDim(value: Float) {
        AppLog.d(TAG, "setMacropadBackgroundDim($value)")
        _macropadBackgroundDim.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_DIM] = value } }
    }

    /** Updates the background dim level in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadBackgroundDimLive(value: Float) {
        _macropadBackgroundDim.value = value
    }

    fun setMacropadBackgroundPreview(value: Boolean) {
        AppLog.d(TAG, "setMacropadBackgroundPreview($value)")
        _macropadBackgroundPreview.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_PREVIEW] = value } }
    }

    fun setMacropadBackgroundApplyTheme(value: Boolean) {
        AppLog.d(TAG, "setMacropadBackgroundApplyTheme($value)")
        _macropadBackgroundApplyTheme.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_APPLY_THEME] = value } }
    }
}
