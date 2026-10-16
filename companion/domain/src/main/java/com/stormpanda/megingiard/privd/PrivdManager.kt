package com.stormpanda.megingiard.privd

import android.content.Context
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * Lifecycle state of the privileged-mode subsystem.
 */
enum class PrivdState {
    /** Privileged Mode is off (no connection, no in-flight bootstrap). */
    OFF,

    /** Pairing / pushing the binary / spawning the daemon over ADB Wireless Debugging. */
    BOOTSTRAPPING,

    /** Connection attempt in progress. */
    CONNECTING,

    /** Daemon is reachable and authenticated. */
    RUNNING,

    /** Last connect attempt failed — see [PrivdManager.lastError] for details. */
    FAILED,
}

private const val TAG = "PrivdManager"

/**
 * Reasons why a connect attempt may fail. The UI maps each enum value to a
 * localized string resource — strings live in `strings.xml`, not here.
 */
enum class PrivdError {
    /** The loopback socket is not reachable — daemon is probably not running. */
    DAEMON_UNREACHABLE,

    /** ADB Wireless-Debugging pairing rejected the entered code or timed out. */
    PAIRING_FAILED,

    /** ADB connect after pairing failed (peer offline, key mismatch, …). */
    ADB_CONNECT_FAILED,

    /** Pushing the daemon binary into /data/local/tmp failed. */
    BOOTSTRAP_PUSH_FAILED,

    /** Spawning the daemon process via `adb shell` failed. */
    BOOTSTRAP_SPAWN_FAILED,

    /**
     * Provisioning the per-install HMAC key to the daemon failed during bootstrap.
     * Cause: key generation error or the `--provision` ADB shell command returned
     * a non-zero exit. Re-run the Privileged Mode setup wizard to retry.
     */
    BOOTSTRAP_PROVISION_FAILED,

    /** ADB pairing is explicitly required (credentials/key rejected by adbd). */
    ADB_PAIRING_REQUIRED,
}

/**
 * Top-level controller for Privileged Mode.
 *
 * Derives the user-visible [state] stream reactively from [PrivdClient.state],
 * bootstrap status, and [lastError].
 *
 * Per AGENTS.md §7.1: backing `MutableStateFlow`s are private; only the
 * read-only [state] / [lastError] surfaces escape this object.
 */
object PrivdManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val _isBootstrapping = MutableStateFlow(false)
    private val _testStateOverride = MutableStateFlow<PrivdState?>(null)

    private val _lastError = MutableStateFlow<PrivdError?>(null)
    val lastError: StateFlow<PrivdError?> = _lastError.asStateFlow()

    val state: StateFlow<PrivdState> =
        combine(
            PrivdClient.state,
            _isBootstrapping,
            _lastError,
            _testStateOverride,
        ) { clientState, bootstrapping, error, testOverride ->
            when {
                testOverride != null -> testOverride
                clientState == PrivdConnectionState.CONNECTED -> PrivdState.RUNNING
                bootstrapping -> PrivdState.BOOTSTRAPPING
                clientState == PrivdConnectionState.CONNECTING -> PrivdState.CONNECTING
                error != null -> PrivdState.FAILED
                else -> PrivdState.OFF
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = PrivdState.OFF,
        )

    internal fun setStateForTesting(state: PrivdState?) {
        if (state == PrivdState.OFF || state == null) {
            _isBootstrapping.value = false
            _lastError.value = null
        }
        _testStateOverride.value = state
    }

    val isRootBridgeAvailable: Boolean get() = PServiceBridge.isAvailable()

    fun connect(context: Context): Boolean {
        AppLog.i(TAG, "connect() called (current state=${state.value})")
        _testStateOverride.value = null
        _isBootstrapping.value = false
        _lastError.value = null
        val ok = PrivdClient.connect()
        if (ok) {
            AppLog.i(TAG, "Privileged Mode is RUNNING")
            return true
        }

        // Direct connect failed. Attempt Tier-1 fast-track via PServer root bridge
        if (PServiceBridge.isAvailable()) {
            AppLog.i(TAG, "Direct connect failed. PServer root bridge is available — attempting fast-track bootstrap.")
            _isBootstrapping.value = true
            val pOk = PServerBootstrapper.bootstrap(context)
            _isBootstrapping.value = false
            if (pOk) {
                AppLog.i(TAG, "PServer fast-track bootstrap succeeded — Privileged Mode is RUNNING")
                return true
            }
            AppLog.w(TAG, "PServer fast-track bootstrap failed — falling back to Tier-2 ADB route")
        }

        // Direct connect and PServer failed. Check if we have credentials to perform background ADB bootstrap.
        val keyFile = File(context.noBackupFilesDir, "privd_adb_key.bin")
        val certFile = File(context.noBackupFilesDir, "privd_adb_cert.bin")
        if (keyFile.exists() && certFile.exists()) {
            AppLog.i(TAG, "Attempting background bootstrap via saved ADB credentials.")
            return PrivdBootstrapper.bootstrapAndConnect(context, "127.0.0.1")
        }

        _lastError.value = PrivdError.DAEMON_UNREACHABLE
        AppLog.w(TAG, "Privileged Mode FAILED — daemon not reachable and no bootstrap method succeeded")
        return false
    }

    /**
     * Used during the VERIFYING phase of bootstrap. Attempts a raw socket
     * connect while [_isBootstrapping] keeps [state] at [PrivdState.BOOTSTRAPPING]
     * to avoid UI flicker on each retry. On success, [PrivdClient.state] becomes
     * [PrivdConnectionState.CONNECTED] and [state] transitions to [PrivdState.RUNNING].
     */
    internal fun verifyConnect(): Boolean {
        _testStateOverride.value = null
        val ok = PrivdClient.connect()
        if (ok) {
            _isBootstrapping.value = false
            _lastError.value = null
            AppLog.i(TAG, "verifyConnect() succeeded — Privileged Mode is RUNNING")
        }
        return ok
    }

    internal fun reportBootstrapStart() {
        AppLog.i(TAG, "bootstrap started")
        _testStateOverride.value = null
        _lastError.value = null
        _isBootstrapping.value = true
    }

    internal fun reportBootstrapFailure(error: PrivdError) {
        AppLog.w(TAG, "bootstrap failed: $error")
        _testStateOverride.value = null
        _isBootstrapping.value = false
        _lastError.value = error
    }

    internal fun onClientDisconnected() {
        AppLog.w(TAG, "PrivdClient dropped unexpectedly — setting lastError to DAEMON_UNREACHABLE")
        _testStateOverride.value = null
        _isBootstrapping.value = false
        _lastError.value = PrivdError.DAEMON_UNREACHABLE
    }
}
