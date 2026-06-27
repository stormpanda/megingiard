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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testWelcomeTutorialDefaultsAndUpdates() {
        // 1. Verify default value is true
        assertTrue(SettingsManager.showWelcomeTutorial.value)

        // 2. Set to false and verify update
        SettingsManager.setShowWelcomeTutorial(false)
        assertFalse(SettingsManager.showWelcomeTutorial.value)

        // 3. Reset all tutorials and verify reset to true
        SettingsManager.resetAllTutorials()
        assertTrue(SettingsManager.showWelcomeTutorial.value)
    }
}
