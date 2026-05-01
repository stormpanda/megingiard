package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.VignetteShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AmbientSettings"

/**
 * MacroPad ambient-display global default settings: master enable, dim, vignette
 * (enabled/visibleArea/transition/opacity/color/shape), preview toggle, and
 * apply-theme flag. Persists to the shared DataStore owned by [SettingsManager].
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
object AmbientSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    private val _macropadAmbientEnabled = MutableStateFlow(false)
    val macropadAmbientEnabled: StateFlow<Boolean> = _macropadAmbientEnabled.asStateFlow()

    private val _macropadAmbientDim = MutableStateFlow(0f)
    val macropadAmbientDim: StateFlow<Float> = _macropadAmbientDim.asStateFlow()

    private val _macropadAmbientVignetteEnabled = MutableStateFlow(false)
    val macropadAmbientVignetteEnabled: StateFlow<Boolean> = _macropadAmbientVignetteEnabled.asStateFlow()

    private val _macropadAmbientVignetteVisibleArea = MutableStateFlow(0.7f)
    val macropadAmbientVignetteVisibleArea: StateFlow<Float> = _macropadAmbientVignetteVisibleArea.asStateFlow()

    private val _macropadAmbientVignetteTransition = MutableStateFlow(0.5f)
    val macropadAmbientVignetteTransition: StateFlow<Float> = _macropadAmbientVignetteTransition.asStateFlow()

    private val _macropadAmbientVignetteOpacity = MutableStateFlow(0.6f)
    val macropadAmbientVignetteOpacity: StateFlow<Float> = _macropadAmbientVignetteOpacity.asStateFlow()

    private val _macropadAmbientVignetteColor = MutableStateFlow(0xFF000000.toInt())
    val macropadAmbientVignetteColor: StateFlow<Int> = _macropadAmbientVignetteColor.asStateFlow()

    private val _macropadAmbientVignetteShape = MutableStateFlow(VignetteShape.RADIAL)
    val macropadAmbientVignetteShape: StateFlow<VignetteShape> = _macropadAmbientVignetteShape.asStateFlow()

    private val _macropadAmbientPreview = MutableStateFlow(false)
    val macropadAmbientPreview: StateFlow<Boolean> = _macropadAmbientPreview.asStateFlow()

    private val _macropadAmbientApplyTheme = MutableStateFlow(false)
    val macropadAmbientApplyTheme: StateFlow<Boolean> = _macropadAmbientApplyTheme.asStateFlow()

    internal fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _macropadAmbientEnabled.value = prefs[KEY_MACROPAD_AMBIENT_ENABLED] ?: false
        _macropadAmbientDim.value = prefs[KEY_MACROPAD_AMBIENT_DIM] ?: 0f
        _macropadAmbientVignetteEnabled.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED] ?: false
        _macropadAmbientVignetteVisibleArea.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA] ?: 0.7f
        _macropadAmbientVignetteTransition.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION] ?: 0.5f
        _macropadAmbientVignetteOpacity.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY] ?: 0.6f
        _macropadAmbientVignetteColor.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR] ?: 0xFF000000.toInt()
        _macropadAmbientVignetteShape.value = VignetteShape.entries.firstOrNull { it.name == prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE] } ?: VignetteShape.RADIAL
        _macropadAmbientPreview.value = prefs[KEY_MACROPAD_AMBIENT_PREVIEW] ?: false
        _macropadAmbientApplyTheme.value = prefs[KEY_MACROPAD_AMBIENT_APPLY_THEME] ?: false
    }

    fun setMacropadAmbientEnabled(value: Boolean) {
        AppLog.d(TAG, "setMacropadAmbientEnabled($value)")
        _macropadAmbientEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_ENABLED] = value } }
    }

    fun setMacropadAmbientDim(value: Float) {
        AppLog.d(TAG, "setMacropadAmbientDim($value)")
        _macropadAmbientDim.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_DIM] = value } }
    }

    /** Updates the ambient dim level in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadAmbientDimLive(value: Float) {
        _macropadAmbientDim.value = value
    }

    /** Updates vignette visible area in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadAmbientVignetteVisibleAreaLive(value: Float) {
        _macropadAmbientVignetteVisibleArea.value = value
    }

    /** Updates vignette transition in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadAmbientVignetteTransitionLive(value: Float) {
        _macropadAmbientVignetteTransition.value = value
    }

    /** Updates vignette opacity in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadAmbientVignetteOpacityLive(value: Float) {
        _macropadAmbientVignetteOpacity.value = value
    }

    fun setMacropadAmbientVignetteEnabled(value: Boolean) {
        AppLog.d(TAG, "setMacropadAmbientVignetteEnabled($value)")
        _macropadAmbientVignetteEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED] = value } }
    }

    fun setMacropadAmbientVignetteVisibleArea(value: Float) {
        AppLog.d(TAG, "setMacropadAmbientVignetteVisibleArea($value)")
        _macropadAmbientVignetteVisibleArea.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA] = value } }
    }

    fun setMacropadAmbientVignetteTransition(value: Float) {
        AppLog.d(TAG, "setMacropadAmbientVignetteTransition($value)")
        _macropadAmbientVignetteTransition.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION] = value } }
    }

    fun setMacropadAmbientVignetteOpacity(value: Float) {
        AppLog.d(TAG, "setMacropadAmbientVignetteOpacity($value)")
        _macropadAmbientVignetteOpacity.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY] = value } }
    }

    fun setMacropadAmbientVignetteColor(value: Int) {
        AppLog.d(TAG, "setMacropadAmbientVignetteColor(${value.toString(16)})")
        _macropadAmbientVignetteColor.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR] = value } }
    }

    fun setMacropadAmbientVignetteShape(value: VignetteShape) {
        AppLog.d(TAG, "setMacropadAmbientVignetteShape($value)")
        _macropadAmbientVignetteShape.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE] = value.name } }
    }

    fun setMacropadAmbientPreview(value: Boolean) {
        AppLog.d(TAG, "setMacropadAmbientPreview($value)")
        _macropadAmbientPreview.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_PREVIEW] = value } }
    }

    fun setMacropadAmbientApplyTheme(value: Boolean) {
        AppLog.d(TAG, "setMacropadAmbientApplyTheme($value)")
        _macropadAmbientApplyTheme.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_APPLY_THEME] = value } }
    }
}
