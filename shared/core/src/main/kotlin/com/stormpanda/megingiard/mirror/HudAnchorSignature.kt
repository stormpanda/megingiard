package com.stormpanda.megingiard.mirror

import kotlinx.serialization.Serializable

/**
 * Represents a single reference anchor sample point extracted from a stationary HUD element.
 *
 * @param u Normalized horizontal position [0.0, 1.0] within the cutout's source crop rectangle.
 * @param v Normalized vertical position [0.0, 1.0] within the cutout's source crop rectangle.
 * @param r Expected reference Red color channel value [0, 255].
 * @param g Expected reference Green color channel value [0, 255].
 * @param b Expected reference Blue color channel value [0, 255].
 */
@Serializable
data class AnchorPoint(
    val u: Float,
    val v: Float,
    val r: Int,
    val g: Int,
    val b: Int,
)

/**
 * Compact reference signature containing spatially distributed anchor sample points
 * used for real-time HUD presence detection (identifying cutscenes, menus, or loading screens).
 *
 * @param cutoutId Unique identifier of the associated [ScreenCutout].
 * @param points Stratified sample of anchor points with near-zero calibration variance.
 */
@Serializable
data class HudAnchorSignature(
    val cutoutId: String,
    val points: List<AnchorPoint>,
)
