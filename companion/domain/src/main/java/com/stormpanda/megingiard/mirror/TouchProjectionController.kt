package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TouchProjectionCtrl"
private const val MAX_TOUCH_SLOTS = 10

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
    private data class TouchState(
        val slot: Int,
        val cutoutId: String,
        var lastNx: Float,
        var lastNy: Float,
    )

    private val activeTouches = HashMap<Long, TouchState>()
    private val activeSlots = BooleanArray(MAX_TOUCH_SLOTS) { false }

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
        val nearEdge =
            if (overlayAtBottom) {
                y >= boxH - edgeZonePx
            } else {
                y <= edgeZonePx
            }
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

            val projected =
                projectCutoutCoordinates(
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
                    clampToEdge = false,
                )
            if (projected != null) {
                matchedProjected = projected
                matchedCutoutId = cutout.id
                break
            }
        }

        if (matchedProjected == null) return false

        // Allocate slot
        var slot = -1
        for (i in 0 until MAX_TOUCH_SLOTS) {
            if (!activeSlots[i]) {
                slot = i
                break
            }
        }
        if (slot == -1) return false

        activeSlots[slot] = true
        activeTouches[pointerId] =
            TouchState(
                slot = slot,
                cutoutId = matchedCutoutId!!,
                lastNx = matchedProjected.first,
                lastNy = matchedProjected.second,
            )

        if (activeTouches.size == 1) {
            _indicatorPos.value = Pair(x, y)
        }

        AppLog.d(TAG, "onPress slot=$slot cutoutId=$matchedCutoutId")
        TouchInjector.injectTouch(slot, TouchAction.DOWN, matchedProjected.first, matchedProjected.second)
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
        val touch = activeTouches[pointerId] ?: return false

        if (isConsumed) {
            activeTouches.remove(pointerId)
            activeSlots[touch.slot] = false
            if (activeTouches.isEmpty()) {
                _indicatorPos.value = null
            }
            TouchInjector.injectTouch(touch.slot, TouchAction.UP, touch.lastNx, touch.lastNy)
            return false
        }

        val cutout = ScreenCaptureManager.cutouts.value.firstOrNull { it.id == touch.cutoutId }
        if (cutout == null) {
            activeTouches.remove(pointerId)
            activeSlots[touch.slot] = false
            if (activeTouches.isEmpty()) {
                _indicatorPos.value = null
            }
            TouchInjector.injectTouch(touch.slot, TouchAction.UP, touch.lastNx, touch.lastNy)
            return false
        }

        val destLeft = cutout.destX * boxW
        val destTop = cutout.destY * boxH
        val destWidth = cutout.destWidth * boxW
        val destHeight = cutout.destHeight * boxH

        val coords =
            projectCutoutCoordinates(
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
                clampToEdge = false,
            )

        if (coords == null) {
            // Finger panned outside destination bounds — send clamped UP
            val clampedCoords =
                projectCutoutCoordinates(
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
                    clampToEdge = true,
                ) ?: Pair(touch.lastNx, touch.lastNy)

            activeTouches.remove(pointerId)
            activeSlots[touch.slot] = false
            if (activeTouches.isEmpty()) {
                _indicatorPos.value = null
            }
            TouchInjector.injectTouch(touch.slot, TouchAction.UP, clampedCoords.first, clampedCoords.second)
            return true
        }

        touch.lastNx = coords.first
        touch.lastNy = coords.second
        if (activeTouches.keys.firstOrNull() == pointerId) {
            _indicatorPos.value = Pair(x, y)
        }
        TouchInjector.injectTouch(touch.slot, TouchAction.MOVE, touch.lastNx, touch.lastNy)
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
        val touch = activeTouches.remove(pointerId) ?: return false
        activeSlots[touch.slot] = false
        if (activeTouches.isEmpty()) {
            _indicatorPos.value = null
        }

        val cutout = ScreenCaptureManager.cutouts.value.firstOrNull { it.id == touch.cutoutId }
        if (cutout != null && x != null && y != null) {
            val destLeft = cutout.destX * boxW
            val destTop = cutout.destY * boxH
            val destWidth = cutout.destWidth * boxW
            val destHeight = cutout.destHeight * boxH

            val coords =
                projectCutoutCoordinates(
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
                    clampToEdge = true,
                )
            val nx = coords?.first ?: touch.lastNx
            val ny = coords?.second ?: touch.lastNy
            TouchInjector.injectTouch(touch.slot, TouchAction.UP, nx, ny)
        } else {
            TouchInjector.injectTouch(touch.slot, TouchAction.UP, touch.lastNx, touch.lastNy)
        }
        return true
    }

    /** Reset all tracking state. */
    fun reset() {
        AppLog.d(TAG, "reset activeTouches=${activeTouches.size}")
        for ((_, touch) in activeTouches) {
            TouchInjector.injectTouch(touch.slot, TouchAction.UP, touch.lastNx, touch.lastNy)
        }
        activeTouches.clear()
        activeSlots.fill(false)
        _indicatorPos.value = null
    }
}
