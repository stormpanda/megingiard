package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog

private const val TAG = "CutoutPlacementHelper"

private const val CPH_BASE_WIDTH = 0.3f
private const val CPH_BASE_HEIGHT = 0.3f
private const val CPH_MIN_SCALE = 0.5f
private const val CPH_SCALE_STEP = 0.1f
private const val CPH_GRID_STEP = 0.05f
private const val CPH_EPSILON = 0.001f
private const val CPH_CANVAS_BOUND = 1.0f

/**
 * Represents the normalized destination rectangle found for placing a new screen cutout.
 */
data class CutoutSlot(
    val destX: Float,
    val destY: Float,
    val destWidth: Float,
    val destHeight: Float,
)

/**
 * Pure math helper that searches the normalized [0..1, 0..1] canvas for an available, non-overlapping
 * destination bounds slot for a new cutout.
 *
 * If the standard base size (0.3 x 0.3) cannot fit without colliding with existing cutouts, the candidate
 * dimensions are dynamically scaled down by up to 50% (down to 0.15 x 0.15).
 */
object CutoutPlacementHelper {
    /**
     * Finds an available non-overlapping slot on the secondary canvas.
     *
     * @param existingCutouts Current cutouts placed on the layout.
     * @param baseWidth Base width of the candidate cutout (default: 0.3).
     * @param baseHeight Base height of the candidate cutout (default: 0.3).
     * @param minScale Minimum scale factor to reduce candidate dimensions down to (default: 0.5 for up to 50% reduction).
     * @param scaleStep Step decrement for reducing candidate dimensions (default: 0.1).
     * @param gridStep Step increment when scanning across the normalized canvas (default: 0.05).
     * @return [CutoutSlot] with destination coordinates and size, or `null` if no non-overlapping space could be found.
     */
    fun findAvailableSlot(
        existingCutouts: List<ScreenCutout>,
        baseWidth: Float = CPH_BASE_WIDTH,
        baseHeight: Float = CPH_BASE_HEIGHT,
        minScale: Float = CPH_MIN_SCALE,
        scaleStep: Float = CPH_SCALE_STEP,
        gridStep: Float = CPH_GRID_STEP,
    ): CutoutSlot? {
        AppLog.d(TAG, "findAvailableSlot: existingCount=${existingCutouts.size}")

        var scale = 1.0f
        while (scale >= minScale - CPH_EPSILON) {
            val candidateW = baseWidth * scale
            val candidateH = baseHeight * scale

            val maxY = CPH_CANVAS_BOUND - candidateH + CPH_EPSILON
            val maxX = CPH_CANVAS_BOUND - candidateW + CPH_EPSILON

            var y = 0.0f
            while (y <= maxY) {
                var x = 0.0f
                while (x <= maxX) {
                    val collides =
                        existingCutouts.any { other ->
                            x < other.destX + other.destWidth && x + candidateW > other.destX &&
                                y < other.destY + other.destHeight && y + candidateH > other.destY
                        }
                    if (!collides) {
                        AppLog.d(TAG, "Slot found at scale=$scale: x=$x, y=$y, w=$candidateW, h=$candidateH")
                        return CutoutSlot(
                            destX = x,
                            destY = y,
                            destWidth = candidateW,
                            destHeight = candidateH,
                        )
                    }
                    x += gridStep
                }
                y += gridStep
            }
            scale -= scaleStep
        }

        AppLog.w(TAG, "No available slot found even at scale=$minScale")
        return null
    }
}
