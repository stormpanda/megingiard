package com.stormpanda.megingiard.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private fun TestScope.initSettings(): DataStore<Preferences> {
        val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile }, scope = testScope)
        TouchpadSettings.init(testDataStore, testScope)
        return testDataStore
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        TouchpadSettings.loadFrom(
            androidx.datastore.preferences.core
                .emptyPreferences(),
        )
        tempFile = File.createTempFile("touchpad_settings_test", ".preferences_pb").apply { deleteOnExit() }
    }

    @After
    fun tearDown() {
        TouchpadSettings.loadFrom(
            androidx.datastore.preferences.core
                .emptyPreferences(),
        )
        Dispatchers.resetMain()
        tempFile.delete()
    }

    @Test
    fun testMouse45SettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testDataStore = initSettings()

            assertFalse(TouchpadSettings.touchpadMouse45Enabled.value)

            TouchpadSettings.setTouchpadMouse45Enabled(true)
            testScheduler.advanceUntilIdle()
            assertTrue(TouchpadSettings.touchpadMouse45Enabled.value)

            val prefs = testDataStore.data.first()
            assertTrue(prefs[KEY_TOUCHPAD_MOUSE_4_5_ENABLED] == true)

            TouchpadSettings.setTouchpadMouse45Enabled(false)
            testScheduler.advanceUntilIdle()
            assertFalse(TouchpadSettings.touchpadMouse45Enabled.value)
        }

    @Test
    fun testSensitivitySettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testDataStore = initSettings()

            assertEquals(1.0f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)

            TouchpadSettings.setTouchpadSensitivity(1.5f)
            testScheduler.advanceUntilIdle()
            assertEquals(1.5f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)

            val prefs = testDataStore.data.first()
            assertEquals(1.5f, prefs[KEY_TOUCHPAD_SENSITIVITY] ?: 1.0f, 1e-5f)

            TouchpadSettings.setTouchpadSensitivity(0.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(0.1f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)

            TouchpadSettings.setTouchpadSensitivity(4.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(3.0f, TouchpadSettings.touchpadSensitivity.value, 1e-5f)
        }

    @Test
    fun testNaturalScrollSettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testDataStore = initSettings()

            assertTrue(TouchpadSettings.touchpadNaturalScroll.value)

            TouchpadSettings.setTouchpadNaturalScroll(false)
            testScheduler.advanceUntilIdle()
            assertFalse(TouchpadSettings.touchpadNaturalScroll.value)

            val prefs = testDataStore.data.first()
            assertTrue(prefs[KEY_TOUCHPAD_NATURAL_SCROLL] == false)
        }

    @Test
    fun testScrollSpeedSettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testDataStore = initSettings()

            assertEquals(1.0f, TouchpadSettings.touchpadScrollSpeed.value, 1e-5f)

            TouchpadSettings.setTouchpadScrollSpeed(2.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(2.0f, TouchpadSettings.touchpadScrollSpeed.value, 1e-5f)

            val prefs = testDataStore.data.first()
            assertEquals(2.0f, prefs[KEY_TOUCHPAD_SCROLL_SPEED] ?: 1.0f, 1e-5f)

            TouchpadSettings.setTouchpadScrollSpeed(0.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(0.5f, TouchpadSettings.touchpadScrollSpeed.value, 1e-5f)

            TouchpadSettings.setTouchpadScrollSpeed(4.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(3.0f, TouchpadSettings.touchpadScrollSpeed.value, 1e-5f)
        }

    @Test
    fun testHapticsSettingDefaultAndUpdates() =
        runTest(testDispatcher) {
            val testDataStore = initSettings()

            assertTrue(TouchpadSettings.touchpadHapticsEnabled.value)

            TouchpadSettings.setTouchpadHapticsEnabled(false)
            testScheduler.advanceUntilIdle()
            assertFalse(TouchpadSettings.touchpadHapticsEnabled.value)

            val prefs = testDataStore.data.first()
            assertTrue(prefs[KEY_TOUCHPAD_HAPTICS_ENABLED] == false)
        }
}
