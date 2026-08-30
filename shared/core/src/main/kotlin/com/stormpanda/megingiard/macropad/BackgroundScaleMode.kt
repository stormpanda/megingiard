package com.stormpanda.megingiard.macropad

import kotlinx.serialization.Serializable

/**
 * Scaling mode for layout background images when their aspect ratio differs from the screen aspect ratio.
 */
@Serializable
enum class BackgroundScaleMode {
    /**
     * Scales the image proportionally to fill the entire viewport without letterboxing/pillarboxing,
     * cropping excess parts (default behavior).
     */
    FILL,

    /**
     * Scales the image proportionally so the entire image is visible within the viewport,
     * adding black letterbox (top/bottom) or pillarbox (left/right) bars as needed.
     */
    FIT,

    /**
     * Stretches the image non-proportionally to exactly match the viewport width and height.
     */
    STRETCH,
}
