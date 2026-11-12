package com.stormpanda.megingiard.catalog

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display // hardware abstraction, not a UI component — accepted :domain exception
import android.view.Surface
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.DisplayRotationDegrees

private const val TAG = "DisplayDetector"

data class DisplayPixelSize(
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Utility for multi-display discovery and live display geometry queries on dual-screen handhelds.
 *
 * Encapsulates finding the secondary display, resolving logical rotated pixel dimensions and
 * aspect ratios without device-specific hardcoding, and validating secondary-screen execution.
 */
object DisplayDetector {
    /**
     * Find the first non-default (secondary) display.
     *
     * @return the secondary [Display], or null if only the default display exists.
     */
    fun findSecondaryDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return null
        val secondary =
            displayManager
                .getDisplays()
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (secondary == null) {
            AppLog.w(TAG, "No secondary display found")
        } else {
            AppLog.d(TAG, "Secondary display found: id=${secondary.displayId} name=${secondary.name}")
        }
        return secondary
    }

    /**
     * Find the primary default display (`Display.DEFAULT_DISPLAY`).
     */
    fun findPrimaryDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return null
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }

    /**
     * Resolves the current rotation of the primary display (`Display.DEFAULT_DISPLAY`)
     * mapped to [DisplayRotationDegrees].
     */
    fun resolvePrimaryDisplayRotation(context: Context): DisplayRotationDegrees {
        val primary = findPrimaryDisplay(context) ?: return DisplayRotationDegrees.ROT_90
        return DisplayRotationDegrees.fromSurfaceRotation(primary.rotation)
    }

    /**
     * Resolves the logical (rotation-adjusted) full pixel dimensions of [display], or `null`
     * if valid positive dimensions cannot be determined.
     */
    fun resolveDisplaySize(display: Display): DisplayPixelSize? {
        val mode = display.mode
        if (mode != null && mode.physicalWidth > 0 && mode.physicalHeight > 0) {
            val swapAxes =
                display.rotation == Surface.ROTATION_90 ||
                    display.rotation == Surface.ROTATION_270
            val w = if (swapAxes) mode.physicalHeight else mode.physicalWidth
            val h = if (swapAxes) mode.physicalWidth else mode.physicalHeight
            return DisplayPixelSize(widthPx = w, heightPx = h)
        }
        val point = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(point)
        return if (point.x > 0 && point.y > 0) {
            DisplayPixelSize(widthPx = point.x, heightPx = point.y)
        } else {
            null
        }
    }

    /**
     * Resolves the logical (rotation-adjusted) pixel dimensions of the secondary display,
     * or `null` if no secondary display is attached.
     */
    fun resolveSecondaryDisplaySize(context: Context): DisplayPixelSize? {
        val secondary = findSecondaryDisplay(context) ?: return null
        val resolved = resolveDisplaySize(secondary)
        AppLog.d(TAG, "resolveSecondaryDisplaySize -> $resolved")
        return resolved
    }

    /**
     * Resolves the width-to-height aspect ratio of the secondary display, or [fallbackAspectRatio]
     * if no secondary display is currently attached.
     */
    fun resolveSecondaryAspectRatio(
        context: Context,
        fallbackAspectRatio: Float,
    ): Float {
        val size = resolveSecondaryDisplaySize(context) ?: return fallbackAspectRatio
        return if (size.heightPx > 0) {
            size.widthPx.toFloat() / size.heightPx.toFloat()
        } else {
            fallbackAspectRatio
        }
    }

    /**
     * Check and update whether the current display is valid (non-default).
     *
     * Call from `Activity.onConfigurationChanged()` and during initial setup.
     *
     * @param displayId the current display ID (from `display?.displayId`)
     */
    fun updateDisplayValidity(displayId: Int) {
        val isValid = isValidScreen(displayId)
        AppLog.i(TAG, "updateDisplayValidity: displayId=$displayId isValid=$isValid")
    }

    /**
     * Determines whether the companion activity should attempt to retarget itself
     * to the secondary display.
     */
    fun shouldRetarget(
        currentDisplayId: Int,
        secondaryDisplayId: Int?,
        retargetAttempted: Boolean,
    ): Boolean =
        secondaryDisplayId != null &&
            currentDisplayId == Display.DEFAULT_DISPLAY &&
            !retargetAttempted

    /**
     * Checks whether the given display ID is valid for the companion interface (must not be the default display).
     */
    fun isValidScreen(currentDisplayId: Int): Boolean = currentDisplayId != Display.DEFAULT_DISPLAY
}
