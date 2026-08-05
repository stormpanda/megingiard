package com.stormpanda.megingiard.privd

/**
 * Shared constants for the `megingiard_privd` privileged daemon and its transport protocol.
 */
object PrivdConstants {
    /**
     * Integer protocol and binary version of the `megingiard_privd` daemon.
     * Incrementing this value triggers automatic binary re-push and reconnection
     * dialogs across app upgrades and downgrades.
     */
    const val PRIVD_VERSION = 1
}
