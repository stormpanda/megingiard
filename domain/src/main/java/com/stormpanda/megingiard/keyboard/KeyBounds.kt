package com.stormpanda.megingiard.keyboard

data class KeyBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x in left..right && y in top..bottom
}
