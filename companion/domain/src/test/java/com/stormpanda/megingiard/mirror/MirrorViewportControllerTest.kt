package com.stormpanda.megingiard.mirror

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MirrorViewportControllerTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ScreenCaptureManager.scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + testDispatcher)
        MacroPadState.clearPreviewLayout()
        MacroPadState.loadFrom(emptyList(), null)
        testDispatcher.scheduler.advanceUntilIdle()
        ScreenCaptureManager.resetMirrorSessionState()
        ScreenCaptureManager.setCapturing(false)
        ScreenCaptureManager.setScale(1f)
        ScreenCaptureManager.setOffsetX(0f)
        ScreenCaptureManager.setOffsetY(0f)
        ScreenCaptureManager.setSurfaceSize(1000f, 1000f)
    }

    @After
    fun tearDown() {
        MacroPadState.clearPreviewLayout()
        MacroPadState.loadFrom(emptyList(), null)
        ScreenCaptureManager.resetMirrorSessionState()
        ScreenCaptureManager.scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
        Dispatchers.resetMain()
    }

    @Test
    fun restoreFromManager_syncsManagerValues() {
        ScreenCaptureManager.setScale(2.5f)
        ScreenCaptureManager.setOffsetX(50f)
        ScreenCaptureManager.setOffsetY(-100f)

        MirrorViewportController.restoreFromManager()

        assertEquals(2.5f, MirrorViewportController.scale.value, 0.001f)
        assertEquals(50f, MirrorViewportController.offsetX.value, 0.001f)
        assertEquals(-100f, MirrorViewportController.offsetY.value, 0.001f)
    }

    @Test
    fun restoreFromLayout_withCutouts_calculatesCorrectScaleAndOffset() {
        val cutout =
            ScreenCutout(
                id = "c1",
                name = "Cutout 1",
                srcX = 0.25f,
                srcY = 0.25f,
                srcWidth = 0.5f,
                srcHeight = 0.5f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
            )
        val layout =
            PadLayout(
                id = "l1",
                name = "Mirror Layout",
                mirrorCutouts = listOf(cutout),
            )
        val profile =
            PadProfile(
                id = "p1",
                name = "Mirror Profile",
                layouts = listOf(layout),
                activeLayoutId = "l1",
            )
        MacroPadState.loadFrom(listOf(profile), "p1")
        testDispatcher.scheduler.advanceUntilIdle()
        val activeL = MacroPadState.activeLayout.value
        org.junit.Assert.assertNotNull("Active layout should not be null", activeL)
        org.junit.Assert.assertNotNull("Cutout should not be null", activeL?.mirrorCutouts?.firstOrNull())
        ScreenCaptureManager.setSurfaceSize(1000f, 1000f)
        org.junit.Assert.assertTrue(ScreenCaptureManager.surfaceWidth.value > 0f)
        org.junit.Assert.assertTrue(ScreenCaptureManager.surfaceHeight.value > 0f)

        MirrorViewportController.restoreFromLayout()

        assertEquals(2.0f, MirrorViewportController.scale.value, 0.001f)
        assertEquals(0f, MirrorViewportController.offsetX.value, 0.001f)
        assertEquals(0f, MirrorViewportController.offsetY.value, 0.001f)
    }
}
