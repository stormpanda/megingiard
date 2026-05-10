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
import java.util.concurrent.LinkedBlockingQueue

private const val TAG = "PrivdClient"
private const val ABSTRACT_NAME = "megingiard.privd"
private const val PING_TIMEOUT_MS = 1_500L
private const val MIRROR_DIRECT_START_TIMEOUT_MS = 4_000L
private const val MIRROR_STOP_TIMEOUT_MS = 3_000L
private const val WRITER_THREAD_NAME = "PrivdClientWriter"
private const val READER_THREAD_NAME = "PrivdClientReader"

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
            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.outputStream))
            reader = BufferedReader(InputStreamReader(s.inputStream))
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
}
