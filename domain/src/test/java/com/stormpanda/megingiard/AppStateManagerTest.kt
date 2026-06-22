package com.stormpanda.megingiard

import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
        val testProfile = PadProfile(
            id = profileId,
            name = "Test Profile",
            layouts = listOf(
                PadLayout(id = layout1Id, name = "Layout 1"),
                PadLayout(id = layout2Id, name = "Layout 2")
            ),
            activeLayoutId = layout1Id
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
}
