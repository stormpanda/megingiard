package com.stormpanda.megingiard.privd

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.security.HmacUtil
import com.stormpanda.megingiard.session.ProcessCmdlineProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue

private const val TAG = "PrivdClient"

@Volatile private var port = PrivdConstants.PORT_RELEASE_START

private const val CONNECT_TIMEOUT_MS = 500
private const val PING_TIMEOUT_MS = 1_500L

// Daemon polls ServiceManager up to 6s for Binder registration; timeout must exceed that.
private const val MIRROR_DIRECT_START_TIMEOUT_MS = 7_500L
private const val MIRROR_STOP_TIMEOUT_MS = 3_000L
private const val SCREENSHOT_TIMEOUT_MS = 4_000L
private const val READ_FILE_TIMEOUT_MS = 5_000L
private const val WRITER_THREAD_NAME = "PrivdClientWriter"
private const val READER_THREAD_NAME = "PrivdClientReader"
private const val HANDSHAKE_TIMEOUT_MS = 5_000
private const val VERSION_CHECK_TIMEOUT_MS = 1_000
private const val NONCE_HEX_LEN = 32 // 16 nonce bytes → 32 hex chars
private const val HMAC_HEX_LEN = 64 // SHA-256 digest → 64 hex chars

/**
 * Async loopback TCP transport to the `megingiard_privd` daemon.
 *
 * The daemon is bootstrapped via the privileged root bridge or ADB shell channel
 * (see `PrivdManager`) and listens on `127.0.0.1` (release port `51234`,
 * debug port `51244`). After bootstrap, the app process — running in
 * the unprivileged `untrusted_app` SELinux domain — connects over loopback TCP,
 * completes an HMAC-SHA256 challenge-response handshake, and pipes
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
    init {
        ProcessCmdlineProvider.runningProcessesProvider = ::getRunningProcesses
        ProcessCmdlineProvider.textFileReader = ::readTextFile
    }

    private val _state = MutableStateFlow(PrivdConnectionState.DISCONNECTED)
    val state: StateFlow<PrivdConnectionState> = _state.asStateFlow()

    internal fun setStateForTesting(newState: PrivdConnectionState?) {
        isConnectedForTest = if (newState == null) null else (newState == PrivdConnectionState.CONNECTED)
        _state.value = newState ?: PrivdConnectionState.DISCONNECTED
    }

    private val commandMutex = Mutex()

    /**
     * Raw evdev events streamed from the daemon while a `SUB GAMEPAD` subscription is active.
     * Consumed by [com.stormpanda.megingiard.macropad.PhysicalGamepadRecordingManager].
     *
     * Buffer: 64 events (DROP_OLDEST on overflow — the recording manager processes events
     * fast enough that this should never be reached under normal conditions).
     */
    private val _evdevEvents =
        MutableSharedFlow<EvdevEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val evdevEvents: SharedFlow<EvdevEvent> = _evdevEvents.asSharedFlow()

    /**
     * Raw touchscreen evdev events streamed from the daemon while a `SUB TOUCH` subscription is active.
     * Consumed by [com.stormpanda.megingiard.mirror.TouchScreenObserver].
     *
     * Buffer: 128 events (DROP_OLDEST on overflow).
     */
    private val _touchEvdevEvents =
        MutableSharedFlow<EvdevEvent>(
            extraBufferCapacity = 128,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val touchEvdevEvents: SharedFlow<EvdevEvent> = _touchEvdevEvents.asSharedFlow()

    // Per-install HMAC key loaded from Android Keystore-encrypted storage at startup.
    // Null means the Privileged Mode setup wizard has not been run yet (or the app was
    // reinstalled and the Keystore entry was destroyed). connect() fails gracefully while
    // null so the daemon is simply unreachable until the user completes bootstrap.
    @Volatile private var hmacKeyBytes: ByteArray? = null

    /**
     * Loads the per-install HMAC key from Android Keystore-encrypted storage and stores
     * it in memory for subsequent [connect] calls.
     *
     * Must be called before the first [connect] — typically in `MainActivity.onCreate`
     * via `PrivdClient.loadKey(applicationContext)`. If no key has been provisioned yet
     * (setup wizard not run), the key is left null and [connect] will return `false`
     * until bootstrap completes and [setKey] is called.
     *
     * The decryption involves a short (~10 ms) hardware-backed Keystore operation.
     */
    fun setPackageName(name: String) {
        val isDebug = PrivdConstants.isDebugPackageName(name)
        port = PrivdConstants.getPortStart(isDebug)
        AppLog.d(TAG, "setPackageName: $name -> port $port")
    }

    internal fun setPortForTesting(testPort: Int) {
        port = testPort
    }

    fun loadKey(context: Context) {
        setPackageName(context.packageName)
        val key = PrivdPairKey.load(context)
        if (key != null) {
            hmacKeyBytes = key
            AppLog.d(TAG, "loadKey: per-install key loaded from Keystore storage")
        } else {
            AppLog.d(TAG, "loadKey: no key provisioned — Privd will refuse to connect until bootstrap")
        }
    }

    /**
     * Updates the in-memory HMAC key bytes directly. Called by [PrivdBootstrapper] immediately
     * after provisioning the daemon so the subsequent [verifyConnect] handshake uses the
     * freshly-generated key without requiring another Keystore decrypt.
     */
    internal fun setKey(keyBytes: ByteArray) {
        hmacKeyBytes = keyBytes
        AppLog.d(TAG, "setKey: pair key updated in memory")
    }

    @Volatile private var socket: Socket? = null

    @Volatile private var writer: BufferedWriter? = null

    @Volatile private var reader: BufferedReader? = null

    @Volatile private var writerThread: Thread? = null

    @Volatile private var readerThread: Thread? = null

    @Volatile private var running = false

    private val stateLock = Any()
    private val connectLock = Any()

    private val queue = LinkedBlockingQueue<String>()

    @Volatile private var pendingRpc: PendingRpc? = null

    @Volatile internal var isConnectedForTest: Boolean? = null

    val isConnected: Boolean
        get() = isConnectedForTest ?: (running && (socket?.isConnected == true) && (socket?.isClosed == false))

    /**
     * Attempts to connect to the canonical local TCP port.
     * Returns `true` on success, `false` if the daemon is not listening.
     */
    fun connect(): Boolean =
        synchronized(connectLock) {
            synchronized(stateLock) {
                isConnectedForTest = null
                if (isConnected) return true
                cleanupLocked()
                _state.value = PrivdConnectionState.CONNECTING
            }
            val key = hmacKeyBytes
            if (key == null) {
                AppLog.w(TAG, "connect(): no per-install key provisioned — run Privileged Mode setup wizard")
                synchronized(stateLock) {
                    _state.value = PrivdConnectionState.DISCONNECTED
                }
                return false
            }
            val targetPort = port
            try {
                AppLog.d(TAG, "connect(): trying 127.0.0.1:$targetPort")
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", targetPort), CONNECT_TIMEOUT_MS)

                val w = BufferedWriter(OutputStreamWriter(s.outputStream))
                val r = BufferedReader(InputStreamReader(s.inputStream))

                if (performHmacHandshake(s, r, w, key)) {
                    synchronized(stateLock) {
                        socket = s
                        writer = w
                        reader = r
                        queue.clear()
                        running = true
                        writerThread =
                            Thread(::writerLoop, WRITER_THREAD_NAME).apply {
                                isDaemon = true
                                start()
                            }
                        readerThread =
                            Thread(::readerLoop, READER_THREAD_NAME).apply {
                                isDaemon = true
                                start()
                            }
                        _state.value = PrivdConnectionState.CONNECTED
                    }
                    AppLog.i(TAG, "connect() succeeded on port $targetPort")
                    return true
                } else {
                    AppLog.w(TAG, "connect(): HMAC handshake failed on port $targetPort")
                    try {
                        s.close()
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                AppLog.d(TAG, "connect(): port $targetPort not available — $e")
            }
            AppLog.w(TAG, "connect() failed: daemon not reachable on port $targetPort")
            synchronized(stateLock) {
                cleanupLocked()
                _state.value = PrivdConnectionState.DISCONNECTED
            }
            return false
        }

    /**
     * Closes the socket. Daemon stays alive (continues to listen for the
     * next client).
     */
    fun disconnect() {
        synchronized(stateLock) {
            if (!running && socket == null) return
            AppLog.i(TAG, "disconnect()")
            cleanupLocked()
            _state.value = PrivdConnectionState.DISCONNECTED
        }
    }

    /**
     * Enqueues a `\n`-terminated command line. No-op when not connected.
     * Intended for high-frequency feature traffic (e.g. gamepad events).
     */
    fun send(line: String) {
        if (!isConnected) return
        queue.offer(line)
    }

    internal fun pollCommandForTest(): String? = queue.poll()

    internal fun clearQueueForTest() = queue.clear()

    internal val queueSizeForTest: Int get() = queue.size

    private suspend fun executeBooleanRpc(
        command: String,
        timeoutMs: Long,
        rpc: PendingRpc.SingleLine,
    ): Boolean =
        commandMutex.withLock {
            if (!isConnected) return false
            pendingRpc = rpc
            try {
                send(command)
                withTimeoutOrNull(timeoutMs) { rpc.deferred.await() } ?: false
            } finally {
                pendingRpc = null
            }
        }

    private suspend fun executeMultiLineRpc(
        command: String,
        timeoutMs: Long,
        rpc: PendingRpc.MultiLine,
    ): String? =
        commandMutex.withLock {
            if (!isConnected) return null
            pendingRpc = rpc
            try {
                send(command)
                withTimeoutOrNull(timeoutMs) { rpc.deferred.await() }
            } finally {
                pendingRpc = null
            }
        }

    /**
     * Round-trips a `PING` and waits for `PONG`. Returns `true` on success,
     * `false` on timeout or transport error. Useful as a health-check from
     * the Privileged Mode settings card.
     */
    suspend fun ping(): Boolean {
        val ok =
            executeBooleanRpc(
                command = "PING\n",
                timeoutMs = PING_TIMEOUT_MS,
                rpc = PendingRpc.SingleLine(successPrefix = "PONG"),
            )
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
        val ok =
            executeBooleanRpc(
                command = "MIRROR START_DIRECT $width $height\n",
                timeoutMs = MIRROR_DIRECT_START_TIMEOUT_MS,
                rpc =
                    PendingRpc.SingleLine(
                        successPrefix = "MIRROR_DIRECT_READY",
                        errorPrefix = "MIRROR_DIRECT_ERR",
                        exactSuccessMatch = false,
                    ),
            )
        AppLog.i(TAG, "startDirectMirror($width x $height -> app surface) -> $ok")
        return ok
    }

    /**
     * Stops the privileged-mirror server child. Returns `true` if the daemon
     * acknowledged with `MIRROR_STOPPED`, `false` on timeout / disconnect.
     */
    suspend fun stopMirror(): Boolean {
        val ok =
            executeBooleanRpc(
                command = "MIRROR STOP\n",
                timeoutMs = MIRROR_STOP_TIMEOUT_MS,
                rpc = PendingRpc.SingleLine(successPrefix = "MIRROR_STOPPED"),
            )
        AppLog.i(TAG, "stopMirror() → $ok")
        return ok
    }

    suspend fun takeScreenshot(
        path: String,
        displayId: Int? = null,
    ): Boolean {
        val cmd = if (displayId != null) "SCREENSHOT $displayId $path\n" else "SCREENSHOT $path\n"
        val ok =
            executeBooleanRpc(
                command = cmd,
                timeoutMs = SCREENSHOT_TIMEOUT_MS,
                rpc =
                    PendingRpc.SingleLine(
                        successPrefix = "SCREENSHOT_OK",
                        errorPrefix = "SCREENSHOT_ERR",
                    ),
            )
        AppLog.i(TAG, "takeScreenshot($path, displayId=$displayId) -> $ok")
        return ok
    }

    suspend fun readTextFile(path: String): String? {
        val result =
            executeMultiLineRpc(
                command = "READ_FILE $path\n",
                timeoutMs = READ_FILE_TIMEOUT_MS,
                rpc =
                    PendingRpc.MultiLine(
                        beginMarker = "READ_BEGIN",
                        endMarker = "READ_END",
                        errorPrefix = "READ_ERR",
                    ),
            )
        AppLog.i(TAG, "readTextFile($path) fetched ${result?.length ?: 0} bytes")
        return result
    }

    suspend fun getRunningProcesses(): String? {
        val result =
            executeMultiLineRpc(
                command = "LIST_PROCESSES\n",
                timeoutMs = READ_FILE_TIMEOUT_MS,
                rpc =
                    PendingRpc.MultiLine(
                        beginMarker = "PROC_BEGIN",
                        endMarker = "PROC_END",
                        errorPrefix = "PROC_ERR",
                    ),
            )
        AppLog.i(TAG, "getRunningProcesses() fetched ${result?.length ?: 0} bytes")
        return result
    }

    fun subscribeTouch() {
        AppLog.d(TAG, "subscribeTouch() -> sending 'SUB TOUCH\\n'")
        send("SUB TOUCH\n")
    }

    fun unsubscribeTouch() {
        AppLog.d(TAG, "unsubscribeTouch() -> sending 'UNSUB TOUCH\\n'")
        send("UNSUB TOUCH\n")
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun writerLoop() {
        while (running) {
            val line =
                try {
                    queue.take()
                } catch (_: InterruptedException) {
                    break
                }
            val w = writer ?: break
            try {
                w.write(line)
                if (queue.isEmpty()) {
                    w.flush()
                }
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
            val line =
                try {
                    r.readLine() ?: break
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    AppLog.w(TAG, "readerLoop failed: $e")
                    break
                }
            if (pendingRpc?.onLine(line) == true) {
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
                continue
            }
            if (line.startsWith("EVT_TOUCH ")) {
                val parts = line.split(' ')
                if (parts.size == 4) {
                    val type = parts[1].toIntOrNull()
                    val code = parts[2].toIntOrNull()
                    val value = parts[3].toIntOrNull()
                    if (type != null && code != null && value != null) {
                        _touchEvdevEvents.tryEmit(EvdevEvent(type, code, value))
                    }
                }
                continue
            }
        }
        markBroken()
    }

    private fun markBroken() {
        val shouldNotify =
            synchronized(stateLock) {
                if (!running) {
                    false
                } else {
                    cleanupLocked()
                    _state.value = PrivdConnectionState.DISCONNECTED
                    true
                }
            }
        if (shouldNotify) {
            AppLog.w(TAG, "markBroken: connection lost, cleaned up socket")
            PrivdManager.onClientDisconnected()
        }
    }

    /** Must be invoked while holding [stateLock]. */
    private fun cleanupLocked() {
        isConnectedForTest = null
        running = false
        queue.clear()
        pendingRpc?.cancel()
        pendingRpc = null
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
        try {
            socket?.shutdownInput()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
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
        s: Socket,
        reader: BufferedReader,
        writer: BufferedWriter,
        key: ByteArray,
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
            val hmacHex = HmacUtil.computeHmacHex(key, nonceBytes)

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
            val verifyHex = HmacUtil.bytesToHex(verifyNonce)

            writer.write("VERIFY $verifyHex\n")
            writer.flush()

            val proofLine =
                reader.readLine() ?: run {
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
            val expectedProofHex = HmacUtil.computeHmacHex(key, verifyNonce)
            if (!HmacUtil.constantTimeEqualsHex(receivedProofHex, expectedProofHex)) {
                AppLog.w(TAG, "handshake: PROOF mismatch — daemon is not the legitimate binary")
                return false
            }

            // --- Protocol Version Verification ---
            s.soTimeout = VERSION_CHECK_TIMEOUT_MS
            writer.write("VERSION ${PrivdConstants.PRIVD_VERSION}\n")
            writer.flush()

            val versionLine =
                reader.readLine() ?: run {
                    AppLog.w(TAG, "handshake: version response missing / timed out (legacy pre-versioning daemon)")
                    return false
                }
            if (!versionLine.startsWith("VERSION_OK ")) {
                AppLog.w(TAG, "handshake: version check failed, got: $versionLine (expected VERSION_OK ${PrivdConstants.PRIVD_VERSION})")
                return false
            }
            val daemonVersion = versionLine.substring(11).toIntOrNull()
            if (daemonVersion != PrivdConstants.PRIVD_VERSION) {
                AppLog.w(TAG, "handshake: version mismatch — daemon version $daemonVersion != app version ${PrivdConstants.PRIVD_VERSION}")
                return false
            }

            s.soTimeout = 0 // reset to blocking — full mutual handshake and version check complete
            AppLog.d(TAG, "handshake: mutual authentication and version check (v${PrivdConstants.PRIVD_VERSION}) successful")
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "handshake: exception — $e")
            false
        }
    }
}

internal sealed class PendingRpc {
    abstract fun onLine(line: String): Boolean

    abstract fun cancel()

    class SingleLine(
        private val successPrefix: String,
        private val errorPrefix: String? = null,
        private val exactSuccessMatch: Boolean = true,
    ) : PendingRpc() {
        val deferred = CompletableDeferred<Boolean>()

        override fun onLine(line: String): Boolean {
            val matchedSuccess =
                if (exactSuccessMatch) line == successPrefix else line.startsWith(successPrefix)
            if (matchedSuccess) {
                deferred.complete(true)
                return true
            }
            if (errorPrefix != null && line.startsWith(errorPrefix)) {
                deferred.complete(false)
                return true
            }
            return false
        }

        override fun cancel() {
            deferred.complete(false)
        }
    }

    class MultiLine(
        private val beginMarker: String,
        private val endMarker: String,
        private val errorPrefix: String,
    ) : PendingRpc() {
        val deferred = CompletableDeferred<String?>()
        private var capturing = false
        private val buffer = StringBuilder()

        override fun onLine(line: String): Boolean {
            if (line == beginMarker) {
                capturing = true
                buffer.clear()
                return true
            }
            if (line == endMarker) {
                capturing = false
                deferred.complete(buffer.toString())
                buffer.clear()
                return true
            }
            if (line.startsWith(errorPrefix)) {
                capturing = false
                buffer.clear()
                deferred.complete(null)
                return true
            }
            if (capturing) {
                buffer.append(line).append('\n')
                return true
            }
            return false
        }

        override fun cancel() {
            capturing = false
            deferred.complete(null)
        }
    }
}
