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

    fun distanceSquaredTo(
        x: Float,
        y: Float,
    ): Float {
        val dx =
            when {
                x < left -> left - x
                x > right -> x - right
                else -> 0f
            }
        val dy =
            when {
                y < top -> top - y
                y > bottom -> y - bottom
                else -> 0f
            }
        return dx * dx + dy * dy
    }
}
