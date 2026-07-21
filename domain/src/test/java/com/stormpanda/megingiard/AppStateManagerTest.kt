package com.stormpanda.megingiard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.KeyboardSettings
import com.stormpanda.megingiard.settings.MacroPadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateManagerTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dummyDataStore =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = emptyFlow()

                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = emptyPreferences()
            }
        KeyboardSettings.init(dummyDataStore, CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changing active layout closes active modals in AppStateManager`() {
        // Set up initial profile with two layouts
        val layout1Id = UUID.randomUUID().toString()
        val layout2Id = UUID.randomUUID().toString()
        val profileId = UUID.randomUUID().toString()
        val testProfile =
            PadProfile(
                id = profileId,
                name = "Test Profile",
                layouts =
                    listOf(
                        PadLayout(id = layout1Id, name = "Layout 1"),
                        PadLayout(id = layout2Id, name = "Layout 2"),
                    ),
                activeLayoutId = layout1Id,
            )

        // Load into MacroPadState
        MacroPadState.loadFrom(listOf(testProfile), profileId)

        // Verify active layout is Layout 1
        assertTrue(MacroPadState.activeLayout.value?.id == layout1Id)

        // Activate viewport edit mode in AppStateManager
        AppStateManager.setViewportEditActive(true)
        assertTrue(AppStateManager.isViewportEditActive.value)

        // Switch layout to Layout 2
        MacroPadState.setActiveLayoutId(layout2Id)
        assertTrue(MacroPadState.activeLayout.value?.id == layout2Id)

        // Verify that AppStateManager has closed the viewport edit active modal
        assertFalse(AppStateManager.isViewportEditActive.value)
    }

    @Test
    fun `reconnect prompt dialog stays active during transitions and auto-resets on success`() =
        runTest {
            // Reset states
            AppStateManager.setHasAdbCredentials(true)
            AppStateManager.setPrivdPromptDismissed(false)
            AppStateManager.setBackgroundSettingsActive(false)
            MacroPadSettings.setPrivdShowAdbPromptForTesting(true)
            PrivdManager.setStateForTesting(PrivdState.OFF)

            // Yield to allow combine collection to initialize
            testScheduler.advanceUntilIdle()

            // Initially prompt is not active
            assertFalse(AppStateManager.isPrivdPromptActive.value)

            // 1. Transition to FAILED -> prompt should show
            PrivdManager.setStateForTesting(PrivdState.FAILED)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 2. Transition to CONNECTING -> prompt must STAY active (regression check)
            PrivdManager.setStateForTesting(PrivdState.CONNECTING)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 3. Transition to RUNNING -> prompt must STAY active until clicked Done
            PrivdManager.setStateForTesting(PrivdState.RUNNING)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 4. Click Done (or Skip) -> prompt turns off
            AppStateManager.setPrivdPromptDismissed(true)
            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)

            // 5. RUNNING state automatically resets dismissed to false for future drops
            assertFalse(AppStateManager.isPrivdPromptDismissed.value)

            // 6. Transition to FAILED again -> prompt should show again since dismissed was reset
            PrivdManager.setStateForTesting(PrivdState.FAILED)
            testScheduler.advanceUntilIdle()
            assertTrue(AppStateManager.isPrivdPromptActive.value)

            // 7. Open settings overlay -> prompt should hide and mark dismissed = true
            AppStateManager.setBackgroundSettingsActive(true)
            testScheduler.advanceUntilIdle()
            assertFalse(AppStateManager.isPrivdPromptActive.value)
            assertTrue(AppStateManager.isPrivdPromptDismissed.value)
        }

    @Test
    fun `setFullscreenKeyboardActive falls back to KeyboardSettings layout when null`() =
        runTest {
            // Assert initial state
            AppStateManager.setFullscreenKeyboardActive(false)

            // Set layout explicitly
            AppStateManager.setFullscreenKeyboardActive(true, KbLayout.AZERTY)
            assertEquals(KbLayout.AZERTY, AppStateManager.fullscreenKeyboardLayout.value)

            // Reset
            AppStateManager.setFullscreenKeyboardActive(false)

            // Activate with null/default layout, it should fall back to KeyboardSettings (default QWERTZ)
            AppStateManager.setFullscreenKeyboardActive(true)
            assertEquals(KbLayout.QWERTZ, AppStateManager.fullscreenKeyboardLayout.value)
        }

    @Test
    fun `fullscreenKeyboardLayout updates dynamically when KeyboardSettings layout changes`() =
        runTest {
            // Activate keyboard with no layout override (null)
            AppStateManager.setFullscreenKeyboardActive(true)
            assertEquals(KbLayout.QWERTZ, AppStateManager.fullscreenKeyboardLayout.value)

            // Change persistent setting layout
            KeyboardSettings.setKbLayout(KbLayout.AZERTY)

            // Verify fullscreenKeyboardLayout changes immediately
            assertEquals(KbLayout.AZERTY, AppStateManager.fullscreenKeyboardLayout.value)

            // Clean up
            AppStateManager.setFullscreenKeyboardActive(false)
            KeyboardSettings.setKbLayout(KbLayout.QWERTZ)
        }

    @Test
    fun `requestShutOff sets flag and consumeShutOffRequest resets it`() =
        runTest {
            assertFalse(AppStateManager.shutOffRequested.value)

            AppStateManager.requestShutOff()
            assertTrue(AppStateManager.shutOffRequested.value)

            AppStateManager.consumeShutOffRequest()
            assertFalse(AppStateManager.shutOffRequested.value)
        }
}
