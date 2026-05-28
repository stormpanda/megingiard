package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

private const val TAG = "TouchScreenObserver"
private const val EVENT_NODE = "/dev/input/event6"
private const val INPUT_EVENT_SIZE = 24

object TouchScreenObserver {
    private var job: Job? = null
    // Created per start(), closed per stop() so the backing thread does not outlive the session.
    private var dispatcher: ExecutorCoroutineDispatcher? = null
    // Retained so stop() can close the stream and unblock the in-progress blocking read() immediately.
    @Volatile private var activeStream: FileInputStream? = null

    // Callback invoked when a touch coordinate is received.
    // Coordinates are normalized landscape [0, 1].
    @Volatile var onTouchNormalized: ((Float, Float) -> Unit)? = null

    fun start() {
        if (job != null) return
        AppLog.i(TAG, "start()")
        val exec = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        dispatcher = exec
        job = CoroutineScope(exec).launch {
            val file = File(EVENT_NODE)
            if (!file.exists()) {
                AppLog.e(TAG, "Touch event node $EVENT_NODE does not exist")
                return@launch
            }
            try {
                val fis = FileInputStream(file)
                activeStream = fis
                fis.use {
                    val buffer = ByteArray(INPUT_EVENT_SIZE)
                    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder())

                    var currentX: Int? = null
                    var currentY: Int? = null

                    while (job?.isActive == true) {
                        var bytesRead = 0
                        while (bytesRead < INPUT_EVENT_SIZE) {
                            val r = fis.read(buffer, bytesRead, INPUT_EVENT_SIZE - bytesRead)
                            if (r < 0) break
                            bytesRead += r
                        }
                        if (bytesRead < INPUT_EVENT_SIZE) {
                            AppLog.w(TAG, "Read fewer bytes than expected input event size, stopping")
                            break
                        }

                        byteBuffer.rewind()
                        // Skip timeval (16 bytes on 64-bit systems)
                        byteBuffer.position(16)
                        val type = byteBuffer.short.toInt() and 0xFFFF
                        val code = byteBuffer.short.toInt() and 0xFFFF
                        val value = byteBuffer.int

                        // EV_ABS = 3
                        if (type == 3) {
                            if (code == 53) { // ABS_MT_POSITION_X
                                currentX = value
                            } else if (code == 54) { // ABS_MT_POSITION_Y
                                currentY = value
                            }
                        } else if (type == 0 && code == 0) { // EV_SYN / SYN_REPORT
                            val x = currentX
                            val y = currentY
                            if (x != null && y != null) {
                                // Map to normalized landscape coordinates:
                                // normalizedX = sensor_y / 1920f
                                // normalizedY = 1.0f - (sensor_x / 1080f)
                                val nx = y.toFloat() / 1920f
                                val ny = 1.0f - (x.toFloat() / 1080f)
                                // Coerce to [0, 1]
                                val coercedNx = nx.coerceIn(0f, 1f)
                                val coercedNy = ny.coerceIn(0f, 1f)
                                onTouchNormalized?.invoke(coercedNx, coercedNy)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Exception in touch screen reading loop: $e")
            } finally {
                activeStream = null
            }
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop()")
        activeStream?.close() // unblocks the blocking read() immediately
        activeStream = null
        job?.cancel()
        job = null
        dispatcher?.close()  // shuts down the backing executor thread
        dispatcher = null
    }
}
