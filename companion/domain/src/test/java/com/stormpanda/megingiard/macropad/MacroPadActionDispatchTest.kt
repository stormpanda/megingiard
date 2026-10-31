package com.stormpanda.megingiard.macropad

import app.cash.turbine.test
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionCommand
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenshotTarget
import com.stormpanda.megingiard.privd.PrivdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
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

        val kbAction = PadAction.FullScreenKeyboard()
        injectActionDown(kbAction)
        assertTrue(AppStateManager.isFullscreenKeyboardActive.value)

        val editAction = PadAction.MirrorViewportEdit
        injectActionDown(editAction)
        assertTrue(AppStateManager.isViewportEditActive.value)
    }

    @Test
    fun injectActionDown_mirrorActions() =
        runTest {
            AppStateManager.commands.test {
                ScreenCaptureManager.setCapturing(false)
                injectActionDown(PadAction.MirrorPlayStop)
                assertEquals(CompanionCommand.StartMirror, awaitItem())

                ScreenCaptureManager.setCapturing(true)
                injectActionDown(PadAction.MirrorPlayStop)
                assertEquals(CompanionCommand.StopMirror, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }

            ScreenCaptureManager.setFrozen(false)
            injectActionDown(PadAction.MirrorFreeze)
            assertTrue(ScreenCaptureManager.isFrozen.value)
        }

    @Test
    fun injectActionDown_appLauncher() =
        runTest {
            AppStateManager.commands.test {
                injectActionDown(PadAction.AppLauncher(packageName = "com.retroarch"))
                assertEquals(CompanionCommand.LaunchApp(packageName = "com.retroarch"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
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

    @Test
    fun injectActionDownAndUp_screenshotTargets() {
        for (target in ScreenshotTarget.entries) {
            ScreenCaptureManager.consumeScreenshotRequest()
            val action = PadAction.Screenshot(target)
            injectActionDown(action)
            assertTrue(ScreenCaptureManager.screenshotRequested.value)
            assertEquals(target, ScreenCaptureManager.pendingScreenshotTarget.value)
            injectActionUp(action)
            assertTrue(ScreenCaptureManager.screenshotRequested.value)
            ScreenCaptureManager.consumeScreenshotRequest()
        }
    }

    @Test
    fun injectActionDownAndUp_switchToLayoutAndHoldToShift() {
        val layoutA = PadLayout(id = "layout-a", name = "Main")
        val layoutB = PadLayout(id = "layout-b", name = "Shift")
        val profile =
            PadProfile(
                id = "prof-1",
                name = "Test",
                layouts = listOf(layoutA, layoutB),
                activeLayoutId = "layout-a",
            )
        MacroPadState.loadFrom(listOf(profile), "prof-1")
        assertEquals("layout-a", MacroPadState.activeLayout.value?.id)

        // Tap-to-switch stays on layout B after release
        val tapAction = PadAction.SwitchToLayout(layoutId = "layout-b", holdToShift = false)
        injectActionDown(tapAction)
        assertEquals("layout-b", MacroPadState.activeLayout.value?.id)
        injectActionUp(tapAction)
        assertEquals("layout-b", MacroPadState.activeLayout.value?.id)

        // Reset to layout A and test Hold-to-Shift
        MacroPadState.setActiveLayoutId("layout-a")
        val holdAction = PadAction.SwitchToLayout(layoutId = "layout-b", holdToShift = true)
        injectActionDown(holdAction)
        assertEquals("layout-b", MacroPadState.activeLayout.value?.id)
        injectActionUp(holdAction)
        assertEquals("layout-a", MacroPadState.activeLayout.value?.id)

        // End-to-end hit-test engine hold-to-shift across layouts
        val engine = MacroPadHitTestEngine(buttonUnitDpToPx = { it })
        val shiftBtn =
            PadButton(
                id = "btn-shift",
                label = "Shift",
                posX = 0.5f,
                posY = 0.5f,
                action = holdAction,
            )
        engine.onPress(7L, 500f, 500f, 1000f, 1000f, listOf(shiftBtn), false)
        assertEquals("layout-b", MacroPadState.activeLayout.value?.id)
        engine.onRelease(7L, emptyList())
        assertEquals("layout-a", MacroPadState.activeLayout.value?.id)
    }

    @Test
    fun injectActionDown_switchToHub() {
        AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
        injectActionDown(PadAction.SwitchToHub)
        assertEquals(CompanionViewMode.DASHBOARD, AppStateManager.companionViewMode.value)
    }

    @Test
    fun injectActionDown_stopAllMacros() =
        runBlocking {
            PrivdClient.isConnectedForTest = true
            try {
                val macro =
                    Macro(
                        id = "macro-loop",
                        name = "LoopMacro",
                        loopEnabled = true,
                        steps =
                            listOf(
                                MacroStep.GamepadButtonTap(
                                    startTimeMs = 0L,
                                    durationMs = 50L,
                                    btnCode = 304,
                                    label = "A",
                                ),
                            ),
                    )
                MacroExecutor.execute(macro)
                val started =
                    withTimeoutOrNull(1000L) {
                        while (!MacroExecutor.isRunning("macro-loop")) {
                            delay(5)
                        }
                        true
                    } ?: false
                assertTrue(started)

                injectActionDown(PadAction.StopAllMacros)
                val stopped =
                    withTimeoutOrNull(1000L) {
                        while (MacroExecutor.isRunning("macro-loop")) {
                            delay(5)
                        }
                        true
                    } ?: false
                assertTrue(stopped)
            } finally {
                MacroExecutor.stopAll()
                PrivdClient.isConnectedForTest = null
            }
        }

    @Test
    fun injectActionDownAndUp_touchTap() {
        PrivdClient.isConnectedForTest = true
        PrivdClient.clearQueueForTest()
        try {
            val action = PadAction.TouchTap(normX = 0.5f, normY = 0.5f)
            injectActionDown(action)
            assertEquals("D $MP_TOUCH_TAP_SLOT 540 960\n", PrivdClient.pollCommandForTest())

            injectActionUp(action)
            assertEquals("U $MP_TOUCH_TAP_SLOT\n", PrivdClient.pollCommandForTest())
        } finally {
            PrivdClient.clearQueueForTest()
            PrivdClient.isConnectedForTest = null
        }
    }

    @Test
    fun injectActionDownAndUp_mediaControl() {
        PrivdClient.isConnectedForTest = true
        PrivdClient.clearQueueForTest()
        try {
            for (mediaAction in MediaControlAction.entries) {
                val action = PadAction.MediaControl(mediaAction)
                injectActionDown(action)
                assertEquals("KD ${mediaAction.keycode}\n", PrivdClient.pollCommandForTest())

                injectActionUp(action)
                assertEquals("KU ${mediaAction.keycode}\n", PrivdClient.pollCommandForTest())
            }
        } finally {
            PrivdClient.clearQueueForTest()
            PrivdClient.isConnectedForTest = null
        }
    }
}
