package com.stormpanda.megingiard.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardSettingsTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempFile = File.createTempFile("keyboard_settings_test", ".preferences_pb")
        tempFile.deleteOnExit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFile.delete()
    }

    @Test
    fun testKeyboardTouchpadSettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
            val testDataStore =
                PreferenceDataStoreFactory.create(
                    produceFile = { tempFile },
                    scope = testScope,
                )

            KeyboardSettings.init(testDataStore, testScope)

            // 1. Verify default value is true
            assertTrue(KeyboardSettings.kbTouchpadEnabled.value)

            // 2. Set to false and verify it updates in flow
            KeyboardSettings.setKbTouchpadEnabled(false)
            testScheduler.advanceUntilIdle()
            assertFalse(KeyboardSettings.kbTouchpadEnabled.value)

            // 3. Verify it is persisted in the DataStore
            val prefs = testDataStore.data.first()
            assertTrue(prefs[KEY_KB_TOUCHPAD_ENABLED] == false)

            // 4. Set back to true and verify it is updated and persisted
            KeyboardSettings.setKbTouchpadEnabled(true)
            testScheduler.advanceUntilIdle()
            assertTrue(KeyboardSettings.kbTouchpadEnabled.value)

            val updatedPrefs = testDataStore.data.first()
            assertTrue(updatedPrefs[KEY_KB_TOUCHPAD_ENABLED] == true)
        }
}
