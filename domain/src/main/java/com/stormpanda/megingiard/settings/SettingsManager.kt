package com.stormpanda.megingiard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.VignetteShape
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private const val SETTINGS_DATASTORE_NAME = "megingiard_settings"

/** Per-app language preference. [SYSTEM] follows the device locale. */
enum class AppLanguage { SYSTEM, EN, DE }

// VignetteShape is defined in core: com.stormpanda.megingiard.macropad.VignetteShape

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME
)

private const val DEFAULT_ACCENT_COLOR: Int = (0xFFCC0000).toInt()

private const val TAG = "SettingsManager"

object SettingsManager {
    private val KEY_AUTO_START_CAPTURE = booleanPreferencesKey("auto_start_capture")
    private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
    private val KEY_OVERLAY_AT_BOTTOM = booleanPreferencesKey("overlay_at_bottom")

    // Mirror touch projection settings
    private val KEY_PINCH_WHILE_PROJECTING = booleanPreferencesKey("mirror_pinch_while_projecting")
    // Mirror session state persistence — "remember" flags
    private val KEY_REMEMBER_VIEWPORT = booleanPreferencesKey("mirror_remember_viewport")
    private val KEY_REMEMBER_LOCK = booleanPreferencesKey("mirror_remember_lock")
    private val KEY_REMEMBER_PROJECTION = booleanPreferencesKey("mirror_remember_projection")
    // Mirror session state persistence — saved values
    private val KEY_SAVED_SCALE = floatPreferencesKey("mirror_saved_scale")
    private val KEY_SAVED_OFFSET_X = floatPreferencesKey("mirror_saved_offset_x")
    private val KEY_SAVED_OFFSET_Y = floatPreferencesKey("mirror_saved_offset_y")

    // Appearance
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    // MacroPad settings
    private val KEY_MACROPAD_PROFILES           = stringPreferencesKey("macropad_profiles")
    private val KEY_MACROPAD_ACTIVE_PROFILE_ID  = stringPreferencesKey("macropad_active_profile_id")
    private val KEY_MACROPAD_MACROS             = stringPreferencesKey("macropad_macros")

    // Keyboard settings
    private val KEY_KB_LAYOUT = stringPreferencesKey("kb_layout")
    private val KEY_KB_TRACKPOINT_ENABLED = booleanPreferencesKey("kb_trackpoint_enabled")
    private val KEY_KB_REPEAT_ENABLED = booleanPreferencesKey("kb_repeat_enabled")
    private val KEY_KB_FULLSCREEN = booleanPreferencesKey("kb_fullscreen")
    private val KEY_KB_MOUSE_BTN_POS = stringPreferencesKey("kb_mouse_btn_pos")

    // Language
    private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

    // Logging
    private val KEY_LOG_LEVEL = stringPreferencesKey("log_level")

    // Touchpad settings
    private val KEY_TOUCHPAD_USE_MOUSE = booleanPreferencesKey("touchpad_use_mouse")
    private val KEY_TOUCHPAD_TAP_TO_CLICK = booleanPreferencesKey("touchpad_tap_to_click")
    private val KEY_TOUCHPAD_TWO_FINGER_TAP = booleanPreferencesKey("touchpad_two_finger_tap")

    // MacroPad ambient display settings
    private val KEY_MACROPAD_AMBIENT_ENABLED = booleanPreferencesKey("macropad_ambient_enabled")
    private val KEY_MACROPAD_AMBIENT_DIM = floatPreferencesKey("macropad_ambient_dim")
    private val KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED = booleanPreferencesKey("macropad_ambient_vignette_enabled")
    private val KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA = floatPreferencesKey("macropad_ambient_vignette_visible_area")
    private val KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION = floatPreferencesKey("macropad_ambient_vignette_transition")
    private val KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY = floatPreferencesKey("macropad_ambient_vignette_opacity")
    private val KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR = intPreferencesKey("macropad_ambient_vignette_color")
    private val KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE = stringPreferencesKey("macropad_ambient_vignette_shape")
    private val KEY_MACROPAD_AMBIENT_PREVIEW = booleanPreferencesKey("macropad_ambient_preview")
    private val KEY_MACROPAD_AMBIENT_APPLY_THEME = booleanPreferencesKey("macropad_ambient_apply_theme")

