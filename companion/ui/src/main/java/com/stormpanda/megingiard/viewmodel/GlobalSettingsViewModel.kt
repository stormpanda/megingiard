package com.stormpanda.megingiard.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.media.SteamGridDbClient
import com.stormpanda.megingiard.media.SteamGridDbException
import com.stormpanda.megingiard.privd.PrivdBootstrapper
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "GlobalSettingsVM"

enum class SteamGridDbTestStatus(
    val labelResId: Int,
) {
    IDLE(R.string.settings_steamgriddb_test_btn),
    TESTING(R.string.settings_steamgriddb_status_testing),
    CONNECTED(R.string.settings_steamgriddb_status_connected),
    INVALID_TOKEN(R.string.settings_steamgriddb_status_invalid),
    OFFLINE(R.string.settings_steamgriddb_status_offline),
    RATE_LIMITED(R.string.settings_steamgriddb_status_rate_limited),
    UNREACHABLE(R.string.settings_steamgriddb_status_unreachable),
    ERROR(R.string.settings_steamgriddb_status_error),
}

private fun Throwable.toSteamGridDbTestStatus(): SteamGridDbTestStatus =
    when (this) {
        is SteamGridDbException.Offline -> SteamGridDbTestStatus.OFFLINE
        is SteamGridDbException.RateLimited -> SteamGridDbTestStatus.RATE_LIMITED
        is SteamGridDbException.ServiceUnavailable -> SteamGridDbTestStatus.UNREACHABLE
        is SteamGridDbException.Unauthorized -> SteamGridDbTestStatus.INVALID_TOKEN
        else -> SteamGridDbTestStatus.ERROR
    }

/**
 * ViewModel for [com.stormpanda.megingiard.settings.GlobalSettingsScreen] — coordinates
 * asynchronous operations (SteamGridDB token validation and Privileged Mode bootstrap/connection).
 */
class GlobalSettingsViewModel : ViewModel() {
    private val _isRootBridgeAvailable = MutableStateFlow<Boolean?>(null)
    val isRootBridgeAvailable: StateFlow<Boolean?> = _isRootBridgeAvailable.asStateFlow()

    private val _steamGridDbTestStatus = MutableStateFlow(SteamGridDbTestStatus.IDLE)
    val steamGridDbTestStatus: StateFlow<SteamGridDbTestStatus> = _steamGridDbTestStatus.asStateFlow()

    fun setSteamGridDbApiToken(value: String) {
        SettingsManager.setSteamGridDbApiToken(value)
        _steamGridDbTestStatus.value = SteamGridDbTestStatus.IDLE
    }

    fun testSteamGridDbConnection(token: String) {
        if (token.isBlank()) {
            _steamGridDbTestStatus.value = SteamGridDbTestStatus.INVALID_TOKEN
            return
        }
        viewModelScope.launch {
            _steamGridDbTestStatus.value = SteamGridDbTestStatus.TESTING
            _steamGridDbTestStatus.value =
                SteamGridDbClient.validateToken(token.trim()).fold(
                    onSuccess = { SteamGridDbTestStatus.CONNECTED },
                    onFailure = { it.toSteamGridDbTestStatus() },
                )
        }
    }

    /**
     * Initiates a connection to the daemon socket asynchronously on [Dispatchers.IO].
     * The result is reflected in [PrivdManager.state] — optional [onComplete] callback on Main.
     */
    fun privdConnect(
        context: Context,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        AppLog.i(TAG, "privdConnect()")
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val success = PrivdManager.connect(appContext)
            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(success)
                }
            }
        }
    }

    /**
     * Pair with the device's ADB Wireless-Debugging service.
     * Result is delivered via [onResult] on the main thread.
     */
    fun privdPair(
        context: Context,
        host: String,
        port: Int,
        code: String,
        onResult: (Boolean) -> Unit,
    ) {
        AppLog.i(TAG, "privdPair($host:$port)")
        val appContext = context.applicationContext
        viewModelScope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    PrivdBootstrapper.pair(appContext, host, port, code)
                }
            onResult(ok)
        }
    }

    /**
     * After pairing succeeded: connect directly to [host], push the
     * daemon binary, spawn the daemon, then verify with [PrivdManager.connect].
     * The ADB connect port is detected automatically from the system property.
     * On success, persists the auto-connect flag so future app starts skip the wizard.
     */
    fun privdBootstrap(
        context: Context,
        host: String,
        onResult: (Boolean) -> Unit,
    ) {
        AppLog.i(TAG, "privdBootstrap()")
        val appContext = context.applicationContext
        viewModelScope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    PrivdBootstrapper.bootstrapAndConnect(appContext, host)
                }
            onResult(ok)
        }
    }

    fun checkPrivilegedModeStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRootBridgeAvailable.value = PrivdManager.isRootBridgeAvailable
            AppLog.d(
                TAG,
                "checkPrivilegedModeStatus: isRootBridgeAvailable=${_isRootBridgeAvailable.value}",
            )
        }
    }
}
