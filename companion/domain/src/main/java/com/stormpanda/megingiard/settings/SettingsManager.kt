package com.stormpanda.megingiard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.config.ConfigManager
import com.stormpanda.megingiard.config.InternalBackup
import com.stormpanda.megingiard.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

private val backupsJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private const val SETTINGS_DATASTORE_NAME = "megingiard_settings"

/** Per-app language preference. [SYSTEM] follows the device locale. */
enum class AppLanguage { SYSTEM, EN, DE, ZH_TW }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler {
            emptyPreferences()
        },
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

    @Volatile
    private var autoBackupTriggered = false

    private var lastBackupsJsonStr: String? = null

    private val _internalBackups = MutableStateFlow<List<InternalBackup>>(emptyList())
    val internalBackups: StateFlow<List<InternalBackup>> = _internalBackups.asStateFlow()

    private val _excludeFromRecents = MutableStateFlow(false)
    val excludeFromRecents: StateFlow<Boolean> = _excludeFromRecents.asStateFlow()

    private val _accentColor = MutableStateFlow(DEFAULT_ACCENT_COLOR)
    val accentColor: StateFlow<Int> = _accentColor.asStateFlow()

    private val _customAccentColor = MutableStateFlow(DEFAULT_ACCENT_COLOR)
    val customAccentColor: StateFlow<Int> = _customAccentColor.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _overlayAtBottom = MutableStateFlow(false)
    val overlayAtBottom: StateFlow<Boolean> = _overlayAtBottom.asStateFlow()

    private val _overlayFadeOut = MutableStateFlow(false)
    val overlayFadeOut: StateFlow<Boolean> = _overlayFadeOut.asStateFlow()

    private val _steamGridDbApiToken = MutableStateFlow("")
    val steamGridDbApiToken: StateFlow<String> = _steamGridDbApiToken.asStateFlow()

    const val CURRENT_WELCOME_TOUR_VERSION = 1

    private val _showMacroEditorTutorial = MutableStateFlow(true)
    val showMacroEditorTutorial: StateFlow<Boolean> = _showMacroEditorTutorial.asStateFlow()

    private val _welcomeTourCompletedVersion = MutableStateFlow(0)
    val welcomeTourCompletedVersion: StateFlow<Int> = _welcomeTourCompletedVersion.asStateFlow()

    // Mirror settings live in [MirrorSettings] (pinch-while-projecting + remember-* flags + session save/restore).
    // Keyboard settings live in [KeyboardSettings].
    // Touchpad settings live in [TouchpadSettings].
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
                val prefs =
                    withTimeoutOrNull(DATASTORE_BOOTSTRAP_TIMEOUT_MS) {
                        dataStore.data.first()
                    }
                if (prefs != null) {
                    val level =
                        AppLog.Level.entries.firstOrNull { it.name == prefs[KEY_LOG_LEVEL] }
                            ?: AppLog.Level.WARN
                    _logLevel.value = level
                    AppLog.level = level

                    _themeMode.value = ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME_MODE] } ?: ThemeMode.DARK
                    _accentColor.value = prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
                    _customAccentColor.value = prefs[KEY_CUSTOM_ACCENT_COLOR] ?: prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
                    _steamGridDbApiToken.value = prefs[KEY_STEAMGRIDDB_API_TOKEN] ?: ""
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
        MirrorSettings.init(dataStore, scope)
        MacroPadSettings.init(dataStore, scope)
        UpdateManager.init(dataStore, scope)

        scope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .collect { prefs ->
                    AppLog.i(TAG, "settings loaded from DataStore")

                    _excludeFromRecents.value = prefs[KEY_EXCLUDE_FROM_RECENTS] ?: false
                    _accentColor.value = prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
                    _customAccentColor.value = prefs[KEY_CUSTOM_ACCENT_COLOR] ?: prefs[KEY_ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
                    _themeMode.value = ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME_MODE] } ?: ThemeMode.DARK
                    _overlayAtBottom.value = prefs[KEY_OVERLAY_AT_BOTTOM] ?: false
                    _overlayFadeOut.value = prefs[KEY_OVERLAY_FADE_OUT] ?: false
                    _steamGridDbApiToken.value = prefs[KEY_STEAMGRIDDB_API_TOKEN] ?: ""

                    _showMacroEditorTutorial.value = prefs[KEY_SHOW_MACRO_EDITOR_TUTORIAL] ?: true
                    _welcomeTourCompletedVersion.value = prefs[KEY_WELCOME_TOUR_COMPLETED_VERSION] ?: 0
                    MirrorSettings.loadFrom(prefs)
                    KeyboardSettings.loadFrom(prefs)
                    TouchpadSettings.loadFrom(prefs)
                    _appLanguage.value = AppLanguage.entries.firstOrNull { it.name == prefs[KEY_APP_LANGUAGE] } ?: AppLanguage.SYSTEM
                    _logLevel.value = AppLog.Level.entries.firstOrNull { it.name == prefs[KEY_LOG_LEVEL] } ?: AppLog.Level.WARN
                    AppLog.level = _logLevel.value
                    MacroPadSettings.loadFrom(prefs)
                    UpdateManager.loadFrom(prefs)

                    val backupsJsonStr = prefs[KEY_INTERNAL_BACKUPS]
                    if (backupsJsonStr != lastBackupsJsonStr) {
                        lastBackupsJsonStr = backupsJsonStr
                        _internalBackups.value = decodeBackups(backupsJsonStr)
                    }

                    if (!autoBackupTriggered) {
                        autoBackupTriggered = true
                        triggerAutoBackupIfNeeded(context.applicationContext)
                    }
                }
        }
    }

    private val optionalDataStore: DataStore<Preferences>?
        get() = if (::dataStore.isInitialized) dataStore else null

    fun setShowMacroEditorTutorial(value: Boolean) {
        updateSettingPref(
            KEY_SHOW_MACRO_EDITOR_TUTORIAL,
            value,
            _showMacroEditorTutorial,
            scope,
            optionalDataStore,
            TAG,
            "setShowMacroEditorTutorial",
        )
    }

    fun setWelcomeTourCompletedVersion(value: Int) {
        updateSettingPref(
            KEY_WELCOME_TOUR_COMPLETED_VERSION,
            value,
            _welcomeTourCompletedVersion,
            scope,
            optionalDataStore,
            TAG,
            "setWelcomeTourCompletedVersion",
        )
    }

    fun resetAllTutorials() {
        AppLog.d(TAG, "resetAllTutorials()")
        _showMacroEditorTutorial.value = true
        _welcomeTourCompletedVersion.value = 0
        scope.launch {
            optionalDataStore?.edit { prefs ->
                prefs[KEY_SHOW_MACRO_EDITOR_TUTORIAL] = true
                prefs[KEY_WELCOME_TOUR_COMPLETED_VERSION] = 0
            }
        }
    }

    fun setExcludeFromRecents(value: Boolean) {
        updateSettingPref(KEY_EXCLUDE_FROM_RECENTS, value, _excludeFromRecents, scope, optionalDataStore, TAG, "setExcludeFromRecents")
    }

    @Volatile
    var onThemeChangedListener: (() -> Unit)? = null

    @Volatile
    var onSettingsChangedListener: (() -> Unit)? = null

    fun setAccentColor(argb: Int) {
        updateSettingPref(KEY_ACCENT_COLOR, argb, _accentColor, scope, optionalDataStore, TAG, "setAccentColor")
        onThemeChangedListener?.invoke()
    }

    fun setCustomAccentColor(argb: Int) {
        updateSettingPref(KEY_CUSTOM_ACCENT_COLOR, argb, _customAccentColor, scope, optionalDataStore, TAG, "setCustomAccentColor")
    }

    fun setThemeMode(value: ThemeMode) {
        updateEnumSettingPref(
            KEY_THEME_MODE,
            value,
            _themeMode,
            scope,
            optionalDataStore,
            TAG,
            "setThemeMode",
            onChanged = { onThemeChangedListener?.invoke() },
        )
    }

    fun setOverlayAtBottom(value: Boolean) {
        updateSettingPref(KEY_OVERLAY_AT_BOTTOM, value, _overlayAtBottom, scope, optionalDataStore, TAG, "setOverlayAtBottom")
    }

    fun setOverlayFadeOut(value: Boolean) {
        updateSettingPref(KEY_OVERLAY_FADE_OUT, value, _overlayFadeOut, scope, optionalDataStore, TAG, "setOverlayFadeOut")
    }

    fun setSteamGridDbApiToken(value: String) {
        updateSettingPref(
            KEY_STEAMGRIDDB_API_TOKEN,
            value,
            _steamGridDbApiToken,
            scope,
            optionalDataStore,
            TAG,
            "setSteamGridDbApiToken(redacted)",
            onChanged = { onSettingsChangedListener?.invoke() },
        )
    }

    // Mirror setters + session save/restore live in [MirrorSettings].

    fun setAppLanguage(value: AppLanguage) {
        updateEnumSettingPref(KEY_APP_LANGUAGE, value, _appLanguage, scope, optionalDataStore, TAG, "setAppLanguage")
    }

    fun setLogLevel(value: AppLog.Level) {
        updateEnumSettingPref(
            KEY_LOG_LEVEL,
            value,
            _logLevel,
            scope,
            optionalDataStore,
            TAG,
            "setLogLevel",
            onChanged = { AppLog.level = value },
        )
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
                entries[key.name] =
                    when (raw) {
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
                    @Suppress("UNCHECKED_CAST")
                    when (key) {
                        in BOOLEAN_KEYS -> {
                            element.booleanOrNull?.let { prefs[key as Preferences.Key<Boolean>] = it }
                        }

                        in INT_KEYS -> {
                            element.intOrNull?.let { prefs[key as Preferences.Key<Int>] = it }
                        }

                        in FLOAT_KEYS -> {
                            element.floatOrNull?.let { prefs[key as Preferences.Key<Float>] = it }
                        }

                        in STRING_KEYS -> {
                            element.contentOrNull?.let { prefs[key as Preferences.Key<String>] = it }
                        }
                    }
                }
            }
        }
    }

    private fun decodeBackups(jsonStr: String?): List<InternalBackup> {
        if (jsonStr == null) return emptyList()
        return runCatching {
            backupsJson.decodeFromString<List<InternalBackup>>(jsonStr)
        }.getOrElse { e ->
            AppLog.w(TAG, "Failed to decode internal backups list: ${e.javaClass.simpleName} - ${e.message}")
            emptyList()
        }
    }

    suspend fun saveBackup(backup: InternalBackup) {
        AppLog.d(TAG, "saveBackup: date=${backup.dateString}")
        dataStore.edit { prefs ->
            val currentList = decodeBackups(prefs[KEY_INTERNAL_BACKUPS])
            val newList =
                (currentList.filter { it.dateString != backup.dateString } + backup)
                    .sortedByDescending { it.timestampMs }
                    .take(5)
            prefs[KEY_INTERNAL_BACKUPS] = backupsJson.encodeToString(newList)
        }
    }

    private fun triggerAutoBackupIfNeeded(context: Context) {
        scope.launch {
            try {
                val currentDateStr =
                    LocalDate
                        .now()
                        .toString()
                val alreadyHasBackup = _internalBackups.value.any { it.dateString == currentDateStr }
                if (alreadyHasBackup) {
                    AppLog.d(TAG, "Auto-backup already exists for today ($currentDateStr), skipping.")
                    return@launch
                }

                AppLog.i(TAG, "Creating automatic daily configuration backup for $currentDateStr")
                val metadata =
                    ConfigManager.defaultMetadata(context).copy(
                        author = null,
                        description = null,
                    )
                val export = ConfigManager.buildExport(metadata)
                val backup =
                    InternalBackup(
                        dateString = currentDateStr,
                        timestampMs = System.currentTimeMillis(),
                        export = export,
                    )
                saveBackup(backup)
                AppLog.i(TAG, "Automatic daily backup saved successfully.")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to create automatic daily backup", e)
            }
        }
    }
}
