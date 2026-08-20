package com.stormpanda.megingiard.catalog

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "DisplayDetectorTest"
private const val PRIMARY_DISPLAY_ID = Display.DEFAULT_DISPLAY
private const val SECONDARY_DISPLAY_ID = 4
private const val TERTIARY_DISPLAY_ID = 5

class DisplayDetectorTest {
    @Test
    fun testShouldRetarget_onPrimaryDisplayWithSecondaryAvailable_returnsTrue() {
        val shouldRetarget =
            DisplayDetector.shouldRetarget(
                currentDisplayId = PRIMARY_DISPLAY_ID,
                secondaryDisplayId = SECONDARY_DISPLAY_ID,
                retargetAttempted = false,
            )
        assertTrue(shouldRetarget)
    }

    @Test
    fun testShouldRetarget_alreadyAttempted_returnsFalseToPreventLoop() {
        val shouldRetarget =
            DisplayDetector.shouldRetarget(
                currentDisplayId = PRIMARY_DISPLAY_ID,
                secondaryDisplayId = SECONDARY_DISPLAY_ID,
                retargetAttempted = true,
            )
        assertFalse(shouldRetarget)
    }

    @Test
    fun testShouldRetarget_alreadyOnSecondaryDisplay_returnsFalse() {
        val shouldRetarget =
            DisplayDetector.shouldRetarget(
                currentDisplayId = SECONDARY_DISPLAY_ID,
                secondaryDisplayId = SECONDARY_DISPLAY_ID,
                retargetAttempted = false,
            )
        assertFalse(shouldRetarget)
    }

    @Test
    fun testShouldRetarget_noSecondaryDisplay_returnsFalse() {
        val shouldRetarget =
            DisplayDetector.shouldRetarget(
                currentDisplayId = PRIMARY_DISPLAY_ID,
                secondaryDisplayId = null,
                retargetAttempted = false,
            )
        assertFalse(shouldRetarget)
    }

    @Test
    fun testIsValidScreen_primaryDisplay_returnsFalse() {
        assertFalse(DisplayDetector.isValidScreen(PRIMARY_DISPLAY_ID))
    }

    @Test
    fun testIsValidScreen_secondaryDisplay_returnsTrue() {
        assertTrue(DisplayDetector.isValidScreen(SECONDARY_DISPLAY_ID))
        assertTrue(DisplayDetector.isValidScreen(TERTIARY_DISPLAY_ID))
    }
}
