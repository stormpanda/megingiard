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
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val SETTINGS_DATASTORE_NAME = "megingiard_settings"
private const val DEFAULT_OVERLAY_TIMEOUT_MS = 3_000L

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
    private val KEY_SAVED_LOCKED = booleanPreferencesKey("mirror_saved_locked")
    private val KEY_SAVED_PROJECTION = booleanPreferencesKey("mirror_saved_projection")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private var initialized = false

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

    private val _overlayAtBottom = MutableStateFlow(false)
    val overlayAtBottom: StateFlow<Boolean> = _overlayAtBottom.asStateFlow()

    // Mirror touch projection — pinch-to-zoom while projecting
    private val _pinchWhileProjecting = MutableStateFlow(false)
    val pinchWhileProjecting: StateFlow<Boolean> = _pinchWhileProjecting.asStateFlow()

    // Mirror session state persistence — whether each aspect is remembered
    private val _rememberViewport = MutableStateFlow(false)
    val rememberViewport: StateFlow<Boolean> = _rememberViewport.asStateFlow()

    private val _rememberLock = MutableStateFlow(false)
    val rememberLock: StateFlow<Boolean> = _rememberLock.asStateFlow()

    private val _rememberProjection = MutableStateFlow(false)
    val rememberProjection: StateFlow<Boolean> = _rememberProjection.asStateFlow()

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
                _overlayAtBottom.value = prefs[KEY_OVERLAY_AT_BOTTOM] ?: false
                _pinchWhileProjecting.value = prefs[KEY_PINCH_WHILE_PROJECTING] ?: false
                _rememberViewport.value = prefs[KEY_REMEMBER_VIEWPORT] ?: false
                _rememberLock.value = prefs[KEY_REMEMBER_LOCK] ?: false
                _rememberProjection.value = prefs[KEY_REMEMBER_PROJECTION] ?: false
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
    AppMode.MEDIA -> R.string.tool_name_media
    AppMode.TOUCHPAD -> R.string.tool_name_touchpad
}
