package com.stormpanda.megingiard.mirror

import kotlinx.serialization.Serializable

/**
 * Target display selection for screenshot capture actions.
 */
@Serializable
enum class ScreenshotTarget {
    /** Capture the primary / top display (Display 0). */
    TOP,

    /** Capture the secondary / bottom display. */
    BOTTOM,

    /** Capture both displays simultaneously and composite them vertically. */
    BOTH,
}
