package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.keyboard.KbLayout
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MacroPadActionDispatchTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setFrozen(false)
        AppStateManager.setFullscreenMouseActive(false)
        AppStateManager.setFullscreenKeyboardActive(false)
        AppStateManager.setViewportEditActive(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun injectActionDownAndUp_keyboardKey() {
        val action = PadAction.KeyboardKey(keycode = 30, label = "A", modifiers = listOf(42))
        injectActionDown(action)
        injectActionUp(action)
    }

    @Test
    fun injectActionDownAndUp_gamepadButton() {
        val action = PadAction.GamepadButton(btnCode = 304, label = "A", extraBtnCodes = listOf(305))
        injectActionDown(action)
        injectActionUp(action)
    }

    @Test
    fun injectActionDownAndUp_mouseButton() {
        val action = PadAction.MouseButton(button = MouseButton.LEFT)
        injectActionDown(action)
        injectActionUp(action)
    }

    @Test
    fun injectActionDown_uiModeActions() {
        val mouseAction = PadAction.FullScreenMouse(sensitivity = 2f)
        injectActionDown(mouseAction)
        assertTrue(AppStateManager.isFullscreenMouseActive.value)

        val kbAction = PadAction.FullScreenKeyboard(layout = KbLayout.QWERTY)
        injectActionDown(kbAction)
        assertTrue(AppStateManager.isFullscreenKeyboardActive.value)

        val editAction = PadAction.MirrorViewportEdit
        injectActionDown(editAction)
        assertTrue(AppStateManager.isViewportEditActive.value)
    }

    @Test
    fun injectActionDown_mirrorActions() {
        ScreenCaptureManager.setCapturing(false)
        injectActionDown(PadAction.MirrorPlayStop)
        assertTrue(AppStateManager.mirrorStartRequested.value)

        ScreenCaptureManager.setCapturing(true)
        injectActionDown(PadAction.MirrorPlayStop)
        assertTrue(AppStateManager.mirrorStopRequested.value)

        ScreenCaptureManager.setFrozen(false)
        injectActionDown(PadAction.MirrorFreeze)
        assertTrue(ScreenCaptureManager.isFrozen.value)
    }

    @Test
    fun injectActionDown_appLauncher() {
        injectActionDown(PadAction.AppLauncher(packageName = "com.retroarch"))
        assertEquals("com.retroarch", AppStateManager.pendingAppLaunchRequest.value?.packageName)
    }

    @Test
    fun injectActionDown_navigationAndNoOpActions() {
        injectActionDown(PadAction.ScrollWheel)
        injectActionDown(PadAction.TrackpointMove())
        injectActionDown(PadAction.BackgroundPeek)
        injectActionDown(PadAction.LayoutNext)
        injectActionDown(PadAction.LayoutPrevious)
        injectActionDown(PadAction.ProfileSwitcher)
        injectActionDown(PadAction.MirrorTouchProjection)

        injectActionUp(PadAction.ScrollWheel)
        injectActionUp(PadAction.TrackpointMove())
        injectActionUp(PadAction.BackgroundPeek)
        injectActionUp(PadAction.LayoutNext)
        injectActionUp(PadAction.LayoutPrevious)
        injectActionUp(PadAction.ProfileSwitcher)
        injectActionUp(PadAction.MirrorTouchProjection)
    }
}
