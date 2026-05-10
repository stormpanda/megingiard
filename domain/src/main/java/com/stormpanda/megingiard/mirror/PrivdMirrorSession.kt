package com.stormpanda.megingiard.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.view.Surface
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.nio.ByteBuffer

private const val TAG = "PrivdMirrorSession"
private const val DECODER_MIME = "video/avc"
private const val DEQUEUE_TIMEOUT_US = 100_000L
private const val DEFAULT_BITRATE = 8_000_000
private const val DEFAULT_MAX_FPS = 60

/**
 * One privileged-mirror session.
 *
 * Lifecycle:
 * 1. [start] — sends `MIRROR START` over the privd control socket, opens a
 *    second [LocalSocket] to the mirror data socket the daemon hands back,
 *    creates a [MediaCodec] H.264 decoder bound to [outputSurface], and
 *    spawns a reader thread that demultiplexes length-prefixed NAL units
 *    and feeds them into the decoder.
 * 2. [stop] — interrupts the reader thread, releases the decoder, closes
 *    the data socket, and sends `MIRROR STOP` to the daemon.
 *
 * Per AGENTS.md §4: state is exposed read-only via [state]; the backing
 * [MutableStateFlow] is private.
 */
class PrivdMirrorSession(
    private val outputSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val bitrate: Int = DEFAULT_BITRATE,
    private val maxFps: Int = DEFAULT_MAX_FPS,
) {
    enum class State { IDLE, STARTING, RUNNING, STOPPED, FAILED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var dataSocket: LocalSocket? = null
    @Volatile private var decoder: MediaCodec? = null
    @Volatile private var readerThread: Thread? = null
    @Volatile private var stopping = false

    /**
     * Suspends until the daemon confirms readiness or fails. Returns `true`
     * when the session transitions to [State.RUNNING].
     */
    suspend fun start(): Boolean {
        if (_state.value != State.IDLE) return _state.value == State.RUNNING
        _state.value = State.STARTING
        AppLog.i(TAG, "start() ${width}x${height} @${maxFps}fps ${bitrate}bps")

        val socketName = PrivdClient.startMirror(width, height, bitrate, maxFps)
        if (socketName.isNullOrBlank()) {
            AppLog.w(TAG, "daemon refused MIRROR START")
            _state.value = State.FAILED
            return false
        }

        return try {
            val sock = LocalSocket()
            sock.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            dataSocket = sock

            val fmt = MediaFormat.createVideoFormat(DECODER_MIME, width, height)
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, maxFps)
            val dec = MediaCodec.createDecoderByType(DECODER_MIME)
            dec.configure(fmt, outputSurface, null, 0)
            dec.start()
            decoder = dec

            readerThread = Thread({ runReader(sock, dec) }, "PrivdMirrorReader").apply {
                isDaemon = true
                start()
            }
            _state.value = State.RUNNING
            AppLog.i(TAG, "session running")
            true
        } catch (t: Throwable) {
            AppLog.e(TAG, "start() failed: $t")
            cleanup()
            _state.value = State.FAILED
            false
        }
    }

    /** Idempotent — safe to call multiple times. */
    fun stop() {
        if (stopping || _state.value == State.STOPPED || _state.value == State.IDLE) return
        stopping = true
        AppLog.i(TAG, "stop()")
        cleanup()
        scope.launch {
            runCatching { PrivdClient.stopMirror() }
        }
        _state.value = State.STOPPED
    }

    private fun cleanup() {
        runCatching { dataSocket?.shutdownInput() }
        runCatching { dataSocket?.shutdownOutput() }
        runCatching { dataSocket?.close() }
        dataSocket = null
        readerThread?.interrupt()
        readerThread = null
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
    }

    /**
     * Drains length-prefixed NAL units from [sock] and submits them to [dec].
     * Output buffers are released directly to the configured Surface so the
     * decoded frames render with zero copy.
     */
    private fun runReader(sock: LocalSocket, dec: MediaCodec) {
        val input = DataInputStream(sock.inputStream)
        val info = MediaCodec.BufferInfo()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val len = try { input.readInt() } catch (_: Throwable) { break }
                if (len <= 0 || len > 4 * 1024 * 1024) {
                    AppLog.w(TAG, "reader: invalid NAL length $len — aborting")
                    break
                }
                val payload = ByteArray(len)
                input.readFully(payload)

                // Submit to decoder.
                val inIdx = dec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIdx >= 0) {
                    val buf: ByteBuffer = dec.getInputBuffer(inIdx) ?: continue
                    buf.clear()
                    buf.put(payload)
                    dec.queueInputBuffer(inIdx, 0, len, System.nanoTime() / 1000, 0)
                }

                // Drain output (non-blocking).
                while (true) {
                    val outIdx = dec.dequeueOutputBuffer(info, 0)
                    if (outIdx < 0) break
                    dec.releaseOutputBuffer(outIdx, true)
                }
            }
        } catch (t: Throwable) {
            if (!stopping) AppLog.w(TAG, "reader ended: $t")
        } finally {
            if (!stopping) {
                AppLog.i(TAG, "reader exited unexpectedly — marking session FAILED")
                _state.value = State.FAILED
            }
        }
    }

    /** Final teardown — also cancels the internal coroutine scope. */
    fun release() {
        stop()
        scope.cancel()
    }
}
