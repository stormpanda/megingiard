package com.stormpanda.megingiard

private const val TAG = "SwipeGestureProcessor"

/**
 * Reusable edge-zone swipe detector for revealing the quick menu overlay.
 *
 * The same swipe logic is used in [MainAppScreen] and [MirrorScreen].
 * This processor is Compose-free: it accepts raw coordinates and dimensions
 * and calls back into [AppStateManager] when a swipe is detected.
 *
 * Create one instance per pointer-input scope; it tracks only the current
 * gesture and does not survive scope restarts (which is intentional — the
 * Compose `pointerInput` key list already causes a restart whenever
 * `overlayAtBottom` changes).
 */
class SwipeGestureProcessor(
    private val edgeZonePx: Float,
    private val swipeThresholdPx: Float,
    private val overlayAtBottom: Boolean,
    private val quickMenuBarZoneWidthPx: Float? = null,
    private val onTouchingChanged: (Boolean) -> Unit = { AppStateManager.setTouching(it) },
    private val onEdgeSwipe: () -> Unit = { AppStateManager.handleEdgeSwipe() },
    private val customZoneCheck: ((pointerX: Float, containerWidth: Float) -> Boolean)? = null,
) {
    private var swipeStartY = Float.NaN
    private var swipeTriggered = false

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
        swipeTriggered = false
    }

    /** Call on every Move event with the first pointer's Y. */
    fun onMove(pointerY: Float) {
        if (!swipeStartY.isNaN() && !swipeTriggered) {
            val delta =
                if (overlayAtBottom) {
                    swipeStartY - pointerY
                } else {
                    pointerY - swipeStartY
                }
            if (delta >= swipeThresholdPx) {
                AppLog.d(TAG, "edge swipe detected (delta=${delta.toInt()}px, bottom=$overlayAtBottom)")
                onEdgeSwipe()
                swipeTriggered = true
            }
        }
    }

    /** Call on Release when all pointers are up. */
    fun onRelease(allPointersUp: Boolean) {
        if (allPointersUp) {
            onTouchingChanged(false)
        }
        swipeStartY = Float.NaN
        swipeTriggered = false
    }

    /** Whether the current touch started in the edge zone. */
    val isNearEdge: Boolean get() = !swipeStartY.isNaN()

    /** Whether the edge-swipe gesture has been triggered during the current touch sequence. */
    val isSwipeTriggered: Boolean get() = swipeTriggered
}
