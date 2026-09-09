package com.stormpanda.megingiard.mirror

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class CutoutShape {
    RECTANGLE,
    CIRCLE,
}

@Serializable
enum class AspectRatioMode {
    FREE,
    TOP,
    BOTTOM,
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
 * @param hasTransparencyMask Whether an auto-tuned transparency mask bitmap is present for this cutout.
 * @param maskFeathering Outward edge expansion radius in pixels (0..10) with decreasing opacity falloff.
 * @param maskTranslucency Semi-transparent HUD capture sensitivity level (0..100%), preserving dials and glows.
 * @param freezeOnHudLoss Whether to freeze the last valid HUD frame when the HUD element is absent (e.g. cutscene or menu).
 * @param customAnchorEnabled Whether presence detection monitors a separate reference anchor rather than the cutout's own crop.
 * @param anchorSrcX Normalized X position of custom reference anchor on primary screen.
 * @param anchorSrcY Normalized Y position of custom reference anchor on primary screen.
 * @param anchorSrcWidth Normalized width of custom reference anchor on primary screen.
 * @param anchorSrcHeight Normalized height of custom reference anchor on primary screen.
 * @param anchorCutoutId Optional ID of another cutout whose presence state or crop is borrowed as reference anchor.
 * @param streamDelayFrames Number of frames (0..10) to delay the live stream by to eliminate cutscene flicker.
 */
@Serializable
data class AnchorCrop(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

const val DEFAULT_ANCHOR_SIZE = 0.15f
const val MAX_STREAM_DELAY_FRAMES = 10

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
    val motionSmoothingStrength: Int = 85,
    val followTouch: Boolean = false,
    val touchProjectionEnabled: Boolean = false,
    val shape: CutoutShape = CutoutShape.RECTANGLE,
    val aspectRatioMode: AspectRatioMode = if (keepAspectRatio) AspectRatioMode.TOP else AspectRatioMode.BOTTOM,
    val hasTransparencyMask: Boolean = false,
    val maskFeathering: Int = 0,
    val maskTranslucency: Int = 0,
    val freezeOnHudLoss: Boolean = false,
    val customAnchorEnabled: Boolean = false,
    val anchorSrcX: Float = 0f,
    val anchorSrcY: Float = 0f,
    val anchorSrcWidth: Float = DEFAULT_ANCHOR_SIZE,
    val anchorSrcHeight: Float = DEFAULT_ANCHOR_SIZE,
    val anchorCutoutId: String? = null,
    val streamDelayFrames: Int = 0,
) {
    /**
     * Resolves the effective normalized screen crop rectangle used for presence anchor sampling.
     * If [customAnchorEnabled] is false, returns the cutout's own crop ([srcX], [srcY], [srcWidth], [srcHeight]).
     * If linked to another cutout via [anchorCutoutId], recursively resolves that cutout's anchor crop (with cycle protection).
     * Otherwise returns the custom anchor bounds ([anchorSrcX], [anchorSrcY], [anchorSrcWidth], [anchorSrcHeight]).
     */
    fun getEffectiveAnchorCrop(allCutouts: List<ScreenCutout> = emptyList()): AnchorCrop {
        if (!customAnchorEnabled) {
            return AnchorCrop(srcX, srcY, srcWidth, srcHeight)
        }
        if (anchorCutoutId != null) {
            val visited = mutableSetOf(id)
            var currentLinkedId: String? = anchorCutoutId
            while (currentLinkedId != null && currentLinkedId !in visited) {
                visited.add(currentLinkedId)
                val target = allCutouts.find { it.id == currentLinkedId } ?: break
                if (!target.customAnchorEnabled || target.anchorCutoutId == null) {
                    return target.getEffectiveAnchorCrop(allCutouts)
                }
                currentLinkedId = target.anchorCutoutId
            }
        }
        val w = if (anchorSrcWidth > 0f) anchorSrcWidth else DEFAULT_ANCHOR_SIZE
        val h = if (anchorSrcHeight > 0f) anchorSrcHeight else DEFAULT_ANCHOR_SIZE
        return AnchorCrop(anchorSrcX, anchorSrcY, w, h)
    }

    companion object {
        val FULLSCREEN =
            ScreenCutout(
                id = "fullscreen_master",
                name = "",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                opacity = 1f,
                shape = CutoutShape.RECTANGLE,
                aspectRatioMode = AspectRatioMode.TOP,
            )

        fun createDefault(
            srcPixelWidth: Float = 1920f,
            srcPixelHeight: Float = 1080f,
            bottomPixelWidth: Float = 4f,
            bottomPixelHeight: Float = 3f,
        ): ScreenCutout {
            val srcRatio = srcPixelWidth / srcPixelHeight
            val destRatio = bottomPixelWidth / bottomPixelHeight
            val destWidth: Float
            val destHeight: Float
            val destX: Float
            val destY: Float

            if (srcRatio > destRatio) {
                // Fit-to-width
                destWidth = 1f
                destHeight = (bottomPixelWidth * srcPixelHeight) / (bottomPixelHeight * srcPixelWidth)
                destX = 0f
                destY = (1f - destHeight) / 2f
            } else {
                // Fit-to-height
                destHeight = 1f
                destWidth = (bottomPixelHeight * srcPixelWidth) / (bottomPixelWidth * srcPixelHeight)
                destX = (1f - destWidth) / 2f
                destY = 0f
            }

            return ScreenCutout(
                id = UUID.randomUUID().toString(),
                name = "",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = destX,
                destY = destY,
                destWidth = destWidth,
                destHeight = destHeight,
                aspectRatioMode = AspectRatioMode.TOP,
            )
        }
    }
}
