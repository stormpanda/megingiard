package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.stormpanda.megingiard.keyboard.KbLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "KeyboardSettings"

/**
 * Keyboard-feature persisted settings. Owns the layout, touchpad, and auto-open
 * state and persists it to the shared DataStore owned by [SettingsManager].
 *
 * Lifecycle:
 * - [SettingsManager.init] calls [init] with the shared `dataStore` + `scope`.
 * - [SettingsManager.init]'s `dataStore.data.collect { prefs -> }` calls [loadFrom]
 *   on every emission, including the initial load.
 *
 * Setters apply the in-memory `StateFlow` value immediately, then launch a
 * coroutine on the shared scope to persist to DataStore. Same pattern as
 * before extraction — no behavioural change.
 */
object KeyboardSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    private val _kbLayout = MutableStateFlow(KbLayout.QWERTZ)
    val kbLayout: StateFlow<KbLayout> = _kbLayout.asStateFlow()

    private val _kbTouchpadEnabled = MutableStateFlow(true)
    val kbTouchpadEnabled: StateFlow<Boolean> = _kbTouchpadEnabled.asStateFlow()

    private val _kbAutoOpenOnFocus = MutableStateFlow(true)
    val kbAutoOpenOnFocus: StateFlow<Boolean> = _kbAutoOpenOnFocus.asStateFlow()

    internal fun init(
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ) {
        this.dataStore = dataStore
        this.scope = scope
    }

    fun resetForTesting() {
        _kbLayout.value = KbLayout.QWERTZ
        _kbTouchpadEnabled.value = true
        _kbAutoOpenOnFocus.value = true
    }

    internal fun loadFrom(prefs: Preferences) {
        prefs[KEY_KB_LAYOUT]?.let { name ->
            KbLayout.entries.firstOrNull { it.name == name }?.let { _kbLayout.value = it }
        }
        prefs[KEY_KB_TOUCHPAD_ENABLED]?.let { _kbTouchpadEnabled.value = it }
        prefs[KEY_KB_AUTO_OPEN_ON_FOCUS]?.let { _kbAutoOpenOnFocus.value = it }
    }

    private val optionalDataStore: DataStore<Preferences>?
        get() = if (::dataStore.isInitialized) dataStore else null

    private val optionalScope: CoroutineScope?
        get() = if (::scope.isInitialized) scope else null

    fun setKbLayout(value: KbLayout) {
        updateEnumSettingPref(KEY_KB_LAYOUT, value, _kbLayout, optionalScope, optionalDataStore, TAG, "setKbLayout")
    }

    fun setKbTouchpadEnabled(value: Boolean) {
        updateSettingPref(KEY_KB_TOUCHPAD_ENABLED, value, _kbTouchpadEnabled, optionalScope, optionalDataStore, TAG, "setKbTouchpadEnabled")
    }

    fun setKbAutoOpenOnFocus(value: Boolean) {
        updateSettingPref(
            KEY_KB_AUTO_OPEN_ON_FOCUS,
            value,
            _kbAutoOpenOnFocus,
            optionalScope,
            optionalDataStore,
            TAG,
            "setKbAutoOpenOnFocus",
        )
    }
}
