package com.stormpanda.megingiard.settings

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.config.ExportMetadata
import com.stormpanda.megingiard.config.InternalBackup
import com.stormpanda.megingiard.config.MegingiardExport
import com.stormpanda.megingiard.config.SCHEMA_VERSION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SettingsManager.resetForTesting(RuntimeEnvironment.getApplication(), CoroutineScope(testDispatcher))
        testDispatcher.scheduler.advanceUntilIdle()
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

        // 2. Set updates and verify
        SettingsManager.setWelcomeTourCompletedVersion(1)
        assertEquals(1, SettingsManager.welcomeTourCompletedVersion.value)

        // 3. Reset all tutorials and verify reset
        SettingsManager.resetAllTutorials()
        assertEquals(0, SettingsManager.welcomeTourCompletedVersion.value)
    }

    @Test
    fun testAccentColorAndCustomAccentColorIndependence() {
        val testCustomColor = 0xFF00E5FF.toInt() // Cyan
        val testPresetColor = 0xFFFF5252.toInt() // Red preset

        SettingsManager.setCustomAccentColor(testCustomColor)
        SettingsManager.setAccentColor(testCustomColor)
        assertEquals(testCustomColor, SettingsManager.customAccentColor.value)
        assertEquals(testCustomColor, SettingsManager.accentColor.value)

        // Switch to preset: accentColor changes, but customAccentColor is preserved
        SettingsManager.setAccentColor(testPresetColor)
        assertEquals(testPresetColor, SettingsManager.accentColor.value)
        assertEquals(testCustomColor, SettingsManager.customAccentColor.value)
    }

    @Test
    fun testThemeModeAndOverlaySetters() {
        SettingsManager.setThemeMode(ThemeMode.VALHALLA)
        assertEquals(ThemeMode.VALHALLA, SettingsManager.themeMode.value)

        SettingsManager.setOverlayAtBottom(true)
        assertTrue(SettingsManager.overlayAtBottom.value)

        SettingsManager.setOverlayFadeOut(true)
        assertTrue(SettingsManager.overlayFadeOut.value)

        SettingsManager.setExcludeFromRecents(true)
        assertTrue(SettingsManager.excludeFromRecents.value)
    }

    @Test
    fun testSteamGridDbTokenAndAppLanguageAndLogLevel() {
        SettingsManager.setSteamGridDbApiToken("test_token_sgdb")
        assertEquals("test_token_sgdb", SettingsManager.steamGridDbApiToken.value)

        SettingsManager.setAppLanguage(AppLanguage.DE)
        assertEquals(AppLanguage.DE, SettingsManager.appLanguage.value)

        SettingsManager.setLogLevel(AppLog.Level.DEBUG)
        assertEquals(AppLog.Level.DEBUG, SettingsManager.logLevel.value)
        assertEquals(AppLog.Level.DEBUG, AppLog.level)
    }

    @Test
    fun testDefaultExportAuthorPersistenceAndReset() =
        runTest(testDispatcher) {
            assertEquals("", SettingsManager.defaultExportAuthor.value)

            SettingsManager.setDefaultExportAuthor("StormPanda")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("StormPanda", SettingsManager.defaultExportAuthor.value)

            SettingsManager.resetForTesting(RuntimeEnvironment.getApplication(), CoroutineScope(testDispatcher))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("", SettingsManager.defaultExportAuthor.value)
        }

    @Test
    fun testImportKeyboardAutoOpenOnFocus() =
        runTest(testDispatcher) {
            val payload =
                mapOf(
                    "keyboard" to
                        mapOf(
                            "kb_auto_open_on_focus" to JsonPrimitive(true),
                            "kb_auto_hide_on_focus_loss" to JsonPrimitive(false),
                        ),
                )
            SettingsManager.importGroupedSettingsAwait(payload)
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(KeyboardSettings.kbAutoOpenOnFocus.value)
            assertFalse(KeyboardSettings.kbAutoHideOnFocusLoss.value)
        }

    @Test
    fun testSaveBackupPersistsToDedicatedFileOutOfDataStore() =
        runTest(testDispatcher) {
            val app = RuntimeEnvironment.getApplication()
            val backupFile = SettingsManager.internalBackupsFile(app.filesDir)
            val customBackup =
                InternalBackup(
                    dateString = "2026-01-15",
                    timestampMs = 1768435200000L,
                    export =
                        MegingiardExport(
                            schemaVersion = SCHEMA_VERSION,
                            metadata =
                                ExportMetadata(
                                    appVersionCode = 1,
                                    appVersionName = "1.0.0",
                                    exportedAt = "2026-01-15T00:00:00Z",
                                    deviceModel = "TestDevice",
                                ),
                            checksum = "sha256:test",
                            settings = emptyMap(),
                            profiles = emptyList(),
                        ),
                )

            SettingsManager.saveBackup(customBackup)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue("Internal backups file must exist under filesDir/backups/", backupFile.exists())
            assertTrue(SettingsManager.internalBackups.value.any { it.dateString == "2026-01-15" })
            assertTrue(backupFile.readText().contains("2026-01-15"))
        }

    @Test
    fun testLiveDeadzoneUpdateIsNotOverwrittenByUnrelatedSettingSave() =
        runTest(testDispatcher) {
            MacroPadSettings.setDeadzoneLeft(0.15f)
            testDispatcher.scheduler.advanceUntilIdle()

            // Simulate an in-flight 120 Hz slider drag (live in-memory update before commit)
            MacroPadSettings.updateDeadzoneLeftLive(0.32f)
            assertEquals(0.32f, MacroPadSettings.deadzoneLeft.value, 0.0001f)

            // Trigger an unrelated DataStore write; load-once guard must not echo stale 0.15f back over 0.32f
            SettingsManager.setOverlayAtBottom(true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(0.32f, MacroPadSettings.deadzoneLeft.value, 0.0001f)
        }
}
