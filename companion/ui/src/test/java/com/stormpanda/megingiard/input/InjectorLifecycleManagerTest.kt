package com.stormpanda.megingiard.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectorLifecycleManagerTest {
    @Test
    fun testForegroundResumed_startsKeyMouseTouchInjectors() {
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                isActivityResumed = true,
                isPrivdSetupWizardActive = false,
            )
        assertTrue(states.startKeyboard)
        assertTrue(states.startMouse)
        assertTrue(states.startTouch)
    }

    @Test
    fun testPrivdSetupWizardActive_pausesKeyboardOnlyToAllowIme() {
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                isActivityResumed = true,
                isPrivdSetupWizardActive = true,
            )
        assertFalse(states.startKeyboard)
        assertTrue(states.startMouse)
        assertTrue(states.startTouch)
    }

    @Test
    fun testBackgrounded_stopsAllInjectors() {
        val states =
            InjectorLifecycleManager.calculateInjectorStates(
                isActivityResumed = false,
                isPrivdSetupWizardActive = false,
            )
        assertFalse(states.startKeyboard)
        assertFalse(states.startMouse)
        assertFalse(states.startTouch)
    }
}
