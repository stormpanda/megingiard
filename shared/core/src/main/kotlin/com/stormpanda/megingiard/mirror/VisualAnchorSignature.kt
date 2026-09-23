package com.stormpanda.megingiard.mirror

import kotlinx.serialization.Serializable

/**
 * Represents a single reference anchor sample point extracted from a stationary reference element.
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
 * used for real-time visual anchor presence detection.
 *
 * @param cutoutId Unique identifier of the associated layout or cutout.
 * @param points Stratified sample of anchor points with near-zero calibration variance.
 */
@Serializable
data class VisualAnchorSignature(
    val cutoutId: String = "",
    val points: List<AnchorPoint> = emptyList(),
)

/**
 * Diagnostic evaluation result for a single reference anchor sample point.
 *
 * @param point The reference anchor sample point.
 * @param isMatch True if the observed color is within detection tolerance of expected reference color.
 * @param diff The absolute RGB difference sum (|ΔR| + |ΔG| + |ΔB|) observed.
 */
@Serializable
data class AnchorPointMatchResult(
    val point: AnchorPoint,
    val isMatch: Boolean,
    val diff: Int,
)
