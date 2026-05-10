package com.stormpanda.megingiard.mirror

/**
 * Pure decision helper that selects between the legacy MediaProjection
 * mirror path and the privileged on-device daemon path.
 *
 * Kept in `:core` so it has no Android dependencies and is unit-testable
 * without Robolectric.
 */
enum class MirrorStrategy {
    /** Legacy `MediaProjection` + `VirtualDisplay` path (requires user consent). */
    MEDIA_PROJECTION,

    /** Privileged daemon spawns `app_process` mirror server (no consent dialog). */
    PRIVILEGED,
}

enum class PrivdMirrorTransport {
    /** Privileged SurfaceControl output directly to the secondary display target. */
    DIRECT_SURFACE,

    /** Current scrcpy-style fallback: H.264 over LocalSocket, decoded in the app. */
    H264_STREAM,
}

/**
 * Returns [MirrorStrategy.PRIVILEGED] iff both:
 *  - the privileged-mirror per-feature flag is enabled, **and**
 *  - the privileged daemon is currently `RUNNING`.
 *
 * Otherwise returns [MirrorStrategy.MEDIA_PROJECTION] — including any
 * transient state (`OFF`, `BOOTSTRAPPING`, `CONNECTING`, `FAILED`).
 *
 * @param privdMirrorEnabled value of `MacroPadSettings.privdMirrorEnabled`.
 * @param privdRunning `true` iff `PrivdManager.state == PrivdState.RUNNING`.
 */
fun selectMirrorStrategy(
    privdMirrorEnabled: Boolean,
    privdRunning: Boolean,
): MirrorStrategy =
    if (privdMirrorEnabled && privdRunning) MirrorStrategy.PRIVILEGED
    else MirrorStrategy.MEDIA_PROJECTION

fun selectPrivdMirrorTransport(
    directSurfaceAvailable: Boolean,
): PrivdMirrorTransport =
    if (directSurfaceAvailable) PrivdMirrorTransport.DIRECT_SURFACE
    else PrivdMirrorTransport.H264_STREAM
