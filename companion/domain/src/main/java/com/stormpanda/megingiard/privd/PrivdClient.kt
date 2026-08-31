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
private const val PORT_RELEASE_START = 51234
private const val PORT_DEBUG_START = 51244

@Volatile private var portStart = PORT_RELEASE_START

@Volatile private var portEnd = PORT_RELEASE_START + 4
private const val CONNECT_TIMEOUT_MS = 500
private const val PING_TIMEOUT_MS = 1_500L
private const val MIRROR_DIRECT_START_TIMEOUT_MS = 4_000L
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
    init {
        ProcessCmdlineProvider.runningProcessesProvider = ::getRunningProcesses
        ProcessCmdlineProvider.textFileReader = ::readTextFile
    }

    private val _state = MutableStateFlow(PrivdConnectionState.DISCONNECTED)
    val state: StateFlow<PrivdConnectionState> = _state.asStateFlow()

    internal fun setStateForTesting(newState: PrivdConnectionState) {
        isConnectedForTest = (newState == PrivdConnectionState.CONNECTED)
        _state.value = newState
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
        val isDebug = name.contains(".debug")
        portStart = if (isDebug) PORT_DEBUG_START else PORT_RELEASE_START
        portEnd = portStart + 4
        AppLog.d(TAG, "setPackageName: $name -> port range $portStart..$portEnd")
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

    private val queue = LinkedBlockingQueue<String>()

    @Volatile private var pingDeferred: CompletableDeferred<Boolean>? = null

    @Volatile private var mirrorDirectStartDeferred: CompletableDeferred<Boolean>? = null

    @Volatile private var mirrorStopDeferred: CompletableDeferred<Boolean>? = null

    @Volatile private var screenshotDeferred: CompletableDeferred<Boolean>? = null

    @Volatile private var readFileDeferred: CompletableDeferred<String?>? = null

    @Volatile private var isCapturingReadFile = false

    private val readFileDumpBuilder = StringBuilder()

    @Volatile private var listProcessesDeferred: CompletableDeferred<String?>? = null

    @Volatile private var isCapturingProcesses = false

    private val processesDumpBuilder = StringBuilder()

    private val dumpLock = Any()

    @Volatile internal var isConnectedForTest: Boolean? = null

    val isConnected: Boolean
        get() = isConnectedForTest ?: (running && (socket?.isConnected == true) && (socket?.isClosed == false))

    /**
     * Attempts to connect to the local TCP port range.
     * Returns `true` on success, `false` if the daemon is not listening.
     */
    @Synchronized
    fun connect(): Boolean {
        if (isConnected) return true
        cleanupLocked()
        _state.value = PrivdConnectionState.CONNECTING
        val key = hmacKeyBytes
        if (key == null) {
            AppLog.w(TAG, "connect(): no per-install key provisioned — run Privileged Mode setup wizard")
            _state.value = PrivdConnectionState.DISCONNECTED
            return false
        }
        for (port in portStart..portEnd) {
            try {
                AppLog.d(TAG, "connect(): trying 127.0.0.1:$port")
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)

                val w = BufferedWriter(OutputStreamWriter(s.outputStream))
                val r = BufferedReader(InputStreamReader(s.inputStream))

                if (performHmacHandshake(s, r, w, key)) {
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
                    AppLog.i(TAG, "connect() succeeded on port $port")
                    return true
                } else {
                    AppLog.w(TAG, "connect(): HMAC handshake failed on port $port")
                    try {
                        s.close()
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                AppLog.d(TAG, "connect(): port $port not available — $e")
            }
        }
        AppLog.w(TAG, "connect() failed: daemon not reachable on any port in range $portStart..$portEnd")
        cleanupLocked()
        _state.value = PrivdConnectionState.DISCONNECTED
        return false
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
    suspend fun ping(): Boolean =
        commandMutex.withLock {
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
    ): Boolean =
        commandMutex.withLock {
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
    suspend fun stopMirror(): Boolean =
        commandMutex.withLock {
            if (!isConnected) return false
            val deferred = CompletableDeferred<Boolean>()
            mirrorStopDeferred = deferred
            send("MIRROR STOP\n")
            val ok = withTimeoutOrNull(MIRROR_STOP_TIMEOUT_MS) { deferred.await() } ?: false
            mirrorStopDeferred = null
            AppLog.i(TAG, "stopMirror() → $ok")
            return ok
        }

    suspend fun takeScreenshot(
        path: String,
        displayId: Int? = null,
    ): Boolean =
        commandMutex.withLock {
            if (!isConnected) return false
            val deferred = CompletableDeferred<Boolean>()
            screenshotDeferred = deferred
            val cmd = if (displayId != null) "SCREENSHOT $displayId $path\n" else "SCREENSHOT $path\n"
            send(cmd)
            val ok = withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) { deferred.await() } ?: false
            screenshotDeferred = null
            AppLog.i(TAG, "takeScreenshot($path, displayId=$displayId) -> $ok")
            return ok
        }

    suspend fun readTextFile(path: String): String? =
        commandMutex.withLock {
            if (!isConnected) return null
            val deferred = CompletableDeferred<String?>()
            readFileDeferred = deferred
            send("READ_FILE $path\n")
            val result = withTimeoutOrNull(READ_FILE_TIMEOUT_MS) { deferred.await() }
            readFileDeferred = null
            isCapturingReadFile = false
            AppLog.i(TAG, "readTextFile($path) fetched ${result?.length ?: 0} bytes")
            return result
        }

    suspend fun getRunningProcesses(): String? =
        commandMutex.withLock {
            if (!isConnected) return null
            val deferred = CompletableDeferred<String?>()
            listProcessesDeferred = deferred
            send("LIST_PROCESSES\n")
            val result = withTimeoutOrNull(READ_FILE_TIMEOUT_MS) { deferred.await() }
            listProcessesDeferred = null
            isCapturingProcesses = false
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
            var line =
                try {
                    queue.take()
                } catch (_: InterruptedException) {
                    break
                }
            if (line.startsWith("MM ")) {
                var dx = 0
                var dy = 0
                var parsedOk = false
                try {
                    val parts = line.trim().split(' ')
                    if (parts.size == 3) {
                        dx = parts[1].toInt()
                        dy = parts[2].toInt()
                        parsedOk = true
                    }
                } catch (_: Exception) {
                }

                if (parsedOk) {
                    while (true) {
                        val next = queue.peek() ?: break
                        if (!next.startsWith("MM ")) break
                        queue.poll()
                        try {
                            val parts = next.trim().split(' ')
                            if (parts.size == 3) {
                                dx += parts[1].toInt()
                                dy += parts[2].toInt()
                            }
                        } catch (_: Exception) {
                        }
                    }
                    line = "MM $dx $dy\n"
                }
            } else if (line.startsWith("M ")) {
                try {
                    val parts = line.trim().split(' ')
                    if (parts.size == 4) {
                        val slot = parts[1].toInt()
                        while (true) {
                            val next = queue.peek() ?: break
                            if (!next.startsWith("M ")) break
                            val nextParts = next.trim().split(' ')
                            if (nextParts.size != 4 || nextParts[1].toInt() != slot) break
                            queue.poll()
                            line = next
                        }
                    }
                } catch (_: Exception) {
                }
            }
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
            val line =
                try {
                    r.readLine() ?: break
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    AppLog.w(TAG, "readerLoop failed: $e")
                    break
                }
            when (line) {
                "PONG" -> {
                    pingDeferred?.complete(true)
                    continue
                }

                "MIRROR_STOPPED" -> {
                    mirrorStopDeferred?.complete(true)
                    continue
                }

                "SCREENSHOT_OK" -> {
                    screenshotDeferred?.complete(true)
                    continue
                }

                "READ_BEGIN" -> {
                    isCapturingReadFile = true
                    synchronized(dumpLock) { readFileDumpBuilder.clear() }
                    continue
                }

                "READ_END" -> {
                    isCapturingReadFile = false
                    val content = synchronized(dumpLock) { readFileDumpBuilder.toString().also { readFileDumpBuilder.clear() } }
                    readFileDeferred?.complete(content)
                    continue
                }

                "PROC_BEGIN" -> {
                    isCapturingProcesses = true
                    synchronized(dumpLock) { processesDumpBuilder.clear() }
                    continue
                }

                "PROC_END" -> {
                    isCapturingProcesses = false
                    val content = synchronized(dumpLock) { processesDumpBuilder.toString().also { processesDumpBuilder.clear() } }
                    listProcessesDeferred?.complete(content)
                    continue
                }
            }
            if (line.startsWith("MIRROR_DIRECT_READY")) {
                mirrorDirectStartDeferred?.complete(true)
                continue
            }
            if (line.startsWith("MIRROR_DIRECT_ERR")) {
                mirrorDirectStartDeferred?.complete(false)
                continue
            }
            if (line.startsWith("SCREENSHOT_ERR")) {
                screenshotDeferred?.complete(false)
                continue
            }
            if (line.startsWith("READ_ERR")) {
                isCapturingReadFile = false
                readFileDeferred?.complete(null)
                continue
            }
            if (line.startsWith("PROC_ERR")) {
                isCapturingProcesses = false
                listProcessesDeferred?.complete(null)
                continue
            }
            if (isCapturingReadFile) {
                synchronized(dumpLock) { readFileDumpBuilder.append(line).append('\n') }
                continue
            }
            if (isCapturingProcesses) {
                synchronized(dumpLock) { processesDumpBuilder.append(line).append('\n') }
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
        screenshotDeferred?.complete(false)
        screenshotDeferred = null
        readFileDeferred?.complete(null)
        readFileDeferred = null
        isCapturingReadFile = false
        listProcessesDeferred?.complete(null)
        listProcessesDeferred = null
        isCapturingProcesses = false
        synchronized(dumpLock) {
            readFileDumpBuilder.clear()
            processesDumpBuilder.clear()
        }
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
