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
import kotlinx.coroutines.launch
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
            assertTrue(UpdateManager.lastCheckTime.value >= 0L)
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

    @Test
    fun `checkForUpdates with mock server updates release info and updateAvailable`() =
        runTest(testDispatcher) {
            val server = java.net.ServerSocket(0)
            val port = server.localPort
            val releaseJson =
                """
                {
                    "tag_name": "v0.9.0",
                    "html_url": "https://github.com/stormpanda/megingiard/releases/tag/v0.9.0",
                    "body": "Major improvements"
                }
                """.trimIndent()

            val job =
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val client = server.accept()
                    val reader = client.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) {}
                    val out = client.getOutputStream()
                    val bodyBytes = releaseJson.toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${bodyBytes.size}\r\n\r\n".toByteArray(Charsets.UTF_8))
                    out.write(bodyBytes)
                    out.flush()
                    client.close()
                    server.close()
                }

            UpdateManager.checkForUpdates(
                force = true,
                currentVersion = "0.8.0",
                releasesApiUrl = "http://127.0.0.1:$port/release",
            )
            job.join()
            var tries = 0
            while (UpdateManager.isChecking.value && tries < 50) {
                Thread.sleep(20)
                tries++
            }
            testScheduler.advanceUntilIdle()

            assertEquals("v0.9.0", UpdateManager.latestReleaseInfo.value?.tagName)
            assertTrue(UpdateManager.updateAvailable.value)
            assertFalse(UpdateManager.isChecking.value)
        }

    @Test
    fun `checkForUpdates with server error sets checkError`() =
        runTest(testDispatcher) {
            val server = java.net.ServerSocket(0)
            val port = server.localPort

            val job =
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val client = server.accept()
                    val reader = client.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) {}
                    val out = client.getOutputStream()
                    val bodyBytes = "Internal Server Error".toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: ${bodyBytes.size}\r\n\r\n".toByteArray(Charsets.UTF_8))
                    out.write(bodyBytes)
                    out.flush()
                    client.close()
                    server.close()
                }

            UpdateManager.checkForUpdates(
                force = true,
                currentVersion = "0.8.0",
                releasesApiUrl = "http://127.0.0.1:$port/release",
            )
            job.join()
            var tries = 0
            while (UpdateManager.isChecking.value && tries < 50) {
                Thread.sleep(20)
                tries++
            }
            testScheduler.advanceUntilIdle()

            assertTrue(UpdateManager.checkError.value != null)
            assertFalse(UpdateManager.isChecking.value)
        }
}
