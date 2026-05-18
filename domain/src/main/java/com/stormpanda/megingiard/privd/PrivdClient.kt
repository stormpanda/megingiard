package com.stormpanda.megingiard.privd

import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import com.stormpanda.megingiard.security.HmacUtil

private const val TAG = "PrivdClient"
private const val ABSTRACT_NAME = "megingiard.privd"
private const val PING_TIMEOUT_MS = 1_500L
private const val MIRROR_DIRECT_START_TIMEOUT_MS = 4_000L
private const val MIRROR_STOP_TIMEOUT_MS = 3_000L
private const val WRITER_THREAD_NAME = "PrivdClientWriter"
private const val READER_THREAD_NAME = "PrivdClientReader"
private const val HANDSHAKE_TIMEOUT_MS = 5_000
private const val NONCE_HEX_LEN = 32   // 16 nonce bytes → 32 hex chars
private const val HMAC_HEX_LEN = 64    // SHA-256 digest → 64 hex chars
// Default key matching the C daemon default in megingiard_privd.c.
// Users who want genuine secrecy must set megingiard.privd.hmac.key in
// local.properties and rebuild the daemon via build_megingiard_privd.sh.
private const val DEFAULT_PRIVD_HMAC_KEY =
    "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6C7D8E9F0A1B2"

/**
 * Async LocalSocket transport to the `megingiard_privd` daemon.
 *
 * The daemon is bootstrapped via the privileged ADB shell channel (see
 * `PrivdManager`) and binds the abstract Unix socket `@megingiard.privd`.
 * After bootstrap, the app process — running in the unprivileged
 * `untrusted_app` SELinux domain — connects to that socket and pipes
 * feature-prefixed ASCII commands.
 *
 * ### Threading model
 * - One **writer thread** drains a [LinkedBlockingQueue] of pending lines.
 * - One **reader thread** continuously reads `\n`-terminated responses to
 *   support [ping] without racing the writer.
 *
 * Both threads exit on socket failure and the client transitions to
 * [PrivdConnectionState.DISCONNECTED].
 *
 * Per AGENTS.md §4: the backing [MutableStateFlow] is `private`; the public
 * surface only exposes the read-only [state].
 */
object PrivdClient {

    private val _state = MutableStateFlow(PrivdConnectionState.DISCONNECTED)
    val state: StateFlow<PrivdConnectionState> = _state.asStateFlow()

    /**
     * Raw evdev events streamed from the daemon while a `SUB GAMEPAD` subscription is active.
     * Consumed by [com.stormpanda.megingiard.macropad.PhysicalGamepadRecordingManager].
     *
     * Buffer: 64 events (DROP_OLDEST on overflow — the recording manager processes events
     * fast enough that this should never be reached under normal conditions).
     */
    private val _evdevEvents = MutableSharedFlow<EvdevEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val evdevEvents: SharedFlow<EvdevEvent> = _evdevEvents.asSharedFlow()

    @Volatile private var hmacKeyBytes: ByteArray = HmacUtil.hexToBytes(DEFAULT_PRIVD_HMAC_KEY)

    /**
     * Sets the pre-shared HMAC key used in the daemon challenge-response handshake.
     * Must be called before the first [connect] — typically in MainActivity.onCreate
     * via `PrivdClient.setHmacKey(BuildConfig.PRIVD_HMAC_KEY)`.  The key must be
     * a 64-character uppercase hex string (32 bytes).  If the string is invalid the
     * default key is retained.
     */
    fun setHmacKey(hexKey: String) {
        val clean = hexKey.uppercase().replace(":", "").replace(" ", "")
        if (clean.length != 64) {
            AppLog.w(TAG, "setHmacKey: invalid key length ${clean.length} — retaining default")
            return
        }
        hmacKeyBytes = HmacUtil.hexToBytes(clean)
        AppLog.d(TAG, "setHmacKey: key updated")
    }

    @Volatile private var socket: LocalSocket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var reader: BufferedReader? = null
    @Volatile private var writerThread: Thread? = null
    @Volatile private var readerThread: Thread? = null
    @Volatile private var running = false

    private val queue = LinkedBlockingQueue<String>()
    @Volatile private var pingDeferred: CompletableDeferred<Boolean>? = null
    @Volatile private var mirrorDirectStartDeferred: CompletableDeferred<Boolean>? = null
    @Volatile private var mirrorStopDeferred: CompletableDeferred<Boolean>? = null

    val isConnected: Boolean
        get() = running && (socket?.isConnected == true)