    private val KEY_SAVED_LOCKED = booleanPreferencesKey("mirror_saved_locked")
    private val KEY_SAVED_PROJECTION = booleanPreferencesKey("mirror_saved_projection")

    // ── Section key groups for config export/import ───────────────────────────

    private val GLOBAL_KEYS: Set<Preferences.Key<*>> = setOf(
        KEY_ACCENT_COLOR, KEY_OVERLAY_AT_BOTTOM, KEY_THEME_MODE,
        KEY_APP_LANGUAGE, KEY_LOG_LEVEL,
    )
    private val MIRROR_KEYS: Set<Preferences.Key<*>> = setOf(
        KEY_AUTO_START_CAPTURE, KEY_PINCH_WHILE_PROJECTING,
        KEY_REMEMBER_VIEWPORT, KEY_REMEMBER_LOCK, KEY_REMEMBER_PROJECTION,
    )
    private val TOUCHPAD_KEYS: Set<Preferences.Key<*>> = setOf(
        KEY_TOUCHPAD_USE_MOUSE, KEY_TOUCHPAD_TAP_TO_CLICK, KEY_TOUCHPAD_TWO_FINGER_TAP,
    )
    private val KEYBOARD_KEYS: Set<Preferences.Key<*>> = setOf(
        KEY_KB_LAYOUT, KEY_KB_TRACKPOINT_ENABLED, KEY_KB_REPEAT_ENABLED,
        KEY_KB_FULLSCREEN, KEY_KB_MOUSE_BTN_POS,
    )
    private val MACROPAD_SETTINGS_KEYS: Set<Preferences.Key<*>> = setOf(
        KEY_MACROPAD_AMBIENT_ENABLED, KEY_MACROPAD_AMBIENT_DIM,
        KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED, KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA,
        KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION, KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY,
        KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR, KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE,
        KEY_MACROPAD_AMBIENT_PREVIEW, KEY_MACROPAD_AMBIENT_APPLY_THEME,
    )

    internal val SECTION_MAP: Map<String, Set<Preferences.Key<*>>> = mapOf(
        "global" to GLOBAL_KEYS,
        "mirror" to MIRROR_KEYS,
        "touchpad" to TOUCHPAD_KEYS,
        "keyboard" to KEYBOARD_KEYS,
        "macropad_settings" to MACROPAD_SETTINGS_KEYS,
    )

    // Reverse lookup: DataStore key name → section name
    internal val KEY_TO_SECTION: Map<String, String> by lazy {
        SECTION_MAP.flatMap { (section, keys) -> keys.map { it.name to section } }.toMap()
    }

    /** Flat map from DataStore key name to the actual typed key instance, used by [importGroupedSettings]. */
    internal val KEY_BY_NAME: Map<String, Preferences.Key<*>> by lazy {
        SECTION_MAP.values.flatten().associateBy { it.name }
    }

    // App-lifetime scope: intentionally never cancelled — this singleton lives for the
    // duration of the process. Cancellation is handled by process termination.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private var initialized = false

    private val macropadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _autoStartCapture = MutableStateFlow(false)
    val autoStartCapture: StateFlow<Boolean> = _autoStartCapture.asStateFlow()

    private val _accentColor = MutableStateFlow(DEFAULT_ACCENT_COLOR)
    val accentColor: StateFlow<Int> = _accentColor.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _overlayAtBottom = MutableStateFlow(false)
    val overlayAtBottom: StateFlow<Boolean> = _overlayAtBottom.asStateFlow()

