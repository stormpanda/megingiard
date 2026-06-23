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
    private var activeCutoutId: String? = null
    private var lastInjectedNx = 0f
    private var lastInjectedNy = 0f

    private val _indicatorPos = MutableStateFlow<Pair<Float, Float>?>(null)
    /** Screen-space position of the touch indicator dot, or null when hidden. */
    val indicatorPos: StateFlow<Pair<Float, Float>?> = _indicatorPos.asStateFlow()

    /**
     * Handle a Press event.
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
            activeCutoutId = null
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

        var matchedProjected: Pair<Float, Float>? = null
        var matchedCutoutId: String? = null

        val cutouts = ScreenCaptureManager.cutouts.value
        for (cutout in cutouts) {
            if (!cutout.touchProjectionEnabled) continue
            val destLeft = cutout.destX * boxW
            val destTop = cutout.destY * boxH
            val destWidth = cutout.destWidth * boxW
            val destHeight = cutout.destHeight * boxH

            val projected = projectCutoutCoordinates(
                touchX = x,
                touchY = y,
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destWidth,
                destHeight = destHeight,
                srcX = cutout.srcX,
                srcY = cutout.srcY,
                srcWidth = cutout.srcWidth,
                srcHeight = cutout.srcHeight,
                clampToEdge = false
            )
            if (projected != null) {
                matchedProjected = projected
                matchedCutoutId = cutout.id
                break
            }
        }

        if (matchedProjected == null) return false

        _indicatorPos.value = Pair(x, y)
        lastInjectedNx = matchedProjected.first
        lastInjectedNy = matchedProjected.second
        TouchInjector.injectTouch(TouchAction.DOWN, lastInjectedNx, lastInjectedNy)
        activePointerId = pointerId
        activeCutoutId = matchedCutoutId
        gestureStarted = true
        return true
    }

    /**
     * Handle a Move event.
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

        val cutoutId = activeCutoutId
        val cutout = if (cutoutId != null) {
            ScreenCaptureManager.cutouts.value.firstOrNull { it.id == cutoutId }
        } else null

        if (cutoutId == null || cutout == null) {
            _indicatorPos.value = null
            TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
            gestureStarted = false
            activePointerId = -1L
            activeCutoutId = null
            return false
        }

        val destLeft = cutout.destX * boxW
        val destTop = cutout.destY * boxH
        val destWidth = cutout.destWidth * boxW
        val destHeight = cutout.destHeight * boxH

        val coords = projectCutoutCoordinates(
            touchX = x,
            touchY = y,
            destLeft = destLeft,
            destTop = destTop,
            destWidth = destWidth,
            destHeight = destHeight,
            srcX = cutout.srcX,
            srcY = cutout.srcY,
            srcWidth = cutout.srcWidth,
            srcHeight = cutout.srcHeight,
            clampToEdge = false
        )

        if (coords == null) {
            // Finger panned outside destination bounds — send clamped UP
            _indicatorPos.value = null
            val clampedCoords = projectCutoutCoordinates(
                touchX = x,
                touchY = y,
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destWidth,
                destHeight = destHeight,
                srcX = cutout.srcX,
                srcY = cutout.srcY,
                srcWidth = cutout.srcWidth,
                srcHeight = cutout.srcHeight,
                clampToEdge = true
            ) ?: Pair(lastInjectedNx, lastInjectedNy)

            TouchInjector.injectTouch(TouchAction.UP, clampedCoords.first, clampedCoords.second)
            gestureStarted = false
            activePointerId = -1L
            activeCutoutId = null
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
            val cutoutId = activeCutoutId
            val cutout = if (cutoutId != null) {
                ScreenCaptureManager.cutouts.value.firstOrNull { it.id == cutoutId }
            } else null

            if (cutout != null && x != null && y != null) {
                val destLeft = cutout.destX * boxW
                val destTop = cutout.destY * boxH
                val destWidth = cutout.destWidth * boxW
                val destHeight = cutout.destHeight * boxH

                val coords = projectCutoutCoordinates(
                    touchX = x,
                    touchY = y,
                    destLeft = destLeft,
                    destTop = destTop,
                    destWidth = destWidth,
                    destHeight = destHeight,
                    srcX = cutout.srcX,
                    srcY = cutout.srcY,
                    srcWidth = cutout.srcWidth,
                    srcHeight = cutout.srcHeight,
                    clampToEdge = true
                )
                val nx = coords?.first ?: lastInjectedNx
                val ny = coords?.second ?: lastInjectedNy
                TouchInjector.injectTouch(TouchAction.UP, nx, ny)
            } else {
                TouchInjector.injectTouch(TouchAction.UP, lastInjectedNx, lastInjectedNy)
            }
        }
        gestureInEdgeZone = false
        gestureStarted = false
        activePointerId = -1L
        activeCutoutId = null
        return !gestureInEdgeZone
    }

    /** Reset all tracking state. */
    fun reset() {
        gestureInEdgeZone = false
        gestureStarted = false
        activePointerId = -1L
        activeCutoutId = null
        _indicatorPos.value = null
    }
}
