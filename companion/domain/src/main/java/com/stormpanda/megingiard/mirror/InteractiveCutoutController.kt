package com.stormpanda.megingiard.mirror

import android.os.SystemClock
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

private const val TAG = "InteractiveCutoutController"

const val DOUBLE_TAP_TIMEOUT_MS = 300L
const val DOUBLE_TAP_MAX_DISTANCE_PX = 48f
const val SNAP_BACK_DURATION_MS = 250L
const val BOUNCE_BACK_DURATION_MS = 200L
const val ANIMATION_FRAME_INTERVAL_MS = 16L
const val MIN_PINCH_DISTANCE_PX = 10f

data class PointerRecord(
    val id: Long,
    var x: Float,
    var y: Float,
)

data class TapRecord(
    val timeMs: Long,
    val x: Float,
    val y: Float,
)

object InteractiveCutoutController {
    private val _overrideCrops = MutableStateFlow<Map<String, NormalizedCrop>>(emptyMap())
    val overrideCrops: StateFlow<Map<String, NormalizedCrop>> = _overrideCrops.asStateFlow()

    private val trackedPointers = mutableMapOf<String, MutableList<PointerRecord>>()
    private val lastTapByCutout = mutableMapOf<String, TapRecord>()
    private val animationJobs = mutableMapOf<String, Job>()

    var onHapticFeedback: (() -> Unit)? = null
    var onCropUpdated: (() -> Unit)? = null
    internal var timeProvider: () -> Long = { SystemClock.elapsedRealtime() }

    internal var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        set(value) {
            cancelAllAnimations()
            field = value
        }

    fun getEffectiveCrop(cutout: ScreenCutout): NormalizedCrop = _overrideCrops.value[cutout.id] ?: NormalizedCrop.fromCutout(cutout)

    /**
     * Checks if a pointer touches an interactive cutout and registers the gesture.
     */
    fun onPress(
        pointerId: Long,
        xPx: Float,
        yPx: Float,
        boxW: Float,
        boxH: Float,
        cutouts: List<ScreenCutout>,
    ): Boolean {
        val target = findInteractiveCutoutAt(xPx, yPx, boxW, boxH, cutouts) ?: return false
        val cutoutId = target.id

        // Cancel any active animation on this cutout
        animationJobs[cutoutId]?.cancel()
        animationJobs.remove(cutoutId)

        val pointers = trackedPointers.getOrPut(cutoutId) { mutableListOf() }
        val now = timeProvider()

        // Double-tap check on first finger
        if (pointers.isEmpty()) {
            val lastTap = lastTapByCutout[cutoutId]
            if (lastTap != null && (now - lastTap.timeMs) <= DOUBLE_TAP_TIMEOUT_MS) {
                val dist = hypot(xPx - lastTap.x, yPx - lastTap.y)
                if (dist <= DOUBLE_TAP_MAX_DISTANCE_PX) {
                    AppLog.d(TAG, "Double-tap detected on cutout=$cutoutId -> resetting viewport")
                    lastTapByCutout.remove(cutoutId)
                    resetCutoutViewport(target)
                    return true
                }
            }
            lastTapByCutout[cutoutId] = TapRecord(timeMs = now, x = xPx, y = yPx)
        }

        // Register pointer
        pointers.removeAll { it.id == pointerId }
        pointers.add(PointerRecord(id = pointerId, x = xPx, y = yPx))
        AppLog.d(TAG, "onPress pointer=$pointerId on cutout=$cutoutId (active count=${pointers.size})")
        onCropUpdated?.invoke()
        return true
    }

    fun isCutoutActivelyInteracting(cutoutId: String): Boolean =
        (trackedPointers[cutoutId]?.isNotEmpty() == true) ||
            _overrideCrops.value.containsKey(cutoutId) ||
            animationJobs.containsKey(cutoutId)

