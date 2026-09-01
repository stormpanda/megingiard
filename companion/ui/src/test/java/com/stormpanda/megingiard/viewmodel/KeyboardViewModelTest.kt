package com.stormpanda.megingiard.viewmodel

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.keyboard.KeyboardMode
import com.stormpanda.megingiard.settings.KeyboardSettings
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
class KeyboardViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppStateManager.closeQuickMenu()
    }

    @After
    fun tearDown() {
        AppStateManager.closeQuickMenu()
        Dispatchers.resetMain()
    }

    @Test
    fun testKeyboardModeTransitions() {
        val app = RuntimeEnvironment.getApplication()
        val vm = KeyboardViewModel(app)

        assertEquals(KeyboardMode.LETTERS, vm.keyboardMode.value)
        vm.setKeyboardMode(KeyboardMode.SYMBOLS_1)
        assertEquals(KeyboardMode.SYMBOLS_1, vm.keyboardMode.value)
        vm.setKeyboardMode(KeyboardMode.NUMERIC)
        assertEquals(KeyboardMode.NUMERIC, vm.keyboardMode.value)
    }

    @Test
    fun testLayoutCyclingAndSetters() {
        val app = RuntimeEnvironment.getApplication()
        val vm = KeyboardViewModel(app)

        vm.setKbLayout(KbLayout.QWERTY)
        assertEquals(KbLayout.QWERTY, vm.kbLayout.value)

        vm.cycleKbLayout()
        assertEquals(KbLayout.AZERTY, vm.kbLayout.value)

        vm.setKbTouchpadEnabled(false)
        assertFalse(vm.kbTouchpadEnabled.value)
    }

    @Test
    fun testShortcutHelpers() {
        val app = RuntimeEnvironment.getApplication()
        val vm = KeyboardViewModel(app)

        vm.selectAll()
        vm.copy()
        vm.cut()
        vm.paste()
    }

    @Test
    fun testQuickMenuControl() {
        val app = RuntimeEnvironment.getApplication()
        val vm = KeyboardViewModel(app)

        AppStateManager.openQuickMenu()
        assertTrue(vm.isQuickMenuOpen.value)

        vm.closeQuickMenu()
        assertFalse(vm.isQuickMenuOpen.value)
    }

    @Test
    fun testGestureProcessorAndControllerNotNull() {
        val app = RuntimeEnvironment.getApplication()
        val vm = KeyboardViewModel(app)

        assertNotNull(vm.controller)
        assertNotNull(vm.gestureProcessor)
    }
}
