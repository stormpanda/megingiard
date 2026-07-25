package com.stormpanda.megingiard.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        // 1. Verify default values
        assertEquals(0, SettingsManager.welcomeTourCompletedVersion.value)
        assertTrue(SettingsManager.showMacroEditorTutorial.value)

        // 2. Set updates and verify
        SettingsManager.setWelcomeTourCompletedVersion(1)
        SettingsManager.setShowMacroEditorTutorial(false)
        assertEquals(1, SettingsManager.welcomeTourCompletedVersion.value)
        assertFalse(SettingsManager.showMacroEditorTutorial.value)

        // 3. Reset all tutorials and verify reset
        SettingsManager.resetAllTutorials()
        assertEquals(0, SettingsManager.welcomeTourCompletedVersion.value)
        assertTrue(SettingsManager.showMacroEditorTutorial.value)
    }
}
