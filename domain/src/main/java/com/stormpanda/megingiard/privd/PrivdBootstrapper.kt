package com.stormpanda.megingiard.privd

import android.content.Context
import com.stormpanda.megingiard.AppLog
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

private const val TAG = "PrivdBootstrapper"
private const val DAEMON_ASSET_NAME = "megingiard_privd_arm64"
private const val DAEMON_REMOTE_PATH = "/data/local/tmp/megingiard_privd"
private const val SPAWN_OK_MARKER = "MGRD_SPAWN_OK"
private const val SHELL_READ_TIMEOUT_MS = 8_000L
private const val VERIFY_INITIAL_DELAY_MS = 500L
private const val VERIFY_RETRY_COUNT = 20
private const val VERIFY_RETRY_DELAY_MS = 300L
private const val SYNC_SERVICE = "sync:"
private const val SYNC_SEND = "SEND"
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
     * not enabled) or cannot be parsed.
     */
    private fun readAdbTlsConnectPort(): Int {
        return try {
            val proc = ProcessBuilder("getprop", "service.adb.tls.port")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readLine()?.trim() ?: ""
            proc.waitFor()
            output.toIntOrNull() ?: 0
        } catch (e: Exception) {
            AppLog.w(TAG, "readAdbTlsConnectPort() threw: $e")
            0
        }
    }

    private fun pushDaemon(context: Context, mgr: PrivdAdbConnectionManager): Boolean {
        val binaryBytes = context.assets.open(DAEMON_ASSET_NAME).use { it.readBytes() }
        AppLog.d(TAG, "push: ${binaryBytes.size} bytes -> $DAEMON_REMOTE_PATH")
        val stream = mgr.openStream(SYNC_SERVICE) ?: return false
        return stream.use { s ->
            syncSendFile(s, binaryBytes, DAEMON_REMOTE_PATH, REMOTE_FILE_MODE) &&
                verifyRemoteSize(s, DAEMON_REMOTE_PATH, binaryBytes.size)
        }
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
