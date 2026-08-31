package com.stormpanda.megingiard

private const val TAG = "SwipeGestureProcessor"

/**
 * Reusable edge-zone swipe detector for revealing the quick menu overlay.
 *
 * This processor is Compose-free: it accepts raw coordinates and dimensions.
 * It tracks the gesture drag delta, handles release-based triggers, and notifies
 * the caller about progress, cancellation, and haptic ticks.
 */
class SwipeGestureProcessor(
    private val edgeZonePx: Float,
    private val swipeThresholdPx: Float,
    private val overlayAtBottom: Boolean,
    private val quickMenuBarZoneWidthPx: Float? = null,
    private val onTouchingChanged: (Boolean) -> Unit = {},
    private val onEdgeSwipe: () -> Unit = AppStateManager::handleEdgeSwipe,
    private val customZoneCheck: ((pointerX: Float, containerWidth: Float) -> Boolean)? = null,
    private val onSwipeProgress: ((delta: Float, isPastThreshold: Boolean) -> Unit)? = null,
    private val onSwipeCancel: (() -> Unit)? = null,
    private val onHapticTick: (() -> Unit)? = null,
) {
    private var swipeStartY = Float.NaN
    private var swipeLastDelta = 0f
    private var hapticTriggered = false

    /** Call on every Press event with the first pointer's Y and the container height. */
    fun onPress(
        pointerY: Float,
        containerHeight: Float,
        pointerX: Float = 0f,
        containerWidth: Float = 0f,
    ) {
        onTouchingChanged(true)
        val inZoneX =
            if (customZoneCheck != null) {
                customZoneCheck(pointerX, containerWidth)
            } else if (quickMenuBarZoneWidthPx != null && containerWidth > 0f) {
                val center = containerWidth / 2f
                pointerX >= center - quickMenuBarZoneWidthPx / 2f && pointerX <= center + quickMenuBarZoneWidthPx / 2f
            } else {
                true
            }
        val nearEdge =
            (
                if (overlayAtBottom) {
                    pointerY >= containerHeight - edgeZonePx
                } else {
                    pointerY <= edgeZonePx
                }
            ) && inZoneX
        swipeStartY = if (nearEdge) pointerY else Float.NaN
        swipeLastDelta = 0f
        hapticTriggered = false
    }

    /** Call on every Move event with the first pointer's Y. */
    fun onMove(pointerY: Float) {
        if (!swipeStartY.isNaN()) {
            val delta =
                if (overlayAtBottom) {
                    swipeStartY - pointerY
                } else {
                    pointerY - swipeStartY
                }
            swipeLastDelta = delta.coerceAtLeast(0f)
            val isPast = swipeLastDelta >= swipeThresholdPx

            if (isPast && !hapticTriggered) {
                onHapticTick?.invoke()
                hapticTriggered = true
            } else if (!isPast && hapticTriggered) {
                hapticTriggered = false
            }

            onSwipeProgress?.invoke(swipeLastDelta, isPast)
        }
    }

    /** Call on Release when all pointers are up. */
    fun onRelease(allPointersUp: Boolean) {
        if (allPointersUp) {
            onTouchingChanged(false)
            if (!swipeStartY.isNaN()) {
                if (swipeLastDelta >= swipeThresholdPx) {
                    AppLog.d(TAG, "edge swipe released past threshold (delta=${swipeLastDelta.toInt()}px, bottom=$overlayAtBottom)")
                    onEdgeSwipe()
                } else {
                    onSwipeCancel?.invoke()
                }
            }
        }
        swipeStartY = Float.NaN
        swipeLastDelta = 0f
        hapticTriggered = false
    }

    /** Whether the current touch started in the edge zone. */
    val isNearEdge: Boolean get() = !swipeStartY.isNaN()

    /** Whether the edge-swipe gesture has been triggered or crossed the threshold. */
    val isSwipeTriggered: Boolean get() = swipeLastDelta >= swipeThresholdPx
}
