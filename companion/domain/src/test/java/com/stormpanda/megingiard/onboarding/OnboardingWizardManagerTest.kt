package com.stormpanda.megingiard.onboarding

import com.stormpanda.megingiard.onboarding.OnboardingStepId
import com.stormpanda.megingiard.settings.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnboardingWizardManagerTest {
    @Before
    fun setUp() {
        SettingsManager.setWelcomeTourCompletedVersion(0)
        OnboardingWizardManager.resetWizardForTest()
    }

    @Test
    fun `orderedStepIds contains all onboarding enum values`() {
        assertEquals(OnboardingStepId.entries.size, OnboardingWizardManager.orderedStepIds.size)
        assertTrue(OnboardingWizardManager.orderedStepIds.containsAll(OnboardingStepId.entries))
    }

    @Test
    fun `shouldAutoStartWizard compares against CURRENT_WELCOME_TOUR_VERSION`() {
        assertTrue(OnboardingWizardManager.shouldAutoStartWizard())
        assertEquals(1, SettingsManager.CURRENT_WELCOME_TOUR_VERSION)

        SettingsManager.setWelcomeTourCompletedVersion(1)
        assertFalse(OnboardingWizardManager.shouldAutoStartWizard())
    }

    @Test
    fun `startWizard with force or auto`() {
        SettingsManager.setWelcomeTourCompletedVersion(1)
        OnboardingWizardManager.startWizard(force = false)
        assertFalse(OnboardingWizardManager.isWizardActive.value)

        OnboardingWizardManager.startWizard(force = true)
        assertTrue(OnboardingWizardManager.isWizardActive.value)
        assertEquals(0, OnboardingWizardManager.activeStepIndex.value)
    }

    @Test
    fun `finishWizard and skipWizard set isWizardActive to false`() {
        OnboardingWizardManager.startWizard(force = true)
        assertTrue(OnboardingWizardManager.isWizardActive.value)

        OnboardingWizardManager.finishWizard()
        assertFalse(OnboardingWizardManager.isWizardActive.value)
        assertEquals(SettingsManager.CURRENT_WELCOME_TOUR_VERSION, SettingsManager.welcomeTourCompletedVersion.value)

        OnboardingWizardManager.startWizard(force = true)
        OnboardingWizardManager.skipWizard()
        assertFalse(OnboardingWizardManager.isWizardActive.value)
    }

    @Test
    fun `backward navigation resets completion state of future steps`() {
        // Reset and initialize at step 0
        OnboardingWizardManager.startWizard(force = true)
        assertEquals(0, OnboardingWizardManager.activeStepIndex.value)

        // Advance to step 2 (index 2)
        OnboardingWizardManager.nextStep()
        OnboardingWizardManager.nextStep()
        assertEquals(2, OnboardingWizardManager.activeStepIndex.value)
        assertTrue(OnboardingWizardManager.steps.value[0].isCompleted)
        assertTrue(OnboardingWizardManager.steps.value[1].isCompleted)
        assertFalse(OnboardingWizardManager.steps.value[2].isCompleted)
        assertTrue(OnboardingWizardManager.steps.value[2].isCurrent)

        // Navigate BACK to step 1 (index 1)
        OnboardingWizardManager.prevStep()
        assertEquals(1, OnboardingWizardManager.activeStepIndex.value)
        assertTrue(OnboardingWizardManager.steps.value[0].isCompleted)
        assertFalse(OnboardingWizardManager.steps.value[1].isCompleted)
        assertTrue(OnboardingWizardManager.steps.value[1].isCurrent)
        // Step 2 must be reset to uncompleted (unreached)
        assertFalse(OnboardingWizardManager.steps.value[2].isCompleted)
        assertFalse(OnboardingWizardManager.steps.value[3].isCompleted)
    }
}
