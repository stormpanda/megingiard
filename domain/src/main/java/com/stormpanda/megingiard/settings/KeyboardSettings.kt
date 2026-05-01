package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "KeyboardSettings"

/**
 * Keyboard-feature persisted settings. Owns the layout/trackpoint/repeat/fullscreen/
 * mouse-button-position state and persists it to the shared DataStore owned by
 * [SettingsManager].
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

    private val _kbTrackpointEnabled = MutableStateFlow(true)
    val kbTrackpointEnabled: StateFlow<Boolean> = _kbTrackpointEnabled.asStateFlow()

    private val _kbRepeatEnabled = MutableStateFlow(true)
    val kbRepeatEnabled: StateFlow<Boolean> = _kbRepeatEnabled.asStateFlow()

    // false = bottom padding for IME (default); true = fullscreen, no padding
    private val _kbFullscreen = MutableStateFlow(false)
    val kbFullscreen: StateFlow<Boolean> = _kbFullscreen.asStateFlow()

    private val _kbMouseBtnPos = MutableStateFlow(KbMouseBtnPos.LEFT)
    val kbMouseBtnPos: StateFlow<KbMouseBtnPos> = _kbMouseBtnPos.asStateFlow()

    internal fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _kbLayout.value = KbLayout.entries.firstOrNull { it.name == prefs[KEY_KB_LAYOUT] } ?: KbLayout.QWERTZ
        _kbTrackpointEnabled.value = prefs[KEY_KB_TRACKPOINT_ENABLED] ?: true
        _kbRepeatEnabled.value = prefs[KEY_KB_REPEAT_ENABLED] ?: true
        _kbFullscreen.value = prefs[KEY_KB_FULLSCREEN] ?: false
        _kbMouseBtnPos.value = KbMouseBtnPos.entries.firstOrNull { it.name == prefs[KEY_KB_MOUSE_BTN_POS] } ?: KbMouseBtnPos.LEFT
    }

    fun setKbLayout(value: KbLayout) {
        AppLog.d(TAG, "setKbLayout($value)")
        _kbLayout.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_LAYOUT] = value.name } }
    }

    fun setKbTrackpointEnabled(value: Boolean) {
        AppLog.d(TAG, "setKbTrackpointEnabled($value)")
        _kbTrackpointEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_TRACKPOINT_ENABLED] = value } }
    }

    fun setKbRepeatEnabled(value: Boolean) {
        AppLog.d(TAG, "setKbRepeatEnabled($value)")
        _kbRepeatEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_REPEAT_ENABLED] = value } }
    }

    fun setKbFullscreen(value: Boolean) {
        AppLog.d(TAG, "setKbFullscreen($value)")
        _kbFullscreen.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_FULLSCREEN] = value } }
    }

    fun setKbMouseBtnPos(value: KbMouseBtnPos) {
        AppLog.d(TAG, "setKbMouseBtnPos($value)")
        _kbMouseBtnPos.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_MOUSE_BTN_POS] = value.name } }
    }
}
