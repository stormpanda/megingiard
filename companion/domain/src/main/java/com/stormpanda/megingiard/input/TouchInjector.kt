package com.stormpanda.megingiard.input

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "TouchInjector"
private const val TOUCH_SLOT_MIN = 0
private const val TOUCH_SLOT_MAX = 9
private const val TOUCH_STOP_FLUSH_TIMEOUT_MS = 100L

/**
 * Shared touch injection facade used by both Touchpad and Mirror Touch Projection.
 *
 * Converts normalised logical-display coordinates to the physical portrait space of
 * the AYN Thor's primary touchscreen (`fts_ts`, `/dev/input/event6`) and routes them
 * to the running daemon if connected, otherwise falling back to [ShellInputInjector].
 *
 * Display 0 runs at ROTATION_270 (sensor mounted inverted relative to the logical
 * landscape orientation). The sensor's portrait X/Y map as:
 *   sensor_x = (1 − normalizedY) * PHYS_W
 *   sensor_y = normalizedX * PHYS_H
 *
 * @param normalizedX  0.0 (left edge) … 1.0 (right edge) of the logical touch surface
 * @param normalizedY  0.0 (top edge)  … 1.0 (bottom edge) of the logical touch surface
 */
object TouchInjector {
    // Physical dimensions of fts_ts (event6) in portrait orientation.
    // These are fixed hardware constants; they do not change with display rotation.
    const val THOR_SENSOR_W = 1080f
    const val THOR_SENSOR_H = 1920f

    private val activeClients = mutableSetOf<String>()

    @Volatile private var lastContext: Context? = null

    @Volatile private var usePrivd: Boolean = false

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            PrivdClient.state.collect { state ->
                synchronized(TouchInjector) {
                    if (activeClients.isNotEmpty()) {
                        val isConnected = (state == PrivdConnectionState.CONNECTED)
                        if (isConnected && !usePrivd) {
                            AppLog.i(TAG, "Privd connected while TouchInjector active -> switching to PRIVD backend")
                            usePrivd = true
                            if (ShellInputInjector.isRunning) {
                                ShellInputInjector.stop()
                            }
                        } else if (!isConnected && usePrivd) {
                            AppLog.i(TAG, "Privd disconnected while TouchInjector active -> switching to fallback")
                            usePrivd = false
                            lastContext?.let { ctx ->
                                if (!ShellInputInjector.isRunning) {
                                    ShellInputInjector.start(ctx)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts the native touch injector for a specific client [token].
     * Coordinates start/stop across multiple active clients. Safe to call if already running.
     */
    @Synchronized
    fun start(
        context: Context,
        token: String,
    ) {
        lastContext = context.applicationContext
        val wasEmpty = activeClients.isEmpty()
        activeClients.add(token)
        AppLog.i(TAG, "start() client='$token' activeClients=$activeClients")
        if (wasEmpty) {
            usePrivd = PrivdClient.isConnected
            AppLog.i(TAG, "start() — backend=${if (usePrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        }
        if (!usePrivd) {
            if (wasEmpty || !ShellInputInjector.isRunning) {
                if (!ShellInputInjector.isRunning) {
                    ShellInputInjector.start(context)
                }
            }
        }
    }

    /**
     * Stops the native touch injector for a specific client [token].
     * Coordinates teardown by only stopping the daemon when all clients have released it.
     */
    @Synchronized
    fun stop(token: String) {
        if (!activeClients.contains(token)) {
            AppLog.d(TAG, "stop() called for non-active client '$token'. Ignoring.")
            return
        }
        activeClients.remove(token)
        AppLog.i(TAG, "stop() client='$token' activeClients=$activeClients")
        if (activeClients.isEmpty()) {
            lastContext = null
            if (usePrivd) {
                releaseAllSlots()
                return
            }
            if (!ShellInputInjector.isRunning) {
                ShellInputInjector.stop()
                return
            }
            releaseAllSlots()
            /* Flush and stop on a daemon thread to avoid blocking the calling thread.
               stop() can be invoked from Compose DisposableEffect.onDispose (main thread). */
            Thread {
                if (!ShellInputInjector.flushPendingTouches(TOUCH_STOP_FLUSH_TIMEOUT_MS)) {
                    AppLog.w(TAG, "stop() timed out while flushing touch release commands")
                }
                synchronized(TouchInjector) {
                    if (activeClients.isEmpty()) {
                        ShellInputInjector.stop()
                    } else {
                        AppLog.i(TAG, "stop() aborted: new clients registered during flush: $activeClients")
                    }
                }
            }.also { it.isDaemon = true }.start()
        }
    }

    val isRunning: Boolean
        get() = if (usePrivd) PrivdClient.isConnected else ShellInputInjector.isRunning

    /**
     * Injects a touch event using normalised coordinates.
     *
     * Coordinates are clamped to the safe overrun range of [-0.5, 1.5] to prevent
     * signed integer overflow wrapping/jumps in target applications.
     *
     * @param action       DOWN / MOVE / UP
     * @param normalizedX  normalised coordinate, clamped to [-0.5, 1.5] (maps to logical screen bounds with safe overrun)
     * @param normalizedY  normalised coordinate, clamped to [-0.5, 1.5] (maps to logical screen bounds with safe overrun)
     */
    fun injectTouch(
        action: TouchAction,
        normalizedX: Float,
        normalizedY: Float,
    ) {
        injectTouch(0, action, normalizedX, normalizedY)
    }

    /**
     * Injects a slot-aware touch event using normalised coordinates.
     *
     * Coordinates are clamped to the safe overrun range of [-0.5, 1.5] to prevent
     * signed integer overflow wrapping/jumps in target applications.
     *
     * @param slot         touch slot index
     * @param action       DOWN / MOVE / UP
     * @param normalizedX  normalised coordinate, clamped to [-0.5, 1.5]
     * @param normalizedY  normalised coordinate, clamped to [-0.5, 1.5]
     */
    fun injectTouch(
        slot: Int,
        action: TouchAction,
        normalizedX: Float,
        normalizedY: Float,
    ) {
        val cx = normalizedX.coerceIn(-0.5f, 1.5f)
        val cy = normalizedY.coerceIn(-0.5f, 1.5f)
        val px = ((1f - cy) * THOR_SENSOR_W).toInt()
        val py = (cx * THOR_SENSOR_H).toInt()

        val isPrivd = usePrivd || PrivdClient.isConnected
        if (isPrivd) {
            if (action == TouchAction.UP) {
                PrivdClient.send("U $slot\n")
            } else {
                val char = if (action == TouchAction.DOWN) "D" else "M"
                PrivdClient.send("$char $slot $px $py\n")
            }
        } else {
            ShellInputInjector.injectTouch(slot, action, px, py)
        }
    }

    fun releaseAllSlots() {
        val isPrivd = usePrivd || PrivdClient.isConnected
        for (slot in TOUCH_SLOT_MIN..TOUCH_SLOT_MAX) {
            if (isPrivd) {
                PrivdClient.send("U $slot\n")
            } else {
                ShellInputInjector.injectTouch(slot, TouchAction.UP, 0, 0)
            }
        }
    }
}
