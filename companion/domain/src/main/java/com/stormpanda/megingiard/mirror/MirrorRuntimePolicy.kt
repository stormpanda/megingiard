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
    isManuallyDisconnected: Boolean = false,
): Boolean {
    val autoConnectPending = !isManuallyDisconnected
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
    /**
     * True when autonomous mode (AUTO) and profile-level auto layout switching are active
     * and the profile contains at least one layout with an enabled visual anchor.
     * Keeps screen capture alive so candidate layouts can be evaluated and auto-switched even
     * when the active layout does not explicitly request mirrorAutoStart or has no visual anchor.
     */
    val autoSwitchWantsMirror: Boolean = false,
    /**
     * True while the privd mirror daemon is in a transient connecting state
     * (CONNECTING, BOOTSTRAPPING, or OFF-but-auto-connect-pending).
     * Blocks policy auto-start until the daemon settles so the correct
     * strategy (privd vs. MediaProjection consent) can be selected.
     */
    val privdMirrorConnecting: Boolean = false,
    /** True if welcome onboarding or quick menu swipe tutorial is actively shown. */
    val tutorialsActive: Boolean = false,
    /** True if the fullscreen touchpad overlay is in Absolute Touch mode with touchpad mirroring enabled. */
    val touchpadWantsMirror: Boolean = false,
)

enum class MirrorRuntimeAction {
    NONE,
    START,
    STOP,
}

/**
 * Reconciles runtime capture state with the active layout's persisted mirror state.
 *
 * `PadLayout.mirrorAutoStart` is the single source of truth for individual layouts, supplemented
 * by touchpad mirroring and profile-level autonomous layout switching requirements.
 * A running capture stops whenever neither the active layout, touchpad mirroring, nor autonomous
 * layout switching wants capture.
 */
fun decideMirrorRuntimeAction(state: MirrorRuntimePolicyState): MirrorRuntimeAction {
    if (!state.isOnValidScreen || state.layoutId == null) return MirrorRuntimeAction.NONE

    val captureWanted = state.layoutWantsMirror || state.autoSwitchWantsMirror || state.touchpadWantsMirror

    return when {
        state.isCapturing && !captureWanted -> MirrorRuntimeAction.STOP

        captureWanted &&
            !state.isCapturing &&
            !state.promptInFlight &&
            !state.privdMirrorConnecting &&
            !state.tutorialsActive -> MirrorRuntimeAction.START

        else -> MirrorRuntimeAction.NONE
    }
}