    // Mirror touch projection — pinch-to-zoom while projecting
    private val _pinchWhileProjecting = MutableStateFlow(false)
    val pinchWhileProjecting: StateFlow<Boolean> = _pinchWhileProjecting.asStateFlow()

    // Keyboard
    private val _kbLayout = MutableStateFlow(KbLayout.QWERTZ)
    val kbLayout: StateFlow<KbLayout> = _kbLayout.asStateFlow()

    private val _kbTrackpointEnabled = MutableStateFlow(true)
    val kbTrackpointEnabled: StateFlow<Boolean> = _kbTrackpointEnabled.asStateFlow()

    private val _kbRepeatEnabled = MutableStateFlow(true)
    val kbRepeatEnabled: StateFlow<Boolean> = _kbRepeatEnabled.asStateFlow()

    // false = bottom padding for IME (default); true = fullscreen, no padding
    private val _kbFullscreen = MutableStateFlow(false)
    val kbFullscreen: StateFlow<Boolean> = _kbFullscreen.asStateFlow()

    // Keyboard trackpoint mouse button position
    private val _kbMouseBtnPos = MutableStateFlow(KbMouseBtnPos.LEFT)
    val kbMouseBtnPos: StateFlow<KbMouseBtnPos> = _kbMouseBtnPos.asStateFlow()

    // Touchpad input method: false = touch (default), true = mouse
    private val _touchpadUseMouse = MutableStateFlow(false)
    val touchpadUseMouse: StateFlow<Boolean> = _touchpadUseMouse.asStateFlow()

    // Tap-to-click — only active in touchpad mouse mode
    private val _touchpadTapToClick = MutableStateFlow(true)
    val touchpadTapToClick: StateFlow<Boolean> = _touchpadTapToClick.asStateFlow()

    // Two-finger tap = right click — only active in touchpad mouse mode
    private val _touchpadTwoFingerTap = MutableStateFlow(true)
    val touchpadTwoFingerTap: StateFlow<Boolean> = _touchpadTwoFingerTap.asStateFlow()

    // Mirror session state persistence — whether each aspect is remembered
    private val _rememberViewport = MutableStateFlow(false)
    val rememberViewport: StateFlow<Boolean> = _rememberViewport.asStateFlow()

    private val _rememberLock = MutableStateFlow(false)
    val rememberLock: StateFlow<Boolean> = _rememberLock.asStateFlow()

    private val _rememberProjection = MutableStateFlow(false)
    val rememberProjection: StateFlow<Boolean> = _rememberProjection.asStateFlow()

    // App language
    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // Logging
    private val _logLevel = MutableStateFlow(AppLog.Level.WARN)
    val logLevel: StateFlow<AppLog.Level> = _logLevel.asStateFlow()

    // MacroPad ambient display
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

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        dataStore = context.applicationContext.settingsDataStore

