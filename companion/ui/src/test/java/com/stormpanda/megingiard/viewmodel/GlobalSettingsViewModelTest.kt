package com.stormpanda.megingiard.viewmodel

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.AppLanguage
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class GlobalSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SettingsManager.init(RuntimeEnvironment.getApplication())
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModelStateReflectsSettings() {
        val vm = GlobalSettingsViewModel()

        vm.setAccentColor(0xFFFF0000.toInt())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0xFFFF0000.toInt(), vm.accentColor.value)

        vm.setCustomAccentColor(0xFF00FF00.toInt())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0xFF00FF00.toInt(), vm.customAccentColor.value)

        vm.setThemeMode(ThemeMode.VALHALLA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ThemeMode.VALHALLA, vm.themeMode.value)

        vm.setOverlayAtBottom(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.overlayAtBottom.value)

        vm.setOverlayFadeOut(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.overlayFadeOut.value)

        vm.setAppLanguage(AppLanguage.DE)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppLanguage.DE, vm.appLanguage.value)

        vm.setLogLevel(AppLog.Level.DEBUG)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppLog.Level.DEBUG, vm.logLevel.value)

        vm.setSteamGridDbApiToken("test_token")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("test_token", vm.steamGridDbApiToken.value)
        assertEquals(SteamGridDbTestStatus.IDLE, vm.steamGridDbTestStatus.value)

        vm.setExcludeFromRecents(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.excludeFromRecents.value)

        vm.setGamepadSwapFaceButtons(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.gamepadSwapFaceButtons.value)

        vm.setAutoUpdateCheckEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.autoUpdateCheckEnabled.value)

        vm.setPrivdDeadzoneLeft(0.20f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.20f, vm.privdDeadzoneLeft.value, 0.001f)

        vm.setPrivdDeadzoneRight(0.22f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.22f, vm.privdDeadzoneRight.value, 0.001f)
    }

    @Test
    fun testSteamGridDbTestValidation() {
        val vm = GlobalSettingsViewModel()

        // Blank token immediately flags invalid
        vm.testSteamGridDbConnection("   ")
        assertEquals(SteamGridDbTestStatus.INVALID_TOKEN, vm.steamGridDbTestStatus.value)
    }

    @Test
    fun testUpdateCheckTriggers() {
        val vm = GlobalSettingsViewModel()
        vm.checkForUpdatesManually()
        vm.checkForUpdatesBackground()
        assertNotNull(vm.isCheckingUpdates)
    }

    @Test
    fun testResetActions() {
        val vm = GlobalSettingsViewModel()
        vm.resetAllTutorials()
        vm.privdResetBootstrapStage()
        vm.requestSaveLogReport()
    }
}