    /**
     * Handles pointer movement (1-finger pan or 2-finger pinch-to-zoom).
     */
    fun onMove(
        pointerId: Long,
        xPx: Float,
        yPx: Float,
        boxW: Float,
        boxH: Float,
        cutouts: List<ScreenCutout>,
    ): Boolean {
        val cutoutId = findCutoutIdForPointer(pointerId) ?: return false
        val cutout = cutouts.find { it.id == cutoutId } ?: return false
        val pointers = trackedPointers[cutoutId] ?: return false

        val currentPointer = pointers.find { it.id == pointerId } ?: return false
        val prevX = currentPointer.x
        val prevY = currentPointer.y

        val destW = (cutout.destWidth * boxW).coerceAtLeast(1f)
        val destH = (cutout.destHeight * boxH).coerceAtLeast(1f)
        val destLeft = cutout.destX * boxW
        val destTop = cutout.destY * boxH

        val defaultCrop = NormalizedCrop.fromCutout(cutout)
        val currentCrop = getEffectiveCrop(cutout)

        if (pointers.size == 1) {
            // 1-finger Pan
            val deltaX = xPx - prevX
            val deltaY = yPx - prevY
            currentPointer.x = xPx
            currentPointer.y = yPx

            val deltaNormX = deltaX / destW
            val deltaNormY = deltaY / destH

            val newCrop = CutoutGestureMath.applyPan(currentCrop, deltaNormX, deltaNormY)
            AppLog.d(TAG, "onMove 1-finger cutout=$cutoutId delta=($deltaNormX, $deltaNormY) crop=$newCrop")
            updateOverrideCrop(cutoutId, newCrop)
            return true
        } else if (pointers.size >= 2) {
            // 2-finger Pinch & Pan
            val otherPointer = pointers.firstOrNull { it.id != pointerId } ?: return false
            val prevDist = hypot(prevX - otherPointer.x, prevY - otherPointer.y).coerceAtLeast(MIN_PINCH_DISTANCE_PX)
            val curDist = hypot(xPx - otherPointer.x, yPx - otherPointer.y).coerceAtLeast(MIN_PINCH_DISTANCE_PX)

            val prevMidX = (prevX + otherPointer.x) / 2f
            val prevMidY = (prevY + otherPointer.y) / 2f
            val curMidX = (xPx + otherPointer.x) / 2f
            val curMidY = (yPx + otherPointer.y) / 2f

            val midDeltaX = curMidX - prevMidX
            val midDeltaY = curMidY - prevMidY

            currentPointer.x = xPx
            currentPointer.y = yPx

            val scaleFactor = curDist / prevDist
            val deltaNormX = midDeltaX / destW
            val deltaNormY = midDeltaY / destH

            val focalNormX = ((curMidX - destLeft) / destW).coerceIn(0f, 1f)
            val focalNormY = ((curMidY - destTop) / destH).coerceIn(0f, 1f)

            val newCrop =
                CutoutGestureMath.applyPinchZoomAndPan(
                    current = currentCrop,
                    defaultCrop = defaultCrop,
                    scaleFactor = scaleFactor,
                    deltaNormX = deltaNormX,
                    deltaNormY = deltaNormY,
                    focalNormX = focalNormX,
                    focalNormY = focalNormY,
                )
            AppLog.d(
                TAG,
                "onMove 2-finger cutout=$cutoutId scale=$scaleFactor midDelta=($deltaNormX, $deltaNormY) crop=$newCrop",
            )
            updateOverrideCrop(cutoutId, newCrop)
            return true
        }

        return false
    }

    /**
     * Handles pointer release and triggers snap-back / bounce-back as configured.
     */
    fun onRelease(
        pointerId: Long,
        cutouts: List<ScreenCutout>,
    ): Boolean {
        val cutoutId = findCutoutIdForPointer(pointerId) ?: return false
        val cutout = cutouts.find { it.id == cutoutId }
        val pointers = trackedPointers[cutoutId] ?: return false

        pointers.removeAll { it.id == pointerId }
        AppLog.d(TAG, "onRelease pointer=$pointerId on cutout=$cutoutId (remaining=${pointers.size})")

        if (pointers.isEmpty()) {
            trackedPointers.remove(cutoutId)
            if (cutout != null) {
                handleGestureEnded(cutout)
            }
        }
        return true
    }

    private fun handleGestureEnded(cutout: ScreenCutout) {
        val currentCrop = getEffectiveCrop(cutout)
        val defaultCrop = NormalizedCrop.fromCutout(cutout)

        when (cutout.snapBackMode) {
            CutoutSnapBackMode.INSTANT -> {
                AppLog.d(TAG, "Instant snap-back triggered for cutout=${cutout.id}")
                animateCropTo(
                    cutoutId = cutout.id,
                    startCrop = currentCrop,
                    targetCrop = defaultCrop,
                    durationMs = SNAP_BACK_DURATION_MS,
                    onFinished = {
                        removeOverrideCrop(cutout.id)
                        onHapticFeedback?.invoke()
                    },
                )
            }

            CutoutSnapBackMode.OFF -> {
                val clamped = CutoutGestureMath.clampToScreenBounds(currentCrop)
                if (!CutoutGestureMath.isCloseTo(currentCrop, clamped)) {
                    AppLog.d(TAG, "Elastic bounce-back triggered for cutout=${cutout.id}")
                    animateCropTo(
                        cutoutId = cutout.id,
                        startCrop = currentCrop,
                        targetCrop = clamped,
                        durationMs = BOUNCE_BACK_DURATION_MS,
                        onFinished = {
                            updateOverrideCrop(cutout.id, clamped)
                            onHapticFeedback?.invoke()
                        },
                    )
                }
            }
        }
    }

