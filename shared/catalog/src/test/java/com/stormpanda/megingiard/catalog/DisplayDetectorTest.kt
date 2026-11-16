package com.stormpanda.megingiard.catalog

import android.view.Display
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.math.DisplayRotationDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

private const val TAG = "DisplayDetectorTest"
private const val PRIMARY_DISPLAY_ID = Display.DEFAULT_DISPLAY
private const val SECONDARY_DISPLAY_ID = 4
private const val TERTIARY_DISPLAY_ID = 5

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DisplayDetectorTest {
    @Test
    fun testFindSecondaryDisplay_withRobolectric() {
        AppLog.d(TAG, "Testing findSecondaryDisplay default Robolectric state")
        val context = RuntimeEnvironment.getApplication()
        val display = DisplayDetector.findSecondaryDisplay(context)
        // Default Robolectric display manager only has default display
        assertTrue(display == null || display.displayId != PRIMARY_DISPLAY_ID)
    }

    @Test
    fun testPrimaryAndSecondaryDisplaySizeAndAspectRatio_whenSecondaryAttached() {
        AppLog.d(TAG, "Testing secondary display resolution and aspect ratio")
        val context = RuntimeEnvironment.getApplication()
        val primary = DisplayDetector.findPrimaryDisplay(context)
        assertNotNull(primary)
        val primarySize = DisplayDetector.resolveDisplaySize(primary!!)
        assertNotNull(primarySize)
        assertTrue(primarySize!!.widthPx > 0 && primarySize.heightPx > 0)

        // Default Robolectric primary display is ROTATION_0 -> ROT_0
        assertEquals(DisplayRotationDegrees.ROT_0, DisplayDetector.resolvePrimaryDisplayRotation(context))
        // Fallback aspect ratio returned before secondary display is attached
        assertEquals(1.148f, DisplayDetector.resolveSecondaryAspectRatio(context, 1.148f), 0.001f)

        val addedDisplayId = ShadowDisplayManager.addDisplay("w1280dp-h960dp-mdpi")
        try {
            val secondary = DisplayDetector.findSecondaryDisplay(context)
            assertNotNull(secondary)
            assertEquals(addedDisplayId, secondary!!.displayId)

            val secSize = DisplayDetector.resolveSecondaryDisplaySize(context)
            assertNotNull(secSize)
            assertEquals(1280, secSize!!.widthPx)
            assertEquals(960, secSize.heightPx)
            assertEquals(4f / 3f, DisplayDetector.resolveSecondaryAspectRatio(context, 1.0f), 0.001f)
        } finally {
            ShadowDisplayManager.removeDisplay(addedDisplayId)
        }
    }

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

    @Test
    fun testUpdateDisplayValidity() {
        DisplayDetector.updateDisplayValidity(PRIMARY_DISPLAY_ID)
        DisplayDetector.updateDisplayValidity(SECONDARY_DISPLAY_ID)
    }
}
