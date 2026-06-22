package com.stormpanda.megingiard.mirror

import kotlinx.serialization.Serializable

private const val TAG = "ScreenCutout"

@Serializable
enum class CutoutShape {
    RECTANGLE,
    CIRCLE
}

@Serializable
enum class AspectRatioMode {
    FREE,
    TOP,
    BOTTOM
}

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
 * @param shape       The visual shape of this cutout (rectangle or circle).
 * @param aspectRatioMode The mode specifying how aspect ratio is locked between top crop and bottom bounds.
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
    val motionSmoothing: Boolean = false,
    val shape: CutoutShape = CutoutShape.RECTANGLE,
    val aspectRatioMode: AspectRatioMode = if (keepAspectRatio) AspectRatioMode.TOP else AspectRatioMode.BOTTOM,
)