    /**
     * Resets a cutout's viewport back to default with smooth animation.
     */
    fun resetCutoutViewport(cutout: ScreenCutout) {
        val currentCrop = getEffectiveCrop(cutout)
        val defaultCrop = NormalizedCrop.fromCutout(cutout)
        if (CutoutGestureMath.isCloseTo(currentCrop, defaultCrop)) {
            removeOverrideCrop(cutout.id)
            return
        }

        animateCropTo(
            cutoutId = cutout.id,
            startCrop = currentCrop,
            targetCrop = defaultCrop,
            durationMs = SNAP_BACK_DURATION_MS,
            onFinished = {
                removeOverrideCrop(cutout.id)
                onHapticFeedback?.invoke()
            },
        )
    }

    /**
     * Called when Follow Touch detects a top-screen touch, overriding manual crop.
     */
    fun onFollowTouchReceived(targetCutoutId: String) {
        if (_overrideCrops.value.containsKey(targetCutoutId) || animationJobs.containsKey(targetCutoutId)) {
            AppLog.d(TAG, "Follow touch received -> overriding manual pan for cutout=$targetCutoutId")
            animationJobs[targetCutoutId]?.cancel()
            animationJobs.remove(targetCutoutId)
            trackedPointers.remove(targetCutoutId)
            removeOverrideCrop(targetCutoutId)
        }
    }

    fun isPointerTracked(pointerId: Long): Boolean = findCutoutIdForPointer(pointerId) != null

    fun cancelAllAnimations() {
        animationJobs.values.forEach { it.cancel() }
        animationJobs.clear()
        trackedPointers.clear()
    }

    fun reset() {
        cancelAllAnimations()
        _overrideCrops.value = emptyMap()
        lastTapByCutout.clear()
        timeProvider = { SystemClock.elapsedRealtime() }
    }

    private fun updateOverrideCrop(
        cutoutId: String,
        crop: NormalizedCrop,
    ) {
        val next = _overrideCrops.value.toMutableMap()
        next[cutoutId] = crop
        _overrideCrops.value = next
        onCropUpdated?.invoke()
    }

    private fun removeOverrideCrop(cutoutId: String) {
        val next = _overrideCrops.value.toMutableMap()
        next.remove(cutoutId)
        _overrideCrops.value = next
        onCropUpdated?.invoke()
    }

    private fun findInteractiveCutoutAt(
        x: Float,
        y: Float,
        boxW: Float,
        boxH: Float,
        cutouts: List<ScreenCutout>,
    ): ScreenCutout? {
        return cutouts.lastOrNull { cutout ->
            if (!cutout.interactivePanZoom || cutout.touchProjectionEnabled) return@lastOrNull false
            val left = cutout.destX * boxW
            val top = cutout.destY * boxH
            val right = (cutout.destX + cutout.destWidth) * boxW
            val bottom = (cutout.destY + cutout.destHeight) * boxH
            x in left..right && y in top..bottom
        }
    }

    private fun findCutoutIdForPointer(pointerId: Long): String? =
        trackedPointers.entries
            .firstOrNull { (_, list) ->
                list.any {
                    it.id == pointerId
                }
            }?.key

    private fun animateCropTo(
        cutoutId: String,
        startCrop: NormalizedCrop,
        targetCrop: NormalizedCrop,
        durationMs: Long,
        onFinished: () -> Unit,
    ) {
        animationJobs[cutoutId]?.cancel()
        val job =
            scope.launch {
                val startTime = timeProvider()
                var accumulatedElapsed = 0L
                while (isActive) {
                    val realElapsed = timeProvider() - startTime
                    accumulatedElapsed = max(accumulatedElapsed, realElapsed)
                    val rawFraction = (accumulatedElapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    // Smooth Hermite interpolation (smoothstep)
                    val smoothFraction = rawFraction * rawFraction * (3f - 2f * rawFraction)
                    val interpolated = CutoutGestureMath.lerp(startCrop, targetCrop, smoothFraction)
                    updateOverrideCrop(cutoutId, interpolated)

                    if (rawFraction >= 1f) {
                        break
                    }
                    delay(ANIMATION_FRAME_INTERVAL_MS)
                    accumulatedElapsed += ANIMATION_FRAME_INTERVAL_MS
                }
                onFinished()
                animationJobs.remove(cutoutId)
            }
        animationJobs[cutoutId] = job
    }
}
