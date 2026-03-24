package com.stormpanda.megingiard.touchpad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AccessibilityService that forwards touch input from the bottom screen to the
 * primary (top) display via [dispatchGesture].
 *
 * ### Why the naive continueStroke approach breaks
 * The system only accepts a continuation **after** the previous segment has
 * finished playing (i.e. after [GestureResultCallback.onCompleted] fires).
 * At 60 fps a new Move event arrives every ~16 ms, but a segment takes at least
 * [STROKE_DURATION_MS] to play.  Every call to dispatchGesture while a gesture
 * is already in-flight returns `false` and is silently dropped — which is why
 * single taps (one DOWN + one UP, no overlap) work fine but continuous drags do
 * not.
 *
 * ### Solution: callback-gated queue
 * Move-point coordinates are pushed into [pendingPoints].  The callback-loop
 * works as follows:
 *  1. DOWN  – dispatch the first stroke with `willContinue = true`; mark
 *             [gestureInFlight] = true.
 *  2. onCompleted fires → flush all queued points as a single multi-segment
 *             Path in the next continuation stroke; keep `willContinue = true`
 *             as long as the finger is still down.
 *  3. UP    – set [fingerUp] = true.  The next onCompleted will dispatch a
 *             final stroke with `willContinue = false`, releasing the pointer.
 *
 * Accumulating queued points into one Path per callback cycle keeps latency
 * low (≤ one segment duration behind) while never dispatching faster than the
 * system can consume.
 */
class MegingiardAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MegingiardAccessibilityService? = null

        /** Duration of each dispatched stroke segment in milliseconds. */
        private const val STROKE_DURATION_MS = 16L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- mutable state (only touched on mainHandler thread) ----

    /** Stroke description of the most recently dispatched segment. */
    private var activeStroke: GestureDescription.StrokeDescription? = null

    /**
     * Points accumulated since the last dispatch.  Each entry is the latest
     * known finger position; the path drawn between them forms the next segment.
     */
    private val pendingPoints = mutableListOf<PointF>()

    /** True while a dispatchGesture call has not yet called back. */
    private val gestureInFlight = AtomicBoolean(false)

    /** Set to true when the finger lifts; processed on the next callback. */
    private var fingerUp = false

    /** Last dispatched position – used as the start of the next Path. */
    private var lastX = 0f
    private var lastY = 0f

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        TouchpadManager.setAccessibilityEnabled(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        TouchpadManager.setAccessibilityEnabled(false)
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Public API (called from TouchpadManager, any thread)
    // -------------------------------------------------------------------------

    fun handleTouch(action: TouchAction, x: Float, y: Float) {
        mainHandler.post {
            when (action) {
                TouchAction.DOWN -> onDown(x, y)
                TouchAction.MOVE -> onMove(x, y)
                TouchAction.UP   -> onUp(x, y)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal – all methods below run exclusively on mainHandler
    // -------------------------------------------------------------------------

    private fun onDown(x: Float, y: Float) {
        // Reset state for a fresh gesture
        activeStroke = null
        pendingPoints.clear()
        fingerUp = false
        gestureInFlight.set(false)
        lastX = x
        lastY = y

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(
            path,
            /* startTime = */ 0L,
            STROKE_DURATION_MS,
            /* willContinue = */ true
        )
        activeStroke = stroke
        gestureInFlight.set(true)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), gestureCallback, mainHandler)
    }

    private fun onMove(x: Float, y: Float) {
        pendingPoints.add(PointF(x, y))
        // If no gesture is currently in flight (e.g. the callback already fired
        // before this Move arrived), dispatch immediately.
        if (!gestureInFlight.get()) {
            flushPending(willContinue = true)
        }
    }

    private fun onUp(x: Float, y: Float) {
        pendingPoints.add(PointF(x, y))
        fingerUp = true
        if (!gestureInFlight.get()) {
            flushPending(willContinue = false)
        }
    }

    /**
     * Builds a Path from [lastX]/[lastY] through all [pendingPoints] and
     * dispatches it as the next continuation stroke.
     */
    private fun flushPending(willContinue: Boolean) {
        val prev = activeStroke ?: return
        if (pendingPoints.isEmpty()) {
            if (!willContinue) {
                // Finger lifted with no queued points – close with a stationary segment
                val path = Path().apply { moveTo(lastX, lastY) }
                dispatchContinuation(prev, path, willContinue = false)
            }
            return
        }

        val path = Path().apply {
            moveTo(lastX, lastY)
            pendingPoints.forEach { p -> lineTo(p.x, p.y) }
        }
        lastX = pendingPoints.last().x
        lastY = pendingPoints.last().y
        pendingPoints.clear()

        dispatchContinuation(prev, path, willContinue)
    }

    private fun dispatchContinuation(
        prev: GestureDescription.StrokeDescription,
        path: Path,
        willContinue: Boolean
    ) {
        val continuation = prev.continueStroke(path, 0L, STROKE_DURATION_MS, willContinue)
        activeStroke = if (willContinue) continuation else null
        gestureInFlight.set(true)
        dispatchGesture(
            GestureDescription.Builder().addStroke(continuation).build(),
            if (willContinue) gestureCallback else null,
            mainHandler
        )
    }

    /** Fires on the main thread after each segment completes. */
    private val gestureCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            gestureInFlight.set(false)
            when {
                // Finger already up — send the final closing segment
                fingerUp -> flushPending(willContinue = false)
                // Points waiting — consume them as the next continuation
                pendingPoints.isNotEmpty() -> flushPending(willContinue = true)
                // Finger still down but no new points yet — stay idle until
                // the next onMove/onUp call which will call flushPending itself
                else -> Unit
            }
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            gestureInFlight.set(false)
            activeStroke = null
            pendingPoints.clear()
            fingerUp = false
        }
    }
}
