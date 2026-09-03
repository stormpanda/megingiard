package com.stormpanda.megingiard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrimaryFocusAnchorActivityTest {
    @Test
    fun testAnchorActivityFinishesImmediatelyInOnCreate() {
        val controller = Robolectric.buildActivity(PrimaryFocusAnchorActivity::class.java)
        controller.create()
        val activity = controller.get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun testAnchorPrimaryFocusDispatchesIntentToPrimaryDisplay() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)

        PrimaryFocusAnchorActivity.anchorPrimaryFocus(app)

        val startedIntent = shadowApp.nextStartedActivity
        assertTrue(startedIntent != null)
        assertEquals(PrimaryFocusAnchorActivity::class.java.name, startedIntent?.component?.className)
    }
}
