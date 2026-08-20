package com.stormpanda.megingiard.catalog

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display // hardware abstraction, not a UI component — accepted :domain exception
import com.stormpanda.megingiard.AppLog

private const val TAG = "DisplayDetector"

/**
 * Utility for multi-display detection on the AYN Thor.
 *
 * Encapsulates the logic for finding the secondary display and
 * validating whether the app is running on the correct screen.
 */
object DisplayDetector {
    /**
     * Find the first non-default (secondary) display.
     *
     * @return the secondary [Display], or null if only the default display exists.
     */
    fun findSecondaryDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondary =
            displayManager
                .getDisplays()
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (secondary == null) {
            AppLog.e(TAG, "No secondary display found!")
        } else {
            AppLog.i(TAG, "Secondary display found: id=${secondary.displayId} name=${secondary.name}")
        }
        return secondary
    }

    /**
     * Check and update whether the current display is valid (non-default).
     *
     * Call from `Activity.onConfigurationChanged()` and during initial setup.
     *
     * @param displayId  the current display ID (from `display?.displayId`)
     */
    fun updateDisplayValidity(displayId: Int) {
        val isValid = isValidScreen(displayId)
        AppLog.i(TAG, "updateDisplayValidity: displayId=$displayId isValid=$isValid")
    }

    /**
     * Determines whether the companion activity should attempt to retarget itself
     * to the secondary display.
     *
     * @param currentDisplayId the display ID where the Activity is currently executing
     * @param secondaryDisplayId the display ID of the detected secondary display, or null if none
     * @param retargetAttempted whether a retarget has already been attempted for this launch/intent
     * @return true if the Activity is on the default display, a secondary display exists, and no retarget was attempted
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
     *
     * @param currentDisplayId the display ID where the Activity or composable is running
     * @return true if running on a non-default (secondary) display
     */
    fun isValidScreen(currentDisplayId: Int): Boolean = currentDisplayId != Display.DEFAULT_DISPLAY
}
