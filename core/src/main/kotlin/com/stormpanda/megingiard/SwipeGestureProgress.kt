package com.stormpanda.megingiard

/**
 * Type of the active edge-swipe gesture.
 */
enum class SwipeGestureType {
    KEYBOARD,
    MENU,
    TOUCHPAD,
}

/**
 * Snapshot of the current active edge-swipe gesture progress.
 */
data class SwipeGestureProgress(
    val type: SwipeGestureType,
    val deltaPx: Float,
    val thresholdPx: Float,
    val isPastThreshold: Boolean,
)
