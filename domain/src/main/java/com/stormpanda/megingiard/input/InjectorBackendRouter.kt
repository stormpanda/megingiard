package com.stormpanda.megingiard.input

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient

/**
 * Thread-safe strategy router helper for native input injector facades in `:domain`.
 *
 * Manages backend determination ([PrivdClient.isConnected] vs standard virtual uinput binary),
 * lifecycle logging, and connection status queries across [KeyInjector], [MouseInjector],
 * [GamepadInjector], and [TouchInjector].
 */
class InjectorBackendRouter(
    private val tag: String,
) {
    @Volatile
    private var usePrivd: Boolean = false

    /**
     * Determines the active backend strategy upon starting the injector.
     * Returns true if the privileged daemon (`PRIVD`) is connected, false for fallback (`VIRTUAL_UINPUT`).
     */
    fun resolveBackend(): Boolean {
        usePrivd = PrivdClient.isConnected
        AppLog.i(tag, "start() — backend=${if (usePrivd) "PRIVD" else "VIRTUAL_UINPUT"}")
        return usePrivd
    }

    /**
     * Returns whether the privileged backend is currently selected.
     */
    val isPrivd: Boolean get() = usePrivd

    /**
     * Queries whether the underlying backend injector is active.
     */
    fun isRunning(isFallbackRunning: () -> Boolean): Boolean = if (usePrivd) PrivdClient.isConnected else isFallbackRunning()

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
