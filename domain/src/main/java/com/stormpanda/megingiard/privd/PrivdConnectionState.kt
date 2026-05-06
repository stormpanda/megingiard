package com.stormpanda.megingiard.privd

/**
 * Connection state of the [PrivdClient] LocalSocket transport to the
 * `megingiard_privd` daemon.
 */
enum class PrivdConnectionState {
    /** Client is not connected and no connection attempt is in flight. */
    DISCONNECTED,

    /** Client is currently connecting (handshake in progress). */
    CONNECTING,

    /** Client is connected to the daemon and accepting commands. */
    CONNECTED,
}
