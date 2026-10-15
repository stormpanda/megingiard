package com.stormpanda.megingiard.privd

import android.content.Context
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Owns the lifecycle abstraction (`OFF` → `CONNECTING` → `RUNNING` / `FAILED`)
 * and exposes a single read-only [state] stream that the UI binds to.
 *
 * Per AGENTS.md §7.1: backing `MutableStateFlow`s are private; only the
 * read-only [state] / [lastError] surfaces escape this object.
 */
object PrivdManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clientObserverJob: Job? = null

    private val _state = MutableStateFlow(PrivdState.OFF)
    val state: StateFlow<PrivdState> = _state.asStateFlow()

    internal fun setStateForTesting(state: PrivdState) {
        _state.value = state
    }

    private val _lastError = MutableStateFlow<PrivdError?>(null)
    val lastError: StateFlow<PrivdError?> = _lastError.asStateFlow()

    val isRootBridgeAvailable: Boolean get() = PServiceBridge.isAvailable()

    fun connect(context: Context): Boolean {
        AppLog.i(TAG, "connect() called (current state=${_state.value})")
        _state.value = PrivdState.CONNECTING
        _lastError.value = null
        val ok = PrivdClient.connect()
        if (ok) {
            _state.value = PrivdState.RUNNING
            AppLog.i(TAG, "Privileged Mode is RUNNING")
            startClientObserver()
            return true
        }

        // Direct connect failed. Attempt Tier-1 fast-track via PServer root bridge
        if (PServiceBridge.isAvailable()) {
            AppLog.i(TAG, "Direct connect failed. PServer root bridge is available — attempting fast-track bootstrap.")
            _state.value = PrivdState.BOOTSTRAPPING
            val pOk = PServerBootstrapper.bootstrap(context)
            if (pOk) {
                _state.value = PrivdState.RUNNING
                AppLog.i(TAG, "PServer fast-track bootstrap succeeded — Privileged Mode is RUNNING")
                startClientObserver()
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

        _state.value = PrivdState.FAILED
        _lastError.value = PrivdError.DAEMON_UNREACHABLE
        AppLog.w(TAG, "Privileged Mode FAILED — daemon not reachable and no bootstrap method succeeded")
        return false
    }

    /**
     * Used during the VERIFYING phase of bootstrap. Attempts a raw socket
     * connect without publishing CONNECTING/FAILED state transitions, to avoid
     * UI flicker on each retry. On success, transitions state to RUNNING and
     * starts the client observer. The caller is responsible for calling
     * [reportBootstrapFailure] if all retries are exhausted.
     *
     * @return `true` if the daemon socket was reached and `PrivdClient` is now
     * connected.
     */
    internal fun verifyConnect(): Boolean {
        val ok = PrivdClient.connect()
        if (ok) {
            _state.value = PrivdState.RUNNING
            AppLog.i(TAG, "verifyConnect() succeeded — Privileged Mode is RUNNING")
            startClientObserver()
        }
        return ok
    }

    internal fun reportBootstrapStart() {
        AppLog.i(TAG, "bootstrap started")
        _state.value = PrivdState.BOOTSTRAPPING
        _lastError.value = null
    }

    internal fun reportBootstrapFailure(error: PrivdError) {
        AppLog.w(TAG, "bootstrap failed: $error")
        _state.value = PrivdState.FAILED
        _lastError.value = error
    }

    /**
     * Observes [PrivdClient.state] while the connection is RUNNING. If the
     * transport drops unexpectedly (I/O error, daemon killed) this drives
     * [PrivdManager.state] back to FAILED so the UI reflects the real state.
     */
    private fun startClientObserver() {
        clientObserverJob?.cancel()
        clientObserverJob =
            scope.launch {
                PrivdClient.state.collect { clientState ->
                    if (clientState == PrivdConnectionState.DISCONNECTED &&
                        _state.value == PrivdState.RUNNING
                    ) {
                        AppLog.w(TAG, "PrivdClient dropped unexpectedly — setting state to FAILED")
                        _state.value = PrivdState.FAILED
                        _lastError.value = PrivdError.DAEMON_UNREACHABLE
                    }
                }
            }
    }
}