    /**
     * Attempts to connect to the abstract socket `@megingiard.privd`.
     * Returns `true` on success, `false` if the daemon is not listening.
     */
    @Synchronized
    fun connect(): Boolean {
        if (isConnected) return true
        _state.value = PrivdConnectionState.CONNECTING
        return try {
            val s = LocalSocket()
            s.connect(LocalSocketAddress(ABSTRACT_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            val w = BufferedWriter(OutputStreamWriter(s.outputStream))
            val r = BufferedReader(InputStreamReader(s.inputStream))
            // HMAC challenge-response: prove to the app that the daemon is the
            // legitimate megingiard_privd binary (not a rogue process claiming
            // the abstract socket). The daemon sends a random nonce, the app
            // responds with HMAC-SHA256(key, nonce), and the daemon replies OK.
            if (!performHmacHandshake(s, r, w)) {
                AppLog.w(TAG, "connect() rejected: HMAC handshake failed")
                try { s.close() } catch (_: Exception) {}
                _state.value = PrivdConnectionState.DISCONNECTED
                return false
            }
            socket = s
            writer = w
            reader = r
            queue.clear()
            running = true
            writerThread = Thread(::writerLoop, WRITER_THREAD_NAME).apply {
                isDaemon = true
                start()
            }
            readerThread = Thread(::readerLoop, READER_THREAD_NAME).apply {
                isDaemon = true
                start()
            }
            _state.value = PrivdConnectionState.CONNECTED
            AppLog.i(TAG, "connect() succeeded")
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "connect() failed: $e")
            cleanupLocked()
            _state.value = PrivdConnectionState.DISCONNECTED
            false
        }
    }

    /**
     * Closes the socket. Daemon stays alive (continues to listen for the
     * next client).
     */
    @Synchronized
    fun disconnect() {
        if (!running && socket == null) return
        AppLog.i(TAG, "disconnect()")
        cleanupLocked()
        _state.value = PrivdConnectionState.DISCONNECTED
    }

    /**
     * Enqueues a `\n`-terminated command line. No-op when not connected.
     * Intended for high-frequency feature traffic (e.g. gamepad events).
     */
    fun send(line: String) {
        if (!running) return
        queue.offer(line)
    }

    /**
     * Round-trips a `PING` and waits for `PONG`. Returns `true` on success,
     * `false` on timeout or transport error. Useful as a health-check from
     * the Privileged Mode settings card.
     */
    suspend fun ping(): Boolean {
        if (!isConnected) return false
        val deferred = CompletableDeferred<Boolean>()
        pingDeferred = deferred
        send("PING\n")
        val ok = withTimeoutOrNull(PING_TIMEOUT_MS) { deferred.await() } ?: false
        pingDeferred = null
        AppLog.d(TAG, "ping() → $ok")
        return ok
    }

    /**
     * Requests the daemon to start the direct-Surface privileged mirror path.
     * Returns `false` when the daemon build does not support the direct handoff yet.
     */
    suspend fun startDirectMirror(
        width: Int,
        height: Int,
    ): Boolean {
        if (!isConnected) return false
        val deferred = CompletableDeferred<Boolean>()
        mirrorDirectStartDeferred = deferred
        send("MIRROR START_DIRECT $width $height\n")
        val ok = withTimeoutOrNull(MIRROR_DIRECT_START_TIMEOUT_MS) { deferred.await() } ?: false
        mirrorDirectStartDeferred = null
        AppLog.i(TAG, "startDirectMirror($width x $height -> app surface) -> $ok")
        return ok
    }

    /**
     * Stops the privileged-mirror server child. Returns `true` if the daemon
     * acknowledged with `MIRROR_STOPPED`, `false` on timeout / disconnect.
     */
    suspend fun stopMirror(): Boolean {
        if (!isConnected) return false
        val deferred = CompletableDeferred<Boolean>()
        mirrorStopDeferred = deferred
        send("MIRROR STOP\n")
        val ok = withTimeoutOrNull(MIRROR_STOP_TIMEOUT_MS) { deferred.await() } ?: false
        mirrorStopDeferred = null
        AppLog.i(TAG, "stopMirror() → $ok")
        return ok
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun writerLoop() {
        while (running) {
            val line = try { queue.take() } catch (_: InterruptedException) { break }
            val w = writer ?: break
            try {
                w.write(line)
                w.flush()
            } catch (e: Exception) {
                AppLog.w(TAG, "writerLoop failed: $e")
                markBroken()
                break
            }
        }
    }

    private fun readerLoop() {
        val r = reader ?: return
        while (running) {
            val line = try {
                r.readLine() ?: break
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                AppLog.w(TAG, "readerLoop failed: $e")
                break
            }
            if (line == "PONG") {
                pingDeferred?.complete(true)
                continue
            }
            if (line.startsWith("MIRROR_DIRECT_READY")) {
                mirrorDirectStartDeferred?.complete(true)
                continue
            }
            if (line.startsWith("MIRROR_DIRECT_ERR")) {
                mirrorDirectStartDeferred?.complete(false)
                continue
            }
            if (line == "MIRROR_STOPPED") {
                mirrorStopDeferred?.complete(true)
                continue
            }
            if (line.startsWith("EVT ")) {
                val parts = line.split(' ')
                if (parts.size == 4) {
                    val type = parts[1].toIntOrNull()
                    val code = parts[2].toIntOrNull()
                    val value = parts[3].toIntOrNull()
                    if (type != null && code != null && value != null) {
                        _evdevEvents.tryEmit(EvdevEvent(type, code, value))
                    }
                }
            }
        }
        markBroken()
    }

    private fun markBroken() {
        if (!running) return
        running = false
        _state.value = PrivdConnectionState.DISCONNECTED
        // Schedule full cleanup on a daemon thread so the socket fd is released
        // and the writer thread is unblocked. We can't call disconnect() directly
        // here because the caller (writer / reader thread) may be called while
        // the main thread is inside another @Synchronized function, causing a
        // deadlock. Scheduling on a new thread avoids that race.
        Thread { disconnect() }.also { it.isDaemon = true }.start()
    }

    /** Must be invoked from a synchronized block. */
    private fun cleanupLocked() {
        running = false
        queue.clear()
        pingDeferred?.complete(false)
        pingDeferred = null
        mirrorDirectStartDeferred?.complete(false)
        mirrorDirectStartDeferred = null
        mirrorStopDeferred?.complete(false)
        mirrorStopDeferred = null
        writerThread?.interrupt()
        readerThread?.interrupt()
        writerThread = null
        readerThread = null
        // shutdownInput() sends SHUT_RD to the socket fd, which immediately unblocks
        // any reader thread stuck in readLine(). close() then frees the fd.
        // We intentionally skip reader.close() and writer.close(): both acquire
        // internal BufferedReader/Writer locks that the I/O threads may hold,
        // causing the main thread to block for several seconds (ANR). Since
        // socket.close() frees the underlying fd, the stream wrappers have no
        // independent resources to clean up.
        try { socket?.shutdownInput() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        reader = null
        socket = null
    }

    // -------------------------------------------------------------------------
    // HMAC handshake helpers
    // -------------------------------------------------------------------------

    /**
     * Synchronous mutual challenge-response handshake.
     *
     * Protocol:
     *   S→C  `CHAL <32-hex-nonce>\n`    (daemon challenges app)
     *   C→S  `AUTH <64-hex-hmac>\n`     (app proves it knows the key)
     *   S→C  `OK\n`                     (daemon accepts app)
     *   C→S  `VERIFY <32-hex-nonce2>\n` (app challenges daemon back)
     *   S→C  `PROOF <64-hex-hmac>\n`    (daemon proves it knows the key)
     *
     * Both halves use HMAC-SHA256 with the same pre-shared key. Either side
     * aborting or providing a wrong MAC causes this function to return `false`,
     * triggering a reconnect back-off in [connect].
     *
     * The socket read timeout ([HANDSHAKE_TIMEOUT_MS]) is active for both the
     * CHAL and PROOF reads and reset to 0 (blocking) only after mutual success.
     */
    private fun performHmacHandshake(
        s: LocalSocket,
        reader: BufferedReader,
        writer: BufferedWriter,
    ): Boolean {
        return try {
            s.soTimeout = HANDSHAKE_TIMEOUT_MS

            // --- Daemon challenges App ---
            val chalLine = reader.readLine() ?: return false
            if (!chalLine.startsWith("CHAL ")) {
                AppLog.w(TAG, "handshake: expected CHAL, got: $chalLine")
                return false
            }
            val nonceHex = chalLine.substring(5)
            if (nonceHex.length != NONCE_HEX_LEN) {
                AppLog.w(TAG, "handshake: nonce length ${nonceHex.length} != $NONCE_HEX_LEN")
                return false
            }
            val nonceBytes = HmacUtil.hexToBytes(nonceHex)
            val hmacHex = HmacUtil.computeHmacHex(hmacKeyBytes, nonceBytes)

            writer.write("AUTH $hmacHex\n")
            writer.flush()

            val okLine = reader.readLine() ?: return false
            if (okLine != "OK") {
                AppLog.w(TAG, "handshake: expected OK, got: $okLine")
                return false
            }

            // --- App challenges Daemon (mutual authentication) ---
            val verifyNonce = ByteArray(NONCE_HEX_LEN / 2)
            SecureRandom().nextBytes(verifyNonce)
            val verifyHex = verifyNonce.joinToString("") { b -> "%02X".format(b) }

            writer.write("VERIFY $verifyHex\n")
            writer.flush()

            val proofLine = reader.readLine() ?: run {
                AppLog.w(TAG, "handshake: no PROOF received")
                return false
            }
            if (!proofLine.startsWith("PROOF ")) {
                AppLog.w(TAG, "handshake: expected PROOF, got: $proofLine")
                return false
            }
            val receivedProofHex = proofLine.substring(6)
            if (receivedProofHex.length != HMAC_HEX_LEN) {
                AppLog.w(TAG, "handshake: proof length ${receivedProofHex.length} != $HMAC_HEX_LEN")
                return false
            }
            val expectedProofHex = HmacUtil.computeHmacHex(hmacKeyBytes, verifyNonce)
            if (!HmacUtil.constantTimeEqualsHex(receivedProofHex, expectedProofHex)) {
                AppLog.w(TAG, "handshake: PROOF mismatch — daemon is not the legitimate binary")
                return false
            }

            s.soTimeout = 0 // reset to blocking — full mutual handshake complete
            AppLog.d(TAG, "handshake: mutual authentication successful")
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "handshake: exception — $e")
            false
        }
    }

}
