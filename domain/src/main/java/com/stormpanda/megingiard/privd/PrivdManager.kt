package com.stormpanda.megingiard.privd

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Features that become available when Privileged Mode is connected.
 * Each entry has its own user-controllable enable flag (see
 * [com.stormpanda.megingiard.settings.MacroPadSettings]).
 *
 * To add a new feature:
 *   1. Add an enum value here.
 *   2. Add a per-feature DataStore key in `SettingsKeys.kt`.
 *   3. Expose a `StateFlow<Boolean>` + setter on [com.stormpanda.megingiard.settings.MacroPadSettings].
 *   4. Wire the consumer to check both [PrivdManager.state] == RUNNING **and** the per-feature flag.
 */
enum class PrivdFeature {
    /**
     * MacroPad gamepad events are merged into the physical controller's evdev
     * node (single-controller emulation). When disabled, the app falls back
     * to the legacy virtual uinput gamepad.
     */
    GAMEPAD_MERGE,
}

/**
 * Lifecycle state of the privileged-mode subsystem.
 *
 * **Meilenstein A note** — Bootstrap of the daemon currently happens
 * out-of-band: the user runs `adb shell /data/local/tmp/megingiard_privd &`
 * once, then taps "Connect" in settings. Meilenstein B will replace this
 * with on-device libadb-android pairing + auto-bootstrap, but the state
 * machine and per-feature flags are designed to outlive that change.
 */
enum class PrivdState {
    /** Privileged Mode is off (no connection, no in-flight bootstrap). */
    OFF,

    /** Pairing / pushing the binary / spawning the daemon over ADB Wireless Debugging. */
    BOOTSTRAPPING,

    /** Connection attempt in progress. */
    CONNECTING,

    /** Daemon is reachable; per-feature flags decide who actually uses it. */
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
    /** The abstract socket is not reachable — daemon is probably not running. */
    DAEMON_UNREACHABLE,

    /** ADB Wireless-Debugging pairing rejected the entered code or timed out. */
    PAIRING_FAILED,

    /** Could not auto-discover an ADB Wireless-Debugging endpoint via mDNS. */
    ADB_DISCOVERY_FAILED,

    /** ADB connect after pairing failed (peer offline, key mismatch, …). */
    ADB_CONNECT_FAILED,

    /** Pushing the daemon binary into /data/local/tmp failed. */
    BOOTSTRAP_PUSH_FAILED,

    /** Spawning the daemon process via `adb shell` failed. */
    BOOTSTRAP_SPAWN_FAILED,
}

/**
 * Top-level controller for Privileged Mode.
 *
 * Owns the lifecycle abstraction (`OFF` → `CONNECTING` → `RUNNING` / `FAILED`)
 * and exposes a single read-only [state] stream that the UI binds to.
 * Bootstrapping the actual daemon process is delegated:
 *   - **Meilenstein A**: manual — user starts the daemon via `adb shell` once;
 *     [connect] just attempts a [PrivdClient.connect] and reports the result.
 *   - **Meilenstein B**: automatic — a `PrivdBootstrapper` (TBD) will use
 *     libadb-android to push the binary and spawn it via Wireless Debugging,
 *     then call into [connect] the same way.
 *
 * Per AGENTS.md §4: backing `MutableStateFlow`s are private; only the
 * read-only [state] / [lastError] surfaces escape this object.
 */
object PrivdManager {

    private val _state = MutableStateFlow(PrivdState.OFF)
    val state: StateFlow<PrivdState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<PrivdError?>(null)
    val lastError: StateFlow<PrivdError?> = _lastError.asStateFlow()

    /**
     * Attempts to connect to the running daemon.
     * Returns `true` on success.
     */
    fun connect(): Boolean {
        AppLog.i(TAG, "connect() called (current state=${_state.value})")
        _state.value = PrivdState.CONNECTING
        _lastError.value = null
        val ok = PrivdClient.connect()
        if (ok) {
            _state.value = PrivdState.RUNNING
            AppLog.i(TAG, "Privileged Mode is RUNNING")
        } else {
            _state.value = PrivdState.FAILED
            _lastError.value = PrivdError.DAEMON_UNREACHABLE
            AppLog.w(TAG, "Privileged Mode FAILED — daemon not reachable")
        }
        return ok
    }

    /**
     * Disconnects from the daemon. The daemon itself stays alive.
     */
    fun disconnect() {
        AppLog.i(TAG, "disconnect()")
        PrivdClient.disconnect()
        _state.value = PrivdState.OFF
        _lastError.value = null
    }

    /**
     * Convenience: are we ready to dispatch events for [feature]?
     * Caller is responsible for combining this with the per-feature flag
     * stored in user settings.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isAvailable(feature: PrivdFeature): Boolean =
        _state.value == PrivdState.RUNNING && PrivdClient.isConnected

    /**
     * Internal hooks used by [PrivdBootstrapper] to drive the BOOTSTRAPPING /
     * FAILED state transitions while the wizard pushes the binary and spawns
     * the daemon over ADB Wireless Debugging.
     */
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
}
