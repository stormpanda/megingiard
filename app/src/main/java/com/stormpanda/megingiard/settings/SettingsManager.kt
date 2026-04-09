package com.stormpanda.megingiard.settings

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.ui.ThemeMode
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KbMouseBtnPos
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroFolder
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroState
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SETTINGS_DATASTORE_NAME = "megingiard_settings"
private const val DEFAULT_OVERLAY_TIMEOUT_MS = 3_000L

/** Per-app language preference. [SYSTEM] follows the device locale. */
enum class AppLanguage { SYSTEM, EN, DE }

/** Shape of the vignette overlay in Ambient Display mode. */
enum class VignetteShape { RADIAL, LETTERBOX, PILLARBOX }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME
)

private val DEFAULT_ACCENT_COLOR: Int = AndroidColor.argb(255, 204, 0, 0)

object SettingsManager {
    private val KEY_ENABLED_TOOLS = stringPreferencesKey("enabled_tools")
    private val KEY_TOOL_ORDER = stringPreferencesKey("tool_order")
    private val KEY_AUTO_START_CAPTURE = booleanPreferencesKey("auto_start_capture")
    private val KEY_OVERLAY_TIMEOUT_MS = longPreferencesKey("overlay_timeout_ms")
    private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
    private val KEY_OVERLAY_AT_BOTTOM = booleanPreferencesKey("overlay_at_bottom")
    private val KEY_REMEMBER_LAST_TOOL = booleanPreferencesKey("remember_last_tool")
    private val KEY_LAST_TOOL = stringPreferencesKey("last_tool")

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
    private val KEY_MACROPAD_MACRO_FOLDERS      = stringPreferencesKey("macropad_macro_folders")

    // Keyboard settings
    private val KEY_KB_LAYOUT = stringPreferencesKey("kb_layout")
    private val KEY_KB_TRACKPOINT_ENABLED = booleanPreferencesKey("kb_trackpoint_enabled")
    private val KEY_KB_REPEAT_ENABLED = booleanPreferencesKey("kb_repeat_enabled")
    private val KEY_KB_FULLSCREEN = booleanPreferencesKey("kb_fullscreen")
    private val KEY_KB_MOUSE_BTN_POS = stringPreferencesKey("kb_mouse_btn_pos")

    // Language
    private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

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

    private val KEY_SAVED_LOCKED = booleanPreferencesKey("mirror_saved_locked")
    private val KEY_SAVED_PROJECTION = booleanPreferencesKey("mirror_saved_projection")

    // App-lifetime scope: intentionally never cancelled — this singleton lives for the
    // duration of the process. Cancellation is handled by process termination.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private var initialized = false

    private val macropadJson = Json { ignoreUnknownKeys = true }

    private val _enabledTools = MutableStateFlow(AppMode.entries.toSet())
    val enabledTools: StateFlow<Set<AppMode>> = _enabledTools.asStateFlow()

    private val _toolOrder = MutableStateFlow(AppMode.entries.toList())
    val toolOrder: StateFlow<List<AppMode>> = _toolOrder.asStateFlow()

    private val _autoStartCapture = MutableStateFlow(false)
    val autoStartCapture: StateFlow<Boolean> = _autoStartCapture.asStateFlow()

    private val _overlayTimeoutMs = MutableStateFlow(DEFAULT_OVERLAY_TIMEOUT_MS)
    val overlayTimeoutMs: StateFlow<Long> = _overlayTimeoutMs.asStateFlow()

    private val _activeTools = MutableStateFlow(AppMode.entries.toList())
    val activeTools: StateFlow<List<AppMode>> = _activeTools.asStateFlow()

    private val _accentColor = MutableStateFlow(Color(DEFAULT_ACCENT_COLOR))
    val accentColor: StateFlow<Color> = _accentColor.asStateFlow()

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

    // General session — remember last used tool across restarts
    private val _rememberLastTool = MutableStateFlow(false)
    val rememberLastTool: StateFlow<Boolean> = _rememberLastTool.asStateFlow()

    // App language
    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

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

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        dataStore = context.applicationContext.settingsDataStore

