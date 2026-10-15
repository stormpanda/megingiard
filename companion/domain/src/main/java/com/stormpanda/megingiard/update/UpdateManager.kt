package com.stormpanda.megingiard.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.KEY_AUTO_UPDATE_CHECK_ENABLED
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_TAG
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_URL
import com.stormpanda.megingiard.settings.updateSettingPref
import kotlinx.coroutines.CancellationException
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

    private val stateLock = Any()

    @Volatile
    private var isLoadedFromDataStore = false
    private var currentAppVersion: String = ""
    private var pendingCheckVersion: String? = null
    private var pendingCheckApiUrl: String? = null
    private var lastCheckTimeMs = 0L

    private val _autoUpdateCheckEnabled = MutableStateFlow(true)
    val autoUpdateCheckEnabled: StateFlow<Boolean> = _autoUpdateCheckEnabled.asStateFlow()

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _latestReleaseInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val latestReleaseInfo: StateFlow<AppReleaseInfo?> = _latestReleaseInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _checkError = MutableStateFlow<String?>(null)
    val checkError: StateFlow<String?> = _checkError.asStateFlow()

    private fun applyReleaseInfo(info: AppReleaseInfo?) {
        _latestReleaseInfo.value = info
        _updateAvailable.value =
            if (info != null && currentAppVersion.isNotBlank()) {
                SemVerComparator.isUpdateAvailable(currentAppVersion, info.tagName)
            } else {
                false
            }
    }

    internal fun init(
        dataStore: DataStore<Preferences>,
        scope: CoroutineScope,
    ) {
        synchronized(stateLock) {
            this.dataStore = dataStore
            this.scope = scope
        }
        AppLog.d(TAG, "UpdateManager initialized with shared DataStore and scope")
    }

    fun resetForTesting(
        dataStore: DataStore<Preferences>? = null,
        scope: CoroutineScope? = null,
    ) {
        synchronized(stateLock) {
            this.dataStore = dataStore
            this.scope = scope
            _autoUpdateCheckEnabled.value = true
            currentAppVersion = ""
            applyReleaseInfo(null)
            _isChecking.value = false
            lastCheckTimeMs = 0L
            _checkError.value = null
            isLoadedFromDataStore = false
            pendingCheckVersion = null
            pendingCheckApiUrl = null
        }
    }

    internal fun loadFrom(
        prefs: Preferences,
        currentVersion: String = "",
    ) {
        var pendingVer: String? = null
        var pendingUrl: String? = null
        synchronized(stateLock) {
            prefs[KEY_AUTO_UPDATE_CHECK_ENABLED]?.let { _autoUpdateCheckEnabled.value = it }
            if (currentVersion.isNotBlank()) {
                currentAppVersion = currentVersion
            }
            if (isLoadedFromDataStore) {
                if (currentVersion.isNotBlank()) {
                    applyReleaseInfo(_latestReleaseInfo.value)
                }
                return
            }
            isLoadedFromDataStore = true

            val tag = prefs[KEY_LATEST_RELEASE_TAG] ?: ""
            val url = prefs[KEY_LATEST_RELEASE_URL] ?: ""

            if (tag.isNotBlank() && url.isNotBlank()) {
                applyReleaseInfo(AppReleaseInfo(tagName = tag, htmlUrl = url))
            } else if (currentVersion.isNotBlank()) {
                applyReleaseInfo(_latestReleaseInfo.value)
            }
            AppLog.d(
                TAG,
                "loadFrom: autoCheck=${_autoUpdateCheckEnabled.value}, lastCheck=$lastCheckTimeMs, tag=$tag, updateAvailable=${_updateAvailable.value}",
            )

            pendingVer = pendingCheckVersion
            pendingUrl = pendingCheckApiUrl
            pendingCheckVersion = null
            pendingCheckApiUrl = null
        }

        val ver = pendingVer
        val url = pendingUrl
        if (ver != null && url != null) {
            AppLog.d(TAG, "DataStore load complete, executing pending initial update check")
            checkForUpdates(force = false, currentVersion = ver, releasesApiUrl = url)
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        updateSettingPref(
            KEY_AUTO_UPDATE_CHECK_ENABLED,
            enabled,
            _autoUpdateCheckEnabled,
            scope,
            dataStore,
            TAG,
            "setAutoUpdateCheckEnabled",
        )
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
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            if (currentVersion.isNotBlank()) {
                currentAppVersion = currentVersion
                applyReleaseInfo(_latestReleaseInfo.value)
            }

            if (!force && !isLoadedFromDataStore) {
                AppLog.d(TAG, "DataStore preferences not loaded yet, queuing initial update check")
                pendingCheckVersion = currentVersion
                pendingCheckApiUrl = releasesApiUrl
                return
            }

            if (!force) {
                if (!_autoUpdateCheckEnabled.value) {
                    AppLog.d(TAG, "Automatic update check skipped: feature disabled in settings")
                    return
                }
                if (now - lastCheckTimeMs < AUTO_UPDATE_CHECK_INTERVAL_MS) {
                    AppLog.d(
                        TAG,
                        "Automatic update check skipped: last checked less than 24h ago in this session (${now - lastCheckTimeMs}ms ago)",
                    )
                    return
                }
            }
        }

        if (!_isChecking.compareAndSet(false, true)) {
            AppLog.d(TAG, "Update check already in progress, skipping duplicate call")
            return
        }

        AppLog.i(TAG, "Starting update check against $releasesApiUrl (force=$force, currentVersion=$currentVersion)")
        _checkError.value = null

        scope?.launch {
            try {
                val releaseInfo = fetchLatestRelease(releasesApiUrl)
                synchronized(stateLock) {
                    applyReleaseInfo(releaseInfo)
                    lastCheckTimeMs = now
                }
                _isChecking.value = false

                AppLog.i(
                    TAG,
                    "Update check completed: latestTag=${releaseInfo.tagName}, updateAvailable=${_updateAvailable.value}",
                )

                dataStore?.edit { prefs ->
                    prefs[KEY_LATEST_RELEASE_TAG] = releaseInfo.tagName
                    prefs[KEY_LATEST_RELEASE_URL] = releaseInfo.htmlUrl
                }
            } catch (e: CancellationException) {
                _isChecking.value = false
                throw e
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
