package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.privd.PrivdState

/**
 * Calculates whether screen mirroring auto-start should be blocked because the
 * privileged helper daemon is currently connecting or has failed and is awaiting user action.
 */
fun isPrivdMirrorConnecting(
    privdState: PrivdState,
    promptActive: Boolean,
    hasCreds: Boolean,
    dismissed: Boolean,
    isManuallyDisconnected: Boolean,
): Boolean {
    val autoConnectPending = hasCreds && !isManuallyDisconnected
    val promptShouldShow = privdState == PrivdState.FAILED && hasCreds && !dismissed
    return privdState == PrivdState.CONNECTING ||
        privdState == PrivdState.BOOTSTRAPPING ||
        (privdState == PrivdState.OFF && autoConnectPending) ||
        promptActive ||
        promptShouldShow
}

/** Runtime inputs for reconciling the active layout's persisted mirror preference. */
data class MirrorRuntimePolicyState(
    val promptInFlight: Boolean,
    val isOnValidScreen: Boolean,
    val isCapturing: Boolean,
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
    /** True if welcome onboarding or quick menu swipe tutorial is actively shown. */
    val tutorialsActive: Boolean = false,
    /** True if the companion home/dashboard screen is currently active. */
    val showIntegrationHome: Boolean = false,
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

    // If the companion integration dashboard is active, mirroring is prohibited.
    if (state.showIntegrationHome) {
        return if (state.isCapturing) MirrorRuntimeAction.STOP else MirrorRuntimeAction.NONE
    }

    return when {
        state.isCapturing && !state.layoutWantsMirror -> MirrorRuntimeAction.STOP

        state.layoutWantsMirror &&
            !state.autoStartSuppressed &&
            !state.isCapturing &&
            !state.promptInFlight &&
            !state.privdMirrorConnecting &&
            !state.tutorialsActive -> MirrorRuntimeAction.START

        else -> MirrorRuntimeAction.NONE
    }
}
