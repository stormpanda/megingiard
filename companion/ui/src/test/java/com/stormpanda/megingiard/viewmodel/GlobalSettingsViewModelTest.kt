package com.stormpanda.megingiard.viewmodel

import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        SettingsManager.resetForTesting(
            RuntimeEnvironment.getApplication(),
            CoroutineScope(testDispatcher),
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSteamGridDbTokenAndValidation() {
        val vm = GlobalSettingsViewModel()

        // Blank token immediately flags invalid
        vm.testSteamGridDbConnection("   ")
        assertEquals(SteamGridDbTestStatus.INVALID_TOKEN, vm.steamGridDbTestStatus.value)

        // Updating token resets status to IDLE and updates SettingsManager
        vm.setSteamGridDbApiToken("test_token")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("test_token", SettingsManager.steamGridDbApiToken.value)
        assertEquals(SteamGridDbTestStatus.IDLE, vm.steamGridDbTestStatus.value)
    }
}
