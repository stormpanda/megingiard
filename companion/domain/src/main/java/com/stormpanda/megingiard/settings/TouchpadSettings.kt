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

    // Enable physical haptic feedback tick on clicks / taps
    private val _touchpadHapticsEnabled = MutableStateFlow(true)
    val touchpadHapticsEnabled: StateFlow<Boolean> = _touchpadHapticsEnabled.asStateFlow()

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
        _touchpadHapticsEnabled.value = prefs[KEY_TOUCHPAD_HAPTICS_ENABLED] ?: true
    }

    private val optionalDataStore: DataStore<Preferences>?
        get() = if (::dataStore.isInitialized) dataStore else null

    private val optionalScope: CoroutineScope?
        get() = if (::scope.isInitialized) scope else null

    fun setTouchpadUseMouse(value: Boolean) {
        updateSettingPref(KEY_TOUCHPAD_USE_MOUSE, value, _touchpadUseMouse, optionalScope, optionalDataStore, TAG, "setTouchpadUseMouse")
    }

    fun setTouchpadTapToClick(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_TAP_TO_CLICK,
            value,
            _touchpadTapToClick,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadTapToClick",
        )
    }

    fun setTouchpadTwoFingerTap(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_TWO_FINGER_TAP,
            value,
            _touchpadTwoFingerTap,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadTwoFingerTap",
        )
    }

    fun setTouchpadThreeFingerTap(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_THREE_FINGER_TAP,
            value,
            _touchpadThreeFingerTap,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadThreeFingerTap",
        )
    }

    fun setTouchpadTapDrag(value: Boolean) {
        updateSettingPref(KEY_TOUCHPAD_TAP_DRAG, value, _touchpadTapDrag, optionalScope, optionalDataStore, TAG, "setTouchpadTapDrag")
    }

    fun setTouchpadTwoFingerScroll(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_TWO_FINGER_SCROLL,
            value,
            _touchpadTwoFingerScroll,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadTwoFingerScroll",
        )
    }

    fun setTouchpadMirroringEnabled(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_MIRRORING_ENABLED,
            value,
            _touchpadMirroringEnabled,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadMirroringEnabled",
        )
    }

    fun setTouchpadMirrorDim(value: Int) {
        updateSettingPref(
            KEY_TOUCHPAD_MIRROR_DIM,
            value.coerceIn(0, 90),
            _touchpadMirrorDim,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadMirrorDim",
        )
    }

    fun setTouchpadMouse45Enabled(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_MOUSE_4_5_ENABLED,
            value,
            _touchpadMouse45Enabled,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadMouse45Enabled",
        )
    }

    fun setTouchpadSensitivity(value: Float) {
        updateSettingPref(
            KEY_TOUCHPAD_SENSITIVITY,
            value.coerceIn(0.1f, 3.0f),
            _touchpadSensitivity,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadSensitivity",
        )
    }

    fun setTouchpadNaturalScroll(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_NATURAL_SCROLL,
            value,
            _touchpadNaturalScroll,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadNaturalScroll",
        )
    }

    fun setTouchpadScrollSpeed(value: Float) {
        updateSettingPref(
            KEY_TOUCHPAD_SCROLL_SPEED,
            value.coerceIn(0.5f, 3.0f),
            _touchpadScrollSpeed,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadScrollSpeed",
        )
    }

    fun setTouchpadHapticsEnabled(value: Boolean) {
        updateSettingPref(
            KEY_TOUCHPAD_HAPTICS_ENABLED,
            value,
            _touchpadHapticsEnabled,
            optionalScope,
            optionalDataStore,
            TAG,
            "setTouchpadHapticsEnabled",
        )
    }
}
