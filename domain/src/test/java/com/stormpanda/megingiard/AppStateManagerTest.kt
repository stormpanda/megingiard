package com.stormpanda.megingiard

import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import com.stormpanda.megingiard.settings.MacroPadSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
}
