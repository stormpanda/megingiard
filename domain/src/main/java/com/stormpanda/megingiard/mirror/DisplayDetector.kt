package com.stormpanda.megingiard.mirror

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display // hardware abstraction, not a UI component — accepted :domain exception
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager

@Suppress("unused")
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
        val secondary = displayManager.getDisplays()
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
        val isValid = displayId != Display.DEFAULT_DISPLAY
        AppLog.i(TAG, "updateDisplayValidity: displayId=$displayId isValid=$isValid")
        AppStateManager.setOnValidScreen(isValid)
    }
}
