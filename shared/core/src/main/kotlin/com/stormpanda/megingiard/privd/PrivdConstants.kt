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
    const val PRIVD_VERSION = 18

    const val DAEMON_BASE_NAME = "megingiard_privd"
    const val DAEMON_ASSET_NAME = "megingiard_privd_arm64"
    const val MIRROR_DEX_ASSET_NAME = "megingiard_mirror.dex"
    const val GFX_INJECT_ASSET_NAME = "megingiard_gfx_inject_arm64"
    const val GFX_LAYER_ASSET_NAME = "libVkLayer_MegingiardInspector.so"
    const val GFX_INJECT_REMOTE_PATH = "/data/local/tmp/megingiard_gfx_inject_arm64"
    const val GFX_LAYER_REMOTE_PATH = "/data/local/tmp/libVkLayer_MegingiardInspector.so"
    const val GFX_LAYER_NAME = "VK_LAYER_MEGINGIARD_INSPECTOR"

    const val PORT_RELEASE_START = 51234
    const val PORT_DEBUG_START = 51244

    fun isDebugPackageName(packageName: String): Boolean = packageName.contains(".debug")

    fun getDaemonRemotePath(isDebug: Boolean): String =
        if (isDebug) "/data/local/tmp/${DAEMON_BASE_NAME}_debug" else "/data/local/tmp/$DAEMON_BASE_NAME"

    fun getDaemonProcessName(isDebug: Boolean): String = if (isDebug) "${DAEMON_BASE_NAME}_debug" else DAEMON_BASE_NAME

    fun getMirrorDexRemotePath(isDebug: Boolean): String =
        if (isDebug) "/data/local/tmp/megingiard_mirror_debug.dex" else "/data/local/tmp/megingiard_mirror.dex"

    fun getStateKeyFilePath(isDebug: Boolean): String =
        if (isDebug) "/data/local/tmp/${DAEMON_BASE_NAME}_debug.key" else "/data/local/tmp/$DAEMON_BASE_NAME.key"

    fun getPortStart(isDebug: Boolean): Int = if (isDebug) PORT_DEBUG_START else PORT_RELEASE_START
}
