package com.stormpanda.megingiard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrimaryFocusAnchorActivityTest {
    @Before
    fun setUp() {
        PrimaryFocusAnchorActivity.resetDebounceForTesting()
        AppStateManager.setPrivdSetupWizardOpen(false)
    }

    @After
    fun tearDown() {
        PrimaryFocusAnchorActivity.resetDebounceForTesting()
        AppStateManager.setPrivdSetupWizardOpen(false)
    }

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

    @Test
    fun testAnchorPrimaryFocusThrottlesRapidSuccessiveCalls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)

        PrimaryFocusAnchorActivity.anchorPrimaryFocus(app)
        val firstIntent = shadowApp.nextStartedActivity
        assertTrue(firstIntent != null)

        // Immediate second call should be throttled
        PrimaryFocusAnchorActivity.anchorPrimaryFocus(app)
        val secondIntent = shadowApp.nextStartedActivity
        assertNull("Second immediate call should be debounced and not dispatch an intent", secondIntent)
    }

    @Test
    fun testAnchorPrimaryFocusDispatchesWhenForced() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)

        PrimaryFocusAnchorActivity.anchorPrimaryFocus(app)
        val firstIntent = shadowApp.nextStartedActivity
        assertTrue(firstIntent != null)

        // Forced second call should bypass debounce
        PrimaryFocusAnchorActivity.anchorPrimaryFocus(app, force = true)
        val secondIntent = shadowApp.nextStartedActivity
        assertTrue("Forced call should bypass debounce and dispatch an intent", secondIntent != null)
    }

    @Test
    fun testAnchorPrimaryFocusSuppressedWhenPrivdWizardActive() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)

        AppStateManager.setPrivdSetupWizardOpen(true)
        PrimaryFocusAnchorActivity.anchorPrimaryFocus(app)

        val startedIntent = shadowApp.nextStartedActivity
        assertNull("anchorPrimaryFocus must be suppressed while Privd wizard is open", startedIntent)
    }
}
