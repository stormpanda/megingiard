package com.stormpanda.megingiard.mirror

/**
 * Target display selection for screenshot capture actions.
 */
enum class ScreenshotTarget {
    /** Capture the primary / top display (Display 0). */
    TOP,

    /** Capture the secondary / bottom display. */
    BOTTOM,

    /** Capture both displays simultaneously and composite them vertically. */
    BOTH,
}
