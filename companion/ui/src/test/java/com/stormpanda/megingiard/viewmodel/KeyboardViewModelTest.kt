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

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        KeyboardSettings.resetForTesting()
        AppStateManager.closeQuickMenu()
    }

    @After
    fun tearDown() {
        AppStateManager.closeQuickMenu()
        KeyboardSettings.resetForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun testKeyboardModeTransitions() {
        val vm = KeyboardViewModel()

        assertEquals(KeyboardMode.LETTERS, vm.keyboardMode.value)
        vm.setKeyboardMode(KeyboardMode.SYMBOLS_1)
        assertEquals(KeyboardMode.SYMBOLS_1, vm.keyboardMode.value)
        vm.setKeyboardMode(KeyboardMode.NUMERIC)
        assertEquals(KeyboardMode.NUMERIC, vm.keyboardMode.value)
    }

    @Test
    fun testLayoutCyclingAndSetters() {
        val vm = KeyboardViewModel()

        vm.setKbLayout(KbLayout.QWERTY)
        assertEquals(KbLayout.QWERTY, vm.kbLayout.value)

        vm.setKbTouchpadEnabled(false)
        assertFalse(vm.kbTouchpadEnabled.value)
    }

    @Test
    fun testShortcutHelpers() {
        val vm = KeyboardViewModel()

        vm.selectAll()
        vm.copy()
        vm.cut()
        vm.paste()
    }

    @Test
    fun testQuickMenuControl() {
        val vm = KeyboardViewModel()

        AppStateManager.openQuickMenu()
        assertTrue(vm.isQuickMenuOpen.value)

        vm.closeQuickMenu()
        assertFalse(vm.isQuickMenuOpen.value)
    }

    @Test
    fun testGestureProcessorNotNull() {
        val vm = KeyboardViewModel()

        assertNotNull(vm.gestureProcessor)
    }
}
