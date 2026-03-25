package com.stormpanda.megingiard.settings

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stormpanda.megingiard.AppMode
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val scope = CoroutineScope(SupervisorJob())
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

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        dataStore = context.applicationContext.settingsDataStore

        scope.launch {
            dataStore.data.collect { prefs ->
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
}

internal fun AppMode.displayNameResId(): Int = when (this) {
    AppMode.MIRROR -> R.string.tool_name_mirror
    AppMode.MEDIA -> R.string.tool_name_media
    AppMode.TOUCHPAD -> R.string.tool_name_touchpad
}
