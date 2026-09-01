package com.stormpanda.megingiard.viewmodel

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
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
class MacroPadViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MacroPadState.clearPreviewLayout()
        MacroPadState.loadFrom(emptyList(), null)
        AppStateManager.closeQuickMenu()
    }

    @After
    fun tearDown() {
        MacroPadState.clearPreviewLayout()
        MacroPadState.loadFrom(emptyList(), null)
        AppStateManager.closeQuickMenu()
        Dispatchers.resetMain()
    }

    @Test
    fun testActiveProfileAndLayoutObservation() {
        val app = RuntimeEnvironment.getApplication()
        val vm = MacroPadViewModel(app)

        val layout = PadLayout(id = "l1", name = "TestLayout")
        val profile = PadProfile(id = "p1", name = "TestProfile", layouts = listOf(layout), activeLayoutId = "l1")
        MacroPadState.loadFrom(listOf(profile), "p1")

        assertEquals("p1", vm.activeProfile.value?.id)
        assertEquals("l1", vm.activeLayout.value?.id)
    }

    @Test
    fun testQuickMenuStateObservation() {
        val app = RuntimeEnvironment.getApplication()
        val vm = MacroPadViewModel(app)

        assertFalse(vm.isQuickMenuOpen.value)
        AppStateManager.openQuickMenu()
        assertTrue(vm.isQuickMenuOpen.value)
        AppStateManager.closeQuickMenu()
        assertFalse(vm.isQuickMenuOpen.value)
    }

    @Test
    fun testCreateHitTestEngine() {
        val app = RuntimeEnvironment.getApplication()
        val vm = MacroPadViewModel(app)

        val engine = vm.createHitTestEngine(buttonUnitDpToPx = { it * 2f })
        assertNotNull(engine)
    }

    @Test
    fun testStopInjectorsLifecycle() {
        val app = RuntimeEnvironment.getApplication()
        val vm = MacroPadViewModel(app)

        vm.watchInjectorLifecycle(app)
        vm.stopInjectors()
    }
}
