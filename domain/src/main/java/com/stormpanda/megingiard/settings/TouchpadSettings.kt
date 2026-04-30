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

private const val TAG = "TouchpadSettings"

/**
 * Touchpad-feature persisted settings. Owns the input-mode (touch vs. mouse)
 * and tap-gesture toggles, persisting them to the shared DataStore owned by
 * [SettingsManager].
 *
 * Lifecycle: see [KeyboardSettings] — same pattern (init + loadFrom called
 * by SettingsManager).
 */
object TouchpadSettings {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    // Touchpad input method: false = touch (default), true = mouse
    private val _touchpadUseMouse = MutableStateFlow(false)
    val touchpadUseMouse: StateFlow<Boolean> = _touchpadUseMouse.asStateFlow()

    // Tap-to-click — only active in touchpad mouse mode
    private val _touchpadTapToClick = MutableStateFlow(true)
    val touchpadTapToClick: StateFlow<Boolean> = _touchpadTapToClick.asStateFlow()

    // Two-finger tap = right click — only active in touchpad mouse mode
    private val _touchpadTwoFingerTap = MutableStateFlow(true)
    val touchpadTwoFingerTap: StateFlow<Boolean> = _touchpadTwoFingerTap.asStateFlow()

    internal fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _touchpadUseMouse.value = prefs[KEY_TOUCHPAD_USE_MOUSE] ?: false
        _touchpadTapToClick.value = prefs[KEY_TOUCHPAD_TAP_TO_CLICK] ?: true
        _touchpadTwoFingerTap.value = prefs[KEY_TOUCHPAD_TWO_FINGER_TAP] ?: true
    }

    fun setTouchpadUseMouse(value: Boolean) {
        AppLog.d(TAG, "setTouchpadUseMouse($value)")
        _touchpadUseMouse.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_USE_MOUSE] = value } }
    }

    fun setTouchpadTapToClick(value: Boolean) {
        AppLog.d(TAG, "setTouchpadTapToClick($value)")
        _touchpadTapToClick.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_TAP_TO_CLICK] = value } }
    }

    fun setTouchpadTwoFingerTap(value: Boolean) {
        AppLog.d(TAG, "setTouchpadTwoFingerTap($value)")
        _touchpadTwoFingerTap.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_TWO_FINGER_TAP] = value } }
    }
}
