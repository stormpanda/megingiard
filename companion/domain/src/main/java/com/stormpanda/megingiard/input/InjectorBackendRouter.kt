package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Thread-safe strategy router helper for native input injector facades in `:domain`.
 *
 * Manages backend determination ([PrivdClient.isConnected] vs standard virtual uinput binary),
 * lifecycle logging, connection status queries, and automatic backend re-synchronization
 * upon Privd daemon reconnects across [KeyInjector], [MouseInjector], [GamepadInjector],
 * and [TouchInjector].
 */
class InjectorBackendRouter(
    private val tag: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onPrivdConnected: (() -> Unit)? = null,
    private val onPrivdDisconnected: (() -> Unit)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile
    private var usePrivd: Boolean = false

    @Volatile
    private var active: Boolean = false

    init {
        scope.launch {
            PrivdClient.state.collect { state ->
                val isConnected = (state == PrivdConnectionState.CONNECTED)
                if (active) {
                    if (isConnected) {
                        AppLog.i(tag, "Privd connected while injector active -> sync PRIVD backend")
                        usePrivd = true
                        onPrivdConnected?.invoke()
                    } else if (usePrivd) {
                        AppLog.i(tag, "Privd disconnected while injector active -> switch to fallback")
                        usePrivd = false
                        onPrivdDisconnected?.invoke()
                    }
                }
            }
        }
    }

    /**
     * Determines the active backend strategy upon starting the injector.
     * Returns true if the privileged daemon (`PRIVD`) is connected, false for fallback (`VIRTUAL_UINPUT`).
     */
    fun resolveBackend(): Boolean {
        active = true
        usePrivd = PrivdClient.isConnected
        AppLog.i(tag, "start() — backend=${if (usePrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        return usePrivd
    }

    /**
     * Marks the injector backend as stopped so background connection changes no longer dispatch commands.
     */
    fun markStopped() {
        active = false
    }

    /**
     * Returns whether the privileged backend is currently selected.
     */
    val isPrivd: Boolean get() = usePrivd || PrivdClient.isConnected

    /**
     * Queries whether the underlying backend injector is active.
     */
    fun isRunning(isFallbackRunning: () -> Boolean): Boolean =
        if (!active) {
            false
        } else if (usePrivd) {
            PrivdClient.isConnected
        } else {
            isFallbackRunning()
        }

    /**
     * Executes [privdAction] if the privileged backend is active, otherwise [shellAction].
     */
    inline fun dispatch(
        privdAction: () -> Unit,
        shellAction: () -> Unit,
    ) {
        if (isPrivd) {
            privdAction()
        } else {
            shellAction()
        }
    }
}
