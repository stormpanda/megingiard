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

    // Three-finger tap = middle click — only active in touchpad mouse mode
    private val _touchpadThreeFingerTap = MutableStateFlow(true)
    val touchpadThreeFingerTap: StateFlow<Boolean> = _touchpadThreeFingerTap.asStateFlow()

    // Double-tap and hold = drag — only active in touchpad mouse mode
    private val _touchpadTapDrag = MutableStateFlow(true)
    val touchpadTapDrag: StateFlow<Boolean> = _touchpadTapDrag.asStateFlow()

    // Two-finger scroll — only active in touchpad mouse mode
    private val _touchpadTwoFingerScroll = MutableStateFlow(true)
    val touchpadTwoFingerScroll: StateFlow<Boolean> = _touchpadTwoFingerScroll.asStateFlow()

    // Absolute touchpad mirroring: false = disabled (default), true = enabled
    private val _touchpadMirroringEnabled = MutableStateFlow(false)
    val touchpadMirroringEnabled: StateFlow<Boolean> = _touchpadMirroringEnabled.asStateFlow()

    // Dim percentage of the mirrored screen: default 50, range 0 to 90
    private val _touchpadMirrorDim = MutableStateFlow(50)
    val touchpadMirrorDim: StateFlow<Int> = _touchpadMirrorDim.asStateFlow()

    // Enable Mouse 4/5 buttons in relative mouse mode
    private val _touchpadMouse45Enabled = MutableStateFlow(false)
    val touchpadMouse45Enabled: StateFlow<Boolean> = _touchpadMouse45Enabled.asStateFlow()

    // Pointer sensitivity speed multiplier
    private val _touchpadSensitivity = MutableStateFlow(1.0f)
    val touchpadSensitivity: StateFlow<Float> = _touchpadSensitivity.asStateFlow()

    // Natural scrolling direction: true = enabled (default), false = traditional
    private val _touchpadNaturalScroll = MutableStateFlow(true)
    val touchpadNaturalScroll: StateFlow<Boolean> = _touchpadNaturalScroll.asStateFlow()

    // Scroll speed sensitivity multiplier: default 1.0f, range 0.5f to 3.0f
    private val _touchpadScrollSpeed = MutableStateFlow(1.0f)
    val touchpadScrollSpeed: StateFlow<Float> = _touchpadScrollSpeed.asStateFlow()

    internal fun init(
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ) {
        this.dataStore = dataStore
        this.scope = scope
    }

    internal fun loadFrom(prefs: Preferences) {
        _touchpadUseMouse.value = prefs[KEY_TOUCHPAD_USE_MOUSE] ?: false
        _touchpadTapToClick.value = prefs[KEY_TOUCHPAD_TAP_TO_CLICK] ?: true
        _touchpadTwoFingerTap.value = prefs[KEY_TOUCHPAD_TWO_FINGER_TAP] ?: true
        _touchpadThreeFingerTap.value = prefs[KEY_TOUCHPAD_THREE_FINGER_TAP] ?: true
        _touchpadTapDrag.value = prefs[KEY_TOUCHPAD_TAP_DRAG] ?: true
        _touchpadTwoFingerScroll.value = prefs[KEY_TOUCHPAD_TWO_FINGER_SCROLL] ?: true
        _touchpadMirroringEnabled.value = prefs[KEY_TOUCHPAD_MIRRORING_ENABLED] ?: false
        _touchpadMirrorDim.value = prefs[KEY_TOUCHPAD_MIRROR_DIM] ?: 50
        _touchpadMouse45Enabled.value = prefs[KEY_TOUCHPAD_MOUSE_4_5_ENABLED] ?: false
        _touchpadSensitivity.value = prefs[KEY_TOUCHPAD_SENSITIVITY] ?: 1.0f
        _touchpadNaturalScroll.value = prefs[KEY_TOUCHPAD_NATURAL_SCROLL] ?: true
        _touchpadScrollSpeed.value = prefs[KEY_TOUCHPAD_SCROLL_SPEED] ?: 1.0f
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

    fun setTouchpadThreeFingerTap(value: Boolean) {
        AppLog.d(TAG, "setTouchpadThreeFingerTap($value)")
        _touchpadThreeFingerTap.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_THREE_FINGER_TAP] = value } }
    }

    fun setTouchpadTapDrag(value: Boolean) {
        AppLog.d(TAG, "setTouchpadTapDrag($value)")
        _touchpadTapDrag.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_TAP_DRAG] = value } }
    }

    fun setTouchpadTwoFingerScroll(value: Boolean) {
        AppLog.d(TAG, "setTouchpadTwoFingerScroll($value)")
        _touchpadTwoFingerScroll.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_TWO_FINGER_SCROLL] = value } }
    }

    fun setTouchpadMirroringEnabled(value: Boolean) {
        AppLog.d(TAG, "setTouchpadMirroringEnabled($value)")
        _touchpadMirroringEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_MIRRORING_ENABLED] = value } }
    }

    fun setTouchpadMirrorDim(value: Int) {
        val clamped = value.coerceIn(0, 90)
        AppLog.d(TAG, "setTouchpadMirrorDim($clamped)")
        _touchpadMirrorDim.value = clamped
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_MIRROR_DIM] = clamped } }
    }

    fun setTouchpadMouse45Enabled(value: Boolean) {
        AppLog.d(TAG, "setTouchpadMouse45Enabled($value)")
        _touchpadMouse45Enabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_MOUSE_4_5_ENABLED] = value } }
    }

    fun setTouchpadSensitivity(value: Float) {
        val clamped = value.coerceIn(0.1f, 3.0f)
        AppLog.d(TAG, "setTouchpadSensitivity($clamped)")
        _touchpadSensitivity.value = clamped
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_SENSITIVITY] = clamped } }
    }

    fun setTouchpadNaturalScroll(value: Boolean) {
        AppLog.d(TAG, "setTouchpadNaturalScroll($value)")
        _touchpadNaturalScroll.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_NATURAL_SCROLL] = value } }
    }

    fun setTouchpadScrollSpeed(value: Float) {
        val clamped = value.coerceIn(0.5f, 3.0f)
        AppLog.d(TAG, "setTouchpadScrollSpeed($clamped)")
        _touchpadScrollSpeed.value = clamped
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_SCROLL_SPEED] = clamped } }
    }
}
