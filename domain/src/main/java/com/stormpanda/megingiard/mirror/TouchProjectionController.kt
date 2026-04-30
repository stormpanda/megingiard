package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TouchProjectionCtrl"

/**
 * Gesture state machine for mirror touch projection.
 *
 * Manages the lifecycle of a single projected touch gesture:
 * DOWN → MOVE* → UP, including forced cancellation when a second
 * finger arrives (pinch takeover) and clamped UP when the finger
 * leaves the content area.
 *
 * All coordinates are in screen pixels relative to the gesture surface.
 * The controller calls [projectCoordinates] to convert to normalised
 * surface coordinates, then dispatches via [TouchInjector].
 *
 * Create a new instance for each pointer-input scope.
 */
class TouchProjectionController(
    private val edgeZonePx: Float,
    private val overlayAtBottom: Boolean,
) {
    private var gestureInEdgeZone = false
    private var gestureStarted = false
    private var activePointerId = -1L
    private var lastInjectedNx = 0f
    private var lastInjectedNy = 0f

    private val _indicatorPos = MutableStateFlow<Pair<Float, Float>?>(null)
    /** Screen-space position of the touch indicator dot, or null when hidden. */
    val indicatorPos: StateFlow<Pair<Float, Float>?> = _indicatorPos.asStateFlow()

    /**
     * Handle a Press event.
     *
     * @param pointerId     unique ID of the pointer that just went down
     * @param x             pointer X in gesture-box pixels
     * @param y             pointer Y in gesture-box pixels
     * @param boxW          gesture-box width in pixels
     * @param boxH          gesture-box height in pixels
     * @param isConsumed    true if a child (e.g. button) already consumed this pointer
     * @param pointerCount  total number of pressed pointers in this event
     * @return true if the event was handled and should be consumed
     */
    fun onPress(
        pointerId: Long,
        x: Float,
        y: Float,
        boxW: Float,
        boxH: Float,
        isConsumed: Boolean,
        pointerCount: Int,
    ): Boolean {
        // If a second finger lands while we have an active injection gesture,
        // gracefully cancel (pinch takeover).
        if (gestureStarted && pointerCount > 1) {
            _indicatorPos.value = null
            TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
            gestureStarted = false
            activePointerId = -1L
            return false
        }

        gestureStarted = false
        val nearEdge = if (overlayAtBottom) {
            y >= boxH - edgeZonePx
        } else {
            y <= edgeZonePx
        }
        gestureInEdgeZone = nearEdge
        if (nearEdge) return false
        if (isConsumed) return false

        val projected = projectCoordinates(
            x, y, boxW, boxH,
            ScreenCaptureManager.surfaceWidth.value,
            ScreenCaptureManager.surfaceHeight.value,
            ScreenCaptureManager.scale.value,
            ScreenCaptureManager.offsetX.value,
            ScreenCaptureManager.offsetY.value
        ) ?: return false

        _indicatorPos.value = Pair(x, y)
        lastInjectedNx = projected.first
        lastInjectedNy = projected.second
        TouchInjector.injectTouch(TouchAction.DOWN, lastInjectedNx, lastInjectedNy)
        activePointerId = pointerId
        gestureStarted = true
        return true
    }

    /**
     * Handle a Move event.
     *
     * @param pointerId   ID of the pointer that moved
     * @param x           pointer X in gesture-box pixels
     * @param y           pointer Y in gesture-box pixels
     * @param boxW        gesture-box width in pixels
     * @param boxH        gesture-box height in pixels
     * @param isConsumed  true if consumed by multi-finger transform (Block 3)
     * @return true if the event was handled and should be consumed
     */
    fun onMove(
        pointerId: Long,
        x: Float,
        y: Float,
        boxW: Float,
        boxH: Float,
        isConsumed: Boolean,
    ): Boolean {
        if (gestureInEdgeZone || !gestureStarted) return false
        if (pointerId != activePointerId) return false

        // If consumed by pinch-zoom (Block 3), send UP at last known position
        if (isConsumed) {
            _indicatorPos.value = null
            TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
            gestureStarted = false
            activePointerId = -1L
            return false
        }

        val sw = ScreenCaptureManager.surfaceWidth.value
        val sh = ScreenCaptureManager.surfaceHeight.value
        val sc = ScreenCaptureManager.scale.value
        val ox = ScreenCaptureManager.offsetX.value
        val oy = ScreenCaptureManager.offsetY.value

        val coords = projectCoordinates(x, y, boxW, boxH, sw, sh, sc, ox, oy)
        if (coords == null) {
            // Finger moved out of content area — send clamped UP
            _indicatorPos.value = null
            val svX = ((x - boxW / 2f - ox) / sc + sw / 2f).coerceIn(0f, sw)
            val svY = ((y - boxH / 2f - oy) / sc + sh / 2f).coerceIn(0f, sh)
            TouchInjector.injectTouch(TouchAction.UP, svX / sw, svY / sh)
            gestureStarted = false
            activePointerId = -1L
            return true
        }

        lastInjectedNx = coords.first
        lastInjectedNy = coords.second
        _indicatorPos.value = Pair(x, y)
        TouchInjector.injectTouch(TouchAction.MOVE, lastInjectedNx, lastInjectedNy)
        return true
    }

    /**
     * Handle a Release event.
     *
     * @param pointerId  ID of the pointer that released, or -1 if unknown
     * @param x          pointer X in gesture-box pixels (may be null if pointer left the list)
     * @param y          pointer Y in gesture-box pixels
     * @param boxW       gesture-box width in pixels
     * @param boxH       gesture-box height in pixels
     * @return true if the event was handled and should be consumed
     */
    fun onRelease(
        pointerId: Long,
        x: Float?,
        y: Float?,
        boxW: Float,
        boxH: Float,
    ): Boolean {
        _indicatorPos.value = null
        if (!gestureInEdgeZone && gestureStarted) {
            val sw = ScreenCaptureManager.surfaceWidth.value
            val sh = ScreenCaptureManager.surfaceHeight.value
            val sc = ScreenCaptureManager.scale.value
            val ox = ScreenCaptureManager.offsetX.value
            val oy = ScreenCaptureManager.offsetY.value

            if (x != null && y != null) {
                val coords = projectCoordinates(x, y, boxW, boxH, sw, sh, sc, ox, oy)
                val nx = coords?.first
                    ?: ((x - boxW / 2f - ox) / sc + sw / 2f).coerceIn(0f, sw) / sw
                val ny = coords?.second
                    ?: ((y - boxH / 2f - oy) / sc + sh / 2f).coerceIn(0f, sh) / sh
                TouchInjector.injectTouch(TouchAction.UP, nx, ny)
            } else {
                TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
            }
        }
        gestureInEdgeZone = false
        gestureStarted = false
        activePointerId = -1L
        return !gestureInEdgeZone
    }

    /** Reset all tracking state. */
    fun reset() {
        gestureInEdgeZone = false
        gestureStarted = false
        activePointerId = -1L
        _indicatorPos.value = null
    }
}
