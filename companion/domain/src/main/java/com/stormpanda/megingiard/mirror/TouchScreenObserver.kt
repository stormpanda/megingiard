package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.Executors

private const val TAG = "TouchScreenObserver"
private const val EVENT_NODE = "/dev/input/event6"
private const val INPUT_EVENT_SIZE = 24
private const val MAX_TOUCH_SLOTS = 10

// Linux evdev protocol constants
private const val EV_SYN = 0
private const val EV_KEY = 1
private const val EV_ABS = 3

private const val SYN_REPORT = 0
private const val BTN_TOUCH = 0x14a

private const val ABS_MT_SLOT = 0x2f
private const val ABS_MT_TOUCH_MAJOR = 0x30
private const val ABS_MT_POSITION_X = 0x35
private const val ABS_MT_POSITION_Y = 0x36
private const val ABS_MT_TRACKING_ID = 0x39

private class SlotTracker(
    val slotId: Int,
) {
    var trackingId: Int = -1
    var rawX: Int? = null
    var rawY: Int? = null
    var isActive: Boolean = false
    var isNewDown: Boolean = false
    var isPendingUp: Boolean = false
    var hasMovedInPacket: Boolean = false

    fun reset() {
        trackingId = -1
        rawX = null
        rawY = null
        isActive = false
        isNewDown = false
        isPendingUp = false
        hasMovedInPacket = false
    }
}

/**
 * Passive evdev reader for the primary touchscreen device node (`/dev/input/event6`).
 *
 * Monitors physical touches on Display 0 in real time without grabbing the device (`EVIOCGRAB`),
 * allowing the foreground game or emulator to receive all native touches unimpeded with zero latency.
 *
 * Uses token-based client reference counting so multiple consumers (e.g. Follow Mode, Touch Macro Recording)
 * can safely request touch observation without prematurely closing active sessions.
 */
object TouchScreenObserver {
    private val activeClients = Collections.synchronizedSet(mutableSetOf<String>())
    private var job: Job? = null

    // Created per start(), closed per stop() so the backing thread does not outlive the session.
    private var dispatcher: ExecutorCoroutineDispatcher? = null

    // Retained so stop() can close the stream and unblock the in-progress blocking read() immediately.
    @Volatile private var activeStream: FileInputStream? = null

    /**
     * Legacy single-point callback for normalized landscape coordinates `[0, 1]`.
     */
    @Volatile var onTouchNormalized: ((Float, Float) -> Unit)? = null

    /**
     * Full multi-touch stream callback delivering slot index, action, and normalized landscape coordinates `[0, 1]`.
     */
    @Volatile var onTouchEvent: ((slot: Int, action: TouchAction, normX: Float, normY: Float) -> Unit)? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    /**
     * Starts the passive touch screen reader for a specific client [clientToken].
     * Coordinates lifecycle across multiple concurrent active clients.
     */
    @Synchronized
    fun start(clientToken: String) {
        val wasEmpty = activeClients.isEmpty()
        activeClients.add(clientToken)
        AppLog.i(TAG, "start() client='$clientToken' activeClients=$activeClients")
        if (wasEmpty) {
            startInternal()
        }
    }

    /**
     * Stops the passive touch screen reader for a specific client [clientToken].
     * Only terminates the underlying reader thread when all registered clients have released it.
     */
    @Synchronized
    fun stop(clientToken: String) {
        if (!activeClients.contains(clientToken)) {
            AppLog.d(TAG, "stop() called for non-active client '$clientToken'. Ignoring.")
            return
        }
        activeClients.remove(clientToken)
        AppLog.i(TAG, "stop() client='$clientToken' activeClients=$activeClients")
        if (activeClients.isEmpty()) {
            onTouchNormalized = null
            onTouchEvent = null
            stopInternal()
        }
    }

    /**
     * Force-stops all clients and tears down the reader thread. For testing / cleanup only.
     */
    @Synchronized
    fun stopAll() {
        AppLog.i(TAG, "stopAll() activeClients=$activeClients")
        activeClients.clear()
        onTouchNormalized = null
        onTouchEvent = null
        stopInternal()
    }

    private fun startInternal() {
        if (job != null) return
        AppLog.i(TAG, "startInternal()")
        val exec = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        dispatcher = exec
        job =
            CoroutineScope(exec).launch {
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

                        val slots = Array(MAX_TOUCH_SLOTS) { SlotTracker(it) }
                        var currentSlot = 0

                        while (coroutineContext[Job]?.isActive == true) {
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

                            when (type) {
                                EV_ABS -> {
                                    when (code) {
                                        ABS_MT_SLOT -> {
                                            currentSlot = value.coerceIn(0, MAX_TOUCH_SLOTS - 1)
                                        }

                                        ABS_MT_TRACKING_ID -> {
                                            val slot = slots[currentSlot]
                                            if (value == -1) {
                                                if (slot.isActive) {
                                                    slot.isActive = false
                                                    slot.isPendingUp = true
                                                    slot.trackingId = -1
                                                }
                                            } else {
                                                if (!slot.isActive) {
                                                    slot.isActive = true
                                                    slot.isNewDown = true
                                                    slot.trackingId = value
                                                }
                                            }
                                        }

                                        ABS_MT_POSITION_X -> {
                                            val slot = slots[currentSlot]
                                            slot.rawX = value
                                            slot.hasMovedInPacket = true
                                        }

                                        ABS_MT_POSITION_Y -> {
                                            val slot = slots[currentSlot]
                                            slot.rawY = value
                                            slot.hasMovedInPacket = true
                                        }
                                    }
                                }

                                EV_SYN -> {
                                    if (code == SYN_REPORT) {
                                        for (slot in slots) {
                                            val rx = slot.rawX
                                            val ry = slot.rawY
                                            if (rx != null && ry != null) {
                                                val nx = (ry.toFloat() / TouchInjector.THOR_SENSOR_H).coerceIn(0f, 1f)
                                                val ny = (1.0f - (rx.toFloat() / TouchInjector.THOR_SENSOR_W)).coerceIn(0f, 1f)

                                                if (slot.isNewDown) {
                                                    slot.isNewDown = false
                                                    onTouchEvent?.invoke(slot.slotId, TouchAction.DOWN, nx, ny)
                                                    onTouchNormalized?.invoke(nx, ny)
                                                } else if (slot.isActive && slot.hasMovedInPacket) {
                                                    slot.hasMovedInPacket = false
                                                    onTouchEvent?.invoke(slot.slotId, TouchAction.MOVE, nx, ny)
                                                    onTouchNormalized?.invoke(nx, ny)
                                                }

                                                if (slot.isPendingUp) {
                                                    slot.isPendingUp = false
                                                    onTouchEvent?.invoke(slot.slotId, TouchAction.UP, nx, ny)
                                                }
                                            }
                                        }
                                    }
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

    private fun stopInternal() {
        AppLog.i(TAG, "stopInternal()")
        activeStream?.close() // unblocks the blocking read() immediately
        activeStream = null
        job?.cancel()
        job = null
        dispatcher?.close() // shuts down the backing executor thread
        dispatcher = null
    }
}
