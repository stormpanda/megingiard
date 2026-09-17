package com.stormpanda.megingiard.macropad

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Extensible behavior/effect applied to screen mirroring cutouts when a layout's visual reference anchor is lost.
 */
@Serializable
enum class CutoutLostAnchorEffect {
    /**
     * Retains the last valid delayed frame from the ring buffer, freezing the cutout image.
     */
    @SerialName("freeze")
    FREEZE,

    /**
     * Applies a frosted-glass blur over the inactive cutout.
     */
    @SerialName("blur")
    BLUR,
}

val DEFAULT_LOST_ANCHOR_EFFECTS: Set<CutoutLostAnchorEffect> =
    setOf(CutoutLostAnchorEffect.FREEZE, CutoutLostAnchorEffect.BLUR)
