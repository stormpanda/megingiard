package com.stormpanda.megingiard.privd

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.security.BinaryIntegrity
import com.stormpanda.megingiard.security.HmacUtil
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private const val TAG = "PrivdBootstrapper"
private const val DAEMON_ASSET_NAME = "megingiard_privd_arm64"
private const val DAEMON_REMOTE_PATH = "/data/local/tmp/megingiard_privd"
// Process name as seen by pidof/kill — equals the basename of DAEMON_REMOTE_PATH.
private const val DAEMON_PROCESS_NAME = "megingiard_privd"
private const val MIRROR_DEX_ASSET_NAME = "megingiard_mirror.dex"
private const val MIRROR_DEX_REMOTE_PATH = "/data/local/tmp/megingiard_mirror.dex"
private const val MIRROR_DEX_REMOTE_MODE_RAW = 33188 // 0100644 = regular + rw-r--r--
private const val SPAWN_OK_MARKER = "MGRD_SPAWN_OK"
// Marker echoed by the daemon's --provision mode on success (exits 0, then shell echoes this).
private const val PROVISION_OK_MARKER = "MGRD_PROVISION_OK"
private const val SHELL_READ_TIMEOUT_MS = 8_000L
private const val VERIFY_INITIAL_DELAY_MS = 500L
private const val VERIFY_RETRY_COUNT = 20
private const val VERIFY_RETRY_DELAY_MS = 300L
private const val SYNC_SERVICE = "sync:"
private const val SYNC_SEND = "SEND"
private const val GETPROP_TIMEOUT_MS = 2_000L
private const val SYNC_DATA = "DATA"
private const val SYNC_DONE = "DONE"
private const val SYNC_OKAY = "OKAY"
private const val SYNC_FAIL = "FAIL"
private const val SYNC_STAT = "STAT"
private const val SYNC_ID_SIZE = 4
private const val SYNC_HEADER_SIZE = 8
private const val SYNC_VALUE_OFFSET = 4
private const val SYNC_STAT_RESPONSE_SIZE = 16
private const val SYNC_DATA_MAX = 64 * 1024
private const val UNIX_FILE_TYPE_REGULAR = 32768
private const val UNIX_MODE_755 = 493
private const val REMOTE_FILE_MODE = UNIX_FILE_TYPE_REGULAR + UNIX_MODE_755
private const val MS_PER_SECOND = 1_000L
private const val UINT_MASK = 0xffffffffL

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
 * 2. **Connect** directly to the host + connect port shown by Wireless Debugging.
 * 3. **Push** the bundled `megingiard_privd_arm64` asset to `/data/local/tmp/`
 *    via the ADB `sync:` protocol, then verify the remote byte size.
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
     * The ADB Wireless-Debugging connect port is read automatically from the
     * `service.adb.tls.port` system property — no user input required.
     *
     * @param host The device's IP address (always `127.0.0.1` for on-device use).
     *
     * Blocking — call on `Dispatchers.IO`. Returns `true` on success.
     * Failure modes are reported through [PrivdManager.reportBootstrapFailure].
     */
    fun bootstrapAndConnect(context: Context, host: String): Boolean {
        val connectPort = readAdbTlsConnectPort()
        if (connectPort <= 0) {
            AppLog.w(TAG, "bootstrapAndConnect: wireless debugging port not available (port=$connectPort) — is Wireless Debugging enabled?")
            PrivdManager.reportBootstrapFailure(PrivdError.ADB_CONNECT_FAILED)
            return false
        }
        AppLog.i(TAG, "bootstrapAndConnect(host=$host connectPort=$connectPort)")
        PrivdManager.reportBootstrapStart()
        val mgr = PrivdAdbConnectionManager.getInstance(context)
        // Connect directly to adbd — mDNS self-discovery is unreliable on-device.
        _stage.value = BootstrapStage.CONNECTING_ADB
        val connected = try {
            mgr.connect(host, connectPort)
        } catch (e: Exception) {
            AppLog.w(TAG, "connect($host:$connectPort) threw: $e")
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
        // Provision: generate a fresh per-install key, push it to the daemon via ADB shell,
        // and update PrivdClient's in-memory copy so the subsequent verifyConnect() handshake
        // uses the new key without an extra Keystore decrypt roundtrip.
        val keyBytes = runCatching { PrivdPairKey.generateAndStore(context) }.getOrElse { e ->
            AppLog.e(TAG, "generateAndStore() threw: $e")
            null
        }
        if (keyBytes == null) {
            PrivdManager.reportBootstrapFailure(PrivdError.BOOTSTRAP_PROVISION_FAILED)
            disconnectQuietly(mgr)
            _stage.value = BootstrapStage.IDLE
            return false
        }
        val provisionOk = runCatching {
            provisionDaemon(mgr, HmacUtil.bytesToHex(keyBytes), android.os.Process.myUid())
        }.getOrElse { e ->
            AppLog.w(TAG, "provisionDaemon() threw: $e")
            false
        }
        if (!provisionOk) {
            PrivdManager.reportBootstrapFailure(PrivdError.BOOTSTRAP_PROVISION_FAILED)
            disconnectQuietly(mgr)
            _stage.value = BootstrapStage.IDLE
            return false
        }
        // Key is now stored on both sides. Update in-memory copy before verifyConnect().
        PrivdClient.setKey(keyBytes)
        AppLog.i(TAG, "bootstrapAndConnect: per-install key provisioned (app UID=${android.os.Process.myUid()})")

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
        // Verify via abstract-socket round-trip.
        // Give the daemon time to setsid() and bind the socket before the first attempt.
        // Use verifyConnect() instead of connect() to avoid CONNECTING→FAILED UI flicker
        // on each retry while PrivdManager.state is still BOOTSTRAPPING.
        _stage.value = BootstrapStage.VERIFYING
        Thread.sleep(VERIFY_INITIAL_DELAY_MS)
        var ok = false
        for (i in 0 until VERIFY_RETRY_COUNT) {
            if (PrivdManager.verifyConnect()) { ok = true; break }
            Thread.sleep(VERIFY_RETRY_DELAY_MS)
        }
        disconnectQuietly(mgr)
        return if (ok) {
            _stage.value = BootstrapStage.DONE
            true
        } else {
            PrivdManager.reportBootstrapFailure(PrivdError.DAEMON_UNREACHABLE)
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

    /**
     * Reads the ADB Wireless-Debugging TLS connect port from the system property
     * `service.adb.tls.port`. Returns 0 if the property is absent (Wireless Debugging
     * not enabled), if the `getprop` invocation hangs beyond [GETPROP_TIMEOUT_MS],
     * or if the output cannot be parsed.
     */
    private fun readAdbTlsConnectPort(): Int {
        var proc: Process? = null
        return try {
            proc = ProcessBuilder("getprop", "service.adb.tls.port")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().use { reader ->
                reader.readLine()?.trim().orEmpty()
            }
            val exited = proc.waitFor(GETPROP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!exited) {
                AppLog.w(TAG, "readAdbTlsConnectPort() getprop timed out after $GETPROP_TIMEOUT_MS ms")
                proc.destroyForcibly()
                return 0
            }
            output.toIntOrNull() ?: 0
        } catch (e: Exception) {
            AppLog.w(TAG, "readAdbTlsConnectPort() threw: $e")
            proc?.destroyForcibly()
            0
        }
    }

    private fun pushDaemon(context: Context, mgr: PrivdAdbConnectionManager): Boolean {
        val binaryBytes = context.assets.open(DAEMON_ASSET_NAME).use { it.readBytes() }
        if (!BinaryIntegrity.verify(DAEMON_ASSET_NAME, binaryBytes)) {
            AppLog.e(TAG, "Refusing to push $DAEMON_ASSET_NAME — integrity check failed")
            return false
        }
        AppLog.d(TAG, "push: ${binaryBytes.size} bytes -> $DAEMON_REMOTE_PATH")
        val daemonOk = mgr.openStream(SYNC_SERVICE)?.use { s ->
            syncSendFile(s, binaryBytes, DAEMON_REMOTE_PATH, REMOTE_FILE_MODE) &&
                verifyRemoteSize(s, DAEMON_REMOTE_PATH, binaryBytes.size)
        } ?: false
        if (!daemonOk) return false

        // Also push the privileged-mirror DEX (used by Privileged Mode mirror).
        // The DEX is harmless if Privileged Mirror is never enabled, so we always
        // push it as part of the bootstrap to avoid a separate setup flow.
        val dexBytes = context.assets.open(MIRROR_DEX_ASSET_NAME).use { it.readBytes() }
        if (!BinaryIntegrity.verify(MIRROR_DEX_ASSET_NAME, dexBytes)) {
            AppLog.e(TAG, "Refusing to push $MIRROR_DEX_ASSET_NAME — integrity check failed")
            // Daemon push already succeeded; treat as DEX failure (non-fatal below).
            return true
        }
        AppLog.d(TAG, "push: ${dexBytes.size} bytes -> $MIRROR_DEX_REMOTE_PATH")
        val dexOk = mgr.openStream(SYNC_SERVICE)?.use { s ->
            syncSendFile(s, dexBytes, MIRROR_DEX_REMOTE_PATH, MIRROR_DEX_REMOTE_MODE_RAW) &&
                verifyRemoteSize(s, MIRROR_DEX_REMOTE_PATH, dexBytes.size)
        } ?: false
        if (!dexOk) {
            AppLog.w(TAG, "mirror DEX push failed — privileged mirror will be unavailable until next bootstrap")
        }
        // Daemon push is the gating step; DEX failure is non-fatal.
        return true
    }

    private fun syncSendFile(
        stream: AdbStream,
        bytes: ByteArray,
        remotePath: String,
        mode: Int,
    ): Boolean {
        val out = stream.openOutputStream()
        val input = stream.openInputStream()
        val sendTarget = "$remotePath,$mode".toByteArray(StandardCharsets.UTF_8)
        out.write(syncHeader(SYNC_SEND, sendTarget.size))
        out.write(sendTarget)
        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = minOf(SYNC_DATA_MAX, bytes.size - offset)
            out.write(syncHeader(SYNC_DATA, chunkSize))
            out.write(bytes, offset, chunkSize)
            offset += chunkSize
        }
        out.write(syncHeader(SYNC_DONE, currentEpochSeconds()))
        out.flush()
        return readSyncStatus(input)
    }

    private fun verifyRemoteSize(stream: AdbStream, remotePath: String, expectedSize: Int): Boolean {
        val out = stream.openOutputStream()
        val input = stream.openInputStream()
        val pathBytes = remotePath.toByteArray(StandardCharsets.UTF_8)
        out.write(syncHeader(SYNC_STAT, pathBytes.size))
        out.write(pathBytes)
        out.flush()
        val response = readFully(input, SYNC_STAT_RESPONSE_SIZE)
        val id = syncId(response, 0)
        if (id != SYNC_STAT) {
            AppLog.w(TAG, "sync STAT returned unexpected id=$id")
            return false
        }
        // STAT v1 response: id(4) + mode(4) + size(4) + mtime(4)
        // position(SYNC_ID_SIZE) skips the 4-byte id field only, leaving mode/size/mtime.
        val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(SYNC_ID_SIZE)
        val statMode = buffer.int   // bytes 4-7: file permissions
        val statSize = buffer.int   // bytes 8-11: file size in bytes
        AppLog.d(TAG, "sync STAT $remotePath mode=$statMode size=$statSize expected=$expectedSize")
        return statSize == expectedSize
    }

    private fun readSyncStatus(input: InputStream): Boolean {
        val header = readFully(input, SYNC_HEADER_SIZE)
        val id = syncId(header, 0)
        val value = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(SYNC_VALUE_OFFSET)
        return when (id) {
            SYNC_OKAY -> true
            SYNC_FAIL -> {
                val message = readFully(input, value).toString(StandardCharsets.UTF_8)
                AppLog.w(TAG, "sync SEND failed: $message")
                false
            }
            else -> {
                AppLog.w(TAG, "sync SEND returned unexpected id=$id")
                false
            }
        }
    }

    private fun syncHeader(id: String, value: Int): ByteArray = ByteBuffer
        .allocate(SYNC_HEADER_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put(id.toByteArray(StandardCharsets.US_ASCII))
        .putInt(value)
        .array()

    private fun syncId(bytes: ByteArray, offset: Int): String = bytes
        .copyOfRange(offset, offset + SYNC_ID_SIZE)
        .toString(StandardCharsets.US_ASCII)

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(bytes, offset, size - offset)
            if (read < 0) error("ADB sync stream closed while reading $size bytes")
            offset += read
        }
        return bytes
    }

    private fun currentEpochSeconds(): Int = ((System.currentTimeMillis() / MS_PER_SECOND) and UINT_MASK).toInt()

    /**
     * Runs the daemon in provisioning mode: `megingiard_privd --provision <keyHex> <appUid>`.
     *
     * The daemon writes the per-install HMAC key and the expected app UID to a shell-owned
     * 0600 state file (`/data/local/tmp/megingiard_privd.key`), then exits with 0.
     * The shell then echoes [PROVISION_OK_MARKER] confirming success.
     *
     * This step runs between [pushDaemon] and [spawnDaemon] so the daemon has a valid key
     * when it starts. The key is transmitted over the already-verified ADB TLS channel.
     *
     * @param mgr   The open ADB connection to use.
     * @param keyHex 64 uppercase hex characters (32 bytes) — the per-install HMAC key.
     * @param appUid The Android UID of the Megingiard app process.
     * @return `true` if the daemon wrote the state file successfully.
     */
    private fun provisionDaemon(mgr: PrivdAdbConnectionManager, keyHex: String, appUid: Int): Boolean {
        val cmd = "shell:$DAEMON_REMOTE_PATH --provision $keyHex $appUid && echo $PROVISION_OK_MARKER"
        AppLog.d(TAG, "provision cmd issued (keyHex redacted, appUid=$appUid)")
        val stream = mgr.openStream(cmd) ?: return false
        return stream.use { s -> readUntilMarker(s, PROVISION_OK_MARKER) }
    }

    private fun spawnDaemon(mgr: PrivdAdbConnectionManager): Boolean {
        // Kill any previous daemon instance before spawning a fresh one.
        // An old daemon (e.g. built without HMAC support) holds the abstract Unix
        // socket and prevents the new binary from binding it.  Without this kill step
        // the new binary exits immediately with EADDRINUSE and the old one continues
        // to serve connections without the expected HMAC challenge — which is exactly
        // the failure mode we saw after the HMAC feature was added in commit 6de02b3.
        //
        // SIGTERM (default kill) is insufficient: the daemon calls signal() which sets
        // SA_RESTART on Android/Linux.  While the daemon is blocked in accept() waiting
        // for a client, SIGTERM fires the handler (sets g_should_exit=1) but accept()
        // is automatically restarted — the while(!g_should_exit) guard is never
        // re-evaluated, so the process stays alive indefinitely.
        // SIGKILL cannot be caught, blocked, or ignored — it unconditionally removes
        // the process and releases the abstract socket immediately.
        // The `sleep 1` after kill gives the kernel time to fully release the abstract
        // socket before the new binary tries to bind it.  Without this delay there is
        // a race: kill(2) returns as soon as the signal is queued, but the process may
        // not have been fully reaped yet when the shell continues to the next command,
        // causing the new binary to fail with EADDRINUSE and exit immediately.
        val killCmd = "kill -9 \$(pidof $DAEMON_PROCESS_NAME 2>/dev/null) 2>/dev/null; sleep 1"
        val cmd = "shell:$killCmd; $DAEMON_REMOTE_PATH </dev/null >/dev/null 2>&1 &\necho $SPAWN_OK_MARKER"
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
