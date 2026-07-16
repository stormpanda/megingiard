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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TouchpadSettingsTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempFile = File.createTempFile("touchpad_settings_test", ".preferences_pb")
        tempFile.deleteOnExit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFile.delete()
    }

    @Test
    fun testMouse45SettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
            val testDataStore =
                PreferenceDataStoreFactory.create(
                    produceFile = { tempFile },
                    scope = testScope,
                )

            TouchpadSettings.init(testDataStore, testScope)

            // 1. Verify default value is false
            assertFalse(TouchpadSettings.touchpadMouse45Enabled.value)

            // 2. Set to true and verify it updates in flow
            TouchpadSettings.setTouchpadMouse45Enabled(true)
            testScheduler.advanceUntilIdle()
            assertTrue(TouchpadSettings.touchpadMouse45Enabled.value)

            // 3. Verify it is persisted in the DataStore
            val prefs = testDataStore.data.first()
            assertTrue(prefs[KEY_TOUCHPAD_MOUSE_4_5_ENABLED] == true)

            // 4. Set back to false
            TouchpadSettings.setTouchpadMouse45Enabled(false)
            testScheduler.advanceUntilIdle()
            assertFalse(TouchpadSettings.touchpadMouse45Enabled.value)
        }

    @Test
    fun testSensitivitySettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
            val testDataStore =
                PreferenceDataStoreFactory.create(
                    produceFile = { tempFile },
                    scope = testScope,
                )

            TouchpadSettings.init(testDataStore, testScope)

            // 1. Verify default value is 1.0f
            assertEquals(1.0f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)

            // 2. Set to 1.5f and verify it updates in flow
            TouchpadSettings.setTouchpadSensitivity(1.5f)
            testScheduler.advanceUntilIdle()
            assertEquals(1.5f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)

            // 3. Verify it is persisted in the DataStore
            val prefs = testDataStore.data.first()
            assertEquals(1.5f, prefs[KEY_TOUCHPAD_SENSITIVITY] ?: 1.0f, 1e-5f)

            // 4. Out-of-bounds lower clamping
            TouchpadSettings.setTouchpadSensitivity(0.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(0.1f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)

            // 5. Out-of-bounds upper clamping
            TouchpadSettings.setTouchpadSensitivity(4.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(3.0f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)
        }
}
