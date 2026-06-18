package com.stormpanda.megingiard.mirror

import kotlinx.serialization.Serializable

private const val TAG = "ScreenCutout"

/**
 * Represents a single cropped section of the primary display (source)
 * that is displayed and positioned on the secondary display (destination).
 *
 * All coordinates are normalized in the range [0.0, 1.0].
 *
 * @param id          Unique identifier for this cutout.
 * @param name        User-friendly label/name for the cutout.
 * @param srcX        Normalized X start of the crop on the primary screen.
 * @param srcY        Normalized Y start of the crop on the primary screen.
 * @param srcWidth    Normalized width of the crop on the primary screen.
 * @param srcHeight   Normalized height of the crop on the primary screen.
 * @param destX       Normalized X start of the destination bounds on the secondary screen.
 * @param destY       Normalized Y start of the destination bounds on the secondary screen.
 * @param destWidth   Normalized width of the destination bounds on the secondary screen.
 * @param destHeight  Normalized height of the destination bounds on the secondary screen.
 * @param opacity     Transparency level [0.0, 1.0] of this cutout.
 */
@Serializable
data class ScreenCutout(
    val id: String,
    val name: String = "",
    val srcX: Float,
    val srcY: Float,
    val srcWidth: Float,
    val srcHeight: Float,
    val destX: Float,
    val destY: Float,
    val destWidth: Float,
    val destHeight: Float,
    val opacity: Float = 1.0f,
    val keepAspectRatio: Boolean = false,
)
