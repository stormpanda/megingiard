package com.stormpanda.megingiard.mirror

/**
 * Normalized rectangular crop coordinates on the primary display [0.0..1.0].
 */
data class AnchorCrop(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
