package com.stormpanda.megingiard.settings

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.config.InternalBackup
import com.stormpanda.megingiard.config.MegingiardExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
        SettingsManager.resetForTesting(RuntimeEnvironment.getApplication())
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
}