        scope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .collect { prefs ->
                val enabledStr = prefs[KEY_ENABLED_TOOLS]
                if (enabledStr != null) {
                    val parsed = enabledStr.split(",")
                        .mapNotNull { runCatching { AppMode.valueOf(it) }.getOrNull() }
                        .toSet()
                    _enabledTools.value = parsed.ifEmpty { AppMode.entries.toSet() }
                }

                val orderStr = prefs[KEY_TOOL_ORDER]
                if (orderStr != null) {
                    val parsed = orderStr.split(",")
                        .mapNotNull { runCatching { AppMode.valueOf(it) }.getOrNull() }
                    if (parsed.containsAll(AppMode.entries)) {
                        _toolOrder.value = parsed
                    }
                }

                _autoStartCapture.value = prefs[KEY_AUTO_START_CAPTURE] ?: false
                _overlayTimeoutMs.value = prefs[KEY_OVERLAY_TIMEOUT_MS] ?: DEFAULT_OVERLAY_TIMEOUT_MS
                _accentColor.value = Color(prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR)
                _themeMode.value = ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME_MODE] } ?: ThemeMode.DARK
                _overlayAtBottom.value = prefs[KEY_OVERLAY_AT_BOTTOM] ?: false
                _pinchWhileProjecting.value = prefs[KEY_PINCH_WHILE_PROJECTING] ?: false
                _rememberViewport.value = prefs[KEY_REMEMBER_VIEWPORT] ?: false
                _rememberLock.value = prefs[KEY_REMEMBER_LOCK] ?: false
                _rememberProjection.value = prefs[KEY_REMEMBER_PROJECTION] ?: false
                _rememberLastTool.value = prefs[KEY_REMEMBER_LAST_TOOL] ?: false
                _kbLayout.value = KbLayout.entries.firstOrNull { it.name == prefs[KEY_KB_LAYOUT] } ?: KbLayout.QWERTZ
                _kbTrackpointEnabled.value = prefs[KEY_KB_TRACKPOINT_ENABLED] ?: true
                _kbRepeatEnabled.value = prefs[KEY_KB_REPEAT_ENABLED] ?: true
                _kbFullscreen.value = prefs[KEY_KB_FULLSCREEN] ?: false
                _kbMouseBtnPos.value = KbMouseBtnPos.entries.firstOrNull { it.name == prefs[KEY_KB_MOUSE_BTN_POS] } ?: KbMouseBtnPos.LEFT
                _touchpadUseMouse.value = prefs[KEY_TOUCHPAD_USE_MOUSE] ?: false
                _touchpadTapToClick.value = prefs[KEY_TOUCHPAD_TAP_TO_CLICK] ?: true
                _touchpadTwoFingerTap.value = prefs[KEY_TOUCHPAD_TWO_FINGER_TAP] ?: true
                _appLanguage.value = AppLanguage.entries.firstOrNull { it.name == prefs[KEY_APP_LANGUAGE] } ?: AppLanguage.SYSTEM
                _macropadAmbientEnabled.value = prefs[KEY_MACROPAD_AMBIENT_ENABLED] ?: false
                _macropadAmbientDim.value = prefs[KEY_MACROPAD_AMBIENT_DIM] ?: 0f
                _macropadAmbientVignetteEnabled.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED] ?: false
                _macropadAmbientVignetteVisibleArea.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA] ?: 0.7f
                _macropadAmbientVignetteTransition.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION] ?: 0.5f
                _macropadAmbientVignetteOpacity.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY] ?: 0.6f
                _macropadAmbientVignetteColor.value = prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR] ?: 0xFF000000.toInt()
                _macropadAmbientVignetteShape.value = VignetteShape.entries.firstOrNull { it.name == prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE] } ?: VignetteShape.RADIAL
                _macropadAmbientPreview.value = prefs[KEY_MACROPAD_AMBIENT_PREVIEW] ?: false

                // MacroPad profiles
                val macropadProfilesJson = prefs[KEY_MACROPAD_PROFILES]
                if (macropadProfilesJson != null) {
                    val profiles = runCatching {
                        macropadJson.decodeFromString<List<PadProfile>>(macropadProfilesJson)
                    }.getOrElse { emptyList() }
                    val activeId = prefs[KEY_MACROPAD_ACTIVE_PROFILE_ID]
                    MacroPadState.loadFrom(profiles, activeId)
                }

                // MacroPad macros (global library)
                val macrosJson = prefs[KEY_MACROPAD_MACROS]
                if (macrosJson != null) {
                    val macros = runCatching {
                        macropadJson.decodeFromString<List<Macro>>(macrosJson)
                    }.getOrElse { emptyList() }
                    MacroState.loadFrom(macros)
                } else {
                    MacroState.loadFrom(emptyList())
                }

                // MacroPad macro folders
                val foldersJson = prefs[KEY_MACROPAD_MACRO_FOLDERS]
                if (foldersJson != null) {
                    val folders = runCatching {
                        macropadJson.decodeFromString<List<MacroFolder>>(foldersJson)
                    }.getOrElse { emptyList() }
                    MacroState.loadFoldersFrom(folders)
                } else {
                    MacroState.loadFoldersFrom(emptyList())
                }
            }
        }

        combine(_enabledTools, _toolOrder) { enabled, order ->
            order.filter { it in enabled }.ifEmpty { listOf(AppMode.entries.first()) }
        }.onEach { active ->
            _activeTools.value = active
            if (AppStateManager.currentMode.value !in active) {
                AppStateManager.setMode(active.first())
            }
        }.launchIn(scope)

        // One-shot: select the correct initial tool once DataStore data is available.
        // Uses the first tool in the active list, or the last used tool if that option is on.
        scope.launch {
            val prefs = dataStore.data.catch { emit(emptyPreferences()) }.first()
            val enabledStr = prefs[KEY_ENABLED_TOOLS]
            val enabled = if (enabledStr != null) {
                enabledStr.split(",")
                    .mapNotNull { runCatching { AppMode.valueOf(it) }.getOrNull() }
                    .toSet()
                    .ifEmpty { AppMode.entries.toSet() }
            } else AppMode.entries.toSet()
            val orderStr = prefs[KEY_TOOL_ORDER]
            val order = if (orderStr != null) {
                val parsed = orderStr.split(",")
                    .mapNotNull { runCatching { AppMode.valueOf(it) }.getOrNull() }
                if (parsed.containsAll(AppMode.entries)) parsed else AppMode.entries.toList()
            } else AppMode.entries.toList()
            val active = order.filter { it in enabled }.ifEmpty { listOf(AppMode.entries.first()) }
            val rememberLast = prefs[KEY_REMEMBER_LAST_TOOL] ?: false
            val lastTool = prefs[KEY_LAST_TOOL]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() }
            val startMode = if (rememberLast && lastTool != null && lastTool in active) lastTool else active.first()
            AppStateManager.setMode(startMode)
        }

        // Persist the active tool whenever the user navigates, so it can be restored on restart.
        AppStateManager.currentMode
            .drop(1)
            .onEach { mode -> dataStore.edit { prefs -> prefs[KEY_LAST_TOOL] = mode.name } }
            .launchIn(scope)
    }

    fun setEnabledTools(tools: Set<AppMode>) {
        require(tools.isNotEmpty()) { "At least one tool must remain enabled" }
        _enabledTools.value = tools
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_ENABLED_TOOLS] = tools.joinToString(",") { it.name }
            }
        }
    }

    fun setToolOrder(order: List<AppMode>) {
        _toolOrder.value = order
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_TOOL_ORDER] = order.joinToString(",") { it.name }
            }
        }
    }

    fun setAutoStartCapture(value: Boolean) {
        _autoStartCapture.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_AUTO_START_CAPTURE] = value
            }
        }
    }

    fun setOverlayTimeoutMs(value: Long) {
        _overlayTimeoutMs.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_OVERLAY_TIMEOUT_MS] = value
            }
        }
    }

    fun setAccentColor(color: Color) {
        _accentColor.value = color
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_ACCENT_COLOR] = color.toArgb()
            }
        }
    }

    fun setThemeMode(value: ThemeMode) {
        _themeMode.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = value.name } }
    }

    fun setOverlayAtBottom(value: Boolean) {
        _overlayAtBottom.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_OVERLAY_AT_BOTTOM] = value
            }
        }
    }

    fun setPinchWhileProjecting(value: Boolean) {
        _pinchWhileProjecting.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_PINCH_WHILE_PROJECTING] = value }
        }
    }

    fun setRememberViewport(value: Boolean) {
        _rememberViewport.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_REMEMBER_VIEWPORT] = value }
        }
    }

    fun setRememberLock(value: Boolean) {
        _rememberLock.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_REMEMBER_LOCK] = value }
        }
    }

    fun setRememberProjection(value: Boolean) {
        _rememberProjection.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_REMEMBER_PROJECTION] = value }
        }
    }

    fun setRememberLastTool(value: Boolean) {
        _rememberLastTool.value = value
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_REMEMBER_LAST_TOOL] = value }
        }
    }

    fun setAppLanguage(value: AppLanguage) {
        _appLanguage.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_APP_LANGUAGE] = value.name } }
    }

    fun setKbLayout(value: KbLayout) {
        _kbLayout.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_LAYOUT] = value.name } }
    }

    fun setKbTrackpointEnabled(value: Boolean) {
        _kbTrackpointEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_TRACKPOINT_ENABLED] = value } }
    }

    fun setKbRepeatEnabled(value: Boolean) {
        _kbRepeatEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_REPEAT_ENABLED] = value } }
    }

    fun setKbFullscreen(value: Boolean) {
        _kbFullscreen.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_FULLSCREEN] = value } }
    }

    fun setKbMouseBtnPos(value: KbMouseBtnPos) {
        _kbMouseBtnPos.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_KB_MOUSE_BTN_POS] = value.name } }
    }

    fun setTouchpadUseMouse(value: Boolean) {
        _touchpadUseMouse.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_USE_MOUSE] = value } }
    }

    fun setTouchpadTapToClick(value: Boolean) {
        _touchpadTapToClick.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_TAP_TO_CLICK] = value } }
    }

    fun setTouchpadTwoFingerTap(value: Boolean) {
        _touchpadTwoFingerTap.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TOUCHPAD_TWO_FINGER_TAP] = value } }
    }

    fun setMacropadAmbientEnabled(value: Boolean) {
        _macropadAmbientEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_ENABLED] = value } }
    }

    fun setMacropadAmbientDim(value: Float) {
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
        _macropadAmbientVignetteEnabled.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_ENABLED] = value } }
    }

    fun setMacropadAmbientVignetteVisibleArea(value: Float) {
        _macropadAmbientVignetteVisibleArea.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_VISIBLE_AREA] = value } }
    }

    fun setMacropadAmbientVignetteTransition(value: Float) {
        _macropadAmbientVignetteTransition.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_TRANSITION] = value } }
    }

    fun setMacropadAmbientVignetteOpacity(value: Float) {
        _macropadAmbientVignetteOpacity.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_OPACITY] = value } }
    }

    fun setMacropadAmbientVignetteColor(value: Int) {
        _macropadAmbientVignetteColor.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_COLOR] = value } }
    }

    fun setMacropadAmbientVignetteShape(value: VignetteShape) {
        _macropadAmbientVignetteShape.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_VIGNETTE_SHAPE] = value.name } }
    }

    fun setMacropadAmbientPreview(value: Boolean) {
        _macropadAmbientPreview.value = value
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MACROPAD_AMBIENT_PREVIEW] = value } }
    }

    /**
     * Persists the global macro library to DataStore.
     * Called by [MacroState] mutators whenever the macro list changes.
     */
    fun saveMacroData() {
        val macros = MacroState.macros.value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_MACROPAD_MACROS] = macropadJson.encodeToString(macros)
            }
        }
    }

    /**
     * Persists the macro folder list to DataStore.
     * Called by [MacroState] mutators whenever the folder list changes.
     */
    fun saveMacroFolderData() {
        val folders = MacroState.folders.value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_MACROPAD_MACRO_FOLDERS] = macropadJson.encodeToString(folders)
            }
        }
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
}

internal fun AppMode.displayNameResId(): Int = when (this) {
    AppMode.MIRROR -> R.string.tool_name_mirror
    AppMode.TOUCHPAD -> R.string.tool_name_touchpad
    AppMode.KEYBOARD -> R.string.tool_name_keyboard
    AppMode.MACROPAD -> R.string.tool_name_macropad
}
