package com.stormpanda.megingiard.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.KEY_AUTO_UPDATE_CHECK_ENABLED
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_NOTES
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_TAG
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "UpdateManager"

private const val DEFAULT_RELEASES_API_URL = "https://api.github.com/repos/stormpanda/megingiard/releases/latest"
private const val TIMEOUT_CONNECT_MS = 8000
private const val TIMEOUT_READ_MS = 10000

/** Auto update check frequency limit: 24 hours in milliseconds. */
const val AUTO_UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

/**
 * Manages background update checks against GitHub Releases.
 *
 * Exposes state via read-only [StateFlow]s and persists check timestamps and latest
 * release data into the shared DataStore owned by `SettingsManager`.
 */
object UpdateManager {
    private var dataStore: DataStore<Preferences>? = null
    private var scope: CoroutineScope? = null

    private val json = Json { ignoreUnknownKeys = true }

    private var isLoadedFromDataStore = false
    private var pendingCheckVersion: String? = null
    private var pendingCheckApiUrl: String? = null

    private val _autoUpdateCheckEnabled = MutableStateFlow(true)
    val autoUpdateCheckEnabled: StateFlow<Boolean> = _autoUpdateCheckEnabled.asStateFlow()

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _latestReleaseInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val latestReleaseInfo: StateFlow<AppReleaseInfo?> = _latestReleaseInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _lastCheckTime = MutableStateFlow(0L)
    val lastCheckTime: StateFlow<Long> = _lastCheckTime.asStateFlow()

    private val _checkError = MutableStateFlow<String?>(null)
    val checkError: StateFlow<String?> = _checkError.asStateFlow()

    internal fun init(
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ) {
        this.dataStore = dataStore
        this.scope = scope
        AppLog.d(TAG, "UpdateManager initialized with shared DataStore and scope")
    }

    internal fun loadFrom(
        prefs: Preferences,
        currentVersion: String = "",
    ) {
        val firstLoad = !isLoadedFromDataStore
        isLoadedFromDataStore = true
        _autoUpdateCheckEnabled.value = prefs[KEY_AUTO_UPDATE_CHECK_ENABLED] ?: true

        val tag = prefs[KEY_LATEST_RELEASE_TAG] ?: ""
        val url = prefs[KEY_LATEST_RELEASE_URL] ?: ""
        val notes = prefs[KEY_LATEST_RELEASE_NOTES] ?: ""

        if (tag.isNotBlank() && url.isNotBlank()) {
            val info = AppReleaseInfo(tagName = tag, htmlUrl = url, releaseNotes = notes)
            _latestReleaseInfo.value = info
            if (currentVersion.isNotBlank()) {
                _updateAvailable.value = SemVerComparator.isUpdateAvailable(currentVersion, tag)
            }
        }
        AppLog.d(
            TAG,
            "loadFrom: autoCheck=${_autoUpdateCheckEnabled.value}, lastCheck=${_lastCheckTime.value}, tag=$tag, updateAvailable=${_updateAvailable.value}",
        )

        if (firstLoad) {
            val pendingVer = pendingCheckVersion
            val pendingUrl = pendingCheckApiUrl
            if (pendingVer != null && pendingUrl != null) {
                pendingCheckVersion = null
                pendingCheckApiUrl = null
                AppLog.d(TAG, "DataStore load complete, executing pending initial update check")
                checkForUpdates(force = false, currentVersion = pendingVer, releasesApiUrl = pendingUrl)
            }
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        AppLog.d(TAG, "setAutoUpdateCheckEnabled: $enabled")
        _autoUpdateCheckEnabled.value = enabled
        scope?.launch {
            dataStore?.edit { prefs ->
                prefs[KEY_AUTO_UPDATE_CHECK_ENABLED] = enabled
            }
        }
    }

    /**
     * Checks GitHub Releases for a newer version of the app.
     *
     * @param force When `true`, ignores the 24-hour rate limit and auto-check toggle.
     * @param currentVersion Current app version name (e.g. "0.8.0-SNAPSHOT").
     * @param releasesApiUrl GitHub API endpoint URL (overridable for testing).
     */
    fun checkForUpdates(
        force: Boolean = false,
        currentVersion: String,
        releasesApiUrl: String = DEFAULT_RELEASES_API_URL,
    ) {
        if (!force && !isLoadedFromDataStore) {
            AppLog.d(TAG, "DataStore preferences not loaded yet, queuing initial update check")
            pendingCheckVersion = currentVersion
            pendingCheckApiUrl = releasesApiUrl
            return
        }

        val now = System.currentTimeMillis()
        if (!force) {
            if (!_autoUpdateCheckEnabled.value) {
                AppLog.d(TAG, "Automatic update check skipped: feature disabled in settings")
                return
            }
            if (now - _lastCheckTime.value < AUTO_UPDATE_CHECK_INTERVAL_MS) {
                AppLog.d(
                    TAG,
                    "Automatic update check skipped: last checked less than 24h ago in this session (${now - _lastCheckTime.value}ms ago)",
                )
                return
            }
        }

        if (_isChecking.value) {
            AppLog.d(TAG, "Update check already in progress, skipping duplicate call")
            return
        }

        AppLog.i(TAG, "Starting update check against $releasesApiUrl (force=$force, currentVersion=$currentVersion)")
        _isChecking.value = true
        _checkError.value = null

        scope?.launch {
            try {
                val releaseInfo = fetchLatestRelease(releasesApiUrl)
                val updateIsAvail = SemVerComparator.isUpdateAvailable(currentVersion, releaseInfo.tagName)

                _latestReleaseInfo.value = releaseInfo
                _updateAvailable.value = updateIsAvail
                _lastCheckTime.value = now
                _isChecking.value = false

                AppLog.i(TAG, "Update check completed: latestTag=${releaseInfo.tagName}, updateAvailable=$updateIsAvail")

                dataStore?.edit { prefs ->
                    prefs[KEY_LATEST_RELEASE_TAG] = releaseInfo.tagName
                    prefs[KEY_LATEST_RELEASE_URL] = releaseInfo.htmlUrl
                    prefs[KEY_LATEST_RELEASE_NOTES] = releaseInfo.releaseNotes
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Update check failed: ${e.javaClass.simpleName} - ${e.message}")
                _checkError.value = e.message ?: "Failed to check for updates"
                _isChecking.value = false
            }
        }
    }

    private suspend fun fetchLatestRelease(apiUrl: String): AppReleaseInfo =
        withContext(Dispatchers.IO) {
            val url = URL(apiUrl)
            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_CONNECT_MS
                    readTimeout = TIMEOUT_READ_MS
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Megingiard-App")
                }
            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    throw IllegalStateException("HTTP $responseCode: $errorText")
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<AppReleaseInfo>(body)
            } finally {
                connection.disconnect()
            }
        }
}
