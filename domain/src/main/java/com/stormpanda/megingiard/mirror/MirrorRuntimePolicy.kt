package com.stormpanda.megingiard.mirror

/** Runtime inputs for reconciling the active layout's persisted mirror preference. */
data class MirrorRuntimePolicyState(
    val promptInFlight: Boolean,
    val isOnValidScreen: Boolean,
    val isCapturing: Boolean,
    val globalAutoStart: Boolean,
    val layoutWantsMirror: Boolean,
    val confirmedCapturingWithMirrorOn: Boolean,
)

enum class MirrorRuntimeAction {
    NONE,
    START,
    STOP,
}

data class MirrorRuntimeDecision(
    val action: MirrorRuntimeAction,
    val confirmedCapturingWithMirrorOn: Boolean,
)

/**
 * Reconciles runtime capture state with the active layout's persisted mirror state.
 *
 * The confirmed-on latch prevents a stale `layoutWantsMirror=false` snapshot from
 * stopping a freshly-started session before the active-layout StateFlow has observed
 * the persisted `mirrorAutoStart=true` update.
 */
fun decideMirrorRuntimeAction(state: MirrorRuntimePolicyState): MirrorRuntimeDecision {
    if (!state.isOnValidScreen) {
        return MirrorRuntimeDecision(
            action = MirrorRuntimeAction.NONE,
            confirmedCapturingWithMirrorOn = state.confirmedCapturingWithMirrorOn,
        )
    }

    var confirmed = state.confirmedCapturingWithMirrorOn
    if (!state.isCapturing) confirmed = false
    if (state.isCapturing && state.layoutWantsMirror) confirmed = true

    val action = when {
        state.layoutWantsMirror &&
            state.globalAutoStart &&
            !state.isCapturing &&
            !state.promptInFlight -> MirrorRuntimeAction.START

        confirmed &&
            !state.layoutWantsMirror &&
            state.isCapturing -> {
                confirmed = false
                MirrorRuntimeAction.STOP
            }

        else -> MirrorRuntimeAction.NONE
    }

    return MirrorRuntimeDecision(
        action = action,
        confirmedCapturingWithMirrorOn = confirmed,
    )
}
