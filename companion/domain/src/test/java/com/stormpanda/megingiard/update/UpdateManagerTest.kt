package com.stormpanda.megingiard.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.stormpanda.megingiard.settings.KEY_AUTO_UPDATE_CHECK_ENABLED
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_NOTES
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_TAG
import com.stormpanda.megingiard.settings.KEY_LATEST_RELEASE_URL
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
class UpdateManagerTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempFile = File.createTempFile("update_manager_test", ".preferences_pb")
        tempFile.deleteOnExit()

        val testDataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
                produceFile = { tempFile },
            )

        UpdateManager.init(
            dataStore = testDataStore,
            scope = CoroutineScope(SupervisorJob() + testDispatcher),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFile.delete()
    }

    @Test
    fun `loadFrom correctly sets state when update is available`() =
        runTest(testDispatcher) {
            val testDataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + testDispatcher),
                    produceFile = { tempFile },
                )

            testDataStore.edit { prefs ->
                prefs[KEY_AUTO_UPDATE_CHECK_ENABLED] = true
                prefs[KEY_LATEST_RELEASE_TAG] = "v0.8.1"
                prefs[KEY_LATEST_RELEASE_URL] = "https://github.com/stormpanda/megingiard/releases/tag/v0.8.1"
                prefs[KEY_LATEST_RELEASE_NOTES] = "New release features"
            }

            val prefs = testDataStore.data.first()
            UpdateManager.loadFrom(prefs, currentVersion = "0.8.0")

            assertTrue(UpdateManager.autoUpdateCheckEnabled.value)
            assertEquals(0L, UpdateManager.lastCheckTime.value)
            assertTrue(UpdateManager.updateAvailable.value)
            assertEquals("v0.8.1", UpdateManager.latestReleaseInfo.value?.tagName)
        }

    @Test
    fun `setAutoUpdateCheckEnabled updates state and persists`() =
        runTest(testDispatcher) {
            UpdateManager.setAutoUpdateCheckEnabled(false)
            assertFalse(UpdateManager.autoUpdateCheckEnabled.value)

            UpdateManager.setAutoUpdateCheckEnabled(true)
            assertTrue(UpdateManager.autoUpdateCheckEnabled.value)
        }

    @Test
    fun `checkForUpdates before loadFrom respects disabled auto update setting after loadFrom`() =
        runTest(testDispatcher) {
            val testDataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + testDispatcher),
                    produceFile = { tempFile },
                )

            testDataStore.edit { prefs ->
                prefs[KEY_AUTO_UPDATE_CHECK_ENABLED] = false
            }

            // Simulate MainActivity calling checkForUpdates before DataStore loads
            UpdateManager.checkForUpdates(force = false, currentVersion = "0.8.0")
            assertFalse(UpdateManager.isChecking.value)

            val prefs = testDataStore.data.first()
            UpdateManager.loadFrom(prefs, currentVersion = "0.8.0")

            assertFalse(UpdateManager.autoUpdateCheckEnabled.value)
            assertFalse(UpdateManager.isChecking.value)
        }
}
