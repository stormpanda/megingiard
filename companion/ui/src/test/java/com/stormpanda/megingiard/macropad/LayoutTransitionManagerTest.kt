package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppStateManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutTransitionManagerTest {
    private val layout1 = PadLayout(id = "layout_1", name = "Layout 1")
    private val layout2 = PadLayout(id = "layout_2", name = "Layout 2")
    private val profile =
        PadProfile(
            id = "test_profile",
            name = "Test Profile",
            layouts = listOf(layout1, layout2),
            activeLayoutId = "layout_1",
        )

    @Before
    fun setUp() {
        MacroPadState.loadFrom(listOf(profile), profile.id)
        AppStateManager.closeQuickMenu()
        LayoutTransitionManager.unregisterWindowProvider()
    }

    @After
    fun tearDown() {
        LayoutTransitionManager.cancelTransition()
        LayoutTransitionManager.unregisterWindowProvider()
    }

    @Test
    fun `initial state has no active transition or snapshot`() {
        assertNull(LayoutTransitionManager.transitionSnapshot.value)
        assertEquals(0f, LayoutTransitionManager.transitionAlpha.value, 0.001f)
        assertFalse(LayoutTransitionManager.isTransitioning.value)
    }

    @Test
    fun `switchLayout with current layout id is no-op`() {
        assertEquals("layout_1", MacroPadState.activeLayout.value?.id)
        LayoutTransitionManager.switchLayout("layout_1")

        assertNull(LayoutTransitionManager.transitionSnapshot.value)
        assertFalse(LayoutTransitionManager.isTransitioning.value)
        assertEquals("layout_1", MacroPadState.activeLayout.value?.id)
    }

    @Test
    fun `switchLayout when window is null immediately updates activeLayout without snapshot`() {
        LayoutTransitionManager.registerWindowProvider { null }

        LayoutTransitionManager.switchLayout("layout_2")

        assertEquals("layout_2", MacroPadState.activeLayout.value?.id)
        assertNull(LayoutTransitionManager.transitionSnapshot.value)
        assertFalse(LayoutTransitionManager.isTransitioning.value)
        assertEquals(0f, LayoutTransitionManager.transitionAlpha.value, 0.001f)
    }

    @Test
    fun `switchLayout when QuickMenu is open immediately updates activeLayout without snapshot`() {
        AppStateManager.openQuickMenu()

        LayoutTransitionManager.switchLayout("layout_2")

        assertEquals("layout_2", MacroPadState.activeLayout.value?.id)
        assertNull(LayoutTransitionManager.transitionSnapshot.value)
        assertFalse(LayoutTransitionManager.isTransitioning.value)
        assertEquals(0f, LayoutTransitionManager.transitionAlpha.value, 0.001f)
    }

    @Test
    fun `cancelTransition clears transition state and resets alpha`() {
        LayoutTransitionManager.cancelTransition()

        assertNull(LayoutTransitionManager.transitionSnapshot.value)
        assertFalse(LayoutTransitionManager.isTransitioning.value)
        assertEquals(0f, LayoutTransitionManager.transitionAlpha.value, 0.001f)
    }

    @Test
    fun `unregisterWindowProvider clears window provider and cancels any transition`() {
        LayoutTransitionManager.registerWindowProvider { null }
        LayoutTransitionManager.unregisterWindowProvider()

        assertNull(LayoutTransitionManager.transitionSnapshot.value)
        assertFalse(LayoutTransitionManager.isTransitioning.value)
    }
}
