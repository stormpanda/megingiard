package com.stormpanda.megingiard.privd

import android.content.Context
import com.stormpanda.megingiard.AppLog
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Base64

private const val TAG = "PrivdBootstrapper"
private const val DAEMON_ASSET_NAME = "megingiard_privd_arm64"
private const val DAEMON_REMOTE_PATH = "/data/local/tmp/megingiard_privd"
private const val ADB_AUTO_CONNECT_TIMEOUT_MS = 10_000L
private const val PUSH_OK_MARKER = "MGRD_PUSH_OK"
private const val SPAWN_OK_MARKER = "MGRD_SPAWN_OK"
private const val SHELL_READ_TIMEOUT_MS = 8_000L

/**
 * Stages of the on-device ADB Wireless-Debugging bootstrap flow.
 * The UI maps each stage to a localized progress label.
 */
enum class BootstrapStage {
    IDLE,
    PAIRING,
    CONNECTING_ADB,
    PUSHING_BINARY,
    SPAWNING_DAEMON,
    VERIFYING,
    DONE,
}

/**
 * Drives the automatic Privileged-Mode bootstrap (Meilenstein B).
 *
 * Flow:
 * 1. **Pair** with the device's ADB pairing service (host / port / 6-digit code from
 *    `Settings → Developer options → Wireless debugging → Pair device with pairing code`).
 *    Only required once per fresh install or after the user revokes the pairing.
 * 2. **Auto-connect** via mDNS to the connect endpoint (`_adb-tls-connect._tcp`).
 * 3. **Push** the bundled `megingiard_privd_arm64` asset to `/data/local/tmp/` as
 *    base64-piped data, then `chmod 755`.
 * 4. **Spawn** the daemon as a detached background process; closing the shell
 *    stream is safe because the daemon `setsid()`s and ignores `SIGHUP`.
 * 5. **Verify** by calling [PrivdManager.connect] which opens the abstract socket.
 *
 * All work is synchronous; the caller must invoke from a coroutine on
 * `Dispatchers.IO`. The [stage] flow drives the wizard UI; [PrivdManager] still
 * owns the high-level OFF/BOOTSTRAPPING/RUNNING/FAILED state.
 *
 * Per AGENTS.md §4: the backing [MutableStateFlow] is `private`; only the
 * read-only [stage] surface escapes.
 */
object PrivdBootstrapper {

    private val _stage = MutableStateFlow(BootstrapStage.IDLE)
    val stage: StateFlow<BootstrapStage> = _stage.asStateFlow()

    /**
     * Pair with the device's ADB pairing service. Blocking — call on `Dispatchers.IO`.
     *
     * @return `true` on successful pairing.
     */
    fun pair(context: Context, host: String, port: Int, pairingCode: String): Boolean {
        AppLog.i(TAG, "pair(host=$host port=$port codeLen=${pairingCode.length})")
        _stage.value = BootstrapStage.PAIRING
        return try {
            val mgr = PrivdAdbConnectionManager.getInstance(context)
            val ok = mgr.pair(host, port, pairingCode)
            AppLog.i(TAG, "pair() → $ok")
            if (!ok) PrivdManager.reportBootstrapFailure(PrivdError.PAIRING_FAILED)
            ok
        } catch (e: Exception) {
            AppLog.w(TAG, "pair() threw: $e")
            PrivdManager.reportBootstrapFailure(PrivdError.PAIRING_FAILED)
            false
        }
    }

