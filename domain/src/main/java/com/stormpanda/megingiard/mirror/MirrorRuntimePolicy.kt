package com.stormpanda.megingiard.mirror

/** Runtime inputs for reconciling the active layout's persisted mirror preference. */
data class MirrorRuntimePolicyState(
    val promptInFlight: Boolean,
    val isOnValidScreen: Boolean,
    val isCapturing: Boolean,
    val globalAutoStart: Boolean,
    val layoutId: String?,
    val layoutWantsMirror: Boolean,
    val confirmedMirrorLayoutId: String?,
)

enum class MirrorRuntimeAction {
    NONE,
    START,
    STOP,
}

data class MirrorRuntimeDecision(
    val action: MirrorRuntimeAction,
    val confirmedMirrorLayoutId: String?,
)

/**
 * Reconciles runtime capture state with the active layout's persisted mirror state.
 *
 * The confirmed-layout latch prevents a stale `layoutWantsMirror=false` snapshot
 * from stopping a freshly-started session before the active-layout StateFlow has
 * observed the persisted `mirrorAutoStart=true` update. Once a running session has
 * been confirmed on any layout, switching to an off-layout stops the runtime.
 */
fun decideMirrorRuntimeAction(state: MirrorRuntimePolicyState): MirrorRuntimeDecision {
    if (!state.isOnValidScreen) {
        return MirrorRuntimeDecision(
            action = MirrorRuntimeAction.NONE,
            confirmedMirrorLayoutId = state.confirmedMirrorLayoutId,
        )
    }

    var confirmedLayoutId = state.confirmedMirrorLayoutId
    if (!state.isCapturing) confirmedLayoutId = null
    if (state.isCapturing && state.layoutWantsMirror) {
        confirmedLayoutId = state.layoutId
    }

    val action = when {
        state.layoutWantsMirror &&
            state.globalAutoStart &&
            !state.isCapturing &&
            !state.promptInFlight -> MirrorRuntimeAction.START

        confirmedLayoutId != null &&
            !state.layoutWantsMirror &&
            state.isCapturing -> {
                confirmedLayoutId = null
                MirrorRuntimeAction.STOP
            }

        else -> MirrorRuntimeAction.NONE
    }

    return MirrorRuntimeDecision(
        action = action,
        confirmedMirrorLayoutId = confirmedLayoutId,
    )
}
