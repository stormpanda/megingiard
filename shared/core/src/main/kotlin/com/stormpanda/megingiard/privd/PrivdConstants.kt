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
    const val PRIVD_VERSION = 11

    const val DAEMON_ASSET_NAME = "megingiard_privd_arm64"
    const val MIRROR_DEX_ASSET_NAME = "megingiard_mirror.dex"

    const val PORT_RELEASE_START = 51234
    const val PORT_DEBUG_START = 51244

    fun isDebugPackageName(packageName: String): Boolean = packageName.contains(".debug")

    fun getDaemonRemotePath(isDebug: Boolean): String =
        if (isDebug) "/data/local/tmp/megingiard_privd_debug" else "/data/local/tmp/megingiard_privd"

    fun getDaemonProcessName(isDebug: Boolean): String = if (isDebug) "megingiard_privd_debug" else "megingiard_privd"

    fun getMirrorDexRemotePath(isDebug: Boolean): String =
        if (isDebug) "/data/local/tmp/megingiard_mirror_debug.dex" else "/data/local/tmp/megingiard_mirror.dex"

    fun getStateKeyFilePath(isDebug: Boolean): String =
        if (isDebug) "/data/local/tmp/megingiard_privd_debug.key" else "/data/local/tmp/megingiard_privd.key"

    fun getPortStart(isDebug: Boolean): Int = if (isDebug) PORT_DEBUG_START else PORT_RELEASE_START
}
