package com.stormpanda.megingiard.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SettingsManager.resetAllTutorials()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testWelcomeTutorialDefaultsAndUpdates() {
        // 1. Verify default values are true
        assertTrue(SettingsManager.showWelcomeTutorial.value)
        assertTrue(SettingsManager.showMacroEditorTutorial.value)
        assertTrue(SettingsManager.showPillTutorial.value)

        // 2. Set to false and verify updates
        SettingsManager.setShowWelcomeTutorial(false)
        SettingsManager.setShowMacroEditorTutorial(false)
        SettingsManager.setShowPillTutorial(false)
        assertFalse(SettingsManager.showWelcomeTutorial.value)
        assertFalse(SettingsManager.showMacroEditorTutorial.value)
        assertFalse(SettingsManager.showPillTutorial.value)

        // 3. Reset all tutorials and verify reset to true
        SettingsManager.resetAllTutorials()
        assertTrue(SettingsManager.showWelcomeTutorial.value)
        assertTrue(SettingsManager.showMacroEditorTutorial.value)
        assertTrue(SettingsManager.showPillTutorial.value)
    }
}
