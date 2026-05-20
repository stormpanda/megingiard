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

private const val TAG = "BackgroundSettings"

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
object BackgroundSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    private val _macropadBackgroundEnabled = MutableStateFlow(false)
    val macropadBackgroundEnabled: StateFlow<Boolean> = _macropadBackgroundEnabled.asStateFlow()

    private val _macropadBackgroundDim = MutableStateFlow(0f)
    val macropadBackgroundDim: StateFlow<Float> = _macropadBackgroundDim.asStateFlow()

    private val _macropadBackgroundVignetteEnabled = MutableStateFlow(false)
    val macropadBackgroundVignetteEnabled: StateFlow<Boolean> = _macropadBackgroundVignetteEnabled.asStateFlow()

    private val _macropadBackgroundVignetteVisibleArea = MutableStateFlow(0.7f)
    val macropadBackgroundVignetteVisibleArea: StateFlow<Float> = _macropadBackgroundVignetteVisibleArea.asStateFlow()

    private val _macropadBackgroundVignetteTransition = MutableStateFlow(0.5f)
    val macropadBackgroundVignetteTransition: StateFlow<Float> = _macropadBackgroundVignetteTransition.asStateFlow()

    private val _macropadBackgroundVignetteOpacity = MutableStateFlow(0.6f)
    val macropadBackgroundVignetteOpacity: StateFlow<Float> = _macropadBackgroundVignetteOpacity.asStateFlow()

    private val _macropadBackgroundVignetteColor = MutableStateFlow(0xFF000000.toInt())
    val macropadBackgroundVignetteColor: StateFlow<Int> = _macropadBackgroundVignetteColor.asStateFlow()

    private val _macropadBackgroundVignetteShape = MutableStateFlow(VignetteShape.RADIAL)
    val macropadBackgroundVignetteShape: StateFlow<VignetteShape> = _macropadBackgroundVignetteShape.asStateFlow()

    private val _macropadBackgroundPreview = MutableStateFlow(false)
    val macropadBackgroundPreview: StateFlow<Boolean> = _macropadBackgroundPreview.asStateFlow()

    private val _macropadBackgroundApplyTheme = MutableStateFlow(false)
    val macropadBackgroundApplyTheme: StateFlow<Boolean> = _macropadBackgroundApplyTheme.asStateFlow()

    internal fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _macropadBackgroundEnabled.value = prefs[KEY_MACROPAD_AMBIENT_ENABLED] ?: false
        _macropadBackgroundDim.value = prefs[KEY_MACROPAD_AMBIENT_DIM] ?: 0f
        _macropadBackgroundVignetteEnabled.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED] ?: false
        _macropadBackgroundVignetteVisibleArea.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA] ?: 0.7f
        _macropadBackgroundVignetteTransition.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION] ?: 0.5f
        _macropadBackgroundVignetteOpacity.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY] ?: 0.6f
        _macropadBackgroundVignetteColor.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR] ?: 0xFF000000.toInt()
        _macropadBackgroundVignetteShape.value = VignetteShape.entries.firstOrNull { it.name == prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE] } ?: VignetteShape.RADIAL
        _macropadBackgroundPreview.value = prefs[KEY_MACROPAD_AMBIENT_PREVIEW] ?: false
        _macropadBackgroundApplyTheme.value = prefs[KEY_MACROPAD_AMBIENT_APPLY_THEME] ?: false
    }

    fun setMacropadBackgroundEnabled(value: Boolean) {
        AppLog.d(TAG, "setMacropadBackgroundEnabled($value)")
        _macropadBackgroundEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_ENABLED] = value } }
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

    /** Updates vignette visible area in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadBackgroundVignetteVisibleAreaLive(value: Float) {
        _macropadBackgroundVignetteVisibleArea.value = value
    }

    /** Updates vignette transition in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadBackgroundVignetteTransitionLive(value: Float) {
        _macropadBackgroundVignetteTransition.value = value
    }

    /** Updates vignette opacity in memory only — no DataStore write. Safe to call on every drag frame. */
    fun updateMacropadBackgroundVignetteOpacityLive(value: Float) {
        _macropadBackgroundVignetteOpacity.value = value
    }

    fun setMacropadBackgroundVignetteEnabled(value: Boolean) {
        AppLog.d(TAG, "setMacropadBackgroundVignetteEnabled($value)")
        _macropadBackgroundVignetteEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED] = value } }
    }

    fun setMacropadBackgroundVignetteVisibleArea(value: Float) {
        AppLog.d(TAG, "setMacropadBackgroundVignetteVisibleArea($value)")
        _macropadBackgroundVignetteVisibleArea.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA] = value } }
    }

    fun setMacropadBackgroundVignetteTransition(value: Float) {
        AppLog.d(TAG, "setMacropadBackgroundVignetteTransition($value)")
        _macropadBackgroundVignetteTransition.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION] = value } }
    }

    fun setMacropadBackgroundVignetteOpacity(value: Float) {
        AppLog.d(TAG, "setMacropadBackgroundVignetteOpacity($value)")
        _macropadBackgroundVignetteOpacity.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY] = value } }
    }

    fun setMacropadBackgroundVignetteColor(value: Int) {
        AppLog.d(TAG, "setMacropadBackgroundVignetteColor(${value.toString(16)})")
        _macropadBackgroundVignetteColor.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR] = value } }
    }

    fun setMacropadBackgroundVignetteShape(value: VignetteShape) {
        AppLog.d(TAG, "setMacropadBackgroundVignetteShape($value)")
        _macropadBackgroundVignetteShape.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE] = value.name } }
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
