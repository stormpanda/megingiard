package com.stormpanda.megingiard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    }
)

private const val DEFAULT_ACCENT_COLOR: Int = (0xFFCC0000).toInt()

private const val TAG = "SettingsManager"
// Max wait for DataStore on the main thread during the synchronous bootstrap in init().
// If DataStore does not respond within this window, the default log level (WARN) is kept
// and startup continues normally — preventing an ANR on slow/unavailable storage.
private const val DATASTORE_BOOTSTRAP_TIMEOUT_MS = 500L

object SettingsManager {
    // Preference keys + section maps live in SettingsKeys.kt (same package, internal).

    // App-lifetime scope: intentionally never cancelled — this singleton lives for the
    // duration of the process. Cancellation is handled by process termination.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private var initialized = false

    private val _autoStartCapture = MutableStateFlow(false)
    val autoStartCapture: StateFlow<Boolean> = _autoStartCapture.asStateFlow()

    private val _accentColor = MutableStateFlow(DEFAULT_ACCENT_COLOR)
    val accentColor: StateFlow<Int> = _accentColor.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _overlayAtBottom = MutableStateFlow(false)
    val overlayAtBottom: StateFlow<Boolean> = _overlayAtBottom.asStateFlow()

    private val _showMirrorControlLabels = MutableStateFlow(false)
    val showMirrorControlLabels: StateFlow<Boolean> = _showMirrorControlLabels.asStateFlow()

    private val _showFullscreenExitHints = MutableStateFlow(true)
    val showFullscreenExitHints: StateFlow<Boolean> = _showFullscreenExitHints.asStateFlow()

    // Mirror settings live in [MirrorSettings] (pinch-while-projecting + remember-* flags + session save/restore).
    // Keyboard settings live in [KeyboardSettings].
    // Touchpad settings live in [TouchpadSettings].
    // MacroPad background display settings live in [BackgroundSettings].
    // MacroPad recording dialogs + gamepad-swap + macropad profile data live in [MacroPadSettings].

    // App language
    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // Logging
    private val _logLevel = MutableStateFlow(AppLog.Level.WARN)
    val logLevel: StateFlow<AppLog.Level> = _logLevel.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        dataStore = context.applicationContext.settingsDataStore

        // Apply the persisted log level synchronously before returning so that
        // code running immediately after init() (e.g. SignatureGuard) already
        // logs at the user-configured level.  A bounded timeout prevents ANR on
        // pathological I/O — if DataStore does not respond within the window, the
        // default WARN level is retained and startup proceeds normally.
        runBlocking(Dispatchers.IO) {
            try {
                val prefs = withTimeoutOrNull(DATASTORE_BOOTSTRAP_TIMEOUT_MS) {
                    dataStore.data.first()
                }
                if (prefs != null) {
                    val level = AppLog.Level.entries.firstOrNull { it.name == prefs[KEY_LOG_LEVEL] }
                        ?: AppLog.Level.WARN
                    _logLevel.value = level
                    AppLog.level = level
                } else {
                    AppLog.w(TAG, "DataStore bootstrap timed out — retaining default log level")
                }
            } catch (e: Exception) {
                AppLog.w(
                    TAG,
                    "DataStore bootstrap failed (${e.javaClass.simpleName}): ${e.message ?: "no message"} - retaining default log level",
                )
            }
        }

        // Hand the shared DataStore + scope to feature-scoped sub-managers so they
        // can persist their own settings without each one opening its own DataStore.
        KeyboardSettings.init(dataStore, scope)
        TouchpadSettings.init(dataStore, scope)
        BackgroundSettings.init(dataStore, scope)
        MirrorSettings.init(dataStore, scope)
        MacroPadSettings.init(dataStore, scope)

        scope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .collect { prefs ->
                AppLog.i(TAG, "settings loaded from DataStore")

                _autoStartCapture.value = prefs[KEY_AUTO_START_CAPTURE] ?: false
                _accentColor.value = prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
                _themeMode.value = ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME_MODE] } ?: ThemeMode.DARK
                _overlayAtBottom.value = prefs[KEY_OVERLAY_AT_BOTTOM] ?: false
                _showMirrorControlLabels.value = prefs[KEY_SHOW_MIRROR_CONTROL_LABELS] ?: false
                _showFullscreenExitHints.value = prefs[KEY_SHOW_FULLSCREEN_EXIT_HINTS] ?: true
                MirrorSettings.loadFrom(prefs)
                KeyboardSettings.loadFrom(prefs)
                TouchpadSettings.loadFrom(prefs)
                _appLanguage.value = AppLanguage.entries.firstOrNull { it.name == prefs[KEY_APP_LANGUAGE] } ?: AppLanguage.SYSTEM
                _logLevel.value = AppLog.Level.entries.firstOrNull { it.name == prefs[KEY_LOG_LEVEL] } ?: AppLog.Level.WARN
                AppLog.level = _logLevel.value
                BackgroundSettings.loadFrom(prefs)
                MacroPadSettings.loadFrom(prefs)
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

    fun setShowMirrorControlLabels(value: Boolean) {
        AppLog.d(TAG, "setShowMirrorControlLabels($value)")
        _showMirrorControlLabels.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_SHOW_MIRROR_CONTROL_LABELS] = value
            }
        }
    }

    fun setShowFullscreenExitHints(value: Boolean) {
        AppLog.d(TAG, "setShowFullscreenExitHints($value)")
        _showFullscreenExitHints.value = value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_SHOW_FULLSCREEN_EXIT_HINTS] = value
            }
        }
    }

    // Mirror setters + session save/restore live in [MirrorSettings].

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

    // Keyboard setters live in [KeyboardSettings]; touchpad setters in [TouchpadSettings].

    // MacroPad background setters live in [BackgroundSettings].
    // MacroPad recording-dialog flags + gamepad-swap setter + saveMacroPadData live in [MacroPadSettings].

    // Mirror session state save/restore lives in [MirrorSettings].

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