    /**
     * Push the daemon binary, spawn it, then call [PrivdManager.connect] to verify.
     * Caller must have completed [pair] (or have a previously-paired device).
     *
     * Blocking — call on `Dispatchers.IO`. Returns `true` on success.
     * Failure modes are reported through [PrivdManager.reportBootstrapFailure].
     */
    fun bootstrapAndConnect(context: Context): Boolean {
        AppLog.i(TAG, "bootstrapAndConnect()")
        PrivdManager.reportBootstrapStart()
        val mgr = PrivdAdbConnectionManager.getInstance(context)
        // Connect via mDNS auto-discovery
        _stage.value = BootstrapStage.CONNECTING_ADB
        val connected = try {
            mgr.autoConnect(context.applicationContext, ADB_AUTO_CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            AppLog.w(TAG, "autoConnect() threw: $e")
            false
        }
        if (!connected) {
            PrivdManager.reportBootstrapFailure(PrivdError.ADB_CONNECT_FAILED)
            _stage.value = BootstrapStage.IDLE
            return false
        }
        // Push binary
        _stage.value = BootstrapStage.PUSHING_BINARY
        val pushOk = runCatching { pushDaemon(context, mgr) }.getOrElse { e ->
            AppLog.w(TAG, "pushDaemon() threw: $e")
            false
        }
        if (!pushOk) {
            PrivdManager.reportBootstrapFailure(PrivdError.BOOTSTRAP_PUSH_FAILED)
            disconnectQuietly(mgr)
            _stage.value = BootstrapStage.IDLE
            return false
        }
        // Spawn
        _stage.value = BootstrapStage.SPAWNING_DAEMON
        val spawnOk = runCatching { spawnDaemon(mgr) }.getOrElse { e ->
            AppLog.w(TAG, "spawnDaemon() threw: $e")
            false
        }
        if (!spawnOk) {
            PrivdManager.reportBootstrapFailure(PrivdError.BOOTSTRAP_SPAWN_FAILED)
            disconnectQuietly(mgr)
            _stage.value = BootstrapStage.IDLE
            return false
        }
        // Verify via abstract-socket round-trip
        _stage.value = BootstrapStage.VERIFYING
        // Daemon's R\n + setsid takes a few ms; retry a couple times.
        var ok = false
        repeat(5) {
            if (PrivdManager.connect()) {
                ok = true
                return@repeat
            }
            Thread.sleep(200)
        }
        disconnectQuietly(mgr)
        return if (ok) {
            _stage.value = BootstrapStage.DONE
            true
        } else {
            // PrivdManager.connect() already set FAILED + DAEMON_UNREACHABLE.
            _stage.value = BootstrapStage.IDLE
            false
        }
    }

    /**
     * Resets the wizard stage — call from the UI when dismissing a finished /
     * failed wizard so it returns to IDLE for next time.
     */
    fun resetStage() {
        _stage.value = BootstrapStage.IDLE
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun pushDaemon(context: Context, mgr: PrivdAdbConnectionManager): Boolean {
        val binaryBytes = context.assets.open(DAEMON_ASSET_NAME).use { it.readBytes() }
        val cmd = "shell:base64 -d > $DAEMON_REMOTE_PATH && " +
                "chmod 755 $DAEMON_REMOTE_PATH && echo $PUSH_OK_MARKER"
        AppLog.d(TAG, "push: ${binaryBytes.size} bytes → $DAEMON_REMOTE_PATH")
        val stream = mgr.openStream(cmd) ?: return false
        return stream.use { s ->
            val out = s.openOutputStream()
            // Base64 with newlines per 76 chars (default) is fine for `base64 -d`.
            val encoded = Base64.getMimeEncoder().encode(binaryBytes)
            out.write(encoded)
            out.flush()
            out.close() // EOF → base64 -d completes
            readUntilMarker(s, PUSH_OK_MARKER)
        }
    }

    private fun spawnDaemon(mgr: PrivdAdbConnectionManager): Boolean {
        val cmd = "shell:$DAEMON_REMOTE_PATH </dev/null >/dev/null 2>&1 &\necho $SPAWN_OK_MARKER"
        AppLog.d(TAG, "spawn cmd: $cmd")
        val stream = mgr.openStream(cmd) ?: return false
        return stream.use { s ->
            // Don't write anything; just read the marker.
            readUntilMarker(s, SPAWN_OK_MARKER)
        }
    }

    private fun readUntilMarker(s: AdbStream, marker: String): Boolean {
        val deadline = System.currentTimeMillis() + SHELL_READ_TIMEOUT_MS
        val reader = BufferedReader(InputStreamReader(s.openInputStream()))
        while (System.currentTimeMillis() < deadline) {
            val line = try {
                reader.readLine() ?: return false
            } catch (e: Exception) {
                AppLog.w(TAG, "readUntilMarker read failed: $e")
                return false
            }
            if (line.contains(marker)) return true
        }
        AppLog.w(TAG, "readUntilMarker timeout waiting for '$marker'")
        return false
    }

    private fun disconnectQuietly(mgr: PrivdAdbConnectionManager) {
        try {
            mgr.disconnect()
        } catch (e: Exception) {
            AppLog.d(TAG, "disconnect() threw (ignored): $e")
        }
    }
}