        scope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .collect { prefs ->
                AppLog.i(TAG, "settings loaded from DataStore")

                _autoStartCapture.value = prefs[KEY_AUTO_START_CAPTURE] ?: false
                _accentColor.value = prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
                _themeMode.value = ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME_MODE] } ?: ThemeMode.DARK
                _overlayAtBottom.value = prefs[KEY_OVERLAY_AT_BOTTOM] ?: false
                _pinchWhileProjecting.value = prefs[KEY_PINCH_WHILE_PROJECTING] ?: false
                _rememberViewport.value = prefs[KEY_REMEMBER_VIEWPORT] ?: false
                _rememberLock.value = prefs[KEY_REMEMBER_LOCK] ?: false
                _rememberProjection.value = prefs[KEY_REMEMBER_PROJECTION] ?: false
                _kbLayout.value = KbLayout.entries.firstOrNull { it.name == prefs[KEY_KB_LAYOUT] } ?: KbLayout.QWERTZ
                _kbTrackpointEnabled.value = prefs[KEY_KB_TRACKPOINT_ENABLED] ?: true
                _kbRepeatEnabled.value = prefs[KEY_KB_REPEAT_ENABLED] ?: true
                _kbFullscreen.value = prefs[KEY_KB_FULLSCREEN] ?: false
                _kbMouseBtnPos.value = KbMouseBtnPos.entries.firstOrNull { it.name == prefs[KEY_KB_MOUSE_BTN_POS] } ?: KbMouseBtnPos.LEFT
                _touchpadUseMouse.value = prefs[KEY_TOUCHPAD_USE_MOUSE] ?: false
                _touchpadTapToClick.value = prefs[KEY_TOUCHPAD_TAP_TO_CLICK] ?: true
                _touchpadTwoFingerTap.value = prefs[KEY_TOUCHPAD_TWO_FINGER_TAP] ?: true
                _appLanguage.value = AppLanguage.entries.firstOrNull { it.name == prefs[KEY_APP_LANGUAGE] } ?: AppLanguage.SYSTEM
                _logLevel.value = AppLog.Level.entries.firstOrNull { it.name == prefs[KEY_LOG_LEVEL] } ?: AppLog.Level.WARN
                AppLog.level = _logLevel.value
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

                // MacroPad profiles (with global macros for migration)
                val macropadProfilesJson = prefs[KEY_MACROPAD_PROFILES]
                val globalMacros = prefs[KEY_MACROPAD_MACROS]?.let { json ->
                    runCatching { macropadJson.decodeFromString<List<Macro>>(json) }.getOrElse { emptyList() }
                } ?: emptyList()
                if (macropadProfilesJson != null) {
                    val profiles = runCatching {
                        macropadJson.decodeFromString<List<PadProfile>>(macropadProfilesJson)
                    }.getOrElse { emptyList() }
                    val activeId = prefs[KEY_MACROPAD_ACTIVE_PROFILE_ID]
                    MacroPadState.loadFrom(profiles, activeId, globalMacros)
                }
            }
        }
    }

    fun setAutoStartCapture(value: Boolean) {
        AppLog.d(TAG, "setAutoStartCapture($value)")
        _autoStartCapture.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_AUTO_START_CAPTURE] = value
            }
        }
    }

    fun setAccentColor(argb: Int) {
        AppLog.d(TAG, "setAccentColor(${argb.toString(16)})")
        _accentColor.value = argb
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_ACCENT_COLOR] = argb
            }
        }
    }

    fun setThemeMode(value: ThemeMode) {
        AppLog.d(TAG, "setThemeMode($value)")
        _themeMode.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = value.name } }
    }

    fun setOverlayAtBottom(value: Boolean) {
        AppLog.d(TAG, "setOverlayAtBottom($value)")
        _overlayAtBottom.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_OVERLAY_AT_BOTTOM] = value
            }
        }
    }

    fun setPinchWhileProjecting(value: Boolean) {
        AppLog.d(TAG, "setPinchWhileProjecting($value)")
        _pinchWhileProjecting.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_PINCH_WHILE_PROJECTING] = value }
        }
    }

    fun setRememberViewport(value: Boolean) {
        AppLog.d(TAG, "setRememberViewport($value)")
        _rememberViewport.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_REMEMBER_VIEWPORT] = value }
        }
    }

    fun setRememberLock(value: Boolean) {
        AppLog.d(TAG, "setRememberLock($value)")
        _rememberLock.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_REMEMBER_LOCK] = value }
        }
    }

    fun setRememberProjection(value: Boolean) {
        AppLog.d(TAG, "setRememberProjection($value)")
        _rememberProjection.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_REMEMBER_PROJECTION] = value }
        }
    }

    fun setAppLanguage(value: AppLanguage) {
        AppLog.d(TAG, "setAppLanguage($value)")
        _appLanguage.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_APP_LANGUAGE] = value.name } }
    }

    fun setLogLevel(value: AppLog.Level) {
        AppLog.i(TAG, "setLogLevel($value)")
        _logLevel.value = value
        AppLog.level = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_LOG_LEVEL] = value.name } }
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

    /**
     * Persists the current MacroPad profile list and active profile ID to DataStore.
     * Called by [MacroPadState] mutators whenever the profile state changes.
     */
    fun saveMacroPadData() {
        val profiles = MacroPadState.profiles.value
        val activeId = MacroPadState.activeProfileId.value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_MACROPAD_PROFILES] = macropadJson.encodeToString(profiles)
                if (activeId != null) {
                    prefs[KEY_MACROPAD_ACTIVE_PROFILE_ID] = activeId
                } else {
                    prefs.remove(KEY_MACROPAD_ACTIVE_PROFILE_ID)
                }
            }
        }
    }

    /** Persists the current mirror session state for aspects the user opted to remember. */
    fun saveMirrorSessionState() {
        AppLog.d(TAG, "saveMirrorSessionState")
        // Capture ALL values synchronously on the calling thread (main) BEFORE
        // resetMirrorSessionState() zeroes them out — including the remember-flags,
        // so the async lambda never reads stale StateFlow state.
        val scale = ScreenCaptureManager.scale.value
        val offsetX = ScreenCaptureManager.offsetX.value
        val offsetY = ScreenCaptureManager.offsetY.value
        val locked = ScreenCaptureManager.isLocked.value
        val projection = ScreenCaptureManager.isTouchProjectionActive.value
        val rememberViewport = _rememberViewport.value
        val rememberLock = _rememberLock.value
        val rememberProjection = _rememberProjection.value
        scope.launch {
            dataStore.edit { prefs ->
                if (rememberViewport) {
                    prefs[KEY_SAVED_SCALE] = scale
                    prefs[KEY_SAVED_OFFSET_X] = offsetX
                    prefs[KEY_SAVED_OFFSET_Y] = offsetY
                }
                if (rememberLock) {
                    prefs[KEY_SAVED_LOCKED] = locked
                }
                if (rememberProjection) {
                    prefs[KEY_SAVED_PROJECTION] = projection
                }
            }
        }
    }

    /**
     * Restores previously saved mirror session state into [ScreenCaptureManager].
     * Only restores aspects the user opted to remember.
     *
     * This is a **suspend** function so the caller can wait for the DataStore read
     * to complete before syncing UI state (e.g. Animatable values).
     */
    suspend fun restoreMirrorSessionState() {
        AppLog.i(TAG, "restoreMirrorSessionState")
        // Read the entire prefs snapshot once and derive both the remember-flags
        // and the saved values from it.  This avoids any race with the async init
        // block that populates the in-memory StateFlows (_rememberViewport etc.),
        // which may not have loaded yet when this is called on first capture start.
        val prefs = dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        if (prefs[KEY_REMEMBER_VIEWPORT] ?: false) {
            prefs[KEY_SAVED_SCALE]?.let { ScreenCaptureManager.setScale(it) }
            prefs[KEY_SAVED_OFFSET_X]?.let { ScreenCaptureManager.setOffsetX(it) }
            prefs[KEY_SAVED_OFFSET_Y]?.let { ScreenCaptureManager.setOffsetY(it) }
        }
        if (prefs[KEY_REMEMBER_LOCK] ?: false) {
            prefs[KEY_SAVED_LOCKED]?.let { ScreenCaptureManager.setLocked(it) }
        }
        if (prefs[KEY_REMEMBER_PROJECTION] ?: false) {
            prefs[KEY_SAVED_PROJECTION]?.let { ScreenCaptureManager.setTouchProjectionActive(it) }
        }
    }

    // ── Bulk export/import for config files ──────────────────────────────────

    /**
     * Snapshots all exportable settings from DataStore, grouped by section name.
     * Each value is converted to a [JsonElement] so ConfigManager can serialise it directly.
     */
    suspend fun exportGroupedSettings(): Map<String, Map<String, JsonElement>> {
        AppLog.d(TAG, "exportGroupedSettings")
        val prefs = dataStore.data.catch { emit(emptyPreferences()) }.first()
        val result = mutableMapOf<String, Map<String, JsonElement>>()
        for ((section, keys) in SECTION_MAP) {
            val entries = mutableMapOf<String, JsonElement>()
            for (key in keys) {
                val raw = prefs[key] ?: continue
                entries[key.name] = when (raw) {
                    is Boolean -> JsonPrimitive(raw)
                    is Int -> JsonPrimitive(raw)
                    is Long -> JsonPrimitive(raw)
                    is Float -> JsonPrimitive(raw)
                    is String -> JsonPrimitive(raw)
                    else -> continue
                }
            }
            if (entries.isNotEmpty()) result[section] = entries
        }
        return result
    }

    /**
     * Writes all settings from [sections] into DataStore in a single edit.
     * The existing `.collect {}` in [init] automatically re-hydrates every [StateFlow]
     * after the edit completes — no manual setter calls needed.
     *
     * Type dispatch uses [KEY_BY_NAME] to resolve the actual [Preferences.Key] and
     * `prefs.asMap()` to detect the stored type, so DataStore proto fields are always
     * written with the correct type (not a heuristic-guessed type).
     */
    fun importGroupedSettings(sections: Map<String, Map<String, JsonElement>>) {
        AppLog.i(TAG, "importGroupedSettings: sections=${sections.keys}")
        scope.launch {
            importGroupedSettingsInternal(sections)
        }
    }

    /**
     * Awaitable variant — callers that need to know when the DataStore write completes
     * (e.g. [ConfigManager.applyImport]) should call this directly from a suspend context.
     */
    suspend fun importGroupedSettingsAwait(sections: Map<String, Map<String, JsonElement>>) {
        AppLog.i(TAG, "importGroupedSettingsAwait: sections=${sections.keys}")
        importGroupedSettingsInternal(sections)
    }

    private suspend fun importGroupedSettingsInternal(sections: Map<String, Map<String, JsonElement>>) {
        dataStore.edit { prefs ->
                for ((_, entries) in sections) {
                    for ((keyName, element) in entries) {
                        if (element !is JsonPrimitive) continue
                        val key = KEY_BY_NAME[keyName]
                        if (key == null) {
                            AppLog.w(TAG, "importGroupedSettings: unknown key '$keyName', skipping")
                            continue
                        }
                        val existingValue = prefs.asMap()[key]
                        @Suppress("UNCHECKED_CAST")
                        if (existingValue != null) {
                            // Type is known from the currently stored value — safe cast by construction.
                            when (existingValue) {
                                is Boolean -> element.booleanOrNull?.let { prefs[key as Preferences.Key<Boolean>] = it }
                                is Int     -> element.intOrNull?.let    { prefs[key as Preferences.Key<Int>]     = it }
                                is Long    -> element.longOrNull?.let   { prefs[key as Preferences.Key<Long>]    = it }
                                is Float   -> element.floatOrNull?.let  { prefs[key as Preferences.Key<Float>]   = it }
                                is String  -> element.contentOrNull?.let { prefs[key as Preferences.Key<String>]  = it }
                            }
                        } else {
                            // Key absent on fresh install — infer type from JSON primitive.
                            @Suppress("UNCHECKED_CAST")
                            when {
                                element.booleanOrNull != null ->
                                    prefs[key as Preferences.Key<Boolean>] = element.booleanOrNull!!
                                element.floatOrNull != null && element.contentOrNull?.contains('.') == true ->
                                    prefs[key as Preferences.Key<Float>] = element.floatOrNull!!
                                element.intOrNull != null ->
                                    prefs[key as Preferences.Key<Int>] = element.intOrNull!!
                                else ->
                                    element.contentOrNull?.let { prefs[key as Preferences.Key<String>] = it }
                            }
                        }
                    }
                }
            }
        }
    }

