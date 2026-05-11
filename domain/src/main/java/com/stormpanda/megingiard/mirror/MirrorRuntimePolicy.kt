package com.stormpanda.megingiard.mirror

/** Runtime inputs for reconciling the active layout's persisted mirror preference. */
data class MirrorRuntimePolicyState(
    val promptInFlight: Boolean,
    val isOnValidScreen: Boolean,
    val isCapturing: Boolean,
    val globalAutoStart: Boolean,
    val layoutId: String?,
    val layoutWantsMirror: Boolean,
    val autoStartSuppressed: Boolean,
    /**
     * True while the privd mirror daemon is in a transient connecting state
     * (CONNECTING, BOOTSTRAPPING, or OFF-but-auto-connect-pending).
     * Blocks policy auto-start until the daemon settles so the correct
     * strategy (privd vs. MediaProjection consent) can be selected.
     */
    val privdMirrorConnecting: Boolean = false,
)

enum class MirrorRuntimeAction {
    NONE,
    START,
    STOP,
}

/**
 * Reconciles runtime capture state with the active layout's persisted mirror state.
 *
 * `PadLayout.mirrorAutoStart` is the single source of truth: a running capture
 * stops whenever the active layout does not want mirror, and a stopped capture
 * starts only when the active layout wants mirror and global auto-start allows it.
 * A freshly stopped or cancelled layout can suppress auto-start while asynchronous
 * layout state catches up, preventing immediate restart loops.
 */
fun decideMirrorRuntimeAction(state: MirrorRuntimePolicyState): MirrorRuntimeAction {
    if (!state.isOnValidScreen || state.layoutId == null) return MirrorRuntimeAction.NONE

    return when {
        state.isCapturing && !state.layoutWantsMirror -> MirrorRuntimeAction.STOP

        state.layoutWantsMirror &&
            state.globalAutoStart &&
            !state.autoStartSuppressed &&
            !state.isCapturing &&
            !state.promptInFlight &&
            !state.privdMirrorConnecting -> MirrorRuntimeAction.START
        else -> MirrorRuntimeAction.NONE
    }
}
